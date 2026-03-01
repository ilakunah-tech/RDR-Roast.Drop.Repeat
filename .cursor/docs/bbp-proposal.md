# Предложение: BBP (Between Batch Protocol) в RDR — по образцу Cropster

## Почему Cropster, а не Artisan

- **Cropster**: BBP — это **отдельная фаза записи** после Stop: таймер идёт, **кривые температур (BT, ET) пишутся в реальном времени** между жарками. Видно, как ведёт себя машина до следующей загрузки. Данные — реальные кривые + статистика (min/max temp, time between batches, gas changes и т.д.).
- **Artisan**: BBP — **вычисленные метрики** по отрезку «начало профиля → CHARGE» плюс кэш конца предыдущей жарки; отдельной записи между партиями нет.

В RDR предлагается подход **как в Cropster**: после Stop запускается сессия BBP с записью кривых и таймером.

---

## 1. Концепция BBP в стиле Cropster

- После **Stop** жарки автоматически начинается **BBP-сессия**:
  - **Таймер** считает время от Stop до следующего Start (или до Stop BBP / Restart).
  - **Запись продолжается** с того же источника (датчики BT, ET): сэмплы идут не в roast-профиль, а в **Between Batch Log** — отдельная кривая «меж партиями».
- Ограничения (как в Cropster):
  - Запись BBP не более **15 минут** (после 15 мин — авто-остановка записи или только таймер без записи, на выбор).
  - Кнопка **Restart** — сброс таймера и очистка данных BBP (подготовка к перерыву).
  - Кнопка **Stop BBP** — остановить запись BBP, таймер можно оставить или сбросить (для перерывов в производстве).
- При **Start** следующей жарки:
  - BBP-сессия завершается; накопленный **BetweenBatchLog** привязывается к **следующей** жарке (или к предыдущей как «что было после DROP» — в Cropster привязка previousBatch/nextBatch к ID партий; в RDR можно привязать к следующему профилю).
- **Хранение**: BBP хранится как часть данных следующей жарки (или отдельный файл/блок), чтобы в отчётах и Roast Compare видеть кривую и статистику «между партиями».

---

## 2. Доменная модель (Cropster-стиль)

### 2.1. BetweenBatchLog (данные BBP — результат записи)

Отдельная структура, не часть RoastProfile:

```kotlin
// domain/Bbp.kt

/**
 * Лог межпартийного протокола: кривые и метаданные, записанные между Stop и следующим Start.
 * По образцу Cropster BetweenBatchLog.
 */
data class BetweenBatchLog(
    val startEpochMs: Long,
    val durationMs: Long,
    /** Время в секундах от начала BBP (0 = момент Stop). */
    val timex: List<Double>,
    /** BT по времени. */
    val temp1: List<Double>,
    /** ET по времени. */
    val temp2: List<Double>,
    val mode: TemperatureUnit = TemperatureUnit.CELSIUS
) {
    val isEmpty: Boolean get() = durationMs <= 0 || timex.isEmpty()

    /** Индекс момента минимальной BT (lowest temperature). */
    fun lowestTemperatureIndex(): Int? { ... }

    /** Индекс момента максимальной BT (highest temperature). */
    fun highestTemperatureIndex(): Int? { ... }
}
```

Опционально можно добавить:
- `lowestTemperatureTimeMs: Long?`, `highestTemperatureTimeMs: Long?` — для статистики без пересчёта;
- позже — контроль (gas/fan) по времени, если RDR будет их писать.

### 2.2. Состояние BBP-сессии (живая запись)

Отдельная модель «текущий BBP», аналог Cropster BetweenBatchModel:

```kotlin
/**
 * Текущая сессия BBP: приём сэмплов после Stop, пока не нажали Start (новая жарка) или Restart/Stop BBP.
 */
data class BetweenBatchSession(
    val startEpochMs: Long,
    val timex: MutableList<Double> = mutableListOf(),
    val temp1: MutableList<Double> = mutableListOf(),
    val temp2: MutableList<Double> = mutableListOf(),
    var stopped: Boolean = false,
    val maxDurationSec: Double = 15 * 60.0  // 15 min
) {
    fun addSample(timeSec: Double, bt: Double, et: Double) {
        if (stopped) return
        if (timeSec >= maxDurationSec) return  // не записываем дальше лимита
        timex.add(timeSec)
        temp1.add(bt)
        temp2.add(et)
    }

    fun toLog(): BetweenBatchLog? {
        if (timex.isEmpty()) return null
        val durationMs = ((timex.maxOrNull() ?: 0.0) * 1000).toLong()
        return BetweenBatchLog(
            startEpochMs = startEpochMs,
            durationMs = durationMs,
            timex = timex.toList(),
            temp1 = temp1.toList(),
            temp2 = temp2.toList()
        )
    }
}
```

Здесь `timeSec` — секунды от начала BBP (0 = момент Stop). При сборе сэмплов можно использовать `(System.currentTimeMillis() - startEpochMs) / 1000.0`.

---

