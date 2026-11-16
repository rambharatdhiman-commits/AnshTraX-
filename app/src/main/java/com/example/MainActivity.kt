package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.GameScreen
import com.example.ui.GameViewModel
import com.example.ui.screens.GameplayScreen
import com.example.ui.screens.GarageScreen
import com.example.ui.screens.MainMenuScreen
import com.example.ui.screens.MapSelectScreen
import com.example.ui.theme.AnshTraXTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }

        // Immersive full-screen landscape experience
        runCatching {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        setContent {
            AnshTraXTheme {
                val progress by viewModel.progress.collectAsStateWithLifecycle()
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                val world by viewModel.activeWorld.collectAsStateWithLifecycle()
                val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(feedback) {
                    feedback?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearFeedback()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(targetState = screen, label = "screen_transition") { current ->
                            when (current) {
                                GameScreen.MAIN_MENU -> {
                                    MainMenuScreen(
                                        progress = progress,
                                        onPlayClick = { viewModel.startFreeDrive() },
                                        onGarageClick = { viewModel.navigateTo(GameScreen.GARAGE) },
                                        onMapsClick = { viewModel.navigateTo(GameScreen.MAP_SELECT) },
                                        onToggleMusic = { viewModel.toggleMusic() },
                                        onToggleSfx = { viewModel.toggleSfx() }
                                    )
                                }
                                GameScreen.GARAGE -> {
                                    GarageScreen(
                                        progress = progress,
                                        onBack = { viewModel.navigateTo(GameScreen.MAIN_MENU) },
                                        onPlayNow = { viewModel.startFreeDrive() },
                                        onSelectTractor = { viewModel.selectOrUnlockTractor(it) },
                                        onSelectDriver = { viewModel.selectDriver(it) },
                                        onSelectTrailer = { viewModel.selectOrUnlockTrailer(it) },
                                        onSelectCargo = { viewModel.selectOrUnlockCargo(it) },
                                        onPurchaseUpgrade = { viewModel.purchaseUpgrade(it) }
                                    )
                                }
                                GameScreen.MAP_SELECT -> {
                                    MapSelectScreen(
                                        progress = progress,
                                        onBack = { viewModel.navigateTo(GameScreen.MAIN_MENU) },
                                        onSelectOrUnlockMap = { viewModel.selectOrUnlockMap(it) },
                                        onDriveMap = { viewModel.startFreeDrive(it) },
                                        onStartMission = { viewModel.startMission(it) }
                                    )
                                }
                                GameScreen.GAMEPLAY -> {
                                    val active = world
                                    if (active != null) {
                                        GameplayScreen(
                                            world = active,
                                            progress = progress,
                                            onPause = { viewModel.pauseGameplay() },
                                            onResume = { viewModel.resumeGameplay() },
                                            onRestart = { viewModel.restartRun() },
                                            onOpenGarage = { viewModel.navigateTo(GameScreen.GARAGE) },
                                            onExitMenu = { viewModel.navigateTo(GameScreen.MAIN_MENU) },
                                            onToggleMusic = { viewModel.toggleMusic() },
                                            onToggleSfx = { viewModel.toggleSfx() },
                                            onRunFinished = { viewModel.commitRunRewardsIfNeeded(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResume()
    }
}
