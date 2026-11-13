package com.vauth.foxyvpn.ui.screens

import com.vauth.foxyvpn.ui.components.GlassTextButton
import com.vauth.foxyvpn.ui.components.GlassAlertDialog
import com.vauth.foxyvpn.ui.components.GlassButton
import com.vauth.foxyvpn.ui.components.GlassGroup
import com.vauth.foxyvpn.ui.components.GlassTopBar
import com.vauth.foxyvpn.ui.components.glassSource
import com.vauth.foxyvpn.ui.components.rememberGlassSource
import androidx.compose.ui.res.painterResource
import android.content.Intent
import com.vauth.foxyvpn.ui.localizedLabel
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vauth.foxyvpn.BuildConfig
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.FoxyRemoteConfig
import com.vauth.foxyvpn.data.ProxySettingsRepository
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.ui.ProxyEditor
import com.vauth.foxyvpn.ui.ProxySettingsViewModel
import com.vauth.foxyvpn.ui.settings.ConcurrencyPickerDialog
import com.vauth.foxyvpn.ui.settings.CustomEdgeAddressDialog
import com.vauth.foxyvpn.ui.settings.DnsProviderPickerDialog
import com.vauth.foxyvpn.ui.settings.HttpVersionPickerDialog
import com.vauth.foxyvpn.ui.settings.IpModePickerDialog
import com.vauth.foxyvpn.ui.settings.LatencyProbeDialog
import com.vauth.foxyvpn.ui.settings.MtuPickerDialog
import com.vauth.foxyvpn.ui.settings.ProxySettingsDialog
import com.vauth.foxyvpn.ui.settings.RelayedEndpointsDialog
import com.vauth.foxyvpn.ui.settings.RelayerProviderPickerDialog
import com.vauth.foxyvpn.ui.settings.SplitTunnelDialog
import com.vauth.foxyvpn.ui.settings.TlsFragmentPickerDialog
import com.vauth.foxyvpn.ui.settings.UtlsFingerprintPickerDialog
import java.util.Locale
import com.vauth.foxyvpn.update.ReleaseInfo
import com.vauth.foxyvpn.vpn.FoxyVpnService
import com.vauth.foxyvpn.vpn.TunnelHealth

private const val GITHUB_URL = com.vauth.foxyvpn.data.AppLinks.SOURCE

private enum class SettingsDialog {
    NONE,
    LATENCY_PROBE,
    TLS_FRAGMENT,
    UTLS_FINGERPRINT,
    CONCURRENCY,
    MTU,
    DNS_PROVIDER,
    BOOTSTRAP_DNS_PROVIDER,
    EDGE_ADDRESS,
    SPLIT_TUNNEL,
    HTTP_VERSION,
    IP_MODE,
    RELAYER_PROVIDER,
    RELAYER_ENDPOINTS,
}

