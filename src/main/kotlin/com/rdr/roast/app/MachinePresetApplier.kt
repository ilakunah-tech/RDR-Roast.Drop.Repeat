package com.rdr.roast.app

object MachinePresetApplier {

    fun apply(preset: AsetPreset, brand: String, model: String, current: AppSettings): AppSettings {
        val machineType = resolveDeviceId(preset.device.id)
        val machineConfig = buildMachineConfig(preset, machineType)
        val portConfig = buildPortConfig(preset, current.portConfig)
        val deviceAssignment = buildDeviceAssignment(preset, current.deviceAssignment)
        val presetSliders = buildPresetSliders(preset)
        val customButtons = buildCustomButtons(preset)
        val extraSensors = buildExtraSensors(preset)
        val quantifiers = buildQuantifiers(preset, current.eventQuantifiers)
        val eventSliders = buildEventSliders(preset, current.eventSliders)
        val eventButtonsConfig = buildEventButtonsDialogConfig(preset, current.eventButtonsConfig)

        return current.copy(
            machineConfig = machineConfig,
            portConfig = portConfig,
            deviceAssignment = deviceAssignment,
            presetSliders = presetSliders,
            customButtons = customButtons,
            extraSensors = extraSensors,
            eventQuantifiers = quantifiers,
            eventSliders = eventSliders,
            eventButtonsConfig = eventButtonsConfig,
            presetBrand = brand,
            presetModel = model
        )
    }

    fun resolveDeviceId(deviceId: Int): MachineType = when (deviceId) {
        0 -> MachineType.SIMULATOR
        29 -> MachineType.MODBUS_GENERIC
        79 -> MachineType.S7_GENERIC
        101 -> MachineType.SERIAL_GENERIC
        in 83..84, 99, 176 -> MachineType.SERIAL_GENERIC
        else -> MachineType.MODBUS_GENERIC
    }

    private fun buildMachineConfig(preset: AsetPreset, machineType: MachineType): MachineConfig {
        val mb = preset.modbus
        val serial = preset.serial
        val pollingMs = preset.general.delay.toLong().coerceAtLeast(200)

        val transport = when {
            mb != null && (mb.type == 3 || mb.type == 4) -> Transport.TCP
            mb != null && mb.type == 0 -> Transport.SERIAL
            else -> Transport.TCP
        }

        val modbusTransportType = when (mb?.type) {
            0 -> ModbusTransportType.SERIAL_RTU
            1 -> ModbusTransportType.SERIAL_ASCII
            3 -> ModbusTransportType.TCP
            4 -> ModbusTransportType.UDP
            else -> ModbusTransportType.TCP
        }

        val input1 = mb?.inputs?.getOrNull(0)
        val input2 = mb?.inputs?.getOrNull(1)

        val parity = when ((mb?.parity ?: serial?.parity ?: "N").uppercase()) {
            "E" -> SerialParity.EVEN
            "O" -> SerialParity.ODD
            else -> SerialParity.NONE
        }

        val modbusInputs = mb?.inputs?.map { inp ->
            ModbusInputConfig(
                deviceId = inp.deviceId,
                register = inp.register,
                functionCode = inp.code,
                dividerIndex = inp.div,
                mode = when (inp.mode.uppercase()) {
                    "F" -> ModbusInputMode.F
                    "C" -> ModbusInputMode.C
                    else -> ModbusInputMode.NONE
                },
                decode = resolveModbusDecode(inp)
            )
        } ?: defaultModbusInputs()

        val modbusPid = mb?.pid?.let { p ->
            if (p.svRegister != 0 || p.pRegister != 0)
                ModbusPidConfig(
                    deviceId = p.deviceId,
                    svRegister = p.svRegister,
                    pRegister = p.pRegister,
                    iRegister = p.iRegister,
                    dRegister = p.dRegister,
                    onCommand = p.onAction,
                    offCommand = p.offAction
                )
            else null
        }

        val gasReg = findSliderRegister(preset, 3)
        val airReg = findSliderRegister(preset, 0)
        val drumReg = findSliderRegister(preset, 1)

        return MachineConfig(
            machineType = machineType,
            transport = transport,
            host = mb?.host ?: "127.0.0.1",
            tcpPort = mb?.port ?: 502,
            port = mb?.comport ?: serial?.comport ?: "COM4",
            baudRate = mb?.baudrate ?: serial?.baudrate ?: 9600,
            byteSize = mb?.bytesize ?: serial?.bytesize ?: 8,
            parity = parity,
            stopBits = mb?.stopbits ?: serial?.stopbits ?: 1,
            serialTimeoutSec = mb?.timeout ?: serial?.timeout ?: 0.5,
            modbusTransportType = modbusTransportType,
            slaveId = input1?.deviceId ?: 1,
            btRegister = input1?.register ?: 0,
            etRegister = input2?.register ?: 0,
            functionCode = input1?.code ?: 3,
            divisionFactor = when (input1?.div) {
                1 -> 10.0; 2 -> 100.0; else -> 1.0
            },
            pollingIntervalMs = pollingMs,
            gasRegister = gasReg,
            airflowRegister = airReg,
            drumRegister = drumReg,
            modbusInputs = modbusInputs,
            modbusPid = modbusPid
        )
    }

