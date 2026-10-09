package com.carfinc.app.model

import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

object Palette {
    val swatches: List<Int> = listOf(
        0xFF14402B, 0xFF2E8B57, 0xFF2447C9, 0xFF4A5560, 0xFF6A2FA0, 0xFF1A1A1A,
        0xFFC8664A, 0xFFD9B26F, 0xFFF3C6D8, 0xFFD4CBF5, 0xFFF1EBD4
    ).map { it.toInt() }
    val default: Int get() = swatches[0]
}

object MatFactory {

    /** Spell [name] out as ransom-note letters, centered on the mat. */
    fun letters(name: String, size: MatSize, rnd: Random, startId: Long = 1L): List<MatItem> {
        val chars = name.filter { !it.isWhitespace() }.uppercase().take(14)
        val n = chars.length
        if (n == 0) return emptyList()
        val rows = when (size) {
            MatSize.MEDIUM -> if (n <= 4) 1 else if (n <= 8) 2 else 3
            MatSize.LARGE -> if (n <= 7) 1 else 2
        }
        val cols = ceil(n / rows.toFloat()).toInt()
        val tile = min(min(900f / cols, size.h * 0.88f / rows), 260f)
        val out = ArrayList<MatItem>()
        var idx = 0
        for (r in 0 until rows) {
            val count = min(cols, n - idx)
            if (count <= 0) break
            val y0 = size.h / 2f + (r - (rows - 1) / 2f) * tile * 0.96f
            for (c in 0 until count) {
                val x = size.w / 2f + (c - (count - 1) / 2f) * tile * 0.96f
                out += MatItem(
                    id = startId + idx, kind = Kind.LETTER, key = chars[idx].toString(),
                    x = x + (rnd.nextFloat() - 0.5f) * tile * 0.1f,
                    y = y0 + (rnd.nextFloat() - 0.5f) * tile * 0.1f,
                    scale = tile / 200f, rot = 0f,
                    jit = (rnd.nextFloat() * 2f - 1f) * 11f,
                    seed = rnd.nextInt(1_000_000)
                )
                idx++
            }
        }
        return out
    }

    fun create(name: String, color: Int = Palette.default, size: MatSize = MatSize.MEDIUM,
               rnd: Random = Random(System.nanoTime())): Mat =
        Mat(id = UUID.randomUUID().toString(), name = name, color = color, size = size,
            items = letters(name, size, rnd))

    fun stickerItem(id: Long, stickerId: String, size: MatSize, rnd: Random): MatItem = MatItem(
        id = id, kind = Kind.STICKER, key = stickerId,
        x = size.w * (0.15f + rnd.nextFloat() * 0.7f),
        y = size.h * (0.15f + rnd.nextFloat() * 0.7f),
        scale = 0.7f + rnd.nextFloat() * 0.5f,
        rot = (rnd.nextFloat() * 2f - 1f) * 28f, jit = 0f, seed = rnd.nextInt(1_000_000)
    )

    fun withStickers(mat: Mat, ids: List<String>, rnd: Random): Mat {
        var next = (mat.items.maxOfOrNull { it.id } ?: 0L) + 1
        val extra = ids.map { stickerItem(next++, it, mat.size, rnd) }
        return mat.copy(items = mat.items + extra)
    }
}
