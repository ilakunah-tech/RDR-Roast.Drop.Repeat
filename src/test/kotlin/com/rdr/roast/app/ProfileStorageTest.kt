package com.rdr.roast.app

import com.rdr.roast.domain.BetweenBatchLog
import com.rdr.roast.domain.EventType
import com.rdr.roast.domain.ProtocolComment
import com.rdr.roast.domain.RoastProfile
import com.rdr.roast.domain.TemperatureUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class ProfileStorageTest {

    /** Minimal Artisan .alog line: timex in seconds, temp1=ET, temp2=BT, timeindex charge/drop. */
    private val minimalAlog = """
        {'mode': 'C', 'timex': [0.0, 30.5, 60.0, 90.0], 'temp1': [100.0, 120.0, 140.0, 160.0], 'temp2': [80.0, 100.0, 120.0, 140.0], 'timeindex': [0, -1, -1, -1, -1, -1, 3, -1]}
    """.trim()

    @Test
    fun parseAlogContent_minimal() {
        val profile = ProfileStorage.parseAlogContent(minimalAlog)
        assertEquals(4, profile.timex.size)
        assertEquals(0.0, profile.timex[0])
        assertEquals(60.0, profile.timex[2])
        // Domain: temp1=BT, temp2=ET
        assertEquals(80.0, profile.temp1[0])
        assertEquals(140.0, profile.temp2[2])
        assertEquals(TemperatureUnit.CELSIUS, profile.mode)
        val charge = profile.eventByType(EventType.CHARGE)
        val drop = profile.eventByType(EventType.DROP)
        assertTrue(charge != null)
        assertTrue(drop != null)
        assertEquals(0.0, charge!!.timeSec)
        assertEquals(90.0, drop!!.timeSec)
    }

    /** Same structure as buildAlogDict output (with extratimex etc.). */
    private val savedFormatAlog = """
        {'recording_version': '4.0.1', 'mode': 'C', 'timex': [0.000000, 30.500000, 60.000000, 90.000000], 'temp1': [100.0, 120.0, 140.0, 160.0], 'temp2': [80.0, 100.0, 120.0, 140.0], 'timeindex': [0, -1, -1, -1, -1, -1, 3, -1], 'extratimex': [], 'extratemp1': [], 'extratemp2': []}
    """.trim()

    @Test
    fun parseAlogContent_savedFormatWithExtratimex() {
        val profile = ProfileStorage.parseAlogContent(savedFormatAlog)
        assertEquals(4, profile.timex.size)
        assertEquals(0.0, profile.timex[0])
        assertEquals(90.0, profile.timex[3])
    }

    @Test
    fun saveAndReload_roundTrip() {
        val profile = ProfileStorage.parseAlogContent(minimalAlog)
        val tmp = Files.createTempFile("rdr_alog_", ".alog")
        try {
            ProfileStorage.saveProfile(profile, tmp)
            val content = Files.readString(tmp)
            assertTrue(content.contains("'timex':"))
            assertTrue(content.contains("'temp1':"))
            assertTrue(content.contains("'temp2':"))
            assertTrue(content.contains("'timeindex':"))
            assertTrue(content.contains("'mode': 'C'"))
            val reloaded = ProfileStorage.parseAlogContent(content)
            assertEquals(profile.timex.size, reloaded.timex.size)
            profile.timex.zip(reloaded.timex).forEach { (a, b) -> assertEquals(a, b, 1e-5) }
            profile.temp1.zip(reloaded.temp1).forEach { (a, b) -> assertEquals(a, b, 0.01) }
            profile.temp2.zip(reloaded.temp2).forEach { (a, b) -> assertEquals(a, b, 0.01) }
            assertEquals(profile.mode, reloaded.mode)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    @Test
    fun saveAndReload_roundTrip_preservesBbpComments() {
        val profile = RoastProfile(
            timex = mutableListOf(0.0, 10.0, 20.0),
            temp1 = mutableListOf(180.0, 170.0, 160.0),
            temp2 = mutableListOf(250.0, 245.0, 240.0),
            betweenBatchLog = BetweenBatchLog(
                startEpochMs = 1000L,
                durationMs = 20_000L,
                timex = listOf(0.0, 10.0, 20.0),
                temp1 = listOf(200.0, 180.0, 170.0),
                temp2 = listOf(300.0, 290.0, 280.0),
                mode = TemperatureUnit.CELSIUS,
                lowestTemperatureTimeMs = 20_000L,
                highestTemperatureTimeMs = 0L,
                comments = listOf(
                    ProtocolComment(timeSec = 10.0, text = "BBP note", tempBT = 180.0, gas = 5.0, airflow = 3.0)
                )
            )
        )
        val tmp = Files.createTempFile("rdr_bbp_", ".alog")
        try {
            ProfileStorage.saveProfile(profile, tmp)
            val content = Files.readString(tmp)
            assertTrue(content.contains("'bbp_comment_texts': ['BBP note']"))
            assertTrue(content.contains("'bbp_comment_gas': [5.000000]"))
            assertTrue(content.contains("'bbp_comment_airflow': [3.000000]"))
            val reloaded = ProfileStorage.parseAlogContent(content)
            val bbp = reloaded.betweenBatchLog
            assertTrue(bbp != null)
            assertEquals(1, bbp!!.comments.size)
            assertEquals("BBP note", bbp.comments.first().text)
            assertEquals(5.0, bbp.comments.first().gas)
            assertEquals(3.0, bbp.comments.first().airflow)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    /** Load Artisan .alog from project roasts-background (BESCA-style) and assert reference data is parseable. */
    @Test
    fun loadReferenceFromBackground_parsesNonEmptyCurves() {
        val dir = Paths.get("src", "test", "resources", "roasts-background")
        if (!Files.isDirectory(dir)) return
        val first = Files.list(dir).use { it.filter { p -> p.toString().endsWith(".alog") }.findFirst().orElse(null) }
            ?: return
        val content = Files.readString(first)
        val profile = ProfileStorage.parseAlogContent(content)
        assertTrue(profile.timex.isNotEmpty(), "timex should be non-empty for reference display: ${first.fileName}")
        assertTrue(profile.temp1.size == profile.timex.size, "temp1 size should match timex")
        assertTrue(profile.temp2.size == profile.timex.size, "temp2 size should match timex")
    }
}
