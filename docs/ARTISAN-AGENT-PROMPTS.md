# Детальные промпты для агентов: перенос настроек Artisan → RDR

Каждый блок ниже — **один промпт для одного агента**. Агент получает только свой блок и выполняет одну задачу по переносу. Репозиторий Artisan: `D:\project\Projects\artisantest-master`. RDR: `D:\project\Projects\RDR-roast.drop.repeat`. Язык RDR: Kotlin, UI JavaFX/FXML.

---

## Агент 1: Ports Configuration — полная сетка Modbus Input 1…10 и PID

**Цель:** Расширить диалог Ports Configuration в RDR так, чтобы поддерживались все параметры вкладки Modbus из Artisan: Input 1…10 (Device, Register, Function, Divider, Mode, Decode) и блок PID (Device, SV/P/I/D регистры, ON/OFF команды).

**Что смотреть в Artisan:**
- `src/artisanlib/ports.py` — построение UI вкладки MODBUS: поля для Input 1…10 (Device, Register, Function, Divider, Mode, Decode), секция PID (Device, SV, P, I, D, Type, SV Factor, pid Factor, ON/OFF команды). Как значения пишутся в `self.aw.modbus.*`.
- `src/artisanlib/modbusport.py` — класс `modbusport`: списки `inputDeviceIds`, `inputRegisters`, `inputCodes`, `inputDivs`, `inputModes`, флаги `inputFloats`, `inputBCDs`, `inputSigned`; поля PID: `PID_device_ID`, `PID_SV_register`, `PID_p_register`, `PID_i_register`, `PID_d_register`, `PID_ON_action`, `PID_OFF_action`. Типы Decode (uint16 и т.д.) и как они мапятся на inputFloats/inputBCDs/inputSigned.

**Что сделать в RDR:**
1. Расширить `MachineConfig` (или отдельную DTO): для каждого из 10 входов — deviceId, register, functionCode, divider (например 0/1/2 → 1, 1/10, 1/100), mode (C/F), decode (uint16/sint16/float32 и т.д.). Для PID: deviceId, svRegister, pRegister, iRegister, dRegister, onCommand, offCommand (строки).
2. Расширить `PortsConfigView.fxml` и `PortsConfigController.kt`: таблица или сетка полей Input 1…10 и секция PID. Загрузка/сохранение из/в `MachineConfig`. Не сохранять в файл из диалога — вызывающий код считывает значения и сам обновляет настройки (паттерн Artisan).
3. Использовать новые поля в `AbstractModbusRoasterSource`/чтении BT-ET: для каналов 0 и 1 брать register/functionCode/divider из конфига входов (вместо единых btRegister/etRegister/divisionFactor, если выбран режим «полная сетка»), либо оставить обратную совместимость с текущими полями btRegister/etRegister/divisionFactor.

**Критерий готовности:** В диалоге Ports Configuration можно задать для Input 1 и Input 2 (и при желании остальных) Device, Register, Function, Divider, Mode, Decode; задать PID Device и регистры SV/P/I/D и строки ON/OFF; эти значения сохраняются в настройках и используются при подключении к Modbus.

---

## Агент 2: Events Config — Modbus-команды для CHARGE, DROP, FC START, COOL END

**Цель:** Перенести из Artisan возможность привязывать к стандартным событиям (CHARGE, DROP, DRY END, FC START, COOL END и т.д.) произвольные Modbus-команды (write, wcoil, sleep) и выполнять их при нажатии кнопки события или при автоматическом срабатывании.

**Что смотреть в Artisan:**
- `src/artisanlib/events.py` — вкладка Config диалога Events: где хранятся строки команд для событий (CHARGE, DROP, FC START, COOL END). Искать по «alarm», «event», «command», «CHARGE», «DROP».
- `src/artisanlib/main.py` — выполнение этих команд при нажатии кнопки или по таймеру: разбор строки на подкоманды по `;`, затем разбор каждой подкоманды `write(1,1008,2)`, `wcoil(1,2005,1)`, `sleep(15)`. Вызовы `self.modbus.writeRegister`, `self.modbus.writeCoil`, `libtime.sleep`. Блоки около 9060–9200 (write/wcoil/wcoils/mwrite/sleep) и поиск по alarmcommand/eventcommand.

**Что сделать в RDR:**
1. Ввести модель: для каждого типа события (CHARGE, DROP, DRY_END, FC_START, COOL_END и т.д.) — опциональная строка команды (например `write(1,1008,2);sleep(15);write(1,1008,5);`). Хранить в `AppSettings` или в `MachineConfig` (например `eventCommands: Map<EventType, String>`).
2. Реализовать парсер строки команды: разбить по `;`, для каждого токена распознать `write(slaveId, register, value)`, `wcoil(slaveId, coil, 0|1)`, `sleep(seconds)`. Вызывать `ModbusConnectionCore.writeSingle` (или writeRegister), writeCoil (если в RDR есть или добавить в core), и `Thread.sleep`/delay для sleep.
3. При нажатии кнопки CHARGE/DROP/FC START/COOL END в UI (или при автоматической отметке события) выполнять соответствующую команду через текущее подключение к ростеру (через `RoastRecorder.dataSource` или отдельный доступ к Modbus).

