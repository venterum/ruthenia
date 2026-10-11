// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

package com.vntrum.ruthenia

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import com.vntrum.ruthenia.core.data.repository.LibraryRepository
import com.vntrum.ruthenia.core.data.settings.SettingsRepository
import com.vntrum.ruthenia.core.security.RuStoreImageClient
import com.vntrum.ruthenia.core.updates.UpdateScheduler

@HiltAndroidApp
class RutheniaApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var updateScheduler: UpdateScheduler

    @Inject
    lateinit var libraryRepository: LibraryRepository

    /**
     * Icons live on the RuStore CDN, which is served by a Russian Trusted CA
     * chain, so Coil must use a client with composite trust.
     */
    @Inject
    @RuStoreImageClient
    lateinit var ruStoreImageClient: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { ruStoreImageClient }))
            }
            .build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // The background check schedule always matches the current settings.
        applicationScope.launch {
            settingsRepository.settings.collect { settings ->
                updateScheduler.reschedule(settings)
            }
        }

        // The installed list may have changed while the app was closed.
        applicationScope.launch {
            libraryRepository.syncInstalledVersions()
        }
    }
}
