package com.vauth.foxyvpn.data

import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Thrown when Mozilla's bot protection asks for a CAPTCHA, which only a person can solve. */
class HumanCheckRequiredException :
    Exception("Mozilla asks to confirm that a person is using this account. Complete the check to continue.")

/**
 * Whether Mozilla's bot protection is waiting for a person to solve a CAPTCHA.
 *
 * While it is, requests to Mozilla are not sent at all: repeating them only
 * makes the protection stricter. The UI offers the check in the in-app browser
 * and calls [resolved] once it has been passed.
 */
object HumanCheck {
    private val _required = MutableStateFlow(false)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    val isRequired: Boolean
        get() = _required.value

    fun require() {
        if (!_required.value) {
            AppLogger.w("HumanCheck", "Mozilla requires a CAPTCHA; pausing requests until it is solved")
            _required.value = true
        }
    }

    /** Throws instead of sending a request that would only meet the CAPTCHA again. */
    fun ensureClear() {
        if (_required.value) throw HumanCheckRequiredException()
    }

    /**
     * The check was passed in the browser: carry its cookies over to the app's
     * own HTTP client so its requests are let through too, then resume.
     */
    fun resolved() {
        importBrowserCookies()
        if (_required.value) AppLogger.i("HumanCheck", "check completed; resuming requests")
        _required.value = false
    }

    suspend fun awaitResolved() {
        required.first { !it }
    }

    private val hosts =
        listOf(
            "accounts.firefox.com",
            "api.accounts.firefox.com",
            "oauth.accounts.firefox.com",
            "vpn.mozilla.org",
        )

    private fun importBrowserCookies() {
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        val inAYear = System.currentTimeMillis() + 365L * 24 * 3600 * 1000
        for (host in hosts) {
            val header = runCatching { manager.getCookie("https://$host") }.getOrNull() ?: continue
            val url = "https://$host/".toHttpUrl()
            val cookies =
                header.split(';').mapNotNull { part ->
                    val pair = part.trim()
                    val eq = pair.indexOf('=')
                    if (eq <= 0) return@mapNotNull null
                    runCatching {
                            Cookie.Builder()
                                .name(pair.substring(0, eq))
                                .value(pair.substring(eq + 1))
                                .hostOnlyDomain(host)
                                .path("/")
                                .secure()
                                .expiresAt(inAYear)
                                .build()
                        }
                        .getOrNull()
                }
            if (cookies.isNotEmpty()) ControlPlaneHttp.cookieJar.saveFromResponse(url, cookies)
        }
    }
}
