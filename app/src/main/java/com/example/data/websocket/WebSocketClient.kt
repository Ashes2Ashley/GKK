package com.example.data.websocket

import com.example.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

enum class WebSocketStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CLOSING,
    ERROR
}

data class WebSocketMessage(
    val id: String = java.util.UUID.randomUUID().toString().take(6),
    val text: String,
    val isIncoming: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

object AppWebSocketManager {

    private val _status = MutableStateFlow(WebSocketStatus.DISCONNECTED)
    val status: StateFlow<WebSocketStatus> = _status.asStateFlow()

    private val _messages = MutableStateFlow<List<WebSocketMessage>>(emptyList())
    val messages: StateFlow<List<WebSocketMessage>> = _messages.asStateFlow()

    private var activeSocket: WebSocket? = null

    fun connect(url: String = "wss://echo.websocket.org") {
        if (_status.value == WebSocketStatus.CONNECTED || _status.value == WebSocketStatus.CONNECTING) return

        _status.value = WebSocketStatus.CONNECTING
        val request = Request.Builder().url(url).build()

        activeSocket = RetrofitClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _status.value = WebSocketStatus.CONNECTED
                addMessage("Connected to $url (TLS WebSocket Handshake OK)", isIncoming = true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                addMessage(text, isIncoming = true)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                addMessage("[Binary Payload: ${bytes.size} bytes]", isIncoming = true)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _status.value = WebSocketStatus.CLOSING
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _status.value = WebSocketStatus.DISCONNECTED
                addMessage("Socket closed: $reason ($code)", isIncoming = true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _status.value = WebSocketStatus.ERROR
                addMessage("Socket error: ${t.localizedMessage}", isIncoming = true)
            }
        })
    }

    fun sendMessage(text: String): Boolean {
        val socket = activeSocket
        return if (socket != null && _status.value == WebSocketStatus.CONNECTED) {
            val sent = socket.send(text)
            if (sent) {
                addMessage(text, isIncoming = false)
            }
            sent
        } else {
            false
        }
    }

    fun disconnect() {
        activeSocket?.close(1000, "User disconnected")
        activeSocket = null
        _status.value = WebSocketStatus.DISCONNECTED
    }

    private fun addMessage(text: String, isIncoming: Boolean) {
        val msg = WebSocketMessage(text = text, isIncoming = isIncoming)
        val current = _messages.value
        _messages.value = (listOf(msg) + current).take(50)
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}
