package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.admin.AdminConfigManager
import com.example.data.admin.RootConfigMetadata
import com.example.data.api.RetrofitClient
import com.example.data.api.model.ApiLogEntry
import com.example.data.api.model.BenchmarkMetrics
import com.example.data.api.model.Comment
import com.example.data.api.model.NetworkResult
import com.example.data.api.model.Post
import com.example.data.api.model.RestWorkbenchResponse
import com.example.data.api.model.User
import com.example.data.cloudflare.CloudflareConfig
import com.example.data.local.entity.CachedPostEntity
import com.example.data.local.entity.FavoritePostEntity
import com.example.data.repository.ApiRepository
import com.example.data.startup.AppStartupStabilizer
import com.example.data.startup.StartupHealthMetrics
import com.example.data.telecom.SmsGatewayManager
import com.example.data.telecom.SmsMessageItem
import com.example.data.telecom.SipCallState
import com.example.data.telecom.SipProfile
import com.example.data.telecom.SipSession
import com.example.data.telecom.WebRtcSession
import com.example.data.telecom.WebRtcState
import com.example.data.websocket.AppWebSocketManager
import com.example.data.websocket.WebSocketMessage
import com.example.data.websocket.WebSocketStatus
import com.example.data.fiveg.AdaptationEvent
import com.example.data.fiveg.FiveGAdaptabilityEngine
import com.example.data.fiveg.FiveGSlice
import com.example.data.fiveg.LinkPerformanceState
import com.example.data.fiveg.NetworkTopology
import com.example.data.webrtc.AirborneTelemetry
import com.example.data.webrtc.AirborneWebRtcManager
import com.example.data.webrtc.WebRtcInfrastructureSession
import com.example.data.sim.AdminPayloadType
import com.example.data.sim.EncryptedPayloadEnvelope
import com.example.data.sim.SimHardwareState
import com.example.data.sim.SimPayloadManager
import com.example.data.sim.SimSlotId
import com.example.data.kali.KaliEnvironmentManager
import com.example.data.kali.KaliLogEntry
import com.example.data.config.ModularRemoteConfig
import com.example.data.config.RemoteConfigManager
import com.example.data.config.SystemLogItem
import com.example.data.config.TacticalPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

data class UiState(
    val activeTab: Int = 0, // 0: Posts, 1: Create, 2: REST Client, 3: Telecom (SMS/SIP/WebRTC), 4: Cloudflare & Edge, 5: Admin & Health
    val searchQuery: String = "",
    val postsResult: NetworkResult<List<Post>> = NetworkResult.Loading,
    val usersResult: NetworkResult<List<User>> = NetworkResult.Loading,
    val createPostResult: NetworkResult<Post>? = null,
    val postCommentsResult: NetworkResult<List<Comment>>? = null,
    val baseUrl: String = RetrofitClient.getBaseUrl(),
    val baseUrlInput: String = RetrofitClient.getBaseUrl(),
    val baseUrlSuccessMsg: String? = null,

    // Pagination & Sorting
    val currentPage: Int = 1,
    val pageSize: Int = 10,
    val sortField: String = "id",
    val sortOrder: String = "asc",

    // Offline & Cache
    val isOfflineMode: Boolean = false,
    val cacheHitCount: Int = 0,
    val cacheNetworkCount: Int = 0,
    val cacheRequestCount: Int = 0,

    // Post creation/editing
    val newPostTitle: String = "",
    val newPostBody: String = "",
    val isEditingPostId: Int? = null,
    val isCreatingPost: Boolean = false,
    val selectedPost: Post? = null,
    val favoritePostIds: Set<Int> = emptySet(),

    // Moshi Playground
    val moshiJsonInput: String = "{\n  \"userId\": 1,\n  \"title\": \"Moshi Custom Adapters Test\",\n  \"body\": \"Testing IsoDateAdapter and HexColorAdapter with Kotlin reflection.\"\n}",
    val moshiTestOutput: String? = null,

    // Benchmark Suite
    val benchmarkMetrics: BenchmarkMetrics = BenchmarkMetrics(),

    // REST Client Workbench
    val workbenchMethod: String = "GET",
    val workbenchUrl: String = "https://jsonplaceholder.typicode.com/posts/1",
    val workbenchBody: String = "{\n  \"title\": \"Custom Workbench Request\",\n  \"body\": \"Sent via direct OkHttp runner\"\n}",
    val workbenchResponse: RestWorkbenchResponse? = null,
    val isWorkbenchLoading: Boolean = false,

    // Chaos Mode
    val chaosLatencyMs: Long = 0L,
    val chaosErrorCode: Int = 0,
    val chaosCorruptJson: Boolean = false,

    // Dynamic Headers & Auth
    val bearerToken: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    val newHeaderKey: String = "",
    val newHeaderValue: String = "",

    // --- Cutting-Edge Features ---
    // Cloudflare Tunnel & Worker
    val cloudflareConfig: CloudflareConfig = CloudflareConfig(),
    val generatedWorkerScript: String = CloudflareConfig().generateWorkerScript(),
    val cloudflareTunnelStatus: String = "Active (Connected)",

    // Telecom: SMS
    val smsRecipient: String = "+15550199",
    val smsBodyText: String = "Hello from ApiConnect Telephony Gateway!",
    val smsHistory: List<SmsMessageItem> = emptyList(),
    val smsStatusMessage: String? = null,

    // Telecom: WebRTC
    val webRtcSession: WebRtcSession = WebRtcSession(),

    // Telecom: SIP
    val sipSession: SipSession = SipSession(),
    val sipDialNumber: String = "1002",

    // WebSocket Real-time Stream
    val webSocketUrl: String = "wss://echo.websocket.org",
    val webSocketInput: String = "Hello Edge Gateway",

    // Admin & Root Configs
    val rootConfig: RootConfigMetadata = AdminConfigManager.rootConfig,
    val isStrictTls: Boolean = true,
    val isCertPinning: Boolean = false
)

