package com.carfinc.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.carfinc.app.data.Prefs
import com.carfinc.app.data.Repo
import com.carfinc.app.model.MatSize
import com.carfinc.app.model.Palette
import com.carfinc.app.render.MatRenderer
import com.carfinc.app.render.NoteArt
import com.carfinc.app.render.StickerArt
import com.carfinc.app.render.StickerImages
import com.carfinc.app.util.Export
import com.carfinc.app.util.Haptics
import com.carfinc.app.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(matId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val initial = remember(matId) { Repo.get(matId) }
    if (initial == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val state = remember(matId) { EditorState(initial) { Repo.save(it) } }

    var showMat by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<String?>(null) }
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var showWidgetHelp by remember { mutableStateOf(false) }
    var wallpaper by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // leaving the editor: save and refresh any widgets that show this mat
    DisposableEffect(matId) {
        onDispose { state.persist(); WidgetUpdater.updateAll(ctx) }
    }
    BackHandler { onBack() }

    val mat = state.mat

    val topBar: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { Haptics.click(view); onBack() }
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .clickable { Haptics.click(view); showRename = true }.padding(8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Text(mat.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = Muted, modifier = Modifier.size(18.dp))
            }
            RoundIcon(Icons.Filled.Widgets, "Add widget") {
                Haptics.click(view)
                state.persist()
                Prefs.pendingWidgetMat = mat.id
                if (!WidgetUpdater.requestPin(ctx)) showWidgetHelp = true
            }
            Spacer(Modifier.width(8.dp))
            RoundIcon(Icons.Filled.Share, "Share") { Haptics.click(view); showShare = true }
        }
    }

    val undoButton: @Composable () -> Unit = {
        RoundIcon(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = state.canUndo) {
            if (state.undo()) Haptics.reject(view)
        }
    }

    val sliders: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 24.dp)) {
            LabeledSlider(Icons.Filled.ZoomIn, "Size", mat.letterScale, 0.4f..1.6f,
                onStart = { state.checkpoint() }, onChange = { state.setLetterScale(it) }, onEnd = { state.persist() })
            LabeledSlider(Icons.Filled.Shuffle, "Chaos", mat.chaos, 0f..2f,
                onStart = { state.checkpoint() }, onChange = { state.setChaos(it) }, onEnd = { state.persist() })
        }
    }

    val contextRow: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolPill(Icons.Filled.ContentCut, "Paper edge", active = mat.paperEdge) {
                Haptics.click(view); state.togglePaperEdge()
            }
            Spacer(Modifier.weight(1f))
            if (state.selectedId != null) {
                val key = state.selectedKey()
                if (key != null && NoteArt.isNote(key)) {
                    SmallAction(Icons.Filled.Edit, "Edit note") { Haptics.click(view); editingNote = key; showNote = true }
                }
                SmallAction(Icons.Filled.FlipToFront, "Bring to front") { Haptics.click(view); state.bringToFront() }
                SmallAction(Icons.Filled.ContentCopy, "Duplicate") { Haptics.confirm(view); state.duplicateSelected() }
                SmallAction(Icons.Filled.Delete, "Delete", danger = true) { Haptics.reject(view); state.deleteSelected() }
            }
        }
    }

    val toolbar: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(Icons.Filled.AspectRatio, "Mat") { Haptics.click(view); showMat = true }
            ToolButton(Icons.Filled.TextFields, "Note") { Haptics.click(view); editingNote = null; showNote = true }
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Green)
                    .clickable { Haptics.confirm(view); showStickers = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, "Add sticker", tint = Color.White, modifier = Modifier.size(30.dp)) }
            ToolButton(Icons.Filled.AutoAwesome, "Remix") { Haptics.confirm(view); state.remix() }
            ToolButton(Icons.Filled.Layers, "Wallpaper") {
                Haptics.click(view)
                state.persist()
                wallpaper = Export.wallpaperBitmap(ctx, state.mat)
            }
        }
    }

    if (!landscape) {
        Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            topBar()
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                MatCanvas(state, Modifier.fillMaxWidth())
                Box(Modifier.align(Alignment.TopEnd)) { undoButton() }
            }
            sliders()
            contextRow()
            toolbar()
        }
    } else {
        // Landscape: the mat gets the whole left side, controls live in a scrollable panel on the right.
        Row(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp), contentAlignment = Alignment.Center) {
                MatCanvas(state, Modifier)
                Box(Modifier.align(Alignment.TopEnd)) { undoButton() }
            }
            Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                topBar()
                sliders()
                contextRow()
                toolbar()
            }
        }
    }

    // ---------- sheets & dialogs ----------
    if (showRename) {
        NameDialog("Name your mat", "The name gets spelled out on the mat in cutout letters.",
            mat.name, "Done", { showRename = false }) { showRename = false; state.rename(it) }
    }

    if (showMat) {
        MatSheet(state, onClose = { showMat = false })
    }

    if (showNote) {
        NoteDialog(
            initial = editingNote,
            onDismiss = { showNote = false },
            onDone = { key ->
                showNote = false
                Haptics.confirm(view)
                if (editingNote != null) state.replaceSelectedKey(key) else state.addSticker(key)
                editingNote = null
            }
        )
    }

    if (showStickers) {
        StickerSheet(
            onClose = { showStickers = false },
            onPick = { id -> showStickers = false; Haptics.confirm(view); state.addSticker(id) },
            onImported = { id -> showStickers = false; Haptics.confirm(view); state.addSticker(id) }
        )
    }

    if (showShare) {
        ModalBottomSheet(
            onDismissRequest = { showShare = false }, containerColor = Color.White,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                SheetHeader("Share", onClose = { showShare = false })
                SheetRow(Icons.Filled.Share, "Share…") {
                    showShare = false; state.persist(); Haptics.confirm(view)
                    Export.share(ctx, state.mat)
                }
                SheetRow(Icons.Filled.SaveAlt, "Save to Photos") {
                    showShare = false; state.persist()
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Export.saveToPhotos(ctx, state.mat) }
                        if (ok) Haptics.confirm(view) else Haptics.reject(view)
                        Toast.makeText(ctx, if (ok) "Saved to Pictures/Carf.INC" else "Couldn't save image", Toast.LENGTH_SHORT).show()
                    }
                }
                SheetRow(Icons.Filled.Wallpaper, "Wallpaper") {
                    showShare = false; state.persist(); Haptics.click(view)
                    wallpaper = Export.wallpaperBitmap(ctx, state.mat)
                }
                SheetRow(Icons.Filled.Widgets, "Add to Home Screen") {
                    showShare = false; state.persist(); Haptics.click(view)
                    Prefs.pendingWidgetMat = state.mat.id
                    if (!WidgetUpdater.requestPin(ctx)) showWidgetHelp = true
                }
            }
        }
    }

    if (showWidgetHelp) WidgetHelpDialog { showWidgetHelp = false }

    wallpaper?.let { bmp ->
        WallpaperPreview(bmp, onClose = { wallpaper = null }) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Export.setWallpaper(ctx, bmp) }
                if (ok) Haptics.confirm(view) else Haptics.reject(view)
                Toast.makeText(ctx, if (ok) "Wallpaper set" else "Couldn't set wallpaper", Toast.LENGTH_SHORT).show()
                if (ok) wallpaper = null
            }
        }
    }
}

