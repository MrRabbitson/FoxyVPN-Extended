package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.vauth.foxyvpn.data.model.ProxyCandidate
import com.vauth.foxyvpn.ui.components.GlassSurface
import com.vauth.foxyvpn.ui.components.GlassTopBar
import com.vauth.foxyvpn.ui.components.glassSource
import com.vauth.foxyvpn.ui.components.rememberGlassSource
import com.vauth.foxyvpn.ui.components.GlassTextButton
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.ProxyStateStore
import com.vauth.foxyvpn.data.RECOMMENDED_COUNTRY_CODE
import com.vauth.foxyvpn.data.ServerListClient
import com.vauth.foxyvpn.data.model.VpnCity
import com.vauth.foxyvpn.data.model.VpnCountry
import com.vauth.foxyvpn.data.model.emojiFlag
import com.vauth.foxyvpn.data.model.localizedCountryName
import com.vauth.foxyvpn.ui.ServerListViewModel
import com.vauth.foxyvpn.ui.ServerPingState
import com.vauth.foxyvpn.ui.theme.LocalFoxyStatusColors

private data class CountryGroup(
    val country: VpnCountry,
    val name: String,
    val pingMs: Int?,
    val cities: List<VpnCity>,
)

/** Countries that answered sort first, still-measuring ones next, unreachable ones last. */
private const val RANK_MEASURED = 0
private const val RANK_PENDING = 1
private const val RANK_UNREACHABLE = 2

