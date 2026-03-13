package com.rdr.roast.app

import java.io.InputStream

data class AsetGeneral(
    val delay: Int = 1000,
    val oversampling: Boolean = false,
    val roasterTypeSetup: String = "",
    val roasterSizeSetup: Double = 0.0,
    val roasterHeatingSetup: Int = 0,
    val dropDuplicates: Boolean = false
)

data class AsetDevice(
    val id: Int = 0
)

data class AsetModbusInput(
    val register: Int = 0,
    val deviceId: Int = 0,
    val code: Int = 3,
    val mode: String = "C",
    val div: Int = 0,
    val isFloat: Boolean = false,
    val isBcd: Boolean = false,
    val floatsAsInt: Boolean = false,
    val bcdsAsInt: Boolean = false,
    val isSigned: Boolean = false
)

data class AsetModbusPid(
    val deviceId: Int = 0,
    val svRegister: Int = 0,
    val pRegister: Int = 0,
    val iRegister: Int = 0,
    val dRegister: Int = 0,
    val pidMultiplier: Int = 0,
    val svMultiplier: Int = 1,
    val onAction: String = "",
    val offAction: String = ""
)

data class AsetModbus(
    val host: String = "127.0.0.1",
    val port: Int = 502,
    val comport: String = "COM4",
    val baudrate: Int = 9600,
    val bytesize: Int = 8,
    val parity: String = "N",
    val stopbits: Int = 1,
    val timeout: Double = 0.4,
    val type: Int = 0,
    val wordorderLittle: Boolean = false,
    val littleEndianFloats: Boolean = false,
    val optimizer: Boolean = false,
    val fetchMaxBlocks: Boolean = false,
    val inputs: List<AsetModbusInput> = List(10) { AsetModbusInput() },
    val pid: AsetModbusPid = AsetModbusPid()
)

data class AsetSerial(
    val comport: String = "COM4",
    val baudrate: Int = 9600,
    val bytesize: Int = 8,
    val stopbits: Int = 1,
    val parity: String = "N",
    val timeout: Double = 0.5
)

data class AsetS7Input(
    val area: Int = 0,
    val dbNr: Int = 0,
    val start: Int = 0,
    val type: Int = 0,
    val mode: Int = 0,
    val div: Int = 0
)

data class AsetS7Pid(
    val area: Int = 0,
    val dbNr: Int = 0,
    val svRegister: Int = 0,
    val pRegister: Int = 0,
    val iRegister: Int = 0,
    val dRegister: Int = 0,
    val pidMultiplier: Int = 0,
    val svMultiplier: Int = 0,
    val svType: Int = 0,
    val onAction: String = "",
    val offAction: String = ""
)

data class AsetS7(
    val host: String = "127.0.0.1",
    val port: Int = 102,
    val rack: Int = 0,
    val slot: Int = 0,
    val optimizer: Boolean = false,
    val fetchMaxBlocks: Boolean = false,
    val inputs: List<AsetS7Input> = List(12) { AsetS7Input() },
    val pid: AsetS7Pid = AsetS7Pid()
)

data class AsetExtraDevice(
    val deviceId: Int = 0,
    val name1: String = "",
    val name2: String = "",
    val color1: String = "black",
    val color2: String = "black",
    val curveVisible1: Boolean = true,
    val curveVisible2: Boolean = true,
    val lcdVisible1: Boolean = false,
    val lcdVisible2: Boolean = false,
    val delta1: Boolean = false,
    val delta2: Boolean = false,
    val mathExpression1: String = "",
    val mathExpression2: String = ""
)

data class AsetExtraComm(
    val baudrates: List<Int> = emptyList(),
    val bytesizes: List<Int> = emptyList(),
    val comports: List<String> = emptyList(),
    val parities: List<String> = emptyList(),
    val stopbits: List<Int> = emptyList(),
    val timeouts: List<Double> = emptyList()
)

