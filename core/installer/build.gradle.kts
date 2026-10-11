// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vntrum.ruthenia.core.installer"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        // Shizuku loads the user service class by name at runtime; the keep
        // rules travel with this module (see consumer-rules.pro).
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        // The Shizuku user service is an AIDL interface (silent install).
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:security"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Optional silent install through Shizuku or Sui (shell/root identity).
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
