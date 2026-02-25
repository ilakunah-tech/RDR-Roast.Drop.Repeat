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

Single-module Kotlin/JavaFX desktop app (`rdr-roast`) — coffee roasting logger with live chart, simulator, and hardware drivers. No external services, databases, or Docker required.

### Prerequisites

- **JDK 21** (toolchain enforced in `build.gradle.kts`). Pre-installed on cloud VMs.
- **Gradle 8.12** via wrapper. The `gradlew` script must exist; if missing, regenerate: `java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain wrapper`.

### Key commands

| Task | Command |
|------|---------|
| Build | `./gradlew build` |
| Tests (10 unit tests) | `./gradlew test` |
| Check / lint | `./gradlew check` |
| Run app (needs X display) | `DISPLAY=:1 ./gradlew run` |

### Gotchas

- The repo's `gradlew` Unix script was originally missing (only `gradlew.bat` was committed). If it disappears again, regenerate it using the command above.
- JavaFX requires a display. Cloud VMs have Xvfb on `:1` — always set `DISPLAY=:1` when running the app.
- The app defaults to the **Simulator** data source, so no real hardware is needed for development or testing.
- Profiles are saved to `~/roasts/` and settings to `~/.rdr/settings.json` on the local filesystem.
