# Сверка BBP: декомпилированный Cropster vs план и реализация в RDR

## 1. Что есть в Cropster (по декомпилированному коду)

### 1.1 BetweenBatchModel (org.cropster.roastingintelligence.BetweenBatchModel)
- `_startDate`, `_time`, `_offset`, `_stopped`
- `_channels: Map<String, Channel>`, `_curves: Map<String, CurveModel>` (по имени канала, включая beanTemperature как primary)
- `_controlStates: Map<ControlState, Boolean>` — газ/вентиляция и т.д.
- **put(Channel, Date, value)** — пишет в CurveModel по каналу; если `isStopped()` — return; обновляет `_time`
- **getDuration()** = `_time - _startDate.getTime()`
- **reset()**: `_stopped = false`, `_startDate = new Date(MathUtil.floorLong(NanoTime.currentTimeMillis() + 1000L, 1000L))` (т.е. начало через 1 с округлённое до секунды), очистка comments, очистка всех curves, сброс controlStates
- **isStopped() / setStopped(boolean)**
- Наследует **AbstractCommentable** — есть comments (RoastingComment)

### 1.2 BetweenBatchLog (model/BetweenBatchLog)
- `startDate`, `duration`, `previousBatch`, `nextBatch` (Long — ID партий)
- `lowestTemperatureTime`, `highestTemperatureTime` (Long)
- `curves: Map<String, Curve>` — по имени канала
- `comments: Set<RoastingComment>`
- **isEmpty()** = duration == null || duration <= 0

### 1.3 UnsavedBetweenBatchLog (model/UnsavedBetweenBatchLog)
- Наследует BetweenBatchLog
- `previousBatchDate`, `nextBatchDate` (Date)
- **setNextBatchDate(nextBatchDate)** — обновляет duration = nextBatchDate - startDate
- **normalizeCurves()** — переводит ключи кривых в относительное время (time - firstKey), чтобы кривые начинались с 0

### 1.4 RoastingManager
- **stopRoasting()** → в конце вызывает **fireRoastStopped(roastingModel)**.
- **finalizeRoasting()** → в конце вызывает **switchBetweenBatchLog(appModel, roastingModel)**:
  - Текущий `appModel.getBetweenBatchLog()` (UnsavedBetweenBatchLog): `setNextBatchDate(roastingModel.getEffectiveStartDate())`, `normalizeCurves()`, если не empty и **duration <= 900000** (15 мин) — `saveBetweenBatchLog(currentBetweenBatchLog)`.
  - Затем создаётся **новый** UnsavedBetweenBatchLog: `previousBatchDate = roastingModel.getEffectiveStartDate()`, **startDate = effectiveEndDate.getTime() + 1000L** (т.е. +1 сек после конца жарки), вешается на appModel.
- **updateBetweenBatchLog(betweenBatchLog, betweenBatchModel)** — копирует startDate из model в log, по каждому channel копирует CurveModel.getValues() в Curve, копирует comments из model в log. Вызывается при финализации (перед saveBetweenBatchLog) извне.

### 1.5 MeasurementHub
- **addModel(MeasurementModel)**, **removeModel(MeasurementModel)** — рассылает сэмплы всем зарегистрированным моделям в мониторах.
- При остановке жарки предполагается: снять RoastingModel с монитора, добавить BetweenBatchModel — те же датчики продолжают слать данные в BBP-модель (описано в плане; в коде AppModel хранит BetweenBatchModel, вызов addModel/removeModel, вероятно, в UI/контроллере при roastingStopped).

### 1.6 Персистенция
- **BetweenBatchLogFilters**: `FILE_PATTERN = "betweenbatchlog-\\d{8}_\\d{6}_\\d{3}\\.json"` — отдельный JSON-файл на каждый BBP.
- **FilePersister.saveBetweenBatchLog(UnsavedBetweenBatchLog)** — пишет в файл с именем по nextBatchDate (createBetweenBatchLogFileName).
- **JsonWriter.writeBetweenBatchLog**, **JsonReader.readBetweenBatchLog** — сериализация Curve через CurveSerializer/CurveDeserializer.

### 1.7 Настройка
- **PreferencesStore**: `PREF_BETWEEN_BATCH_PROTOCOL = "betweenBatchProtocol"`, загрузка `getBoolean(PREF_BETWEEN_BATCH_PROTOCOL, true)` → **setBetweenBatchProtocolEnabled** (включён по умолчанию).

