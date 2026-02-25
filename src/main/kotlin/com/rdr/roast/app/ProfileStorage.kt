package com.rdr.roast.app

import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.RoastProfile
import java.nio.file.Files
import java.nio.file.Path
import java.time.format.DateTimeFormatter

object ProfileStorage {

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
