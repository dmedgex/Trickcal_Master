package com.trickcal.crayon.master.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trickcal.crayon.master.ui.screens.ApostleScreen
import com.trickcal.crayon.master.ui.screens.CrayonImportScreen
import com.trickcal.crayon.master.ui.screens.StaticImportScreen
import com.trickcal.crayon.master.ui.screens.BattlePowerScreen
import com.trickcal.crayon.master.ui.screens.DetailScreen
import com.trickcal.crayon.master.ui.screens.HomeScreen
import com.trickcal.crayon.master.ui.screens.MeScreen
import com.trickcal.crayon.master.ui.screens.PetDispatchScreen
import com.trickcal.crayon.master.ui.screens.SavesScreen
import com.trickcal.crayon.master.ui.screens.StatsScreen
import com.trickcal.crayon.master.ui.theme.Tc
import kotlinx.coroutines.delay

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("home", "首页", Icons.Filled.Home),
    TabItem("apostle", "使徒", Icons.Filled.Person),
    TabItem("stats", "统计", Icons.Filled.List),
    TabItem("me", "我的", Icons.Filled.AccountCircle),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val showBar = TABS.any { it.route == route }

    /**
     * 统一导航入口：
     * 四个 Tab 路由一律 pop 回起始页再进入，保证栈里每个 Tab 只有一层；
     * 不能与 saveState/restoreState 混用，否则被弹出的页面会被恢复回来（曾导致点「首页」回到统计页）。
     */
    val go: (String) -> Unit = { target ->
        if (TABS.any { it.route == target }) {
            nav.navigate(target) {
                popUpTo(nav.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        } else {
            nav.navigate(target) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = Tc.colors.paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                BottomBar(current = route, onSelect = go)
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { HomeScreen(vm, go) }
                composable("apostle") {
                    ApostleScreen(vm) { id -> nav.navigate("detail/$id") }
                }
                composable("stats") { StatsScreen(vm) }
                composable("crayonimport") {
                    CrayonImportScreen(vm, onBack = { nav.popBackStack() }, onStatic = { nav.navigate("staticimport") })
                }
                composable("staticimport") {
                    StaticImportScreen(vm) { nav.popBackStack() }
                }
                composable("me") { MeScreen(vm, go) }
                composable("detail/{id}") { backStack ->
                    DetailScreen(
                        vm = vm,
                        charId = backStack.arguments?.getString("id").orEmpty(),
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("battlepower") {
                    BattlePowerScreen(vm) { nav.popBackStack() }
                }
                composable("petdispatch") {
                    PetDispatchScreen(vm) { nav.popBackStack() }
                }
                composable("saves") {
                    SavesScreen(vm) { nav.popBackStack() }
                }
            }
            ToastHost(vm, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun BottomBar(current: String, onSelect: (String) -> Unit) {
    val navPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tc.colors.surface)
            .padding(bottom = navPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Tc.colors.line),
        )
        Row(modifier = Modifier.fillMaxWidth().height(58.dp)) {
            TABS.forEach { tab ->
                val on = tab.route == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab.route) }
                        .padding(top = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (on) Tc.colors.brand else Color.Transparent),
                    )
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (on) Tc.colors.brand else Tc.colors.ink3,
                        modifier = Modifier.size(23.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.5.sp,
                        color = if (on) Tc.colors.brand else Tc.colors.ink3,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastHost(vm: AppViewModel, modifier: Modifier = Modifier) {
    val message = vm.toast
    LaunchedEffect(message) {
        if (message != null) {
            delay(1900)
            vm.consumeToast()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.padding(bottom = 96.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(Color(0xE62A2320), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                text = message.orEmpty(),
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 页面通用顶部留白（状态栏）。 */
@Composable
fun statusBarTop(): androidx.compose.ui.unit.Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
