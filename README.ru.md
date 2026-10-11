# Ruthenia Store

[![License](https://ziadoua.github.io/m3-Markdown-Badges/badges/LicenceGPLv3/licencegplv31.svg)](LICENSE)
[![Kotlin](https://ziadoua.github.io/m3-Markdown-Badges/badges/Kotlin/kotlin1.svg)](https://kotlinlang.org)
[![Android](https://ziadoua.github.io/m3-Markdown-Badges/badges/Android/android1.svg)](https://developer.android.com)

[English version is here!](README.md)

**Лёгкий приватный клиент RuStore. Написан с нуля. Без слежки, без рекламы, без лишнего.**

Ruthenia Store позволяет устанавливать и обновлять приложения из каталога RuStore (банки, госуслуги и другие отечественные сервисы) без официального приложения — никаких трекеров, навязчивых обновлений и захламлённого интерфейса.

---

## Что есть в этом клиенте?

- **Поиск** по каталогу RuStore по названию или package name
- **Лента приложений** — «Рекомендуем» + категорийные секции (банки, госуслуги, игры и т.д.)
- **Страница игр** — популярные игры и жанровые ленты (можно скрыть в настройках)
- **Карточка приложения** — скриншоты, «что нового», рейтинг, «Ещё от разработчика»
- **Установка и обновления** — поддержка split-APK, одна инсталляционная сессия, фоновая загрузка через WorkManager
- **Тихая установка через Shizuku** — опционально ставит APK без системного диалога (по умолчанию выключена, проверка подписи сохраняется)
- **Библиотека** — установленные приложения со статусом обновлений, пакетное обновление, импорт с устройства
- **Обновление сертификатов** — ручная сверка SPKI-пинов с живыми цепочками RuStore и официальным CA Минцифры
- **Приватность ante omnia** — никакой аналитики, крашлитиков, рекламных SDK, анонимная работа, проверка подписи при каждом обновлении

---

## Скриншоты

<!-- TODO: Добавить скриншоты (docs/screenshots/*.png) -->

---

## Сборка с нуля

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (~3,3 МБ, R8)
./gradlew :app:assembleRelease

# Юнит-тесты (84 теста, без сети)
./gradlew testDebugUnitTest
```

**Требования:** JDK 17, Android SDK 37 (compile/target), minSdk 26

---

## Документация

| Документ | Описание |
|----------|----------|
| [CONTRIBUTING](docs/ru/CONTRIBUTING.md) | Сборка, стиль, правила PR |
| [PRIVACY](docs/ru/PRIVACY.md) | Практики работы с данными и разрешения |
| [SECURITY](docs/ru/SECURITY.md) | Сообщение об уязвимостях |
| [Fastlane metadata](fastlane/metadata/android/) | Описание приложения |

**Не забывайте, что это неофициальный клиент**, который работает поверх реверс-инжиниренного API RuStore (у которого нет публичной документации). Фактически, это означает, что он может перестать работать при смене API.

---

## Лицензия

GPL-3.0-or-later. См. [LICENSE](LICENSE).

Логика сетевого слоя опирается на анализ [Obtainium](https://github.com/ImranR98/Obtainium) (GPL-3.0), файл `lib/app_sources/rustore.dart`.