data class AsetSliderDef(
    val command: String = "",
    val actionType: Int = 0,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val factor: Double = 1.0,
    val offset: Double = 0.0,
    val visible: Boolean = true,
    val unit: String = "",
    val isTemp: Boolean = false,
    val bernoulli: Boolean = false,
    val coarse: Boolean = false
)

data class AsetSliders(
    val modeTempSliders: String = "C",
    val flags: List<Boolean> = listOf(true, true, true),
    val sliders: List<AsetSliderDef> = List(4) { AsetSliderDef() },
    val keyboardControl: Boolean = false,
    val alternativeLayout: Boolean = false
)

data class AsetCustomButton(
    val label: String = "",
    val description: String = "",
    val eventType: Int = 0,
    val eventValue: Int = 0,
    val actionType: Int = 0,
    val actionString: String = "",
    val visible: Boolean = true,
    val backgroundColor: String = "#808080",
    val textColor: String = "white"
)

data class AsetEventButtons(
    val buttons: List<AsetCustomButton> = emptyList(),
    val buttonSize: Int = 1,
    val markLastPressed: Boolean = true,
    val showTooltips: Boolean = false,
    val buttonsFlags: List<Boolean> = listOf(true, true, true)
)

data class AsetDefaultButtons(
    val actions: List<Int> = emptyList(),
    val actionStrings: List<String> = emptyList(),
    val visibility: List<Boolean> = emptyList()
)

data class AsetQuantifiers(
    val sources: List<Int> = listOf(0, 0, 0, 0),
    val mins: List<Int> = listOf(0, 0, 0, 0),
    val maxs: List<Int> = listOf(100, 100, 100, 100),
    val active: List<Boolean> = listOf(false, false, false, false),
    val coarse: List<Boolean> = listOf(false, false, false, false),
    val clusterEvents: Boolean = false
)

data class AsetPid(
    val kp: Double = 20.0,
    val ki: Double = 0.01,
    val kd: Double = 3.0,
    val cycle: Int = 1000,
    val source: Int = 1,
    val svValue: Double = 0.0,
    val svMode: Int = 0,
    val positiveTarget: Int = 0,
    val negativeTarget: Int = 0,
    val dutyMin: Int = -100,
    val dutyMax: Int = 100,
    val dutySteps: Int = 1,
    val invertControl: Boolean = false,
    val pidOnCharge: Boolean = false,
    val svSlider: Boolean = false,
    val svSliderMin: Int = 0,
    val svSliderMax: Int = 480,
    val svButtons: Boolean = false,
    val pOnE: Boolean = true
)

data class AsetEvents(
    val etypes: List<String> = listOf("", "", "", "", ""),
    val defaultEtypesSet: List<Int> = listOf(0, 0, 0, 0, 0),
    val autoCharge: Boolean = false,
    val autoDrop: Boolean = false
)

data class AsetPreset(
    val general: AsetGeneral = AsetGeneral(),
    val device: AsetDevice = AsetDevice(),
    val modbus: AsetModbus? = null,
    val s7: AsetS7? = null,
    val serial: AsetSerial? = null,
    val extraDevices: List<AsetExtraDevice> = emptyList(),
    val extraComm: AsetExtraComm? = null,
    val sliders: AsetSliders? = null,
    val eventButtons: AsetEventButtons? = null,
    val defaultButtons: AsetDefaultButtons? = null,
    val quantifiers: AsetQuantifiers? = null,
    val pid: AsetPid? = null,
    val events: AsetEvents? = null
)

object AsetParser {

