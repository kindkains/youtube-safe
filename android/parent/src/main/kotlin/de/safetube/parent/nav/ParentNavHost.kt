package de.safetube.parent.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.safetube.parent.child.ChildSettingsScreen
import de.safetube.parent.container
import de.safetube.parent.history.HistoryScreen
import de.safetube.parent.login.LoginScreen
import de.safetube.parent.overview.OverviewActions
import de.safetube.parent.overview.OverviewScreen
import de.safetube.parent.parents.ParentsScreen
import de.safetube.parent.requests.RequestDetailScreen
import de.safetube.parent.requests.RequestsScreen
import de.safetube.parent.reward.RewardScreen
import de.safetube.parent.rules.AddRuleScreen
import de.safetube.parent.rules.RulesScreen
import de.safetube.parent.stats.StatsScreen

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val TOP_LEVEL_ITEMS = listOf(
    TopLevel(Routes.OVERVIEW, "Übersicht", Icons.Filled.Home),
    TopLevel(Routes.REQUESTS, "Anfragen", Icons.Filled.Notifications),
    TopLevel(Routes.RULES, "Regeln", Icons.Filled.Lock),
    TopLevel(Routes.PARENTS, "Eltern", Icons.Filled.Person),
)

/** Argument „childId“ bzw. „requestId“ als Int. */
internal val childIdArg = listOf(navArgument("childId") { type = NavType.IntType })
internal val requestIdArg = listOf(navArgument("requestId") { type = NavType.IntType })

@Composable
fun ParentNavHost(
    loggedIn: Boolean,
    openRequestId: Int?,
    onRequestOpened: () -> Unit,
    onLoggedIn: () -> Unit,
    onLoggedOut: () -> Unit,
    overviewHeader: @Composable () -> Unit = {},
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(openRequestId, loggedIn) {
        if (loggedIn && openRequestId != null) {
            nav.navigate(Routes.request(openRequestId))
            onRequestOpened()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = { if (route in Routes.TOP_LEVEL) BottomBar(nav, route) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (loggedIn) Routes.OVERVIEW else Routes.LOGIN,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(onLoggedIn = {
                    onLoggedIn()
                    nav.navigate(Routes.OVERVIEW) { popUpTo(Routes.LOGIN) { inclusive = true } }
                })
            }
            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    actions = OverviewActions(
                        onRequests = { nav.navigateTopLevel(Routes.REQUESTS) },
                        onHistory = { nav.navigate(Routes.history(it)) },
                        onStats = { nav.navigate(Routes.stats(it)) },
                        onReward = { nav.navigate(Routes.reward(it)) },
                        onChild = { nav.navigate(Routes.child(it)) },
                    ),
                    header = overviewHeader,
                )
            }
            // Routen
            composable(Routes.PARENTS) {
                val container = LocalContext.current.container
                ParentsScreen(
                    onLoggedOut = {
                        onLoggedOut()
                        nav.toLogin()
                    },
                    logout = { container.auth.logout(container.api) },
                )
            }
            composable(Routes.CHILD, arguments = childIdArg) { entry ->
                ChildSettingsScreen(childId = entry.arguments!!.getInt("childId"), onBack = { nav.popBackStack() })
            }
            composable(Routes.REWARD, arguments = childIdArg) { entry ->
                RewardScreen(childId = entry.arguments!!.getInt("childId"), onBack = { nav.popBackStack() })
            }
            composable(Routes.STATS, arguments = childIdArg) { entry ->
                StatsScreen(childId = entry.arguments!!.getInt("childId"), onBack = { nav.popBackStack() })
            }
            composable(Routes.HISTORY, arguments = childIdArg) { entry ->
                HistoryScreen(childId = entry.arguments!!.getInt("childId"), onBack = { nav.popBackStack() })
            }
            composable(Routes.RULE_ADD) {
                AddRuleScreen(onDone = { nav.popBackStack() })
            }
            composable(Routes.RULES) {
                RulesScreen(onAdd = { nav.navigate(Routes.RULE_ADD) })
            }
            composable(Routes.REQUEST, arguments = requestIdArg) { entry ->
                RequestDetailScreen(
                    requestId = entry.arguments!!.getInt("requestId"),
                    onBack = { nav.popBackStack() },
                    onDecided = { },
                )
            }
            composable(Routes.REQUESTS) {
                RequestsScreen(onOpen = { nav.navigate(Routes.request(it)) })
            }
        }
    }
}

/** Nach dem Abmelden: alles vom Stapel, zurück zum Login. */
internal fun NavHostController.toLogin() {
    navigate(Routes.LOGIN) { popUpTo(graph.id) { inclusive = true } }
}

internal fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.OVERVIEW) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar {
        TOP_LEVEL_ITEMS.forEach { item ->
            NavigationBarItem(
                selected = current == item.route,
                onClick = { nav.navigateTopLevel(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
            )
        }
    }
}
