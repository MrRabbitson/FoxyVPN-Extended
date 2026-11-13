package com.vauth.foxyvpn.ui.navigation

import com.vauth.foxyvpn.ui.components.GlassTopBarHost
import com.vauth.foxyvpn.ui.components.LocalTopBarHost
import com.vauth.foxyvpn.ui.components.TopBarHost
import androidx.compose.runtime.CompositionLocalProvider
import com.vauth.foxyvpn.ui.components.LocalGlassContent
import androidx.compose.runtime.LaunchedEffect
import com.vauth.foxyvpn.ui.components.GlassClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.ui.components.GlassTab
import com.vauth.foxyvpn.ui.components.GlassTabBar
import com.vauth.foxyvpn.ui.components.glassSource
import com.vauth.foxyvpn.ui.components.rememberGlassSource
import android.net.ConnectivityManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vauth.foxyvpn.AppContainer
import com.vauth.foxyvpn.ui.HomeViewModel
import com.vauth.foxyvpn.ui.ServerListViewModel
import com.vauth.foxyvpn.data.model.ConnectionState
import com.vauth.foxyvpn.ui.components.EmphasizedEasing
import com.vauth.foxyvpn.ui.components.GlassScreen
import com.vauth.foxyvpn.vpn.FoxyVpnService
import com.vauth.foxyvpn.ui.screens.HomeScreen
import com.vauth.foxyvpn.ui.screens.LoginScreen
import com.vauth.foxyvpn.ui.screens.LogsScreen
import com.vauth.foxyvpn.ui.screens.ServerListScreen
import com.vauth.foxyvpn.ui.screens.SettingsScreen
import com.vauth.foxyvpn.ui.screens.SplashScreen
import com.vauth.foxyvpn.ui.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TabRoutes = setOf("home", "servers", "settings")

object FoxyRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SERVERS = "servers"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
}

