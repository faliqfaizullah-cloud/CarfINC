package com.carfinc.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.random.Random

/**
 * Paper-scrap notes (sticky notes, typed slips, black quote cards) with your own text.
 * Sticker id format:  `note:<style>:<text>`
 */
object NoteArt {
    private fun c(v: Long) = v.toInt()

    class Style(val name: String, val bg: Int, val ink: Int, val face: Typeface,
                val upper: Boolean, val tape: Boolean, val lines: Boolean)

    val styles: List<Style> = listOf(
        Style("Pink",   c(0xFFFF8FC4), c(0xFF2B1733), Typeface.create("casual", Typeface.BOLD), false, false, false),
        Style("Yellow", c(0xFFFFE066), c(0xFF2B2230), Typeface.create("casual", Typeface.BOLD), false, false, false),
        Style("Paper",  c(0xFFFFFFFF), c(0xFF111111), Typeface.create(Typeface.MONOSPACE, Typeface.BOLD), false, true, true),
        Style("Black",  c(0xFF141414), c(0xFFFFFFFF), Typeface.create("sans-serif-black", Typeface.NORMAL), true, false, false),
        Style("Blue",   c(0xFFA9D6FF), c(0xFF10265E), Typeface.create("sans-serif-condensed", Typeface.BOLD), true, true, false),
        Style("Kraft",  c(0xFFE7CFA2), c(0xFF4A2F14), Typeface.create("serif", Typeface.BOLD_ITALIC), false, true, false)
    )

    fun id(style: Int, text: String) = "note:$style:$text"
    fun isNote(id: String) = id.startsWith("note:")
    fun styleOf(id: String): Int = id.split(":", limit = 3).getOrNull(1)?.toIntOrNull()?.coerceIn(0, styles.lastIndex) ?: 0
    fun textOf(id: String): String = id.split(":", limit = 3).getOrNull(2) ?: ""

    private const val W = 200f
    private const val H = 160f
    private val layoutCache = object : LinkedHashMap<String, StaticLayout>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, StaticLayout>?) = size > 40
    }

    @Synchronized
    private fun layoutFor(id: String, s: Style): StaticLayout = layoutCache.getOrPut(id) {
        var text = textOf(id).ifBlank { "..." }
        if (s.upper) text = text.uppercase()
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.typeface = s.face
        tp.color = s.ink
        val maxW = (W - 30f).toInt()
        val maxH = H - 34f
        var size = 44f
        var layout: StaticLayout
        while (true) {
            tp.textSize = size
            layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, maxW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 0.95f).build()
            if (layout.height <= maxH || size <= 13f) break
            size -= 2f
        }
        layout
    }

    private fun torn(rnd: Random): Path {
        fun j(a: Float) = (rnd.nextFloat() - 0.5f) * 2f * a
        val hw = W / 2; val hh = H / 2
        return Path().apply {
            moveTo(-hw + j(5f), -hh + j(5f))
            var x = -hw
            while (x < hw) { x += 28f; lineTo(x.coerceAtMost(hw) + j(3f), -hh + j(4f)) }
            lineTo(hw + j(5f), -hh / 3 + j(4f)); lineTo(hw + j(4f), hh / 3 + j(4f))
            lineTo(hw + j(5f), hh + j(5f))
            x = hw
            while (x > -hw) { x -= 28f; lineTo(x.coerceAtLeast(-hw) + j(3f), hh + j(4f)) }
            lineTo(-hw + j(4f), hh / 3 + j(4f)); lineTo(-hw + j(5f), -hh / 3 + j(4f))
            close()
        }
    }

    fun draw(canvas: Canvas, id: String, paperEdge: Boolean) {
        val s = styles[styleOf(id)]
        val rnd = Random(id.hashCode())
        val path = torn(rnd)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // shadow
        canvas.save(); canvas.translate(4f, 8f)
        p.style = Paint.Style.FILL
        p.color = Color.argb(if (paperEdge) 80 else 45, 0, 0, 0)
        canvas.drawPath(path, p)
        canvas.restore()

        // white paper border
        if (paperEdge) {
            p.style = Paint.Style.FILL_AND_STROKE; p.strokeWidth = 14f; p.strokeJoin = Paint.Join.ROUND
            p.color = Color.WHITE
            canvas.drawPath(path, p)
        }

        // body
        p.style = Paint.Style.FILL; p.strokeWidth = 0f; p.color = s.bg
        canvas.drawPath(path, p)

        // ruled lines on the typed slip
        if (s.lines) {
            p.color = Color.argb(40, 0, 60, 160); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
            var y = -H / 2 + 34f
            while (y < H / 2 - 8f) { canvas.drawLine(-W / 2 + 12f, y, W / 2 - 12f, y, p); y += 22f }
            p.style = Paint.Style.FILL
        }

        // text, vertically centered
        val l = layoutFor(id, s)
        canvas.save()
        canvas.translate(-l.width / 2f, -l.height / 2f + 4f)
        l.draw(canvas)
        canvas.restore()

        // translucent tape
        if (s.tape) {
            canvas.save()
            canvas.translate(0f, -H / 2 + 2f)
            canvas.rotate(if (rnd.nextBoolean()) -4f else 5f)
            p.color = Color.argb(150, 255, 244, 190)
            canvas.drawRect(RectF(-30f, -12f, 30f, 12f), p)
            canvas.restore()
        }
    }

    val presets: List<String> = listOf(
        id(0, "good vibes only"), id(1, "do not\ndisturb"), id(2, "call mom"),
        id(3, "steal like an artist"), id(4, "let it\nbe"), id(5, "made with love"),
        id(0, "mental breakdown later"), id(3, "stay\ncurious")
    )
}
