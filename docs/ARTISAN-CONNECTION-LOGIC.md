# Логика подключения Artisan → RDR: что, где, как

Документ описывает, **что** настраивается в Artisan для подключения к ростерам и управления ими, **где** это реализовано в коде Artisan (`D:\project\Projects\artisantest-master`), и **как** это соотносится с RDR. Используется для итеративного переноса настроек в RDR.

---

## 1. Ports Configuration (диалог «Ports Configuration»)

**Что на скринах:** вкладки ET/BT, Extra, **Modbus**, S7, WebSocket. В Modbus: Serial (Comm Port, Baud, Byte Size, Parity, Stopbits, Timeout), Input 1…10 (Device, Register, Function, Divider, Mode, Decode), PID (Device, SV/P/I/D, ON/OFF команды), Serial/UDP-TCP таймауты и ретраи, Type (TCP), Host, Port.

**Где в Artisan:**
- **`src/artisanlib/ports.py`** — класс `comportDlg`, заголовок `"Ports Configuration"` (строка ~390). Вкладки: ET/BT, Extra, MODBUS, S7, WebSocket. Построение UI Modbus (поля Serial, Input 1…10, PID, Timeout/Retries, Type/Host/Port).
- **`src/artisanlib/modbusport.py`** — класс `modbusport`: `inputDeviceIds`, `inputRegisters`, `inputCodes`, `inputDivs`, `inputModes`, `host`, `port`, `type`, `comport`, `baudrate`, `bytesize`, `parity`, `stopbits`, `timeout`, `IP_timeout`, `IP_retries`, PID-поля (`PID_device_ID`, `PID_SV_register`, `PID_ON_action`, `PID_OFF_action` и т.д.). Чтение регистров: `comm.py` (вызовы modbus по `inputDeviceIds[i]`, `inputRegisters[i]`, `inputCodes[i]`) и `modbusport.py` (`read_input_registers`, `readSingleRegister` и т.д.).

**Как в RDR:** `PortsConfigView.fxml` + `PortsConfigController.kt` (вкладки Comm Port, MODBUS). `MachineConfig` + `ConnectionRuntimeConfig` + `ModbusConnectionCore`. Часть полей уже перенесена; Input 1…10 (полная сетка), PID, Decode — кандидаты на следующие итерации.

---

## 2. Events — Config (события CHARGE, DROP, FC START и Modbus-команды)

**Что на скринах:** вкладка Config: события RESET, CHARGE, DRY END, FC START, DROP, COOL END с полем «Modbus Command» — строки вида `write(1,1008,2);sleep(15);write(1,1008,5);`, `wcoil(1,2006,1); wcoil(1,2005,1);`, `write(1,1009,2);`, `wcoil(1,2005,0); wcoil(1,2006,0); write(1,1010,5)`.

**Где в Artisan:**
- **`src/artisanlib/events.py`** — диалог Events, вкладки Config, Buttons, Sliders, Quantifiers, Palettes, Style, Annotations. В Config хранятся команды для стандартных событий (CHARGE, DROP и т.д.) — имена переменных типа `alarmcommand`/event command (нужно искать по `alarmcommand`, `alarmbutton`, `qmc` в main.py и events.py).
- **`src/artisanlib/main.py`** — выполнение Modbus-команд при нажатии кнопок/событий: разбор строк `write(…)`, `wcoil(…)`, `sleep(…)` и вызовы `self.modbus.writeRegister`, `self.modbus.writeCoil` и т.д. (блоки около строк 9060–9180 и 11464, 11503, 11532 для extraeventsactionstrings).

**Как в RDR:** Пока нет эквивалента «Events → Config» с произвольными Modbus-командами для CHARGE/DROP/FC/COOL END. В RDR есть жёстко заданные действия при нажатии кнопок; для переноса нужна модель «событие → строка команды» и интерпретатор `write`/`wcoil`/`sleep`.

---

## 3. Events — Buttons (таблица кнопок: Label, Modbus Command, Visibility, Color)

**Что на скринах:** вкладка Buttons: таблица с колонками Label, Description, Type, Value, Action (Modbus Command), Documentation (строка команды), Visibility, Color, Text Color. Примеры: «Burner ON» → `wcoil(1,2003,1)`, «Mixer Open» → `write(1,1010,2)`, «COOL END» → несколько команд через `;`.