// ============================================================ canvas

@Composable
private fun MatCanvas(state: EditorState, modifier: Modifier = Modifier) {
    val view = LocalView.current
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier = modifier
            .aspectRatio(state.mat.size.w / state.mat.size.h)
            .onSizeChanged { sizePx = it }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (sizePx.width == 0) return@awaitEachGesture
                    val f = state.mat.size.w / sizePx.width
                    val hit = state.hit(down.position.x * f, down.position.y * f)
                    if (hit != null && hit != state.selectedId) Haptics.soft(view)
                    state.selectedId = hit
                    var started = false
                    do {
                        val ev = awaitPointerEvent()
                        val pan = ev.calculatePan()
                        val zoom = ev.calculateZoom()
                        val rot = ev.calculateRotation()
                        if (state.selectedId != null && (pan != Offset.Zero || zoom != 1f || rot != 0f)) {
                            if (!started) { state.checkpoint(); started = true }
                            if (state.transformSelected(pan.x * f, pan.y * f, zoom, rot)) Haptics.tick(view)
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (started) state.persist()
                }
            }
    ) {
        drawIntoCanvas { MatRenderer.draw(it.nativeCanvas, state.mat, size.width, state.selectedId) }
    }
}

// ============================================================ small pieces

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (enabled) Ink else Color(0xFFB8BEC9), modifier = Modifier.size(21.dp)) }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Ink, modifier = Modifier.size(24.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ToolPill(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (active) Color(0xFFDDF1E6) else Color.White)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) Green else Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (active) Green else Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SmallAction(icon: ImageVector, desc: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(if (danger) Color(0xFFFDE4E4) else Color.White)
            .clickable(onClick = onClick), contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (danger) Color(0xFFD7263D) else Ink, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun LabeledSlider(
    icon: ImageVector, label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onStart: () -> Unit, onChange: (Float) -> Unit, onEnd: () -> Unit
) {
    val view = LocalView.current
    var dragging by remember { mutableStateOf(false) }
    var lastBucket by remember { mutableIntStateOf(-1) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = Muted, modifier = Modifier.size(20.dp))
        Slider(
            value = value, valueRange = range, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            onValueChange = { v ->
                if (!dragging) { dragging = true; onStart() }
                val frac = (v - range.start) / (range.endInclusive - range.start)
                val bucket = (frac * 16).toInt()
                if (bucket != lastBucket) { lastBucket = bucket; Haptics.tick(view) }
                onChange(v)
            },
            onValueChangeFinished = { dragging = false; onEnd() },
            colors = SliderDefaults.colors(
                thumbColor = SliderBlue, activeTrackColor = SliderBlue, inactiveTrackColor = Color(0xFFD8DEE9)
            )
        )
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Ink, fontSize = 16.sp)
    }
}

