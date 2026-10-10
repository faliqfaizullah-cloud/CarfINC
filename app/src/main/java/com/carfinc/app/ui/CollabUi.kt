package com.carfinc.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carfinc.app.data.Collab
import com.carfinc.app.data.Prefs
import com.carfinc.app.data.Repo
import com.carfinc.app.model.Mat
import com.carfinc.app.util.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-time setup: the Firebase Realtime Database that stores collective mats. */
@Composable
fun DbSetupDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val view = LocalView.current
    var url by remember { mutableStateOf(Prefs.dbUrl) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
        title = { Text("Set up shared mats", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "Shared mats live in a free Firebase Realtime Database that you own.\n\n" +
                        "1. Open console.firebase.google.com and create a project.\n" +
                        "2. Build → Realtime Database → Create database.\n" +
                        "3. Rules tab: set \".read\" and \".write\" to true, then Publish.\n" +
                        "4. Copy the database URL (https://…firebaseio.com) and paste it below.\n\n" +
                        "Everyone who joins your mats needs the same URL.",
                    color = Ink, fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = false }, singleLine = true,
                    placeholder = { Text("https://your-project-default-rtdb.firebaseio.com", fontSize = 12.sp, color = Muted) },
                    isError = error
                )
                if (error) Text("That doesn't look like a Firebase database URL.", color = Color(0xFFD7263D), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton({
                val u = url.trim()
                val ok = u.startsWith("https://") && (u.contains("firebaseio.com") || u.contains("firebasedatabase.app"))
                if (ok) { Prefs.dbUrl = u; Haptics.confirm(view); onSaved() } else { error = true; Haptics.reject(view) }
            }) { Text("Save", color = Green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Muted) } }
    )
}

/** Enter a 6-letter code to join someone's mat. */
@Composable
fun JoinDialog(onDismiss: () -> Unit, onJoined: (Mat) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var setup by remember { mutableStateOf(false) }

    fun join() {
        if (busy) return
        busy = true; message = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { Collab.join(code) }
            busy = false
            when (r) {
                is Collab.JoinResult.Ok -> { Repo.save(r.mat); Haptics.confirm(view); onJoined(r.mat) }
                Collab.JoinResult.NotConfigured -> setup = true
                Collab.JoinResult.NotFound -> { message = "No mat with that code."; Haptics.reject(view) }
                Collab.JoinResult.Full -> { message = "That mat already has ${Collab.MAX_MEMBERS} people."; Haptics.reject(view) }
                Collab.JoinResult.Network -> { message = "Couldn't reach the database. Check your connection and the URL."; Haptics.reject(view) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
        title = { Text("Join a mat", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("Ask a friend for the 6-letter code from their mat.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code, onValueChange = { code = Collab.normalizeCode(it) }, singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 22.sp, letterSpacing = 4.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
                message?.let { Text(it, color = Color(0xFFD7263D), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = Green)
                Text("Database: " + (if (Collab.configured()) "connected" else "not set up yet") + " · change",
                    color = Green, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp).clickable { setup = true })
            }
        },
        confirmButton = {
            TextButton({ if (code.length == 6) join() else Haptics.reject(view) }) {
                Text("Join", color = Green, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Muted) } }
    )
    if (setup) DbSetupDialog(onDismiss = { setup = false }, onSaved = { setup = false })
}

/** Invite people to a mat (turns a personal mat into a collective one the first time). */
@Composable
fun InviteDialog(mat: Mat, members: Int, onDismiss: () -> Unit, onMadeCollective: (String) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var setup by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(28.dp),
        title = { Text(if (mat.collective) "Invite friends" else "Make a mat with someone", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (!mat.collective) {
                    Text("• A partner, your family, a friend far away. Up to ${Collab.MAX_MEMBERS} of you\n" +
                        "• Everyone adds their own cutouts, everyone arranges\n" +
                        "• It lives on every home screen at once",
                        color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                    error?.let { Text(it, color = Color(0xFFD7263D), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = Green)
                } else {
                    Text("Share this code. Anyone with the app can join from Friends → Join.", color = Muted, fontSize = 13.sp)
                    Text(mat.code, fontFamily = FontFamily.Monospace, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp, color = Ink, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE8F4EC)).padding(vertical = 14.dp))
                    Text("$members of ${Collab.MAX_MEMBERS} people are on this mat", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton({
                            Haptics.click(view)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join my mat “${mat.name}” on Carf.INC: open the app → Friends → Join a mat → code ${mat.code}")
                            }
                            ctx.startActivity(Intent.createChooser(send, "Invite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp), tint = Green); Spacer(Modifier.width(6.dp)); Text("Share", color = Green) }
                        TextButton({
                            Haptics.confirm(view)
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("Mat code", mat.code))
                        }) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp), tint = Green); Spacer(Modifier.width(6.dp)); Text("Copy", color = Green) }
                    }
                }
            }
        },
        confirmButton = {
            if (!mat.collective) {
                TextButton({
                    if (busy) return@TextButton
                    if (!Collab.configured()) { setup = true; return@TextButton }
                    busy = true; error = null
                    scope.launch {
                        val made = withContext(Dispatchers.IO) { Collab.create(mat) }
                        busy = false
                        if (made != null) { Haptics.confirm(view); onMadeCollective(made.code) }
                        else { Haptics.reject(view); error = "Couldn't reach the database. Check the URL and that its rules allow read/write." }
                    }
                }) { Text("Make a mat with friends", color = Green, fontWeight = FontWeight.Bold) }
            } else TextButton(onDismiss) { Text("Done", color = Green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { if (!mat.collective) TextButton(onDismiss) { Text("Not now", color = Muted) } }
    )
    if (setup) DbSetupDialog(onDismiss = { setup = false }, onSaved = { setup = false })
}
