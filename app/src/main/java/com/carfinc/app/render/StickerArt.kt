package com.carfinc.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Original, procedurally drawn cut-out stickers. Each fits a 200x200 box centered on the origin. */
object StickerArt {

    val categories: LinkedHashMap<String, List<String>> = linkedMapOf(
        "Dynamic" to listOf("dynamic_bolt", "dynamic_sparkle", "dynamic_burst", "dynamic_ring",
            "dynamic_planet", "dynamic_arrow", "dynamic_diamond", "dynamic_wave"),
        "Cute" to listOf("cute_star", "cute_heart", "cute_moon", "cute_cat",
            "cute_ghost", "cute_cloud", "cute_bear", "cute_smile"),
        "Flowers" to listOf("flowers_daisy", "flowers_tulip", "flowers_clover", "flowers_sunflower",
            "flowers_blossom", "flowers_leaf", "flowers_cactus", "flowers_mushroom"),
        "Food" to listOf("food_cherry", "food_strawberry", "food_lemon", "food_donut",
            "food_pizza", "food_apple", "food_avocado", "food_icecream")
    )

    private fun c(v: Long) = v.toInt()

    private class Art(val cv: Canvas, val outline: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun prep(color: Int, stroke: Boolean, width: Float) {
            p.shader = null
            p.color = if (outline) Color.WHITE else color
            if (stroke) {
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeJoin = Paint.Join.ROUND
                p.strokeWidth = if (outline) width + 16f else width
            } else if (outline) {
                p.style = Paint.Style.FILL_AND_STROKE
                p.strokeJoin = Paint.Join.ROUND
                p.strokeWidth = 16f
            } else {
                p.style = Paint.Style.FILL
                p.strokeWidth = 0f
            }
        }

        fun fill(path: Path, color: Int) { prep(color, false, 0f); cv.drawPath(path, p) }
        fun stroke(path: Path, width: Float, color: Int) { prep(color, true, width); cv.drawPath(path, p) }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) {
            prep(color, true, width); cv.drawLine(x1, y1, x2, y2, p)
        }
        fun circle(cx: Float, cy: Float, r: Float, color: Int) =
            fill(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }, color)
        fun oval(l: Float, t: Float, r: Float, b: Float, color: Int) =
            fill(Path().apply { addOval(RectF(l, t, r, b), Path.Direction.CW) }, color)
        fun rrect(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) =
            fill(Path().apply { addRoundRect(RectF(l, t, r, b), rad, rad, Path.Direction.CW) }, color)
        fun poly(color: Int, vararg pts: Float) {
            val path = Path()
            path.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
            path.close()
            fill(path, color)
        }
        fun star(cx: Float, cy: Float, outer: Float, inner: Float, n: Int, color: Int, startDeg: Float = -90f) {
            val path = Path()
            val steps = n * 2
            for (i in 0 until steps) {
                val r = if (i % 2 == 0) outer else inner
                val a = Math.toRadians((startDeg + i * 360.0 / steps))
                val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            fill(path, color)
        }
        fun rotated(deg: Float, block: () -> Unit) { cv.save(); cv.rotate(deg); block(); cv.restore() }
    }

    private val DK = c(0xFF2B2230)

    private fun body(a: Art, id: String) {
        with(a) {
            when (id) {
                // ---------- Cute ----------
                "cute_star" -> {
                    star(0f, 2f, 92f, 44f, 5, c(0xFFE53935)); star(0f, 2f, 64f, 31f, 5, c(0xFFFFC93C))
                    circle(-14f, 0f, 5f, DK); circle(14f, 0f, 5f, DK)
                    stroke(Path().apply { moveTo(-13f, 14f); quadTo(0f, 28f, 13f, 14f) }, 5f, DK)
                }
                "cute_heart" -> {
                    fill(Path().apply {
                        moveTo(0f, 85f); cubicTo(-120f, 0f, -70f, -90f, 0f, -35f)
                        cubicTo(70f, -90f, 120f, 0f, 0f, 85f); close()
                    }, c(0xFFE63960))
                    oval(-62f, -52f, -30f, -28f, c(0x88FFFFFF))
                }
                "cute_moon" -> {
                    val m = Path().apply { addCircle(0f, 0f, 88f, Path.Direction.CW) }
                    m.op(Path().apply { addCircle(40f, -20f, 72f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
                    fill(m, c(0xFFFFD35C))
                    star(56f, 42f, 22f, 9f, 4, c(0xFFFFE9A8))
                    circle(-52f, 8f, 6f, DK)
                }
                "cute_cat" -> {
                    poly(c(0xFFF4A340), -80f, -28f, -72f, -94f, -20f, -62f)
                    poly(c(0xFFF4A340), 20f, -62f, 72f, -94f, 80f, -28f)
                    poly(c(0xFFF7B6C8), -66f, -42f, -62f, -78f, -36f, -60f)
                    poly(c(0xFFF7B6C8), 36f, -60f, 62f, -78f, 66f, -42f)
                    oval(-84f, -64f, 84f, 80f, c(0xFFF4A340))
                    oval(-40f, 0f, 40f, 64f, c(0xFFFFE2B8))
                    circle(-30f, -10f, 8f, DK); circle(30f, -10f, 8f, DK)
                    poly(c(0xFFE0607E), -8f, 8f, 8f, 8f, 0f, 18f)
                    stroke(Path().apply { moveTo(0f, 18f); quadTo(-10f, 30f, -20f, 24f); moveTo(0f, 18f); quadTo(10f, 30f, 20f, 24f) }, 4f, DK)
                    line(-60f, 10f, -92f, 4f, 3f, DK); line(-60f, 22f, -92f, 28f, 3f, DK)
                    line(60f, 10f, 92f, 4f, 3f, DK); line(60f, 22f, 92f, 28f, 3f, DK)
                }
                "cute_ghost" -> {
                    fill(Path().apply {
                        addArc(RectF(-70f, -85f, 70f, 55f), 180f, 180f)
                        lineTo(70f, 74f); quadTo(52f, 96f, 35f, 74f); quadTo(17f, 52f, 0f, 74f)
                        quadTo(-17f, 96f, -35f, 74f); quadTo(-52f, 52f, -70f, 74f); close()
                    }, c(0xFFF3EEFF))
                    oval(-34f, -30f, -16f, 0f, DK); oval(16f, -30f, 34f, 0f, DK)
                    circle(-44f, 14f, 9f, c(0xFFF7B6C8)); circle(44f, 14f, 9f, c(0xFFF7B6C8))
                    oval(-8f, 8f, 8f, 28f, c(0xFF7B6AA8))
                }
                "cute_cloud" -> {
                    val col = c(0xFF9FD3F5)
                    circle(-38f, 12f, 40f, col); circle(8f, -14f, 52f, col); circle(54f, 14f, 38f, col)
                    rrect(-76f, 10f, 90f, 54f, 22f, col)
                    oval(-20f, -48f, 6f, -30f, c(0x99FFFFFF))
                }
                "cute_bear" -> {
                    val br = c(0xFF8D5A3B)
                    circle(-60f, -62f, 26f, br); circle(60f, -62f, 26f, br)
                    circle(-60f, -62f, 13f, c(0xFFD9A47F)); circle(60f, -62f, 13f, c(0xFFD9A47F))
                    circle(0f, 6f, 80f, br)
                    oval(-34f, 4f, 34f, 62f, c(0xFFE2B58F))
                    circle(-30f, -14f, 7f, DK); circle(30f, -14f, 7f, DK)
                    oval(-11f, 14f, 11f, 30f, DK)
                }
                "cute_smile" -> {
                    circle(0f, 0f, 88f, c(0xFFFFD23F))
                    oval(-38f, -38f, -20f, -4f, DK); oval(20f, -38f, 38f, -4f, DK)
                    stroke(Path().apply { moveTo(-42f, 18f); quadTo(0f, 66f, 42f, 18f) }, 9f, DK)
                    circle(-56f, 22f, 10f, c(0x66FF6B6B)); circle(56f, 22f, 10f, c(0x66FF6B6B))
                }
                // ---------- Dynamic ----------
                "dynamic_bolt" -> {
                    poly(c(0xFFFFC400), 18f, -95f, -58f, 8f, -8f, 8f, -30f, 95f, 58f, -22f, 8f, -22f)
                    poly(c(0xFFFF9F1C), 18f, -95f, 8f, -22f, 58f, -22f)
                }
                "dynamic_sparkle" -> {
                    star(0f, 0f, 96f, 16f, 4, c(0xFFB39DFF)); star(0f, 0f, 52f, 9f, 4, c(0xFFFFFFFF))
                    star(66f, -62f, 24f, 6f, 4, c(0xFFFF8FB8)); star(-64f, 62f, 20f, 5f, 4, c(0xFFFFD35C))
                }
                "dynamic_burst" -> {
                    star(0f, 0f, 96f, 68f, 14, c(0xFFFF7A1A)); circle(0f, 0f, 54f, c(0xFFFFD23F))
                    star(0f, 0f, 32f, 13f, 5, c(0xFFFF7A1A))
                }
                "dynamic_ring" -> {
                    fill(Path().apply {
                        fillType = Path.FillType.EVEN_ODD
                        addCircle(0f, 0f, 88f, Path.Direction.CW); addCircle(0f, 0f, 48f, Path.Direction.CW)
                    }, c(0xFF2F6BFF))
                    stroke(Path().apply { addArc(RectF(-70f, -70f, 70f, 70f), 200f, 60f) }, 8f, c(0xAAFFFFFF))
                }
                "dynamic_planet" -> {
                    rotated(-22f) { stroke(Path().apply { addOval(RectF(-100f, -28f, 100f, 28f), Path.Direction.CW) }, 11f, c(0xFFFFC857)) }
                    circle(0f, 0f, 58f, c(0xFF7B3FE4))
                    circle(-18f, -16f, 10f, c(0xFF6430C0)); circle(20f, 20f, 14f, c(0xFF6430C0))
                    rotated(-22f) {
                        cv.save(); cv.clipRect(-110f, 0f, 110f, 40f)
                        stroke(Path().apply { addOval(RectF(-100f, -28f, 100f, 28f), Path.Direction.CW) }, 11f, c(0xFFFFC857))
                        cv.restore()
                    }
                }
                "dynamic_arrow" -> poly(c(0xFFFF5DA2), -90f, -22f, 10f, -22f, 10f, -62f, 90f, 0f, 10f, 62f, 10f, 22f, -90f, 22f)
                "dynamic_diamond" -> {
                    poly(c(0xFF23C4B8), -60f, -60f, 60f, -60f, 92f, -14f, 0f, 96f, -92f, -14f)
                    poly(c(0xFF7BE8DE), -60f, -60f, 60f, -60f, 34f, -14f, -34f, -14f)
                    poly(c(0xFF139A91), 0f, 96f, 34f, -14f, 92f, -14f)
                }
                "dynamic_wave" -> stroke(Path().apply {
                    moveTo(-86f, 22f); lineTo(-46f, -30f); lineTo(-6f, 22f); lineTo(34f, -30f); lineTo(74f, 22f)
                }, 26f, c(0xFF2F6BFF))
                // ---------- Flowers ----------
                "flowers_daisy" -> {
                    for (i in 0 until 8) rotated(i * 45f) {
                        oval(-17f, -96f, 17f, -26f, c(0xFFFFF7FA)); oval(-5f, -88f, 5f, -44f, c(0xFFEBDDF0))
                    }
                    circle(0f, 0f, 28f, c(0xFFFFC21A))
                }
                "flowers_tulip" -> {
                    line(0f, 10f, 0f, 94f, 10f, c(0xFF2E9E4F))
                    fill(Path().apply { moveTo(0f, 94f); quadTo(-62f, 62f, -56f, 28f); quadTo(-8f, 44f, 0f, 94f); close() }, c(0xFF2E9E4F))
                    fill(Path().apply {
                        moveTo(-52f, -72f); quadTo(-64f, 14f, 0f, 34f); quadTo(64f, 14f, 52f, -72f)
                        lineTo(26f, -46f); lineTo(0f, -80f); lineTo(-26f, -46f); close()
                    }, c(0xFFE63960))
                }
                "flowers_clover" -> {
                    val g = c(0xFF2E9E4F); val d = c(0xFF1E7A3A)
                    line(0f, 30f, 24f, 94f, 10f, d)
                    circle(-34f, -34f, 38f, g); circle(34f, -34f, 38f, g); circle(-34f, 34f, 38f, g); circle(34f, 34f, 38f, g)
                    line(0f, 0f, -44f, -44f, 4f, d); line(0f, 0f, 44f, -44f, 4f, d)
                    line(0f, 0f, -44f, 44f, 4f, d); line(0f, 0f, 44f, 44f, 4f, d)
                }
                "flowers_sunflower" -> {
                    for (i in 0 until 12) rotated(i * 30f) { oval(-15f, -98f, 15f, -42f, c(0xFFFFC21A)) }
                    circle(0f, 0f, 48f, c(0xFF5A3417))
                    for (i in 0 until 8) { val an = Math.toRadians(i * 45.0); circle((24 * cos(an)).toFloat(), (24 * sin(an)).toFloat(), 4f, c(0xFF8A5A2B)) }
                }
                "flowers_blossom" -> {
                    for (i in 0 until 5) rotated(i * 72f) { circle(0f, -52f, 36f, c(0xFFFFA8C8)) }
                    circle(0f, 0f, 24f, c(0xFFFFE066))
                }
                "flowers_leaf" -> rotated(-35f) {
                    oval(-46f, -96f, 46f, 96f, c(0xFF3FA34D))
                    line(0f, -88f, 0f, 90f, 5f, c(0xFF1E7A3A))
                    line(0f, -30f, 28f, -52f, 3f, c(0xFF1E7A3A)); line(0f, -30f, -28f, -52f, 3f, c(0xFF1E7A3A))
                    line(0f, 20f, 28f, -2f, 3f, c(0xFF1E7A3A)); line(0f, 20f, -28f, -2f, 3f, c(0xFF1E7A3A))
                }
                "flowers_cactus" -> {
                    val g = c(0xFF3FA34D)
                    rrect(-62f, -40f, -22f, -5f, 16f, g); rrect(-62f, -72f, -38f, -5f, 12f, g)
                    rrect(22f, -12f, 62f, 24f, 16f, g); rrect(38f, -48f, 62f, 24f, 12f, g)
                    rrect(-24f, -84f, 24f, 62f, 24f, g)
                    poly(c(0xFFD2693E), -50f, 50f, 50f, 50f, 40f, 96f, -40f, 96f)
                    rrect(-56f, 42f, 56f, 62f, 6f, c(0xFFB5532F))
                    circle(0f, -84f, 7f, c(0xFFFF8FB8))
                }
                "flowers_mushroom" -> {
                    rrect(-28f, 20f, 28f, 92f, 14f, c(0xFFF6E7C8))
                    fill(Path().apply { addArc(RectF(-92f, -88f, 92f, 56f), 180f, 180f); close() }, c(0xFFE5383B))
                    circle(-40f, -30f, 12f, Color.WHITE); circle(10f, -52f, 14f, Color.WHITE); circle(52f, -18f, 11f, Color.WHITE)
                }
                // ---------- Food ----------
                "food_cherry" -> {
                    val g = c(0xFF2E9E4F)
                    stroke(Path().apply { moveTo(-30f, 48f); quadTo(-10f, -30f, 26f, -86f) }, 6f, g)
                    stroke(Path().apply { moveTo(42f, 56f); quadTo(36f, -20f, 26f, -86f) }, 6f, g)
                    rotated(-30f) { oval(24f, -98f, 82f, -76f, g) }
                    circle(-36f, 56f, 38f, c(0xFFD7263D)); circle(42f, 60f, 36f, c(0xFFD7263D))
                    circle(-48f, 44f, 8f, c(0x88FFFFFF)); circle(32f, 48f, 8f, c(0x88FFFFFF))
                }
                "food_strawberry" -> {
                    fill(Path().apply {
                        moveTo(0f, 94f); cubicTo(-112f, 30f, -92f, -62f, 0f, -52f)
                        cubicTo(92f, -62f, 112f, 30f, 0f, 94f); close()
                    }, c(0xFFE63946))
                    val seeds = floatArrayOf(-34f, -20f, 0f, -26f, 34f, -20f, -50f, 10f, -18f, 8f, 18f, 8f, 50f, 10f, -30f, 40f, 0f, 36f, 30f, 40f, 0f, 66f)
                    var i = 0
                    while (i < seeds.size) { oval(seeds[i] - 5f, seeds[i + 1] - 7f, seeds[i] + 5f, seeds[i + 1] + 7f, c(0xFFFFE08A)); i += 2 }
                    star(0f, -56f, 52f, 20f, 5, c(0xFF3FA34D))
                }
                "food_lemon" -> rotated(-25f) {
                    oval(-88f, -56f, 88f, 56f, c(0xFFFFE04A))
                    circle(-90f, 0f, 12f, c(0xFFFFE04A)); circle(90f, 0f, 12f, c(0xFFFFE04A))
                    oval(-60f, -40f, -20f, -26f, c(0x99FFFFFF))
                    rotated(20f) { oval(10f, -86f, 70f, -62f, c(0xFF3FA34D)) }
                }
                "food_donut" -> {
                    fill(Path().apply {
                        fillType = Path.FillType.EVEN_ODD
                        addCircle(0f, 0f, 90f, Path.Direction.CW); addCircle(0f, 0f, 26f, Path.Direction.CW)
                    }, c(0xFFD9A066))
                    fill(Path().apply {
                        fillType = Path.FillType.EVEN_ODD
                        addCircle(0f, -4f, 78f, Path.Direction.CW); addCircle(0f, 0f, 26f, Path.Direction.CW)
                    }, c(0xFFFF8FB8))
                    val cols = intArrayOf(c(0xFFFFFFFF), c(0xFF2F6BFF), c(0xFFFFD23F), c(0xFF2E9E4F))
                    for (i in 0 until 12) {
                        val an = Math.toRadians(i * 30.0 + 8)
                        val r = if (i % 2 == 0) 52f else 64f
                        val x = (r * cos(an)).toFloat(); val y = (r * sin(an)).toFloat() - 3f
                        line(x - 5f, y - 3f, x + 5f, y + 3f, 5f, cols[i % cols.size])
                    }
                }
                "food_pizza" -> {
                    poly(c(0xFFFFC94A), -76f, -70f, 76f, -70f, 0f, 96f)
                    rrect(-82f, -94f, 82f, -64f, 14f, c(0xFFD9923A))
                    circle(-26f, -34f, 16f, c(0xFFC0392B)); circle(30f, -30f, 15f, c(0xFFC0392B)); circle(2f, 20f, 16f, c(0xFFC0392B))
                    oval(-8f, 48f, 12f, 62f, c(0xFF2E9E4F))
                }
                "food_apple" -> {
                    circle(-24f, 10f, 58f, c(0xFFE5383B)); circle(24f, 10f, 58f, c(0xFFE5383B))
                    rrect(-30f, 30f, 30f, 66f, 20f, c(0xFFE5383B))
                    line(0f, -44f, 8f, -80f, 8f, c(0xFF6B4423))
                    rotated(25f) { oval(8f, -92f, 62f, -68f, c(0xFF3FA34D)) }
                    oval(-60f, -14f, -40f, 20f, c(0x66FFFFFF))
                }
                "food_avocado" -> {
                    val dark = c(0xFF3E7B34)
                    circle(0f, 48f, 62f, dark); circle(0f, -30f, 42f, dark)
                    poly(dark, -42f, -30f, 42f, -30f, 62f, 48f, -62f, 48f)
                    val light = c(0xFFD9E89A)
                    circle(0f, 48f, 50f, light); circle(0f, -30f, 30f, light)
                    poly(light, -30f, -30f, 30f, -30f, 50f, 48f, -50f, 48f)
                    circle(0f, 50f, 26f, c(0xFF8A5A2B)); circle(-8f, 42f, 7f, c(0x66FFFFFF))
                }
                "food_icecream" -> {
                    poly(c(0xFFE0A458), -48f, 0f, 48f, 0f, 0f, 98f)
                    line(-30f, 18f, 20f, 70f, 3f, c(0xFFB9803F)); line(30f, 18f, -20f, 70f, 3f, c(0xFFB9803F))
                    circle(0f, -22f, 52f, c(0xFFFF8FB8))
                    fill(Path().apply {
                        moveTo(-52f, -14f); quadTo(-40f, 20f, -26f, 4f); quadTo(-10f, 26f, 6f, 2f)
                        quadTo(26f, 24f, 34f, 4f); quadTo(46f, 18f, 52f, -14f); close()
                    }, c(0xFFFF8FB8))
                    circle(0f, -80f, 12f, c(0xFFD7263D))
                }
                else -> circle(0f, 0f, 70f, c(0xFFCCCCCC))
            }
        }
    }

    private fun drawImage(canvas: Canvas, bmp: Bitmap, paperEdge: Boolean) {
        val s = 190f / maxOf(bmp.width, bmp.height)
        val w = bmp.width * s; val h = bmp.height * s
        val r = RectF(-w / 2, -h / 2, w / 2, h / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(80, 0, 0, 0)
        canvas.drawRoundRect(RectF(r.left + 4, r.top + 8, r.right + 4, r.bottom + 8), 14f, 14f, p)
        if (paperEdge) {
            p.color = Color.WHITE
            canvas.drawRoundRect(RectF(r.left - 8, r.top - 8, r.right + 8, r.bottom + 8), 20f, 20f, p)
        }
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(r, 14f, 14f, Path.Direction.CW) })
        canvas.drawBitmap(bmp, null, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    }

    fun draw(canvas: Canvas, id: String, paperEdge: Boolean) {
        if (id.startsWith("img:")) {
            StickerImages.get(id)?.let { drawImage(canvas, it, paperEdge) }
            return
        }
        if (paperEdge) {
            // shadow, then white paper outline, then colour
            canvas.save(); canvas.translate(4f, 8f)
            val layerPaint = Paint().apply {
                alpha = 80
                colorFilter = android.graphics.PorterDuffColorFilter(Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN)
            }
            val rect = RectF(-140f, -140f, 140f, 140f)
            val sc = canvas.saveLayer(rect, layerPaint)
            body(Art(canvas, true), id)
            canvas.restoreToCount(sc)
            canvas.restore()
            body(Art(canvas, true), id)
        }
        body(Art(canvas, false), id)
    }
}
