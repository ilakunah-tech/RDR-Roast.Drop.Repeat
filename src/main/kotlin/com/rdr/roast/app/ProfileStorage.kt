package com.rdr.roast.app

import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.RoastEvent
import com.rdr.roast.domain.TemperatureUnit
import java.nio.file.Files
import java.nio.file.Path
import java.time.format.DateTimeFormatter

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
     * Parses .alog content (single-line Python dict) into RoastProfile.
     */
    fun parseAlogContent(content: String): RoastProfile {
        val timex = parseDoubleList(content, "timex")
        val temp1Alog = parseDoubleList(content, "temp1")  // ET in Artisan
        val temp2Alog = parseDoubleList(content, "temp2")  // BT in Artisan
        val timeindex = parseIntList(content, "timeindex")
        val mode = if (content.contains("'mode': 'F'")) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS

        val n = minOf(timex.size, temp1Alog.size, temp2Alog.size)
        val timexList = timex.take(n).toMutableList()
        val btList = temp2Alog.take(n).toMutableList()   // domain temp1 = BT
        val etList = temp1Alog.take(n).toMutableList()   // domain temp2 = ET

        val events = mutableListOf<RoastEvent>()
        if (timeindex.size >= 7 && timexList.isNotEmpty()) {
            val chargeIdx = timeindex.getOrNull(0)?.takeIf { it in 0 until timexList.size }
            val dropIdx = timeindex.getOrNull(6)?.takeIf { it in 0 until timexList.size }
            chargeIdx?.let { events.add(RoastEvent(timexList[it], EventType.CHARGE, btList.getOrNull(it), etList.getOrNull(it))) }
            dropIdx?.let { events.add(RoastEvent(timexList[it], EventType.DROP, btList.getOrNull(it), etList.getOrNull(it))) }
        }

        return RoastProfile(
            timex = timexList,
            temp1 = btList,
            temp2 = etList,
            events = events,
            mode = mode
        )
    }

    private fun parseDoubleList(content: String, key: String): List<Double> {
        val start = content.indexOf("'$key'")
        if (start < 0) return emptyList()
        val bracket = content.indexOf('[', start)
        if (bracket < 0) return emptyList()
        val end = findMatchingBracket(content, bracket)
        if (end < 0) return emptyList()
        val inner = content.substring(bracket + 1, end)
        return inner.split(',').mapNotNull { it.trim().toDoubleOrNull() }
    }

    private fun parseIntList(content: String, key: String): List<Int> {
        val start = content.indexOf("'$key'")
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
        if (timeindex.size >= 7 && timexList.isNotEmpty()) {
            val chargeIdx = timeindex.getOrNull(0)?.takeIf { it in 0 until timexList.size }
            val dropIdx = timeindex.getOrNull(6)?.takeIf { it in 0 until timexList.size }
            chargeIdx?.let { events.add(RoastEvent(timexList[it], EventType.CHARGE, bt.getOrNull(it), et.getOrNull(it))) }
            dropIdx?.let { events.add(RoastEvent(timexList[it], EventType.DROP, bt.getOrNull(it), et.getOrNull(it))) }
        }

        return RoastProfile(timex = timexList, temp1 = bt, temp2 = et, events = events, mode = mode)
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
            com.rdr.roast.domain.TemperatureUnit.CELSIUS -> 'C'
            com.rdr.roast.domain.TemperatureUnit.FAHRENHEIT -> 'F'
        }
        val timex = formatList(profile.timex)
        // Artisan: temp1 = ET, temp2 = BT. Domain: temp1 = BT, temp2 = ET.
        val temp1 = formatList(profile.temp2)  // ET
        val temp2 = formatList(profile.temp1)  // BT

        val chargeIdx = profile.eventIndex(EventType.CHARGE)
        val dropIdx = profile.eventIndex(EventType.DROP)
        val timeindex = listOf(chargeIdx, 0, 0, 0, 0, 0, dropIdx, 0)
        val timeindexStr = formatIntList(timeindex)

        val now = java.time.LocalDateTime.now()
        val roastisodate = now.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val roasttime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        return """
            {'version': '0.1.0', 'mode': '$modeChar', 'timex': $timex, 'temp1': $temp1, 'temp2': $temp2, 'timeindex': $timeindexStr, 'roastisodate': '$roastisodate', 'roasttime': '$roasttime', 'specialevents': [], 'specialeventstype': [], 'specialeventsvalue': [], 'specialeventsStrings': []}
        """.trim()
    }

    private fun formatList(list: List<Double>): String {
        return "[" + list.joinToString(", ") { "%.2f".format(it) } + "]"
    }

    private fun formatIntList(list: List<Int>): String {
        return "[" + list.joinToString(", ") { it.toString() } + "]"
    }
}
