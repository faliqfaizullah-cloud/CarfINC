package com.carfinc.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.ArrayDeque

/**
 * Lifts only the PERSON out of a photo: everything that is not a person becomes transparent and the
 * bitmap is cropped to the person's bounding box (never the whole picture).
 */
object PersonCutout {

    private const val MAX_OUT = 640
    private const val MIN_PERSON_FRACTION = 0.02f   // < 2% of the photo → treat as "no person"

    /** Blocking – call from a background thread. Returns null when no person is found. */
    fun extract(src: Bitmap): Bitmap? {
        val opts = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(opts)
        try {
            val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(src, 0)))
            val mw = mask.width; val mh = mask.height
            val buf = mask.buffer
            buf.rewind()
            val conf = FloatArray(mw * mh)
            buf.asFloatBuffer().get(conf)

            // 1. soft alpha with a tight ramp so edges are clean but not jagged
            val alpha = FloatArray(conf.size) { smooth(conf[it], 0.45f, 0.80f) }

            // 2. keep the main person blob(s), drop stray specks
            keepMainBlobs(alpha, mw, mh)

            // 3. bounding box of the person
            var x0 = mw; var y0 = mh; var x1 = -1; var y1 = -1; var count = 0
            for (y in 0 until mh) for (x in 0 until mw) {
                if (alpha[y * mw + x] > 0.5f) {
                    count++
                    if (x < x0) x0 = x; if (x > x1) x1 = x
                    if (y < y0) y0 = y; if (y > y1) y1 = y
                }
            }
            if (x1 < 0 || count < mw * mh * MIN_PERSON_FRACTION) return null

            val padX = ((x1 - x0) * 0.03f).toInt() + 1
            val padY = ((y1 - y0) * 0.03f).toInt() + 1
            x0 = (x0 - padX).coerceAtLeast(0); y0 = (y0 - padY).coerceAtLeast(0)
            x1 = (x1 + padX).coerceAtMost(mw - 1); y1 = (y1 + padY).coerceAtMost(mh - 1)
            val cw = x1 - x0 + 1; val ch = y1 - y0 + 1

