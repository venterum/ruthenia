// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import com.vntrum.ruthenia.core.data.settings.AppIconShape
import com.vntrum.ruthenia.core.data.settings.AppLanguage
import com.vntrum.ruthenia.core.data.settings.ThemeMode
import com.vntrum.ruthenia.core.designsystem.component.LocalRutheniaIconShape
import com.vntrum.ruthenia.core.designsystem.component.RutheniaIconShape
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaAccent
import com.vntrum.ruthenia.core.designsystem.theme.RutheniaTheme
import com.vntrum.ruthenia.core.updates.DownloadNotifications
import com.vntrum.ruthenia.ui.RutheniaApp

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the system splash before super.onCreate: the cold-start
        // theme switches to Theme.Ruthenia once Compose renders its first frame.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleInstallIntent(intent)
        handleDeepLinkIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val confirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // UI language: AppCompat persists the choice itself (on API < 33)
            // and delegates to the system LocaleManager on 33+. Comparing before
            // setting prevents an endless Activity recreation loop.
            LaunchedEffect(settings.language) {
                val locales = when (settings.language) {
                    AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                    AppLanguage.RU -> LocaleListCompat.forLanguageTags("ru")
                    AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
                }
                if (AppCompatDelegate.getApplicationLocales() != locales) {
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }

            val accent = RutheniaAccent.entries
                .firstOrNull { it.name == settings.accent.name }
                ?: RutheniaAccent.TEAL

            // The system install dialog is launched from the Activity — it cannot be bypassed.
            LaunchedEffect(confirmation) {
                confirmation?.let {
                    runCatching { startActivity(it) }
                    viewModel.onConfirmationLaunched()
                }
            }

            RutheniaTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                accent = accent,
            ) {
                // App icons follow the user's shape setting; the brand mark in
                // the Apps header deliberately stays round.
                CompositionLocalProvider(
                    LocalRutheniaIconShape provides settings.appIconShape.toRutheniaIconShape(),
                ) {
                    RutheniaApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInstallIntent(intent)
        handleDeepLinkIntent(intent)
    }

    /** "Install" action from a notification: opens the app and installs the package. */
    private fun handleInstallIntent(intent: Intent?) {
        val packageName = intent
            ?.getStringExtra(DownloadNotifications.EXTRA_INSTALL_PACKAGE)
            ?: return
        viewModel.installPackage(packageName)
    }

    /**
     * A link to an app page in the RuStore catalog opens the details page
     * inside Ruthenia Store rather than in the browser. Notification
     * permission is not requested here: that happens in settings when
     * background checks are enabled.
     */
    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val packageName = rustorePackageNameFromLink(intent.dataString) ?: return
        viewModel.openApp(packageName)
    }
}

/** Maps the stored icon shape setting to the design-system shape. */
internal fun AppIconShape.toRutheniaIconShape(): RutheniaIconShape = when (this) {
    AppIconShape.CIRCLE -> RutheniaIconShape.CIRCLE
    AppIconShape.ROUNDED_SQUARE -> RutheniaIconShape.ROUNDED_SQUARE
    AppIconShape.SQUARE -> RutheniaIconShape.SQUARE
}