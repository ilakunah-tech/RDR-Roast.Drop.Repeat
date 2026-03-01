package com.rdr.roast.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

/**
 * Server API: auth (login, refresh), upload/sync roasts, references (see ReferenceApi).
 * Used for full connect with test-server-qqplus.
 * Future: refresh token on 401, offline queue for upload retries, WebSocket /ws/notifications.
 */
object ServerApi {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val objectMapper = ObjectMapper().registerKotlinModule()

    data class LoginResult(
        val token: String,
        val refreshToken: String?,
        val userId: String?,
        val email: String?,
        val username: String?
    )

    /** Schedule list item (GET /schedule). */
    data class ScheduleItem(
        val id: String,
        val title: String,
        val scheduled_date: String,
        val scheduled_weight_kg: Double?,
        val status: String,
        val coffee_id: String?,
        val blend_id: String?,
        val batch_id: String?,
        val machine_id: String?,
        val notes: String?
    )

    /** Response of GET /schedule: data.items, data.total. */
    data class ScheduleListResult(
        val data: Map<String, Any?>
    )

    /**
     * POST {baseUrl}/auth/login with email and password.
     * Returns LoginResult on success; throws on 4xx or network error.
     */
    fun login(baseUrl: String, email: String, password: String, remember: Boolean = true): LoginResult {
        val path = "$baseUrl/auth/login"
        val body = mapOf(
            "email" to email,
            "password" to password,
            "remember" to remember
        )
        val json = objectMapper.writeValueAsString(body)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(json, Charsets.UTF_8))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val msg = parseErrorBody(response.body())
            throw ServerApiException(response.statusCode(), msg)
        }
        @Suppress("UNCHECKED_CAST")
        val map = objectMapper.readValue<Map<String, Any?>>(response.body())
        val data = map["data"] as? Map<String, Any?> ?: throw ServerApiException(response.statusCode(), "No data in response")
        val token = data["token"]?.toString() ?: throw ServerApiException(response.statusCode(), "No token")
        val refreshToken = data["refresh_token"]?.toString()
        val userId = data["user_id"]?.toString() ?: data["id"]?.toString()
        val emailRes = data["email"]?.toString()
        val username = data["username"]?.toString()
        return LoginResult(token = token, refreshToken = refreshToken, userId = userId, email = emailRes, username = username)
    }

    /**
     * POST {baseUrl}/auth/refresh with refresh_token. Returns new access token.
     */
    fun refresh(baseUrl: String, refreshToken: String): String {
        val path = "$baseUrl/auth/refresh"
        val body = mapOf("refresh_token" to refreshToken)
        val json = objectMapper.writeValueAsString(body)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(json, Charsets.UTF_8))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val msg = parseErrorBody(response.body())
            throw ServerApiException(response.statusCode(), msg)
        }
        @Suppress("UNCHECKED_CAST")
        val map = objectMapper.readValue<Map<String, Any?>>(response.body())
        val data = map["data"] as? Map<String, Any?>
        val token = data?.get("token")?.toString() ?: throw ServerApiException(response.statusCode(), "No token in refresh response")
        return token
    }

    private fun parseErrorBody(body: String): String {
        if (body.isBlank()) return "Request failed"
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = objectMapper.readValue<Map<String, Any?>>(body)
            when (val detail = map["detail"]) {
                is String -> detail
                is List<*> -> (detail.firstOrNull() as? Map<*, *>)?.get("msg")?.toString() ?: body.take(200)
                else -> map["error"]?.toString() ?: body.take(200)
            }
        } catch (_: Exception) {
            body.take(200)
        }
    }

    /**
     * POST {baseUrl}/roasts/aroast with JSON body (Artisan Plus protocol).
     * Returns response body map on success; throws on 4xx/5xx or network error.
     */
    fun uploadRoast(baseUrl: String, token: String, body: Map<String, Any?>): Map<String, Any?> {
        val path = "$baseUrl/roasts/aroast"
        val json = objectMapper.writeValueAsString(body)
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .timeout(Duration.ofSeconds(25))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Idempotency-Key", (body["roast_id"]?.toString() ?: java.util.UUID.randomUUID().toString()).replace("-", ""))
            .POST(HttpRequest.BodyPublishers.ofString(json, Charsets.UTF_8))
        token.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val msg = parseErrorBody(response.body())
            throw ServerApiException(response.statusCode(), msg)
        }
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(response.body())
    }

    /**
     * GET {baseUrl}/roasts/aroast/{roastId} for sync (pull). Optional query modified_at (ms).
     * Returns 200 with body, 204 No Content if client has latest, 404 if not found.
     */
    fun getRoast(baseUrl: String, token: String, roastId: String, modifiedAtMs: Long? = null): Pair<Int, Map<String, Any?>?> {
        val path = buildString {
            append("$baseUrl/roasts/aroast/$roastId")
            if (modifiedAtMs != null) append("?modified_at=$modifiedAtMs")
        }
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .timeout(Duration.ofSeconds(15))
            .GET()
        token.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        return when (response.statusCode()) {
            204 -> 204 to emptyMap()
            200 -> {
                @Suppress("UNCHECKED_CAST")
                val body = objectMapper.readValue<Map<String, Any?>>(response.body())
                200 to body
            }
            else -> response.statusCode() to null
        }
    }

    /**
     * GET {baseUrl}/schedule with optional date_from, date_to, status (ISO date YYYY-MM-DD).
     * Returns list of ScheduleItem and total count; throws on error.
     */
    fun listSchedule(
        baseUrl: String,
        token: String,
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null,
        status: String? = null
    ): Pair<List<ScheduleItem>, Int> {
        val path = buildString {
            append("$baseUrl/schedule?limit=500")
            dateFrom?.let { append("&date_from=$it") }
            dateTo?.let { append("&date_to=$it") }
            status?.takeIf { it.isNotBlank() }?.let { append("&status=$it") }
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .header("Authorization", "Bearer $token")
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val msg = parseErrorBody(response.body())
            throw ServerApiException(response.statusCode(), msg)
        }
        @Suppress("UNCHECKED_CAST")
        val map = objectMapper.readValue<Map<String, Any?>>(response.body())
        val data = map["data"] as? Map<String, Any?> ?: return emptyList<ScheduleItem>() to 0
        val rawItems = data["items"] as? List<*>
        val items: List<ScheduleItem> = rawItems?.mapNotNull { raw: Any? ->
            @Suppress("UNCHECKED_CAST")
            val item = raw as? Map<String, Any?> ?: return@mapNotNull null
            ScheduleItem(
                id = item["id"]?.toString() ?: "",
                title = item["title"]?.toString() ?: "",
                scheduled_date = item["scheduled_date"]?.toString() ?: "",
                scheduled_weight_kg = (item["scheduled_weight_kg"] as? Number)?.toDouble(),
                status = item["status"]?.toString() ?: "pending",
                coffee_id = item["coffee_id"]?.toString(),
                blend_id = item["blend_id"]?.toString(),
                batch_id = item["batch_id"]?.toString(),
                machine_id = item["machine_id"]?.toString(),
                notes = item["notes"]?.toString()
            )
        } ?: emptyList()
        val total = (data["total"] as? Number)?.toInt() ?: items.size
        return items to total
    }

    /**
     * PUT {baseUrl}/schedule/{scheduleId}/complete with roast_id, optional roasted_weight_kg, notes.
     */
    fun completeSchedule(
        baseUrl: String,
        token: String,
        scheduleId: java.util.UUID,
        roastId: java.util.UUID,
        roastedWeightKg: Double? = null,
        notes: String? = null
    ) {
        val path = "$baseUrl/schedule/$scheduleId/complete"
        val body = mutableMapOf<String, Any?>(
            "roast_id" to roastId.toString()
        ).apply {
            roastedWeightKg?.let { put("roasted_weight_kg", it) }
            notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
        }
        val json = objectMapper.writeValueAsString(body)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Authorization", "Bearer $token")
            .PUT(HttpRequest.BodyPublishers.ofString(json, Charsets.UTF_8))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val msg = parseErrorBody(response.body())
            throw ServerApiException(response.statusCode(), msg)
        }
    }
}

