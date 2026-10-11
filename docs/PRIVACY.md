# Privacy policy

> [Русская версия здесь!](ru/PRIVACY.md)

In a nutshell, **Ruthenia Store does not collect personal data, does not use analytics, and makes no network requests other than to the RuStore catalog API (plus fetching the official Mintsifry certificates on a manual refresh in settings).** All processing happens strictly locally.

---

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | The only network access; only to the hosts listed below |
| `POST_NOTIFICATIONS` | A single grouped "updates available" notification (Android 13+); requested only when background checks are enabled |
| `REQUEST_INSTALL_PACKAGES` | Installing downloaded APKs via the system `PackageInstaller` |
| `QUERY_ALL_PACKAGES` | Reading the list of installed apps **locally** (never sent anywhere) |
| `ACCESS_NETWORK_STATE` | Network checks in WorkManager (Wi-Fi / availability) |
| `WAKE_LOCK` | Keeping the CPU awake during update checks |

---

## Network hosts

| Host | Purpose | TLS | Pinning |
|------|---------|-----|---------|
| `backapi.rustore.ru` | Search, feed, app info, download links | System | ✅ |
| `api.rustore.ru` | Nonce for the session signature | System | ✅ |
| `static-m.rustore.ru` | APK/ZIP downloads, icons | **Russian Trusted Root/Sub CA** | ✅ |
| `static.rustore.ru` | Possible icon CDN | System | ✅ |
| `gu-st.ru` | Official Russian Trusted CA certificates (Mintsifry); only on a manual certificate refresh | System (GlobalSign) | — |

- All requests use **HTTPS only** (`usesCleartextTraffic=false`)
- The APK bundles the Russian Trusted CA, used only to reach `static-m.rustore.ru` over HTTPS.
- A request is rejected if the chain does not match the known pins
- Pins are baked into the APK. They can be refreshed manually: Settings → "Security" → "RuStore certificates". The app checks the live RuStore host chains and the official Mintsifry certificates (`gu-st.ru`). Nothing happens automatically.

---

## What is sent in requests

| Field | Value |
|-------|-------|
| `deviceId` | 8 random bytes + a hash of the model; generated on every launch; **not stored**, not linked to an advertising ID |
| `deviceManufacturerName`, `deviceModelName`, `deviceModel` | Constants (`Google Pixel 8 Pro`) — the same for everyone |
| `firmwareVer`, `androidSdkVer`, `firmwareLang` | Constants |
| `ruStoreVerCode`, `deviceType` | Constants |
| `User-Agent` | `RuStore/<ver> (Android …)` |
| `X-Client-Signature` | `base64(HMAC-SHA256(key, nonce ‖ certSha256))` — computed locally |
| `download-link` body | `appId`, `firstInstall`, `supportedAbis`, `sdkVersion`, `screenDensity` — the real profile needed for correct split-APKs |

---

## What the app does NOT do

- ❌ Does not send data to hosts other than those listed above
- ❌ Does not personalise recommendations
- ❌ Does not install APKs without your explicit consent
- ❌ Does not collect usage statistics or analytics
- ❌ Does not request any unnecessary permissions

---

## Local storage

| Data | Where | Lifetime |
|------|-------|----------|
| App cache (name, icon, description, rating, downloads, version) | Room `catalog_apps` | Until the cache is cleared |
| Home feed sections | Room `home_entries` | Until the cache is cleared |
| Installed list + versions | Room `installed_apps` | Until removed from the list |
| Hidden updates (package + version) | DataStore `ignored_updates` | Until settings are reset |
| Settings (theme, dynamic colour, interval, Wi-Fi, diagnostics) | DataStore | Indefinitely, locally only |
| Device profile seed (random install ID for the header seal) | DataStore `local_identity_seed` | Until app data is cleared; not in the backup |
| Downloaded APKs | `cacheDir/apk/<package>` | Deleted after installation |

---

## Logging

- There are no third-party crash reporters or analytics, and there never will be
- There is no permanent request logging
- Optional local diagnostics: **off by default**, stays on the device, never sent anywhere

---

## Dependencies

| Library | Role |
|---------|------|
| OkHttp | HTTP client (directly, without Retrofit) |
| kotlinx.serialization | JSON parsing |
| kotlinx.coroutines | Asynchrony |
| Hilt / Dagger | DI |
| Room | Local database |
| DataStore Preferences | Settings |
| WorkManager | Background update checks |
| Coil | Image loading (same OkHttp) |
| AndroidX Compose (Material 3 Expressive) | UI |
| Navigation Compose | Navigation |
| Shizuku API/provider | Optional silent install (off by default, no network) |

**You can verify the list yourself:** `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`

---

## Don't believe what's written above?

Good! Below is a simple guide on how to verify everything yourself.

1. Install [mitmproxy](https://mitmproxy.org/) and route the device's traffic through it
2. Install the mitmproxy CA **for testing only** (the app does not trust it — pinning + a custom TrustManager)
3. Make sure the hosts match the table above

Checking permissions and APK contents:

```bash
aapt2 dump badging app/build/outputs/apk/release/app-release*.apk
unzip -l app/build/outputs/apk/release/app-release*.apk | grep -E "res/.*\.cer"
```