// ============================================================ mat sheet (color + shape)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatSheet(state: EditorState, onClose: () -> Unit) {
    val view = LocalView.current
    var hueDialog by remember { mutableStateOf(false) }
    val mat = state.mat
    ModalBottomSheet(
        onDismissRequest = onClose, containerColor = Color(0xFFE8F4EC),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            SheetHeader("Mat", onClose)
            Text("Color", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            val cells: List<Int?> = Palette.swatches + listOf<Int?>(null)
            cells.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { color ->
                        val selected = color != null && color == mat.color
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .then(
                                    if (color == null) Modifier.background(
                                        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
                                    ) else Modifier.background(Color(color))
                                )
                                .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape).padding(3.dp).border(2.dp, Ink, CircleShape) else Modifier)
                                .clickable {
                                    Haptics.click(view)
                                    if (color == null) hueDialog = true else state.setColor(color)
                                }
                        )
                    }
                    repeat(6 - row.size) { Spacer(Modifier.size(40.dp)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Shape", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MatSize.values().forEach { size ->
                    val sel = mat.size == size
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { Haptics.confirm(view); state.setSize(size) }) {
                        val preview = remember(mat.color, size) {
                            mat.copy(size = size, items = emptyList(), id = "preview_${size.name}")
                        }
                        MatThumb(preview, Modifier.width(if (size == MatSize.MEDIUM) 92.dp else 140.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(if (sel) 3.dp else 0.dp, if (sel) Green else Color.Transparent, RoundedCornerShape(14.dp)),
                            widthPx = 280)
                        Text(size.label, color = if (sel) Green else Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
    if (hueDialog) {
        HueDialog(mat.color, onDismiss = { hueDialog = false }) { hueDialog = false; state.setColor(it) }
    }
}

@Composable
private fun HueDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val view = LocalView.current
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var v by remember { mutableFloatStateOf(hsv[2].coerceIn(0.25f, 1f)) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.6f, v))
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Custom color", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(argb)))
                Spacer(Modifier.height(12.dp))
                Slider(hue, { hue = it; Haptics.tick(view) }, valueRange = 0f..360f,
                    colors = SliderDefaults.colors(thumbColor = Color(argb), activeTrackColor = Color(argb)))
                Slider(v, { v = it; Haptics.tick(view) }, valueRange = 0.25f..1f,
                    colors = SliderDefaults.colors(thumbColor = Color(argb), activeTrackColor = Color(argb)))
            }
        },
        confirmButton = { TextButton({ Haptics.confirm(view); onPick(argb) }) { Text("Apply", color = Green, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Muted) } }
    )
}

