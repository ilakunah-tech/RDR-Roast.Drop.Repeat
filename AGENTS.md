# Roasting App — Cursor Agents

This project uses a multi-agent orchestration system for feature delivery, bug fixing, and reference-based implementation.

## Orchestration workflow

For structured work, use the **orchestration workflow**:

1. **Planner** — Breaks task into steps, lists files, risks, acceptance criteria
2. **Reference Researcher** — Inspects Cropster (`C:\Users\ilaku\Downloads\1_extracted`) and Artisan (`D:\project\Projects\artisantest-master`), follows `reference-cropster.mdc`
3. **Implementer** — Implements after planning and reference research
4. **Reviewer** — Checks plan compliance, reference alignment, correctness
5. **Tester** — Runs compile/build/test and reports results

The **Orchestrator** coordinates the flow and never writes production code.

## How to trigger

- **Slash command**: Type `/orchestrate` in chat, then describe your task
- **Rule**: Reference `@orchestration` or `@orchestration.mdc`
- **Phrase**: "Run the orchestration workflow for [task]"

## Agent roles

| Agent | Purpose |
|-------|---------|
| **orchestrator** | Coordinates workflow; ensures order; handles review/test failures; produces final summary |
| **planner** | Restates task; 3–7 steps; files; risks; acceptance criteria. No code. |
| **reference-researcher** | Inspects local references; follows `reference-cropster.mdc`; returns mirror/adapt/do-not-copy |
| **implementer** | Implements after Planner + Reference Researcher output; scoped changes |
| **reviewer** | Plan compliance; reference alignment; correctness; edge cases; consistency |
| **tester** | Compile/build/test; factual report; no production code changes unless asked |

## Domain specialists (direct invocation)

For focused work without full orchestration:

| Task | Agent |
|------|-------|
| Roast profile, events, metrics (RoR, development time) | **domain-model** |
| JavaFX UI, chart, bindings, live roast curve | **javafx-ui** |
| MODBUS, S7, Serial, Phidget, Yoctopuce drivers | **hardware-drivers** |

Invoke directly: e.g. "Use **domain-model** to add time-to-first-crack metric."

## Rules and skills

- **Rules**: `.cursor/rules/` — project-stack, kotlin-java, javafx-ui, hardware-drivers, tests, **reference-cropster** (always for reference research), **orchestration** (workflow)
- **Skills**: `.cursor/skills/` — roasting-app-domain, javafx-roasting-ui, hardware-integration, gradle-packaging

## Cursor Cloud specific instructions

- **Build**: `./gradlew build -x test`
- **Tests**: `./gradlew test`
- **Run**: `DISPLAY=:1 ./gradlew run` (Xvfb on headless Linux)
- App defaults to Simulator; no physical roaster needed for basic testing.
- **Xvfb**: Must be running before `./gradlew run` or GUI tests. Start with `Xvfb :1 -screen 0 1280x1024x24 -ac &` then set `DISPLAY=:1`.
- **CSS warnings**: JavaFX 23 emits `ClassCastException` warnings about `-fx-border-radius` / `-fx-background-radius` from `appearance.css`. These are cosmetic and do not affect functionality.
- **No external services**: No database, Docker, or API server required. The app is fully self-contained with the Simulator data source.
- **Java 21**: The Gradle toolchain requires JDK 21 (pre-installed in the Cloud VM).
