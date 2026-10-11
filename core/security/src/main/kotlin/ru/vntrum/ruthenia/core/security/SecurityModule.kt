/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.security

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/** JSON only for the internal pin config, to avoid clashing with the network DTOs. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecurityJson

/** OkHttp client for Coil: talks to the RuStore CDN with composite trust. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RuStoreImageClient

/** OkHttp client for the manual certificate refresh: composite trust, no pins. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CertificateDiscoveryClient

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    private const val PIN_CACHE_FILE = "rustore_pins.json"

    @Provides
    @Singleton
    @SecurityJson
    fun provideSecurityJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun providePinConfigStore(
        @ApplicationContext context: Context,
        @SecurityJson json: Json,
    ): PinConfigStore = PinConfigStore(
        file = File(context.filesDir, PIN_CACHE_FILE),
        json = json,
    )

    /**
     * Client used only for the manual certificate refresh.
     *
     * It deliberately has no [okhttp3.CertificatePinner] — otherwise it could not
     * discover a rotated pin — and trusts system roots plus the bundled Russian
     * Trusted CA, so it can reach both `gu-st.ru` (GlobalSign) and the RuStore
     * hosts.
     */
    @Provides
    @Singleton
    @CertificateDiscoveryClient
    fun provideCertificateDiscoveryClient(
        @ApplicationContext context: Context,
    ): OkHttpClient {
        val trustManager = TrustStores.compositeTrustManager(context)
        return OkHttpClient.Builder()
            .sslSocketFactory(trustManager.asSocketFactory(), trustManager)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideCertificateRefresher(
        @ApplicationContext context: Context,
        store: PinConfigStore,
        @CertificateDiscoveryClient discoveryClient: OkHttpClient,
    ): CertificateRefresher = CertificateRefresher(
        context = context,
        store = store,
        discoveryClient = discoveryClient,
    )

    @Provides
    @Singleton
    fun provideRuStoreHttpClient(
        @ApplicationContext context: Context,
        refresher: CertificateRefresher,
    ): RuStoreHttpClient = RuStoreClients.build(
        context = context,
        pins = refresher.current(),
    )

    @Provides
    @Singleton
    @RuStoreImageClient
    fun provideRuStoreImageClient(client: RuStoreHttpClient): OkHttpClient = client.imageClient
}
