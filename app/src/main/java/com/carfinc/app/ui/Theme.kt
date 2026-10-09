package com.carfinc.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.carfinc.app.model.Mat
import com.carfinc.app.render.MatRenderer
import com.carfinc.app.util.Haptics

val Bg = Color(0xFFF3F5F9)
val Ink = Color(0xFF14161A)
val Muted = Color(0xFF7A8190)
val Green = Color(0xFF1F7A4D)
val SliderBlue = Color(0xFF2F7BFF)
val Hairline = Color(0xFFE2E6EE)

@Composable
fun CarfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green, onPrimary = Color.White,
            background = Bg, surface = Color.White, onSurface = Ink
        ),
        content = content
    )
}

/** Renders a mat to a bitmap once per change and shows it. */
@Composable
fun MatThumb(mat: Mat, modifier: Modifier = Modifier, widthPx: Int = 520) {
    val bmp = remember(mat.id, mat.updated, mat.size, mat.color, mat.items.size, mat.paperEdge) {
        MatRenderer.toBitmap(mat, widthPx).asImageBitmap()
    }
    Image(bitmap = bmp, contentDescription = mat.name, modifier = modifier)
}

@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(36.dp))
        Text(title, Modifier.weight(1f), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.size(36.dp).clip(CircleShape).background(Hairline)
            .clickable { Haptics.click(view); onClose() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Ink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun Wordmark(size: Int = 34) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("Carf", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
            fontSize = size.sp, color = Ink)
        Text(".INC", fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Bold, color = Green,
            modifier = Modifier.padding(start = 2.dp, bottom = (size * 0.18f).dp))
    }
}

@Composable
fun NameDialog(
    title: String, subtitle: String, initial: String, confirm: String,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit
) {
    val view = LocalView.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(subtitle, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        if (v.length <= 14) { text = v; Haptics.key(view) } else Haptics.reject(view)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) Haptics.reject(view) else { Haptics.confirm(view); onConfirm(text.trim()) }
            }) { Text(confirm, color = Green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}
