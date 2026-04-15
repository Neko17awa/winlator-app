package com.winlator

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.winlator.ui.navigation.Screen
import com.winlator.ui.screen.MainScreen
import com.winlator.ui.theme.WinlatorTheme

fun setComposeContent(
    activity: MainActivity,
    darkTheme: Boolean,
    editInputControls: Boolean,
    selectedProfileId: Int,
    showShortcutsFirst: Boolean,
) {
    activity.setContent {
        WinlatorTheme(darkTheme = darkTheme) {
            Surface(modifier = Modifier.fillMaxSize()) {
                MainScreen(
                    startDestination = if (showShortcutsFirst) Screen.Shortcuts.route
                                       else Screen.Containers.route,
                    editInputControls = editInputControls,
                    selectedProfileId = selectedProfileId,
                )
            }
        }
    }
}
