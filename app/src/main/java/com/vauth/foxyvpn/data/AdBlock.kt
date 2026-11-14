package com.vauth.foxyvpn.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Blocking of ad, tracking and telemetry domains inside the tunnel.
 *
 * A small hand-picked list ships with the app so blocking works at once and
 * offline. Larger community lists (HaGeZi) are downloaded on top and kept up
 * to date. The engine is given one combined rule-set file.
 */
object AdBlock {
    enum class Level(val remoteFile: String?) {
        /** Only the built-in list: the big ad and analytics networks. */
        BASIC(null),
        /** Built-in list plus HaGeZi Light (about 40 thousand domains). */
        STANDARD("light-onlydomains.txt"),
        /** Built-in list plus HaGeZi Pro mini (stricter; may break the odd site). */
        STRICT("pro.mini-onlydomains.txt"),
    }

    /** Never blocked, whatever a list says: the VPN itself depends on them. */
    private val Protected =
        listOf("firefox.com", "mozilla.org", "mozilla.com", "mozilla.net", "fastly-masque.net", "github.com", "githubusercontent.com", "jsdelivr.net")

    private const val REFRESH_AFTER_MS = 3L * 24 * 3600 * 1000
    private const val MAX_LIST_BYTES = 12 * 1024 * 1024

    private fun dir(context: Context) = File(context.filesDir, "adblock").apply { mkdirs() }

    private fun listFile(context: Context, level: Level) = level.remoteFile?.let { File(dir(context), it) }

    /** When the downloaded list for [level] was last refreshed, or null if never. */
    fun lastUpdated(context: Context, level: Level): Long? =
        listFile(context, level)?.takeIf { it.isFile }?.lastModified()

    fun isStale(context: Context, level: Level): Boolean {
        val file = listFile(context, level) ?: return false
        return !file.isFile || System.currentTimeMillis() - file.lastModified() > REFRESH_AFTER_MS
    }

    /** Downloads the list for [level]. Returns the number of domains, or throws. */
    suspend fun refresh(context: Context, level: Level): Int =
        withContext(Dispatchers.IO) {
            val name = level.remoteFile ?: return@withContext Builtin.size
            val urls =
                listOf(
                    "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/$name",
                    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/$name",
                )
            var failure: Exception? = null
            for (url in urls) {
                try {
                    val text =
                        ControlPlaneHttp.client
                            .newCall(Request.Builder().url(url).build())
                            .awaitResponse()
                            .use { response ->
                                check(response.isSuccessful) { "HTTP ${response.code}" }
                                response.body?.byteStream()?.use { it.readBoundedUtf8(MAX_LIST_BYTES) }
                                    ?: error("empty response")
                            }
                    val domains = parse(text)
                    check(domains.size > 1_000) { "list looks truncated (${domains.size} domains)" }
                    val target = listFile(context, level)!!
                    val temp = File(target.parentFile, target.name + ".tmp")
                    temp.writeText(domains.joinToString("\n"))
                    check(temp.renameTo(target)) { "could not store the list" }
                    AppLogger.i("AdBlock", "updated ${level.name.lowercase()} list: ${domains.size} domains")
                    return@withContext domains.size
                } catch (error: Exception) {
                    failure = error
                    AppLogger.w("AdBlock", "could not download $url", error)
                }
            }
            throw failure ?: IllegalStateException("no list source")
        }