### 1.8 UI
- **BetweenBatchPanel**: placeholder (VBox), BetweenBatchChartWrapper как center; кнопка «More» открывает контекстное меню; Restart/Stop menu items из chartWrapper; dimmedProperty, chartOpacityProperty.
- **BetweenBatchChartWrapper**: кнопка «More» с меню **Restart recording** и **Stop recording**; HintsButton с текстом про лимит **15 minutes** («Since only up to {0} minutes of between batch data can be saved...»).
- **CurveChart.setBetweenBatchAnnotation(long start, long end)** — добавляет BetweenBatchAnnotation на все subplots (полупрозрачный прямоугольник, tooltip «Between batch time»); **removeBetweenBatchAnnotation()**.
- **BetweenBatchAnnotation** — рисует фон x1..x2 и опционально текст.

### 1.8.1 Как выглядит экран во время BBP (по декомпилированному коду)

- **Расположение:** В Cropster левая часть стартового экрана — BorderPane `leftStartPanel`. В его **center** показывается либо **PreparationPanel** (подготовка к жарке), либо **BetweenBatchPanel** (при активном BBP). То есть при BBP **весь центр левой области** занимает панель BBP — это **отдельный экран**, а не тот же график, что при жарке.

- **Содержимое BetweenBatchPanel при BBP:**
  - В центре — **отдельный** CurveChart только для BBP (`new CurveChart(1, null)`), обёрнутый в BetweenBatchChartWrapper. На нём **только** кривые BBP (BT и др. каналы из BetweenBatchModel), время от 0 (конец жарки). Референс предыдущей жарки (Charge, DE, FC, Drop) на этом графике **не показывается**.
  - Если есть сохранённый betweenBatchLog — поверх добавляются кривые из лога (серые), маркеры: «End of roast» (0), «Lowest temperature», «Start of roast» (duration), комментарии.
  - Тулбар графика: кнопка **More** (контекстное меню «Restart recording», «Stop recording»), **Hints** (подсказка про лимит 15 минут).
  - При **dimmed** (например, при открытом диалоге) opacity графика = 0.3. Таймер BBP привязан к `chartOpacityProperty()` — виден вместе с графиком.

- **Placeholder:** Если `setChart(null)` (BBP выключен или нет модели), в панели виден только VBox-placeholder (пустой или с текстом).

- **Итог:** В Cropster во время BBP пользователь видит **отдельный график BBP** без референса жарки, без заголовка «Between Batch Protocol» поверх того же чарта; аннотация BetweenBatchAnnotation используется **на основном графике жарки** при просмотре профиля с BBP (полоса «Between batch time» до нуля), а не на экране BBP.

### 1.9 Прочее
- **RoastUploader**: загружает файлы BBP с диска, storeBetweenBatchLog на сервер, удаляет локальный файл.
- **DataLoader**: подтягивает betweenBatchLogs по referenceIds, маппинг по **nextBatch** (bbl.getNextBatch()).

---

## 2. Соответствие RDR плану и реализации

