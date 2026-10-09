package com.carfinc.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.carfinc.app.data.Prefs
import com.carfinc.app.data.Repo
import com.carfinc.app.data.Samples
import com.carfinc.app.ui.CarfTheme
import com.carfinc.app.ui.EditorScreen
import com.carfinc.app.ui.HomeScreen
import com.carfinc.app.ui.OnboardingScreen

private sealed interface Screen {
    object Onboarding : Screen
    object Home : Screen
    data class Editor(val id: String) : Screen
}

class MainActivity : ComponentActivity() {
    private var requestedMat by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Samples.ensure()
        requestedMat = intent?.getStringExtra(EXTRA_MAT)
        setContent {
            CarfTheme {
                App(requestedMat) { requestedMat = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_MAT)?.let { requestedMat = it }
    }

    companion object {
        const val EXTRA_MAT = "mat_id"
    }
}

@Composable
private fun App(requestedMat: String?, consume: () -> Unit) {
    var screen by remember { mutableStateOf<Screen>(if (Prefs.onboarded) Screen.Home else Screen.Onboarding) }

    // Tapping the home-screen widget deep-links straight into that mat's editor.
    LaunchedEffect(requestedMat) {
        if (requestedMat != null) {
            if (Repo.get(requestedMat) != null) screen = Screen.Editor(requestedMat)
            consume()
        }
    }

    when (val s = screen) {
        Screen.Onboarding -> OnboardingScreen { screen = Screen.Home }
        Screen.Home -> HomeScreen(
            onOpen = { screen = Screen.Editor(it) },
            onReplayIntro = { screen = Screen.Onboarding }
        )
        is Screen.Editor -> {
            EditorScreen(s.id) { screen = Screen.Home }
        }
    }
}
