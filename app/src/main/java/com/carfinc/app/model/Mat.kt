package com.carfinc.app.model

import org.json.JSONArray
import org.json.JSONObject

/** Mat shapes. Logical units are 1000 wide; the renderer scales to any pixel size. */
enum class MatSize(val w: Float, val h: Float, val label: String) {
    MEDIUM(1000f, 1000f, "Medium"),
    LARGE(1000f, 500f, "Large")
}

enum class Kind { LETTER, STICKER }

/**
 * One piece on the mat.
 * @param key  the character for LETTER, or sticker id for STICKER
 * @param rot  user rotation in degrees
 * @param jit  random base tilt for letters (scaled by the mat "chaos" slider)
 */
data class MatItem(
    val id: Long,
    val kind: Kind,
    val key: String,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rot: Float,
    val jit: Float,
    val seed: Int
)

data class Mat(
    val id: String,
    val name: String,
    val color: Int,
    val size: MatSize,
    val items: List<MatItem>,
    val paperEdge: Boolean = true,
    val letterScale: Float = 1f,
    val chaos: Float = 1f,
    val updated: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("color", color)
        put("size", size.name); put("paperEdge", paperEdge)
        put("letterScale", letterScale.toDouble()); put("chaos", chaos.toDouble())
        put("updated", updated)
        put("items", JSONArray().also { arr ->
            items.forEach { i ->
                arr.put(JSONObject().apply {
                    put("id", i.id); put("kind", i.kind.name); put("key", i.key)
                    put("x", i.x.toDouble()); put("y", i.y.toDouble())
                    put("scale", i.scale.toDouble()); put("rot", i.rot.toDouble())
                    put("jit", i.jit.toDouble()); put("seed", i.seed)
                })
            }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): Mat {
            val arr = o.getJSONArray("items")
            val items = (0 until arr.length()).map { n ->
                val i = arr.getJSONObject(n)
                MatItem(
                    id = i.getLong("id"),
                    kind = Kind.valueOf(i.getString("kind")),
                    key = i.getString("key"),
                    x = i.getDouble("x").toFloat(), y = i.getDouble("y").toFloat(),
                    scale = i.getDouble("scale").toFloat(), rot = i.getDouble("rot").toFloat(),
                    jit = i.optDouble("jit", 0.0).toFloat(), seed = i.optInt("seed", 0)
                )
            }
            return Mat(
                id = o.getString("id"), name = o.getString("name"), color = o.getInt("color"),
                size = MatSize.valueOf(o.getString("size")), items = items,
                paperEdge = o.optBoolean("paperEdge", true),
                letterScale = o.optDouble("letterScale", 1.0).toFloat(),
                chaos = o.optDouble("chaos", 1.0).toFloat(),
                updated = o.optLong("updated", System.currentTimeMillis())
            )
        }
    }
}
