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
