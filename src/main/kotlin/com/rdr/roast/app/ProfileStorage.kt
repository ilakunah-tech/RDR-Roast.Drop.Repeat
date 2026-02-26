package com.rdr.roast.app

import com.rdr.roast.domain.EventType
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
            addEventFromTimeindex(events, timeindex, timexList, btList, etList, 1, EventType.DE)   // Dry End
            addEventFromTimeindex(events, timeindex, timexList, btList, etList, 2, EventType.FC)   // FCs
            addEventFromTimeindex(events, timeindex, timexList, btList, etList, 6, EventType.DROP)
            addTpEventIfMissing(events, timexList, btList, etList)
            events.sortBy { it.timeSec }
        }

        return RoastProfile(
            timex = timexList,
            temp1 = btList,
            temp2 = etList,
            events = events,
            mode = mode
        )
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
            addEventFromTimeindex(events, timeindex, timexList, bt, et, 1, EventType.DE)
            addEventFromTimeindex(events, timeindex, timexList, bt, et, 2, EventType.FC)
            addEventFromTimeindex(events, timeindex, timexList, bt, et, 6, EventType.DROP)
            addTpEventIfMissing(events, timexList, bt, et)
            events.sortBy { it.timeSec }
        }

        return RoastProfile(timex = timexList, temp1 = bt, temp2 = et, events = events, mode = mode)
    }

    /**
     * Serializes the profile as a Python dict repr string for Artisan .alog compatibility.
     * Writes to file with UTF-8 encoding.
     */
    fun saveProfile(profile: RoastProfile, filePath: Path) {
        // #region agent log
        val profRef = System.identityHashCode(profile)
        java.io.File("/opt/cursor/logs/debug.log").appendText("""{"id":"log_saveProfile","timestamp":${System.currentTimeMillis()},"location":"ProfileStorage.kt:saveProfile","message":"before buildAlogDict","data":{"profRef":$profRef,"timexSize":${profile.timex.size},"temp1Size":${profile.temp1.size},"temp2Size":${profile.temp2.size},"eventsSize":${profile.events.size},"filePath":"$filePath"},"hypothesisId":"E"}""" + "\n")
        // #endregion
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
        val deIdx = (profile.eventIndex(EventType.DE).takeIf { it >= 0 }
            ?: profile.eventIndex(EventType.CC)).coerceAtLeast(-1)
        val fcIdx = profile.eventIndex(EventType.FC).coerceAtLeast(-1)
        val dropIdx = profile.eventIndex(EventType.DROP).coerceAtLeast(-1)
        val timeindex = listOf(chargeIdx, deIdx, fcIdx, -1, -1, -1, dropIdx, -1)
        val timeindexStr = formatIntList(timeindex)

        val now = java.time.LocalDateTime.now()
        val roastisodate = now.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val roasttime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        // Artisan 4.x–compatible minimal dict: required keys so Artisan can open the file.
        return (
            "{'recording_version': '4.0.1', 'version': '4.0.1', 'mode': '$modeChar', " +
            "'timex': $timex, 'temp1': $temp1, 'temp2': $temp2, " +
            "'timeindex': $timeindexStr, " +
            "'roastisodate': '$roastisodate', 'roasttime': '$roasttime', " +
            "'specialevents': [], 'specialeventstype': [], 'specialeventsvalue': [], 'specialeventsStrings': [], " +
            "'extratimex': [], 'extratemp1': [], 'extratemp2': []}"
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
}