            // 4. build the transparent cut-out (mask is same size as the input bitmap)
            val scaled = if (src.width == mw && src.height == mh) src
                         else Bitmap.createScaledBitmap(src, mw, mh, true)
            val px = IntArray(cw * ch)
            val row = IntArray(cw)
            for (y in 0 until ch) {
                scaled.getPixels(row, 0, cw, x0, y0 + y, cw, 1)
                for (x in 0 until cw) {
                    val a = alpha[(y0 + y) * mw + (x0 + x)]
                    val c = row[x]
                    val na = (Color.alpha(c) * a).toInt()
                    px[y * cw + x] = if (na == 0) 0 else Color.argb(na, Color.red(c), Color.green(c), Color.blue(c))
                }
            }
            val cut = Bitmap.createBitmap(px, cw, ch, Bitmap.Config.ARGB_8888)
            val s = MAX_OUT.toFloat() / maxOf(cw, ch)
            return if (s < 1f) Bitmap.createScaledBitmap(cut, (cw * s).toInt().coerceAtLeast(1), (ch * s).toInt().coerceAtLeast(1), true) else cut
        } finally {
            segmenter.close()
        }
    }

    /**
     * Camera "Object" mode: lifts the main subject (a book, a drink, a camera…) with ML Kit Subject
     * Segmentation. The model is delivered by Google Play Services (downloads once on first use).
     */
    fun extractObject(src: Bitmap): Bitmap? {
        val options = com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap().build()
        val segmenter = com.google.mlkit.vision.segmentation.subject.SubjectSegmentation.getClient(options)
        try {
            val result = Tasks.await(segmenter.process(InputImage.fromBitmap(src, 0)))
            val fg = result.foregroundBitmap ?: return null
            val w = fg.width; val h = fg.height
            val px = IntArray(w * h)
            fg.getPixels(px, 0, w, 0, 0, w, h)
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            for (y in 0 until h) for (x in 0 until w) {
                if (Color.alpha(px[y * w + x]) > 24) {
                    if (x < x0) x0 = x; if (x > x1) x1 = x
                    if (y < y0) y0 = y; if (y > y1) y1 = y
                }
            }
            if (x1 < 0 || (x1 - x0) * (y1 - y0) < w * h * 0.01f) return null
            val pad = (maxOf(x1 - x0, y1 - y0) * 0.02f).toInt() + 1
            x0 = (x0 - pad).coerceAtLeast(0); y0 = (y0 - pad).coerceAtLeast(0)
            x1 = (x1 + pad).coerceAtMost(w - 1); y1 = (y1 + pad).coerceAtMost(h - 1)
            val cw = x1 - x0 + 1; val ch = y1 - y0 + 1
            val cut = Bitmap.createBitmap(fg, x0, y0, cw, ch)
            val s = MAX_OUT.toFloat() / maxOf(cw, ch)
            return if (s < 1f) Bitmap.createScaledBitmap(cut, (cw * s).toInt().coerceAtLeast(1), (ch * s).toInt().coerceAtLeast(1), true) else cut
        } finally {
            segmenter.close()
        }
    }

    private fun smooth(v: Float, lo: Float, hi: Float): Float {
        val t = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Zero out connected regions smaller than 12% of the biggest one. */
    private fun keepMainBlobs(alpha: FloatArray, w: Int, h: Int) {
        val label = IntArray(w * h)
        val sizes = ArrayList<Int>()
        val q = ArrayDeque<Int>()
        for (i in alpha.indices) {
            if (alpha[i] <= 0.5f || label[i] != 0) continue
            val id = sizes.size + 1
            var n = 0
            label[i] = id; q.add(i)
            while (q.isNotEmpty()) {
                val p = q.poll(); n++
                val x = p % w; val y = p / w
                if (x > 0) push(alpha, label, q, p - 1, id)
                if (x < w - 1) push(alpha, label, q, p + 1, id)
                if (y > 0) push(alpha, label, q, p - w, id)
                if (y < h - 1) push(alpha, label, q, p + w, id)
            }
            sizes.add(n)
        }
        if (sizes.isEmpty()) return
        val keepMin = (sizes.max() * 0.12f).toInt()
        for (i in alpha.indices) {
            val l = label[i]
            if (l != 0 && sizes[l - 1] < keepMin) alpha[i] = 0f
        }
        // fringe pixels (0<a<=0.5) not touching any kept blob are removed
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (label[i] == 0 && alpha[i] > 0f) {
                var near = false
                loop@ for (dy in -2..2) for (dx in -2..2) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h) {
                        val l = label[ny * w + nx]
                        if (l != 0 && sizes[l - 1] >= keepMin) { near = true; break@loop }
                    }
                }
                if (!near) alpha[i] = 0f
            }
        }
    }

    private fun push(alpha: FloatArray, label: IntArray, q: ArrayDeque<Int>, i: Int, id: Int) {
        if (alpha[i] > 0.5f && label[i] == 0) { label[i] = id; q.add(i) }
    }

    /**
     * Die-cut outline: a white silhouette of [bmp] grown by [pad] px on every side (drawn once, cached by caller).
     * The result is [pad]*2 larger than the input in both dimensions.
     */
    fun outline(bmp: Bitmap, pad: Int): Bitmap {
        val out = Bitmap.createBitmap(bmp.width + pad * 2, bmp.height + pad * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        val steps = 28
        for (ring in 1..3) {
            val r = pad * ring / 3f
            for (k in 0 until steps) {
                val a = Math.PI * 2 * k / steps
                c.drawBitmap(bmp, pad + (r * Math.cos(a)).toFloat(), pad + (r * Math.sin(a)).toFloat(), p)
            }
        }
        c.drawBitmap(bmp, pad.toFloat(), pad.toFloat(), p)
        return out
    }
}
