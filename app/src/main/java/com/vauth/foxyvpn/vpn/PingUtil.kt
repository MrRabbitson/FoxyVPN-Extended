package com.vauth.foxyvpn.vpn

import com.vauth.foxyvpn.data.AppLogger
import com.vauth.foxyvpn.data.ControlPlaneHttp
import com.vauth.foxyvpn.data.SettingsStore
import com.vauth.foxyvpn.data.awaitResponse
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object PingUtil {

    suspend fun measureTcpLatencyMs(host: String, port: Int): Int? =
        withContext(Dispatchers.IO) {
            try {
                val start = System.nanoTime()
                val address =
                    ControlPlaneHttp.client.dns.lookup(host).firstOrNull()
                        ?: return@withContext null
                ControlPlaneHttp.client.socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), 1_500)
                }
                ((System.nanoTime() - start) / 1_000_000L)
                    .coerceIn(1, Int.MAX_VALUE.toLong())
                    .toInt()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    /**
     * One UDP round trip: a minimal DNS question (the root's name servers) sent
     * to a public resolver, timed until the answer arrives. UDP has no "ping" of
     * its own, so something has to answer; a resolver always does.
     */
    suspend fun measureUdpLatencyMs(host: String = UDP_PROBE_HOST, port: Int = 53): Int? =
        withContext(Dispatchers.IO) {
            try {
                val query =
                    byteArrayOf(
                        0x46, 0x58, // id
                        0x01, 0x00, // standard query, recursion desired
                        0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // one question
                        0x00, // name: root
                        0x00, 0x02, // type NS
                        0x00, 0x01, // class IN
                    )
                val address = InetAddress.getByName(host)
                DatagramSocket().use { socket ->
                    socket.soTimeout = 1_500
                    val start = System.nanoTime()
                    socket.send(DatagramPacket(query, query.size, address, port))
                    val answer = ByteArray(512)
                    socket.receive(DatagramPacket(answer, answer.size))
                    ((System.nanoTime() - start) / 1_000_000L).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    private const val UDP_PROBE_HOST = "1.1.1.1"

    suspend fun measureProxyLatencyMs(
        socksPort: Int,
        probeUrl: String,
        username: String = "",
        password: String = "",
        headOnly: Boolean = false,
    ): Int? =
        withContext(Dispatchers.IO) {
            require(socksPort in 1..65_535) { "Invalid local proxy port" }
            require(SettingsStore.isValidProbeUrl(probeUrl)) { "Latency endpoint must be HTTPS" }

            val client = localProxyClient(socksPort, username, password)
            try {
                val request =
                    Request.Builder()
                        .url(probeUrl)
                        .header("Cache-Control", "no-cache, no-store")
                        .let { if (headOnly) it.head() else it }
                        .build()
                val start = System.nanoTime()
                client.newCall(request).awaitResponse().use { response ->
                    check(response.isSuccessful) { "Latency probe failed: HTTP ${response.code}" }
                    ((System.nanoTime() - start) / 1_000_000L)
                        .coerceIn(1, Int.MAX_VALUE.toLong())
                        .toInt()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.d("PingUtil", "HTTPS latency probe failed: ${error.message}")
                null
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
}
