/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.model

/**
 * Where the app came from on the device.
 *
 * Determined from the installer (`getInstallSourceInfo`/`getInstallerPackageName`)
 * locally, on import. Needed to:
 * - honestly show the source in the library;
 * - let the user exclude whole stores (for example, Google Play);
 * - not offer an update where the client cannot install it.
 */
enum class AppSource {
    /** Installed by the RuStore catalog. */
    RUSTORE,

    /** Installed by Google Play. */
    GOOGLE_PLAY,

    /** Sideloaded (PackageInstaller, file manager, adb). */
    SIDELOAD,

    /** Another store or installer. */
    OTHER,

    /** Installer unknown. */
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps an installer package name to a source.
         *
         * The list of store names is deliberately explicit: guessing by
         * substring is not allowed (`ru.vostok.vstore` is VK Store, not
         * RuStore). Google Play is named separately because it re-signs its
         * builds with its own key.
         */
        fun fromInstaller(installerPackage: String?): AppSource {
            val installer = installerPackage?.trim()?.lowercase()
            if (installer.isNullOrEmpty()) return UNKNOWN

            return when {
                installer == "com.android.vending" -> GOOGLE_PLAY

                installer in RUSTORE_INSTALLERS -> RUSTORE

                installer == "com.android.packageinstaller" ||
                    installer == "com.google.android.packageinstaller" -> SIDELOAD

                else -> OTHER
            }
        }

        /** Known RuStore installers. */
        private val RUSTORE_INSTALLERS = setOf(
            "com.rustore.app",
            "ru.rustore.app",
            "ru.vostok.rustore",
            "com.vk.rustore",
        )
    }
}