**Где в Artisan:**
- **`src/artisanlib/events.py`** — таблица `eventbuttontable`, списки `extraeventslabels`, `extraeventsactionstrings`, `extraeventsvisibility`, `extraeventbuttoncolor`, `extraeventbuttontextcolor`. Загрузка/сохранение в/из `self.aw.extraevents*`.
- **`src/artisanlib/main.py`** — `extraeventslabels`, `extraeventsactionstrings`, при нажатии кнопки выполнение строки из `extraeventsactionstrings[ee]` (разбор `write`/`wcoil`/`sleep` и вызов modbus). Сохранение в настройках: `extraeventsactionstrings`, `extraeventslabels` и т.д. (около 19343, 20975).

**Как в RDR:** Нет таблицы настраиваемых кнопок с произвольной Modbus-командой и цветом. Перенос: модель данных «кнопка: label, command, visibility, color» + UI таблица/список + выполнение через общий интерпретатор команд (как в п.2).

---

## 4. Events — Sliders (Air, Drum, Burner: Command, Min, Max, Factor, Offset, Step)

**Что на скринах:** вкладка Sliders: для каждого «Event» (Air, Drum, Damper, Burner) — Action «Modbus Command», Command `write(1,1003,0)`, `write(1,1001,0)`, `write(1,3904,0)`, Min, Max, Factor, Offset, Bernoulli, Step, Temp Unit.

**Где в Artisan:**
- **`src/artisanlib/events.py`** — вкладка Sliders, привязка к данным слайдеров (команды, min/max/factor/offset).
- **`src/artisanlib/atypes.py`** — типы/списки для событий: `eventslider*`, slider commands, factors, offsets, min, max, coarse (номера полей 9–24 в кортеже настроек).
- **`src/artisanlib/main.py`** — `eventslidervalues`, `eventsliderfactors`, `eventslidermin`, `eventslidermax`, `eventslidercoarse`, сохранение/загрузка. При движении слайдера запись в Modbus по команде с подстановкой значения (с учётом factor/offset).

**Как в RDR:** Есть слайдеры Burner/Air/Drum с фиксированными регистрами в `BescaModbusTcpSource` и `SliderStepConfig`. Нет конфигурируемых команд и формул Factor/Offset. Перенос: конфиг слайдера (register, factor, offset, min, max, step) + использование при записи значения.

---

## 5. Events — Quantifiers (Source, Min, Max, Step для событий)

**Что на скринах:** вкладка Quantifiers: для Air, Drum, Damper, Burner — Source (ET), SV, Min, Max, Step, Action.

**Где в Artisan:**
- **`src/artisanlib/events.py`** — вкладка Quantifiers.
- **`src/artisanlib/atypes.py`** — quantifier sources, min, max, coarse, action flags (поля 14–18, 26–27).

**Как в RDR:** Отдельной модели Quantifiers нет. Можно перенести как часть расширенной модели Events/Sliders (источник значения для слайдера/квантователя).

---

## 6. Device Assignment (ET/BT, Extra Devices, Symb ET/BT, Phidgets, Yoctopuce, Ambient, Networks)

**Что на скринах:** диалог «Device Assignment», вкладки ET/BT (Meter MODBUS, PID Control ET/Read BT, Arduino TC4, External Program; Curves/LCDs), Extra Devices (Device, Color, Label, y100/y200, LCD, Curve, Δ Axis, Fill), Symb ET/BT (ET Y(x), BT Y(x)), Phidgets/Yoctopuce/Ambient/Networks (хосты, порты, параметры устройств).

**Где в Artisan:**
- **`src/artisanlib/devices.py`** — класс `DeviceAssignmentDlg`, заголовок `"Device Assignment"`. Вкладки: ET/BT, Extra Devices, Symb ET/BT, Phidgets, Yoctopuce, Ambient, Networks, Batch Manager. Построение UI и привязка к данным (Meter, PID, TC4, Extra Devices таблица, Networks и т.д.).
- **`src/artisanlib/comm.py`** — использование выбранного источника ET/BT и опрос Modbus (например, по `modbus.inputDeviceIds`, `inputRegisters` для каналов 0/1 как ET/BT).
- **`src/artisanlib/modbusport.py`** — параметры Modbus (host, port, type, inputRegisters и т.д.), используемые при опросе.

**Как в RDR:** Выбор «источника» ET/BT в RDR по сути задаётся типом машины (Besca/Diedrich) и транспортом (TCP/Serial). Отдельного диалога Device Assignment с вкладками ET/BT / Extra / Networks нет. Перенос — по приоритету: сначала Modbus ET/BT и параметры, при необходимости — Extra Devices и Networks как отдельные задачи.