/**
 * Builds the aroast JSON payload from profile and roast properties.
 * Artisan convention: temp1 = ET, temp2 = BT. RDR domain: temp1 = BT, temp2 = ET.
 */
fun buildAroastPayload(
    profile: com.rdr.roast.domain.RoastProfile,
    title: String,
    weightInKg: Double,
    weightOutKg: Double,
    notes: String,
    roastId: String,
    dateIso8601: String,
    coffeeId: String? = null,
    blendId: String? = null
): Map<String, Any?> {
    val chargeIdx = profile.eventIndex(com.rdr.roast.domain.EventType.CHARGE).coerceAtLeast(-1)
    val deIdx = profile.eventIndex(com.rdr.roast.domain.EventType.CC).coerceAtLeast(-1)
    val fcIdx = profile.eventIndex(com.rdr.roast.domain.EventType.FC).coerceAtLeast(-1)
    val dropIdx = profile.eventIndex(com.rdr.roast.domain.EventType.DROP).coerceAtLeast(-1)
    val timeindex = listOf(chargeIdx, deIdx, fcIdx, -1, -1, -1, dropIdx, -1)
    return mutableMapOf<String, Any?>(
        "roast_id" to roastId,
        "id" to roastId,
        "date" to dateIso8601,
        "label" to title,
        "amount" to weightInKg,
        "end_weight" to weightOutKg,
        "timex" to profile.timex,
        "temp1" to profile.temp2,
        "temp2" to profile.temp1,
        "timeindex" to timeindex
    ).apply {
        if (notes.isNotBlank()) put("notes", notes)
        coffeeId?.takeIf { it.isNotBlank() }?.let { put("coffee_id", it) }
        blendId?.takeIf { it.isNotBlank() }?.let { put("blend_id", it) }
    }
}

class ServerApiException(val statusCode: Int, message: String) : Exception(message)