    private fun resolveModbusDecode(inp: AsetModbusInput): ModbusInputDecode = when {
        inp.isFloat -> ModbusInputDecode.FLOAT32
        inp.isBcd -> ModbusInputDecode.BCD16
        else -> ModbusInputDecode.UINT16
    }

    private fun findSliderRegister(preset: AsetPreset, sliderIndex: Int): Int {
        val slider = preset.sliders?.sliders?.getOrNull(sliderIndex) ?: return 0
        if (slider.command.isBlank() || !slider.visible) return 0
        val match = Regex("""writeSingle\(\d+,(\d+),""").find(slider.command)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun buildPortConfig(preset: AsetPreset, current: PortConfig): PortConfig {
        val mb = preset.modbus
        val s7 = preset.s7

        val modbusAdv = if (mb != null) {
            PortModbusAdvancedConfig(
                ipTimeoutSec = mb.timeout,
                littleEndianWords = mb.wordorderLittle,
                littleEndian = mb.littleEndianFloats,
                optimize = mb.optimizer,
                fetchFullBlocks = mb.fetchMaxBlocks
            )
        } else current.modbusAdvanced

        val s7Config = if (s7 != null) {
            PortS7Config(
                host = s7.host,
                port = s7.port,
                rack = s7.rack,
                slot = s7.slot,
                optimize = s7.optimizer,
                fetchFullBlocks = s7.fetchMaxBlocks,
                inputs = s7.inputs.map { inp ->
                    PortS7InputConfig(
                        area = s7AreaName(inp.area),
                        db = inp.dbNr,
                        start = inp.start,
                        type = s7TypeName(inp.type),
                        mode = s7ModeName(inp.mode)
                    )
                },
                pid = s7.pid.let { p ->
                    PortS7PidConfig(
                        device = p.dbNr,
                        sv = p.svRegister,
                        p = p.pRegister,
                        i = p.iRegister,
                        d = p.dRegister
                    )
                }
            )
        } else current.s7

        return current.copy(
            etBtCommPort = mb?.comport ?: preset.serial?.comport ?: current.etBtCommPort,
            etBtBaudRate = mb?.baudrate ?: preset.serial?.baudrate ?: current.etBtBaudRate,
            etBtByteSize = mb?.bytesize ?: preset.serial?.bytesize ?: current.etBtByteSize,
            etBtParity = mb?.parity ?: preset.serial?.parity ?: current.etBtParity,
            etBtStopbits = mb?.stopbits ?: preset.serial?.stopbits ?: current.etBtStopbits,
            etBtTimeoutSec = mb?.timeout ?: preset.serial?.timeout ?: current.etBtTimeoutSec,
            modbusAdvanced = modbusAdv,
            s7 = s7Config
        )
    }

    private fun s7AreaName(code: Int): String = when (code) {
        0 -> "I"; 1 -> "Q"; 2 -> "M"; 3 -> "DB"; else -> "DB"
    }
    private fun s7TypeName(code: Int): String = when (code) {
        0 -> "Int"; 1 -> "Float"; 2 -> "Bool"; else -> "Int"
    }
    private fun s7ModeName(code: Int): String = when (code) {
        0 -> "C"; 1 -> "F"; else -> "C"
    }

    private fun buildDeviceAssignment(preset: AsetPreset, current: DeviceAssignmentConfig): DeviceAssignmentConfig {
        val etBt = current.etBt.copy(
            meter = when {
                preset.modbus != null -> "MODBUS"
                preset.s7 != null -> "S7"
                else -> current.etBt.meter
            }
        )
        return current.copy(etBt = etBt)
    }

    private val DEFAULT_EVENT_LABELS = listOf("Air", "Drum", "Damper", "Burner")
    private val ALT_EVENT_LABELS = listOf("Fan", "Drum", "Cooling", "Heater")

    private fun resolveEventLabels(preset: AsetPreset): List<String> {
        val events = preset.events
        val rawLabels = events?.etypes
        if (rawLabels != null && rawLabels.any { it.isNotBlank() }) return rawLabels

        val etypesSet = events?.defaultEtypesSet ?: listOf(0, 0, 0, 0, 0)
        return (0 until 5).map { i ->
            if (etypesSet.getOrElse(i) { 0 } != 0)
                ALT_EVENT_LABELS.getOrElse(i) { DEFAULT_EVENT_LABELS.getOrElse(i) { "" } }
            else
                DEFAULT_EVENT_LABELS.getOrElse(i) { "" }
        }
    }

    private fun buildPresetSliders(preset: AsetPreset): List<PresetSliderConfig> {
        val sliders = preset.sliders ?: return emptyList()
        val eventLabels = resolveEventLabels(preset)
        return sliders.sliders.mapIndexedNotNull { i, def ->
            if (!def.visible) return@mapIndexedNotNull null
            PresetSliderConfig(
                label = eventLabels.getOrElse(i) { DEFAULT_EVENT_LABELS.getOrElse(i) { "Slider ${i + 1}" } }
                    .ifBlank { DEFAULT_EVENT_LABELS.getOrElse(i) { "Slider ${i + 1}" } },
                command = def.command,
                actionType = def.actionType,
                min = def.min,
                max = def.max,
                factor = def.factor,
                offset = def.offset,
                visible = def.visible,
                unit = def.unit,
                isTemp = def.isTemp
            )
        }
    }

    private fun buildCustomButtons(preset: AsetPreset): List<CustomButtonConfig> {
        val eb = preset.eventButtons ?: return emptyList()
        val eventLabels = resolveEventLabels(preset)

        return eb.buttons.filter { it.visible && it.label.isNotBlank() }.map { btn ->
            val etypeName = when {
                btn.eventType in 5..8 -> eventLabels.getOrElse(btn.eventType - 5) {
                    DEFAULT_EVENT_LABELS.getOrElse(btn.eventType - 5) { "" }
                }
                else -> ""
            }
            CustomButtonConfig(
                label = decodeButtonLabel(btn.label, etypeName),
                description = btn.description,
                eventType = eventTypeString(btn.eventType),
                eventValue = btn.eventValue,
                actionType = actionTypeName(btn.actionType),
                commandString = btn.actionString,
                visibility = btn.visible,
                backgroundColor = btn.backgroundColor,
                textColor = btn.textColor
            )
        }
    }

    private fun decodeButtonLabel(raw: String, etypeName: String = ""): String {
        return raw
            .replace("\\\\t", etypeName)
            .replace("\\\\1", "ON")
            .replace("\\\\0", "OFF")
            .replace("\\\\o", "OPEN")
            .replace("\\\\c", "CLOSE")
            .replace("\\n", "\n")
            .replace("\\t", etypeName)
            .replace("\\1", "ON")
            .replace("\\0", "OFF")
            .replace("\\o", "OPEN")
            .replace("\\c", "CLOSE")
    }

    private fun eventTypeString(code: Int): String = when (code) {
        4 -> ""; 5 -> "Air"; 6 -> "Drum"; 7 -> "Damper"; 8 -> "Burner"
        else -> ""
    }

    private fun actionTypeName(code: Int): String = when (code) {
        0 -> ""; 1 -> "Call"; 2 -> "Slider"; 3 -> "Serial"; 4 -> "Modbus Command"
        5 -> "DTA Command"; 6 -> "IO Command"; 12 -> "S7 Command"
        else -> "Modbus Command"
    }

    private fun buildExtraSensors(preset: AsetPreset): List<ExtraSensorChannelConfig> {
        return preset.extraDevices.map { dev ->
            ExtraSensorChannelConfig(
                deviceId = dev.deviceId,
                label1 = dev.name1,
                label2 = dev.name2,
                color1 = dev.color1,
                color2 = dev.color2,
                curveVisible1 = dev.curveVisible1,
                curveVisible2 = dev.curveVisible2,
                lcdVisible1 = dev.lcdVisible1,
                lcdVisible2 = dev.lcdVisible2,
                mathExpression1 = dev.mathExpression1,
                mathExpression2 = dev.mathExpression2
            )
        }
    }

    private fun buildQuantifiers(preset: AsetPreset, current: EventQuantifiersConfig): EventQuantifiersConfig {
        val q = preset.quantifiers ?: return current
        fun qConfig(index: Int): EventQuantifierConfig {
            val active = q.active.getOrElse(index) { false }
            if (!active) return EventQuantifierConfig()
            return EventQuantifierConfig(
                source = when (q.sources.getOrElse(index) { 0 }) {
                    1 -> QuantifierSource.ET; 2 -> QuantifierSource.BT
                    3 -> QuantifierSource.BT; 4 -> QuantifierSource.ET
                    else -> QuantifierSource.NONE
                },
                min = q.mins.getOrElse(index) { 0 },
                max = q.maxs.getOrElse(index) { 100 },
                actionEnabled = active
            )
        }
        return EventQuantifiersConfig(
            air = qConfig(0),
            drum = qConfig(1),
            damper = qConfig(2),
            burner = qConfig(3)
        )
    }

    private fun buildEventSliders(preset: AsetPreset, current: EventSlidersConfig): EventSlidersConfig {
        val sliders = preset.sliders ?: return current
        fun row(index: Int): EventSliderRowConfig {
            val def = sliders.sliders.getOrNull(index) ?: return EventSliderRowConfig()
            return EventSliderRowConfig(
                command = def.command,
                min = def.min,
                max = def.max,
                factor = def.factor,
                offset = def.offset,
                bernoulli = def.bernoulli,
                temp = def.isTemp,
                unit = def.unit
            )
        }
        return EventSlidersConfig(
            air = row(0), drum = row(1), damper = row(2), burner = row(3)
        )
    }

    private fun buildEventButtonsDialogConfig(
        preset: AsetPreset,
        current: EventButtonsDialogConfig
    ): EventButtonsDialogConfig {
        val labels = resolveEventLabels(preset)
        return current.copy(
            eventType1Label = labels.getOrElse(0) { "Air" }.ifBlank { "Air" },
            eventType2Label = labels.getOrElse(1) { "Drum" }.ifBlank { "Drum" },
            eventType3Label = labels.getOrElse(2) { "Damper" }.ifBlank { "Damper" },
            eventType4Label = labels.getOrElse(3) { "Burner" }.ifBlank { "Burner" }
        )
    }
}