---

## 7. Axes (Time Axis, Temperature Axis, Legend, Grid, Δ Axis)

**Что на скринах:** диалог «Axes»: Time Axis (Auto, Lock, Min, Max, Step, RECORD Min/Max, Expand), Temperature Axis (Min, Max, Step), Legend Location, Grid (Style, Width, Time/Temp, Opaqueness), Δ Axis (Auto, Δ ET, Δ BT, Min, Max, Step).

**Где в Artisan:**
- **`src/artisanlib/axis.py`** — класс диалога, заголовок `"Axes"`. Группы `Time Axis`, `Temperature Axis`, Legend, Grid, `Δ Axis`. Сохранение в `qmc` (или глобальные настройки графика).
- **`src/artisanlib/canvas.py`** — использование этих настроек при отрисовке осей и сетки.

**Как в RDR:** В RDR есть `ChartConfig` (tempMin/tempMax, rorMin/rorMax, showGrid и т.д.). Не все поля Axes перенесены (например, Time Axis Min/Max в формате времени, Legend Location, Grid Style/Width/Opaqueness, Δ Axis отдельно). Перенос — расширение `ChartConfig` и UI под параметры Axes.

---

## 8. Config → Machine (список ростеров)

**Что на скринах:** меню Config → Machine — длинный список производителей/моделей (Aillio, Besca, Diedrich, Loring и т.д.) с подменю.

**Где в Artisan:** Обычно такое меню строится из конфигурационных файлов или словарей с пресетами машин. Искать в `main.py` по «Machine», «Config», «device», «roaster» или по названиям брендов (Besca, Diedrich). Пресеты могут подставлять значения в Ports/Modbus/Events.

**Как в RDR:** Выбор «машины» сейчас — комбинация «Источник» (Simulator/Besca/Diedrich) и «Транспорт» (TCP/Serial/Phidget). Отдельного дерева «Machine» с подменю нет. Перенос — опционально: список пресетов машин, при выборе которых подставляются готовые Ports/Events/Buttons/Sliders.

---

## 9. Сводка: файлы Artisan по темам

| Тема | Основные файлы Artisan |
|------|------------------------|
| Ports Configuration (Modbus, Serial, S7, WebSocket) | `ports.py` (comportDlg), `modbusport.py` |
| Modbus: чтение регистров ET/BT, Input 1…10 | `modbusport.py`, `comm.py` |
| Events Config (CHARGE/DROP команды) | `events.py`, `main.py` (alarmcommand/event execution) |
| Events Buttons (таблица кнопок + Modbus Command) | `events.py` (eventbuttontable, extraevents*), `main.py` |
| Events Sliders (Command, Min, Max, Factor, Offset) | `events.py`, `atypes.py`, `main.py` |
| Events Quantifiers | `events.py`, `atypes.py` |
| Device Assignment (ET/BT, Extra, Networks) | `devices.py`, `comm.py`, `modbusport.py` |
| Axes (Time, Temp, Grid, Δ) | `axis.py`, `canvas.py` |
| Выполнение write/wcoil/sleep | `main.py` (~9060–9200, 11464+) |

---

## 10. Итерации переноса в RDR

Рекомендуемый порядок задач для агентов (по одному агенту на задачу):

1. **Ports: полная сетка Modbus Input 1…10 + PID** — расширить PortsConfigView/Controller и MachineConfig по образцу Artisan (Device, Register, Function, Divider, Mode, Decode для каждого входа; PID ON/OFF и регистры).
2. **Events Config: команды для CHARGE/DROP/FC/COOL END** — модель «событие → строка команды», парсер `write`/`wcoil`/`sleep` и вызов `ModbusConnectionCore.writeSingle`/writeCoil.
3. **Events Buttons: таблица кнопок** — модель + UI (label, command, visibility, color) и выполнение команд при нажатии.
4. **Events Sliders: конфигурируемые команды и Factor/Offset** — расширить конфиг слайдеров и формулу записи в регистр.
5. **Events Quantifiers** — при необходимости, как расширение слайдеров/источников.
6. **Device Assignment (минимально)** — при необходимости: ET/BT источник и связка с Modbus (частично уже есть через MachineConfig).
7. **Axes** — расширить ChartConfig и UI под Time/Temp/Grid/Δ Axis как в Artisan.

Детальные промпты для каждого пункта — в файле `ARTISAN-AGENT-PROMPTS.md`.
