# Contributing

> [Русская версия здесь!](ru/CONTRIBUTING.md)

Thanks for your interest in the project! Here is a short guide on how you can help with code.

---

## Build and tests

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK
./gradlew :app:assembleRelease

# Unit tests (offline)
./gradlew testDebugUnitTest

# Lint
./gradlew :app:lintDebug
```

**Requires:** JDK 17, Android SDK 37

---

## Code style

- Kotlin, 4 spaces, trailing commas
- Every file starts with the GPL-3.0 header (copy it from any neighbouring file)
- All user-facing strings live only in `strings.xml` + `values-en/strings.xml`
- Build number labels with `plurals` (no string concatenation)
- Don't over-comment! Remember that comments explain "why", not "what"
- The primary language of documentation, code and comments is English; where possible, documentation is duplicated in Russian.

---

## Architecture (modules)

| Module | Responsibility |
|--------|----------------|
| `:core:security` | Certificate trust, SPKI pins, manual certificate refresh |
| `:core:network` | RuStore API, DTOs, session signature |
| `:core:data` | Room, DataStore, repositories, backup |
| `:core:installer` | APK download, signature/size verification, PackageInstaller, Shizuku |
| `:core:updates` | WorkManager: update checks, background downloads |
| `:core:designsystem` | Theme, tokens, Material 3 Expressive components |
| `:feature:*` | Screens and ViewModels |
| `:macrobenchmark` | Baseline/startup profiles, performance measurements |
| `:app` | Entry point, navigation, Hilt graph |

Dependencies **always** point inward (`feature → core`), never the other way around.

---

## Privacy rules

- A new network host or a change to the data sent over the network → update `PRIVACY.md` in the same PR
- Any proposal to add analytics or crash reporting to the project is not considered
- New permissions only with a justification in the manifest and in PRIVACY.md

---

## Pull Requests

- One logical change per PR; describe what changed and why
- Include how you tested it: commands and, if the UI changed, before/after screenshots
- Don't bump dependency versions alongside other changes — open a separate PR for that
- All tests must pass (`testDebugUnitTest`, `lintDebug`)

---

## License

By submitting a PR you agree to distribute your code under [GPL-3.0-or-later](../LICENSE).
