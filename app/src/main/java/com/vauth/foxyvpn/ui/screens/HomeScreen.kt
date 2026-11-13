package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.background
import com.vauth.foxyvpn.ui.components.GlassIconButton
import com.vauth.foxyvpn.ui.components.GlassRim
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.ProxyStateStore
import com.vauth.foxyvpn.data.ThemeMode
import com.vauth.foxyvpn.data.formatBytes
import com.vauth.foxyvpn.data.model.ConnectionState
import com.vauth.foxyvpn.data.model.displayName
import com.vauth.foxyvpn.data.model.emojiFlag
import com.vauth.foxyvpn.ui.HomeViewModel
import com.vauth.foxyvpn.ui.components.EmphasizedEasing
import com.vauth.foxyvpn.ui.components.FoxConnectButton
import com.vauth.foxyvpn.ui.components.FoxMascot
import com.vauth.foxyvpn.ui.components.FoxMood
import com.vauth.foxyvpn.ui.components.GlassSurface
import com.vauth.foxyvpn.ui.components.RollingText
import com.vauth.foxyvpn.ui.localizedCountryName
import com.vauth.foxyvpn.ui.theme.FoxOrange
import com.vauth.foxyvpn.ui.theme.LocalFoxyStatusColors
import com.vauth.foxyvpn.ui.theme.ThemeController
import com.vauth.foxyvpn.vpn.FoxyVpnService
import com.vauth.foxyvpn.vpn.IpLocationInfo
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    proxyStateStore: ProxyStateStore,
    homeModel: HomeViewModel,
    themeController: ThemeController,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by FoxyVpnService.state.collectAsStateWithLifecycle()
    val proxyOnlyActive by FoxyVpnService.proxyOnlyActive.collectAsStateWithLifecycle()
    val lastError by FoxyVpnService.lastError.collectAsStateWithLifecycle()
    val liveStats by FoxyVpnService.vpnLiveStats.collectAsStateWithLifecycle()
    val connectedAtElapsedMs by FoxyVpnService.connectedAtElapsedMs.collectAsStateWithLifecycle()
    val pingLatency by FoxyVpnService.pingLatencyMs.collectAsStateWithLifecycle()
    val vpnEdgeIp by FoxyVpnService.vpnEdgeIp.collectAsStateWithLifecycle()
    val isPinging by homeModel.isPinging.collectAsStateWithLifecycle()

    val selectedProxy by proxyStateStore.selectedProxyFlow.collectAsStateWithLifecycle()
    val activeProxy by FoxyVpnService.activeCandidate.collectAsStateWithLifecycle()
    val displayProxy =
        if (state == ConnectionState.DISCONNECTED) selectedProxy else activeProxy ?: selectedProxy

    val statusColors = LocalFoxyStatusColors.current
    val statusColor by
        animateColorAsState(
            targetValue =
                when (state) {
                    ConnectionState.CONNECTED -> statusColors.connected
                    ConnectionState.CONNECTING -> statusColors.connecting
                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurface
                },
            label = "status-color",
        )

    val systemInDarkTheme = isSystemInDarkTheme()
    val haptics = LocalHapticFeedback.current

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    val account by homeModel.account.collectAsStateWithLifecycle()
    val realIp = account.realIp?.let { IpLocationInfo(it, account.country.orEmpty()) }
    val quotaRemaining = account.quotaRemaining
    val quotaMax = account.quotaMax
    val isUnlimited = account.unlimited

    LaunchedEffect(Unit) {
        homeModel.refreshAccount()
    }

    LaunchedEffect(state) {
        if (state == ConnectionState.CONNECTED) {
            homeModel.refreshAccount()
        }
    }

    LaunchedEffect(state, connectedAtElapsedMs) {
        val startEpoch = connectedAtElapsedMs
        if (state == ConnectionState.CONNECTED && startEpoch != null) {
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                elapsedSeconds = ((now - startEpoch) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    Box(Modifier.fillMaxSize()) {

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FoxMascot(
                                mood = FoxMood.GUARDING,
                                modifier = Modifier.size(30.dp),
                                animated = false,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            )
                        }
                    },
                    actions = {
                        val mode = themeController.mode
                        val showingDark = themeController.resolveDark(systemInDarkTheme)
                        GlassSurface(modifier = Modifier.size(44.dp), shape = CircleShape, rim = GlassRim.Vertical) {
Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClick = { themeController.toggle(systemInDarkTheme) },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            themeController.set(ThemeMode.SYSTEM)
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = mode,
                                transitionSpec = {
                                    (fadeIn(tween(200, delayMillis = 60)) +
                                        scaleIn(tween(260, easing = EmphasizedEasing), initialScale = 0.6f)) togetherWith
                                        (fadeOut(tween(100)) + scaleOut(tween(160), targetScale = 0.6f))
                                },
                                label = "theme-icon",
                            ) { current ->
                                Icon(
                                    painter =
                                        when (current) {
                                            ThemeMode.SYSTEM -> painterResource(R.drawable.lucide_sun_moon)
                                            ThemeMode.LIGHT -> painterResource(R.drawable.lucide_sun)
                                            ThemeMode.DARK -> painterResource(R.drawable.lucide_moon)
                                        },
                                    contentDescription =
                                        when (current) {
                                            ThemeMode.SYSTEM ->
                                                if (showingDark) {
                                                    stringResource(R.string.theme_desc_system_light)
                                                } else {
                                                    stringResource(R.string.theme_desc_system_dark)
                                                }
                                            ThemeMode.LIGHT -> stringResource(R.string.theme_desc_light)
                                            ThemeMode.DARK -> stringResource(R.string.theme_desc_dark)
                                        },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
}
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(onClick = onOpenSettings, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.lucide_settings),
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                val scrollState = rememberScrollState()
                val naturalContentHeight = 640.dp
                val availableExtra = (maxHeight - naturalContentHeight).coerceAtLeast(0.dp)
                val dynamicSpace = if (availableExtra > 0.dp) (availableExtra / 2) else 8.dp

                Column(
                    modifier =
                        Modifier.widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .verticalScroll(scrollState, enabled = scrollState.maxValue > 0)
                            .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(dynamicSpace))

                    FoxConnectButton(
                        state = state,
                        contentDescription =
                            if (state == ConnectionState.DISCONNECTED) {
                                stringResource(R.string.home_connect)
                            } else {
                                stringResource(R.string.home_disconnect)
                            },
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state == ConnectionState.DISCONNECTED) onRequestConnect() else onDisconnect()
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            (fadeIn(tween(240, delayMillis = 80)) +
                                slideInVertically(tween(360, easing = EmphasizedEasing)) { it / 4 }) togetherWith
                                (fadeOut(tween(110)) + slideOutVertically(tween(160)) { -it / 6 })
                        },
                        label = "status-text",
                    ) { current ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text =
                                    when (current) {
                                        ConnectionState.CONNECTED -> stringResource(R.string.quick_connect_connected)
                                        ConnectionState.CONNECTING -> stringResource(R.string.quick_connect_connecting)
                                        ConnectionState.DISCONNECTED -> stringResource(R.string.quick_connect_disconnected)
                                    },
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = statusColor,
                            )
                            Text(
                                text =
                                    when (current) {
                                        ConnectionState.CONNECTED -> stringResource(R.string.home_mascot_guarding)
                                        ConnectionState.CONNECTING -> stringResource(R.string.home_mascot_searching)
                                        ConnectionState.DISCONNECTED -> stringResource(R.string.home_mascot_resting)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    when (state) {
                        ConnectionState.CONNECTING ->
                            Text(
                                if (lastError != null) {
                                    stringResource(R.string.home_retrying_tap_to_cancel)
                                } else {
                                    stringResource(R.string.home_tap_to_cancel)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        ConnectionState.CONNECTED ->
                            RollingText(
                                text = formatDuration(elapsedSeconds),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        ConnectionState.DISCONNECTED ->
                            Text(
                                stringResource(R.string.home_tap_to_connect),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }

                    val error = lastError
                    AnimatedVisibility(
                        visible = error != null && state != ConnectionState.CONNECTING,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Text(
                            text = error.orEmpty(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp),
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Location
                        GlassSurface(modifier = Modifier.fillMaxWidth(), onClick = onOpenServers) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier.size(44.dp)
                                            .clip(CircleShape)
                                            .background(FoxOrange.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val flag = displayProxy?.emojiFlag
                                    if (!flag.isNullOrBlank()) {
                                        Text(flag, style = MaterialTheme.typography.titleLarge)
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.lucide_globe),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.home_location),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val displayName =
                                        if (displayProxy != null) {
                                            localizedCountryName(displayProxy.countryCode, displayProxy.displayName)
                                        } else {
                                            stringResource(R.string.home_recommended)
                                        }
                                    AnimatedContent(
                                        targetState = displayName,
                                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                                        label = "location-name",
                                    ) { name ->
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                    if (
                                        state != ConnectionState.DISCONNECTED &&
                                            activeProxy != null &&
                                            selectedProxy != null &&
                                            activeProxy != selectedProxy
                                    ) {
                                        val nextDisplayName =
                                            localizedCountryName(
                                                selectedProxy?.countryCode.orEmpty(),
                                                selectedProxy?.displayName.orEmpty(),
                                            )
                                        Text(
                                            stringResource(R.string.home_next_connection, nextDisplayName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    painterResource(R.drawable.lucide_chevron_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                    Spacer(Modifier.height(12.dp))

                    // IP + protection badge
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            val isConnected = state == ConnectionState.CONNECTED
                            val ipDisplay =
                                if (isConnected) {
                                    vpnEdgeIp?.let {
                                        if (it.countryCode.isNotEmpty()) "${it.ip} • ${it.countryCode}" else it.ip
                                    } ?: "—"
                                } else {
                                    realIp?.let {
                                        if (it.countryCode.isNotEmpty()) "${it.ip} • ${it.countryCode}" else it.ip
                                    } ?: "—"
                                }
                            val badgeColor by
                                animateColorAsState(
                                    targetValue =
                                        if (isConnected) statusColors.connected else MaterialTheme.colorScheme.error,
                                    label = "badge-color",
                                )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier.size(44.dp).clip(CircleShape).background(badgeColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AnimatedContent(
                                        targetState = isConnected,
                                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                                        label = "shield-icon",
                                    ) { isProtected ->
                                        Icon(
                                            if (isProtected) painterResource(R.drawable.lucide_shield_check) else painterResource(R.drawable.lucide_shield_alert),
                                            contentDescription = null,
                                            tint = badgeColor,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.home_current_ip),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    AnimatedContent(
                                        targetState = ipDisplay,
                                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                                        label = "ip-text",
                                    ) { ip ->
                                        Text(
                                            ip,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier =
                                        Modifier.clip(RoundedCornerShape(50))
                                            .background(badgeColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        when {
                                            isConnected && proxyOnlyActive -> stringResource(R.string.home_badge_proxy_active)
                                            isConnected -> stringResource(R.string.home_badge_protected)
                                            else -> stringResource(R.string.home_badge_exposed)
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = badgeColor,
                                    )
                                }
                            }
                        }

                    Spacer(Modifier.height(12.dp))

                    // Traffic, latency & quota
                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                val isConnected = state == ConnectionState.CONNECTED
                                val remaining = quotaRemaining
                                val max = quotaMax

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    StatTile(
                                        icon = painterResource(R.drawable.lucide_arrow_down_up),
                                        label = stringResource(R.string.home_data_used),
                                        value = formatBytes(if (isConnected) liveStats.sessionBytes else 0L),
                                        modifier = Modifier.weight(1f),
                                    )
                                    val currentPing = if (isConnected) pingLatency else null
                                    StatTile(
                                        icon = painterResource(R.drawable.lucide_gauge),
                                        label = stringResource(R.string.home_latency),
                                        value =
                                            when {
                                                isPinging -> stringResource(R.string.home_pinging)
                                                currentPing != null -> stringResource(R.string.unit_ms, currentPing)
                                                else -> stringResource(R.string.home_ping_none)
                                            },
                                        valueColor =
                                            if (currentPing != null) statusColors.connected
                                            else MaterialTheme.colorScheme.onSurface,
                                        modifier =
                                            Modifier.weight(1f)
                                                .heightIn(min = 48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable(enabled = isConnected && !isPinging, role = Role.Button) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    homeModel.triggerPing()
                                                },
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.home_quota_remaining),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    val quotaText =
                                        when {
                                            remaining != null && max != null ->
                                                "${formatBytes(remaining)} / ${formatBytes(max)}"
                                            remaining != null -> formatBytes(remaining)
                                            isUnlimited -> stringResource(R.string.home_quota_unlimited)
                                            max != null -> stringResource(R.string.home_quota_limited_to, formatBytes(max))
                                            else -> "—"
                                        }
                                    Text(
                                        quotaText,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                val quotaFraction =
                                    when {
                                        max != null && max > 0L && remaining != null ->
                                            (remaining.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                                        isUnlimited -> 1f
                                        remaining != null && remaining > 0L -> 1f
                                        else -> 0f
                                    }
                                val animatedFraction by
                                    animateFloatAsState(
                                        targetValue = quotaFraction,
                                        animationSpec = tween(900, easing = FastOutSlowInEasing),
                                        label = "quota-fraction",
                                    )

                                LinearProgressIndicator(
                                    progress = { animatedFraction },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = if (isConnected) statusColors.connected else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            }
                        }

                    Spacer(Modifier.height(dynamicSpace))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: Painter,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            RollingText(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