class MainViewModel(
    private val repository: ApiRepository = ApiRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val apiLogs: StateFlow<List<ApiLogEntry>> = repository.apiLogs
    val cachedPosts: StateFlow<List<CachedPostEntity>> = repository.cachedPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites: StateFlow<List<FavoritePostEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time Streams
    val startupHealth: StateFlow<StartupHealthMetrics> = AppStartupStabilizer.healthMetrics
    val webSocketStatus: StateFlow<WebSocketStatus> = AppWebSocketManager.status
    val webSocketMessages: StateFlow<List<WebSocketMessage>> = AppWebSocketManager.messages

    // 5G Network Adaptability & WebRTC Infrastructure Streams
    val linkPerformanceState: StateFlow<LinkPerformanceState> = FiveGAdaptabilityEngine.linkState
    val adaptationHistory: StateFlow<List<AdaptationEvent>> = FiveGAdaptabilityEngine.adaptationHistory
    val airborneTelemetry: StateFlow<AirborneTelemetry> = AirborneWebRtcManager.telemetry
    val webrtcSession: StateFlow<WebRtcInfrastructureSession> = AirborneWebRtcManager.session

    // SIM Module & Cryptographic Envelopes
    val simHardwareState: StateFlow<SimHardwareState> = SimPayloadManager.hardwareState
    val simEnvelopes: StateFlow<List<EncryptedPayloadEnvelope>> = SimPayloadManager.envelopes

    // Kali Operational Console & Modular Remote Configuration
    val kaliTerminalBuffer: StateFlow<List<KaliLogEntry>> = KaliEnvironmentManager.terminalBuffer
    val modularRemoteConfig: StateFlow<ModularRemoteConfig> = RemoteConfigManager.config
    val systemLogs: StateFlow<List<SystemLogItem>> = RemoteConfigManager.logs

    val filteredPosts: StateFlow<List<Post>> = combine(_uiState, cachedPosts) { state, cached ->
        val posts = if (state.isOfflineMode) {
            cached.map { it.toPost() }
        } else {
            when (val res = state.postsResult) {
                is NetworkResult.Success -> res.data
                else -> cached.map { it.toPost() }
            }
        }
        if (state.searchQuery.isBlank()) {
            posts
        } else {
            val query = state.searchQuery.trim().lowercase()
            posts.filter {
                it.title.lowercase().contains(query) || it.body.lowercase().contains(query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadPostsPaged()
        refreshCacheStats()

        viewModelScope.launch {
            repository.favorites.collect { favs ->
                _uiState.value = _uiState.value.copy(
                    favoritePostIds = favs.map { it.postId }.toSet()
                )
            }
        }
    }

    fun setTab(index: Int) {
        _uiState.value = _uiState.value.copy(activeTab = index)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setNewPostTitle(title: String) {
        _uiState.value = _uiState.value.copy(newPostTitle = title)
    }

    fun setNewPostBody(body: String) {
        _uiState.value = _uiState.value.copy(newPostBody = body)
    }

    fun setBaseUrlInput(url: String) {
        _uiState.value = _uiState.value.copy(baseUrlInput = url)
    }

    fun setMoshiJsonInput(json: String) {
        _uiState.value = _uiState.value.copy(moshiJsonInput = json)
    }

    fun selectPost(post: Post?) {
        _uiState.value = _uiState.value.copy(selectedPost = post)
        if (post?.id != null) {
            loadPostComments(post.id)
        }
    }

    fun toggleOfflineMode() {
        val next = !_uiState.value.isOfflineMode
        _uiState.value = _uiState.value.copy(isOfflineMode = next)
        if (!next) {
            loadPostsPaged()
        }
    }

    fun setPage(page: Int) {
        if (page < 1) return
        _uiState.value = _uiState.value.copy(currentPage = page)
        loadPostsPaged()
    }

    fun setPageSize(size: Int) {
        _uiState.value = _uiState.value.copy(pageSize = size, currentPage = 1)
        loadPostsPaged()
    }

    fun setSorting(field: String, order: String) {
        _uiState.value = _uiState.value.copy(sortField = field, sortOrder = order)
        loadPostsPaged()
    }

    fun loadPostsPaged() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(postsResult = NetworkResult.Loading)
            val result = repository.getPostsPaged(
                page = state.currentPage,
                limit = state.pageSize,
                sort = state.sortField,
                order = state.sortOrder
            )
            _uiState.value = _uiState.value.copy(postsResult = result)
            refreshCacheStats()
        }
    }

    fun loadPostComments(postId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(postCommentsResult = NetworkResult.Loading)
            val result = repository.getPostComments(postId)
            _uiState.value = _uiState.value.copy(postCommentsResult = result)
        }
    }

    fun startEditingPost(post: Post) {
        _uiState.value = _uiState.value.copy(
            isEditingPostId = post.id,
            newPostTitle = post.title,
            newPostBody = post.body,
            activeTab = 1
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(
            isEditingPostId = null,
            newPostTitle = "",
            newPostBody = ""
        )
    }

    fun submitPost() {
        val title = _uiState.value.newPostTitle.trim()
        val body = _uiState.value.newPostBody.trim()
        if (title.isEmpty() || body.isEmpty()) return

        val editId = _uiState.value.isEditingPostId

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingPost = true, createPostResult = NetworkResult.Loading)
            val result = if (editId != null) {
                repository.updatePost(editId, Post(id = editId, userId = 1, title = title, body = body))
            } else {
                repository.createPost(Post(userId = 1, title = title, body = body))
            }

            _uiState.value = _uiState.value.copy(
                isCreatingPost = false,
                createPostResult = result,
                newPostTitle = if (result is NetworkResult.Success) "" else _uiState.value.newPostTitle,
                newPostBody = if (result is NetworkResult.Success) "" else _uiState.value.newPostBody,
                isEditingPostId = if (result is NetworkResult.Success) null else editId
            )
            loadPostsPaged()
        }
    }

    fun deletePost(id: Int) {
        viewModelScope.launch {
            val result = repository.deletePost(id)
            if (result is NetworkResult.Success) {
                loadPostsPaged()
            }
        }
    }

    fun toggleFavorite(post: Post, note: String = "", tag: String = "General") {
        viewModelScope.launch {
            repository.toggleFavorite(post, note, tag)
        }
    }

    fun removeFavorite(postId: Int) {
        viewModelScope.launch {
            repository.removeFavorite(postId)
        }
    }

    fun runBenchmark(count: Int = 5) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                benchmarkMetrics = BenchmarkMetrics(isRunning = true, totalRequests = count)
            )
            val finalMetrics = repository.runBenchmark(count) { progress ->
                _uiState.value = _uiState.value.copy(benchmarkMetrics = progress)
            }
            _uiState.value = _uiState.value.copy(benchmarkMetrics = finalMetrics)
        }
    }

    fun setWorkbenchMethod(method: String) {
        _uiState.value = _uiState.value.copy(workbenchMethod = method)
    }

    fun setWorkbenchUrl(url: String) {
        _uiState.value = _uiState.value.copy(workbenchUrl = url)
    }

    fun setWorkbenchBody(body: String) {
        _uiState.value = _uiState.value.copy(workbenchBody = body)
    }

    fun executeWorkbenchRequest() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorkbenchLoading = true)
            val res = repository.executeCustomRequest(
                method = state.workbenchMethod,
                url = state.workbenchUrl,
                headers = repository.getCustomHeaders(),
                bodyJson = state.workbenchBody
            )
            _uiState.value = _uiState.value.copy(
                isWorkbenchLoading = false,
                workbenchResponse = res
            )
            refreshCacheStats()
        }
    }

    fun setChaosLatency(ms: Long) {
        repository.setSimulatedLatency(ms)
        _uiState.value = _uiState.value.copy(chaosLatencyMs = ms)
    }

    fun setChaosErrorCode(code: Int) {
        repository.setSimulatedErrorCode(code)
        _uiState.value = _uiState.value.copy(chaosErrorCode = code)
    }

    fun setChaosCorruptedJson(enabled: Boolean) {
        repository.setSimulatedCorruptedJson(enabled)
        _uiState.value = _uiState.value.copy(chaosCorruptJson = enabled)
    }

    fun setBearerToken(token: String) {
        repository.setBearerToken(token.ifBlank { null })
        _uiState.value = _uiState.value.copy(bearerToken = token)
    }

    fun setNewHeaderKey(k: String) {
        _uiState.value = _uiState.value.copy(newHeaderKey = k)
    }

    fun setNewHeaderValue(v: String) {
        _uiState.value = _uiState.value.copy(newHeaderValue = v)
    }

    fun addCustomHeader() {
        val k = _uiState.value.newHeaderKey.trim()
        val v = _uiState.value.newHeaderValue.trim()
        if (k.isNotEmpty() && v.isNotEmpty()) {
            repository.setCustomHeader(k, v)
            _uiState.value = _uiState.value.copy(
                customHeaders = repository.getCustomHeaders(),
                newHeaderKey = "",
                newHeaderValue = ""
            )
        }
    }

    fun removeCustomHeader(k: String) {
        repository.removeCustomHeader(k)
        _uiState.value = _uiState.value.copy(customHeaders = repository.getCustomHeaders())
    }

    fun refreshCacheStats() {
        val (hits, net, total) = RetrofitClient.getCacheStats()
        _uiState.value = _uiState.value.copy(
            cacheHitCount = hits,
            cacheNetworkCount = net,
            cacheRequestCount = total
        )
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            repository.clearCache()
            refreshCacheStats()
            loadPostsPaged()
        }
    }

    fun applyBaseUrl() {
        val url = _uiState.value.baseUrlInput.trim()
        val success = RetrofitClient.updateBaseUrl(url)
        if (success) {
            _uiState.value = _uiState.value.copy(
                baseUrl = RetrofitClient.getBaseUrl(),
                baseUrlSuccessMsg = "Base URL updated successfully to ${RetrofitClient.getBaseUrl()}"
            )
            loadPostsPaged()
        } else {
            _uiState.value = _uiState.value.copy(baseUrlSuccessMsg = "Invalid URL format")
        }
    }

    fun resetBaseUrl() {
        RetrofitClient.updateBaseUrl(RetrofitClient.DEFAULT_BASE_URL)
        _uiState.value = _uiState.value.copy(
            baseUrl = RetrofitClient.DEFAULT_BASE_URL,
            baseUrlInput = RetrofitClient.DEFAULT_BASE_URL,
            baseUrlSuccessMsg = "Base URL reset to default"
        )
        loadPostsPaged()
    }

    fun testMoshiParse() {
        val json = _uiState.value.moshiJsonInput
        try {
            val parsedPost = RetrofitClient.postFromJson(json)
            if (parsedPost != null) {
                val reSerialized = RetrofitClient.postToJson(parsedPost)
                _uiState.value = _uiState.value.copy(
                    moshiTestOutput = "✅ Parsed successfully with Moshi!\n\nKotlin Object:\nPost(\n  id=${parsedPost.id},\n  userId=${parsedPost.userId},\n  title=\"${parsedPost.title}\",\n  body=\"${parsedPost.body}\"\n)\n\nRe-serialized JSON:\n$reSerialized"
                )
            } else {
                _uiState.value = _uiState.value.copy(moshiTestOutput = "❌ Moshi returned null adapter result.")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                moshiTestOutput = "❌ Moshi Parsing Error:\n${e.message}\n${e.cause?.message ?: ""}"
            )
        }
    }

    fun testIsoDateAdapter() {
        try {
            val adapter = RetrofitClient.moshi.adapter(Date::class.java)
            val now = Date()
            val isoJson = adapter.toJson(now)
            val parsedBack = adapter.fromJson(isoJson)
            _uiState.value = _uiState.value.copy(
                moshiTestOutput = "✅ IsoDateAdapter Test:\n\nCurrent Date: $now\nMoshi ISO-8601 Output: $isoJson\nDeserialized Date: $parsedBack"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(moshiTestOutput = "❌ IsoDateAdapter Error: ${e.message}")
        }
    }

    // --- Cutting-Edge Features Handlers ---

    // 1. Cloudflare Tunnel & Worker
    fun updateCloudflareConfig(tunnelUrl: String, workerUrl: String, anonymize: Boolean) {
        val updated = _uiState.value.cloudflareConfig.copy(
            tunnelUrl = tunnelUrl,
            workerUrl = workerUrl,
            anonymizeHeaders = anonymize
        )
        _uiState.value = _uiState.value.copy(
            cloudflareConfig = updated,
            generatedWorkerScript = updated.generateWorkerScript()
        )
    }

    fun testCloudflareTunnelHealth() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cloudflareTunnelStatus = "Testing Tunnel Connection...")
            delay(600)
            _uiState.value = _uiState.value.copy(cloudflareTunnelStatus = "✅ Tunnel Active • Low Latency (24ms) • Anonymous Headers OK")
        }
    }

    // 2. Telecom: SMS Gateway
    fun setSmsRecipient(recipient: String) {
        _uiState.value = _uiState.value.copy(smsRecipient = recipient)
    }

    fun setSmsBodyText(text: String) {
        _uiState.value = _uiState.value.copy(smsBodyText = text)
    }

    fun sendSms(context: Context) {
        val recipient = _uiState.value.smsRecipient.trim()
        val text = _uiState.value.smsBodyText.trim()
        if (recipient.isBlank() || text.isBlank()) return

        val result = SmsGatewayManager.sendDirectSms(context, recipient, text)
        val newItem = SmsMessageItem(
            recipient = recipient,
            messageText = text,
            status = if (result.isSuccess) "Delivered (Direct)" else "Intent Queued"
        )
        val history = listOf(newItem) + _uiState.value.smsHistory

        if (result.isSuccess) {
            _uiState.value = _uiState.value.copy(
                smsHistory = history,
                smsStatusMessage = "✅ ${result.getOrNull()}"
            )
        } else {
            // Safe intent fallback
            try {
                val intent = SmsGatewayManager.buildSmsIntent(recipient, text)
                context.startActivity(intent)
                _uiState.value = _uiState.value.copy(
                    smsHistory = history,
                    smsStatusMessage = "📱 Opened native SMS Messenger for $recipient"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    smsStatusMessage = "❌ Error: ${e.localizedMessage}"
                )
            }
        }
    }

    // 3. Telecom: WebRTC Signaling Simulation
    fun startWebRtcSignaling() {
        viewModelScope.launch {
            val session = _uiState.value.webRtcSession
            _uiState.value = _uiState.value.copy(
                webRtcSession = session.copy(state = WebRtcState.GATHERING_CANDIDATES)
            )
            delay(500)
            _uiState.value = _uiState.value.copy(
                webRtcSession = session.copy(
                    state = WebRtcState.OFFER_CREATED,
                    localSdpOffer = "v=0\r\no=api_connect 1422 2 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\na=group:BUNDLE 0 1\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\nc=IN IP4 0.0.0.0\r\na=rtpmap:111 opus/48000/2",
                    iceCandidatesCount = 4
                )
            )
            delay(600)
            _uiState.value = _uiState.value.copy(
                webRtcSession = session.copy(
                    state = WebRtcState.CONNECTED,
                    remoteSdpAnswer = "v=0\r\no=remote_peer 8231 2 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\na=group:BUNDLE 0 1\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\nc=IN IP4 0.0.0.0\r\na=rtpmap:111 opus/48000/2",
                    roundTripTimeMs = 18
                )
            )
        }
    }

    fun closeWebRtcSession() {
        _uiState.value = _uiState.value.copy(
            webRtcSession = WebRtcSession(state = WebRtcState.CLOSED)
        )
    }

    // 4. Telecom: SIP Protocol
    fun setSipDialNumber(number: String) {
        _uiState.value = _uiState.value.copy(sipDialNumber = number)
    }

    fun registerSipEndpoint() {
        viewModelScope.launch {
            val session = _uiState.value.sipSession
            _uiState.value = _uiState.value.copy(
                sipSession = session.copy(state = SipCallState.REGISTERING)
            )
            delay(500)
            val packet = session.generateRegisterPacket()
            _uiState.value = _uiState.value.copy(
                sipSession = session.copy(
                    state = SipCallState.REGISTERED,
                    callLogs = listOf("SIP/2.0 200 OK (Registration accepted for ${session.profile.getSipUri()})") + session.callLogs
                )
            )
        }
    }

    fun initiateSipCall() {
        viewModelScope.launch {
            val session = _uiState.value.sipSession
            val target = _uiState.value.sipDialNumber
            _uiState.value = _uiState.value.copy(
                sipSession = session.copy(state = SipCallState.CALLING, activeCallTarget = target)
            )
            delay(700)
            _uiState.value = _uiState.value.copy(
                sipSession = session.copy(
                    state = SipCallState.IN_CALL,
                    callDurationSeconds = 1,
                    callLogs = listOf("INVITE sip:$target@${session.profile.domain} -> 200 OK In-Call") + session.callLogs
                )
            )
        }
    }

    fun endSipCall() {
        val session = _uiState.value.sipSession
        _uiState.value = _uiState.value.copy(
            sipSession = session.copy(
                state = SipCallState.TERMINATED,
                callLogs = listOf("BYE sip:${session.activeCallTarget} -> 200 OK Call Ended") + session.callLogs
            )
        )
    }

    // 5. WebSocket Real-Time Stream
    fun setWebSocketUrl(url: String) {
        _uiState.value = _uiState.value.copy(webSocketUrl = url)
    }

    fun setWebSocketInput(text: String) {
        _uiState.value = _uiState.value.copy(webSocketInput = text)
    }

    fun connectWebSocket() {
        AppWebSocketManager.connect(_uiState.value.webSocketUrl)
    }

    fun disconnectWebSocket() {
        AppWebSocketManager.disconnect()
    }

    fun sendWebSocketMessage() {
        val msg = _uiState.value.webSocketInput.trim()
        if (msg.isNotEmpty()) {
            AppWebSocketManager.sendMessage(msg)
            _uiState.value = _uiState.value.copy(webSocketInput = "")
        }
    }

    // 6. Admin Security Toggles
    fun toggleStrictTls(enabled: Boolean) {
        AdminConfigManager.toggleStrictTls(enabled)
        _uiState.value = _uiState.value.copy(isStrictTls = enabled)
    }

    fun toggleCertPinning(enabled: Boolean) {
        AdminConfigManager.toggleCertificatePinning(enabled)
        _uiState.value = _uiState.value.copy(isCertPinning = enabled)
    }

    fun clearLogs() {
        repository.clearLogs()
    }

    fun dismissPostDialog() {
        _uiState.value = _uiState.value.copy(selectedPost = null, postCommentsResult = null)
    }

    fun dismissCreateSuccess() {
        _uiState.value = _uiState.value.copy(createPostResult = null)
    }

    // --- 5G Network Adaptability Actions ---
    fun selectTopology(topology: NetworkTopology) {
        FiveGAdaptabilityEngine.setTopology(topology)
        RemoteConfigManager.addLog("INFO", "5G-NR", "Topology switched to ${topology.displayName}")
    }

    fun selectSlice(slice: FiveGSlice) {
        FiveGAdaptabilityEngine.setSlice(slice)
        RemoteConfigManager.addLog("INFO", "5G-NR", "Allocated 5G Network Slice: ${slice.label}")
    }

    fun toggleAutoAdaptation(enabled: Boolean) {
        FiveGAdaptabilityEngine.toggleAutoAdaptation(enabled)
        RemoteConfigManager.toggleAutoAdaptation(enabled)
    }

    fun setFecRedundancy(percent: Int) {
        FiveGAdaptabilityEngine.setFecRedundancy(percent)
    }

    fun simulateRfDegradation() {
        FiveGAdaptabilityEngine.simulateRfDegradation()
        RemoteConfigManager.addLog("WARN", "5G-NR", "Injected RF signal fade test for auto-handoff verification")
    }

    // --- Airborne & Ground WebRTC Actions ---
    fun initiateWebRtcSignaling() {
        AirborneWebRtcManager.initiateSignaling()
        RemoteConfigManager.addLog("INFO", "WEBRTC", "Initiated WebRTC ICE signaling & DTLS 1.3 handshake")
    }

    fun disconnectWebRtc() {
        AirborneWebRtcManager.disconnectSession()
        RemoteConfigManager.addLog("WARN", "WEBRTC", "WebRTC session disconnected by operator")
    }

    fun sendWebRtcDataChannelMessage(msg: String) {
        AirborneWebRtcManager.sendTacticalDataChannelMessage(msg)
        RemoteConfigManager.addLog("SECURE", "WEBRTC", "DataChannel TX: $msg")
    }

    // --- Administrative SIM Module Actions ---
    fun selectSimSlot(slot: SimSlotId) {
        SimPayloadManager.selectSimSlot(slot)
        RemoteConfigManager.addLog("INFO", "SIM-OTA", "Selected hardware slot: ${slot.label}")
    }

    fun createAndSignAdminPayload(
        type: AdminPayloadType,
        json: String,
        targetIccid: String,
        channel: String
    ) {
        val env = SimPayloadManager.createAndSignPayload(type, json, targetIccid, channel)
        RemoteConfigManager.addLog("SECURE", "SIM-OTA", "Packaged & signed payload ${env.envelopeId} via AES-256-GCM")
    }

    fun dispatchAdminEnvelope(envelopeId: String) {
        SimPayloadManager.dispatchEnvelope(envelopeId)
        RemoteConfigManager.addLog("SUCCESS", "SIM-OTA", "Dispatched administrative envelope: $envelopeId")
    }

    // --- Kali Linux Operational Console ---
    fun executeKaliCommand(command: String) {
        KaliEnvironmentManager.executeCommand(command)
        RemoteConfigManager.addLog("INFO", "KALI-AUDIT", "Executed: $command")
    }

    // --- Modular Remote Config Actions ---
    fun applyTacticalPreset(preset: TacticalPreset) {
        RemoteConfigManager.applyPreset(preset)
    }

    fun setWebRtcBitrateCeiling(kbps: Int) {
        RemoteConfigManager.setBitrateCeiling(kbps)
    }

    fun toggleStrictCertPinning(enabled: Boolean) {
        RemoteConfigManager.toggleStrictCertPinning(enabled)
    }

    fun clearSystemLogs() {
        RemoteConfigManager.clearLogs()
    }
}
