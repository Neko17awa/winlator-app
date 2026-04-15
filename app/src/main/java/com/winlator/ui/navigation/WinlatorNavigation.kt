package com.winlator.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.winlator.ui.screen.ContainerDetailScreen
import com.winlator.ui.screen.ContainerFileManagerScreen
import com.winlator.ui.screen.ContainersScreen
import com.winlator.ui.screen.InputControlsScreen
import com.winlator.ui.screen.SettingsScreen
import com.winlator.ui.screen.ShortcutsScreen

sealed class Screen(val route: String) {
    data object Containers : Screen("containers")
    data object Shortcuts : Screen("shortcuts")
    data object InputControls : Screen("input_controls/{selectedProfileId}") {
        fun createRoute(selectedProfileId: Int = 0) = "input_controls/$selectedProfileId"
    }
    data object Settings : Screen("settings")
    data object ContainerDetail : Screen("container_detail/{containerId}") {
        fun createRoute(containerId: Int = 0) = "container_detail/$containerId"
    }
    data object ContainerFileManager : Screen("container_file_manager/{containerId}") {
        fun createRoute(containerId: Int, startPath: String? = null): String {
            val base = "container_file_manager/$containerId"
            return if (startPath != null) "$base?startPath=$startPath" else base
        }
    }
}

@Composable
fun WinlatorNavHost(
    navController: NavHostController,
    startDestination: String,
    onTitleChange: (String) -> Unit,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Containers.route) {
            ContainersScreen(
                onNavigateToDetail = { containerId ->
                    navController.navigate(Screen.ContainerDetail.createRoute(containerId))
                },
                onNavigateToFileManager = { containerId ->
                    navController.navigate(Screen.ContainerFileManager.createRoute(containerId))
                },
                onTitleChange = onTitleChange,
            )
        }

        composable(Screen.Shortcuts.route) {
            ShortcutsScreen(onTitleChange = onTitleChange)
        }

        composable(
            route = Screen.InputControls.route,
            arguments = listOf(navArgument("selectedProfileId") {
                type = NavType.IntType; defaultValue = 0
            })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getInt("selectedProfileId") ?: 0
            InputControlsScreen(
                selectedProfileId = profileId,
                onTitleChange = onTitleChange,
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onTitleChange = onTitleChange,
                onNavigateToContainers = {
                    navController.navigate(Screen.Containers.route) {
                        popUpTo(Screen.Containers.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.ContainerDetail.route,
            arguments = listOf(navArgument("containerId") {
                type = NavType.IntType; defaultValue = 0
            })
        ) { backStackEntry ->
            val containerId = backStackEntry.arguments?.getInt("containerId") ?: 0
            ContainerDetailScreen(
                containerId = containerId,
                onTitleChange = onTitleChange,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ContainerFileManager.route + "?startPath={startPath}",
            arguments = listOf(
                navArgument("containerId") { type = NavType.IntType },
                navArgument("startPath") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val containerId = backStackEntry.arguments?.getInt("containerId") ?: 0
            val startPath = backStackEntry.arguments?.getString("startPath")
            ContainerFileManagerScreen(
                containerId = containerId,
                startPath = startPath,
                onTitleChange = onTitleChange,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
