package com.vauth.foxyvpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vauth.foxyvpn.data.AppLogger
import com.vauth.foxyvpn.data.ProxyStateStore
import com.vauth.foxyvpn.data.ServerListClient
import com.vauth.foxyvpn.data.model.ProxyCandidate
import com.vauth.foxyvpn.data.model.RECOMMENDED_COUNTRY_CODE
import com.vauth.foxyvpn.data.model.VpnCountry
import com.vauth.foxyvpn.vpn.PingUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class ServerPingState {
    PENDING,
    DONE,
    FAILED,
}

data class ServerListUiState(
    val countries: List<VpnCountry> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val pingResults: Map<String, Int> = emptyMap(),
    val pingStates: Map<String, ServerPingState> = emptyMap(),
    val directoryRevision: Int = 0,
)

class ServerListViewModel(
    private val serverListClient: ServerListClient,
    private val proxyStateStore: ProxyStateStore,
    private val loadFailedMessage: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerListUiState())
    val state: StateFlow<ServerListUiState> = _state.asStateFlow()

    private val probeSlots = Semaphore(8)

    init {
        loadServerList(force = false)
    }

    fun loadServerList(force: Boolean = false) {
        viewModelScope.launch {
            if (_state.value.countries.isEmpty()) {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
            }
            if (!force) {
                val cached = withContext(Dispatchers.IO) { serverListClient.loadCachedCountries() }
                if (cached.isNotEmpty()) {
                    _state.update { it.copy(countries = cached, isLoading = false) }
                    probeAll(cached)
                }
            }
            try {
                val fresh = withContext(Dispatchers.IO) { serverListClient.fetchCountries() }
                _state.update { current ->
                    current.copy(
                        countries = fresh,
                        isLoading = false,
                        errorMessage = null,
                        pingResults = emptyMap(),
                        pingStates = emptyMap(),
                        directoryRevision = current.directoryRevision + 1,
                    )
                }
                probeAll(fresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.w("ServerList", "could not load the server directory", error)
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = loadFailedMessage,
                    )
                }
            }
        }
    }

    /** Measures every city up front so the list can be ordered by latency. */
    private fun probeAll(countries: List<VpnCountry>) {
        for (country in countries) {
            if (country.code.equals(RECOMMENDED_COUNTRY_CODE, ignoreCase = true)) continue
            for (city in country.cities) {
                probeCityLatency(
                    country.code,
                    city.code,
                    ServerListClient.candidatesForCity(countries, country.code, city.code),
                )
            }
        }
    }

    fun probeCityLatency(countryCode: String, cityCode: String, available: List<ProxyCandidate>) {
        val pingKey = "$countryCode:$cityCode"
        if (_state.value.pingStates.containsKey(pingKey)) return

        _state.update { it.copy(pingStates = it.pingStates + (pingKey to ServerPingState.PENDING)) }

        val target = available.firstOrNull()?.let { it.host to it.port }
        if (target == null) {
            _state.update { it.copy(pingStates = it.pingStates + (pingKey to ServerPingState.FAILED)) }
            return
        }

        viewModelScope.launch {
            val result =
                try {
                    probeSlots.withPermit {
                        withContext(Dispatchers.IO) {
                            PingUtil.measureTcpLatencyMs(target.first, target.second)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }

            if (result != null) {
                _state.update {
                    it.copy(
                        pingResults = it.pingResults + (pingKey to result),
                        pingStates = it.pingStates + (pingKey to ServerPingState.DONE),
                    )
                }
            } else {
                _state.update {
                    it.copy(pingStates = it.pingStates + (pingKey to ServerPingState.FAILED))
                }
            }
        }
    }

    fun selectCandidate(candidate: ProxyCandidate) {
        proxyStateStore.save(candidate)
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun setErrorMessage(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    companion object {
        fun factory(
            serverListClient: ServerListClient,
            proxyStateStore: ProxyStateStore,
            loadFailedMessage: String,
        ) = viewModelFactory {
            initializer { ServerListViewModel(serverListClient, proxyStateStore, loadFailedMessage) }
        }
    }
}