**Критерий готовности:** В настройках можно задать строку Modbus-команды для CHARGE, DROP, FC START, COOL END; при нажатии соответствующей кнопки в главном окне строка выполняется (write/wcoil/sleep) через подключённый ростер.

---

## Агент 3: Events Buttons — таблица настраиваемых кнопок (Label, Modbus Command, Visibility, Color)

**Цель:** Перенести из Artisan вкладку Events → Buttons: таблица кнопок с колонками Label, Description, Type, Action (Modbus Command), Documentation (строка команды), Visibility, Color, Text Color. При нажатии кнопки в главном окне выполнять привязанную Modbus-команду.

**Что смотреть в Artisan:**
- `src/artisanlib/events.py` — таблица `eventbuttontable`, списки `extraeventslabels`, `extraeventsdescriptions`, `extraeventsactions`, `extraeventsactionstrings`, `extraeventsvisibility`, `extraeventbuttoncolor`, `extraeventbuttontextcolor`. Заполнение таблицы, сохранение в `self.aw.extraevents*`.
- `src/artisanlib/main.py` — отрисовка кнопок из `extraeventslabels`/visibility/color; при клике выбор индекса `ee` и выполнение `self.extraeventsactionstrings[ee]` (тот же разбор write/wcoil/sleep, что в агенте 2). Сохранение в настройках: `extraeventslabels`, `extraeventsactionstrings`, `extraeventsvisibility`, `extraeventbuttoncolor`, `extraeventbuttontextcolor`.

**Что сделать в RDR:**
1. Модель данных: список записей «кнопка» — label, description, type, action (например «Modbus Command»), commandString, visibility (bool), backgroundColor, textColor. Хранить в `AppSettings` (например `customButtons: List<CustomButtonConfig>`).
2. UI: диалог или вкладка «Events → Buttons» (или аналог): таблица с колонками Label, Description, Action, Command, Visibility, Color. Добавление/удаление/редактирование строк. Сохранение при OK в модель; диалог не пишет в файл сам — вызывающий код считывает и сохраняет.
3. Главное окно: панель динамических кнопок, построенная из `customButtons` (с учётом visibility). По нажатию — выполнение commandString через общий парсер write/wcoil/sleep (как в агенте 2).

**Критерий готовности:** Пользователь может добавить произвольное число кнопок с подписью, командой Modbus и цветом; кнопки отображаются в главном окне и по нажатию выполняют заданную команду.

---

## Агент 4: Events Sliders — конфигурируемые команды, Min/Max, Factor, Offset, Step

**Цель:** Сделать слайдеры управления (Burner, Air, Drum и при необходимости Damper) настраиваемыми как в Artisan: для каждого слайдера задаются Modbus-команда (например `write(1,3904,0)` с подстановкой значения), Min, Max, Factor, Offset, Step. Значение, отправляемое в регистр, вычисляется по формуле с factor и offset.

**Что смотреть в Artisan:**
- `src/artisanlib/events.py` — вкладка Sliders: привязка полей к событиям Air, Drum, Damper, Burner (command, min, max, factor, offset, step).
- `src/artisanlib/atypes.py` — структура настроек слайдеров: команды, factors, offsets, min, max, coarse (step).
- `src/artisanlib/main.py` — при изменении слайдера: получение значения, применение factor/offset, подстановка в шаблон команды (например `write(1,1003,0)` → значение вместо 0), вызов modbus.writeRegister. Поиск по `eventslidervalues`, `eventsliderfactors`, `eventslidermin`, `eventslidermax`.

**Что сделать в RDR:**
1. Расширить конфиг слайдеров (например в `SliderStepConfig` или отдельная структура): для каждого канала (Air, Drum, Burner) — register (или полная строка-шаблон `write(1,reg,VAL)`), min, max, factor, offset, step. Сохранять в `AppSettings`.
2. В UI слайдера: при изменении значения вычислять `modbusValue = (sliderValue - offset) * factor` (или по формуле Artisan) и отправлять в заданный регистр через `ModbusConnectionCore.writeSingle` или аналог. Учесть диапазон min/max и step.
3. Загрузка конфига слайдеров при старте и при смене машины/пресета; отображение слайдеров только для каналов с заданной командой/регистром.

**Критерий готовности:** Слайдеры Burner/Air/Drum настраиваются (регистр, min, max, factor, offset, step); при движении слайдера в регистр записывается значение по формуле Artisan.

---

## Агент 5: Events Quantifiers (Source, Min, Max, Step для событий)

**Цель:** Перенести вкладку Events → Quantifiers: для каждого «события» (Air, Drum, Damper, Burner) задаются Source (ET/BT), SV, Min, Max, Step, Action. Это задаёт источник и диапазон значений для квантователей/слайдеров.

