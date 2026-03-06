package com.rdr.roast.app

import com.rdr.roast.domain.BetweenBatchLog
import com.rdr.roast.domain.ControlEvent
import com.rdr.roast.domain.ControlEventType
import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.ProtocolComment
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.RoastEvent
import com.rdr.roast.domain.TemperatureUnit
import java.nio.file.Files
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import java.util.Locale

object ProfileStorage {

    /**
     * Loads a profile from an Artisan .alog file (Python dict with timex, temp1, temp2).
     * Artisan convention: temp1 = ET, temp2 = BT. We store as domain: temp1 = BT, temp2 = ET.
     */
    fun loadProfile(filePath: Path): RoastProfile {
        val content = Files.readString(filePath, Charsets.UTF_8)
        return parseAlogContent(content)
    }

    /**
     * Parses .alog content (single-line Python dict repr) into RoastProfile.
     * Artisan: temp1 = ET, temp2 = BT; timex in seconds.
     * timeindex[0]=charge, [1]=DRY END, [2]=FCs, [3]=FCe, [4]=SCs, [5]=SCe, [6]=drop, [7]=COOL.
     */
    fun parseAlogContent(content: String): RoastProfile {
        val timex = parseDoubleList(content, "timex")
        val temp1Alog = parseDoubleList(content, "temp1")  // ET in Artisan
        val temp2Alog = parseDoubleList(content, "temp2")  // BT in Artisan
        val timeindex = parseIntList(content, "timeindex")
        val mode = parseMode(content)

        val n = minOf(timex.size, temp1Alog.size, temp2Alog.size)
        if (n == 0) return RoastProfile(mode = mode)

        val timexList = timex.take(n).toMutableList()
        val btList = temp2Alog.take(n).toMutableList()   // domain temp1 = BT
        val etList = temp1Alog.take(n).toMutableList()   // domain temp2 = ET

        val events = mutableListOf<RoastEvent>()
        if (timeindex.isNotEmpty() && timexList.isNotEmpty()) {
            addEventFromTimeindex(events, timeindex, timexList, btList, etList, 0, EventType.CHARGE)
            addEventFromTimeindex(events, timeindex, timexList, btList, etList, 1, EventType.CC)   // Dry End
            addEventFromTimeindex(events, timeindex, timexList, btList, etList, 2, EventType.FC)   // FCs
            addEventFromTimeindex(events, timeindex, timexList, btList, etList, 6, EventType.DROP)
            addTpEventIfMissing(events, timexList, btList, etList)
            events.sortBy { it.timeSec }
        }

        val bbp = parseBbp(content, mode)
        val controlEvents = parseControlEvents(content, timexList)
        val comments = parseComments(content, "rdr_comment")
        return RoastProfile(
            timex = timexList,
            temp1 = btList,
            temp2 = etList,
            events = events,
            comments = comments.toMutableList(),
            controlEvents = controlEvents,
            mode = mode,
            betweenBatchLog = bbp
        )
    }

    /** Artisan .alog: specialevents = indices into timex, specialeventstype/etypes = type index.
     * specialeventsvalue = value; specialeventsStrings = display strings (e.g. '20mbar', 'Pilot') shown on chart. */
    private fun parseControlEvents(content: String, timex: List<Double>): List<ControlEvent> {
        val indices = parseIntList(content, "specialevents")
        val types = parseIntList(content, "specialeventstype").ifEmpty { parseIntList(content, "etypes") }
        val values = parseDoubleList(content, "specialeventsvalue")
        val strings = parseStringList(content, "specialeventsStrings")
        if (indices.isEmpty() || timex.isEmpty()) return emptyList()
        val typeMap = listOf(ControlEventType.AIR, ControlEventType.DRUM, ControlEventType.DAMPER, ControlEventType.GAS)
        return indices.mapIndexed { i, _ ->
            val idx = indices[i].coerceIn(0, timex.size - 1)
            val timeSec = timex[idx]
            val typeInt = types.getOrNull(i)?.coerceIn(0, 3) ?: 0
            val value = values.getOrNull(i) ?: 0.0
            val displayStr = strings.getOrNull(i)?.takeIf { it.isNotBlank() }
            ControlEvent(timeSec, typeMap[typeInt], value, displayStr)
        }.sortedBy { it.timeSec }
    }

