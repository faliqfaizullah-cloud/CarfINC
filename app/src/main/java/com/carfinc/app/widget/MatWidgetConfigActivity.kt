package com.carfinc.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carfinc.app.data.Prefs
import com.carfinc.app.data.Repo
import com.carfinc.app.data.Samples
import com.carfinc.app.model.Mat
import com.carfinc.app.ui.Bg
import com.carfinc.app.ui.CarfTheme
import com.carfinc.app.ui.Ink
import com.carfinc.app.ui.MatThumb
import com.carfinc.app.ui.Muted
import com.carfinc.app.util.Haptics

/** Shown when a widget is placed (or re-configured): choose which mat it displays. */
class MatWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        Samples.ensure()

        setContent {
            CarfTheme {
                ConfigScreen { mat ->
                    Repo.setWidgetMat(widgetId, mat.id)
                    Prefs.pendingWidgetMat = null
                    WidgetUpdater.update(this, AppWidgetManager.getInstance(this), widgetId)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    finish()
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(onPick: (Mat) -> Unit) {
    val view = LocalView.current
    val mats = remember { Repo.all() }
    val pending = remember { Prefs.pendingWidgetMat }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Choose your mat", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
            fontSize = 30.sp, color = Ink)
        Text("This mat will live on your home screen.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(mats, key = { it.id }) { mat ->
                Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                    Haptics.confirm(view); onPick(mat)
                }) {
                    MatThumb(mat, Modifier.fillMaxWidth())
                    Text(mat.name + if (mat.id == pending) "  •  selected" else "",
                        modifier = Modifier.padding(8.dp), color = Ink, fontSize = 14.sp)
                }
            }
        }
    }
}