**Что смотреть в Artisan:**
- `src/artisanlib/events.py` — вкладка Quantifiers, поля Source, SV, Min, Max, Step, Action для каждого события.
- `src/artisanlib/atypes.py` — quantifier sources, min, max, coarse, action flags (поля в кортеже настроек).

**Что сделать в RDR:**
1. Модель: для каждого «события» слайдера — source (ET/BT/None), sv (set value), min, max, step, actionEnabled. Хранить рядом с конфигом слайдеров в `AppSettings`.
2. UI: секция Quantifiers в диалоге Events (или в настройках слайдеров): таблица/форма по событиям. Сохранение через вызывающий код.
3. Использование: при отображении слайдера или расчёте целевого значения использовать source/min/max/step (например ограничение диапазона или подсказка по ET/BT). Детали логики уточнить по коду Artisan.

**Критерий готовности:** В настройках можно задать для Air/Drum/Damper/Burner источник (ET/BT), Min, Max, Step; эти значения сохраняются и при необходимости используются в логике слайдеров/событий.

---

## Агент 6: Device Assignment — минимальная часть (ET/BT источник и связка с Modbus)

**Цель:** Реализовать в RDR аналог вкладки Device Assignment → ET/BT в минимальном виде: выбор источника данных ET/BT (Meter = MODBUS) и при необходимости PID/Curves/LCDs, без полного диалога всех вкладок (Extra, Phidgets, Yoctopuce, Ambient, Networks). Задача — убедиться, что в RDR явно задаётся «источник ET/BT = Modbus» и параметры Modbus (host, port, регистры) согласованы с этим.

**Что смотреть в Artisan:**
- `src/artisanlib/devices.py` — вкладка ET/BT: Meter (MODBUS), PID (Control ET, Read BT, RS485 Unit ID), Arduino TC4, External Program; панели Curves, LCDs.
- `src/artisanlib/comm.py` — как при опросе выбирается источник ET/BT (modbus vs serial vs TC4) и как читаются каналы по `modbus.inputDeviceIds`, `inputRegisters` для каналов 0 и 1.

**Что сделать в RDR:**
1. В настройках подключения явно хранить «источник ET/BT»: Modbus (уже по сути так при выборе Besca), при необходимости позже — Simulator, Phidget, External. Сейчас достаточно задокументировать соответствие: Besca TCP/Serial = Modbus, Diedrich Phidget = Phidget.
2. При необходимости добавить в UI одну секцию «Device Assignment → ET/BT»: выбор Meter = MODBUS и отображение текущих параметров Modbus (Host, Port, Slave ID, регистры BT/ET из MachineConfig). Без отдельного диалога можно ограничиться подписью на вкладке «Подключение»: «ET/BT source: Modbus (Host, Port, Registers from below)».

**Критерий готовности:** Понятно и при необходимости отражено в UI, что источник ET/BT для выбранной машины — Modbus (или иной), и его параметры берутся из Ports/MachineConfig.

---

## Агент 7: Axes — Time Axis, Temperature Axis, Grid, Δ Axis

**Цель:** Расширить настройки графика в RDR по образцу диалога Axes в Artisan: Time Axis (Auto, Lock, Min, Max, Step, RECORD Min/Max), Temperature Axis (Min, Max, Step), Legend Location, Grid (Style, Width, Time/Temp, Opaqueness), Δ Axis (Auto, Δ ET, Δ BT, Min, Max, Step).

**Что смотреть в Artisan:**
- `src/artisanlib/axis.py` — диалог «Axes», группы Time Axis, Temperature Axis, Legend, Grid, Δ Axis. Какие поля куда сохраняются (qmc или глобальные настройки).
- `src/artisanlib/canvas.py` — использование этих настроек при отрисовке осей, сетки и легенды.

**Что сделать в RDR:**
1. Расширить `ChartConfig` (или аналог): timeAxisAuto, timeAxisLock, timeAxisMin, timeAxisMax, timeAxisStep, recordMin, recordMax; tempAxisMin, tempAxisMax, tempAxisStep; legendLocation; gridStyle (dotted/solid), gridWidth, gridTime, gridTemp, gridOpaqueness; deltaAxisAuto, deltaET, deltaBT, deltaMin, deltaMax, deltaStep.
2. UI: диалог или секция «Axes» в настройках графика с перечисленными полями. Сохранение в `AppSettings.chartConfig` (или отдельный блок).
3. Применение в компоненте графика RDR: ограничение осей времени и температуры, стиль сетки, отображение Δ ET / Δ BT по настройкам Δ Axis.

**Критерий готовности:** В настройках можно задать Min/Max/Step для времени и температуры, стиль и видимость сетки, параметры Δ Axis; график их применяет.

---

## Порядок выполнения

Рекомендуемый порядок: **1 → 2 → 3 → 4 → 5** (Ports, затем Events Config, Buttons, Sliders, Quantifiers), затем по необходимости **6** (Device Assignment), **7** (Axes). Задачи 2 и 3 используют один и тот же парсер write/wcoil/sleep — его можно реализовать в задаче 2 и переиспользовать в задаче 3.
