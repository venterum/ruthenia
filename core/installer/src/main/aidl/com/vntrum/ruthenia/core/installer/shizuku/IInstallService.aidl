/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.installer.shizuku;

import android.os.ParcelFileDescriptor;

/**
 * Interface of the Shizuku user service that installs APKs with the shell
 * identity (see ShizukuInstallService).
 */
interface IInstallService {

    /** Reserved by Shizuku: called before the service process is destroyed. */
    void destroy() = 16777114;

    /**
     * Installs the given APK files (base and splits) silently.
     * Returns an empty string on success, otherwise a human-readable error.
     */
    String install(in ParcelFileDescriptor[] apks, String packageName) = 1;
}
