package com.carfinc.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.carfinc.app.model.Kind
import com.carfinc.app.model.Mat
import com.carfinc.app.model.MatFactory
import com.carfinc.app.model.MatSize
import com.carfinc.app.render.MatRenderer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** All editing logic for one mat, with undo. Mat is immutable so each undo step is just a reference. */
class EditorState(initial: Mat, private val onPersist: (Mat) -> Unit) {
    var mat by mutableStateOf(initial)
        private set
    var selectedId by mutableStateOf<Long?>(null)
    var canUndo by mutableStateOf(false)
        private set

    private val undoStack = ArrayList<Mat>()
    private val rnd = Random(System.nanoTime())

    fun checkpoint() {
        undoStack.add(mat)
        if (undoStack.size > 60) undoStack.removeAt(0)
        canUndo = true
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        mat = undoStack.removeAt(undoStack.lastIndex)
        canUndo = undoStack.isNotEmpty()
        selectedId = null
        persist()
        return true
    }

    fun persist() {
        val m = mat.copy(updated = System.currentTimeMillis())
        mat = m
        onPersist(m)
    }

    private fun nextId(): Long = (mat.items.maxOfOrNull { it.id } ?: 0L) + 1

    fun hit(x: Float, y: Float): Long? =
        mat.items.lastOrNull { hypot(x - it.x, y - it.y) <= MatRenderer.radius(mat, it) }?.id

    /** @return true when the rotation has just entered a 45° detent (caller plays a haptic tick). */
    fun transformSelected(dx: Float, dy: Float, zoom: Float, rotDeg: Float): Boolean {
        val id = selectedId ?: return false
        var detent = false
        val w = mat.size.w; val h = mat.size.h
        mat = mat.copy(items = mat.items.map { i ->
            if (i.id != id) i else {
                val r = i.rot + rotDeg
                if (rotDeg != 0f) {
                    val near = Math.round(r / 45f) * 45f
                    if (abs(r - near) < 1.5f && abs(i.rot - near) >= 1.5f) detent = true
                }
                i.copy(
                    x = (i.x + dx).coerceIn(0f, w), y = (i.y + dy).coerceIn(0f, h),
                    scale = (i.scale * zoom).coerceIn(0.2f, 5f), rot = r
                )
            }
        })
        return detent
    }

    fun addSticker(stickerId: String) {
        checkpoint()
        val item = MatFactory.stickerItem(nextId(), stickerId, mat.size, rnd)
        mat = mat.copy(items = mat.items + item)
        selectedId = item.id
        persist()
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        checkpoint()
        mat = mat.copy(items = mat.items.filter { it.id != id })
        selectedId = null
        persist()
    }

    fun duplicateSelected() {
        val id = selectedId ?: return
        val src = mat.items.firstOrNull { it.id == id } ?: return
        checkpoint()
        val copy = src.copy(id = nextId(), x = (src.x + 40f).coerceAtMost(mat.size.w),
            y = (src.y + 40f).coerceAtMost(mat.size.h), seed = if (src.kind == Kind.LETTER) rnd.nextInt(1_000_000) else src.seed)
        mat = mat.copy(items = mat.items + copy)
        selectedId = copy.id
        persist()
    }

    fun bringToFront() {
        val id = selectedId ?: return
        val src = mat.items.firstOrNull { it.id == id } ?: return
        checkpoint()
        mat = mat.copy(items = mat.items.filter { it.id != id } + src)
        persist()
    }

    fun setColor(color: Int) {
        if (color == mat.color) return
        checkpoint(); mat = mat.copy(color = color); persist()
    }

    private fun rebuild(name: String, size: MatSize) {
        val letters = MatFactory.letters(name, size, rnd, 1L)
        val ky = size.h / mat.size.h
        val base = letters.size + 1L
        val stickers = mat.items.filter { it.kind == Kind.STICKER }
            .mapIndexed { n, s -> s.copy(id = base + n, y = s.y * ky) }
        mat = mat.copy(name = name, size = size, items = letters + stickers)
        selectedId = null
    }

    fun setSize(size: MatSize) {
        if (size == mat.size) return
        checkpoint(); rebuild(mat.name, size); persist()
    }

    fun rename(newName: String) {
        val n = newName.trim()
        if (n.isEmpty()) return
        checkpoint(); rebuild(n, mat.size); persist()
    }

    /** Re-roll the paper colours, fonts and tilt of every letter. */
    fun remix() { checkpoint(); rebuild(mat.name, mat.size); persist() }

    fun togglePaperEdge() { checkpoint(); mat = mat.copy(paperEdge = !mat.paperEdge); persist() }

    fun setLetterScale(v: Float) { mat = mat.copy(letterScale = v) }
    fun setChaos(v: Float) { mat = mat.copy(chaos = v) }
}
