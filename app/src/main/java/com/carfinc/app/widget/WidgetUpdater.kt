package com.carfinc.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.carfinc.app.MainActivity
import com.carfinc.app.R
import com.carfinc.app.data.Prefs
import com.carfinc.app.data.Repo
import com.carfinc.app.model.MatSize
import com.carfinc.app.render.MatRenderer

object WidgetUpdater {

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, MatWidgetProvider::class.java))
        ids.forEach { update(ctx, mgr, it) }
    }

    fun update(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
        var matId = Repo.widgetMat(widgetId)
        if (matId == null) {
            // brand-new widget: use the mat the user asked to pin, otherwise the newest one
            matId = Prefs.pendingWidgetMat ?: Repo.all().firstOrNull()?.id
            if (matId != null) Repo.setWidgetMat(widgetId, matId)
            Prefs.pendingWidgetMat = null
        }
        val mat = matId?.let { Repo.get(it) }

        val rv = RemoteViews(ctx.packageName, R.layout.widget_mat)
        if (mat == null) {
            rv.setViewVisibility(R.id.widget_image, View.GONE)
            rv.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        } else {
            val px = if (mat.size == MatSize.LARGE) 840 else 640
            rv.setImageViewBitmap(R.id.widget_image, MatRenderer.toBitmap(mat, px))
            rv.setViewVisibility(R.id.widget_image, View.VISIBLE)
            rv.setViewVisibility(R.id.widget_empty, View.GONE)
        }

        val open = Intent(ctx, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_MAT, mat?.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, widgetId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.widget_root, pi)
        mgr.updateAppWidget(widgetId, rv)
    }

    /** Ask the launcher to pin a new widget (Android 8+ launchers that support it). */
    fun requestPin(ctx: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(ctx)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return mgr.requestPinAppWidget(ComponentName(ctx, MatWidgetProvider::class.java), null, null)
    }
}
