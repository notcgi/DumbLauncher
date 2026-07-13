package com.dumblauncher.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dumblauncher.app.data.AppsRepository
import com.dumblauncher.app.data.BatteryRepository
import com.dumblauncher.app.data.ClockRepository
import com.dumblauncher.app.data.FavoritesStore
import com.dumblauncher.app.data.ScreenTimeRepository
import com.dumblauncher.app.ui.allapps.AllAppsScreen
import com.dumblauncher.app.ui.home.HomeScreen
import com.dumblauncher.app.ui.settings.SettingsScreen
import com.dumblauncher.app.ui.theme.EInkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels {
        val appContext = applicationContext
        LauncherViewModel.Factory(
            appsRepository = AppsRepository(appContext),
            favoritesStore = FavoritesStore(appContext),
            clockRepository = ClockRepository(appContext),
            batteryRepository = BatteryRepository(appContext),
            screenTimeRepository = ScreenTimeRepository(appContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideStatusBar()
        if (isHomeIntent(intent)) {
            viewModel.goHome()
        }
        setContent {
            EInkTheme {
                LauncherApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isHomeIntent(intent)) {
            viewModel.goHome()
        }
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar()
        viewModel.refreshUsageAccess()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun isHomeIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)
    }
}

@Composable
private fun LauncherApp(
    viewModel: LauncherViewModel,
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val allAppsState by viewModel.allAppsState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()

    BackHandler(enabled = destination != LauncherDestination.Home) {
        when (destination) {
            LauncherDestination.AllApps,
            LauncherDestination.Settings,
            -> viewModel.goHome()
            LauncherDestination.Home -> Unit
        }
    }

    // HOME key / launcher: stay on home, do not finish activity.
    BackHandler(enabled = destination == LauncherDestination.Home) {
        // Swallow back on home so we don't leave the launcher.
    }

    when (destination) {
        LauncherDestination.Home -> HomeScreen(
            state = homeState,
            onLaunch = viewModel::launch,
            onOpenAllApps = { viewModel.navigateTo(LauncherDestination.AllApps) },
            onOpenSettings = { viewModel.navigateTo(LauncherDestination.Settings) },
            onRequestUsageAccess = viewModel::openUsageAccessSettings,
            onOpenClock = viewModel::openClock,
            onOpenCalendar = viewModel::openCalendar,
            onOpenScreenTime = viewModel::openScreenTime,
        )

        LauncherDestination.AllApps -> AllAppsScreen(
            state = allAppsState,
            onQueryChange = viewModel::setAllAppsQuery,
            onLaunch = viewModel::launch,
            onHide = viewModel::hideApp,
            onRename = viewModel::renameApp,
            onUninstall = viewModel::uninstallApp,
            onBack = viewModel::goHome,
        )

        LauncherDestination.Settings -> SettingsScreen(
            state = settingsState,
            onBack = viewModel::goHome,
            onFavoriteCountChange = viewModel::setFavoriteCount,
            onHideSelfChange = viewModel::setHideSelf,
            onToggleFavorite = viewModel::toggleFavorite,
            onMoveFavorite = viewModel::moveFavorite,
            onUnhideApp = viewModel::unhideApp,
            onResetAppLabel = viewModel::resetAppLabel,
            onOpenUsageAccess = viewModel::openUsageAccessSettings,
        )
    }
}
