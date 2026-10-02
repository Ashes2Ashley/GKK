package com.example.data.api

import android.content.Context
import com.example.data.api.adapter.HexColorAdapter
import com.example.data.api.adapter.IsoDateAdapter
import com.example.data.api.adapter.SafeIntAdapter
import com.example.data.api.interceptor.ChaosInterceptor
import com.example.data.api.interceptor.CurlGenerator
import com.example.data.api.interceptor.CustomHeaderInterceptor
import com.example.data.api.model.Post
import com.example.data.api.service.ApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Singleton object providing configured instances of:
 * - Moshi for JSON parsing (with Custom Adapters + KotlinJsonAdapterFactory)
 * - OkHttpClient with HTTP logging, Cache, Dynamic Headers, and Chaos Interceptor
 * - Retrofit builder and ApiService
 */
object RetrofitClient {

    const val DEFAULT_BASE_URL = "https://jsonplaceholder.typicode.com/"

    @Volatile
    private var currentBaseUrl: String = DEFAULT_BASE_URL

    /**
     * Moshi JSON parser instance configured with custom adapters and Kotlin reflection.
     */
    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(IsoDateAdapter())
            .add(HexColorAdapter())
            .add(SafeIntAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Reusable JSON adapter for Post model to demonstrate direct Moshi serialization.
     */
    val postJsonAdapter by lazy {
        moshi.adapter(Post::class.java).indent("  ")
    }

    /**
     * Dynamic Custom Header Interceptor for runtime Authorization and custom headers.
     */
    val customHeaderInterceptor: CustomHeaderInterceptor by lazy {
        CustomHeaderInterceptor()
    }

    /**
     * Chaos Engineering Interceptor for latency and error simulation.
     */
    val chaosInterceptor: ChaosInterceptor by lazy {
        ChaosInterceptor()
    }

    /**
     * HttpLoggingInterceptor to log request/response payloads in Logcat.
     */
    val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Volatile
    private var okHttpCache: Cache? = null

    fun initializeCache(context: Context) {
        if (okHttpCache == null) {
            try {
                val cacheDir = File(context.cacheDir, "http_cache")
                val cacheSize = 10L * 1024 * 1024 // 10 MiB
                okHttpCache = Cache(cacheDir, cacheSize)
                rebuildClients()
            } catch (_: Exception) {
                // Ignore cache initialization failure
            }
        }
    }

    fun getCacheStats(): Triple<Int, Int, Int> {
        val cache = okHttpCache ?: return Triple(0, 0, 0)
        return Triple(cache.hitCount(), cache.networkCount(), cache.requestCount())
    }

    fun clearCache() {
        try {
            okHttpCache?.evictAll()
        } catch (_: Exception) {}
    }

    /**
     * Builds OkHttpClient instance with configured timeouts, interceptors, and disk cache.
     */
    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(customHeaderInterceptor)
            .addInterceptor(chaosInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        okHttpCache?.let { builder.cache(it) }

        return builder.build()
    }

    @Volatile
    private var okHttpClientInstance: OkHttpClient = buildOkHttpClient()

    val okHttpClient: OkHttpClient
        get() = okHttpClientInstance

    /**
     * Factory function to create a new Retrofit instance with Moshi converter.
     */
    fun createRetrofit(baseUrl: String = currentBaseUrl): Retrofit {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(sanitizedUrl)
            .client(okHttpClientInstance)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Volatile
    private var retrofitInstance: Retrofit = createRetrofit(currentBaseUrl)

    @Volatile
    private var apiServiceInstance: ApiService = retrofitInstance.create(ApiService::class.java)

    private fun rebuildClients() {
        okHttpClientInstance = buildOkHttpClient()
        retrofitInstance = createRetrofit(currentBaseUrl)
        apiServiceInstance = retrofitInstance.create(ApiService::class.java)
    }

    /**
     * Active ApiService instance.
     */
    val apiService: ApiService
        get() = apiServiceInstance

    /**
     * Get the current active Base URL.
     */
    fun getBaseUrl(): String = currentBaseUrl

    /**
     * Dynamically update the base URL and rebuild the Retrofit & ApiService instances.
     */
    @Synchronized
    fun updateBaseUrl(newUrl: String): Boolean {
        return try {
            val sanitized = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
            val newRetrofit = createRetrofit(sanitized)
            val newService = newRetrofit.create(ApiService::class.java)
            currentBaseUrl = sanitized
            retrofitInstance = newRetrofit
            apiServiceInstance = newService
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generic helper to create any Retrofit service interface.
     */
    fun <T> create(serviceClass: Class<T>): T {
        return retrofitInstance.create(serviceClass)
    }

    /**
     * Reified helper function for clean Kotlin syntax: `RetrofitClient.create<MyService>()`
     */
    inline fun <reified T> create(): T {
        return create(T::class.java)
    }

    /**
     * Helper to serialize a Post object to JSON string using Moshi.
     */
    fun postToJson(post: Post): String {
        return postJsonAdapter.toJson(post)
    }

    /**
     * Helper to parse a JSON string into a Post object using Moshi.
     */
    fun postFromJson(json: String): Post? {
        return postJsonAdapter.fromJson(json)
    }

    /**
     * Directly execute an OkHttp Request for the custom REST Workbench.
     */
    fun executeRawRequest(request: Request): Response {
        return okHttpClientInstance.newCall(request).execute()
    }

    /**
     * Utility to convert request to cURL string.
     */
    fun toCurl(request: Request): String {
        return CurlGenerator.toCurl(request)
    }
}
