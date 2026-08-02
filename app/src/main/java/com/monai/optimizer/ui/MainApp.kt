package com.monai.optimizer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.monai.optimizer.ui.theme.BrandPrimary
import com.monai.optimizer.ui.theme.CardSurfaceAlt
import com.monai.optimizer.ui.theme.DarkSurface
import com.monai.optimizer.ui.theme.TextDisabled
import com.monai.optimizer.ui.theme.TextSecondary

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home  : Screen("home",  "Overview", Icons.Filled.Home)
    object Tools : Screen("tools", "Controls", Icons.Filled.Tune)
    object Log   : Screen("log",   "Activity", Icons.Filled.History)
}

@Composable
fun MainApp(vm: MainViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val screens = listOf(Screen.Home, Screen.Tools, Screen.Log)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = DarkSurface, tonalElevation = 0.dp) {
                screens.forEach { s ->
                    NavigationBarItem(
                        icon = { Icon(s.icon, s.label) },
                        label = { Text(s.label) },
                        selected = current == s.route,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandPrimary,
                            selectedTextColor = BrandPrimary,
                            indicatorColor = CardSurfaceAlt,
                            unselectedIconColor = TextDisabled,
                            unselectedTextColor = TextSecondary,
                        ),
                        onClick = {
                            nav.navigate(s.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, Screen.Home.route, Modifier.padding(pad)) {
            composable(Screen.Home.route) { HomeScreen(vm) }
            composable(Screen.Tools.route) { ToolsScreen(vm) }
            composable(Screen.Log.route) { LogScreen(vm) }
        }
    }
}
