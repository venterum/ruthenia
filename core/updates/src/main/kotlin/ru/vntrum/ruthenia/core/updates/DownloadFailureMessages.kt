/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.updates

import android.content.Context
import com.vntrum.ruthenia.core.installer.ApkVerification
import com.vntrum.ruthenia.core.installer.ApkVerificationFailure
import com.vntrum.ruthenia.core.installer.InstallException
import com.vntrum.ruthenia.core.installer.InstallFailureCode
import com.vntrum.ruthenia.core.installer.InstallResult
import com.vntrum.ruthenia.core.network.RuStoreException
import java.io.IOException
import java.util.zip.ZipException

/** Actions a failure row can offer. The UI turns them into buttons/intents. */
enum class FailureAction {
    RETRY,
    REDOWNLOAD,
    INSTALL_AGAIN,
    OPEN_APP_DETAILS,
    OPEN_INSTALL_SETTINGS,
    OPEN_STORAGE_SETTINGS,
    OPEN_NETWORK_SETTINGS,
    OPEN_SHIZUKU,
    CHECK_UPDATES,
    COPY_DETAILS,
    REMOVE,
}

/** User-facing failure copy. */
data class FailureText(
    val title: String,
    val message: String,
    val primaryAction: FailureAction? = null,
    val secondaryAction: FailureAction? = null,
    val isError: Boolean = true,
)

