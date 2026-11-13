package com.vauth.foxyvpn

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.vauth.foxyvpn.ui.components.GlassClock
import com.vauth.foxyvpn.ui.components.LocalThemeReveal
import com.vauth.foxyvpn.ui.components.ThemeReveal
import com.vauth.foxyvpn.ui.components.ThemeRevealOverlay
import com.vauth.foxyvpn.ui.components.glassTouchWatch
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauth.foxyvpn.ui.components.FlameBackdrop
import com.vauth.foxyvpn.ui.components.LocalGlassBackdrop
import androidx.compose.runtime.remember
import com.vauth.foxyvpn.ui.components.GlassBackdrop
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.vauth.foxyvpn.data.FoxyRemoteConfig
import com.vauth.foxyvpn.data.model.ConnectionState
import com.vauth.foxyvpn.ui.navigation.FoxyNavGraph
import com.vauth.foxyvpn.ui.theme.FoxyVpnTheme
import com.vauth.foxyvpn.ui.theme.rememberThemeController
import com.vauth.foxyvpn.vpn.FoxyVpnService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.vauth.foxyvpn.ui.settings.AppLanguage.wrap(newBase))
    }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                requestNotificationPermissionIfNeeded()
                FoxyVpnService.start(this)
            } else {

                Toast.makeText(
                        this,
                        getString(R.string.vpn_permission_denied_help),
                        Toast.LENGTH_LONG,
                    )
                    .show()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun requestConnect() {
        if ((application as FoxyVpnApp).settingsStore.proxyOnlyMode) {
            requestNotificationPermissionIfNeeded()
            FoxyVpnService.start(this)
            return
        }

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            requestNotificationPermissionIfNeeded()
            FoxyVpnService.start(this)
        }
    }

    private var switchJob: Job? = null

    /** Restarts the tunnel so it picks up a newly selected location. */
    private fun switchServer() {
        if (FoxyVpnService.state.value == ConnectionState.DISCONNECTED) return
        switchJob?.cancel()
        switchJob =
            lifecycleScope.launch {
                FoxyVpnService.stop(this@MainActivity)
                val stopped =
                    withTimeoutOrNull(10_000L) {
                        FoxyVpnService.state.first { it == ConnectionState.DISCONNECTED }
                    }
                if (stopped != null) requestConnect()
            }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        FoxyRemoteConfig.triggerRefresh()
        (application as FoxyVpnApp).appUpdater.check(automatic = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FoxyVpnApp
        setContent {
            val themeController = rememberThemeController(app.settingsStore)
            FoxyVpnTheme(themeMode = themeController.mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val backdrop = remember { GlassBackdrop() }
                    val reveal = remember { ThemeReveal() }
                    // The glass clock only runs while the app is actually on screen.
                    LaunchedEffect(Unit) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { GlassClock.run() }
                    }
                    val vpnState by FoxyVpnService.state.collectAsStateWithLifecycle()
                    Box(Modifier.fillMaxSize().glassTouchWatch()) {
                        FlameBackdrop(
                            state = backdrop,
                            modifier = Modifier.fillMaxSize(),
                            intensity =
                                when (vpnState) {
                                    ConnectionState.CONNECTED -> 1f
                                    ConnectionState.CONNECTING -> 0.6f
                                    ConnectionState.DISCONNECTED -> 0.2f
                                },
                        )
                        CompositionLocalProvider(
                            LocalGlassBackdrop provides backdrop,
                            LocalThemeReveal provides reveal,
                        ) {
                    val navController = rememberNavController()
                    FoxyNavGraph(
                        navController = navController,
                        container = app,
                        themeController = themeController,
                        onRequestConnect = { requestConnect() },
                        onDisconnect = {
                            switchJob?.cancel()
                            FoxyVpnService.stop(this@MainActivity)
                        },
                        onSwitchServer = { switchServer() },
                    )
                        }
                        ThemeRevealOverlay(reveal)
                    }
                }
            }
        }
    }
}
