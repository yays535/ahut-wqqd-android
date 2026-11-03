package cn.ahutwqqd.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

enum class AppScreen { HOME, LOGS, SETTINGS, GUIDE }

@Composable
fun AppRoot(viewModel: AppViewModel) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }

    BackHandler(enabled = screen != AppScreen.HOME) { screen = AppScreen.HOME }

    when (screen) {
        AppScreen.HOME -> HomeScreen(
            viewModel = viewModel,
            onOpenLogs = { screen = AppScreen.LOGS },
            onOpenSettings = { screen = AppScreen.SETTINGS },
            onOpenGuide = { screen = AppScreen.GUIDE }
        )

        AppScreen.LOGS -> LogScreen(viewModel = viewModel, onBack = { screen = AppScreen.HOME })

        AppScreen.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            onBack = { screen = AppScreen.HOME },
            onOpenGuide = { screen = AppScreen.GUIDE }
        )

        AppScreen.GUIDE -> GuideScreen(
            viewModel = viewModel,
            onBack = { screen = AppScreen.SETTINGS }
        )
    }
}
