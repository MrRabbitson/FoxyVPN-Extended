package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vauth.foxyvpn.data.FxaAuthRepository
import com.vauth.foxyvpn.data.HumanCheck
import com.vauth.foxyvpn.ui.LoginViewModel

/**
 * Opens Mozilla's sign-in page in the in-app browser so a person can pass the
 * CAPTCHA. The browser still holds the Firefox session, so normally it is one
 * tap on "Continue" plus the check itself; no password.
 */
@Composable
fun HumanCheckScreen(
    authRepository: FxaAuthRepository,
    onDone: () -> Unit,
    onCanceled: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val model: LoginViewModel =
        viewModel(
            key = "human-check",
            factory = remember(authRepository) { LoginViewModel.factory(authRepository) { appContext.getString(it) } },
        )
    val ui by model.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { model.startBrowserLogin() }
    LaunchedEffect(ui.signedIn) { if (ui.signedIn) onDone() }

    val url = ui.browserStartUrl ?: return
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        BrowserLoginScreen(
            startUrl = url,
            busy = ui.isLoading,
            errorMessage = ui.errorMessage,
            onSessionCaptured = { token ->
                // The page only hands over a session after the check was passed.
                HumanCheck.resolved()
                model.completeBrowserLogin(token)
            },
            onCanceled = onCanceled,
        )
    }
}
