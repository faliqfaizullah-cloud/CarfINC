package com.carfinc.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.carfinc.app.render.CutMode
import com.carfinc.app.util.Haptics
import java.util.concurrent.Executors

/**
 * Live camera that sits above the mat (like the reference app): point at something, tap Capture and the
 * person (or, in Object mode, the main subject) is cut out and dropped straight onto the mat.
 */
@Composable
fun CameraPanel(
    modifier: Modifier,
    mode: CutMode,
    busy: Boolean,
    onMode: (CutMode) -> Unit,
    onCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    var front by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val worker = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { worker.shutdown() } }

    DisposableEffect(granted, front) {
        var provider: ProcessCameraProvider? = null
        if (granted) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                runCatching {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    p.unbindAll()
                    p.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { runCatching { provider?.unbindAll() } }
    }

    fun capture() {
        if (busy) return
        Haptics.confirm(view)
        imageCapture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = runCatching {
                    val raw = image.toBitmap()
                    val m = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                    var out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                    val s = 1400f / maxOf(out.width, out.height)
                    if (s < 1f) out = Bitmap.createScaledBitmap(out, (out.width * s).toInt(), (out.height * s).toInt(), true)
                    out
                }.getOrNull()
                image.close()
                if (bmp != null) Handler(Looper.getMainLooper()).post { onCaptured(bmp) }
            }
            override fun onError(exception: ImageCaptureException) {
                Handler(Looper.getMainLooper()).post { Haptics.reject(view) }
            }
        })
    }

    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xFF111111))) {
        if (granted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera access is needed to capture stickers.", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("Allow camera", color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Green)
                        .clickable { permission.launch(Manifest.permission.CAMERA) }.padding(horizontal = 20.dp, vertical = 9.dp))
            }
        }

        // close + flip
        Box(Modifier.align(Alignment.TopStart).padding(12.dp).size(38.dp).clip(CircleShape).background(Color(0x66000000))
            .clickable { Haptics.click(view); onClose() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Close camera", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(38.dp).clip(CircleShape).background(Color(0x66000000))
            .clickable { Haptics.click(view); front = !front }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.FlipCameraAndroid, "Flip camera", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // Person / Object
        Row(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x66000000)).padding(3.dp)) {
            CutMode.values().forEach { m ->
                val sel = m == mode
                Text(if (m == CutMode.PERSON) "Person" else "Object", fontSize = 12.sp,
                    color = if (sel) Ink else Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(17.dp))
                        .background(if (sel) Color.White else Color.Transparent)
                        .clickable { if (!sel) { Haptics.tick(view); onMode(m) } }
                        .padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }

        // Capture pill
        Text("Capture", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color(0xE6FFFFFF))
                .clickable(enabled = granted && !busy) { capture() }
                .padding(horizontal = 40.dp, vertical = 14.dp))

        if (busy) {
            Box(Modifier.matchParentSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text("Cutting it out…", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