## 3. Состояния рекордера и интеграция с RoastRecorder

Текущие состояния: `DISCONNECTED`, `MONITORING`, `RECORDING`, `STOPPED`.

Варианты встраивания BBP:

**Вариант A — новое состояние BBP**

- После `stop()` переходить не в `STOPPED`, а в **`BBP`** (Between Batch Protocol).
- В состоянии `BBP`:
  - Таймер BBP отображается (секунды от Stop).
  - Сэмплы с того же `RoastDataSource` идут в `BetweenBatchSession`, а не в roast-профиль.
  - Кнопки: **Start** (начать новую жарку → закрыть BBP, сохранить BetweenBatchLog, перейти в RECORDING), **Restart** (очистить BBP, сбросить таймер, остаться в BBP), **Stop BBP** (остановить запись BBP, таймер можно оставить; при Start всё равно передать то, что накопилось).
- При нажатии **Start** (новая жарка): из сессии собрать `BetweenBatchLog`, привязать к следующему профилю (см. ниже), очистить сессию, вызвать `startRecording()` как сейчас.

**Вариант B — BBP внутри STOPPED**

- Остаёмся в `STOPPED`, но параллельно держим **опциональную активную BBP-сессию**.
- После Stop сразу запускаем фоновый сбор в `BetweenBatchSession` (тот же `sampleFlow()` подписываем и пишем в сессию, пока не Start / Restart / Stop BBP).
- В UI в состоянии STOPPED показываем таймер BBP и опционально мини-график BBP; кнопки Restart / Stop BBP по желанию.

Рекомендация: **Вариант A** — явное состояние `BBP` и один поток сэмплов (либо в roast, либо в BBP), проще не путать данные.

Итого новые состояния:

```kotlin
enum class RecorderState {
    DISCONNECTED,
    MONITORING,
    RECORDING,
    STOPPED,   // можно убрать, если всегда идём в BBP после Stop
    BBP        // запись между партиями
}
```

Либо оставить STOPPED и ввести только фазу записи: «после Stop мы в STOPPED, но одновременно запускаем BbpRecorder (отдельный поток сэмплов в BetweenBatchSession)». Тогда переход: RECORDING → stop() → STOPPED + старт BbpRecorder; при Start (новая жарка) → stop BbpRecorder, сохранить лог, reset() → startRecording().

Удобнее для минимальных изменений: **после stop() переходим в BBP**, в BBP сэмплы пишем в BetweenBatchSession; при Start сохраняем BBP в «следующий» профиль и переходим в RECORDING.

---

## 4. Привязка BBP к жарке

В Cropster: BetweenBatchLog имеет `previousBatch` и `nextBatch` (ID партий). В RDR пока нет глобальных ID жарок; есть только текущий профиль и сохранение в .alog.

Варианты:

- **При сохранении следующей жарки** в .alog (или на сервер) сохранять и приложенный **BetweenBatchLog** (кривые + startEpochMs, durationMs). В файле можно два блока: roast-профиль и bbp (timex_bbp, temp1_bbp, temp2_bbp, bbp_start_epoch_ms, bbp_duration_ms). Так BBP всегда привязан к **следующей** жарке (той, что началась после этого BBP).
- Либо хранить BBP в отдельном файле с привязкой по времени (startEpochMs следующей жарки), а в профиле жарки — только ссылку или встроенный снимок.

Предлагается: **встраивать BBP в данные следующей жарки** — при сохранении профиля (Save в Finish dialog или авто-сохранение) в .alog писать секцию BBP, если она есть (например, поля `bbp_start_epoch_ms`, `bbp_duration_ms`, `bbp_timex`, `bbp_temp1`, `bbp_temp2`). В RoastProfile тогда опционально: `betweenBatchLog: BetweenBatchLog? = null`.

---

## 5. Поток данных

1. **RECORDING** — сэмплы из `dataSource.sampleFlow()` → `RoastProfile`.
2. **Stop** — вызываем `stop()`: делаем снимок профиля, переходим в **BBP**; создаём `BetweenBatchSession(startEpochMs = now)`, подписываемся на `sampleFlow()` и пишем сэмплы в сессию. Время для BBP: `(currentTimeMillis() - startEpochMs) / 1000.0`.
3. В состоянии **BBP**:
   - Таймер = elapsed от startEpochMs.
   - Если elapsed > 15 min — перестаём добавлять сэмплы (`session.stopped = true` или не вызывать addSample).
   - Restart — очистить сессию, startEpochMs = now.
   - Stop BBP — только остановить запись (session.stopped = true), при Start всё равно отдадим накопленное.
4. **Start** (новая жарка): из сессии строим `BetweenBatchLog`; сохраняем его в «хранилище следующей жарки» (в рекордере держим `pendingBbpForNextRoast: BetweenBatchLog?`); очищаем сессию; вызываем `startRecording()`. При следующем Save профиля этот `pendingBbpForNextRoast` записываем в профиль (RoastProfile.betweenBatchLog) и в .alog.
5. При **Save** профиля: если у текущего профиля есть `betweenBatchLog`, дописывать в .alog секцию BBP.

