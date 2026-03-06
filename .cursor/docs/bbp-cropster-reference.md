# Between Batch Protocol (BBP) — Cropster reference and RDR alignment

This document collects Cropster BBP behavior from [Cropster Help](https://help.cropster.com/en_US/using-roasting-intelligence/what-is-the-between-batch-protocol-bbp) and [Viewing BBP during roasting](https://help.cropster.com/en_US/using-roasting-intelligence/viewing-the-between-batch-protocol-bbp-data-during-roasting-in-roasting-intelligence-ri), plus decompiled RI sources in `1_extracted`, so RDR can mirror it.

---

## 1. When BBP turns on

- **Trigger**: BBP starts **from the moment you stop the roast** (Stop button).
- **Precondition**: A **roasting session must be completed first** for BBP to become visible.
- **Preference**: BBP curves and timer are shown only if the user has enabled **"Display 'Between Batch' curves and timer during pre-and post-roast"** in **Preferences → Roasting → Interface** (Cropster: `InterfacePreferencesPage`, `betweenBatchProtocolCheckbox`; store: `PREF_BETWEEN_BATCH_PROTOCOL` = `"betweenBatchProtocol"`).
- **Profile**: In Cropster, the BBP window **only displays when a Profile is selected**. If the Profile is deselected, the BBP window disappears until another Profile is selected.

---

## 2. What windows / UI open

- **Between Batch Timer**: Shown after Stop; counts time from Stop until the next roast is started (or Restart/Stop BBP). Format 00:00, counting up.
- **BBP window/panel**: In Cropster this is a dedicated **BetweenBatchPanel** (BorderPane) that can show a placeholder or a **BetweenBatchChartWrapper** (chart with BT/ET curves for the between-batch period). The panel has:
  - **Restart** (menu item: "Restart recording"): resets timer to 00:00 and erases BBP data.
  - **Stop** (menu item: "Stop recording"): stops BBP recording; data is kept until Start.
  - "More" button with context menu for Restart / Stop.
  - Hints: e.g. "Since only up to 15 minutes of between batch data can be saved..."
- **Main roast chart**: When viewing a roast that has an attached BBP, the main curve chart can show a **Between Batch annotation** — a shaded band (gray, Cropster colors 196,196,196 and 226,231,233) over the time range of the BBP (e.g. from -duration to 0 before roast start).

In RDR the same behavior is achieved by: (1) showing the BBP timer in the same elapsed-time label when state is BBP; (2) reusing the main chart in "BBP mode" (X axis 0–15 min, BT/ET from `BetweenBatchSession`); (3) Restart and Stop BBP buttons below the chart; (4) on the roast chart, a BBP band annotation when the profile has `betweenBatchLog`.

---

## 3. What happens during BBP

- **Timer**: Starts at Stop, continues until the next roast is **started** (or Restart/Stop BBP).
- **Recording**: Temperature data (BT, ET) is recorded and displayed in real time in the BBP window (same data source as roasting).
- **Restart**: Clicking Restart resets the Between Batch Timer to 00:00. **All data collected during BBP is erased** when Restart is confirmed.
- **Stop BBP (Stop recording)**: Stops recording; existing BBP data is kept until the next **Start** (new roast). At Start, that BBP log is attached to the **next** roast (the one that just started).
- **15-minute limit**: Only up to 15 minutes of between-batch data can be saved. Cropster persists BBP only if duration ≤ 900000 ms (15 min); longer logs are discarded at save.
- **After saving the roast**: The recorded BBP continues to display in the BBP window on the pre-roast screen for **up to 15 minutes**. After that, the user must click **Restart** to reset the timer; otherwise BBP won’t show on the next roast.
- **Abort behavior**:
  - If the user **aborts a roast before it is recognized as started** (Stop button visible instead of Force start): RI keeps tracking the **previous** roast’s BBP until another roast is actually started.
  - If the user **aborts after the roast has started** (auto-start or Force start): RI **restarts** BBP recording from the abort moment; timer and BBP window reset to 00:00.

---

## 4. Comments on BBP (Cropster)

- In the BBP window the user can **click anywhere on the chart** to open a comment box.
- They can enter **Gas/Airflow** (numeric) and a **personal note**; "Text comment" submits.
- After submitting, a **Comments** button appears (top-left of BBP window) to open the BBP comments interface.
- Only numerical Gas/Airflow values are stored so a graph can be generated in C-sar.

RDR does not yet implement BBP comments; the data model and chart support only temperature curves.

---

## 5. Reference BBP (Cropster)

- If the selected **Profile** has a **Reference curve** with a recorded BBP, that reference BBP curve is displayed **in the background** of the BBP window.

RDR does not yet have reference profiles; this is a future alignment.

---

## 6. Cropster source paths (1_extracted)

| What | Path |
|------|------|
| BBP model (live) | `roastingintelligence-5.10\org\cropster\roastingintelligence\BetweenBatchModel.java` |
| BBP log (persisted) | `roastingintelligence-5.10\org\cropster\roastingintelligence\model\BetweenBatchLog.java` |
| Unsaved BBP log | `roastingintelligence-5.10\org\cropster\roastingintelligence\model\UnsavedBetweenBatchLog.java` |
| BBP panel UI | `roastingintelligence-5.10\com\cropster\roastingintelligence\ui\layout\BetweenBatchPanel.java` |
| BBP chart wrapper | `roastingintelligence-5.10\com\cropster\roastingintelligence\ui\chart\BetweenBatchChartWrapper.java` |
| BBP annotation (band on chart) | `roastingintelligence-5.10\org\cropster\roastingintelligence\ui\chart\BetweenBatchAnnotation.java` |
| BBP preference | `PreferencesStore.java` (`PREF_BETWEEN_BATCH_PROTOCOL`), `InterfacePreferencesPage.java` |
| BBP in RoastingManager | `RoastingManager.java` (`switchBetweenBatchLog`, `updateBetweenBatchLog`) |
| BBP persistence/upload | `tasks\RoastUploader.java`, `tasks\DataLoader.java` |
| CurveChart setBetweenBatchAnnotation | `CurveChart.java` (`setBetweenBatchAnnotation(long start, long end)`) |

---

## 7. RDR alignment checklist

- [x] BBP starts when roast is stopped (if preference enabled).
- [x] BBP timer from Stop until Start (or Restart/Stop BBP).
- [x] Restart clears BBP data and resets timer.
- [x] Stop BBP keeps data until Start; at Start, BBP log is attached to the next roast (`RoastProfile.betweenBatchLog`).
- [x] 15-minute recording limit; session stops adding samples after 15 min.
- [x] Main chart shows BBP band annotation when profile has `betweenBatchLog`.
- [x] Chart switches to "BBP mode" (0–15 min, BT/ET from session) when in BBP state.
- [ ] Preference label: match Cropster wording (Interface / "Display 'Between Batch' curves and timer during pre-and post-roast") where applicable.
- [ ] Button labels: Cropster uses "Restart recording" and "Stop recording" in menu; RDR can use same for tooltips/accessibility.
- [ ] BBP comments on graph: not implemented in RDR (optional).
- [ ] 15-minute post-save display rule: optional; Cropster keeps BBP visible 15 min after save then requires Restart.
- [ ] Abort-before-start vs abort-after-start: RDR currently does not distinguish; optional to align.
