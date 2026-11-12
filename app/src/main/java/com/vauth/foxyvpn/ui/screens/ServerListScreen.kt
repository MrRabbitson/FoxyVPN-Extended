package com.vauth.foxyvpn.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

private sealed class LocationRow {
    data class RecommendedRow(val country: VpnCountry) : LocationRow()

    data class CountryHeader(val country: VpnCountry, val name: String, val pingMs: Int?) : LocationRow()

    data class CityRow(val country: VpnCountry, val city: VpnCity) : LocationRow()
}

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
    val selectedState by proxyStateStore.selectedProxyFlow.collectAsStateWithLifecycle()
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

    val rows =
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

            val names =
                countries.associate {
                    it.code to localizedCountryName(it.code, it.name, locale, recommendedLabel)
                }
            val ordered =
                countries.sortedWith(
                    compareBy<VpnCountry>(
                        { it.code != RECOMMENDED_COUNTRY_CODE },
                        { countryRank(it) },
                        { countryPing(it) ?: Int.MAX_VALUE },
                        { names[it.code].orEmpty() },
                    )
                )
            buildList {
                for (country in ordered) {
                    if (country.code == RECOMMENDED_COUNTRY_CODE) {
                        add(LocationRow.RecommendedRow(country))
                        continue
                    }
                    add(
                        LocationRow.CountryHeader(
                            country = country,
                            name = names[country.code].orEmpty(),
                            pingMs = countryPing(country),
                        )
                    )
                    country.cities
                        .sortedWith(
                            compareBy<VpnCity>(
                                { cityPing(country, it) ?: Int.MAX_VALUE },
                                { it.name.ifBlank { it.code } },
                            )
                        )
                        .forEach { add(LocationRow.CityRow(country, it)) }
                }
            }
        }

    // Rows glide to their new place as measurements come in instead of jumping.
    val reorder =
        spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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
                        TextButton(onClick = { viewModel.loadServerList(force = true) }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                else ->
                    LazyColumn(Modifier.selectableGroup()) {
                        items(
                            rows,
                            key = { row ->
                                when (row) {
                                    is LocationRow.RecommendedRow -> "recommended:${row.country.code}"
                                    is LocationRow.CountryHeader -> "country:${row.country.code}"
                                    is LocationRow.CityRow -> "city:${row.country.code}:${row.city.code}"
                                }
                            },
                            contentType = { row ->
                                when (row) {
                                    is LocationRow.RecommendedRow -> 0
                                    is LocationRow.CountryHeader -> 1
                                    is LocationRow.CityRow -> 2
                                }
                            },
                        ) { row ->
                            val selected = selectedState
                            val itemModifier =
                                Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = reorder,
                                )
                            when (row) {
                                is LocationRow.RecommendedRow ->
                                    ListItem(
                                        leadingContent = {
                                            Icon(
                                                Icons.Filled.Public,
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
                                        trailingContent = {
                                            if (row.country.code == selected?.countryCode) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        modifier =
                                            itemModifier
                                                .padding(horizontal = 8.dp)
                                                .selectable(
                                                    selected = row.country.code == selected?.countryCode,
                                                    role = Role.RadioButton,
                                                    onClick = {
                                                        val chosen =
                                                            ServerListClient.candidatesForCountry(
                                                                    countries,
                                                                    row.country.code,
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
                                                ),
                                    )
                                is LocationRow.CountryHeader ->
                                    ListItem(
                                        leadingContent = {
                                            Text(
                                                text = row.country.emojiFlag,
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                        },
                                        headlineContent = {
                                            Text(row.name, fontWeight = FontWeight.SemiBold)
                                        },
                                        trailingContent = { PingLabel(row.pingMs) },
                                        modifier = itemModifier.padding(horizontal = 8.dp),
                                    )
                                is LocationRow.CityRow -> {
                                    val city = row.city
                                    val cityName = city.name.ifBlank { city.code }
                                    val available =
                                        remember(countries, row.country.code, city.code) {
                                            ServerListClient.candidatesForCity(
                                                countries,
                                                row.country.code,
                                                city.code,
                                            )
                                        }
                                    val serverCount = available.size
                                    val pingKey = "${row.country.code}:${city.code}"
                                    val pingMs = state.pingResults[pingKey]
                                    val pingState = state.pingStates[pingKey] ?: ServerPingState.PENDING
                                    val isSelected =
                                        row.country.code == selected?.countryCode &&
                                            city.code == selected?.cityCode

                                    ListItem(
                                        headlineContent = { Text(cityName) },
                                        supportingContent = {
                                            val primaryHostname = available.firstOrNull()?.host
                                            val serverLabel =
                                                when {
                                                    primaryHostname == null ->
                                                        stringResource(R.string.server_list_no_servers_available)
                                                    serverCount > 1 ->
                                                        stringResource(
                                                            R.string.server_list_more_servers,
                                                            primaryHostname,
                                                            serverCount - 1,
                                                        )
                                                    else -> primaryHostname
                                                }
                                            val pingLabel =
                                                when {
                                                    pingMs != null ->
                                                        stringResource(R.string.server_list_tcp_ms, pingMs)
                                                    pingState == ServerPingState.FAILED ->
                                                        stringResource(R.string.server_list_tcp_unavailable)
                                                    else -> stringResource(R.string.server_list_checking_tcp)
                                                }
                                            Text("$serverLabel • $pingLabel")
                                        },
                                        trailingContent = {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        modifier =
                                            itemModifier
                                                .padding(start = 24.dp, end = 8.dp)
                                                .selectable(
                                                    selected = isSelected,
                                                    role = Role.RadioButton,
                                                    onClick = {
                                                        val chosen = available.randomOrNull()
                                                        if (chosen != null) {
                                                            viewModel.selectCandidate(chosen)
                                                            onServerSelected()
                                                        } else {
                                                            viewModel.setErrorMessage(
                                                                context.getString(
                                                                    R.string.server_list_no_servers_city,
                                                                    cityName,
                                                                )
                                                            )
                                                        }
                                                    },
                                                ),
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
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