    /**
     * Writes the rule-set the engine will use and returns its path, or null if
     * there is nothing to block. Exceptions are kept out by the engine rules,
     * not here, so that a subdomain can be allowed under a blocked parent.
     */
    fun prepareRuleSet(context: Context, level: Level): String? {
        val domains = LinkedHashSet<String>(Builtin)
        listFile(context, level)?.takeIf { it.isFile }?.let { file ->
            runCatching { file.forEachLine { line -> if (line.isNotBlank()) domains.add(line) } }
                .onFailure { AppLogger.w("AdBlock", "could not read the stored list; using the built-in one", it) }
        }
        domains.removeAll { domain -> Protected.any { domain == it || domain.endsWith(".$it") } }
        if (domains.isEmpty()) return null
        val ruleSet =
            JSONObject().apply {
                put("version", 2)
                put("rules", JSONArray().put(JSONObject().put("domain_suffix", JSONArray(domains.toList()))))
            }
        val file = File(dir(context), "ruleset.json")
        file.writeText(ruleSet.toString())
        return file.absolutePath
    }

    /** Domains the user allowed, plus the ones the VPN itself needs. */
    fun allowList(userExceptions: Collection<String>): List<String> = (Protected + userExceptions).distinct()

    private val DomainPattern = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    fun normalizeDomain(raw: String): String? {
        val d =
            raw.trim()
                .lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("*.")
                .removePrefix(".")
                .substringBefore('/')
                .trimEnd('.')
        return d.takeIf { DomainPattern.matches(it) }
    }

    private fun parse(text: String): List<String> =
        text.lineSequence()
            .map { it.substringBefore('#') }
            .mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() }?.let(::normalizeDomain) }
            .distinct()
            .toList()

    /**
     * Built-in list. Each entry also covers its subdomains. Chosen so that
     * blocking them does not break the services they belong to.
     */
    private val Builtin =
        listOf(
            // Google ads and analytics, including the ad requests of YouTube and AdMob
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
            "googletagmanager.com", "google-analytics.com", "app-measurement.com", "adservice.google.com",
            "ads.youtube.com", "admob.com", "firebase-settings.crashlytics.com", "crashlytics.com",
            // Yandex: ad network, AdFox, Metrica, AppMetrica
            "an.yandex.ru", "yabs.yandex.ru", "adfox.ru", "adfox.yandex.ru", "yandexadexchange.net",
            "mobile.yandexadexchange.net", "adsdk.yandex.ru", "awaps.yandex.net", "mc.yandex.ru",
            "mc.yandex.com", "report.appmetrica.yandex.net", "redirect.appmetrica.yandex.com",
            // VK / Mail.ru / myTarget
            "ad.mail.ru", "ads.mail.ru", "top-fwz1.mail.ru", "mradx.net", "rs.mail.ru", "ads.vk.com",
            // Game and app ad networks
            "unityads.unity3d.com", "cdp.cloud.unity3d.com", "perf-events.cloud.unity3d.com",
            "applovin.com", "applvn.com", "supersonicads.com", "ironsrc.mobi", "vungle.com",
            "adcolony.com", "chartboost.com", "inmobi.com", "mopub.com", "an.facebook.com",
            "rayjump.com", "mtgglobals.com", "pangle.io", "pangleglobal.com", "tapjoy.com",
            "fyber.com", "smaato.net", "startappservice.com", "adsmoloco.com", "bidmachine.io",
            // Web ad exchanges and verification
            "amazon-adsystem.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
            "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net", "moatads.com",
            "adsafeprotected.com", "doubleverify.com", "media.net", "adform.net", "smartadserver.com",
            "yieldmo.com", "3lift.com", "sharethrough.com", "teads.tv", "casalemedia.com",
            // Attribution and analytics SDKs
            "appsflyer.com", "appsflyersdk.com", "adjust.com", "kochava.com", "flurry.com",
            "api.mixpanel.com", "api.amplitude.com", "api2.amplitude.com", "api.segment.io",
            "scorecardresearch.com", "hotjar.com", "clarity.ms", "bat.bing.com",
            "analytics.tiktok.com", "ingest.sentry.io", "sessions.bugsnag.com", "notify.bugsnag.com",
            "nr-data.net",
            // Phone makers' ad and tracking services
            "ad.xiaomi.com", "tracking.miui.com", "data.mistat.xiaomi.com", "samsungads.com",
            "ads.huawei.com",
        )
}
