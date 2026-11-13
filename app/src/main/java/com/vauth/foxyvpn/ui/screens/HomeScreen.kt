package com.vauth.foxyvpn.ui.screens

import com.vauth.foxyvpn.ui.components.LocalGlassContent
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import com.vauth.foxyvpn.ui.components.GlassTopBar
import com.vauth.foxyvpn.ui.components.LocalThemeReveal
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.ProxyStateStore
import com.vauth.foxyvpn.data.ThemeMode
import com.vauth.foxyvpn.data.formatBytes
import com.vauth.foxyvpn.data.model.ConnectionState
import com.vauth.foxyvpn.data.model.RECOMMENDED_COUNTRY_CODE
import com.vauth.foxyvpn.data.model.countryCodeToEmojiFlag
import com.vauth.foxyvpn.data.model.displayName
import com.vauth.foxyvpn.data.model.emojiFlag
import com.vauth.foxyvpn.ui.HomeViewModel
import com.vauth.foxyvpn.ui.components.FoxConnectButton
import com.vauth.foxyvpn.ui.components.FoxMascot
import com.vauth.foxyvpn.ui.components.FoxMood
import com.vauth.foxyvpn.ui.components.RollingText
import com.vauth.foxyvpn.ui.components.RowDivider
import com.vauth.foxyvpn.ui.components.SectionCard
import com.vauth.foxyvpn.ui.localizedCountryName
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

    val isConnected = state == ConnectionState.CONNECTED
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

    val content = LocalGlassContent.current
    val reveal = LocalThemeReveal.current
    val rootView = LocalView.current
    var themeButtonCenter by remember { mutableStateOf(Offset.Zero) }
    fun switchTheme(change: () -> Unit) {
        if (reveal != null) reveal.start(rootView, themeButtonCenter, change) else change()
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val scrollState = rememberScrollState()
        // Centre the hero on tall screens instead of leaving all the slack at the bottom.
        val slack = (maxHeight - 760.dp).coerceAtLeast(0.dp)

        Column(
            modifier =
                Modifier.widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Room for the pinned header.
            Spacer(Modifier.height(72.dp))

            Spacer(Modifier.height(20.dp + slack / 2))

            // Hero
            FoxConnectButton(
                state = state,
                contentDescription =
                    if (state == ConnectionState.DISCONNECTED) stringResource(R.string.home_connect)
                    else stringResource(R.string.home_disconnect),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (state == ConnectionState.DISCONNECTED) onRequestConnect() else onDisconnect()
                },
                size = 196.dp,
            )

            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 70)) + slideInVertically(tween(300)) { it / 5 }) togetherWith
                        (fadeOut(tween(100)) + slideOutVertically(tween(160)) { -it / 8 })
                },
                label = "status",
            ) { current ->
                Text(
                    text =
                        when (current) {
                            ConnectionState.CONNECTED -> stringResource(R.string.quick_connect_connected)
                            ConnectionState.CONNECTING -> stringResource(R.string.quick_connect_connecting)
                            ConnectionState.DISCONNECTED -> stringResource(R.string.quick_connect_disconnected)
                        },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(Modifier.height(2.dp))
            when (state) {
                ConnectionState.CONNECTED ->
                    RollingText(
                        text = formatDuration(elapsedSeconds),
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondary,
                    )
                ConnectionState.CONNECTING ->
                    Text(
                        if (lastError != null) stringResource(R.string.home_retrying_tap_to_cancel)
                        else stringResource(R.string.home_tap_to_cancel),
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondary,
                    )
                ConnectionState.DISCONNECTED ->
                    Text(
                        stringResource(R.string.home_tap_to_connect),
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondary,
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

            Spacer(Modifier.height(24.dp + slack / 2))

            // Connection details
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    val locationName =
                        if (displayProxy != null) {
                            localizedCountryName(displayProxy.countryCode, displayProxy.displayName)
                        } else {
                            stringResource(R.string.home_recommended)
                        }
                    val exitCode = vpnEdgeIp?.countryCode.orEmpty()
                    val onRecommended =
                        displayProxy == null ||
                            displayProxy.countryCode.equals(RECOMMENDED_COUNTRY_CODE, ignoreCase = true)
                    val pendingSwitch =
                        state != ConnectionState.DISCONNECTED &&
                            activeProxy != null &&
                            selectedProxy != null &&
                            activeProxy != selectedProxy
                    InfoRow(
                        label = stringResource(R.string.home_location),
                        supporting =
                            when {
                                pendingSwitch ->
                                    stringResource(
                                        R.string.home_next_connection,
                                        localizedCountryName(
                                            selectedProxy?.countryCode.orEmpty(),
                                            selectedProxy?.displayName.orEmpty(),
                                        ),
                                    )
                                isConnected && onRecommended && exitCode.length == 2 ->
                                    stringResource(
                                        R.string.home_connected_to,
                                        "${countryCodeToEmojiFlag(exitCode)} ${localizedCountryName(exitCode, exitCode)}"
                                            .trim(),
                                    )
                                else -> null
                            },
                        onClick = onOpenServers,
                    ) {
                        val flag = displayProxy?.emojiFlag
                        if (!flag.isNullOrBlank()) {
                            Text(flag, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            locationName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            painterResource(R.drawable.lucide_chevron_right),
                            contentDescription = null,
                            tint = secondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    RowDivider()

                    val protectionColor by
                        animateColorAsState(
                            targetValue = if (isConnected) statusColors.connected else MaterialTheme.colorScheme.error,
                            label = "protection-color",
                        )
                    InfoRow(label = stringResource(R.string.home_protection)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(protectionColor))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                isConnected && proxyOnlyActive -> stringResource(R.string.home_badge_proxy_active)
                                isConnected -> stringResource(R.string.home_badge_protected)
                                else -> stringResource(R.string.home_badge_exposed)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = protectionColor,
                        )
                    }
                    RowDivider()

                    val address = if (isConnected) vpnEdgeIp else realIp
                    InfoRow(label = stringResource(R.string.home_current_ip)) {
                        Text(
                            address?.ip?.let(::shortenAddress) ?: "\u2014",
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondary,
                            maxLines = 1,
                        )
                    }
                    RowDivider()

                    val currentPing = if (isConnected) pingLatency else null
                    InfoRow(
                        label = stringResource(R.string.home_latency),
                        onClick =
                            if (isConnected && !isPinging) {
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    homeModel.triggerPing()
                                }
                            } else {
                                null
                            },
                    ) {
                        RollingText(
                            text =
                                when {
                                    isPinging -> stringResource(R.string.home_pinging)
                                    currentPing != null -> stringResource(R.string.unit_ms, currentPing)
                                    else -> stringResource(R.string.home_ping_none)
                                },
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondary,
                        )
                    }
                    RowDivider()

                    InfoRow(label = stringResource(R.string.home_data_used)) {
                        RollingText(
                            text = formatBytes(if (isConnected) liveStats.sessionBytes else 0L),
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Monthly quota
            SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    val remaining = quotaRemaining
                    val max = quotaMax
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.home_quota_remaining),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when {
                                remaining != null && max != null ->
                                    stringResource(R.string.home_quota_of, formatBytes(remaining), formatBytes(max))
                                remaining != null -> formatBytes(remaining)
                                isUnlimited -> stringResource(R.string.home_quota_unlimited)
                                max != null -> stringResource(R.string.home_quota_limited_to, formatBytes(max))
                                else -> "\u2014"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = secondary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val fraction =
                        when {
                            max != null && max > 0L && remaining != null ->
                                (remaining.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                            isUnlimited -> 1f
                            remaining != null && remaining > 0L -> 1f
                            else -> 0f
                        }
                    val shown by
                        animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = tween(900, easing = FastOutSlowInEasing),
                            label = "quota",
                        )
                    LinearProgressIndicator(
                        progress = { shown },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    )
                }
            }

            // Room for the floating tab bar.
            Spacer(Modifier.height(120.dp))
            Spacer(Modifier.navigationBarsPadding())
        }

        GlassTopBar(
            title = stringResource(R.string.app_name),
            onBack = null,
            over = content,
            modifier = Modifier.widthIn(max = 520.dp),
            leading = {
                FoxMascot(mood = FoxMood.GUARDING, modifier = Modifier.size(26.dp), animated = false)
            },
        ) {
            val mode = themeController.mode
            val showingDark = themeController.resolveDark(systemInDarkTheme)
            Box(
                modifier =
                    Modifier.size(44.dp)
                        .onGloballyPositioned { themeButtonCenter = it.boundsInRoot().center }
                        .clip(CircleShape)
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { switchTheme { themeController.toggle(systemInDarkTheme) } },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                switchTheme { themeController.set(ThemeMode.SYSTEM) }
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            when (mode) {
                                ThemeMode.SYSTEM -> R.drawable.lucide_sun_moon
                                ThemeMode.LIGHT -> R.drawable.lucide_sun
                                ThemeMode.DARK -> R.drawable.lucide_moon
                            }
                        ),
                    contentDescription =
                        when (mode) {
                            ThemeMode.SYSTEM ->
                                if (showingDark) stringResource(R.string.theme_desc_system_light)
                                else stringResource(R.string.theme_desc_system_dark)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_desc_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_desc_dark)
                        },
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

/** One line of a details card: label on the left, value on the right. */
@Composable
private fun InfoRow(
    label: String,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    value: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 54.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = value)
    }
}

/** Long IPv6 addresses do not fit a row; keep both ends, which is what people compare. */
private fun shortenAddress(ip: String): String =
    if (ip.length <= 20) ip else ip.take(9) + "\u2026" + ip.takeLast(8)

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
