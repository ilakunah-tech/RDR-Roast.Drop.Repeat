# Roasting App — Cursor Agents

Этот проект использует специализированных субагентов и правила Cursor для разработки десктопного приложения для обжарки (стек: Kotlin, JavaFX, Cropster-like).

## Когда какого субагента вызывать

| Задача | Субагент |
|--------|----------|
| Модель данных профиля, события (charge, crack, drop), метрики (RoR, development time) | **domain-model** |
| Экран, график, биндинги JavaFX, живая кривая обжарки | **javafx-ui** |
| Подключение к оборудованию: MODBUS, S7, Serial, Phidget, Yoctopuce | **hardware-drivers** |
| Юнит-тесты, моки драйверов, тесты метрик | **testing** |
| Проверка кода на соответствие стеку и слоям | **code-reviewer** |

## Как вызвать

В чате Cursor можно написать, например:

- «Используй субагент **domain-model**, чтобы добавить метрику времени до первого крэка»
- «Привлеки **javafx-ui** для доработки графика»
- «Пусть **hardware-drivers** добавит адаптер для MODBUS RTU»
- «Запусти **code-reviewer** на последних изменениях»

## Правила и скиллы

- **Правила** (всегда или по файлам): `.cursor/rules/` — project-stack, kotlin-java, javafx-ui, hardware-drivers, tests.
- **Скиллы** (как делать домен, UI, железо, сборку): `.cursor/skills/` — roasting-app-domain, javafx-roasting-ui, hardware-integration, gradle-packaging.

Общий стек и архитектура описаны в `.cursor/rules/project-stack.mdc` (alwaysApply).

## Cursor Cloud specific instructions

### Project overview

RDR (Roast.Drop.Repeat) is a single-module Kotlin/JavaFX desktop coffee roasting logger. No database, no Docker — settings live in `~/.rdr/settings.json`, profiles in `~/roasts/*.alog`.

### Build & run

- **Build**: `./gradlew build -x test` (compiles Kotlin, downloads all deps via Gradle).
- **Tests**: `./gradlew test` — 3 JUnit 5 test classes (PhasesTest, ProfileStorageTest, RoRTest).
- **Run app**: `DISPLAY=:1 ./gradlew run` — launches JavaFX UI. Requires a virtual display (Xvfb) on headless Linux; the Cloud VM already provides `DISPLAY=:1`.
- The app defaults to **Simulator** machine type, so no physical roaster or network is needed for basic testing.

### Gotchas

- The repo ships without a `gradlew` unix script (only `gradlew.bat`). The update script regenerates it from the existing `gradle-wrapper.jar` if missing.
- CSS warnings about `ClassCastException` on startup are cosmetic (JavaFX 23 strictness with `appearance.css` size values) and do not affect functionality.
- The external server (`artqqplus.ru`) is optional; the app works fully offline with the simulator. Server-dependent features (login, reference profiles, upload) degrade gracefully without credentials.
- No lint tool (detekt, ktlint) is configured in the Gradle build. Compilation warnings from `./gradlew build` serve as the primary code-quality check.
