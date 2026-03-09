# Мастер-промт: исправление цикла обжарки RDR

## Контекст

Я протестировал полный цикл обжарки в симуляторе и сравнил с Cropster RI5 и Artisan. Ниже — **конкретные задачи** с указанием файлов, методов и ожидаемого поведения. Код не надо переписывать с нуля — в проекте уже есть заготовки (auto-detection код, настройки autoMark), которые нужно **довести и подключить**.

---

## Задача 1. Исправить `computePhases()` — Development = 0 и некорректное поведение без событий

**Файл:** `src/main/kotlin/com/rdr/roast/domain/metrics/Phases.kt`

**Текущая проблема:**
- `Development` всегда `PhaseDuration("Development", 0.0, 0.0)` — MVP-заглушка.
- Если нет CC event, Drying duration = 0, а **весь** интервал попадает в Maillard (maillardStart = `cc?.timeSec ?: 0.0`). На графике это рисует «Maillard 100%» сразу после Charge, что неверно.

**Что сделать:**
1. **Development = FC → DROP** (или FC → endTime если нет DROP). Если нет FC — development = 0.
2. **Maillard = CC → FC** (не CC → DROP). Если нет FC — Maillard = CC → endTime. Если нет CC — Maillard = 0.
3. **Drying = Charge → CC**. Если нет CC — Drying = Charge → endTime (вся обжарка = Drying, пока CC не отмечен). Если нет Charge — drying = 0.
4. **Не показывать фазу если её duration ≤ 0.**

**Ожидаемая логика (как в Cropster):**
```
val chargeTime = charge?.timeSec ?: return emptyList()
val endTime = drop?.timeSec ?: profile.timex.lastOrNull() ?: return emptyList()
val ccTime = cc?.timeSec
val fcTime = fc?.timeSec

Drying:      chargeTime → (ccTime ?: fcTime ?: endTime)
Maillard:    ccTime → (fcTime ?: endTime)          // только если ccTime != null
Development: fcTime → endTime                       // только если fcTime != null
```

**Тесты:** обновить `src/test/kotlin/com/rdr/roast/domain/metrics/PhasesTest.kt` — добавить кейсы:
- Только Charge + CC → Drying видна, Maillard растёт до текущего времени
- Charge + CC + FC → все три фазы
- Charge + CC + FC + DROP → все три с процентами
- Только Charge (без CC/FC/DROP) → Drying растёт, Maillard и Development = 0

---

## Задача 2. Добавить `EventType.SC` (Second Crack)

**Файл:** `src/main/kotlin/com/rdr/roast/domain/Model.kt`

**Что сделать:**
1. Добавить `SC` в `enum class EventType` (после FC, до DROP).
2. В `ChartEventPopup.kt` (`buildRoastContent`) — добавить кнопку «Second crack» между FC и комментарием. Обработка аналогична FC: `onAdd(ChartPopupEventResult(timeMs, "SC @ $mmss"))`.
3. В `MainController.kt` (`chartPanel.onPopupResult`) — добавить обработку `result.label.startsWith("SC @")`:
   ```kotlin
   result.label.startsWith("SC @") -> {
       runEventCommand("SC_START")
       recorder.markEventAt(timeSec, EventType.SC)
       curveChart.addEventMarker(result.timeMs, formatMarkerLabel(result.label, btAtTime))
   }
   ```
4. В `ProfileStorage.kt` — добавить SC в сериализацию/десериализацию timeindex (позиция 5 в Artisan: `[CHARGE, TP, DE, FC, SCs, SCe, DROP, COOL]`).
5. В `buildReferenceComments()` и `buildRoastCommentEntries()` в `MainController.kt` — добавить `EventType.SC -> "Second crack"`.
6. В `FinishRoastController.kt` — если нужно, показать SC в summary.

---

## Задача 3. Горячие клавиши Ctrl+1..5 для событий (как в Cropster)

**Файл:** `src/main/kotlin/com/rdr/roast/ui/MainController.kt`, метод `setupSpaceHotkey()`

