package com.carfinc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carfinc.app.data.Prefs
import com.carfinc.app.data.Repo
import com.carfinc.app.model.Mat
import com.carfinc.app.model.MatFactory
import com.carfinc.app.model.MatSize
import com.carfinc.app.model.Palette
import com.carfinc.app.util.Haptics
import com.carfinc.app.widget.WidgetUpdater
import java.util.UUID

private val newMatColors = listOf(Palette.swatches[0], Palette.swatches[6], Palette.swatches[2], Palette.swatches[4], Palette.swatches[1])

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit, onReplayIntro: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var mats by remember { mutableStateOf(Repo.all()) }
    var showNew by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showWidgetHelp by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Mat?>(null) }

    fun refresh() { mats = Repo.all(); WidgetUpdater.updateAll(ctx) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White)
                    .clickable { Haptics.click(view); showSettings = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Settings, "Settings", tint = Ink, modifier = Modifier.size(21.dp))
                }
            }
            Text("Your mats", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 14.dp))

            if (mats.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No mats yet — tap “New mat” to start cutting.", color = Muted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 110.dp), modifier = Modifier.weight(1f)
                ) {
                    items(mats, key = { it.id }) { mat ->
                        MatCard(mat,
                            onOpen = { Haptics.click(view); onOpen(mat.id) },
                            onDuplicate = {
                                Haptics.confirm(view)
                                Repo.save(mat.copy(id = UUID.randomUUID().toString(), name = mat.name, updated = System.currentTimeMillis()))
                                refresh()
                            },
                            onWidget = {
                                Haptics.click(view)
                                Prefs.pendingWidgetMat = mat.id
                                if (!WidgetUpdater.requestPin(ctx)) showWidgetHelp = true
                            },
                            onDelete = { Haptics.heavy(view); toDelete = mat })
                    }
                }
            }
        }

        // New mat button
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
                .clip(RoundedCornerShape(32.dp)).background(Green)
                .clickable { Haptics.confirm(view); showNew = true }
                .padding(horizontal = 26.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("New mat", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }

    if (showNew) {
        NameDialog("Name your mat", "The name gets spelled out on the mat in cutout letters.", "", "Create",
            { showNew = false }) { name ->
            showNew = false
            val mat = MatFactory.create(name, newMatColors.random(), MatSize.MEDIUM)
            Repo.save(mat)
            onOpen(mat.id)
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = Color.White,
            title = { Text("Delete “${m.name}”?") },
            text = { Text("Any widget showing this mat will switch to your newest mat.") },
            confirmButton = {
                TextButton({ Haptics.reject(view); Repo.delete(m.id); toDelete = null; refresh() }) {
                    Text("Delete", color = Color(0xFFD7263D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancel", color = Muted) } }
        )
    }

    if (showWidgetHelp) WidgetHelpDialog { showWidgetHelp = false }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false }, containerColor = Color.White,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            var hap by remember { mutableStateOf(Prefs.haptics) }
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                SheetHeader("Settings", { showSettings = false })
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Haptic feedback", color = Ink, fontSize = 16.sp)
                        Text("Ticks on sliders, snaps and taps", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = hap, onCheckedChange = {
                        hap = it; Prefs.haptics = it; Haptics.enabled = it
                        if (it) Haptics.confirm(view)
                    }, colors = SwitchDefaults.colors(checkedTrackColor = Green))
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Hairline)
                Text("Add widget to Home Screen", color = Ink, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        Haptics.click(view); showSettings = false
                        Prefs.pendingWidgetMat = mats.firstOrNull()?.id
                        if (!WidgetUpdater.requestPin(ctx)) showWidgetHelp = true
                    }.padding(vertical = 12.dp))
                Text("Replay intro", color = Ink, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        Haptics.click(view); showSettings = false; onReplayIntro()
                    }.padding(vertical = 12.dp))
                Text("Carf.INC v1.0", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
}

@Composable
private fun MatCard(mat: Mat, onOpen: () -> Unit, onDuplicate: () -> Unit, onWidget: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White).clickable(onClick = onOpen).padding(10.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MatThumb(mat, Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mat.name, color = Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Box {
                Icon(Icons.Filled.MoreVert, "More", tint = Muted,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable { menu = true }.padding(6.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add as widget") }, onClick = { menu = false; onWidget() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete", color = Color(0xFFD7263D)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
