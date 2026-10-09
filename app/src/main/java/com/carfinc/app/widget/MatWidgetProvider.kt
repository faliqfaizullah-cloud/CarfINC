package com.carfinc.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.carfinc.app.data.Repo

class MatWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetUpdater.update(context, mgr, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetUpdater.update(context, mgr, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { Repo.removeWidget(it) }
    }
}
