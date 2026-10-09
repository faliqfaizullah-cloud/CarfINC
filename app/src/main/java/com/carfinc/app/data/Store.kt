package com.carfinc.app.data

import android.content.Context
import android.content.SharedPreferences
import com.carfinc.app.model.Mat
import com.carfinc.app.model.MatFactory
import com.carfinc.app.model.MatSize
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

object Store {
    lateinit var ctx: Context
}

object Prefs {
    private fun sp(): SharedPreferences = Store.ctx.getSharedPreferences("carf", Context.MODE_PRIVATE)
    internal fun raw(): SharedPreferences = sp()

    var onboarded: Boolean
        get() = sp().getBoolean("onboarded", false)
        set(v) { sp().edit().putBoolean("onboarded", v).apply() }

    var haptics: Boolean
        get() = sp().getBoolean("haptics", true)
        set(v) { sp().edit().putBoolean("haptics", v).apply() }

    /** The mat the user asked to pin as a widget; used when a new widget has no mapping yet. */
    var pendingWidgetMat: String?
        get() = sp().getString("pendingWidgetMat", null)
        set(v) { sp().edit().putString("pendingWidgetMat", v).apply() }
}

/** Mats are stored as one JSON file each in the app's private storage. */
object Repo {
    private fun dir(): File = File(Store.ctx.filesDir, "mats").apply { mkdirs() }

    fun all(): List<Mat> =
        dir().listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { Mat.fromJson(JSONObject(f.readText())) }.getOrNull() }
            ?.sortedByDescending { it.updated }
            ?: emptyList()

    fun get(id: String): Mat? =
        runCatching { Mat.fromJson(JSONObject(File(dir(), "$id.json").readText())) }.getOrNull()

    fun save(mat: Mat) { File(dir(), "${mat.id}.json").writeText(mat.toJson().toString()) }

    fun delete(id: String) { File(dir(), "$id.json").delete() }

    fun widgetMat(widgetId: Int): String? = Prefs.raw().getString("w$widgetId", null)
    fun setWidgetMat(widgetId: Int, matId: String) { Prefs.raw().edit().putString("w$widgetId", matId).apply() }
    fun removeWidget(widgetId: Int) { Prefs.raw().edit().remove("w$widgetId").apply() }
}

/** First-run starter mats so the widget always has something to show. */
object Samples {
    fun ensure() {
        if (Repo.all().isNotEmpty()) return
        val r = Random(7)
        val a = MatFactory.withStickers(
            MatFactory.create("CARF", 0xFF14402B.toInt(), MatSize.MEDIUM, r),
            listOf("flowers_clover", "cute_star", "dynamic_sparkle", "food_cherry", "cute_moon"), r)
        val b = MatFactory.withStickers(
            MatFactory.create("HELLO", 0xFFC8664A.toInt(), MatSize.LARGE, r),
            listOf("flowers_daisy", "cute_cat", "food_strawberry", "dynamic_bolt"), r)
        Repo.save(b.copy(updated = System.currentTimeMillis() - 1000))
        Repo.save(a)
    }
}
