---
name: roasting-app-domain
description: Implements and extends the roasting app domain model: roast profiles, events (charge, first crack, drop), metrics (RoR, development time). Use when adding or changing profile data structures, event types, or roast calculations.
---

# Roasting App Domain

## Core types

- **RoastProfile**: time-ordered series of (timestamp, bean temp, env temp optional). Plus list of **RoastEvent** (charge, first crack, drop, custom).
- **RoastEvent**: type enum, time (seconds or timestamp), optional value (e.g. temperature at event).
- **Metrics**: computed from profile + events: development time (first crack → drop), RoR at a time (derivative or smoothed), time to first crack, etc.

## Rules

- Domain has no dependency on JavaFX, IO, or hardware. Pure Kotlin (or Java) data and functions.
- Prefer immutable data: `data class`, `val`; copy when updating.
- Keep units explicit (e.g. °C vs °F) in one place; convert at boundaries (UI or config).
- RoR: use a small time window (e.g. 30–60 s) and optionally smoothing; match Artisan/Cropster semantics if reusing formulas.

## When adding events or metrics

1. Add the event type or metric name to the domain model.
2. Add calculation in a dedicated service or top-level functions (e.g. `computeRoR(profile, time)`).
3. Add unit tests with a minimal profile fixture (few points + events).

## Reference

- For Artisan-style formulas (e.g. RoR, development ratio), see Artisan source in `artisanlib/` (phases, statistics) if available. Keep domain agnostic of Artisan; only reuse math.