// ============================================================ sticker sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerSheet(onClose: () -> Unit, onPick: (String) -> Unit, onImported: (String) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val tabs = listOf("My stickers") + StickerArt.categories.keys.toList()
    var tab by remember { mutableStateOf("Cute") }
    var mine by remember { mutableStateOf(StickerImages.list()) }
    var busy by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val id = withContext(Dispatchers.IO) { StickerImages.import(uri) }
            busy = false
            if (id != null) onImported(id) else {
                Haptics.reject(view)
                Toast.makeText(ctx, "Couldn't find a person in that photo. Try one where they're clearly visible.", Toast.LENGTH_LONG).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose, containerColor = Color(0xFFF4F1FA),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SheetHeader("Decorations", onClose)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                items(tabs) { t ->
                    val sel = t == tab
                    Text(t, fontSize = 13.sp, color = if (sel) Color.White else Ink,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp))
                            .background(if (sel) Green else Color.White)
                            .clickable { if (!sel) { Haptics.tick(view); tab = t; if (t == "My stickers") mine = StickerImages.list() } }
                            .padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            val ids: List<String> = if (tab == "My stickers") mine else StickerArt.categories[tab].orEmpty()
            Box {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tab == "My stickers") {
                    item {
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White)
                                .clickable { Haptics.click(view); picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.AddPhotoAlternate, "Import photo", tint = Green, modifier = Modifier.size(30.dp))
                                Text("Person cut-out", fontSize = 10.sp, color = Muted, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                items(ids) { id ->
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White)
                            .clickable { onPick(id) }.padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) { StickerPreview(id, Modifier.fillMaxSize()) }
                }
            }
            if (busy) {
                Box(Modifier.matchParentSize().background(Color(0xCCF4F1FA)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Green)
                        Spacer(Modifier.height(10.dp))
                        Text("Cutting out the person…", color = Ink, fontSize = 13.sp)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StickerPreview(id: String, modifier: Modifier) {
    Canvas(modifier) {
        drawIntoCanvas { cv ->
            val n = cv.nativeCanvas
            n.save()
            n.translate(size.width / 2f, size.height / 2f)
            val s = minOf(size.width, size.height) / 236f
            n.scale(s, s)
            StickerArt.draw(n, id, true)
            n.restore()
        }
    }
}

// ============================================================ widget help + wallpaper preview

@Composable
fun WidgetHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Add the widget", fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "1. Long-press an empty spot on your home screen.\n" +
                    "2. Tap Widgets and find Carf.INC.\n" +
                    "3. Drag the mat widget onto the screen.\n" +
                    "4. Pick which mat it should show.\n\n" +
                    "Resize it from 2×2 up to 4×2 — it re-renders crisp every time you edit a mat.",
                color = Ink, fontSize = 14.sp, lineHeight = 21.sp
            )
        },
        confirmButton = { TextButton(onDismiss) { Text("Got it", color = Green, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
private fun WallpaperPreview(bmp: android.graphics.Bitmap, onClose: () -> Unit, onSet: () -> Unit) {
    val view = LocalView.current
    val img = remember(bmp) { bmp.asImageBitmap() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0E0E10))) {
            Image(img, "Wallpaper preview", Modifier.fillMaxSize().systemBarsPadding().padding(top = 56.dp, bottom = 36.dp, start = 20.dp, end = 20.dp)
                .clip(RoundedCornerShape(34.dp)), contentScale = ContentScale.Crop)
            Row(Modifier.fillMaxWidth().systemBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF))
                    .clickable { Haptics.click(view); onClose() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text("Set", color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Green)
                        .clickable { Haptics.confirm(view); onSet() }.padding(horizontal = 22.dp, vertical = 9.dp))
            }
        }
    }
}


// ============================================================ note dialog

private val noteColors = listOf(0xFFFF8FC4, 0xFFFFE066, 0xFFFFFFFF, 0xFF141414, 0xFFA9D6FF, 0xFFE7CFA2)

@Composable
private fun NoteDialog(initial: String?, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val view = LocalView.current
    var text by remember { mutableStateOf(initial?.let { NoteArt.textOf(it) } ?: "") }
    var style by remember { mutableIntStateOf(initial?.let { NoteArt.styleOf(it) } ?: 0) }
    val key = NoteArt.id(style, text.ifBlank { "your text" })
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
        title = { Text(if (initial == null) "New note" else "Edit note", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE8F4EC))) {
                    StickerPreview(key, Modifier.fillMaxSize().padding(6.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    noteColors.forEachIndexed { i, col ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(col))
                                .border(if (i == style) 3.dp else 1.dp, if (i == style) Green else Hairline, CircleShape)
                                .clickable { Haptics.tick(view); style = i }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> if (v.length <= 60) { text = v; Haptics.key(view) } else Haptics.reject(view) },
                    placeholder = { Text("Write something…", color = Muted) },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        },
        confirmButton = {
            TextButton({
                if (text.isBlank()) Haptics.reject(view) else onDone(NoteArt.id(style, text.trim()))
            }) { Text(if (initial == null) "Add" else "Save", color = Green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Muted) } }
    )
}
