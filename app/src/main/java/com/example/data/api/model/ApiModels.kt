package com.example.data.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data model representing a Post resource from the API.
 * Uses @JsonClass(generateAdapter = true) for compile-time Moshi code generation,
 * avoiding reflection overhead at runtime.
 */
@JsonClass(generateAdapter = true)
data class Post(
    @param:Json(name = "id")
    val id: Int? = null,

    @param:Json(name = "userId")
    val userId: Int = 1,

    @param:Json(name = "title")
    val title: String,

    @param:Json(name = "body")
    val body: String
)

/**
 * Data model representing a User resource from the API.
 */
@JsonClass(generateAdapter = true)
data class User(
    @param:Json(name = "id")
    val id: Int,

    @param:Json(name = "name")
    val name: String,

    @param:Json(name = "username")
    val username: String,

    @param:Json(name = "email")
    val email: String,

    @param:Json(name = "phone")
    val phone: String? = null,

    @param:Json(name = "website")
    val website: String? = null,

    @param:Json(name = "company")
    val company: Company? = null
)

/**
 * Nested data model representing User's company.
 */
@JsonClass(generateAdapter = true)
data class Company(
    @param:Json(name = "name")
    val name: String,

    @param:Json(name = "catchPhrase")
    val catchPhrase: String? = null,

    @param:Json(name = "bs")
    val bs: String? = null
)

/**
 * Model representing Comments on a Post.
 */
@JsonClass(generateAdapter = true)
data class Comment(
    @param:Json(name = "id")
    val id: Int,

    @param:Json(name = "postId")
    val postId: Int,

    @param:Json(name = "name")
    val name: String,

    @param:Json(name = "email")
    val email: String,

    @param:Json(name = "body")
    val body: String
)

/**
 * Model holding statistical benchmark metrics.
 */
data class BenchmarkMetrics(
    val totalRequests: Int = 0,
    val completedRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val minLatencyMs: Long = 0,
    val maxLatencyMs: Long = 0,
    val avgLatencyMs: Double = 0.0,
    val latencies: List<Long> = emptyList(),
    val isRunning: Boolean = false
)

/**
 * Model holding REST client response details.
 */
data class RestWorkbenchResponse(
    val statusCode: Int = 0,
    val statusMessage: String = "",
    val durationMs: Long = 0,
    val headers: Map<String, String> = emptyMap(),
    val responseBody: String = "",
    val curlCommand: String = "",
    val isCacheHit: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Generic response state wrapper.
 */
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T, val statusCode: Int = 200, val latencyMs: Long = 0, val isCacheHit: Boolean = false) : NetworkResult<T>()
    data class Error(val message: String, val statusCode: Int? = null, val throwable: Throwable? = null) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}

/**
 * Model representing an API request/response audit log entry for the UI inspector.
 */
data class ApiLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val method: String,
    val endpoint: String,
    val statusCode: Int,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val requestBody: String? = null,
    val responseBody: String? = null,
    val curlCommand: String = "",
    val isCacheHit: Boolean = false,
    val isSuccess: Boolean = statusCode in 200..299
)
