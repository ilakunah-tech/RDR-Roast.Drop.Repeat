---
name: gradle-packaging
description: Configures Gradle (Kotlin DSL), jlink, and jpackage for the roasting desktop app. Use when setting up or changing build, dependencies, or installers (Windows/macOS/Linux).
---

# Gradle and Packaging

## Build

- Gradle with Kotlin DSL. JDK 17+ (21 recommended for JavaFX in JDK).
- Modules: `application`, optional `core` (domain + drivers), or a single module for MVP.
- Dependencies: JavaFX via `org.openjfx` or use JDK 21 bundled JavaFX; ControlSFX; j2mod; moka7; jSerialComm; OkHttp, Retrofit, Jackson; SLF4J, Logback.
- Main class: entry point that starts JavaFX and loads the main view.

## jlink (optional)

- Create a custom runtime image with only required modules: `java.base`, `javafx.controls`, `javafx.fxml`, etc. Use Gradle plugin (e.g. `org.beryx.jlink` or manual `jlink` task) to reduce size.

## jpackage

- Use jpackage (JDK 14+) to produce installers: .msi (Windows), .dmg/.pkg (macOS), .deb/.rpm (Linux). Include the custom runtime from jlink or a full JRE. Application name and vendor from project or env.
- Gradle: call `jpackage` from a task or use a plugin (e.g. `org.beryx.jlink` with `jpackage` task). Output: `build/jpackage/` or `dist/`.

## Run

- `./gradlew run` should start the app with main class and classpath. No edits to system PATH for Java required when using Gradle wrapper.
