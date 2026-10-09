package com.carfinc.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.random.Random

/** Ransom-note style cut-out letter, drawn in a ~200 unit box centered on the origin. */
object LetterTile {
    private fun c(v: Long) = v.toInt()

    // background, foreground
    private val schemes = arrayOf(
        intArrayOf(c(0xFFFFFFFF), c(0xFF111111)),
        intArrayOf(c(0xFFF6EFDD), c(0xFFC8341F)),
        intArrayOf(c(0xFF111111), c(0xFFFFFFFF)),
        intArrayOf(c(0xFFD7263D), c(0xFFFFFFFF)),
        intArrayOf(c(0xFFFFD23F), c(0xFF1D3FBF)),
        intArrayOf(c(0xFF1D3FBF), c(0xFFFFD23F)),
        intArrayOf(c(0xFFF7B6D2), c(0xFF111111)),
        intArrayOf(c(0xFF2E8B57), c(0xFFF6EFDD)),
        intArrayOf(c(0xFFFF8A3D), c(0xFF111111)),
        intArrayOf(c(0xFFB9E4F5), c(0xFF6A2FA0))
    )

    private val faces: Array<Typeface> = arrayOf(
        Typeface.create("serif", Typeface.BOLD),
        Typeface.create("sans-serif-black", Typeface.NORMAL),
        Typeface.create("sans-serif-condensed", Typeface.BOLD),
        Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
        Typeface.create("serif", Typeface.BOLD_ITALIC),
        Typeface.create("casual", Typeface.NORMAL)
    )

    private val sunset = intArrayOf(c(0xFF2B1B6B), c(0xFFD6336C), c(0xFFFF9F43), c(0xFFFFE08A))

    private fun jagged(rnd: Random, w: Float, h: Float): Path {
        fun j(a: Float) = (rnd.nextFloat() - 0.5f) * 2f * a
        val hw = w / 2f; val hh = h / 2f
        return Path().apply {
            moveTo(-hw + j(7f), -hh + j(7f))
            lineTo(j(5f), -hh + j(4f))
            lineTo(hw + j(7f), -hh + j(7f))
            lineTo(hw + j(4f), j(5f))
            lineTo(hw + j(7f), hh + j(7f))
            lineTo(j(5f), hh + j(4f))
            lineTo(-hw + j(7f), hh + j(7f))
            lineTo(-hw + j(4f), j(5f))
            close()
        }
    }

    fun draw(canvas: Canvas, ch: Char, seed: Int, paperEdge: Boolean) {
        val rnd = Random(seed * 31 + ch.code)
        val w = 150f + rnd.nextFloat() * 35f
        val h = 165f + rnd.nextFloat() * 30f
        val path = jagged(rnd, w, h)
        val scheme = schemes[rnd.nextInt(schemes.size)]
        val face = faces[rnd.nextInt(faces.size)]
        val gradient = rnd.nextInt(6) == 0

        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // soft drop shadow
        canvas.save()
        canvas.translate(4f, 8f)
        p.style = Paint.Style.FILL
        p.color = Color.argb(if (paperEdge) 80 else 45, 0, 0, 0)
        canvas.drawPath(path, p)
        canvas.restore()

        // white paper border
        if (paperEdge) {
            p.style = Paint.Style.FILL_AND_STROKE
            p.strokeWidth = 14f
            p.strokeJoin = Paint.Join.ROUND
            p.color = Color.WHITE
            canvas.drawPath(path, p)
        }

        // tile body
        p.style = Paint.Style.FILL
        p.strokeWidth = 0f
        if (gradient) {
            p.shader = LinearGradient(0f, -h / 2, 0f, h / 2, sunset, null, Shader.TileMode.CLAMP)
        } else {
            p.color = scheme[0]
        }
        canvas.drawPath(path, p)
        p.shader = null

        // glyph
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.typeface = face
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = h * 0.84f
        tp.color = if (gradient) Color.WHITE else scheme[1]
        val fm = tp.fontMetrics
        canvas.drawText(ch.toString(), 0f, -(fm.ascent + fm.descent) / 2f, tp)
    }
}
