package com.carfinc.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.carfinc.app.model.Kind
import com.carfinc.app.model.Mat
import com.carfinc.app.model.MatItem

/**
 * Single renderer used by the editor, the gallery thumbnails, exports and the home-screen widget,
 * so the widget is always pixel-identical to what you designed.
 */
object MatRenderer {
    private const val CORNER = 56f

    fun toBitmap(mat: Mat, widthPx: Int): Bitmap {
        val h = (widthPx * mat.size.h / mat.size.w).toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(b), mat, widthPx.toFloat(), null)
        return b
    }

    fun itemScale(mat: Mat, i: MatItem): Float =
        if (i.kind == Kind.LETTER) i.scale * mat.letterScale else i.scale

    fun itemRot(mat: Mat, i: MatItem): Float =
        if (i.kind == Kind.LETTER) i.rot + i.jit * mat.chaos else i.rot

    /** Hit radius of an item in logical units. */
    fun radius(mat: Mat, i: MatItem): Float = 100f * itemScale(mat, i) * 0.92f

    fun draw(c: Canvas, mat: Mat, widthPx: Float, selectedId: Long?) {
        val s = widthPx / mat.size.w
        val w = mat.size.w; val h = mat.size.h
        c.save()
        c.scale(s, s)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w, h), CORNER, CORNER, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        drawBase(c, mat, w, h)
        for (item in mat.items) drawItem(c, mat, item)
        if (selectedId != null) mat.items.firstOrNull { it.id == selectedId }?.let { drawSelection(c, mat, it) }
        c.restore()
        c.restore()
    }

    private fun drawItem(c: Canvas, mat: Mat, i: MatItem) {
        c.save()
        c.translate(i.x, i.y)
        c.rotate(itemRot(mat, i))
        val sc = itemScale(mat, i)
        c.scale(sc, sc)
        if (i.kind == Kind.LETTER) LetterTile.draw(c, i.key[0], i.seed, mat.paperEdge)
        else StickerArt.draw(c, i.key, mat.paperEdge)
        c.restore()
    }

    private fun drawSelection(c: Canvas, mat: Mat, i: MatItem) {
        c.save()
        c.translate(i.x, i.y)
        c.rotate(itemRot(mat, i))
        val r = 100f * itemScale(mat, i) * 1.02f
        val box = RectF(-r, -r, r, r)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.color = Color.argb(140, 0, 0, 0); p.strokeWidth = 7f
        c.drawRoundRect(box, 18f, 18f, p)
        p.color = Color.WHITE; p.strokeWidth = 4f
        p.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
        c.drawRoundRect(box, 18f, 18f, p)
        p.pathEffect = null
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        for ((cx, cy) in listOf(-r to -r, r to -r, -r to r, r to r)) c.drawCircle(cx, cy, 9f, p)
        c.restore()
    }

    private fun drawBase(c: Canvas, mat: Mat, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = mat.color
        c.drawRect(0f, 0f, w, h, p)

        val light = ColorUtils.calculateLuminance(mat.color) > 0.55
        val ink = if (light) Color.BLACK else Color.WHITE

        // grid
        p.style = Paint.Style.STROKE
        p.color = ColorUtils.setAlphaComponent(ink, 26); p.strokeWidth = 1.6f
        var g = 0f
        while (g <= w) { c.drawLine(g, 0f, g, h, p); g += 40f }
        g = 0f
        while (g <= h) { c.drawLine(0f, g, w, g, p); g += 40f }
        p.color = ColorUtils.setAlphaComponent(ink, 70); p.strokeWidth = 2.6f
        g = 0f
        while (g <= w) { c.drawLine(g, 0f, g, h, p); g += 200f }
        g = 0f
        while (g <= h) { c.drawLine(0f, g, w, g, p); g += 200f }

        // cutting-mat guide arcs + diagonals
        p.color = ColorUtils.setAlphaComponent(ink, 60); p.strokeWidth = 2f
        p.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        c.drawCircle(0f, h, w * 0.55f, p)
        c.drawCircle(0f, h, w * 0.85f, p)
        p.pathEffect = null
        c.drawLine(0f, h, minOf(w, h * 1.0f), 0f, p)
        c.drawLine(0f, h, w, h - w * 0.58f, p)

        // ruler ticks and numbers
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.color = ColorUtils.setAlphaComponent(ink, 150); tp.textSize = 17f
        tp.textAlign = Paint.Align.CENTER
        p.color = ColorUtils.setAlphaComponent(ink, 120); p.strokeWidth = 2f
        var x = 20f; var n = 0
        while (x < w) {
            val major = n % 5 == 4
            c.drawLine(x, 0f, x, if (major) 16f else 8f, p)
            if (major) c.drawText(((n + 1) / 5 * 10).toString(), x, 36f, tp)
            x += 20f; n++
        }
        var y = 20f; n = 0
        tp.textAlign = Paint.Align.LEFT
        while (y < h) {
            val major = n % 5 == 4
            c.drawLine(0f, y, if (major) 16f else 8f, y, p)
            if (major) c.drawText(((n + 1) / 5 * 10).toString(), 22f, y + 6f, tp)
            y += 20f; n++
        }

        // soft sheen + inner border
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(w * 0.3f, h * 0.15f, w * 0.95f,
            intArrayOf(0x30FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.color = ColorUtils.setAlphaComponent(Color.WHITE, 40); p.strokeWidth = 5f
        c.drawRoundRect(RectF(6f, 6f, w - 6f, h - 6f), CORNER - 6f, CORNER - 6f, p)
    }
}
