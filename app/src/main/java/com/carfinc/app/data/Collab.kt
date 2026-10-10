package com.carfinc.app.data

import android.util.Base64
import com.carfinc.app.model.Kind
import com.carfinc.app.model.Mat
import com.carfinc.app.model.MatItem
import com.carfinc.app.model.MatSize
import com.carfinc.app.model.matItemFromJson
import com.carfinc.app.model.toJson
import com.carfinc.app.render.StickerImages
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

/**
 * Collective mats ("make a mat with someone", up to 5 people).
 *
 * Storage is a Firebase Realtime Database that you own, talked to over its plain REST API
 * (no SDK, no google-services.json). Layout:
 *   mats/<CODE>/meta            name, colour, shape, sliders
 *   mats/<CODE>/items/i<id>     one node per piece ({"d":true} = deleted)
 *   mats/<CODE>/imgs/<name>     base64 PNG of person/object cut-outs
 *   mats/<CODE>/members/<dev>   timestamp, used to enforce the 5-person limit
 *
 * Every device keeps the last state it agreed on with the server ("base") and does a 3-way merge
 * on each sync, so two people dragging different pieces never overwrite each other.
 */
object Collab {
    const val MAX_MEMBERS = 5
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    data class Meta(
        val name: String, val color: Int, val size: MatSize, val paperEdge: Boolean,
        val letterScale: Float, val chaos: Float
    ) {
        fun toJson() = JSONObject().apply {
            put("name", name); put("color", color); put("size", size.name); put("paperEdge", paperEdge)
            put("letterScale", letterScale.toDouble()); put("chaos", chaos.toDouble())
            put("t", System.currentTimeMillis())
        }
        fun applyTo(m: Mat) = m.copy(name = name, color = color, size = size, paperEdge = paperEdge,
            letterScale = letterScale, chaos = chaos)
        companion object {
            fun of(m: Mat) = Meta(m.name, m.color, m.size, m.paperEdge, m.letterScale, m.chaos)
            fun parse(o: JSONObject): Meta? = runCatching {
                Meta(o.getString("name"), o.getInt("color"), MatSize.valueOf(o.getString("size")),
                    o.optBoolean("paperEdge", true), o.optDouble("letterScale", 1.0).toFloat(),
                    o.optDouble("chaos", 1.0).toFloat())
            }.getOrNull()
        }
    }

    class SyncOutcome(val items: List<MatItem>, val meta: Meta, val members: Int)

    sealed interface JoinResult {
        class Ok(val mat: Mat) : JoinResult
        object NotConfigured : JoinResult
        object NotFound : JoinResult
        object Full : JoinResult
        object Network : JoinResult
    }

    // ------------------------------------------------------------------ plumbing

    fun configured(): Boolean = Prefs.dbUrl.startsWith("https://")
    private fun base(): String = Prefs.dbUrl.trim().trimEnd('/')

    fun newCode(): String = (1..6).map { ALPHABET.random() }.joinToString("")
    fun normalizeCode(s: String): String = s.uppercase().filter { it in ALPHABET }.take(6)

    /** @return response text, or null when the request failed (offline, rules denied, …). */
    private fun http(method: String, path: String, query: String = "", body: String? = null): String? = try {
        val url = URL("${base()}/$path.json" + if (query.isEmpty()) "" else "?$query")
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 8000; c.readTimeout = 15000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
        c.disconnect()
        if (code in 200..299) text ?: "" else null
    } catch (e: Exception) { null }

    /** null = request failed; empty object = nothing stored there. */
    private fun getObj(path: String, query: String = ""): JSONObject? {
        val t = http("GET", path, query) ?: return null
        if (t.isBlank() || t.trim() == "null") return JSONObject()
        return runCatching { JSONObject(t) }.getOrNull()   // null (not "empty") if it isn't an object
    }

    private fun put(path: String, json: String): Boolean = http("PUT", path, body = json) != null

    // ------------------------------------------------------------------ membership

    /** Returns member device ids, or null when offline. */
    private fun members(code: String): Set<String>? =
        getObj("mats/$code/members", "shallow=true")?.keys()?.asSequence()?.toSet()

    private fun touchMember(code: String) { put("mats/$code/members/${Prefs.deviceId}", System.currentTimeMillis().toString()) }

    fun leave(code: String) {
        if (!configured() || code.isEmpty()) return
        http("DELETE", "mats/$code/members/${Prefs.deviceId}")
        bases.remove(code)
    }

    /** Turn a personal mat into a collective one. Returns the updated mat, or null on failure. */
    fun create(mat: Mat): Mat? {
        if (!configured()) return null
        var code = newCode()
        var tries = 0
        while (tries < 5) {
            val existing = getObj("mats/$code/meta") ?: return null
            if (existing.length() == 0) break
            code = newCode(); tries++
        }
        if (!put("mats/$code/meta", Meta.of(mat).toJson().toString())) return null
        touchMember(code)
        val shared = mat.copy(collective = true, code = code)
        // push every piece + image right away
        sync(shared) ?: return null
        return shared
    }

    fun join(rawCode: String): JoinResult {
        if (!configured()) return JoinResult.NotConfigured
        val code = normalizeCode(rawCode)
        if (code.length != 6) return JoinResult.NotFound
        val meta = getObj("mats/$code/meta") ?: return JoinResult.Network
        if (meta.length() == 0) return JoinResult.NotFound
        val m = Meta.parse(meta) ?: return JoinResult.NotFound
        val who = members(code) ?: return JoinResult.Network
        if (Prefs.deviceId !in who && who.size >= MAX_MEMBERS) return JoinResult.Full
        touchMember(code)
        val blank = Mat(id = "c_$code", name = m.name, color = m.color, size = m.size, items = emptyList(),
            paperEdge = m.paperEdge, letterScale = m.letterScale, chaos = m.chaos,
            collective = true, code = code)
        val out = sync(blank) ?: return JoinResult.Network
        return JoinResult.Ok(blank.copy(items = out.items))
    }

