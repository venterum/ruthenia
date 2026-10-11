/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.vntrum.ruthenia.core.data.settings.AppIconVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Switches the app's launcher icon by enabling the matching `<activity-alias>`
 * and disabling every other one.
 *
 * The aliases are declared in the app manifest under
 * `<packageName>.launcher.<aliasSuffix>`. Exactly one is enabled at a time, and
 * every variant shares the themed (monochrome) layer, so Android 13+ themed
 * icons keep working regardless of the choice.
 */
@Singleton
class LauncherIconController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Enables [variant]'s alias and disables every other one. */
    fun apply(variant: AppIconVariant) {
        val packageManager = context.packageManager
        // Enable the target first: disabling the active alias before its
        // replacement exists can drop the app from the home screen.
        setState(packageManager, variant, enabled = true)
        AppIconVariant.entries
            .filter { it != variant }
            .forEach { setState(packageManager, it, enabled = false) }
    }

    private fun setState(
        packageManager: PackageManager,
        variant: AppIconVariant,
        enabled: Boolean,
    ) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            packageManager.setComponentEnabledSetting(
                variant.componentName(),
                state,
                // Do not kill the running task: switching the icon must not
                // drop the user out of the app.
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /**
     * Applies [variant] only when it does not match the currently enabled alias.
     *
     * Called on launch as a safety net: an app update can reset the enabled
     * state back to the manifest default, which would silently revert the icon.
     */
    fun reconcile(variant: AppIconVariant) {
        if (enabledVariant() != variant) apply(variant)
    }

    /** The variant whose alias is currently enabled, if any. */
    private fun enabledVariant(): AppIconVariant? {
        val packageManager = context.packageManager
        return AppIconVariant.entries.firstOrNull { candidate ->
            packageManager.getComponentEnabledSetting(candidate.componentName()) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
    }

    private fun AppIconVariant.componentName(): ComponentName = ComponentName(
        context.packageName,
        "${context.packageName}.launcher.$aliasSuffix",
    )
}
