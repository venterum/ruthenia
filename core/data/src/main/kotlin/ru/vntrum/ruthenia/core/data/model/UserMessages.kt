/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.model

import android.content.Context
import com.vntrum.ruthenia.core.data.R
import com.vntrum.ruthenia.core.network.RuStoreException

/**
 * Human-readable explanation of a network-layer error.
 *
 * The texts come from resources, so they are localized along with the rest of
 * the UI and never show the user "HTTP 500".
 */
fun Throwable.toUserMessage(context: Context): String = when (this) {
    is RuStoreException.AggregatedApp -> context.getString(R.string.error_aggregated)

    is RuStoreException.NoApkAvailable -> context.getString(R.string.error_no_apk)

    is RuStoreException.Http -> when (code) {
        400 -> context.getString(R.string.error_http_400)
        404 -> context.getString(R.string.error_http_404)
        419 -> context.getString(R.string.error_http_419)
        429 -> context.getString(R.string.error_http_429)
        else -> context.getString(R.string.error_http_other, code)
    }

    is RuStoreException.SessionUnavailable ->
        context.getString(R.string.error_session_unavailable)

    is RuStoreException.InvalidResponse ->
        context.getString(R.string.error_invalid_response)

    is RuStoreException.Network ->
        context.getString(R.string.error_network)

    // We deliberately show the error type: without it a user report
    // cannot be turned into a fix.
    else -> context.getString(
        R.string.error_unknown,
        this::class.simpleName.orEmpty(),
        message ?: context.getString(R.string.error_no_details),
    )
}
