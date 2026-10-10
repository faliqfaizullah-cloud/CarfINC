package com.carfinc.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carfinc.app.data.Collab
import com.carfinc.app.data.Prefs
import com.carfinc.app.data.Repo
import com.carfinc.app.model.Mat
import com.carfinc.app.model.MatFactory
import com.carfinc.app.model.MatSize
import com.carfinc.app.model.Palette
import com.carfinc.app.util.Haptics
import com.carfinc.app.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

private val newMatColors = listOf(Palette.swatches[0], Palette.swatches[6], Palette.swatches[2], Palette.swatches[4], Palette.swatches[1])
private val PillGlass = Color(0xCCB9D9CE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String, Boolean) -> Unit, onReplayIntro: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var mats by remember { mutableStateOf(Repo.all()) }
    var tab by remember { mutableIntStateOf(0) }            // 0 = My mats, 1 = Friends
    var showNew by remember { mutableStateOf(false) }
    var showNewCollective by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showDbSetup by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showWidgetHelp by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Mat?>(null) }
    var working by remember { mutableStateOf(false) }

    fun refresh() { mats = Repo.all(); WidgetUpdater.updateAll(ctx) }

    // pull the latest of every shared mat when Home opens / the Friends tab is shown
    LaunchedEffect(tab) {
        if (Collab.configured() && Repo.all().any { it.collective }) {
            val changed = withContext(Dispatchers.IO) { Collab.syncAllLocal() }
            if (changed) refresh()
        }
    }

    val mine = mats.filter { !it.collective }
    val shared = mats.filter { it.collective }

    fun beginCollective() {
        if (!Collab.configured()) showDbSetup = { showNewCollective = true } else showNewCollective = true
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            // ---------- header ----------
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                HeaderButton(Icons.Filled.Inbox, "Join a mat") { Haptics.click(view); showJoin = true }
                Spacer(Modifier.width(10.dp))
                HeaderButton(Icons.Filled.Info, "Settings") { Haptics.click(view); showSettings = true }
            }

            if (tab == 0) {
                if (mine.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No mats yet — tap + to start cutting.", color = Muted)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 150.dp), modifier = Modifier.weight(1f)
                    ) {
                        items(mine, key = { it.id }) { mat ->
                            MatCard(mat,
                                onOpen = { Haptics.click(view); onOpen(mat.id, false) },
                                onDuplicate = {
                                    Haptics.confirm(view)
                                    Repo.save(mat.copy(id = UUID.randomUUID().toString(), updated = System.currentTimeMillis()))
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
            } else {
                if (shared.isEmpty()) {
                    CollectiveIntro(Modifier.weight(1f), onStart = { Haptics.confirm(view); beginCollective() })
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 150.dp), modifier = Modifier.weight(1f)
                    ) {
                        items(shared, key = { it.id }) { mat ->
                            MatCard(mat,
                                onOpen = { Haptics.click(view); onOpen(mat.id, false) },
                                onDuplicate = null,
                                onWidget = {
                                    Haptics.click(view)
                                    Prefs.pendingWidgetMat = mat.id
                                    if (!WidgetUpdater.requestPin(ctx)) showWidgetHelp = true
                                },
                                onDelete = { Haptics.heavy(view); toDelete = mat })
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PillButton("Make a new one", Modifier.weight(1f)) { Haptics.confirm(view); beginCollective() }
                                PillButton("Join with code", Modifier.weight(1f), outlined = true) { Haptics.click(view); showJoin = true }
                            }
                        }
                    }
                }
            }
        }

        // ---------- floating glass tab bar ----------
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp)
                .shadow(10.dp, RoundedCornerShape(40.dp), ambientColor = Color(0x22000000))
                .clip(RoundedCornerShape(40.dp)).background(PillGlass).padding(6.dp)
        ) {
            TabItem(Icons.Filled.GridView, "My mats", tab == 0) { if (tab != 0) { Haptics.tick(view); tab = 0 } }
            TabItem(Icons.Filled.Group, "Friends", tab == 1) { if (tab != 1) { Haptics.tick(view); tab = 1 } }
        }

        // new mat button (My mats only)
        if (tab == 0) {
            Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 26.dp)
                    .size(56.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Green)
                    .clickable { Haptics.confirm(view); showNew = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, "New mat", tint = Color.White, modifier = Modifier.size(28.dp)) }
        }

        if (working) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text("Creating shared mat…", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }

    // ---------- dialogs ----------
    if (showNew) {
        NameDialog("Name your mat", "The name gets spelled out on the mat in cutout letters.", "", "Create",
            { showNew = false }) { name ->
            showNew = false
            val mat = MatFactory.create(name, newMatColors.random(), MatSize.MEDIUM)
            Repo.save(mat)
            onOpen(mat.id, false)
        }
    }

    if (showNewCollective) {
        NameDialog("Name your shared mat", "Everyone you invite sees this name spelled out.", "", "Create",
            { showNewCollective = false }) { name ->
            showNewCollective = false
            working = true
            scope.launch {
                val base = MatFactory.create(name, Palette.swatches[8], MatSize.LARGE)
                val made = withContext(Dispatchers.IO) { Collab.create(base) }
                working = false
                if (made != null) { Repo.save(made); Haptics.confirm(view); onOpen(made.id, true) }
                else {
                    Haptics.reject(view)
                    Toast.makeText(ctx, "Couldn't reach the database. Check the URL and its read/write rules.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showJoin) {
        JoinDialog(onDismiss = { showJoin = false }) { mat ->
            showJoin = false; refresh(); tab = 1; onOpen(mat.id, false)
        }
    }

    showDbSetup?.let { next ->
        DbSetupDialog(onDismiss = { showDbSetup = null }, onSaved = { showDbSetup = null; next() })
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = Color.White,
            title = { Text(if (m.collective) "Leave “${m.name}”?" else "Delete “${m.name}”?") },
            text = {
                Text(if (m.collective) "It disappears from this phone. Your friends keep the mat, and you can rejoin with code ${m.code}."
                else "Any widget showing this mat will switch to your newest mat.")
            },
            confirmButton = {
                TextButton({
                    Haptics.reject(view)
                    if (m.collective) Thread { Collab.leave(m.code) }.start()
                    Repo.delete(m.id); toDelete = null; refresh()
                }) { Text(if (m.collective) "Leave" else "Delete", color = Color(0xFFD7263D), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancel", color = Muted) } }
        )
    }

    if (showWidgetHelp) WidgetHelpDialog { showWidgetHelp = false }

    if (showSettings) {
        var dbDialog by remember { mutableStateOf(false) }
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
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { Haptics.click(view); dbDialog = true }.padding(vertical = 12.dp)) {
                    Text("Shared mats database", color = Ink, fontSize = 16.sp)
                    Text(if (Collab.configured()) "Connected" else "Not set up — needed for Friends", color = Muted, fontSize = 12.sp)
                }
                Text("Replay intro", color = Ink, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        Haptics.click(view); showSettings = false; onReplayIntro()
                    }.padding(vertical = 12.dp))
                Text("Carf.INC v1.2", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            }
        }
        if (dbDialog) DbSetupDialog(onDismiss = { dbDialog = false }, onSaved = { dbDialog = false })
    }
}

// ============================================================ pieces

@Composable
private fun HeaderButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).shadow(6.dp, CircleShape, ambientColor = Color(0x22000000)).clip(CircleShape)
            .background(Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Ink, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(32.dp))
            .background(if (selected) Color(0xB3FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (selected) Ink else Color(0xFF55706A), modifier = Modifier.size(24.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Ink else Color(0xFF55706A))
    }
}

@Composable
private fun PillButton(text: String, modifier: Modifier, outlined: Boolean = false, onClick: () -> Unit) {
    Text(text, textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        color = if (outlined) Green else Color.White,
        modifier = modifier.clip(RoundedCornerShape(30.dp))
            .background(if (outlined) Color(0xFFDDF1E6) else Green)
            .clickable(onClick = onClick).padding(vertical = 15.dp))
}

/** The "Make a mat with someone" screen shown on the Friends tab until you have a shared mat. */
@Composable
private fun CollectiveIntro(modifier: Modifier, onStart: () -> Unit) {
    val preview = remember {
        MatFactory.create("OURS", Palette.swatches[8], MatSize.LARGE, Random(11))
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            MatThumb(preview, Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)))
            Row(
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Group, null, tint = Green, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Collective", color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Make a mat with someone", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            IntroRow(Icons.Filled.Groups, "A partner, your family, a friend far away. Up to ${Collab.MAX_MEMBERS} of you")
            IntroRow(Icons.Filled.ContentCopy, "Everyone adds their own cutouts, everyone arranges")
            IntroRow(Icons.Filled.PhoneAndroid, "It lives on every home screen at once")
        }
        Spacer(Modifier.weight(1f))
        Text("Make a mat with friends", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.shadow(10.dp, RoundedCornerShape(36.dp), ambientColor = Color(0x331F7A4D))
                .clip(RoundedCornerShape(36.dp)).background(Color(0xFF3F7F5F))
                .clickable(onClick = onStart).padding(horizontal = 40.dp, vertical = 18.dp))
        Spacer(Modifier.height(10.dp))
        Text("Joining one is free, always", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun IntroRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Green, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun MatCard(mat: Mat, onOpen: () -> Unit, onDuplicate: (() -> Unit)?, onWidget: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            MatThumb(mat, Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)))
            if (mat.collective) {
                Row(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Group, null, tint = Green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Collective", color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mat.name, color = Muted, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Box {
                Icon(Icons.Filled.MoreVert, "More", tint = Muted,
                    modifier = Modifier.size(34.dp).clip(CircleShape).clickable { menu = true }.padding(6.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add as widget") }, onClick = { menu = false; onWidget() })
                    if (onDuplicate != null) DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(if (mat.collective) "Leave" else "Delete", color = Color(0xFFD7263D)) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