**Текущее состояние:** есть C (Charge), D (Drop), Space (Start), Alt+Esc (Abort), F1 (Help). **Нет** хоткеев для DE, FC, SC.

**Что добавить в обработчик `KEY_PRESSED`** (внутри `scene?.addEventHandler`):
```kotlin
// Ctrl+1 = First Crack
e.code == KeyCode.DIGIT1 && e.isShortcutDown -> {
    if (recorder.stateFlow.value == RecorderState.RECORDING) {
        e.consume()
        triggerFirstCrack()
    }
}
// Ctrl+2 = Second Crack
e.code == KeyCode.DIGIT2 && e.isShortcutDown -> { ... triggerSecondCrack() }
// Ctrl+3 = Dry End (Color Change)
e.code == KeyCode.DIGIT3 && e.isShortcutDown -> { ... triggerDryEnd() }
```

**Создать методы** `triggerDryEnd()`, `triggerFirstCrack()`, `triggerSecondCrack()` по аналогии с `triggerCharge()` / `triggerDrop()`:
```kotlin
private fun triggerDryEnd() {
    val profile = recorder.currentProfile.value
    if (profile.eventByType(EventType.CC) != null) return // уже есть
    val sample = recorder.currentSample.value ?: return
    runEventCommand("DRY_END")
    recorder.markEvent(EventType.CC)
    curveChart.addEventMarker(
        (sample.timeSec * 1000).toLong(),
        "DE @ ${formatSec(sample.timeSec)} · %.1f °C".format(sample.bt)
    )
    updatePhaseStrip()
    refreshCommentsList()
}
```
Аналогично для FC (EventType.FC, команда "FC_START") и SC (EventType.SC, команда "SC_START").

**Обновить `HotkeysHelpView.kt`** — добавить Ctrl+1, Ctrl+2, Ctrl+3 в список подсказок.

---

## Задача 4. Подключить автодетекцию Charge (`findChargeDropIndex`)

**Файлы:**
- `src/main/kotlin/com/rdr/roast/domain/metrics/ChargeDetection.kt` — уже реализован
- `src/main/kotlin/com/rdr/roast/app/AppSettings.kt` — `autoMarkCharge: Boolean = false` уже есть
- `src/main/kotlin/com/rdr/roast/ui/MainController.kt` — нужно подключить

**Что сделать:**

В `MainController.onSample()` (после текущей авто-TP логики) добавить авто-Charge:
```kotlin
// Auto-Charge: detect BT drop when autoMarkCharge is enabled
val settings = SettingsManager.load()
if (settings.eventButtonsConfig.autoMarkCharge &&
    profile.eventByType(EventType.CHARGE) == null &&
    profile.timex.size > 30) { // минимум 30 сэмплов
    val chargeIdx = findChargeDropIndex(profile)
    if (chargeIdx != null) {
        val chargeSec = profile.timex[chargeIdx]
        val chargeBt = profile.temp1[chargeIdx]
        recorder.markEventAt(chargeSec, EventType.CHARGE)
        val chargeTimeMs = (chargeSec * 1000).toLong()
        curveChart.rebaseAllSeries(chargeTimeMs)
        curveChart.addEventMarker(chargeTimeMs, "Charge @ %.1f °C".format(chargeBt))
        refreshCommentsList()
    }
}
```

**Важно:** вызывать `findChargeDropIndex` не на каждом сэмпле (дорого), а раз в N сэмплов (например, каждые 5 сэмплов). Добавить счётчик `autoChargeCheckCounter` и проверять `if (autoChargeCheckCounter++ % 5 == 0)`.

---

## Задача 5. Авто-пометка CC/FC по настраиваемой температуре

**Файл:** `src/main/kotlin/com/rdr/roast/app/AppSettings.kt`

**Что добавить в `EventButtonsDialogConfig`:**
```kotlin
val autoMarkDryEndTemp: Double? = null,    // BT в °C для автометки DE, например 160.0
val autoMarkFirstCrackTemp: Double? = null // BT в °C для автометки FC, например 195.0
```