    /** Parses Python list of strings from .alog, e.g. ['20mbar', '0', None, '15mbar']. */
    private fun parseStringList(content: String, key: String): List<String> {
        val start = indexOfKey(content, key)
        if (start < 0) return emptyList()
        val bracket = content.indexOf('[', start)
        if (bracket < 0) return emptyList()
        val end = findMatchingBracket(content, bracket)
        if (end < 0) return emptyList()
        val inner = content.substring(bracket + 1, end)
        return parseStringListInner(inner)
    }

    private fun parseStringListInner(inner: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < inner.length) {
            while (i < inner.length && (inner[i].isWhitespace() || inner[i] == ',')) i++
            if (i >= inner.length) break
            val s = when (inner[i]) {
                '\'' -> {
                    val start = i + 1
                    val end = inner.indexOf('\'', start)
                    if (end < 0) { i = inner.length; "" }
                    else {
                        i = end + 1
                        inner.substring(start, end).replace("\\'", "'")
                    }
                }
                '"' -> {
                    val start = i + 1
                    var e = start
                    while (e < inner.length) {
                        val q = inner.indexOf('"', e)
                        if (q < 0) break
                        if (inner.getOrNull(q - 1) != '\\') { e = q; break }
                        e = q + 1
                    }
                    i = if (e < inner.length) e + 1 else inner.length
                    if (e > start) inner.substring(start, e).replace("\\\"", "\"") else ""
                }
                else -> {
                    if (inner.substring(i).trimStart().startsWith("None")) {
                        i += 4
                        ""
                    } else {
                        val comma = inner.indexOf(',', i).let { if (it < 0) inner.length else it }
                        val token = inner.substring(i, comma).trim()
                        i = comma
                        if (token == "None" || token == "null") "" else token
                    }
                }
            }
            result.add(s)
        }
        return result
    }

    private fun parseOptionalDoubleList(content: String, key: String): List<Double?> {
        val start = indexOfKey(content, key)
        if (start < 0) return emptyList()
        val bracket = content.indexOf('[', start)
        if (bracket < 0) return emptyList()
        val end = findMatchingBracket(content, bracket)
        if (end < 0) return emptyList()
        val inner = content.substring(bracket + 1, end)
        return inner.split(',').map { token ->
            val trimmed = token.trim()
            when {
                trimmed.isEmpty() -> null
                trimmed == "None" || trimmed == "null" -> null
                else -> trimmed.toDoubleOrNull()
            }
        }
    }

    private fun parseComments(content: String, prefix: String): List<ProtocolComment> {
        val times = parseDoubleList(content, "${prefix}_times")
        val texts = parseStringList(content, "${prefix}_texts")
        val temps = parseOptionalDoubleList(content, "${prefix}_temps")
        val gas = parseOptionalDoubleList(content, "${prefix}_gas")
        val airflow = parseOptionalDoubleList(content, "${prefix}_airflow")
        val count = listOf(times.size, texts.size, temps.size, gas.size, airflow.size)
            .filter { it > 0 }
            .minOrNull() ?: 0
        if (count == 0) return emptyList()
        return (0 until count).map { i ->
            ProtocolComment(
                timeSec = times[i],
                text = texts.getOrElse(i) { "" },
                tempBT = temps.getOrNull(i),
                gas = gas.getOrNull(i),
                airflow = airflow.getOrNull(i)
            )
        }.filter { it.text.isNotBlank() || it.gas != null || it.airflow != null }
            .sortedBy { it.timeSec }
    }

    private fun parseBbp(content: String, mode: TemperatureUnit): BetweenBatchLog? {
        val startMs = parseLong(content, "bbp_start_epoch_ms") ?: return null
        val durationMs = parseLong(content, "bbp_duration_ms") ?: return null
        val timex = parseDoubleList(content, "bbp_timex")
        val temp1 = parseDoubleList(content, "bbp_temp1")
        val temp2 = parseDoubleList(content, "bbp_temp2")
        if (timex.isEmpty() || temp1.size != timex.size || temp2.size != timex.size) return null
        val lowestMs = parseLong(content, "bbp_lowest_temp_time_ms")
        val highestMs = parseLong(content, "bbp_highest_temp_time_ms")
        val comments = parseComments(content, "bbp_comment")
        return BetweenBatchLog(
            startEpochMs = startMs,
            durationMs = durationMs,
            timex = timex,
            temp1 = temp1,
            temp2 = temp2,
            mode = mode,
            lowestTemperatureTimeMs = lowestMs,
            highestTemperatureTimeMs = highestMs,
            comments = comments
        )
    }

    private fun parseLong(content: String, key: String): Long? {
        val start = indexOfKey(content, key)
        if (start < 0) return null
        val after = content.substring(start).let { s ->
            val colon = s.indexOf(':', 4)
            if (colon < 0) return@let null
            s.substring(colon + 1).trim().trimStart(',').split(',', ' ', '}').firstOrNull()?.trim()
        } ?: return null
        return after.toLongOrNull()
    }

    /** Finds key in Python dict repr. Prefer "'key':" so we match 'timex' not 'timeindex' or 'extratimex'. */
    private fun indexOfKey(content: String, key: String): Int {
        val single = "'$key':"
        val double = "\"$key\":"
        var pos = content.indexOf(single)
        if (pos >= 0) return pos
        pos = content.indexOf(double)
        if (pos >= 0) return pos
        pos = content.indexOf("'$key'")
        if (pos >= 0) {
            val after = content.getOrNull(pos + key.length + 2)
            if (after == ':' || after == ' ') return pos
        }
        return content.indexOf("\"$key\"").coerceAtLeast(-1)
    }

    private fun parseMode(content: String): TemperatureUnit {
        val start = indexOfKey(content, "mode")
        if (start < 0) return TemperatureUnit.CELSIUS
        val after = content.substring(start).let { s ->
            val colon = s.indexOf(':', 4)
            if (colon < 0) return@let ""
            s.substring(colon).drop(1).trim()
        }
        return when {
            after.startsWith("'F'") || after.startsWith("\"F\"") -> TemperatureUnit.FAHRENHEIT
            else -> TemperatureUnit.CELSIUS
        }
    }

    private fun parseDoubleList(content: String, key: String): List<Double> {
        val start = indexOfKey(content, key)
        if (start < 0) return emptyList()
        val bracket = content.indexOf('[', start)
        if (bracket < 0) return emptyList()
        val end = findMatchingBracket(content, bracket)
        if (end < 0) return emptyList()
        val inner = content.substring(bracket + 1, end)
        return parseDoubleListInner(inner)
    }

    /** Parses comma-separated numbers (handles -1, floats, scientific notation). */
    private fun parseDoubleListInner(inner: String): List<Double> =
        inner.split(',').mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() }?.toDoubleOrNull() }

    private fun parseIntList(content: String, key: String): List<Int> {
        val start = indexOfKey(content, key)
        if (start < 0) return emptyList()
        val bracket = content.indexOf('[', start)
        if (bracket < 0) return emptyList()
        val end = findMatchingBracket(content, bracket)
        if (end < 0) return emptyList()
        val inner = content.substring(bracket + 1, end)
        return inner.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    private fun findMatchingBracket(s: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until s.length) {
            when (s[i]) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }

    /** Adds one event from timeindex[slot] if index is valid; Artisan uses -1 for missing. */
    private fun addEventFromTimeindex(
        events: MutableList<RoastEvent>,
        timeindex: List<Int>,
        timex: List<Double>,
        bt: List<Double>,
        et: List<Double>,
        slot: Int,
        type: EventType
    ) {
        val idx = timeindex.getOrNull(slot)?.takeIf { it in timex.indices } ?: return
        events.add(RoastEvent(timex[idx], type, bt.getOrNull(idx), et.getOrNull(idx)))
    }

    /** Computes TP (min BT in first 180s after charge) and adds it if charge exists and TP is missing. */
    private fun addTpEventIfMissing(
        events: MutableList<RoastEvent>,
        timex: List<Double>,
        bt: List<Double>,
        et: List<Double>
    ) {
        if (events.none { it.type == EventType.TP } && events.any { it.type == EventType.CHARGE }) {
            val chargeSec = events.firstOrNull { it.type == EventType.CHARGE }?.timeSec ?: return
            val windowEnd = chargeSec + 180.0
            var minBt = Double.MAX_VALUE
            var bestIdx = -1
            for (i in timex.indices) {
                if (timex[i] < chargeSec) continue
                if (timex[i] > windowEnd) break
                val b = bt.getOrNull(i) ?: continue
                if (b < minBt) {
                    minBt = b
                    bestIdx = i
                }
            }
            if (bestIdx >= 0) {
                events.add(RoastEvent(timex[bestIdx], EventType.TP, bt.getOrNull(bestIdx), et.getOrNull(bestIdx)))
            }
        }
    }

    /**
     * Builds RoastProfile from server JSON (e.g. /roasts/{id}/profile/data).
     * Expects timex, temp1 (ET), temp2 (BT), optional timeindex, mode.
     */
    fun profileFromServerJson(json: Map<String, Any?>): RoastProfile {
        @Suppress("UNCHECKED_CAST")
        val timexRaw = json["timex"] as? List<Number> ?: emptyList<Number>()
        @Suppress("UNCHECKED_CAST")
        val temp1Raw = json["temp1"] as? List<Number> ?: emptyList<Number>()  // ET
        @Suppress("UNCHECKED_CAST")
        val temp2Raw = json["temp2"] as? List<Number> ?: emptyList<Number>()  // BT
        @Suppress("UNCHECKED_CAST")
        val timeindexRaw = json["timeindex"] as? List<Int> ?: emptyList<Int>()
        val modeStr = (json["mode"] as? String)?.uppercase() ?: "C"
        val mode = if (modeStr == "F") TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS

        val timex = timexRaw.map { it.toDouble() }.toMutableList()
        val etList = temp1Raw.map { it.toDouble() }.toMutableList()
        val btList = temp2Raw.map { it.toDouble() }.toMutableList()
        val n = minOf(timex.size, btList.size, etList.size)
        if (n == 0) return RoastProfile(mode = mode)
        val timexList = timex.take(n).toMutableList()
        val bt = btList.take(n).toMutableList()
        val et = etList.take(n).toMutableList()

        val events = mutableListOf<RoastEvent>()
        val timeindex = timeindexRaw.take(8)
        if (timeindex.isNotEmpty() && timexList.isNotEmpty()) {
            addEventFromTimeindex(events, timeindex, timexList, bt, et, 0, EventType.CHARGE)
            addEventFromTimeindex(events, timeindex, timexList, bt, et, 1, EventType.CC)
            addEventFromTimeindex(events, timeindex, timexList, bt, et, 2, EventType.FC)
            addEventFromTimeindex(events, timeindex, timexList, bt, et, 6, EventType.DROP)
            addTpEventIfMissing(events, timexList, bt, et)
            events.sortBy { it.timeSec }
        }

        val controlEvents = parseControlEventsFromServerJson(json, timexList)

        return RoastProfile(timex = timexList, temp1 = bt, temp2 = et, events = events, controlEvents = controlEvents, mode = mode)
    }

    /** Parse specialevents / specialeventstype (or etypes) / specialeventsvalue from server profile JSON. */
    private fun parseControlEventsFromServerJson(json: Map<String, Any?>, timex: List<Double>): List<ControlEvent> {
        @Suppress("UNCHECKED_CAST")
        val indicesRaw = json["specialevents"] as? List<Number> ?: json["specialevents"] as? List<Int> ?: emptyList<Number>()
        @Suppress("UNCHECKED_CAST")
        val typesRaw = (json["specialeventstype"] as? List<Number>)?.map { it.toInt() }
            ?: (json["etypes"] as? List<Number>)?.map { it.toInt() }
            ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val valuesRaw = (json["specialeventsvalue"] as? List<Number>)?.map { it.toDouble() } ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val stringsRaw = (json["specialeventsStrings"] as? List<*>)?.mapNotNull { it?.toString()?.takeIf { s -> s != "null" } } ?: emptyList()
        val indices = indicesRaw.map { it.toInt() }
        if (indices.isEmpty() || timex.isEmpty()) return emptyList()
        val typeMap = listOf(ControlEventType.AIR, ControlEventType.DRUM, ControlEventType.DAMPER, ControlEventType.GAS)
        return indices.mapIndexed { i, idx ->
            val timeSec = timex.getOrNull(idx.coerceIn(0, timex.size - 1)) ?: 0.0
            val typeInt = typesRaw.getOrNull(i)?.coerceIn(0, 3) ?: 0
            val value = valuesRaw.getOrNull(i) ?: 0.0
            val displayStr = stringsRaw.getOrNull(i)?.takeIf { it.isNotBlank() }
            ControlEvent(timeSec, typeMap[typeInt], value, displayStr)
        }.sortedBy { it.timeSec }
    }

    /**
     * Serializes the profile as a Python dict repr string for Artisan .alog compatibility.
     * Writes to file with UTF-8 encoding.
     */
    fun saveProfile(profile: RoastProfile, filePath: Path) {
        val dict = buildAlogDict(profile)
        Files.writeString(filePath, dict, Charsets.UTF_8)
    }

    /**
     * Returns a filename in format "yyyy-MM-dd_HHmm.alog" using current date/time.
     */
    fun generateFileName(): String {
        val now = java.time.LocalDateTime.now()
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".alog"
    }

    private fun buildAlogDict(profile: RoastProfile): String {
        val modeChar = when (profile.mode) {
            TemperatureUnit.CELSIUS -> 'C'
            TemperatureUnit.FAHRENHEIT -> 'F'
        }
        // Artisan: timex in seconds (keep precision); temp1 = ET, temp2 = BT.
        val timex = formatTimexList(profile.timex)
        val temp1 = formatTempList(profile.temp2)  // ET
        val temp2 = formatTempList(profile.temp1)  // BT

        val chargeIdx = profile.eventIndex(EventType.CHARGE).coerceAtLeast(-1)
        val deIdx = profile.eventIndex(EventType.CC).coerceAtLeast(-1)
        val fcIdx = profile.eventIndex(EventType.FC).coerceAtLeast(-1)
        val dropIdx = profile.eventIndex(EventType.DROP).coerceAtLeast(-1)
        val timeindex = listOf(chargeIdx, deIdx, fcIdx, -1, -1, -1, dropIdx, -1)
        val timeindexStr = formatIntList(timeindex)

        val now = java.time.LocalDateTime.now()
        val roastisodate = now.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val roasttime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        val bbpSuffix = profile.betweenBatchLog?.let { bbp ->
            val bbpTimex = formatTimexList(bbp.timex)
            val bbpTemp1 = formatTempList(bbp.temp1)
            val bbpTemp2 = formatTempList(bbp.temp2)
            val opt = listOfNotNull(
                bbp.lowestTemperatureTimeMs?.let { "'bbp_lowest_temp_time_ms': $it" },
                bbp.highestTemperatureTimeMs?.let { "'bbp_highest_temp_time_ms': $it" }
            ).joinToString(", ")
            val comments = formatComments("bbp_comment", bbp.comments)
            val optSuffix = if (opt.isNotEmpty()) ", $opt" else ""
            ", 'bbp_start_epoch_ms': ${bbp.startEpochMs}, 'bbp_duration_ms': ${bbp.durationMs}, " +
                "'bbp_timex': $bbpTimex, 'bbp_temp1': $bbpTemp1, 'bbp_temp2': $bbpTemp2" +
                optSuffix +
                comments
        } ?: ""
        val commentsSuffix = formatComments("rdr_comment", profile.comments)

        val specialEventsStr: String
        val specialEventstypeStr: String
        val specialEventsvalueStr: String
        val specialEventsStringsStr: String
        if (profile.controlEvents.isEmpty()) {
            specialEventsStr = "[]"
            specialEventstypeStr = "[]"
            specialEventsvalueStr = "[]"
            specialEventsStringsStr = "[]"
        } else {
            val timexList = profile.timex
            val indices = profile.controlEvents.map { ce ->
                if (timexList.isEmpty()) 0
                else timexList.withIndex().minByOrNull { (_, t) -> kotlin.math.abs(t - ce.timeSec) }?.index ?: 0
            }
            val types = profile.controlEvents.map { it.type.ordinal }
            val values = profile.controlEvents.map { it.value }
            val strings = profile.controlEvents.map { ce -> ce.displayString?.let { "'${it.replace("'", "\\'")}'" } ?: "None" }
            specialEventsStr = formatIntList(indices)
            specialEventstypeStr = formatIntList(types)
            specialEventsvalueStr = "[" + values.joinToString(", ") { String.format(Locale.US, "%.1f", it) } + "]"
            specialEventsStringsStr = "[" + strings.joinToString(", ") + "]"
        }

        // Artisan 4.x–compatible minimal dict: required keys so Artisan can open the file.
        return (
            "{'recording_version': '4.0.1', 'version': '4.0.1', 'mode': '$modeChar', " +
            "'timex': $timex, 'temp1': $temp1, 'temp2': $temp2, " +
            "'timeindex': $timeindexStr, " +
            "'roastisodate': '$roastisodate', 'roasttime': '$roasttime', " +
            "'specialevents': $specialEventsStr, 'specialeventstype': $specialEventstypeStr, 'specialeventsvalue': $specialEventsvalueStr, 'specialeventsStrings': $specialEventsStringsStr, " +
            "'extratimex': [], 'extratemp1': [], 'extratemp2': []" +
            commentsSuffix +
            bbpSuffix + "}"
        )
    }

    /** Time axis: seconds; always use US locale so decimal is period (Artisan expects 0.5 not 0,5). */
    private fun formatTimexList(list: List<Double>): String =
        "[" + list.joinToString(", ") { String.format(Locale.US, "%.6f", it) } + "]"

    /** Temperatures: 1 decimal; -1 kept as invalid placeholder. */
    private fun formatTempList(list: List<Double>): String =
        "[" + list.joinToString(", ") { if (it < 0) "-1" else String.format(Locale.US, "%.1f", it) } + "]"

    private fun formatIntList(list: List<Int>): String =
        "[" + list.joinToString(", ") { it.toString() } + "]"

    private fun formatOptionalDoubleList(list: List<Double?>): String =
        "[" + list.joinToString(", ") { value ->
            value?.let { String.format(Locale.US, "%.6f", it) } ?: "None"
        } + "]"

    private fun formatStringList(list: List<String>): String =
        "[" + list.joinToString(", ") { value -> "'${value.replace("'", "\\'")}'" } + "]"

    private fun formatComments(prefix: String, comments: List<ProtocolComment>): String {
        if (comments.isEmpty()) return ""
        val times = formatTimexList(comments.map { it.timeSec })
        val texts = formatStringList(comments.map { it.text })
        val temps = formatOptionalDoubleList(comments.map { it.tempBT })
        val gas = formatOptionalDoubleList(comments.map { it.gas })
        val airflow = formatOptionalDoubleList(comments.map { it.airflow })
        return ", '${prefix}_times': $times, '${prefix}_texts': $texts, '${prefix}_temps': $temps, '${prefix}_gas': $gas, '${prefix}_airflow': $airflow"
    }
}