    fun parse(input: InputStream): AsetPreset {
        val sections = parseIni(input.bufferedReader().readText())
        return AsetPreset(
            general = parseGeneral(sections["General"]),
            device = parseDevice(sections["Device"]),
            modbus = sections["Modbus"]?.let { parseModbus(it) },
            s7 = sections["S7"]?.let { parseS7(it) },
            serial = sections["SerialPort"]?.let { parseSerial(it) },
            extraDevices = parseExtraDevices(sections["ExtraDev"], sections["ExtraComm"]),
            extraComm = sections["ExtraComm"]?.let { parseExtraComm(it) },
            sliders = sections["Sliders"]?.let { parseSliders(it) },
            eventButtons = sections["ExtraEventButtons"]?.let { parseEventButtons(it) },
            defaultButtons = sections["DefaultButtons"]?.let { parseDefaultButtons(it) },
            quantifiers = sections["Quantifiers"]?.let { parseQuantifiers(it) },
            pid = sections["ArduinoPID"]?.let { parsePid(it) },
            events = sections["events"]?.let { parseEvents(it) }
        )
    }

    private fun parseIni(text: String): Map<String, Map<String, String>> {
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var currentSection: MutableMap<String, String>? = null
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val name = trimmed.substring(1, trimmed.length - 1)
                currentSection = sections.getOrPut(name) { mutableMapOf() }
                continue
            }
            val eq = trimmed.indexOf('=')
            if (eq > 0 && currentSection != null) {
                val key = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                currentSection[key] = value
            }
        }
        return sections
    }

    private fun parseGeneral(map: Map<String, String>?): AsetGeneral {
        if (map == null) return AsetGeneral()
        return AsetGeneral(
            delay = map["Delay"]?.toIntOrNull() ?: 1000,
            oversampling = map["Oversampling"]?.toBooleanStrictOrNull() ?: false,
            roasterTypeSetup = map["roastertype_setup"] ?: "",
            roasterSizeSetup = map["roastersize_setup"]?.toDoubleOrNull() ?: 0.0,
            roasterHeatingSetup = map["roasterheating_setup"]?.toIntOrNull() ?: 0,
            dropDuplicates = map["dropDuplicates"]?.toBooleanStrictOrNull() ?: false
        )
    }

    private fun parseDevice(map: Map<String, String>?): AsetDevice {
        return AsetDevice(id = map?.get("id")?.toIntOrNull() ?: 0)
    }

    private fun parseModbus(map: Map<String, String>): AsetModbus {
        val inputs = (1..10).map { i -> parseModbusInput(map, i) }
        return AsetModbus(
            host = map["host"] ?: "127.0.0.1",
            port = map["port"]?.toIntOrNull() ?: 502,
            comport = map["comport"] ?: "COM4",
            baudrate = map["baudrate"]?.toIntOrNull() ?: 9600,
            bytesize = map["bytesize"]?.toIntOrNull() ?: 8,
            parity = map["parity"] ?: "N",
            stopbits = map["stopbits"]?.toIntOrNull() ?: 1,
            timeout = map["timeout"]?.toDoubleOrNull() ?: 0.4,
            type = map["type"]?.toIntOrNull() ?: 0,
            wordorderLittle = map["wordorderLittle"]?.toBooleanStrictOrNull() ?: false,
            littleEndianFloats = map["littleEndianFloats"]?.toBooleanStrictOrNull() ?: false,
            optimizer = map["optimizer"]?.toBooleanStrictOrNull() ?: false,
            fetchMaxBlocks = map["fetch_max_blocks"]?.toBooleanStrictOrNull() ?: false,
            inputs = inputs,
            pid = AsetModbusPid(
                deviceId = map["PID_device_ID"]?.toIntOrNull() ?: 0,
                svRegister = map["PID_SV_register"]?.toIntOrNull() ?: 0,
                pRegister = map["PID_p_register"]?.toIntOrNull() ?: 0,
                iRegister = map["PID_i_register"]?.toIntOrNull() ?: 0,
                dRegister = map["PID_d_register"]?.toIntOrNull() ?: 0,
                pidMultiplier = map["PIDmultiplier"]?.toIntOrNull() ?: 0,
                svMultiplier = map["SVmultiplier"]?.toIntOrNull() ?: 1,
                onAction = unquote(map["PID_ON_action"] ?: ""),
                offAction = unquote(map["PID_OFF_action"] ?: "")
            )
        )
    }

    private fun parseModbusInput(map: Map<String, String>, index: Int): AsetModbusInput {
        return AsetModbusInput(
            register = map["input${index}register"]?.toIntOrNull() ?: 0,
            deviceId = map["input${index}deviceId"]?.toIntOrNull() ?: 0,
            code = map["input${index}code"]?.toIntOrNull() ?: 3,
            mode = map["input${index}mode"] ?: "C",
            div = map["input${index}div"]?.toIntOrNull() ?: 0,
            isFloat = map["input${index}float"]?.toBooleanStrictOrNull() ?: false,
            isBcd = map["input${index}bcd"]?.toBooleanStrictOrNull() ?: false,
            floatsAsInt = map["input${index}FloatsAsInt"]?.toBooleanStrictOrNull() ?: false,
            bcdsAsInt = map["input${index}BCDsAsInt"]?.toBooleanStrictOrNull() ?: false,
            isSigned = map["input${index}Signed"]?.toBooleanStrictOrNull() ?: false
        )
    }

    private fun parseS7(map: Map<String, String>): AsetS7 {
        val areas = splitInts(map["area"])
        val dbNrs = splitInts(map["db_nr"])
        val starts = splitInts(map["start"])
        val types = splitInts(map["type"])
        val modes = splitInts(map["mode"])
        val divs = splitInts(map["div"])
        val inputs = (0 until 12).map { i ->
            AsetS7Input(
                area = areas.getOrElse(i) { 0 },
                dbNr = dbNrs.getOrElse(i) { 0 },
                start = starts.getOrElse(i) { 0 },
                type = types.getOrElse(i) { 0 },
                mode = modes.getOrElse(i) { 0 },
                div = divs.getOrElse(i) { 0 }
            )
        }
        return AsetS7(
            host = map["host"] ?: "127.0.0.1",
            port = map["port"]?.toIntOrNull() ?: 102,
            rack = map["rack"]?.toIntOrNull() ?: 0,
            slot = map["slot"]?.toIntOrNull() ?: 0,
            optimizer = map["optimizer"]?.toBooleanStrictOrNull() ?: false,
            fetchMaxBlocks = map["fetch_max_blocks"]?.toBooleanStrictOrNull() ?: false,
            inputs = inputs,
            pid = AsetS7Pid(
                area = map["PID_area"]?.toIntOrNull() ?: 0,
                dbNr = map["PID_db_nr"]?.toIntOrNull() ?: 0,
                svRegister = map["PID_SV_register"]?.toIntOrNull() ?: 0,
                pRegister = map["PID_p_register"]?.toIntOrNull() ?: 0,
                iRegister = map["PID_i_register"]?.toIntOrNull() ?: 0,
                dRegister = map["PID_d_register"]?.toIntOrNull() ?: 0,
                pidMultiplier = map["PIDmultiplier"]?.toIntOrNull() ?: 0,
                svMultiplier = map["SVmultiplier"]?.toIntOrNull() ?: 0,
                svType = map["SVtype"]?.toIntOrNull() ?: 0,
                onAction = unquote(map["PID_ON_action"] ?: ""),
                offAction = unquote(map["PID_OFF_action"] ?: "")
            )
        )
    }

    private fun parseSerial(map: Map<String, String>): AsetSerial {
        return AsetSerial(
            comport = map["comport"] ?: "COM4",
            baudrate = map["baudrate"]?.toIntOrNull() ?: 9600,
            bytesize = map["bytesize"]?.toIntOrNull() ?: 8,
            stopbits = map["stopbits"]?.toIntOrNull() ?: 1,
            parity = map["parity"] ?: "N",
            timeout = map["timeout"]?.toDoubleOrNull() ?: 0.5
        )
    }

    private fun parseExtraDevices(
        devMap: Map<String, String>?,
        commMap: Map<String, String>?
    ): List<AsetExtraDevice> {
        if (devMap == null) return emptyList()
        val deviceIds = splitPlainInts(devMap["extradevices"])
        if (deviceIds.isEmpty()) return emptyList()
        val count = deviceIds.size
        val names1 = splitCsv(devMap["extraname1"])
        val names2 = splitCsv(devMap["extraname2"])
        val colors1 = splitCsv(devMap["extradevicecolor1"])
        val colors2 = splitCsv(devMap["extradevicecolor2"])
        val curveVis1 = splitBools(devMap["extraCurveVisibility1"])
        val curveVis2 = splitBools(devMap["extraCurveVisibility2"])
        val lcdVis1 = splitBools(devMap["extraLCDvisibility1"])
        val lcdVis2 = splitBools(devMap["extraLCDvisibility2"])
        val delta1 = splitBools(devMap["extraDelta1"])
        val delta2 = splitBools(devMap["extraDelta2"])
        val math1 = splitCsv(devMap["extramathexpression1"])
        val math2 = splitCsv(devMap["extramathexpression2"])

        return (0 until count).map { i ->
            AsetExtraDevice(
                deviceId = deviceIds[i],
                name1 = names1.getOrElse(i) { "" },
                name2 = names2.getOrElse(i) { "" },
                color1 = colors1.getOrElse(i) { "black" }.ifBlank { "black" },
                color2 = colors2.getOrElse(i) { "black" }.ifBlank { "black" },
                curveVisible1 = curveVis1.getOrElse(i) { true },
                curveVisible2 = curveVis2.getOrElse(i) { true },
                lcdVisible1 = lcdVis1.getOrElse(i) { false },
                lcdVisible2 = lcdVis2.getOrElse(i) { false },
                delta1 = delta1.getOrElse(i) { false },
                delta2 = delta2.getOrElse(i) { false },
                mathExpression1 = math1.getOrElse(i) { "" },
                mathExpression2 = math2.getOrElse(i) { "" }
            )
        }
    }

    private fun parseExtraComm(map: Map<String, String>): AsetExtraComm {
        return AsetExtraComm(
            baudrates = splitPlainInts(map["extrabaudrate"]),
            bytesizes = splitPlainInts(map["extrabytesize"]),
            comports = splitCsv(map["extracomport"]),
            parities = splitCsv(map["extraparity"]),
            stopbits = splitPlainInts(map["extrastopbits"]),
            timeouts = splitDoubles(map["extratimeout"])
        )
    }

    private fun parseSliders(map: Map<String, String>): AsetSliders {
        val commands = splitQuotedCsv(map["slidercommands"])
        val actions = splitInts(map["slideractions"])
        val mins = splitDoubles(map["slidermin"])
        val maxs = splitDoubles(map["slidermax"])
        val factors = splitDoubles(map["sliderfactors"])
        val offsets = splitDoubles(map["slideroffsets"])
        val visibilities = splitInts(map["slidervisibilities"])
        val units = splitCsv(map["eventsliderunits"])
        val temps = splitBoolsFromInts(map["eventslidertemp"])
        val bernoullis = splitBoolsFromInts(map["eventsliderBernoulli"])
        val coarses = splitBoolsFromInts(map["eventslidercoarse"])
        val flags = splitBoolsFromInts(map["eventslidersflags"])

        val sliders = (0 until 4).map { i ->
            AsetSliderDef(
                command = commands.getOrElse(i) { "" },
                actionType = actions.getOrElse(i) { 0 },
                min = mins.getOrElse(i) { 0.0 },
                max = maxs.getOrElse(i) { 100.0 },
                factor = factors.getOrElse(i) { 1.0 },
                offset = offsets.getOrElse(i) { 0.0 },
                visible = (visibilities.getOrElse(i) { 1 }) != 0,
                unit = units.getOrElse(i) { "" },
                isTemp = temps.getOrElse(i) { false },
                bernoulli = bernoullis.getOrElse(i) { false },
                coarse = coarses.getOrElse(i) { false }
            )
        }
        return AsetSliders(
            modeTempSliders = map["ModeTempSliders"] ?: "C",
            flags = flags.takeIf { it.isNotEmpty() } ?: listOf(true, true, true),
            sliders = sliders,
            keyboardControl = map["eventsliderKeyboardControl"]?.toBooleanStrictOrNull() ?: false,
            alternativeLayout = map["eventsliderAlternativeLayout"]?.toBooleanStrictOrNull() ?: false
        )
    }

    private fun parseEventButtons(map: Map<String, String>): AsetEventButtons {
        val labels = splitQuotedCsv(map["extraeventslabels"])
        val descriptions = splitCsv(map["extraeventsdescriptions"])
        val types = splitInts(map["extraeventstypes"])
        val values = splitInts(map["extraeventsvalues"])
        val actions = splitInts(map["extraeventsactions"])
        val actionStrings = splitQuotedCsv(map["extraeventsactionstrings"])
        val visibilities = splitInts(map["extraeventsvisibility"])
        val bgColors = splitCsv(map["extraeventbuttoncolor"])
        val textColors = splitCsv(map["extraeventbuttontextcolor"])
        val count = labels.size.coerceAtLeast(actions.size)
        val flags = splitBoolsFromInts(map["extraeventsbuttonsflags"])

        val buttons = (0 until count).map { i ->
            AsetCustomButton(
                label = labels.getOrElse(i) { "" },
                description = descriptions.getOrElse(i) { "" },
                eventType = types.getOrElse(i) { 0 },
                eventValue = values.getOrElse(i) { 0 },
                actionType = actions.getOrElse(i) { 0 },
                actionString = actionStrings.getOrElse(i) { "" },
                visible = (visibilities.getOrElse(i) { 1 }) != 0,
                backgroundColor = bgColors.getOrElse(i) { "#808080" }.ifBlank { "#808080" },
                textColor = textColors.getOrElse(i) { "white" }.ifBlank { "white" }
            )
        }
        return AsetEventButtons(
            buttons = buttons,
            buttonSize = map["buttonsize"]?.toIntOrNull() ?: 1,
            markLastPressed = map["marklastbuttonpressed"]?.toBooleanStrictOrNull() ?: true,
            showTooltips = map["showextrabuttontooltips"]?.toBooleanStrictOrNull() ?: false,
            buttonsFlags = flags.takeIf { it.isNotEmpty() } ?: listOf(true, true, true)
        )
    }

    private fun parseDefaultButtons(map: Map<String, String>): AsetDefaultButtons {
        return AsetDefaultButtons(
            actions = splitInts(map["buttonactions"]),
            actionStrings = splitQuotedCsv(map["buttonactionstrings"]),
            visibility = splitBools(map["buttonvisibility"])
        )
    }

    private fun parseQuantifiers(map: Map<String, String>): AsetQuantifiers {
        return AsetQuantifiers(
            sources = splitInts(map["quantifiersource"]).takeIf { it.isNotEmpty() } ?: listOf(0, 0, 0, 0),
            mins = splitInts(map["quantifiermin"]).takeIf { it.isNotEmpty() } ?: listOf(0, 0, 0, 0),
            maxs = splitInts(map["quantifiermax"]).takeIf { it.isNotEmpty() } ?: listOf(100, 100, 100, 100),
            active = splitBoolsFromInts(map["quantifieractive"]).takeIf { it.isNotEmpty() } ?: listOf(false, false, false, false),
            coarse = splitBoolsFromInts(map["quantifiercoarse"]).takeIf { it.isNotEmpty() } ?: listOf(false, false, false, false),
            clusterEvents = map["clusterEventsFlag"]?.toBooleanStrictOrNull() ?: false
        )
    }

    private fun parsePid(map: Map<String, String>): AsetPid {
        return AsetPid(
            kp = map["pidKp"]?.toDoubleOrNull() ?: 20.0,
            ki = map["pidKi"]?.toDoubleOrNull() ?: 0.01,
            kd = map["pidKd"]?.toDoubleOrNull() ?: 3.0,
            cycle = map["pidCycle"]?.toIntOrNull() ?: 1000,
            source = map["pidSource"]?.toIntOrNull() ?: 1,
            svValue = map["svValue"]?.toDoubleOrNull() ?: 0.0,
            svMode = map["svMode"]?.toIntOrNull() ?: 0,
            positiveTarget = map["pidPositiveTarget"]?.toIntOrNull() ?: 0,
            negativeTarget = map["pidNegativeTarget"]?.toIntOrNull() ?: 0,
            dutyMin = map["dutyMin"]?.toIntOrNull() ?: -100,
            dutyMax = map["dutyMax"]?.toIntOrNull() ?: 100,
            dutySteps = map["dutySteps"]?.toIntOrNull() ?: 1,
            invertControl = map["invertControl"]?.toBooleanStrictOrNull() ?: false,
            pidOnCharge = map["pidOnCHARGE"]?.toBooleanStrictOrNull() ?: false,
            svSlider = map["svSlider"]?.toBooleanStrictOrNull() ?: false,
            svSliderMin = map["svSliderMin"]?.toIntOrNull() ?: 0,
            svSliderMax = map["svSliderMax"]?.toIntOrNull() ?: 480,
            svButtons = map["svButtons"]?.toBooleanStrictOrNull() ?: false,
            pOnE = map["pOnE"]?.toBooleanStrictOrNull() ?: true
        )
    }

    private fun parseEvents(map: Map<String, String>): AsetEvents {
        val etypes = splitCsv(map["etypes"]).takeIf { it.isNotEmpty() }
            ?: listOf("", "", "", "", "")
        val defaultEtypesSet = splitInts(map["default_etypes_set"]).takeIf { it.size >= 5 }
            ?: listOf(0, 0, 0, 0, 0)
        return AsetEvents(
            etypes = etypes,
            defaultEtypesSet = defaultEtypesSet,
            autoCharge = map["autoCharge"]?.toBooleanStrictOrNull() ?: false,
            autoDrop = map["autoDrop"]?.toBooleanStrictOrNull() ?: false
        )
    }

    // --- Utility parsing helpers ---

    private fun unquote(s: String): String {
        val t = s.trim()
        if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\""))
            return t.substring(1, t.length - 1)
        return t
    }

    private fun splitCsv(s: String?): List<String> {
        if (s.isNullOrBlank() || s.startsWith("@Variant")) return emptyList()
        return s.split(",").map { it.trim() }
    }

    private fun splitInts(s: String?): List<Int> {
        if (s.isNullOrBlank() || s.startsWith("@Variant")) return emptyList()
        return s.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun splitPlainInts(s: String?): List<Int> {
        if (s.isNullOrBlank() || s.startsWith("@Variant")) return emptyList()
        return s.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun splitDoubles(s: String?): List<Double> {
        if (s.isNullOrBlank() || s.startsWith("@Variant")) return emptyList()
        return s.split(",").mapNotNull { it.trim().toDoubleOrNull() }
    }

    private fun splitBools(s: String?): List<Boolean> {
        if (s.isNullOrBlank() || s.startsWith("@Variant")) return emptyList()
        return s.split(",").map { it.trim().equals("true", ignoreCase = true) }
    }

    private fun splitBoolsFromInts(s: String?): List<Boolean> {
        if (s.isNullOrBlank() || s.startsWith("@Variant")) return emptyList()
        return s.split(",").map { it.trim() != "0" }
    }

    private fun splitQuotedCsv(s: String?): List<String> {
        if (s.isNullOrBlank() || s.startsWith("@Variant")) return emptyList()
        val result = mutableListOf<String>()
        var inQuote = false
        val current = StringBuilder()
        for (ch in s) {
            when {
                ch == '"' -> inQuote = !inQuote
                ch == ',' && !inQuote -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
