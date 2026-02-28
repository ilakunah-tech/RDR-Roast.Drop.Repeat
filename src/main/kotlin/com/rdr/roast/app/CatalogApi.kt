package com.rdr.roast.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches stock (coffees) and blends from GET /inventory/stock (Artisan-compatible).
 * Used by Roast Properties to fill Stock and Blend comboboxes.
 */
object CatalogApi {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val objectMapper = ObjectMapper().registerKotlinModule()

    data class StockItem(val id: String, val label: String)
    data class BlendItem(val id: String, val label: String)
    data class StockAndBlends(val coffees: List<StockItem>, val blends: List<BlendItem>)

    /**
     * GET {baseUrl}/inventory/stock. Returns coffees (stock) and blends from result.
     */
    fun getStockAndBlends(baseUrl: String, token: String? = null): StockAndBlends {
        val path = "$baseUrl/inventory/stock"
        val request = newRequest(path, token)
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() != 200) return StockAndBlends(emptyList(), emptyList())
        val body = objectMapper.readValue<Map<String, Any?>>(response.body())
        @Suppress("UNCHECKED_CAST")
        val result = body["result"] as? Map<String, Any?> ?: return StockAndBlends(emptyList(), emptyList())
        @Suppress("UNCHECKED_CAST")
        val coffeesRaw = result["coffees"] as? List<Map<String, Any?>> ?: emptyList()
        val coffees = coffeesRaw.map { c ->
            StockItem(
                id = c["id"]?.toString() ?: "",
                label = c["label"]?.toString() ?: ""
            )
        }.filter { it.id.isNotBlank() }
        @Suppress("UNCHECKED_CAST")
        val blendsRaw = result["blends"] as? List<Map<String, Any?>> ?: emptyList()
        val blends = blendsRaw.map { b ->
            BlendItem(
                id = b["hr_id"]?.toString() ?: "",
                label = b["label"]?.toString() ?: ""
            )
        }.filter { it.id.isNotBlank() }
        return StockAndBlends(coffees, blends)
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