    // ------------------------------------------------------------------ sync

    private class Base(val items: Map<Long, MatItem>, val meta: Meta)
    private val bases = HashMap<String, Base>()
    private val syncCount = HashMap<String, Int>()
    private val memberCache = HashMap<String, Int>()

    /**
     * Pull, 3-way merge with [local], push our changes. Blocking — call off the main thread.
     * @return the merged state, or null when the network/database was unreachable.
     */
    @Synchronized
    fun sync(local: Mat): SyncOutcome? {
        if (!configured() || !local.collective || local.code.isEmpty()) return null
        val code = local.code

        val remoteJson = getObj("mats/$code/items") ?: return null
        val metaJson = getObj("mats/$code/meta") ?: return null
        val remoteImgs = getObj("mats/$code/imgs", "shallow=true") ?: return null

        // remote items; null value = tombstone
        val remote = HashMap<Long, MatItem?>()
        for (k in remoteJson.keys()) {
            // keys are "i<id>" so Firebase never turns small numeric keys into a JSON array
            if (!k.startsWith("i")) continue
            val id = k.substring(1).toLongOrNull() ?: continue
            val o = remoteJson.optJSONObject(k) ?: continue
            if (o.optBoolean("d", false)) { remote[id] = null; continue }
            val item = runCatching { matItemFromJson(id, o) }.getOrNull() ?: continue
            remote[id] = item
        }
        val remoteMeta = if (metaJson.length() == 0) null else Meta.parse(metaJson)

        val b = bases[code]
        val localMap = local.items.associateBy { it.id }
        val localIds = localMap.keys
        val orderedIds = ArrayList<Long>(local.items.map { it.id })
        val extra = HashSet<Long>(remote.keys)
        b?.let { extra.addAll(it.items.keys) }
        orderedIds.addAll(extra.filter { it !in localIds }.sorted())

        val result = LinkedHashMap<Long, MatItem>()
        val toPush = ArrayList<Pair<Long, MatItem?>>()
        for (id in orderedIds) {
            val l = localMap[id]; val r = remote[id]; val base = b?.items?.get(id)
            val hasRemote = remote.containsKey(id)
            if (b == null) {
                // first contact: whatever the server has wins; our extras are uploaded
                if (hasRemote) { if (r != null) result[id] = r }
                else if (l != null) { result[id] = l; toPush += id to l }
            } else if (l != base) {
                if (l != null) { result[id] = l; if (l != r) toPush += id to l }
                else if (r != null) toPush += id to null          // we deleted it
            } else {
                if (r != null) result[id] = r
                else if (!hasRemote && l != null) { result[id] = l; toPush += id to l } // server lost it
            }
        }

        // images first, so other people never see a piece without its picture
        for (item in result.values) {
            if (!item.key.startsWith("img:")) continue
            val name = item.key.removePrefix("img:").removeSuffix(".png")
            val has = StickerImages.exists(item.key)
            val remoteHas = remoteImgs.has(name)
            if (has && !remoteHas) {
                val bytes = StickerImages.bytesForUpload(item.key)
                if (bytes != null) put("mats/$code/imgs/$name", JSONObject.quote(Base64.encodeToString(bytes, Base64.NO_WRAP)))
            } else if (!has && remoteHas) {
                val t = http("GET", "mats/$code/imgs/$name")
                val b64 = t?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }
                if (b64 != null) runCatching { StickerImages.saveShared(item.key, Base64.decode(b64, Base64.DEFAULT)) }
            }
        }

        val failed = HashSet<Long>()
        for ((id, item) in toPush) {
            val ok = put("mats/$code/items/i$id", item?.toJson()?.toString() ?: "{\"d\":true}")
            if (!ok) failed += id
        }

        // meta
        val lm = Meta.of(local)
        var outMeta = lm
        var metaFailed = false
        if (b == null) {
            if (remoteMeta != null) outMeta = remoteMeta
            else if (!put("mats/$code/meta", lm.toJson().toString())) metaFailed = true
        } else if (lm != b.meta) {
            if (!put("mats/$code/meta", lm.toJson().toString())) metaFailed = true
        } else if (remoteMeta != null) outMeta = remoteMeta

        if (b == null && (failed.isNotEmpty() || metaFailed)) return null

        val newBase = HashMap<Long, MatItem>()
        for ((id, item) in result) {
            if (id in failed) b?.items?.get(id)?.let { newBase[id] = it } else newBase[id] = item
        }
        bases[code] = Base(newBase, if (metaFailed) (b?.meta ?: outMeta) else outMeta)

        // membership heartbeat + count (not every cycle)
        val n = (syncCount[code] ?: 0) + 1
        syncCount[code] = n
        if (n % 6 == 1) {
            touchMember(code)
            members(code)?.let { memberCache[code] = it.size }
        }
        return SyncOutcome(result.values.toList(), outMeta, memberCache[code] ?: 1)
    }

    fun memberCount(code: String): Int = memberCache[code] ?: 1

    /** Pull the latest of every collective mat (used when the Friends tab opens). */
    fun syncAllLocal(): Boolean {
        var any = false
        for (m in Repo.all()) {
            if (!m.collective) continue
            val out = sync(m) ?: continue
            val merged = out.meta.applyTo(m).copy(items = out.items)
            if (merged.items != m.items || Meta.of(merged) != Meta.of(m)) {
                Repo.save(merged.copy(updated = System.currentTimeMillis())); any = true
            }
        }
        return any
    }
}
