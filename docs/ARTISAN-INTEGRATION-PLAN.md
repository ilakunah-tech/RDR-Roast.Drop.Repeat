# Интеграция кода Artisan в RDR (под копирку)

## Цель

Перенести в RDR логику и UI подключения к ростеру из Artisan так, чтобы:
- структура диалогов и поток данных совпадали с Artisan;
- применение настроек происходило по схеме Artisan: диалог открыт → правки → OK → **вызывающий код** читает поля диалога и записывает в модель (а не диалог сам сохраняет).

---

## Соответствие компонентов Artisan → RDR

| Artisan | RDR (текущий / целевой) |
|--------|---------------------------|
| **main.py** `self.ser` (serialport), `self.modbus` (modbusport) | **AppSettings.machineConfig** + **RoastRecorder.dataSource** (BescaModbusTcpSource / BescaModbusSource) |
| **ports.py** `comportDlg` — «Ports Configuration», вкладки Comm Port / Extra devices / MODBUS | **PortsConfigController** + **PortsConfigView.fxml** — диалог «Ports Configuration» с вкладками Comm Port и MODBUS |
| **modbusport.py** `modbusport` (host, port, type, comport, baudrate, …) | **MachineConfig** (host, tcpPort, transport, port, baudRate, slaveId, …) |
| **main.py** после `dialog.exec()`: `self.modbus.host = dialog.modbus_hostEdit.text()` и т.д. | После закрытия диалога портов: **SettingsController** (или MainController) читает **PortsConfigController** и обновляет **MachineConfig** / поля вкладки «Подключение» |
| **comm.py** `serialport.openport()` / `closeport()` | **BescaModbusSource.connect()** / **disconnect()** (j2mod serial) |
| **modbusport.py** `connect_async()` (TCP: host, port) | **BescaModbusTcpSource.connect()** (j2mod ModbusTCPMaster(host, port)) |
| **canvas.sample()** опрос ET/BT через devicefunctionlist | **RoastRecorder** + **sampleFlow()** в BescaModbusTcpSource / BescaModbusSource |
| **Device Assignment → ET/BT** (источник BT/ET) | Задаётся типом машины: Besca/Diedrich Serial → Modbus; Diedrich Phidget → Phidget; Simulator → симуляция. См. [ET-BT-SOURCE.md](ET-BT-SOURCE.md). |

---

## Реализованные шаги

### 1. Диалог «Ports Configuration» (как в Artisan) ✅

- **Файлы:** `PortsConfigView.fxml`, `PortsConfigController.kt`.
- **Вкладка 1 — Comm Port:** порт COM, Baud Rate, Byte Size, Parity, Stopbits, Timeout (для устройств не MODBUS; в RDR можно использовать для Serial-источника).
- **Вкладка 2 — MODBUS:**  
  - Comm Port, Baud Rate, Byte Size, Parity, Stopbits, Timeout (для Serial MODBUS);  
  - **Type:** Serial RTU / Serial ASCII / TCP / UDP (индексы 0–4 как в Artisan);  
  - **Host**, **Port** (для TCP/UDP);  
  - Slave ID (для RDR/Besca).
- При открытии диалога поля заполняются из текущего **MachineConfig** (или из **AppSettings**). По нажатию OK диалог только закрывается с результатом OK; **сохранение не выполняет** — вызывающий код считывает значения из контроллера и сам обновляет настройки.

### 2. Применение настроек по схеме Artisan

- В **SettingsController** (вкладка «Подключение»): кнопка **«Ports Configuration…»** открывает **PortsConfigController**.
- По возврату OK вызывающий код:
  - читает из диалога: MODBUS Host, Port, Type, Comm Port, Baud Rate, Slave ID и т.д.;
  - подставляет их в поля вкладки «Подключение» (txtHost, txtTcpPort, cmbTransport, cmbPort, txtBaudRate, txtSlaveId);
  - при необходимости вызывает сохранение или оставляет пользователю нажатие «Сохранить».

### 3. Логика подключения (оставить RDR, выравнять по смыслу с Artisan)

- Оставить **j2mod** и **BescaModbusTcpSource** / **BescaModbusSource**.
- Убедиться, что для TCP используются те же параметры, что в Artisan: **host**, **port** (502 по умолчанию), таймаут; для Serial — comport, baudrate, slave ID.
- При необходимости добавить в **MachineConfig** поля по образцу Artisan (bytesize, parity, stopbits, timeout для serial; type = Serial RTU / TCP / UDP) и использовать их в драйверах.

---

## Опционально (после базовой интеграции)

- Вкладка **Extra devices** (таблица дополнительных serial-устройств) — если понадобится в RDR.
- Полная сетка MODBUS Input 1…10 (Device, Register, Function, Divider, Mode, Decode) как в Artisan — при необходимости расширенной настройки регистров.
- Кнопка **Scan** в диалоге MODBUS — вызов сканера регистров по выбранному Host/Port или Serial.

---

## Итог

После интеграции:

1. В RDR есть диалог **«Ports Configuration»** с вкладками Comm Port и MODBUS и полями Host/Port/Type как в Artisan.
2. Настройки применяются по схеме Artisan: диалог только возвращает OK и свои значения; запись в **MachineConfig** и сохранение выполняет вызывающий код.
3. Подключение к ростеру по-прежнему идёт через **DataSourceFactory** и Besca-драйверы, но параметры соединения задаются тем же способом и теми же понятиями (Host, Port, Type, Comm Port), что и в Artisan.
