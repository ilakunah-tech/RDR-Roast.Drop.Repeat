# ET/BT source and parameters (Connection tab)

This document states where **Bean Temperature (BT)** and **Environment Temperature (ET)** come from for each machine type, and that all parameters are taken from **Settings → Connection** tab and **Ports Configuration** (MachineConfig).

## Summary

| Machine (Source) | ET/BT source | Parameters from |
|-----------------|--------------|-----------------|
| **Simulator**   | Simulated    | —               |
| **Besca** (TCP or Serial) | Modbus | Host, Port (TCP) or COM/Baud (Serial), Slave ID, BT Register, ET Register, Division factor — from Connection tab and Ports/MachineConfig |
| **Diedrich** (Phidget 1048) | Phidget | ET/BT channel numbers from Connection tab (MachineConfig) |
| **Diedrich** (Serial Modbus) | Modbus | Same as Besca Serial: COM, Baud, Slave ID, BT/ET registers from Connection/Ports |

---

## Besca (TCP or Serial)

- **ET/BT source:** Modbus.
- **Parameters:** All come from **Settings → Connection** and **Ports Configuration** (MachineConfig):
  - **TCP:** Host, Port (default 502), Slave ID, BT Register, ET Register, Division factor.
  - **Serial:** Comm port, Baud rate, Slave ID, BT Register, ET Register, Division factor.
- BT/ET are read from the configured Modbus registers (e.g. default registers 6 and 7); raw value is divided by the division factor to get temperature.

---

## Diedrich (Phidget 1048 USB)

- **ET/BT source:** Phidget (thermocouple inputs on the 1048).
- **Parameters:** ET channel and BT channel (from Connection tab / MachineConfig). No Modbus registers; temperatures come directly from the Phidget device.

---

## Diedrich (Serial Modbus)

- **ET/BT source:** Modbus (same logic as Besca Serial).
- **Parameters:** COM port, Baud rate, Slave ID, BT Register, ET Register, Division factor from Connection tab and Ports/MachineConfig.

---

## Simulator

- **ET/BT source:** Simulated (no hardware).
- **Parameters:** None; values are generated internally.

---

## Parity with Artisan

In Artisan, **Device Assignment → ET/BT** lets you choose the meter (e.g. Meter MODBUS, PID, TC4). In RDR the choice is implicit:

- Selecting **Besca** or **Diedrich (Serial)** means ET/BT come from **Modbus** (registers and connection from Ports/MachineConfig).
- Selecting **Diedrich (Phidget)** means ET/BT come from **Phidget**.
- Selecting **Simulator** means ET/BT are **simulated**.

There is no separate “Device Assignment” dialog; the **Connection** tab machine type and transport define the ET/BT source.
