package com.example.data.repository

import com.example.ApiConnectApplication
import com.example.data.api.RetrofitClient
import com.example.data.api.interceptor.CurlGenerator
import com.example.data.api.model.ApiLogEntry
import com.example.data.api.model.BenchmarkMetrics
import com.example.data.api.model.Comment
import com.example.data.api.model.NetworkResult
import com.example.data.api.model.Post
import com.example.data.api.model.RestWorkbenchResponse
import com.example.data.api.model.User
import com.example.data.local.dao.PostDao
import com.example.data.local.entity.CachedPostEntity
import com.example.data.local.entity.FavoritePostEntity
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Advanced Repository coordinating Retrofit API communication, Moshi parsing,
 * Room local caching, performance benchmarks, and REST workbench execution.
 */
class ApiRepository(
    private val client: RetrofitClient = RetrofitClient,
    private val postDao: PostDao = ApiConnectApplication.instance.database.postDao()
) {
    private val _apiLogs = MutableStateFlow<List<ApiLogEntry>>(emptyList())
    val apiLogs: StateFlow<List<ApiLogEntry>> = _apiLogs.asStateFlow()

    // Observable cached posts from Room
    val cachedPosts: Flow<List<CachedPostEntity>> = postDao.getAllCachedPosts()

    // Observable favorites from Room
    val favorites: Flow<List<FavoritePostEntity>> = postDao.getAllFavorites()

    private fun addLog(log: ApiLogEntry) {
        val updated = listOf(log) + _apiLogs.value.take(49)
        _apiLogs.value = updated
    }

    /**
     * Safe execution wrapper for Retrofit calls with metrics, logging, and cURL generation.
     */
    private suspend fun <T> safeApiCall(
        method: String,
        endpoint: String,
        requestBody: String? = null,
        apiCall: suspend () -> Response<T>
    ): NetworkResult<T> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val response = apiCall()
            val duration = System.currentTimeMillis() - startTime
            val code = response.code()
            val rawOkHttpResponse = response.raw()
            val isCacheHit = rawOkHttpResponse.networkResponse == null && rawOkHttpResponse.cacheResponse != null
            val curl = CurlGenerator.toCurl(rawOkHttpResponse.request)

            if (response.isSuccessful) {
                val body = response.body()
                val responseSummary = body?.toString() ?: "Empty body"
                addLog(
                    ApiLogEntry(
                        method = method,
                        endpoint = endpoint,
                        statusCode = code,
                        durationMs = duration,
                        requestBody = requestBody,
                        responseBody = responseSummary.take(500),
                        curlCommand = curl,
                        isCacheHit = isCacheHit
                    )
                )
                if (body != null) {
                    NetworkResult.Success(data = body, statusCode = code, latencyMs = duration, isCacheHit = isCacheHit)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val emptyVal = try {
                        Unit as T
                    } catch (_: Exception) {
                        null
                    }
                    if (emptyVal != null) {
                        NetworkResult.Success(data = emptyVal, statusCode = code, latencyMs = duration, isCacheHit = isCacheHit)
                    } else {
                        NetworkResult.Error("Response body was null", statusCode = code)
                    }
                }
            } else {
                val errorBodyStr = response.errorBody()?.string() ?: response.message()
                addLog(
                    ApiLogEntry(
                        method = method,
                        endpoint = endpoint,
                        statusCode = code,
                        durationMs = duration,
                        requestBody = requestBody,
                        responseBody = errorBodyStr.take(500),
                        curlCommand = curl,
                        isCacheHit = isCacheHit
                    )
                )
                NetworkResult.Error("HTTP $code: $errorBodyStr", statusCode = code)
            }
        } catch (e: HttpException) {
            val duration = System.currentTimeMillis() - startTime
            val code = e.code()
            addLog(
                ApiLogEntry(
                    method = method,
                    endpoint = endpoint,
                    statusCode = code,
                    durationMs = duration,
                    requestBody = requestBody,
                    responseBody = e.message()
                )
            )
            NetworkResult.Error("HTTP Error: ${e.message()}", statusCode = code, throwable = e)
        } catch (e: JsonDataException) {
            val duration = System.currentTimeMillis() - startTime
            addLog(
                ApiLogEntry(
                    method = method,
                    endpoint = endpoint,
                    statusCode = 0,
                    durationMs = duration,
                    requestBody = requestBody,
                    responseBody = "Moshi JsonDataException: ${e.localizedMessage}"
                )
            )
            NetworkResult.Error("Moshi Parsing Error: ${e.localizedMessage}", throwable = e)
        } catch (e: IOException) {
            val duration = System.currentTimeMillis() - startTime
            addLog(
                ApiLogEntry(
                    method = method,
                    endpoint = endpoint,
                    statusCode = 0,
                    durationMs = duration,
                    requestBody = requestBody,
                    responseBody = "Network IO Failure: ${e.localizedMessage}"
                )
            )
            NetworkResult.Error("Network error: Check internet connection (${e.localizedMessage})", throwable = e)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            addLog(
                ApiLogEntry(
                    method = method,
                    endpoint = endpoint,
                    statusCode = 0,
                    durationMs = duration,
                    requestBody = requestBody,
                    responseBody = "Unexpected Error: ${e.localizedMessage}"
                )
            )
            NetworkResult.Error("Unexpected error: ${e.localizedMessage}", throwable = e)
        }
    }

    suspend fun getPosts(limit: Int = 25): NetworkResult<List<Post>> {
        val result = safeApiCall(
            method = "GET",
            endpoint = "/posts?_limit=$limit"
        ) {
            client.apiService.getPosts(limit)
        }

        // Cache in Room on success
        if (result is NetworkResult.Success) {
            val entities = result.data.mapNotNull { CachedPostEntity.fromPost(it) }
            postDao.insertCachedPosts(entities)
        }

        return result
    }

    suspend fun getPostsPaged(
        page: Int,
        limit: Int,
        sort: String? = null,
        order: String? = null,
        userId: Int? = null
    ): NetworkResult<List<Post>> {
        val queryStr = buildString {
            append("/posts?_page=$page&_limit=$limit")
            if (sort != null) append("&_sort=$sort")
            if (order != null) append("&_order=$order")
            if (userId != null) append("&userId=$userId")
        }

        val result = safeApiCall(
            method = "GET",
            endpoint = queryStr
        ) {
            client.apiService.getPostsPaged(page, limit, sort, order, userId)
        }

        if (result is NetworkResult.Success) {
            val entities = result.data.mapNotNull { CachedPostEntity.fromPost(it) }
            postDao.insertCachedPosts(entities)
        }

        return result
    }

    suspend fun getPostById(id: Int): NetworkResult<Post> {
        return safeApiCall(
            method = "GET",
            endpoint = "/posts/$id"
        ) {
            client.apiService.getPostById(id)
        }
    }

    suspend fun getPostComments(postId: Int): NetworkResult<List<Comment>> {
        return safeApiCall(
            method = "GET",
            endpoint = "/posts/$postId/comments"
        ) {
            client.apiService.getPostComments(postId)
        }
    }

    suspend fun createPost(post: Post): NetworkResult<Post> {
        val jsonPayload = try {
            client.postToJson(post)
        } catch (_: Exception) {
            post.toString()
        }
        val result = safeApiCall(
            method = "POST",
            endpoint = "/posts",
            requestBody = jsonPayload
        ) {
            client.apiService.createPost(post)
        }

        if (result is NetworkResult.Success) {
            CachedPostEntity.fromPost(result.data)?.let {
                postDao.insertCachedPosts(listOf(it))
            }
        }
        return result
    }

    suspend fun updatePost(id: Int, post: Post): NetworkResult<Post> {
        val jsonPayload = try {
            client.postToJson(post)
        } catch (_: Exception) {
            post.toString()
        }
        val result = safeApiCall(
            method = "PUT",
            endpoint = "/posts/$id",
            requestBody = jsonPayload
        ) {
            client.apiService.updatePost(id, post)
        }

        if (result is NetworkResult.Success) {
            CachedPostEntity.fromPost(result.data)?.let {
                postDao.insertCachedPosts(listOf(it))
            }
        }
        return result
    }

    suspend fun getUsers(): NetworkResult<List<User>> {
        return safeApiCall(
            method = "GET",
            endpoint = "/users"
        ) {
            client.apiService.getUsers()
        }
    }

    suspend fun deletePost(id: Int): NetworkResult<Unit> {
        val result = safeApiCall(
            method = "DELETE",
            endpoint = "/posts/$id"
        ) {
            client.apiService.deletePost(id)
        }

        if (result is NetworkResult.Success) {
            postDao.deleteCachedPost(id)
        }
        return result
    }

    // --- Room Favorites & Offline ---
    suspend fun isFavorite(postId: Int): Boolean = withContext(Dispatchers.IO) {
        postDao.isFavorite(postId)
    }

    suspend fun toggleFavorite(post: Post, note: String = "", tag: String = "General") = withContext(Dispatchers.IO) {
        val postId = post.id ?: return@withContext
        if (postDao.isFavorite(postId)) {
            postDao.deleteFavorite(postId)
        } else {
            postDao.insertFavorite(
                FavoritePostEntity(
                    postId = postId,
                    userId = post.userId,
                    title = post.title,
                    body = post.body,
                    note = note,
                    tag = tag
                )
            )
        }
    }

    suspend fun removeFavorite(postId: Int) = withContext(Dispatchers.IO) {
        postDao.deleteFavorite(postId)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        postDao.clearCachedPosts()
        client.clearCache()
    }

    // --- Benchmark Suite ---
    suspend fun runBenchmark(
        count: Int = 5,
        onProgress: (BenchmarkMetrics) -> Unit
    ): BenchmarkMetrics = withContext(Dispatchers.IO) {
        val latencies = mutableListOf<Long>()
        var success = 0
        var failed = 0

        for (i in 1..count) {
            val startTime = System.currentTimeMillis()
            try {
                val res = client.apiService.getPostById((i % 10) + 1)
                val duration = System.currentTimeMillis() - startTime
                latencies.add(duration)
                if (res.isSuccessful) success++ else failed++
            } catch (_: Exception) {
                val duration = System.currentTimeMillis() - startTime
                latencies.add(duration)
                failed++
            }

            val currentMetrics = BenchmarkMetrics(
                totalRequests = count,
                completedRequests = i,
                successfulRequests = success,
                failedRequests = failed,
                minLatencyMs = latencies.minOrNull() ?: 0L,
                maxLatencyMs = latencies.maxOrNull() ?: 0L,
                avgLatencyMs = if (latencies.isNotEmpty()) latencies.average() else 0.0,
                latencies = latencies.toList(),
                isRunning = i < count
            )
            onProgress(currentMetrics)
        }

        BenchmarkMetrics(
            totalRequests = count,
            completedRequests = count,
            successfulRequests = success,
            failedRequests = failed,
            minLatencyMs = latencies.minOrNull() ?: 0L,
            maxLatencyMs = latencies.maxOrNull() ?: 0L,
            avgLatencyMs = if (latencies.isNotEmpty()) latencies.average() else 0.0,
            latencies = latencies.toList(),
            isRunning = false
        )
    }

    // --- Custom REST Client Workbench Runner ---
    suspend fun executeCustomRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        bodyJson: String?
    ): RestWorkbenchResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val reqBuilder = Request.Builder().url(url)

            for ((k, v) in headers) {
                reqBuilder.addHeader(k, v)
            }

            val reqBody = if (!bodyJson.isNullOrBlank() && method in listOf("POST", "PUT", "PATCH")) {
                bodyJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            } else null

            when (method.uppercase()) {
                "GET" -> reqBuilder.get()
                "POST" -> reqBuilder.post(reqBody ?: "".toRequestBody(null))
                "PUT" -> reqBuilder.put(reqBody ?: "".toRequestBody(null))
                "PATCH" -> reqBuilder.patch(reqBody ?: "".toRequestBody(null))
                "DELETE" -> reqBuilder.delete(reqBody)
                else -> reqBuilder.get()
            }

            val builtRequest = reqBuilder.build()
            val curl = CurlGenerator.toCurl(builtRequest)
            val response = client.executeRawRequest(builtRequest)
            val duration = System.currentTimeMillis() - startTime
            val responseStr = response.body?.string() ?: ""
            val isCache = response.networkResponse == null && response.cacheResponse != null

            val headerMap = mutableMapOf<String, String>()
            for (i in 0 until response.headers.size) {
                headerMap[response.headers.name(i)] = response.headers.value(i)
            }

            addLog(
                ApiLogEntry(
                    method = method,
                    endpoint = url,
                    statusCode = response.code,
                    durationMs = duration,
                    requestBody = bodyJson,
                    responseBody = responseStr.take(500),
                    curlCommand = curl,
                    isCacheHit = isCache
                )
            )

            RestWorkbenchResponse(
                statusCode = response.code,
                statusMessage = response.message,
                durationMs = duration,
                headers = headerMap,
                responseBody = responseStr,
                curlCommand = curl,
                isCacheHit = isCache
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            RestWorkbenchResponse(
                statusCode = 0,
                statusMessage = "Execution Failed",
                durationMs = duration,
                headers = emptyMap(),
                responseBody = "Error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                curlCommand = ""
            )
        }
    }

    // --- Dynamic Headers & Chaos Management ---
    fun setBearerToken(token: String?) {
        client.customHeaderInterceptor.bearerToken = token
    }

    fun setCustomHeader(name: String, value: String) {
        client.customHeaderInterceptor.setHeader(name, value)
    }

    fun removeCustomHeader(name: String) {
        client.customHeaderInterceptor.removeHeader(name)
    }

    fun getCustomHeaders(): Map<String, String> = client.customHeaderInterceptor.getAllHeaders()

    fun setSimulatedLatency(ms: Long) {
        client.chaosInterceptor.simulatedLatencyMs = ms
    }

    fun setSimulatedErrorCode(code: Int) {
        client.chaosInterceptor.simulatedErrorCode = code
    }

    fun setSimulatedCorruptedJson(corrupt: Boolean) {
        client.chaosInterceptor.simulateCorruptedJson = corrupt
    }

    fun clearLogs() {
        _apiLogs.value = emptyList()
    }
}
