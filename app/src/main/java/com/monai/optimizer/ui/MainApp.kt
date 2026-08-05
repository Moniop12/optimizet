package com.monai.optimizer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.monai.optimizer.ui.theme.CyanGlow
import com.monai.optimizer.ui.theme.DarkBg
import com.monai.optimizer.ui.theme.DarkSurface
import com.monai.optimizer.ui.theme.TextTertiary
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home  : Screen("home",  "Home",  Icons.Filled.Home)
    object Tools : Screen("tools", "Tools", Icons.Filled.Tune)
    object Log   : Screen("log",   "Log",   Icons.Filled.History)
}

/**
 * Tab navigation is driven by a HorizontalPager instead of NavHost:
 *  - swipe left/right between Home/Tools/Log with your finger, not just the bottom bar
 *  - tapping a bottom-bar item animates a smooth slide to that page (pagerState.animateScrollToPage)
 *    instead of the abrupt/no-animation swap NavHost gave us before.
 * The Charging sub-screen is a separate overlay (slide-in from the right, like a normal
 * "push" navigation) on top of the pager, with the system back button popping it closed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainApp(vm: MainViewModel) {
    val screens = listOf(Screen.Home, Screen.Tools, Screen.Log)
    val pagerState = rememberPagerState(pageCount = { screens.size })
    val scope = rememberCoroutineScope()
    var showCharging by remember { mutableStateOf(false) }
    var showFreezer  by remember { mutableStateOf(false) }

    BackHandler(enabled = showCharging) { showCharging = false }
    BackHandler(enabled = showFreezer && !showCharging) { showFreezer = false }

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            // Hide the bottom bar while detail screens are open, for a focused feel.
            if (!showCharging && !showFreezer) {
                NavigationBar(containerColor = DarkSurface, tonalElevation = 0.dp) {
                    screens.forEachIndexed { index, s ->
                        NavigationBarItem(
                            icon     = { Icon(s.icon, s.label) },
                            label    = { Text(s.label) },
                            selected = pagerState.currentPage == index,
                            onClick  = {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        page = index,
                                        animationSpec = tween(durationMillis = 320)
                                    )
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyanGlow,
                                selectedTextColor = CyanGlow,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = CyanGlow.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !showCharging
            ) { page ->
                when (screens[page]) {
                    Screen.Home  -> HomeScreen(vm)
                    Screen.Tools -> ToolsScreen(
                        vm,
                        onOpenCharging = { showCharging = true },
                        onOpenFreezer  = { showFreezer  = true }
                    )
                    Screen.Log   -> LogScreen(vm)
                }
            }

            AnimatedVisibility(
                visible = showCharging,
                enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) + fadeIn(tween(280)),
                exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)) + fadeOut(tween(220))
            ) {
                ChargingScreen(vm, onBack = { showCharging = false })
            }

            AnimatedVisibility(
                visible = showFreezer,
                enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) + fadeIn(tween(280)),
                exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)) + fadeOut(tween(220))
            ) {
                FreezerScreen(vm, onBack = { showFreezer = false })
            }
        }
    }
}
