// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.vntrum.ruthenia.ui.profile

import androidx.annotation.StringRes
import com.vntrum.ruthenia.R

/**
 * The playful rank of the device token, derived from the number of apps in the
 * local library. Eight steps, mirroring the eight token motifs.
 *
 * The [latin] epithet is deliberately not localised: it is the "engraved" Latin
 * half of the rank, while [titleRes] carries the Slavic/English half. Nothing
 * here is sent anywhere — the rank is computed from on-device counters.
 */
enum class TokenRank(
    val numeral: String,
    val latin: String,
    @StringRes val titleRes: Int,
    /** Minimum number of library apps that unlocks this rank. */
    val minApps: Int,
) {
    GUEST("I", "HOSPES", R.string.profile_rank_guest, 0),
    APPRENTICE("II", "DISCIPVLVS", R.string.profile_rank_apprentice, 1),
    JOURNEYMAN("III", "SOCIVS", R.string.profile_rank_journeyman, 3),
    MASTER("IV", "MAGISTER", R.string.profile_rank_master, 6),
    ARCHITECT("V", "ARCHITECTVS", R.string.profile_rank_architect, 11),
    KEEPER("VI", "CVSTOS", R.string.profile_rank_keeper, 21),
    SORCERER("VII", "MAGVS", R.string.profile_rank_sorcerer, 41),
    ARCHIVIST("VIII", "ARCHIVARIVS", R.string.profile_rank_archivist, 76),
    ;

    companion object {

        /** The highest rank whose threshold [count] reaches. */
        fun forCount(count: Int): TokenRank = entries.last { count >= it.minApps }

        /** The next rank, or `null` at the top of the ladder. */
        fun next(rank: TokenRank): TokenRank? = entries.getOrNull(rank.ordinal + 1)

        /** Apps still missing before [next] unlocks; `null` at the top. */
        fun appsToNext(rank: TokenRank, count: Int): Int? =
            next(rank)?.let { (it.minApps - count).coerceAtLeast(1) }

        /**
         * Progress towards the next rank, `0f..1f`. Returns `1f` at the top of
         * the ladder so the indicator is full rather than empty.
         */
        fun progress(rank: TokenRank, count: Int): Float {
            val next = next(rank) ?: return 1f
            val span = (next.minApps - rank.minApps).coerceAtLeast(1)
            return ((count - rank.minApps).toFloat() / span).coerceIn(0f, 1f)
        }
    }
}
