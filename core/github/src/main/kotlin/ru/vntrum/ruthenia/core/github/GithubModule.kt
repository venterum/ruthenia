/*
 * Ruthenia Store — an open, private client for installing and updating apps.
 * Copyright (C) 2026 Ruthenia Store contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vntrum.ruthenia.core.github

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
import okhttp3.Cache
import okhttp3.OkHttpClient

/** System-trust client for GitHub; RuStore pins and Russian CA trust do not apply here. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GithubClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GithubBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GithubJson

@Module
@InstallIn(SingletonComponent::class)
object GithubModule {

    @Provides
    @Singleton
    @GithubClient
    fun provideGithubClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "github-http"), HTTP_CACHE_BYTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @GithubJson
    fun provideGithubJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideGithubApi(
        @GithubClient client: OkHttpClient,
        @GithubJson json: Json,
        tokenStore: GithubTokenStore,
        @GithubBaseUrl baseUrl: String,
    ): GithubApi = GithubApiImpl(
        client = client,
        json = json,
        tokenProvider = tokenStore,
        baseUrl = baseUrl,
    )

    @Provides
    @GithubBaseUrl
    fun provideGithubBaseUrl(): String = "https://api.github.com"

    private const val HTTP_CACHE_BYTES = 8L * 1024 * 1024
}