/** Translates a stable failure code into localized copy and recovery actions. */
fun TaskFailure.text(context: Context): FailureText = when (code) {
    TaskFailureCode.NETWORK -> FailureText(
        title = context.getString(R.string.failure_network_title),
        message = context.getString(R.string.failure_network_message),
        primaryAction = FailureAction.RETRY,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.OFFLINE -> FailureText(
        title = context.getString(R.string.failure_offline_title),
        message = context.getString(R.string.failure_offline_message),
        primaryAction = FailureAction.RETRY,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.WAITING_WIFI -> FailureText(
        title = context.getString(R.string.failure_waiting_wifi_title),
        message = context.getString(R.string.failure_waiting_wifi_message),
        primaryAction = FailureAction.OPEN_NETWORK_SETTINGS,
        secondaryAction = FailureAction.REMOVE,
        isError = false,
    )

    TaskFailureCode.RATE_LIMITED -> FailureText(
        title = context.getString(R.string.failure_rate_limited_title),
        message = context.getString(R.string.failure_rate_limited_message),
        primaryAction = FailureAction.RETRY,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.SERVER -> FailureText(
        title = context.getString(R.string.failure_server_title),
        message = context.getString(R.string.failure_server_message),
        primaryAction = FailureAction.RETRY,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.SESSION -> FailureText(
        title = context.getString(R.string.failure_session_title),
        message = context.getString(R.string.failure_session_message),
        primaryAction = FailureAction.RETRY,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.HTTP -> FailureText(
        title = context.getString(R.string.failure_http_title),
        message = context.getString(R.string.failure_http_message),
        primaryAction = FailureAction.RETRY,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.APP_UNAVAILABLE -> FailureText(
        title = context.getString(R.string.failure_app_unavailable_title),
        message = context.getString(R.string.failure_app_unavailable_message),
        primaryAction = FailureAction.CHECK_UPDATES,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.NO_FILES -> FailureText(
        title = context.getString(R.string.failure_no_files_title),
        message = context.getString(R.string.failure_no_files_message),
        primaryAction = FailureAction.CHECK_UPDATES,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.INVALID_RESPONSE -> FailureText(
        title = context.getString(R.string.failure_invalid_response_title),
        message = context.getString(R.string.failure_invalid_response_message),
        primaryAction = FailureAction.REDOWNLOAD,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.INVALID_APK -> FailureText(
        title = context.getString(R.string.failure_invalid_apk_title),
        message = context.getString(R.string.failure_invalid_apk_message),
        primaryAction = FailureAction.REDOWNLOAD,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.CORRUPTED -> FailureText(
        title = context.getString(R.string.failure_corrupted_title),
        message = context.getString(R.string.failure_corrupted_message),
        primaryAction = FailureAction.REDOWNLOAD,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.SIZE_MISMATCH -> FailureText(
        title = context.getString(R.string.failure_size_mismatch_title),
        message = context.getString(R.string.failure_size_mismatch_message),
        primaryAction = FailureAction.REDOWNLOAD,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.SIGNATURE_MISMATCH -> FailureText(
        title = context.getString(R.string.failure_signature_mismatch_title),
        message = context.getString(R.string.failure_signature_mismatch_message),
        primaryAction = FailureAction.OPEN_APP_DETAILS,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.PACKAGE_MISMATCH -> FailureText(
        title = context.getString(R.string.failure_package_mismatch_title),
        message = context.getString(R.string.failure_package_mismatch_message),
        primaryAction = FailureAction.REDOWNLOAD,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.VERSION_MISMATCH -> FailureText(
        title = context.getString(R.string.failure_version_mismatch_title),
        message = context.getString(R.string.failure_version_mismatch_message),
        primaryAction = FailureAction.CHECK_UPDATES,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.INCOMPATIBLE -> FailureText(
        title = context.getString(R.string.failure_incompatible_title),
        message = context.getString(R.string.failure_incompatible_message),
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.STORAGE_FULL -> FailureText(
        title = context.getString(R.string.failure_storage_full_title),
        message = context.getString(R.string.failure_storage_full_message),
        primaryAction = FailureAction.OPEN_STORAGE_SETTINGS,
        secondaryAction = FailureAction.RETRY,
    )

    TaskFailureCode.FILES_LOST -> FailureText(
        title = context.getString(R.string.failure_files_lost_title),
        message = context.getString(R.string.failure_files_lost_message),
        primaryAction = FailureAction.REDOWNLOAD,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.PERMISSION_REQUIRED -> FailureText(
        title = context.getString(R.string.failure_permission_required_title),
        message = context.getString(R.string.failure_permission_required_message),
        primaryAction = FailureAction.OPEN_INSTALL_SETTINGS,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.INSTALL_BLOCKED -> FailureText(
        title = context.getString(R.string.failure_install_blocked_title),
        message = context.getString(R.string.failure_install_blocked_message),
        primaryAction = FailureAction.COPY_DETAILS,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.INSTALL_CONFLICT -> FailureText(
        title = context.getString(R.string.failure_install_conflict_title),
        message = context.getString(R.string.failure_install_conflict_message),
        primaryAction = FailureAction.OPEN_APP_DETAILS,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.INSTALL_CANCELLED -> FailureText(
        title = context.getString(R.string.failure_install_cancelled_title),
        message = context.getString(R.string.failure_install_cancelled_message),
        primaryAction = FailureAction.INSTALL_AGAIN,
        secondaryAction = FailureAction.REMOVE,
        isError = false,
    )

    TaskFailureCode.INSTALL_INVALID -> FailureText(
        title = context.getString(R.string.failure_install_invalid_title),
        message = context.getString(R.string.failure_install_invalid_message),
        primaryAction = FailureAction.REDOWNLOAD,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.INSTALL_INCOMPATIBLE -> FailureText(
        title = context.getString(R.string.failure_install_incompatible_title),
        message = context.getString(R.string.failure_install_incompatible_message),
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.INSTALL_TIMEOUT -> FailureText(
        title = context.getString(R.string.failure_install_timeout_title),
        message = context.getString(R.string.failure_install_timeout_message),
        primaryAction = FailureAction.INSTALL_AGAIN,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.INTERRUPTED -> FailureText(
        title = context.getString(R.string.failure_interrupted_title),
        message = context.getString(R.string.failure_interrupted_message),
        primaryAction = FailureAction.RETRY,
        secondaryAction = FailureAction.REMOVE,
    )

    TaskFailureCode.UNKNOWN -> FailureText(
        title = context.getString(R.string.failure_unknown_title),
        message = context.getString(R.string.failure_unknown_message),
        primaryAction = FailureAction.COPY_DETAILS,
        secondaryAction = FailureAction.REMOVE,
    )
}

/** Label for a recovery action, localized with the rest of the UI. */
fun FailureAction.label(context: Context): String = context.getString(
    when (this) {
        FailureAction.RETRY -> R.string.failure_action_retry
        FailureAction.REDOWNLOAD -> R.string.failure_action_redownload
        FailureAction.INSTALL_AGAIN -> R.string.failure_action_install_again
        FailureAction.OPEN_APP_DETAILS -> R.string.failure_action_open_app
        FailureAction.OPEN_INSTALL_SETTINGS -> R.string.failure_action_install_settings
        FailureAction.OPEN_STORAGE_SETTINGS -> R.string.failure_action_storage_settings
        FailureAction.OPEN_NETWORK_SETTINGS -> R.string.failure_action_network_settings
        FailureAction.OPEN_SHIZUKU -> R.string.failure_action_shizuku
        FailureAction.CHECK_UPDATES -> R.string.failure_action_check_updates
        FailureAction.COPY_DETAILS -> R.string.failure_action_copy_details
        FailureAction.REMOVE -> R.string.failure_action_remove
    },
)

/** Classifies a download-stage exception into a stable failure code. */
fun Throwable.toTaskFailure(): TaskFailure {
    val failureCode = when (this) {
        is RuStoreException.Network -> TaskFailureCode.NETWORK
        is RuStoreException.SessionUnavailable -> TaskFailureCode.SESSION
        is RuStoreException.NoApkAvailable -> TaskFailureCode.NO_FILES
        is RuStoreException.AggregatedApp -> TaskFailureCode.APP_UNAVAILABLE
        is RuStoreException.InvalidResponse -> TaskFailureCode.INVALID_RESPONSE
        is RuStoreException.Http -> when (code) {
            404, 410 -> TaskFailureCode.APP_UNAVAILABLE
            419 -> TaskFailureCode.SESSION
            429 -> TaskFailureCode.RATE_LIMITED
            in 500..599 -> TaskFailureCode.SERVER
            else -> TaskFailureCode.HTTP
        }

        is InstallException.DownloadFailed -> when (code) {
            403, 404, 410 -> TaskFailureCode.APP_UNAVAILABLE
            416 -> TaskFailureCode.INTERRUPTED
            429 -> TaskFailureCode.RATE_LIMITED
            in 500..599 -> TaskFailureCode.SERVER
            else -> TaskFailureCode.HTTP
        }

        is InstallException.IntegrityMismatch -> TaskFailureCode.SIZE_MISMATCH
        is InstallException.DigestMismatch -> TaskFailureCode.INVALID_APK
        is InstallException.ContainerHasNoApk -> TaskFailureCode.CORRUPTED
        is InstallException.InvalidApk -> TaskFailureCode.INVALID_APK
        is InstallException.PackageMismatch -> TaskFailureCode.PACKAGE_MISMATCH
        is InstallException.VersionMismatch -> TaskFailureCode.VERSION_MISMATCH
        is InstallException.Incompatible -> TaskFailureCode.INCOMPATIBLE
        is InstallException.StorageFull -> TaskFailureCode.STORAGE_FULL
        is InstallException.SessionFailed -> TaskFailureCode.INTERRUPTED
        is ZipException -> TaskFailureCode.CORRUPTED
        is IOException -> TaskFailureCode.NETWORK
        is SecurityException -> TaskFailureCode.PERMISSION_REQUIRED
        else -> TaskFailureCode.UNKNOWN
    }
    return TaskFailure(code = failureCode, detail = sanitizedDetail())
}

/** Maps the verifier result into the shared taxonomy. */
fun ApkVerification.Failed.toTaskFailure(): TaskFailure = TaskFailure(
    code = when (code) {
        ApkVerificationFailure.INVALID_APK -> TaskFailureCode.INVALID_APK
        ApkVerificationFailure.PACKAGE_MISMATCH -> TaskFailureCode.PACKAGE_MISMATCH
        ApkVerificationFailure.VERSION_MISMATCH -> TaskFailureCode.VERSION_MISMATCH
        ApkVerificationFailure.INCOMPATIBLE -> TaskFailureCode.INCOMPATIBLE
        ApkVerificationFailure.SIGNATURE_MISMATCH -> TaskFailureCode.SIGNATURE_MISMATCH
    },
    detail = detail,
)

/** Maps an installer result into the shared taxonomy. */
fun InstallResult.Failure.toTaskFailure(): TaskFailure = TaskFailure(
    code = when (failure.code) {
        InstallFailureCode.USER_CANCELLED -> TaskFailureCode.INSTALL_CANCELLED
        InstallFailureCode.PERMISSION_REQUIRED -> TaskFailureCode.PERMISSION_REQUIRED
        InstallFailureCode.BLOCKED -> TaskFailureCode.INSTALL_BLOCKED
        InstallFailureCode.CONFLICT -> TaskFailureCode.INSTALL_CONFLICT
        InstallFailureCode.INVALID_APK -> TaskFailureCode.INSTALL_INVALID
        InstallFailureCode.STORAGE_FULL -> TaskFailureCode.STORAGE_FULL
        InstallFailureCode.INCOMPATIBLE -> TaskFailureCode.INSTALL_INCOMPATIBLE
        InstallFailureCode.TIMEOUT -> TaskFailureCode.INSTALL_TIMEOUT
        InstallFailureCode.SHIZUKU_UNAVAILABLE -> TaskFailureCode.INSTALL_BLOCKED
        InstallFailureCode.INTERRUPTED -> TaskFailureCode.INTERRUPTED
        InstallFailureCode.SESSION_FAILED -> TaskFailureCode.INSTALL_BLOCKED
        InstallFailureCode.UNKNOWN -> TaskFailureCode.UNKNOWN
    },
    detail = failure.detail,
)

/** Keeps technical details compact and free of signed query strings. */
private fun Throwable.sanitizedDetail(): String? {
    val message = message?.substringBefore('?')?.trim()?.takeIf { it.isNotEmpty() }
    val type = this::class.simpleName
    return listOfNotNull(type, message).joinToString(": ").takeIf { it.isNotEmpty() }
}