**Файл:** `src/main/kotlin/com/rdr/roast/ui/MainController.kt`, метод `onSample()`

В `onSample()` после авто-Charge и авто-TP, добавить:
```kotlin
// Auto Dry End by temperature
if (settings.eventButtonsConfig.autoMarkDryEnd &&
    settings.eventButtonsConfig.autoMarkDryEndTemp != null &&
    profile.eventByType(EventType.CHARGE) != null &&
    profile.eventByType(EventType.CC) == null &&
    sample.bt >= settings.eventButtonsConfig.autoMarkDryEndTemp!!) {
    triggerDryEnd()
}

// Auto First Crack by temperature
if (settings.eventButtonsConfig.autoMarkFirstCrack &&
    settings.eventButtonsConfig.autoMarkFirstCrackTemp != null &&
    profile.eventByType(EventType.CC) != null && // DE должен быть раньше
    profile.eventByType(EventType.FC) == null &&
    sample.bt >= settings.eventButtonsConfig.autoMarkFirstCrackTemp!!) {
    triggerFirstCrack()
}
```

**UI для настроек:** В `EventButtonsController.kt` рядом с чекбоксами `chkAutoDryEnd` / `chkAutoFirstCrack` добавить поля ввода температуры (TextField). Сохранять/загружать из `EventButtonsDialogConfig`.

---

## Задача 6. Три уровня сглаживания RoR (Sensitive / Recommended / Noise Resistant)

**Файлы:**
- `src/main/kotlin/com/rdr/roast/app/AppSettings.kt` — добавить `enum class RorSmoothing { SENSITIVE, RECOMMENDED, NOISE_RESISTANT }`
- `src/main/kotlin/com/rdr/roast/ui/MainController.kt` — пересоздавать CurveModel pipeline с нужными параметрами
- `src/main/kotlin/com/rdr/roast/ui/SettingsController.kt` — UI выбора

**Параметры (из Cropster `Smoothing.java`):**

| Уровень | MovingAverage windowSize | RoR windowMs |
|---------|------------------------|-------------|
| SENSITIVE | 5 | 10_000 |
| RECOMMENDED (default) | 10 | 18_000 |
| NOISE_RESISTANT | 10 | 30_000 |

**Текущие значения:** `MovingAverageCurveModel(btRaw, windowSize = 5)` и `RorCurveModel(btSmooth, windowMs = 30_000L)` — это примерно NOISE_RESISTANT.

**Что сделать:**
1. Добавить `rorSmoothing: RorSmoothing = RorSmoothing.RECOMMENDED` в `AppSettings`.
2. В `MainController` — при создании pipeline и при смене настроек пересоздавать `btSmooth`, `etSmooth`, `rorBt`, `rorEt` с параметрами из таблицы.
3. В настройках (SettingsController, вкладка Chart) — добавить ComboBox для выбора уровня сглаживания.

---

## Задача 7. Phase strip: не показывать при отсутствии Charge

**Файл:** `src/main/kotlin/com/rdr/roast/ui/MainController.kt`, метод `updatePhaseStrip()`

**Текущая проблема:** `updatePhaseStrip()` уже проверяет `if (profile.eventByType(EventType.CHARGE) == null) return`. Но `redrawLivePhaseStripFromState` в `CurveChartFx.kt` рисует Drying от 0 до deMs (или до endMs), даже если DE = null — в этом случае `dryingEnd = fc ?: phaseEnd`, т.е. **вся обжарка = Drying**.

**Исправление в `CurveChartFx.redrawLivePhaseStripFromState()`:**
- Показывать Drying **только если** `de != null || fc != null` (иначе мы не знаем, где кончается Drying).
- Если нет ни одного промежуточного события (ни DE, ни FC) — показывать одну полосу «Elapsed» или не показывать фазы вовсе.

