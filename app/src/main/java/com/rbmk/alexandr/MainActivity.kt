package com.rbmk.alexandr

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rbmk.alexandr.ui.screens.*
import com.rbmk.alexandr.viewmodel.ReactorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val ctrl = WindowInsetsControllerCompat(window, window.decorView)
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { RBMKApp() }
    }
}

enum class Screen { MENU, STORY, GAME }

@Composable
fun RBMKApp() {
    // ViewModel burada tek bir instance olarak oluşturuluyor
    // Her iki ekran da bunu kullanıyor — completedLevels burada tutulur
    val vm: ReactorViewModel = viewModel()
    val completedLevels by vm.completedLevels.collectAsState()

    var screen by remember { mutableStateOf(Screen.MENU) }
    var selectedLevel by remember { mutableIntStateOf(1) }

    when (screen) {
        Screen.MENU -> MainMenuScreen(
            completedLevels = completedLevels,
            onLevelSelect   = { level ->
                selectedLevel = level
                screen = Screen.GAME
            },
            onShowStory = { screen = Screen.STORY }
        )
        Screen.STORY -> StoryScreen(
            onBack = { screen = Screen.MENU }
        )
        Screen.GAME -> GameScreen(
            level = selectedLevel,
            vm    = vm,
            onBack = { screen = Screen.MENU }
        )
    }
}
