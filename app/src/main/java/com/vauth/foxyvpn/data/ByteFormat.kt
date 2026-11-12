package com.vauth.foxyvpn.data

import java.util.Locale

private const val UNIT = 1024.0
private val UNITS_EN = listOf("KB", "MB", "GB", "TB", "PB")
private val UNITS_RU = listOf("КБ", "МБ", "ГБ", "ТБ", "ПБ")

private fun isRussian(): Boolean = Locale.getDefault().language == "ru"

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val ru = isRussian()
    if (bytes < 1024) return if (ru) "$bytes Б" else "$bytes B"
    val units = if (ru) UNITS_RU else UNITS_EN
    var value = bytes / UNIT
    var index = 0
    while (value >= UNIT && index < units.lastIndex) {
        value /= UNIT
        index++
    }
    val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
    return String.format(if (ru) Locale.forLanguageTag("ru") else Locale.US, pattern, value, units[index])
}

fun formatBytesPerSecond(bytesPerSecond: Long): String =
    "${formatBytes(bytesPerSecond.coerceAtLeast(0))}/${if (isRussian()) "с" else "s"}"
