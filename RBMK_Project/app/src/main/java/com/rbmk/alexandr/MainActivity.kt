package com.rbmk.alexandr

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rbmk.alexandr.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tam ekran, döndürme yok (Yatay)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Ekran açık tut
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            RBMKApp()
        }
    }
}

// ─── NAVIGASYON ───────────────────────────────────────────────────────────────
enum class Screen { MENU, STORY, GAME }

@Composable
fun RBMKApp() {
    var screen by remember { mutableStateOf(Screen.MENU) }
    var selectedLevel by remember { mutableStateOf(1) }
    val completedLevels = remember { mutableStateOf(setOf<Int>()) }

    when (screen) {
        Screen.MENU -> MainMenuScreen(
            completedLevels = completedLevels.value,
            onLevelSelect = { level ->
                selectedLevel = level
                screen = Screen.GAME
            },
            onShowStory = { screen = Screen.STORY }
        )
        Screen.STORY -> StoryScreen(
            onBack = { screen = Screen.MENU }
        )
        Screen.GAME -> GameScreen(
            level = selectedLevel
        )
    }
}
