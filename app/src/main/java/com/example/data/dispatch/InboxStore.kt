package com.example.data.dispatch

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Verified inbound messages. ONLY envelopes that passed full Envelope v1
 * verification (signature, addressing, replay, freshness) ever land here —
 * see [EnvelopeRouter]. Persisted as JSON in app-private storage, capped at
 * 200 entries. Exposed as a StateFlow so the chat feed monitor and UI update
 * in real time.
 */
data class InboxMessage(
    val id: String,
    val at: Long,
    val senderIdHex: String,
    val senderName: String,
    val kind: String, // msg | cmd | result | hb | sos | dropped | raw
    val text: String
)

object InboxStore {

    private const val MAX = 200

    private val _messages = MutableStateFlow<List<InboxMessage>>(emptyList())
    val messages: StateFlow<List<InboxMessage>> = _messages.asStateFlow()

    @Volatile
    private var dir: File? = null

    fun init(ctx: Context) {
        if (dir != null) return
        dir = ctx.applicationContext.filesDir
        load()
    }

    @Synchronized
    fun add(msg: InboxMessage) {
        _messages.value = (_messages.value + msg).takeLast(MAX)
        save()
    }

    @Synchronized
    fun clear() {
        _messages.value = emptyList()
        save()
    }

    private fun file() = File(dir, "gkk_inbox.json")

    private fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            _messages.value = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                InboxMessage(
                    o.getString("id"), o.getLong("at"), o.getString("sender"),
                    o.getString("name"), o.getString("kind"), o.getString("text")
                )
            }.takeLast(MAX)
        } catch (e: Exception) {
            // Corrupt store: start fresh rather than crash.
        }
    }

    private fun save() {
        try {
            val arr = JSONArray()
            _messages.value.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("at", it.at)
                        .put("sender", it.senderIdHex).put("name", it.senderName)
                        .put("kind", it.kind).put("text", it.text)
                )
            }
            file().writeText(arr.toString())
        } catch (e: Exception) {
            // Best effort; the in-memory list is authoritative.
        }
    }
}