@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onOpenLogs: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true,
    isSignedIn: Boolean = true,
    signOutInProgress: Boolean = false,
    onCheckForUpdates: (() -> Unit)? = null,
    manualUpdate: ReleaseInfo? = null,
    onDismissUpdateDialog: () -> Unit = {},
    automaticUpdates: Boolean = false,
    onAutomaticUpdatesChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    var automaticRecovery by remember { mutableStateOf(settingsStore.automaticRecovery) }
    val health by FoxyVpnService.tunnelHealth.collectAsStateWithLifecycle()
    var dnsProvider by remember { mutableStateOf(settingsStore.dnsProvider) }
    var customDohUrl by remember { mutableStateOf(settingsStore.customDohUrl) }
    var bootstrapDnsProvider by remember { mutableStateOf(settingsStore.bootstrapDnsProvider) }
    var customBootstrapDohUrl by remember { mutableStateOf(settingsStore.customBootstrapDohUrl) }
    var proxyOnlyMode by remember { mutableStateOf(settingsStore.proxyOnlyMode) }
    var customEdgeAddress by remember { mutableStateOf(settingsStore.customEdgeAddress) }
    var excludedApps by remember { mutableStateOf(settingsStore.excludedApps) }
    var includedApps by remember { mutableStateOf(settingsStore.includedApps) }
    var bypassLocalNetwork by remember { mutableStateOf(settingsStore.bypassLocalNetwork) }
    var splitTunnelMode by remember { mutableStateOf(settingsStore.splitTunnelMode) }
    var mtu by remember { mutableIntStateOf(settingsStore.mtu) }
    var latencyProbeUrl by remember { mutableStateOf(settingsStore.latencyProbeUrl) }
    var httpVersion by remember { mutableStateOf(settingsStore.httpVersion) }
    var tlsFragmentMode by remember { mutableStateOf(settingsStore.tlsFragmentMode) }
    var fragmentFallbackDelay by remember { mutableIntStateOf(settingsStore.fragmentFallbackDelay) }
    var utlsFingerprint by remember { mutableStateOf(settingsStore.utlsFingerprint) }
    var maxConcurrentStreams by remember { mutableIntStateOf(settingsStore.maxConcurrentStreams) }
    var ipMode by remember { mutableStateOf(settingsStore.ipMode) }
    var relayerProvider by remember { mutableStateOf(settingsStore.relayerProvider) }
    var customRelayerUrl by remember { mutableStateOf(settingsStore.customRelayerUrl) }
    var relayedEndpoints by remember { mutableStateOf(settingsStore.relayedEndpoints) }
    var activeDialog by rememberSaveable { mutableStateOf(SettingsDialog.NONE) }

    val proxyViewModel: ProxySettingsViewModel =
        viewModel(
            factory =
                remember(settingsStore) {
                    ProxySettingsViewModel.factory(ProxySettingsRepository(settingsStore))
                }
        )
    val proxyState by proxyViewModel.state.collectAsStateWithLifecycle()
    ProxySettingsDialog(
        proxyState,
        proxyViewModel::close,
        proxyViewModel::open,
        proxyViewModel::save,
        proxyViewModel::save,
    )

    if (manualUpdate != null) {
        val release = manualUpdate
        GlassAlertDialog(
            onDismissRequest = onDismissUpdateDialog,
            title = { Text(stringResource(R.string.update_dialog_title)) },
            text = {
                Text(stringResource(R.string.update_notification_text, release.tag))
            },
            confirmButton = {
                GlassButton(
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    onClick = {
                        onDismissUpdateDialog()
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        }.onFailure {
                            Toast.makeText(
                                context,
                                context.getString(R.string.browser_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.update_btn_view_release))
                }
            },
            dismissButton = {
                GlassTextButton(onClick = onDismissUpdateDialog) {
                    Text(stringResource(R.string.update_dialog_dismiss))
                }
            },
        )
    }

    when (activeDialog) {
        SettingsDialog.NONE -> {}
        SettingsDialog.LATENCY_PROBE -> LatencyProbeDialog(
            current = latencyProbeUrl,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = {
                settingsStore.latencyProbeUrl = it
                latencyProbeUrl = it
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.TLS_FRAGMENT -> TlsFragmentPickerDialog(
            currentMode = tlsFragmentMode,
            currentDelay = fragmentFallbackDelay,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = { mode, delay ->
                tlsFragmentMode = mode
                settingsStore.tlsFragmentMode = mode
                fragmentFallbackDelay = delay
                settingsStore.fragmentFallbackDelay = delay
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.UTLS_FINGERPRINT -> UtlsFingerprintPickerDialog(
            current = utlsFingerprint,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = {
                utlsFingerprint = it
                settingsStore.utlsFingerprint = it
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.CONCURRENCY -> ConcurrencyPickerDialog(
            current = maxConcurrentStreams,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = {
                maxConcurrentStreams = it
                settingsStore.maxConcurrentStreams = it
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.MTU -> MtuPickerDialog(
            current = mtu,
            minimum = if (ipMode.hasIpv6) 1280 else SettingsStore.MIN_MTU,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = {
                mtu = it
                settingsStore.mtu = it
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.DNS_PROVIDER -> DnsProviderPickerDialog(
            currentProvider = dnsProvider,
            currentCustomUrl = customDohUrl,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = { provider, url ->
                settingsStore.saveDns(provider, url)
                dnsProvider = provider
                customDohUrl = settingsStore.customDohUrl
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.BOOTSTRAP_DNS_PROVIDER -> DnsProviderPickerDialog(
            title = stringResource(R.string.settings_bootstrap_dns_provider),
            helpText = stringResource(R.string.dialog_dns_bootstrap_help),
            currentProvider = bootstrapDnsProvider,
            currentCustomUrl = customBootstrapDohUrl,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = { provider, url ->
                settingsStore.saveBootstrapDns(provider, url)
                bootstrapDnsProvider = provider
                customBootstrapDohUrl = settingsStore.customBootstrapDohUrl
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.EDGE_ADDRESS -> CustomEdgeAddressDialog(
            current = customEdgeAddress,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = {
                customEdgeAddress = it
                settingsStore.customEdgeAddress = it
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.SPLIT_TUNNEL -> SplitTunnelDialog(
            currentMode = splitTunnelMode,
            currentExcluded = excludedApps,
            currentIncluded = includedApps,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = { mode, excluded, included ->
                settingsStore.saveSplitTunneling(mode, excluded, included)
                splitTunnelMode = mode
                excludedApps = excluded
                includedApps = included
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.HTTP_VERSION -> HttpVersionPickerDialog(
            current = httpVersion,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = {
                httpVersion = it
                settingsStore.httpVersion = it
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.IP_MODE -> IpModePickerDialog(
            current = ipMode,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = {
                if (it.hasIpv6 && mtu < 1280) {
                    settingsStore.mtu = 1280
                    mtu = 1280
                }
                settingsStore.ipMode = it
                ipMode = it
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.RELAYER_PROVIDER -> RelayerProviderPickerDialog(
            currentProvider = relayerProvider,
            currentCustomUrl = customRelayerUrl,
            availableRelayers = FoxyRemoteConfig.current.relayers,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = { provider, customUrl ->
                settingsStore.saveRelayer(provider, customUrl)
                relayerProvider = provider
                customRelayerUrl = settingsStore.customRelayerUrl
                activeDialog = SettingsDialog.NONE
            },
        )
        SettingsDialog.RELAYER_ENDPOINTS -> RelayedEndpointsDialog(
            currentEndpoints = relayedEndpoints,
            onDismiss = { activeDialog = SettingsDialog.NONE },
            onConfirm = { endpoints ->
                settingsStore.relayedEndpoints = endpoints
                relayedEndpoints = endpoints
                activeDialog = SettingsDialog.NONE
            },
        )
    }

    var appLanguage by remember { mutableStateOf(com.vauth.foxyvpn.ui.settings.AppLanguage.current(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    if (showLanguageDialog) {
        com.vauth.foxyvpn.ui.settings.LanguagePickerDialog(
            current = appLanguage,
            onDismiss = { showLanguageDialog = false },
            onConfirm = { tag ->
                showLanguageDialog = false
                if (tag != appLanguage) {
                    appLanguage = tag
                    (context as? android.app.Activity)?.let {
                        com.vauth.foxyvpn.ui.settings.AppLanguage.set(it, tag)
                    }
                }
            },
        )
    }

    val listContent = rememberGlassSource()
    Scaffold(
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.settings_title),
                onBack = if (showBack) onBack else null,
                over = listContent,
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .glassSource(listContent)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 120.dp)
        ) {
            SectionLabel(stringResource(R.string.settings_section_appearance))
            GlassGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = { Text(com.vauth.foxyvpn.ui.settings.languageLabel(appLanguage)) },
                leadingContent = {
                    Icon(painterResource(R.drawable.lucide_languages), contentDescription = null)
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { showLanguageDialog = true },
            )

            }
            SectionLabel(stringResource(R.string.settings_section_connection))
            GlassGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_always_on_vpn)) },
                supportingContent = { Text(stringResource(R.string.settings_always_on_help)) },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier =
                    Modifier.clickable {
                        runCatching {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
                                )
                            }
                            .onFailure {
                                android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_vpn_unavailable),
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                    },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_latency_test_url)) },
                supportingContent = { Text(latencyProbeUrl) },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.LATENCY_PROBE },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_tunnel_mtu)) },
                supportingContent = { Text(mtu.toString()) },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.MTU },
            )

            }
            SectionLabel(stringResource(R.string.settings_section_dns))
            GlassGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dns_provider)) },
                supportingContent = {
                    val subtitle =
                        when (dnsProvider) {
                            SettingsStore.DnsProvider.CUSTOM ->
                                customDohUrl.ifBlank { dnsProvider.localizedLabel() }
                            else -> dnsProvider.localizedLabel()
                        }
                    Text(subtitle)
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.DNS_PROVIDER },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_bootstrap_dns_provider)) },
                supportingContent = {
                    val subtitle =
                        when (bootstrapDnsProvider) {
                            SettingsStore.DnsProvider.CUSTOM ->
                                customBootstrapDohUrl.ifBlank { bootstrapDnsProvider.localizedLabel() }
                            else -> bootstrapDnsProvider.localizedLabel()
                        }
                    Text(subtitle)
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.BOOTSTRAP_DNS_PROVIDER },
            )

            }
            SectionLabel(stringResource(R.string.settings_section_relayer))
            GlassGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_relayer_provider)) },
                supportingContent = {
                    val label = when {
                        relayerProvider.equals(SettingsStore.RELAYER_AUTOMATIC, ignoreCase = true) ->
                            stringResource(R.string.settings_relayer_automatic)
                        relayerProvider.equals(SettingsStore.RELAYER_DIRECT, ignoreCase = true) ->
                            stringResource(R.string.settings_relayer_direct)
                        relayerProvider.equals(SettingsStore.RELAYER_CUSTOM, ignoreCase = true) ->
                            customRelayerUrl.ifBlank { stringResource(R.string.settings_relayer_custom) }
                        else -> {
                            val entry = FoxyRemoteConfig.current.relayers.entries.firstOrNull {
                                it.key.equals(relayerProvider, ignoreCase = true)
                            }
                            if (entry != null) {
                                entry.key.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                            } else {
                                relayerProvider
                            }
                        }
                    }
                    Text(label)
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.RELAYER_PROVIDER },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_relayer_endpoints)) },
                supportingContent = {
                    val summary = if (relayedEndpoints.isEmpty()) {
                        stringResource(R.string.settings_none)
                    } else {
                        val names = mutableListOf<String>()
                        if ("guardian" in relayedEndpoints) names.add(stringResource(R.string.settings_relayer_ep_guardian))
                        if ("fxa" in relayedEndpoints) names.add(stringResource(R.string.settings_relayer_ep_fxa))
                        if ("config" in relayedEndpoints) names.add(stringResource(R.string.settings_relayer_ep_config))
                        if ("server_list" in relayedEndpoints) names.add(stringResource(R.string.settings_relayer_ep_server_list))
                        names.joinToString(", ")
                    }
                    Text(summary)
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.RELAYER_ENDPOINTS },
            )

            }
            SectionLabel(stringResource(R.string.proxy))
            GlassGroup {
            SettingsToggle(
                title = stringResource(R.string.settings_proxy_only_mode),
                summary = stringResource(R.string.settings_proxy_only_help),
                checked = proxyOnlyMode,
                onCheckedChange = {
                    settingsStore.proxyOnlyMode = it
                    proxyOnlyMode = it
                },
            )
            if (proxyState.editor == null && proxyState.error != null) {
                Text(
                    proxyState.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                GlassTextButton(onClick = proxyViewModel::reload) {
                    Text(stringResource(R.string.retry))
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.local_proxy)) },
                supportingContent = {
                    val summary = proxyState.summary
                    Text(
                        if (summary == null) stringResource(R.string.loading)
                        else
                            stringResource(
                                R.string.local_proxy_summary,
                                summary.localBindAddress,
                                summary.localPort,
                            )
                    )
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier =
                    Modifier.clickable(enabled = !proxyState.busy && proxyState.summary != null) {
                        proxyViewModel.open(ProxyEditor.LOCAL)
                    },
            )
            }
            SectionLabel(stringResource(R.string.settings_section_split_tunnel))
            GlassGroup {
            SettingsToggle(
                title = stringResource(R.string.settings_bypass_local_network),
                summary = stringResource(R.string.settings_bypass_local_network_help),
                checked = bypassLocalNetwork,
                enabled = !proxyOnlyMode,
                onCheckedChange = {
                    settingsStore.bypassLocalNetwork = it
                    bypassLocalNetwork = it
                },
            )
            ListItem(
                headlineContent = {
                    Text(
                        if (splitTunnelMode == SettingsStore.SplitTunnelMode.INCLUDE)
                            stringResource(R.string.settings_included_apps)
                        else stringResource(R.string.settings_excluded_apps)
                    )
                },
                supportingContent = {
                    Text(
                        (if (splitTunnelMode == SettingsStore.SplitTunnelMode.INCLUDE) includedApps
                            else excludedApps)
                            .let {
                                if (it.isEmpty()) stringResource(R.string.settings_none)
                                else pluralStringResource(R.plurals.settings_apps_count, it.size, it.size)
                            }
                    )
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.SPLIT_TUNNEL },
            )

            }
            SectionLabel(stringResource(R.string.settings_section_advanced))
            GlassGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_custom_edge_address)) },
                supportingContent = { Text(customEdgeAddress.ifBlank { stringResource(R.string.not_set) }) },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.EDGE_ADDRESS },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_transport_protocol)) },
                supportingContent = { Text(httpVersion.label) },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.HTTP_VERSION },
            )
            SettingsToggle(
                title = stringResource(R.string.upstream_proxy),
                summary = stringResource(R.string.upstream_proxy_help),
                checked = proxyState.summary?.upstreamEnabled ?: settingsStore.upstreamProxyEnabled,
                enabled =
                    !proxyState.busy &&
                        (proxyState.summary != null || settingsStore.upstreamProxyEnabled),
                onCheckedChange = proxyViewModel::setUpstreamEnabled,
            )
            if (proxyState.summary?.upstreamEnabled == true) {
                if (proxyState.summary?.upstreamReviewRequired == true) {
                    Text(
                        stringResource(R.string.upstream_review_required),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.proxy_type)) },
                    supportingContent = { Text(proxyState.summary?.upstreamType?.name.orEmpty()) },
                    trailingContent = {
                        Icon(
                            painterResource(R.drawable.lucide_chevron_right),
                            contentDescription = null,
                        )
                    },
                    modifier =
                        Modifier.clickable(enabled = !proxyState.busy) {
                            proxyViewModel.open(ProxyEditor.UPSTREAM_TYPE)
                        },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.proxy_address)) },
                    supportingContent = {
                        val summary = proxyState.summary
                        val host = summary?.upstreamHost.orEmpty()
                        Text(
                            if (host.isBlank()) stringResource(R.string.not_set)
                            else "${if (':' in host) "[$host]" else host}:${summary?.upstreamPort}"
                        )
                    },
                    trailingContent = {
                        Icon(
                            painterResource(R.drawable.lucide_chevron_right),
                            contentDescription = null,
                        )
                    },
                    modifier =
                        Modifier.clickable(enabled = !proxyState.busy) {
                            proxyViewModel.open(ProxyEditor.UPSTREAM_ADDRESS)
                        },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.proxy_credentials)) },
                    supportingContent = { Text(stringResource(R.string.proxy_credentials_help)) },
                    trailingContent = {
                        Icon(
                            painterResource(R.drawable.lucide_chevron_right),
                            contentDescription = null,
                        )
                    },
                    modifier =
                        Modifier.clickable(enabled = !proxyState.busy) {
                            proxyViewModel.open(ProxyEditor.UPSTREAM_CREDENTIALS)
                        },
                )
            }

            }
            SectionLabel(stringResource(R.string.settings_section_anticensorship))
            GlassGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_tls_fragmentation)) },
                supportingContent = {
                    Text(
                        if (httpVersion == SettingsStore.HttpVersion.HTTP_3) {
                            stringResource(R.string.settings_tcp_only_notice)
                        } else if (tlsFragmentMode == SettingsStore.TlsFragmentMode.OFF) {
                            stringResource(R.string.settings_disabled)
                        } else if (tlsFragmentMode == SettingsStore.TlsFragmentMode.RECORD) {
                            tlsFragmentMode.localizedLabel()
                        } else {
                            "${tlsFragmentMode.localizedLabel()}, ${stringResource(R.string.unit_ms, fragmentFallbackDelay)}"
                        }
                    )
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.TLS_FRAGMENT },
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.settings_browser_tls_fingerprint))
                },
                supportingContent = {
                    Text(
                        if (httpVersion == SettingsStore.HttpVersion.HTTP_3) {
                            "${utlsFingerprint.localizedLabel()} • ${stringResource(R.string.settings_tcp_only_notice)}"
                        } else {
                            utlsFingerprint.localizedLabel()
                        }
                    )
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.UTLS_FINGERPRINT },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_multiplex_concurrency)) },
                supportingContent = {
                    Text(
                        if (httpVersion != SettingsStore.HttpVersion.HTTP_2) {
                            "${pluralStringResource(R.plurals.stream_count, maxConcurrentStreams, maxConcurrentStreams)} • ${stringResource(R.string.settings_http2_only_notice)}"
                        } else {
                            pluralStringResource(R.plurals.stream_count, maxConcurrentStreams, maxConcurrentStreams)
                        }
                    )
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.CONCURRENCY },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ip_mode)) },
                supportingContent = {
                    Text(
                        when (ipMode) {
                            SettingsStore.IpMode.DUAL_STACK -> stringResource(R.string.ip_mode_dual_stack)
                            SettingsStore.IpMode.PREFER_IPV4 -> stringResource(R.string.ip_mode_prefer_ipv4)
                            SettingsStore.IpMode.PREFER_IPV6 -> stringResource(R.string.ip_mode_prefer_ipv6)
                            SettingsStore.IpMode.IPV4_ONLY -> stringResource(R.string.ip_mode_ipv4_only)
                            SettingsStore.IpMode.IPV6_ONLY -> stringResource(R.string.ip_mode_ipv6_only)
                        }
                    )
                },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.IP_MODE },
            )

            }
            SectionLabel(stringResource(R.string.settings_section_health))
            GlassGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_tunnel_health)) },
                supportingContent = {
                    Text(
                        when (health) {
                            TunnelHealth.UNKNOWN -> stringResource(R.string.settings_health_not_connected)
                            TunnelHealth.CHECKING -> stringResource(R.string.settings_health_checking)
                            TunnelHealth.HEALTHY -> stringResource(R.string.settings_health_healthy)
                            TunnelHealth.NO_NETWORK -> stringResource(R.string.settings_health_no_network)
                            TunnelHealth.UNREACHABLE ->
                                stringResource(R.string.settings_health_unreachable)
                        }
                    )
                },
            )
            SettingsToggle(
                title = stringResource(R.string.settings_automatic_connection_recovery),
                summary = stringResource(R.string.settings_recovery_help),
                checked = automaticRecovery,
                onCheckedChange = {
                    settingsStore.automaticRecovery = it
                    automaticRecovery = it
                },
            )
            }
            SectionLabel(stringResource(R.string.settings_section_about))
            GlassGroup {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_view_logs)) },
                supportingContent = { Text(stringResource(R.string.settings_logs_help)) },
                trailingContent = {
                    Icon(painterResource(R.drawable.lucide_chevron_right), contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onOpenLogs),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_check_for_updates)) },
                supportingContent = { Text(stringResource(R.string.settings_update_source)) },
                modifier =
                    Modifier.clickable(enabled = onCheckForUpdates != null) {
                        onCheckForUpdates?.invoke()
                    },
            )
            SettingsToggle(
                title = stringResource(R.string.settings_automatic_update_checks),
                summary = stringResource(R.string.settings_automatic_updates_help),
                checked = automaticUpdates,
                enabled = onAutomaticUpdatesChange != null,
                onCheckedChange = {
                    onAutomaticUpdatesChange?.invoke(it)
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_source_code)) },
                supportingContent = { Text(GITHUB_URL) },
                leadingContent = { Icon(painterResource(R.drawable.lucide_github), contentDescription = null) },
                modifier =
                    Modifier.clickable {
                        runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                                )
                            }
                            .onFailure {
                                android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.browser_unavailable),
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                    },
            )

            }

            if (isSignedIn) {
                GlassButton(
                    onClick = onSignOut,
                    enabled = !signOutInProgress,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.30f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp).heightIn(min = 52.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.lucide_log_out),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_sign_out))
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier =
            Modifier.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .heightIn(min = 48.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp),
    )
}