@Composable
fun FoxyNavGraph(
    navController: NavHostController = rememberNavController(),
    container: AppContainer,
    themeController: ThemeController,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSwitchServer: () -> Unit,
) {
    val context = LocalContext.current
    val sessionScope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }
    // Shared-axis motion: screens slide a short distance and cross-fade, with
    // the outgoing one gone before the incoming one is fully opaque.
    val shift = with(LocalDensity.current) { 30.dp.roundToPx() }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // Tabs exist once the user is past sign-in, i.e. Home is on the stack.
    val tabbed =
        remember(entry) {
            route in TabRoutes && runCatching { navController.getBackStackEntry(FoxyRoutes.HOME) }.isSuccess
        }
    fun openTab(target: String) {
        if (target == FoxyRoutes.HOME) {
            navController.popBackStack(FoxyRoutes.HOME, inclusive = false)
        } else {
            navController.navigate(target) {
                popUpTo(FoxyRoutes.HOME)
                launchSingleTop = true
            }
        }
    }
    val screens = rememberGlassSource()
    val topBar = remember { TopBarHost() }
    LaunchedEffect(route) { GlassClock.keepLive(900L) }
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().glassSource(screens)) {
    CompositionLocalProvider(LocalGlassContent provides screens, LocalTopBarHost provides topBar) {
    NavHost(
        navController = navController,
        startDestination = FoxyRoutes.SPLASH,
        enterTransition = {
            slideInHorizontally(tween(320, easing = EmphasizedEasing)) { shift } +
                fadeIn(tween(210, delayMillis = 90))
        },
        exitTransition = {
            slideOutHorizontally(tween(320, easing = EmphasizedEasing)) { -shift } + fadeOut(tween(90))
        },
        popEnterTransition = {
            slideInHorizontally(tween(320, easing = EmphasizedEasing)) { -shift } +
                fadeIn(tween(210, delayMillis = 90))
        },
        popExitTransition = {
            slideOutHorizontally(tween(320, easing = EmphasizedEasing)) { shift } + fadeOut(tween(90))
        },
    ) {
        composable(FoxyRoutes.SPLASH) {
            SplashScreen(
                authRepository = container.authRepository,
                onSignedIn = {
                    navController.navigate(FoxyRoutes.HOME) {
                        popUpTo(FoxyRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNeedsLogin = {
                    navController.navigate(FoxyRoutes.LOGIN) {
                        popUpTo(FoxyRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(FoxyRoutes.LOGIN) {
            LoginScreen(
                authRepository = container.authRepository,
                themeController = themeController,
                onSignedIn = {
                    navController.navigate(FoxyRoutes.HOME) {
                        popUpTo(FoxyRoutes.LOGIN) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(FoxyRoutes.SETTINGS) },
            )
        }
        composable(FoxyRoutes.HOME) {
            val homeModel: HomeViewModel =
                viewModel(
                    factory =
                        HomeViewModel.factory(
                            settingsStore = container.settingsStore,
                            guardianClient = container.guardianClient,
                            authRepository = container.authRepository,
                            connectivity = context.getSystemService(ConnectivityManager::class.java),
                        )
                )
            HomeScreen(
                proxyStateStore = container.proxyStateStore,
                homeModel = homeModel,
                themeController = themeController,
                onRequestConnect = onRequestConnect,
                onDisconnect = onDisconnect,
                onOpenServers = { openTab(FoxyRoutes.SERVERS) },
            )
        }
        composable(FoxyRoutes.SERVERS) {
            val serverListModel: ServerListViewModel =
                viewModel(
                    factory =
                        ServerListViewModel.factory(
                            serverListClient = container.serverListClient,
                            proxyStateStore = container.proxyStateStore,
                            loadFailedMessage = context.getString(R.string.server_list_load_failed),
                        )
                )
            GlassScreen {
            ServerListScreen(
                viewModel = serverListModel,
                proxyStateStore = container.proxyStateStore,
                onServerSelected = {
                    navController.popBackStack()
                    // Picking another location while connected moves the tunnel there right away.
                    if (
                        FoxyVpnService.state.value != ConnectionState.DISCONNECTED &&
                            container.proxyStateStore.selectedProxyFlow.value != FoxyVpnService.activeCandidate.value
                    ) {
                        onSwitchServer()
                    }
                },
                onBack = { navController.popBackStack() },
                showBack = !tabbed,
            )
            }
        }
        composable(FoxyRoutes.SETTINGS) {
            val app = context.applicationContext as? com.vauth.foxyvpn.FoxyVpnApp
            val updater = app?.appUpdater
            var autoUpdates by remember { mutableStateOf(updater?.automaticChecks ?: false) }
            val manualUpdate by updater?.manualUpdate?.collectAsStateWithLifecycle()
                ?: remember { mutableStateOf(null) }
            val isSignedIn = remember(container.tokenStore.sessionEpoch) { container.tokenStore.hasStoredSession() }
            GlassScreen {
            SettingsScreen(
                settingsStore = container.settingsStore,
                isSignedIn = isSignedIn,
                onOpenLogs = { navController.navigate(FoxyRoutes.LOGS) },
                signOutInProgress = signingOut,
                onCheckForUpdates = {
                    updater?.check(automatic = false)
                },
                manualUpdate = manualUpdate,
                onDismissUpdateDialog = { updater?.dismissManualUpdate() },
                automaticUpdates = autoUpdates,
                onAutomaticUpdatesChange = { enabled ->
                    updater?.automaticChecks = enabled
                    autoUpdates = enabled
                },
                onSignOut = signOut@{
                    if (signingOut) return@signOut
                    signingOut = true
                    val epoch = container.tokenStore.sessionEpoch
                    onDisconnect()
                    sessionScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                check(container.tokenStore.clearIfCurrent(epoch)) {
                                    context.getString(R.string.session_changed_sign_out)
                                }
                                com.vauth.foxyvpn.data.ControlPlaneHttp.cookieJar.clear()
                                container.settingsStore.clearAccountSession()
                                container.proxyStateStore.clear()
                            }
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                            navController.navigate(FoxyRoutes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                error.message ?: context.getString(R.string.session_clear_failed),
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            signingOut = false
                        }
                    }
                },
                onBack = { navController.popBackStack() },
                showBack = !tabbed,
            )
            }
        }
        composable(FoxyRoutes.LOGS) { GlassScreen { LogsScreen(onBack = { navController.popBackStack() }) } }
    }
    }
    }
        GlassTopBarHost(topBar, over = screens)
        AnimatedVisibility(
            visible = tabbed,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(320, easing = EmphasizedEasing)) { it },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(220)) { it },
        ) {
            GlassTabBar(
                tabs =
                    listOf(
                        GlassTab(FoxyRoutes.HOME, R.drawable.lucide_house, stringResource(R.string.tab_home)),
                        GlassTab(FoxyRoutes.SERVERS, R.drawable.lucide_map_pin, stringResource(R.string.tab_locations)),
                        GlassTab(FoxyRoutes.SETTINGS, R.drawable.lucide_settings, stringResource(R.string.settings_title)),
                    ),
                selected = route,
                onSelect = ::openTab,
                modifier = Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 14.dp),
                over = screens,
            )
        }
    }
}
