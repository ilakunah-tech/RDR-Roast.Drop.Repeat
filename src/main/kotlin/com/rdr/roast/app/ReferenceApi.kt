package com.rdr.roast.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rdr.roast.domain.RoastProfile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * API client for the roast server (test-server-qqplus): list references and fetch profile data.
 */
object ReferenceApi {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val objectMapper = ObjectMapper().registerKotlinModule()

    /**
     * List reference roasts. GET /roasts/references
     * Optional query: coffee_id, blend_id, machine.
     */
    fun listReferences(
        baseUrl: String,
        token: String? = null,
        coffeeId: String? = null,
        machine: String? = null
    ): List<ReferenceItem> {
        val path = buildString {
            append("$baseUrl/roasts/references")
            val params = mutableListOf<String>()
            coffeeId?.let { params.add("coffee_id=$it") }
            machine?.let { params.add("machine=${java.net.URLEncoder.encode(it, Charsets.UTF_8)}") }
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        val request = newRequest(path, token)
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() != 200) return emptyList()
        val body = objectMapper.readValue<Map<String, Any?>>(response.body())
        @Suppress("UNCHECKED_CAST")
        val data = body["data"] as? Map<String, Any?> ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val items = data["items"] as? List<Map<String, Any?>> ?: return emptyList()
        return items.map { ref ->
            ReferenceItem(
                id = ref["id"]?.toString() ?: "",
                label = ref["label"]?.toString() ?: ref["reference_name"]?.toString() ?: "",
                referenceName = ref["reference_name"]?.toString(),
                roastedAt = ref["roasted_at"]?.toString()
            )
        }
    }

    /**
     * Fetch profile data (timex, temp1, temp2) for a roast. GET /roasts/{roast_id}/profile/data
     */
    fun getProfileData(baseUrl: String, roastId: String, token: String? = null): RoastProfile? {
        val path = "$baseUrl/roasts/${roastId}/profile/data"
        val request = newRequest(path, token)
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() != 200) return null
        @Suppress("UNCHECKED_CAST")
        val json = objectMapper.readValue<Map<String, Any?>>(response.body())
        return ProfileStorage.profileFromServerJson(json)
    }

    private fun newRequest(uri: String, token: String?): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(15))
            .GET()
        token?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return builder.build()
    }
}

data class ReferenceItem(
    val id: String,
    val label: String,
    val referenceName: String?,
    val roastedAt: String?
)
