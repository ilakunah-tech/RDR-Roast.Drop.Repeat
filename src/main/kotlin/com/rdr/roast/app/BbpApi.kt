package com.rdr.roast.app

import com.rdr.roast.domain.BetweenBatchLog
import com.rdr.roast.domain.TemperatureUnit
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object BbpApi {
    private val log = LoggerFactory.getLogger(BbpApi::class.java)
    private val client = HttpClient.newHttpClient()
    private val mapper = ObjectMapper().registerKotlinModule()

    fun uploadBbpData(baseUrl: String, token: String, roastId: String, bbpLog: BetweenBatchLog) {
        val payload = buildBbpPayload(bbpLog)
        val body = mapper.writeValueAsString(payload)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/roasts/$roastId/bbp-data"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            log.warn("BBP upload failed: {} {}", response.statusCode(), response.body().take(200))
        }
    }

    fun getBbpData(baseUrl: String, token: String?, roastId: String): BetweenBatchLog? {
        val reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/roasts/$roastId/bbp-data"))
            .GET()
        token?.let { reqBuilder.header("Authorization", "Bearer $it") }
        val response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null
        return parseBbpResponse(response.body())
    }

    data class BbpReference(val roastId: String, val label: String, val machine: String?)

    fun listBbpReferences(baseUrl: String, token: String): List<BbpReference> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/roasts/bbp-references"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            val list = mapper.readValue(response.body(), List::class.java) as? List<Map<String, Any?>> ?: emptyList()
            list.map { m ->
                BbpReference(
                    roastId = m["roast_id"]?.toString() ?: m["id"]?.toString() ?: "",
                    label = m["label"]?.toString() ?: "",
                    machine = m["bbp_reference_machine"]?.toString()
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to parse BBP references: {}", e.message)
            emptyList()
        }
    }

    private fun buildBbpPayload(bbpLog: BetweenBatchLog): Map<String, Any?> {
        val minTemp = if (bbpLog.temp1.isNotEmpty()) bbpLog.temp1.min() else null
        val maxTemp = if (bbpLog.temp1.isNotEmpty()) bbpLog.temp1.max() else null
        return mapOf(
            "bbp_timex" to bbpLog.timex,
            "bbp_temp1" to bbpLog.temp1,
            "bbp_temp2" to bbpLog.temp2,
            "bbp_min_temp" to minTemp,
            "bbp_max_temp" to maxTemp,
            "bbp_start_epoch_ms" to bbpLog.startEpochMs,
            "bbp_duration_ms" to bbpLog.durationMs,
            "bbp_gas_changes" to bbpLog.gasChanges,
            "bbp_air_changes" to bbpLog.airChanges
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBbpResponse(body: String): BetweenBatchLog? {
        return try {
            val map = mapper.readValue(body, Map::class.java) as Map<String, Any?>
            val timex = (map["bbp_timex"] as? List<Number>)?.map { it.toDouble() } ?: return null
            val temp1 = (map["bbp_temp1"] as? List<Number>)?.map { it.toDouble() } ?: return null
            val temp2 = (map["bbp_temp2"] as? List<Number>)?.map { it.toDouble() } ?: return null
            if (timex.isEmpty()) return null
            val startMs = (map["bbp_start_epoch_ms"] as? Number)?.toLong() ?: 0L
            val durationMs = (map["bbp_duration_ms"] as? Number)?.toLong() ?: ((timex.lastOrNull() ?: 0.0) * 1000).toLong()
            val minTemp = (map["bbp_min_temp"] as? Number)?.toDouble()
            val maxTemp = (map["bbp_max_temp"] as? Number)?.toDouble()
            val lowIdx = temp1.indices.minByOrNull { temp1[it] }
            val highIdx = temp1.indices.maxByOrNull { temp1[it] }
            val lowestMs = lowIdx?.let { (timex.getOrNull(it) ?: 0.0) * 1000 }?.toLong()
            val highestMs = highIdx?.let { (timex.getOrNull(it) ?: 0.0) * 1000 }?.toLong()
            val gasChanges = (map["bbp_gas_changes"] as? Number)?.toInt() ?: 0
            val airChanges = (map["bbp_air_changes"] as? Number)?.toInt() ?: 0
            BetweenBatchLog(
                startEpochMs = startMs,
                durationMs = durationMs,
                timex = timex,
                temp1 = temp1,
                temp2 = temp2,
                mode = TemperatureUnit.CELSIUS,
                lowestTemperatureTimeMs = lowestMs,
                highestTemperatureTimeMs = highestMs,
                gasChanges = gasChanges,
                airChanges = airChanges
            )
        } catch (e: Exception) {
            log.warn("Failed to parse BBP data: {}", e.message)
            null
        }
    }
}
