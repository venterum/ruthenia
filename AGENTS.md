# AGENTS.md

Ruthenia Store — an unofficial RuStore client (Kotlin, Jetpack Compose, GPL-3.0-or-later).
Read `docs/CONTRIBUTING.md` for module ownership, privacy rules and PR expectations.

## Build and test

Toolchain: JDK 17, Android SDK 37, Gradle wrapper 9.7.1. There is no committed
`local.properties`: set `ANDROID_HOME`/`ANDROID_SDK_ROOT` or create `local.properties`.
Install the SDK as `platforms;android-37.0` and `build-tools;37.0.0` — the package is not
`platforms;android-37`.

```bash
./gradlew :app:assembleDebug        # installable debug APK (debug key)
./gradlew :app:assembleRelease      # R8 build; unsigned unless signing.properties exists
./gradlew testDebugUnitTest         # all JVM unit tests, offline, no device needed
./gradlew :app:lintDebug

# Single test
./gradlew :core:data:testDebugUnitTest --tests "com.vntrum.ruthenia.core.data.model.VersionComparatorTest"
```

- APKs land in `app/build/outputs/apk/{debug,release}/ruthenia-0.2.0[-debug].apk`.
- Tests are JVM-only under each module's `src/test`; there is no `androidTest` suite.
- Release signing is configured only when the gitignored root `signing.properties` exists
  (see `app/build.gradle.kts`).
- `./gradlew :app:generateBaselineProfile` needs a connected API 33+ device/emulator.
  Profiles are saved into `app/src/main/generated/baselineProfiles` and merged into main;
  do not hand-edit them.

## Toolchain quirks

- AGP 9.4.1 has built-in Kotlin. Do **not** add or apply `org.jetbrains.kotlin.android`;
  the root `build.gradle.kts` deliberately omits it. The Compose compiler plugin is still
  applied per UI module.
- Versions live only in `gradle/libs.versions.toml`. Compose `1.13.0-alpha01` and
  Material3 `1.5.0-alpha28` are pinned for Material 3 Expressive APIs — do not downgrade
  or “stabilize” them.
- KSP drives Hilt, Room and `@HiltWorker` codegen; never edit generated sources.
- Room schemas are committed under `core/data/schemas/`. Add a new numbered schema and a
  migration; never edit or delete existing schema JSON.
- `versionCode` and `versionName` are hardcoded in `app/build.gradle.kts`; bump both for a
  release.
- The default `values/strings.xml` is Russian; English lives in `values-en/strings.xml`.
  Every user-facing string must be added to both, and counts must use `<plurals>`.
- App/feature sources sit under `src/main/kotlin/ru/vntrum/...` but declare package
  `com.vntrum.ruthenia`; copy the nearest file instead of deriving the package from the path.

## Architecture and wiring

- Modules: `:app` (entrypoint, `NavHost`, Hilt root),
  `:core:{security,network,data,installer,updates,designsystem}`,
  `:feature:{catalog,library,app-details,settings}`, `:macrobenchmark`.
- Dependencies point inward only: `feature -> core`; `:app` composes features. Never make a
  core module depend on a feature or the app.
- Entry flow: `RutheniaApplication` (Hilt, WorkManager, Coil) -> `MainActivity` (splash,
  locale, install/deep-link intents) -> `ui/RutheniaApp.kt` (`NavHost`).
- WorkManager is initialized manually with `HiltWorkerFactory`; the manifest removes the
  default initializer. Do not re-add `WorkManagerInitializer`.
- Launcher entry points are `activity-alias` blocks (one per icon colour); `MainActivity`
  intentionally has no `MAIN`/`LAUNCHER` filter. Do not add one.
- Use `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`, not the deprecated
  `androidx.hilt.navigation.compose.hiltViewModel`.
- Build HTTP clients through `:core:security` DI (composite trust manager + certificate
  pins). Do not construct ad-hoc `OkHttpClient`s; RuStore hosts and headers are centralized
  in `core/network/.../RuStoreConfig.kt`.

## Conventions and constraints

- Every source file starts with the GPL-3.0 header; Kotlin uses 4 spaces and trailing commas.
  Comments explain “why”, in English; documentation is mirrored under `docs/ru/`.
- Never add analytics, crash reporting or ads. A new network host or permission requires
  updating `docs/PRIVACY.md` in the same change.
- Keep one logical change per PR and dependency bumps in a separate PR. Changes must pass
  `testDebugUnitTest` and `:app:lintDebug`.