Конкретно, строки 747–755:
```kotlin
// Текущий код:
val dryingEnd = de?.coerceAtLeast(0.0) ?: fc?.coerceAtLeast(0.0) ?: phaseEnd

// Исправить на:
if (de == null && fc == null) {
    // Нет промежуточных событий → не показываем фазы (только elapsed)
    return
}
val dryingEnd = de?.coerceAtLeast(0.0) ?: fc?.coerceAtLeast(0.0) ?: phaseEnd
```

**Также** Maillard (строки 756–765): показывать только если `de != null`:
```kotlin
if (de != null) {
    val maillardStart = de.coerceAtLeast(0.0)
    val maillardEnd = fc?.coerceAtLeast(0.0) ?: phaseEnd
    // ... рисовать
}
```

---

## Задача 8. Симулятор: сброс S-кривой при новом подключении

**Файл:** `src/main/kotlin/com/rdr/roast/driver/simulator/SimulatorSource.kt`

**Проблема:** `sampleFlow()` использует локальную `var t = 0.0` которая начинается с 0 при каждом вызове `sampleFlow()`. Но если предыдущий flow завершился (t > 900), новый flow начинает с 0 снова — это корректно. Однако если Charge нажат поздно (>540с после Start), вся S-кривая уже прошла.

**Минимальное исправление:** ничего менять не нужно. Это поведение корректно — просто юзер должен нажимать C (Charge) в начале обжарки. Но можно добавить предупреждение в UI: если текущий BT > 200°C и Charge ещё не отмечен, показывать hint «Press C to mark Charge».

---

## Задача 9. Автопереподключение при потере связи

**Файл:** `src/main/kotlin/com/rdr/roast/ui/MainController.kt`

**Что сделать:** В `startConnectionStateCollector()` при получении `ConnectionState.Disconnected` (если предыдущее состояние было `Connected`) — запустить retry loop:
```kotlin
ConnectionState.Disconnected -> {
    if (lastConnectionState is ConnectionState.Connected) {
        // Авто-реконнект через 3 секунды, до 10 попыток
        scope.launch {
            repeat(10) { attempt ->
                delay(3000)
                if (recorder.dataSource.connectionState().value is ConnectionState.Connected) return@launch
                try { recorder.connect() } catch (_: Exception) {}
            }
        }
    }
}
```

---

## Задача 10. Зум графика Ctrl+колесо

**Файл:** `src/main/kotlin/com/rdr/roast/ui/chart/ChartPanelFx.kt` или `CurveChartFx.kt`

**Что сделать:** По аналогии с Cropster `ChartUtils.replaceZoomHandler()` — перехватывать scroll event на ChartViewer:
- Если `event.isShortcutDown` (Ctrl/Cmd зажат) + scroll вверх → зум X-оси (уменьшить диапазон)
- Scroll вниз → зум из X-оси (увеличить диапазон)
- Ctrl+R — сброс осей к дефолту

---

## Порядок выполнения

1. **Задача 1** (computePhases) + **Задача 7** (phase strip) — самые критичные, ломают отображение
2. **Задача 3** (хоткеи Ctrl+1,2,3) — ключевой UX для обжарщика
3. **Задача 2** (Second Crack) — расширение модели
4. **Задача 4** (autoCharge) — подключить готовый код
5. **Задача 5** (авто CC/FC по температуре) — новая функциональность
6. **Задача 6** (сглаживание RoR) — настройки
7. **Задача 9** (реконнект) — надёжность
8. **Задача 10** (зум) — удобство

## Проверка

После каждой задачи:
- `./gradlew test` — все тесты проходят
- `./gradlew build -x test` — билд без ошибок
- `DISPLAY=:1 ./gradlew run` — запустить приложение, проверить в симуляторе:
  1. Start → C → TP auto → DE (Ctrl+3) → FC (Ctrl+1) → D — все события на графике
  2. Phase strip: Drying + Maillard + Development с duration и %
  3. Finish panel: время, вес, save .alog
