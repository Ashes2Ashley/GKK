package com.example.data.api.interceptor

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Dynamic Custom Header & Auth Interceptor.
 * Allows adding, editing, and toggling headers (e.g. Bearer Token, API Key) in real-time.
 */
class CustomHeaderInterceptor : Interceptor {

    private val headersMap = ConcurrentHashMap<String, String>()

    @Volatile
    var bearerToken: String? = null

    fun setHeader(name: String, value: String) {
        headersMap[name] = value
    }

    fun removeHeader(name: String) {
        headersMap.remove(name)
    }

    fun getAllHeaders(): Map<String, String> = HashMap(headersMap)

    fun clearHeaders() {
        headersMap.clear()
        bearerToken = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        // Inject default user-agent & accept
        builder.header("User-Agent", "ApiConnect-Android/2.0")
        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }

        // Inject custom Bearer token if set
        val token = bearerToken
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        // Inject user-defined dynamic headers
        for ((name, value) in headersMap) {
            builder.header(name, value)
        }

        return chain.proceed(builder.build())
    }
}

/**
 * Chaos Engineering Interceptor.
 * Simulates artificial network conditions: added latency, simulated HTTP error codes,
 * and corrupted JSON payloads to test Moshi parser resilience and UI error recovery.
 */
class ChaosInterceptor : Interceptor {

    @Volatile
    var simulatedLatencyMs: Long = 0L

    @Volatile
    var simulatedErrorCode: Int = 0 // 0 means normal; 401, 403, 404, 500 etc.

    @Volatile
    var simulateCorruptedJson: Boolean = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Apply artificial latency
        val latency = simulatedLatencyMs
        if (latency > 0) {
            try {
                Thread.sleep(latency)
            } catch (_: InterruptedException) {
                // ignore
            }
        }

        // Apply simulated HTTP status error
        val errorCode = simulatedErrorCode
        if (errorCode > 0) {
            val errorJson = """{"error": "Simulated Chaos Error", "code": $errorCode, "timestamp": ${System.currentTimeMillis()}}"""
            return Response.Builder()
                .code(errorCode)
                .message("Chaos Simulation HTTP $errorCode")
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .body(errorJson.toResponseBody("application/json".toMediaTypeOrNull()))
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Chaos-Simulated", "true")
                .build()
        }

        // If corrupted JSON simulated
        if (simulateCorruptedJson) {
            val originalResponse = chain.proceed(request)
            val malformedBody = """{"id": "NOT_AN_INTEGER_MALFORMED", "userId": true, "corrupted": [unclosed array"""
            return originalResponse.newBuilder()
                .body(malformedBody.toResponseBody("application/json".toMediaTypeOrNull()))
                .addHeader("X-Chaos-Malformed", "true")
                .build()
        }

        return chain.proceed(request)
    }
}

/**
 * Utility to generate a copyable cURL command from an OkHttp Request.
 */
object CurlGenerator {
    fun toCurl(request: Request): String {
        val sb = StringBuilder("curl -X ${request.method} \\\n")
        sb.append("  '${request.url}'")

        val headers = request.headers
        for (i in 0 until headers.size) {
            sb.append(" \\\n  -H '${headers.name(i)}: ${headers.value(i)}'")
        }

        val body = request.body
        if (body != null) {
            try {
                val buffer = Buffer()
                body.writeTo(buffer)
                val bodyStr = buffer.readUtf8()
                if (bodyStr.isNotEmpty()) {
                    sb.append(" \\\n  -d '${bodyStr.replace("'", "\\'")}'")
                }
            } catch (_: Exception) {
                // Ignore buffer read failure
            }
        }

        return sb.toString()
    }
}
