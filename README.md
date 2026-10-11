# Ruthenia Store

[![License](https://ziadoua.github.io/m3-Markdown-Badges/badges/LicenceGPLv3/licencegplv31.svg)](LICENSE)
[![Kotlin](https://ziadoua.github.io/m3-Markdown-Badges/badges/Kotlin/kotlin1.svg)](https://kotlinlang.org)
[![Android](https://ziadoua.github.io/m3-Markdown-Badges/badges/Android/android1.svg)](https://developer.android.com)

[Русская версия здесь!](README.ru.md)

**A lightweight, private RuStore client. Written from scratch. No tracking, no ads, no bloat.**

Ruthenia Store lets you install and update apps from the RuStore catalog (banks, government services and other Russian services) without the official app — no trackers, no nagging updates, no cluttered interface.

---

## What's inside?

- **Search** the RuStore catalog by name or package name
- **Home feed** — "Recommended" plus category sections (banks, government services, games, etc.)
- **Games page** — popular games and genre feeds (can be hidden in settings)
- **App details** — screenshots, "what's new", rating, "More from developer"
- **Install and update** — split-APK support, a single install session, background downloads via WorkManager
- **Silent install via Shizuku** — optionally installs APKs without the system dialog (off by default, signature verification still applies)
- **Library** — installed apps with update status, batch update, import from device
- **Certificate refresh** — manual re-check of SPKI pins against live RuStore chains and the official Mintsifry CA
- **Privacy above all** — no analytics, crash reporting or ad SDKs, anonymous operation, signature verification on every update

---

## Screenshots

<!-- TODO: add screenshots (docs/screenshots/*.png) -->

---

## Building from scratch

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (~3.3 MB, R8)
./gradlew :app:assembleRelease

# Unit tests (84 tests, offline)
./gradlew testDebugUnitTest
```

**Requirements:** JDK 17, Android SDK 37 (compile/target), minSdk 26

---

## Documentation

| Document | Description |
|----------|-------------|
| [CONTRIBUTING](docs/CONTRIBUTING.md) | Build, style, PR rules |
| [PRIVACY](docs/PRIVACY.md) | Data practices and permissions |
| [SECURITY](docs/SECURITY.md) | Reporting vulnerabilities |
| [Fastlane metadata](fastlane/metadata/android/) | Store listing |

**Keep in mind this is an unofficial client** built on top of a reverse-engineered RuStore API (which has no public documentation). In practice, that means it can stop working when the API changes.

---

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

The network layer builds on analysis of [Obtainium](https://github.com/ImranR98/Obtainium) (GPL-3.0), file `lib/app_sources/rustore.dart`.
