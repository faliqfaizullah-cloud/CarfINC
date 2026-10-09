package com.carfinc.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.carfinc.app.data.Store
import java.io.File
import java.io.FileOutputStream

/**
 * User photos imported as "My stickers". Ids look like `img:cut_1712345.png`.
 * New imports are PERSON CUT-OUTS only (background removed, cropped to the person).
 * Older `img:<n>.png` files (whole photo) still render the legacy way.
 */
object StickerImages {
    private val cache = HashMap<String, Bitmap>()
    private val outlines = HashMap<String, Pair<Bitmap, Int>>()

    private fun dir(): File = File(Store.ctx.filesDir, "stickers").apply { mkdirs() }

    fun list(): List<String> =
        dir().listFiles { f -> f.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { "img:" + it.name } ?: emptyList()

    fun isCutout(id: String) = id.startsWith("img:cut_")

    @Synchronized
    fun get(id: String): Bitmap? {
        cache[id]?.let { return it }
        val f = File(dir(), id.removePrefix("img:"))
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        cache[id] = bmp
        return bmp
    }

    /** White die-cut silhouette (grown by `pad` px) for a cut-out sticker, built once. */
    @Synchronized
    fun outline(id: String, bmp: Bitmap): Pair<Bitmap, Int> =
        outlines.getOrPut(id) {
            val pad = (maxOf(bmp.width, bmp.height) * 0.035f).toInt().coerceAtLeast(6)
            PersonCutout.outline(bmp, pad) to pad
        }

    private fun decodeUpright(uri: Uri): Bitmap? {
        val cr = Store.ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = runCatching {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return raw
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    /**
     * Cuts the PERSON out of the picked photo and stores only that.
     * @return the sticker id, or null when no person was found (nothing is stored in that case,
     *         so the whole photo is never used as a sticker).
     */
    fun import(uri: Uri): String? = runCatching {
        val photo = decodeUpright(uri) ?: return null
        val person = PersonCutout.extract(photo) ?: return null
        val name = "cut_${System.currentTimeMillis()}.png"
        FileOutputStream(File(dir(), name)).use { person.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "img:$name"
    }.getOrNull()
}