---

## 6. RoastRecorder и BbpSession

- **RoastRecorder** при переходе в BBP создаёт/обновляет **BetweenBatchSession** и переключает подписку: сэмплы идут в сессию. Либо вынести в отдельный **BbpRecorder**, который получает тот же `RoastDataSource` и при старте подписывается на `sampleFlow()`, при Stop BBP / Restart / Start — отписывается и отдаёт лог.
- Проще всего: в **RoastRecorder** добавить состояние BBP и одну подписку на sampleFlow; в RECORDING пишем в profile, в BBP — в currentBbpSession. При stop() переключаем флаг и создаём сессию; при startRecording() закрываем сессию, сохраняем лог в `lastBetweenBatchLog` (или сразу в следующий профиль — тогда RoastProfile нужно расширить полем `betweenBatchLog: BetweenBatchLog?`, и при startRecording() мы создаём новый профиль и позже, когда пользователь сохранит, мы уже имеем профиль с приложенным BBP от начала этой записи; но BBP относится к периоду *до* этой записи, так что логичнее при Start передать lastBetweenBatchLog в новый профиль как «BBP перед этой жаркой»).

Итого: при **Start** мы создаём новый RoastProfile и к нему сразу прикрепляем только что закрытый `BetweenBatchLog` (поле `RoastProfile.betweenBatchLog`). При сохранении этого профиля в .alog пишем и BBP.

---

## 7. Хранение в .alog

В buildAlogDict добавить (если `profile.betweenBatchLog != null`):

- `'bbp_start_epoch_ms': <long>`
- `'bbp_duration_ms': <long>`
- `'bbp_timex': [ ... ]`
- `'bbp_temp1': [ ... ]`  // BT
- `'bbp_temp2': [ ... ]`  // ET

В parseAlogContent — парсить и собирать BetweenBatchLog, класть в RoastProfile.

Так можно открыть .alog в RDR и видеть кривую BBP; для Artisan совместимости можно оставить только эти ключи или дублировать в формате Artisan bbp_*, если понадобится.

---

## 8. UI

- **После Stop** (состояние BBP):
  - Крупно **таймер BBP** (00:00 → растёт).
  - Опционально **мини-график** BT/ET за период BBP (те же timex, temp1, temp2 из BetweenBatchSession).
  - Кнопки: **Start** (новая жарка), **Restart** (сброс BBP), **Stop BBP** (остановить запись).
- **В диалоге Finish Roast** (после сохранения профиля с BBP): можно показать краткую сводку BBP (длительность, min/max BT, время до следующего CHARGE) и ссылку «Показать кривую BBP» если откроем отдельное окно/вкладку.
- Позже: **Roast Compare** с отображением кривых BBP для выбранных жарок.

---

## 9. Порядок внедрения

1. **domain/Bbp.kt** — типы `BetweenBatchLog`, `BetweenBatchSession` (с addSample, toLog, lowest/highest index).
2. **RoastRecorder**:
   - Добавить состояние `BBP` (или оставить STOPPED и ввести отдельный флаг «идёт запись BBP»).
   - В `stop()`: после снимка профиля переходить в BBP, создавать BetweenBatchSession, переключать сбор сэмплов в сессию (тот же sampleFlow подписан, но пишем в session).
   - В `startRecording()`: если есть активная BbpSession, вызвать toLog(), сохранить в `pendingBetweenBatchLog`; новый RoastProfile создавать с полем `betweenBatchLog = pendingBetweenBatchLog`; очистить сессию и pending.
   - Обработка Restart / Stop BBP (методы recorder или главный контроллер).
3. **RoastProfile** — добавить `betweenBatchLog: BetweenBatchLog? = null`.
4. **ProfileStorage** — запись/чтение bbp_* в .alog.
5. **MainController / UI** — таймер BBP, кнопки Restart / Stop BBP, опционально мини-график BBP в состоянии BBP.
6. **FinishRoastController** — опционально блок «BBP» (длительность, min/max) при наличии betweenBatchLog.

---

## 10. Отличия от Artisan-подхода

| Аспект | Artisan | Cropster (и это предложение для RDR) |
|--------|---------|--------------------------------------|
| Что такое BBP | Метрики по отрезку «начало профиля → CHARGE» + кэш конца прошлой жарки | Отдельная **запись** кривых между Stop и следующим Start |
| Кривые | Нет | Да: timex, BT, ET за период BBP |
| Таймер | Нет | Да: время между партиями |
| Когда считаем | При stop() по текущему профилю | Не «считаем» — пишем сэмплы в реальном времени |
| Лимит | Нет | 15 мин записи, Restart / Stop BBP |
| Хранение | Ключи bbp_* в .alog (числа) | Кривые bbp_timex, bbp_temp1, bbp_temp2 + метаданные |

Так RDR получает реализацию BBP в стиле Cropster: реальные кривые между партиями и таймер, с возможностью Restart и лимитом 15 минут.
