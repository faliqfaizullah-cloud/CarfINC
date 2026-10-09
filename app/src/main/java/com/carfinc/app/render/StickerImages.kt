package com.carfinc.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.carfinc.app.data.Store
import java.io.File
import java.io.FileOutputStream

/** User photos imported as "My stickers". Ids look like `img:1712345.png`. */
object StickerImages {
    private val cache = HashMap<String, Bitmap>()

    private fun dir(): File = File(Store.ctx.filesDir, "stickers").apply { mkdirs() }

    fun list(): List<String> =
        dir().listFiles { f -> f.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { "img:" + it.name } ?: emptyList()

    @Synchronized
    fun get(id: String): Bitmap? {
        cache[id]?.let { return it }
        val f = File(dir(), id.removePrefix("img:"))
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        cache[id] = bmp
        return bmp
    }

    /** Decode a picked image down to ≤ 512px and store it. */
    fun import(uri: Uri): String? = runCatching {
        val cr = Store.ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val s = 512f / maxOf(raw.width, raw.height)
        val bmp = if (s < 1f) Bitmap.createScaledBitmap(raw, (raw.width * s).toInt(), (raw.height * s).toInt(), true) else raw
        val name = "${System.currentTimeMillis()}.png"
        FileOutputStream(File(dir(), name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "img:$name"
    }.getOrNull()
}
