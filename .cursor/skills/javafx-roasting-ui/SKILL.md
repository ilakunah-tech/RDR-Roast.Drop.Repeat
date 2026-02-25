---
name: javafx-roasting-ui
description: Builds JavaFX screens and charts for the roasting app: live roast curve, profile list, event markers, settings. Use when creating or modifying UI, charts, or bindings for the roasting desktop app.
---

# JavaFX Roasting UI

## Stack

- JavaFX 21+ (controls, chart, or Canvas)
- ControlSFX for extra controls if needed
- Charts: JFreeChart-FX or JavaFX Canvas for temperature vs time (and RoR). Prefer incremental updates, not full redraw per sample.

## Patterns

- **Controller**: Only calls application services and updates view. No business logic. Use `Platform.runLater` when updating from a background stream.
- **Binding**: Expose `ObservableList` or `Property` from a ViewModel or service; bind chart series and labels so they update on change.
- **Units**: Read °C/°F from app settings; format axis and tooltips accordingly.
- **Events on chart**: Show charge, first crack, drop as vertical lines or markers; use the same time axis as the main curve.

## Screens (MVP)

1. **Main**: Chart + toolbar (Connect, Start/Stop recording, Save). List or table of saved profiles (name, date).
2. **Profile view**: Loaded profile with events and basic metrics (development time, RoR at key points). Export to CSV option.
3. **Settings**: Data source selection (e.g. MODBUS, Serial), port/address, units, save path.

## Threading

- All UI updates on JavaFX thread. Device/Flow callbacks must switch to FX before touching nodes or observable state.
