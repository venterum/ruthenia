// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baseline.profile)
}

android {
    namespace = "com.vntrum.ruthenia.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The app under test. The `androidx.baselineprofile` plugin adds the
    // `benchmarkRelease` and `nonMinifiedRelease` variants to it and keeps the
    // two modules in sync.
    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Benchmarks and the profile generator drive the app from a separate
    // process instead of instrumenting it.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    // Profiles are generated on a connected device or emulator.
    // The benchmark library needs root or API 33+ for profile collection.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.junit)
    // @LargeTest lives in runner, which the benchmark library pulls only at runtime.
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