@Composable
fun ServerListScreen(
    viewModel: ServerListViewModel,
    proxyStateStore: ProxyStateStore,
    onServerSelected: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val countries = state.countries
    val isLoading = state.isLoading
    val errorMessage = state.errorMessage
    val selected by proxyStateStore.selectedProxyFlow.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val recommendedLabel = stringResource(R.string.home_recommended)

    LaunchedEffect(errorMessage) {
        val message = errorMessage
        if (message != null && countries.isNotEmpty()) {
            snackbarHost.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val recommended = remember(countries) { countries.firstOrNull { it.code == RECOMMENDED_COUNTRY_CODE } }
    val groups =
        remember(countries, state.pingResults, state.pingStates, locale, recommendedLabel) {
            fun cityPing(country: VpnCountry, city: VpnCity): Int? =
                state.pingResults["${country.code}:${city.code}"]

            fun countryPing(country: VpnCountry): Int? =
                country.cities.mapNotNull { cityPing(country, it) }.minOrNull()

            fun countryRank(country: VpnCountry): Int =
                when {
                    countryPing(country) != null -> RANK_MEASURED
                    country.cities.all {
                        state.pingStates["${country.code}:${it.code}"] == ServerPingState.FAILED
                    } -> RANK_UNREACHABLE
                    else -> RANK_PENDING
                }

            countries
                .filter { it.code != RECOMMENDED_COUNTRY_CODE }
                .map { country ->
                    CountryGroup(
                        country = country,
                        name = localizedCountryName(country.code, country.name, locale, recommendedLabel),
                        pingMs = countryPing(country),
                        cities =
                            country.cities.sortedWith(
                                compareBy<VpnCity>(
                                    { cityPing(country, it) ?: Int.MAX_VALUE },
                                    { it.name.ifBlank { it.code } },
                                )
                            ),
                    )
                }
                .sortedWith(
                    compareBy<CountryGroup>(
                        { countryRank(it.country) },
                        { it.pingMs ?: Int.MAX_VALUE },
                        { it.name },
                    )
                )
        }

    // Cards glide to their new place as measurements come in instead of jumping.
    val reorder =
        spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val listContent = rememberGlassSource()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.server_list_title),
                onBack = onBack,
                over = listContent,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                errorMessage != null && countries.isEmpty() ->
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            errorMessage,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        GlassTextButton(onClick = { viewModel.loadServerList(force = true) }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().glassSource(listContent).selectableGroup(),
                        contentPadding =
                            PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = padding.calculateTopPadding() + 4.dp,
                                bottom = padding.calculateBottomPadding() + 24.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (recommended != null) {
                            item(key = "recommended", contentType = 0) {
                                val isSelected = recommended.code == selected?.countryCode
                                GlassSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                                    val chosen =
                                                        ServerListClient.candidatesForCountry(
                                                                countries,
                                                                recommended.code,
                                                            )
                                                            .randomOrNull()
                                                    if (chosen != null) {
                                                        viewModel.selectCandidate(chosen)
                                                        onServerSelected()
                                                    } else {
                                                        viewModel.setErrorMessage(
                                                            context.getString(
                                                                R.string.server_list_no_servers_country,
                                                                recommendedLabel,
                                                            )
                                                        )
                                                    }
                                                },
                                ) {
                                    ListItem(
                                        leadingContent = {
                                            Icon(
                                                painterResource(R.drawable.lucide_globe),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                        headlineContent = {
                                            Text(
                                                stringResource(R.string.server_list_recommended_title),
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        },
                                        supportingContent = {
                                            Text(stringResource(R.string.server_list_recommended_desc))
                                        },
                                        trailingContent = { if (isSelected) SelectedMark() },
                                    )
                                }
                            }
                        }
                        items(groups, key = { "country:${it.country.code}" }, contentType = { 1 }) { group ->
                            GlassSurface(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = reorder)
                            ) {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    ListItem(
                                        leadingContent = {
                                            Text(
                                                text = group.country.emojiFlag,
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                        },
                                        headlineContent = { Text(group.name, fontWeight = FontWeight.SemiBold) },
                                        trailingContent = { PingLabel(group.pingMs) },
                                    )
                                    for (city in group.cities) {
                                        CityRow(
                                            country = group.country,
                                            city = city,
                                            countries = countries,
                                            pingMs = state.pingResults["${group.country.code}:${city.code}"],
                                            pingState =
                                                state.pingStates["${group.country.code}:${city.code}"]
                                                    ?: ServerPingState.PENDING,
                                            isSelected =
                                                group.country.code == selected?.countryCode &&
                                                    city.code == selected?.cityCode,
                                            onChosen = { chosen ->
                                                viewModel.selectCandidate(chosen)
                                                onServerSelected()
                                            },
                                            onUnavailable = { cityName ->
                                                viewModel.setErrorMessage(
                                                    context.getString(R.string.server_list_no_servers_city, cityName)
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun CityRow(
    country: VpnCountry,
    city: VpnCity,
    countries: List<VpnCountry>,
    pingMs: Int?,
    pingState: ServerPingState,
    isSelected: Boolean,
    onChosen: (ProxyCandidate) -> Unit,
    onUnavailable: (String) -> Unit,
) {
    val cityName = city.name.ifBlank { city.code }
    val available =
        remember(countries, country.code, city.code) {
            ServerListClient.candidatesForCity(countries, country.code, city.code)
        }
    ListItem(
        headlineContent = { Text(cityName) },
        supportingContent = {
            val primaryHostname = available.firstOrNull()?.host
            val serverLabel =
                when {
                    primaryHostname == null -> stringResource(R.string.server_list_no_servers_available)
                    available.size > 1 ->
                        stringResource(R.string.server_list_more_servers, primaryHostname, available.size - 1)
                    else -> primaryHostname
                }
            val pingLabel =
                when {
                    pingMs != null -> stringResource(R.string.server_list_tcp_ms, pingMs)
                    pingState == ServerPingState.FAILED -> stringResource(R.string.server_list_tcp_unavailable)
                    else -> stringResource(R.string.server_list_checking_tcp)
                }
            Text("$serverLabel \u2022 $pingLabel")
        },
        trailingContent = { if (isSelected) SelectedMark() },
        modifier =
            Modifier.padding(start = 16.dp)
                .selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = {
                        val chosen = available.randomOrNull()
                        if (chosen != null) onChosen(chosen) else onUnavailable(cityName)
                    },
                ),
    )
}

@Composable
private fun SelectedMark() {
    Icon(
        painterResource(R.drawable.lucide_circle_check),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** Best latency of a country, tinted by how good it is. */
@Composable
private fun PingLabel(pingMs: Int?) {
    if (pingMs == null) return
    val status = LocalFoxyStatusColors.current
    val color by
        animateColorAsState(
            targetValue =
                when {
                    pingMs < 120 -> status.connected
                    pingMs < 250 -> status.connecting
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            animationSpec = tween(300),
            label = "ping-color",
        )
    Text(
        stringResource(R.string.server_list_tcp_ms, pingMs),
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}
