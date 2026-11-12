package com.vauth.foxyvpn.data.model

import java.util.Locale

/**
 * Country name in the user's language. The server directory only ships English
 * names, so the name is derived from the ISO code and the directory's own name
 * is kept as the fallback for codes the platform does not know.
 */
fun localizedCountryName(
    code: String,
    rawName: String,
    locale: Locale,
    recommendedLabel: String,
): String {
    if (normalizeCountryName(code, rawName) == RECOMMENDED_COUNTRY_NAME) return recommendedLabel
    if (code.length == 2) {
        val localized =
            runCatching {
                    Locale.Builder().setRegion(code.uppercase(Locale.ROOT)).build().getDisplayCountry(locale)
                }
                .getOrNull()
        if (!localized.isNullOrBlank() && !localized.equals(code, ignoreCase = true)) return localized
    }
    return rawName.ifBlank { code }
}