| Элемент Cropster | В плане RDR | В реализации RDR |
|------------------|-------------|-------------------|
| BetweenBatchModel (живая запись) | BetweenBatchSession | ✅ Bbp.kt: startEpochMs, timex/temp1/temp2 MutableList, stopped, maxDurationSec=15*60, addSample, toLog(), reset(), setStopped() |
| BetweenBatchLog (результат) | BetweenBatchLog | ✅ startEpochMs, durationMs, timex, temp1, temp2, mode, lowestTemperatureTimeMs, highestTemperatureTimeMs; isEmpty, lowestTemperatureIndex(), highestTemperatureIndex() |
| Каналы (Map по имени) | Одна пара BT/ET | ✅ RDR только temp1 (BT), temp2 (ET) — осознанное упрощение |
| ControlState (gas/fan) в BBP | Нет в первой версии | ✅ Не реализовано (план п.9) |
| Comments в BBP | Не в плане | ✅ Не реализовано |
| previousBatch/nextBatch (ID) | Не нужны без сервера | ✅ Привязка через RoastProfile.betweenBatchLog к следующей жарке |
| startDate = effectiveEndDate + 1s | В плане не уточнено | ⚠️ В RDR startEpochMs = System.currentTimeMillis() при stop() (без +1s). Разница некритична; при желании можно добавить +1000 ms. |
| normalizeCurves (время от первого ключа) | Эквивалент — timex в сек от 0 | ✅ В RDR timex уже в секундах от начала BBP (0 = момент Stop) |
| Лимит 15 мин | maxDurationSec = 15*60 | ✅ addSample не добавляет при timeSec >= maxDurationSec; в Cropster при сохранении duration <= 900000 |
| Restart / Stop BBP | restartBbp(), stopBbpRecording() | ✅ RoastRecorder + кнопки в MainController |
| Переключение потребителя сэмплов | Один sampleFlow; в BBP писать в session | ✅ stop() отменяет job, создаёт session, новый job пишет в session.addSample(bbpTimeSec, bt, et) |
| При Start: toLog() → привязать к новому профилю | RoastProfile.betweenBatchLog | ✅ startRecording() из BBP вызывает toLog(), создаёт RoastProfile(..., betweenBatchLog = pendingBbpLog) |
| Сохранение BBP | В .alog (bbp_*) | ✅ ProfileStorage buildAlogDict/parseAlogContent с bbp_start_epoch_ms, bbp_duration_ms, bbp_timex, bbp_temp1, bbp_temp2, опционально lowest/highest |
| Отдельный JSON-файл BBP | Опционально, первый этап — в .alog | ✅ Не делаем; план разрешает только встраивание в .alog |
| PREF_BETWEEN_BATCH_PROTOCOL | betweenBatchProtocolEnabled, default true | ✅ AppSettings + SettingsController/SettingsView (чекбокс) |
| Таймер BBP | recorder.bbpElapsedSec, MM:SS | ✅ combine(stateFlow, elapsedSec, bbpElapsedSec), в BBP показывается bbpElapsedSec |
| Кнопки Start / Restart / Stop BBP | В состоянии BBP | ✅ updateButtonStates(BBP), btnRestartBbp, btnStopBbp, tooltip про 15 min |
| Блок BBP в Finish Roast | Длительность, min/max BT, время | ✅ bbpPanel, lblBbpDuration, lblBbpMinBt, lblBbpMaxBt с учётом mode °C/°F |
| Мини-график BBP в фазе BBP | Опционально, второй этап | ❌ Не реализовано (план п.8) |
| Аннотация BBP на основном графике | Опционально (setBetweenBatchAnnotation) | ❌ Не реализовано (план п.8) |
| reset() при BBP: переход в MONITORING | В плане не явно | ✅ reset() обрабатывает и BBP (отмена job, очистка session, state = MONITORING) — нужно для «Save» из панели завершения |

---

## 3. Выводы

### Учтено и перенесено
- Живая модель записи (Session) с лимитом 15 мин, stopped, reset, setStopped.
- Иммутабельный лог с кривыми и lowest/highest по BT.
- Привязка BBP к **следующей** жарке (RoastProfile.betweenBatchLog).
- Переключение потока сэмплов: после Stop запись только в BBP-сессию; при Start — toLog() и привязка к новому профилю.
- Персистенция в .alog (bbp_*).
- Настройка betweenBatchProtocolEnabled (default true).
- UI: таймер BBP, кнопки Start / Restart / Stop BBP, блок BBP в Finish Roast (длительность, min/max BT).

### Упрощения по плану (осознанные)
- Одна пара кривых (BT, ET) вместо Map по каналам.
- Нет ControlState (gas/fan) в BBP.
- Нет previousBatch/nextBatch ID; привязка только через поле профиля.
- BBP хранится внутри .alog, без отдельного JSON-файла.
- Нет comments в BBP.

### Опционально (не сделано, план разрешает отложить)
- Мини-график кривых BBP в фазе BBP (аналог BetweenBatchPanel + BetweenBatchChartWrapper).
- Аннотация BBP на основном графике при просмотре профиля с BBP (setBetweenBatchAnnotation(start, end)).

### Мелкое расхождение
- **Cropster**: startDate BBP = effectiveEndDate + 1 секунда. **RDR**: startEpochMs = System.currentTimeMillis() в момент stop(). Для полного совпадения можно задавать `startEpochMs = System.currentTimeMillis() + 1000L` при создании BetweenBatchSession в stop() — по желанию.

---

**Итог:** Всё существенное из декомпилированного Cropster BBP учтено в плане и перенесено в RDR. Упрощения и отложенные пункты явно оговорены в плане (п. 9 и п. 8). Дополнительно в RDR учтён сценарий reset() из состояния BBP (после Save в панели завершения).
