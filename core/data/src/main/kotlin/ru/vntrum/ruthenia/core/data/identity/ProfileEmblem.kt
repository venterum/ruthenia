/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.data.identity

import kotlin.random.Random

/** Number of token motifs; the visual set itself lives in the design system. */
const val TOKEN_MOTIF_VARIANTS = 20

/**
 * Deterministically maps a [seed] to a motif index in `0 until [TOKEN_MOTIF_VARIANTS]`.
 * Pure and stable: the same identity always draws the same motif.
 */
fun tokenMotifIndex(seed: String): Int = seed.hashCode().mod(TOKEN_MOTIF_VARIANTS)

/** Deterministic rotation in degrees, `0 until 360` — keeps symmetric motifs distinct. */
fun tokenRotation(seed: String): Float = seed.hashCode().mod(360).toFloat()

/** Frame style of the token. */
enum class TokenRing {
    NONE,
    THIN,
    THICK,
    TEETH,
}

/**
 * The locally chosen look of the device token: motif, accent, frame style and
 * frame colour.
 *
 * Stored on the device only — it is a personalisation of the local identity,
 * not an account, and never leaves the phone.
 */
data class ProfileEmblem(
    val motif: Int,
    val accent: TokenColor,
    val ring: TokenRing,
    val ringColor: TokenColor,
) {
    companion object {
        /** The look a fresh install starts with: derived from the identity seed. */
        fun fromSeed(seed: String): ProfileEmblem {
            val accent = TokenColor.entries[seed.hashCode().mod(TokenColor.entries.size)]
            return ProfileEmblem(
                motif = tokenMotifIndex(seed),
                accent = accent,
                ring = TokenRing.THIN,
                ringColor = accent,
            )
        }

        /**
         * A fully random look for the "new token" action. The name and the look
         * are generated together — the name is part of the token.
         */
        fun random(): ProfileEmblem = ProfileEmblem(
            motif = Random.nextInt(TOKEN_MOTIF_VARIANTS),
            accent = TokenColor.entries.random(),
            ring = TokenRing.entries.random(),
            ringColor = TokenColor.entries.random(),
        )
    }
}
