# Security policy

> [Русская версия здесь!](ru/SECURITY.md)

## Reporting a vulnerability

**Use GitHub Security Advisories** (Security → Report a vulnerability in this repository).
That way the report stays private until a fix is released.

**Please do not open a public issue** before a fix is available.

---

## Response times

- **Acknowledgement** — within 7 days
- **Severity assessment and fix plan** — within 14 days
- **Public disclosure** — after the fix is released, agreed with the reporter (with attribution if desired)

---

## In scope

- APK signature and download integrity verification (`:core:installer`)
- Certificate trust and the pin config (`:core:security`)
- Request signing and session handling (`:core:network`)
- Silent install, bypassing the system dialog, data leaks
- Deep link handling

---

## Not a vulnerability

- Changes to the undocumented RuStore API: the integration may break until an update (a known limitation, see README)
- Being unable to install an update because the signature does not match — that is **protection**, not a bug
- The presence of `QUERY_ALL_PACKAGES` / `REQUEST_INSTALL_PACKAGES` — required for the app to work, described in PRIVACY.md

---

## Supported versions

Only the latest published version receives security updates.
