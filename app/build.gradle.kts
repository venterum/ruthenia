// Ruthenia Store — an open, private client for installing and updating apps.
// Copyright (C) 2026 Ruthenia Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.baseline.profile)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The release signature lives outside the repository: `signing.properties` in the project root
// (in .gitignore) with a reference to the keystore. Without the file, release is built
// unsigned — convenient for local builds without access to the key.
val signingFile = rootProject.file("signing.properties")
val signingProps = if (signingFile.exists()) {
    Properties().apply { signingFile.inputStream().use { load(it) } }
} else {
    null
}

android {
    namespace = "com.vntrum.ruthenia"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vntrum.ruthenia"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingProps?.let { signingConfigs.getByName("release") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

baselineProfile {
    // Keep the generated profiles in the repository and merge variants into
    // app/src/main/generated/baselineProfiles: the profile is generated rarely
    // and should travel with the sources (see CONTRIBUTING).
    saveInSrc = true
    mergeIntoMain = true
}

dependencies {
    implementation(project(":core:security"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:installer"))
    implementation(project(":core:updates"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:catalog"))
    implementation(project(":feature:library"))
    implementation(project(":feature:app-details"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Delivers the committed baseline/startup profiles to ART on the device
    // and lets macrobenchmarks reset the compilation state between runs.
    implementation(libs.androidx.profileinstaller)

    // Optional silent install: the app manifest declares
    // rikka.shizuku.ShizukuProvider, so the provider artifact is a direct
    // dependency (the installer logic itself lives in :core:installer).
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // WorkManager is initialized manually (see RutheniaApplication), so
    // HiltWorkerFactory is needed by the app directly.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)

    // Producer of the baseline/startup profiles: `./gradlew :app:generateBaselineProfile`.
    baselineProfile(project(":macrobenchmark"))
}
