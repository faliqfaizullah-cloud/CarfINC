package com.carfinc.app.util

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import com.carfinc.app.model.Mat
import com.carfinc.app.render.MatRenderer
import java.io.File
import java.io.FileOutputStream

object Export {
    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "mat" }

    fun render(mat: Mat, widthPx: Int = 2048): Bitmap = MatRenderer.toBitmap(mat, widthPx)

    /** Opens the system share sheet with the mat as a PNG. */
    fun share(ctx: Context, mat: Mat) {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "${safe(mat.name)}.png")
        FileOutputStream(f).use { render(mat).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, null))
    }

    /** Saves to Pictures/Carf.INC in the gallery. No storage permission needed (Android 10+). */
    fun saveToPhotos(ctx: Context, mat: Mat): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${safe(mat.name)}_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Carf.INC")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { render(mat).compress(Bitmap.CompressFormat.PNG, 100, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    }.getOrDefault(false)

    /** Phone-shaped composite: the mat centered on a deep tint of its own color. */
    fun wallpaperBitmap(ctx: Context, mat: Mat): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val top = ColorUtils.blendARGB(mat.color, Color.BLACK, 0.25f)
        val bottom = ColorUtils.blendARGB(mat.color, Color.BLACK, 0.55f)
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val mw = (w * 0.9f).toInt()
        val bmp = MatRenderer.toBitmap(mat, mw)
        cv.drawBitmap(bmp, (w - bmp.width) / 2f, (h - bmp.height) / 2f - h * 0.04f, null)
        return out
    }

    fun setWallpaper(ctx: Context, bmp: Bitmap): Boolean = runCatching {
        WallpaperManager.getInstance(ctx).setBitmap(bmp); true
    }.getOrDefault(false)
}
