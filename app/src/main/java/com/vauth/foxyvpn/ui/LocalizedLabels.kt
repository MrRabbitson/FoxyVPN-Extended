package com.vauth.foxyvpn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.vauth.foxyvpn.R
import com.vauth.foxyvpn.data.SettingsStore

/*
 * Localized display labels for settings enums. The enums keep their English
 * labels for logs and persistence; the UI should always go through these.
 */

@Composable
fun SettingsStore.HttpVersion.localizedDescription(): String =
    stringResource(
        when (this) {
            SettingsStore.HttpVersion.HTTP_1 -> R.string.http_version_1_desc
            SettingsStore.HttpVersion.HTTP_2 -> R.string.http_version_2_desc
            SettingsStore.HttpVersion.HTTP_3 -> R.string.http_version_3_desc
        }
    )

@Composable
fun SettingsStore.TlsFragmentMode.localizedLabel(): String =
    stringResource(
        when (this) {
            SettingsStore.TlsFragmentMode.OFF -> R.string.tls_fragment_off
            SettingsStore.TlsFragmentMode.RECORD -> R.string.tls_fragment_record
            SettingsStore.TlsFragmentMode.PACKET -> R.string.tls_fragment_packet
            SettingsStore.TlsFragmentMode.COMBINED -> R.string.tls_fragment_combined
        }
    )

@Composable
fun SettingsStore.TlsFragmentMode.localizedDescription(): String =
    stringResource(
        when (this) {
            SettingsStore.TlsFragmentMode.OFF -> R.string.tls_fragment_off_desc
            SettingsStore.TlsFragmentMode.RECORD -> R.string.tls_fragment_record_desc
            SettingsStore.TlsFragmentMode.PACKET -> R.string.tls_fragment_packet_desc
            SettingsStore.TlsFragmentMode.COMBINED -> R.string.tls_fragment_combined_desc
        }
    )

@Composable
fun SettingsStore.UtlsFingerprint.localizedLabel(): String =
    if (this == SettingsStore.UtlsFingerprint.RANDOMIZED) {
        stringResource(R.string.utls_randomized)
    } else {
        label
    }

@Composable
fun SettingsStore.DnsProvider.localizedLabel(): String =
    when (this) {
        SettingsStore.DnsProvider.DIRECT_SYSTEM -> stringResource(R.string.dns_system)
        SettingsStore.DnsProvider.CUSTOM -> stringResource(R.string.dns_custom_doh)
        else -> label
    }

@Composable
fun localizedCountryName(code: String, rawName: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val recommended = stringResource(R.string.home_recommended)
    return remember(code, rawName, locale, recommended) {
        com.vauth.foxyvpn.data.model.localizedCountryName(code, rawName, locale, recommended)
    }
}
