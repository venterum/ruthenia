/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional GitHub token storage.
 *
 * The token is encrypted with an AES/GCM key that never leaves Android
 * Keystore; only the IV+ciphertext is kept in SharedPreferences. The token is
 * deliberately not part of [com.vntrum.ruthenia.core.data.settings.AppSettings]
 * and therefore never appears in backups or logs.
 */
fun interface GithubTokenProvider {
    fun token(): String?
}

@Singleton
class GithubTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : GithubTokenProvider {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun token(): String? = runCatching {
        val payload = preferences.getString(KEY_TOKEN, null) ?: return null
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        if (bytes.size <= GCM_IV_BYTES) return null
        val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8).takeIf { it.isNotBlank() }
    }.getOrNull()

    fun hasToken(): Boolean = !token().isNullOrBlank()

    fun setToken(token: String?) {
        if (token.isNullOrBlank()) {
            preferences.edit().remove(KEY_TOKEN).apply()
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey())
            }
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val payload = cipher.iv + encrypted
            preferences.edit()
                .putString(KEY_TOKEN, Base64.encodeToString(payload, Base64.NO_WRAP))
                .apply()
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "github_auth"
        const val KEY_TOKEN = "token"
        const val KEY_ALIAS = "ruthenia.github.token"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
