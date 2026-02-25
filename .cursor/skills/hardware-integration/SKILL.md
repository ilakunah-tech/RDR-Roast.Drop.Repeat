---
name: hardware-integration
description: Integrates roasting hardware via MODBUS, Siemens S7, serial, Phidget, Yoctopuce. Use when adding or fixing drivers, data-source adapters, or device configuration for the roasting app.
---

# Hardware Integration

## Contract

- Define a single interface, e.g. `RoastDataSource` or `TemperatureSource`, that provides:
  - A stream of samples (time, bean temp, optional env temp) and optional events
  - Connection state (disconnected, connecting, connected, error)
  - Connect / disconnect / reconnect (with timeout)
- All protocol-specific code lives in adapters that implement this interface.

## Libraries

- **MODBUS**: j2mod. Read holding/input registers; map to temperature (and optionally fan, gas) per device manual.
- **S7**: moka7. Read DB blocks; map addresses to temperatures.
- **Serial**: jSerialComm. Open port, set baud rate; parse line-based or binary protocol; emit samples on a background thread and expose as Flow or callback.
- **Phidget / Yoctopuce**: Use official Java libs; wrap in the same interface.

## Requirements

- Timeout on every read (e.g. 2–5 s). On timeout, set state to error and allow retry.
- Do not block the JavaFX thread. Run polling or listeners in coroutines or a dedicated executor.
- Configuration: port name, slave ID, register addresses, etc. from config/settings; validate and log on connect.

## Testing

- Use a mock or in-memory implementation of the interface for unit tests. Optional: Testcontainers or a MODBUS/S7 simulator for integration tests.
