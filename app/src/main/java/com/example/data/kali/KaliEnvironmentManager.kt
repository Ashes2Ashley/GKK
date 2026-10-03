package com.example.data.kali

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.admin.DeviceAttestation
import com.example.data.bearer.BearerConfig
import com.example.data.bearer.BearerManager
import com.example.data.net.MdnsDiscovery
import com.example.data.net.NetIntel
import com.example.data.voice.VoiceCallManager
import com.example.data.webrtc.WebRtcBearer
import com.example.data.dispatch.AgentExecutor
import com.example.data.dispatch.AutomationEngine
import com.example.data.dispatch.DispatchManager
import com.example.data.dispatch.HeartbeatManager
import com.example.data.dispatch.InboxStore
import com.example.data.realtime.MonitorRegistry
import com.example.data.secure.DeviceKeys
import com.example.data.secure.EnvelopeCrypto
import com.example.data.secure.PairedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The operator console. LAW: every command executes real logic against real
 * APIs. Unknown or unavailable commands fail honestly — nothing here prints
 * fiction. The old canned outputs (fake nmap, fake airmon, fake kernel
 * strings) are deleted, not decorated.
 *
 * Command reference (also printed by `help`):
 *   sysinfo · netstat · ps · ping <host> · dns <host> · rdns <ip> ·
 *   scan <host> <p1,p2,..> · id · trust <b64> <name> <phone> · devices ·
 *   dispatch <target> <text> · runcmd <target> <cmd> [argsJson] ·
 *   inbox [n] · heartbeats · monitor <name> · monitors · beacon ·
 *   agent on|off · clear · help
 */
data class KaliLogEntry(
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
    val command: String,
    val output: String,
    val exitCode: Int = 0
)

object KaliEnvironmentManager {

    private val _terminalBuffer = MutableStateFlow<List<KaliLogEntry>>(emptyList())
    val terminalBuffer: StateFlow<List<KaliLogEntry>> = _terminalBuffer.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var appCtx: Context? = null

    @Volatile
    private var booted = false

    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        DispatchManager.init(appCtx!!)
        AutomationEngine.startWatchdog()
    }

    fun boot() {
        if (booted) return
        booted = true
        scope.launch {
            val idLine = try {
                val kp = DeviceKeys.getOrCreateIdentity()
                val fp = EnvelopeCrypto.deviceIdHex(kp.public)
                "identity: $fp (${if (DeviceKeys.isKeystoreBacked) "keystore" else "MEMORY ONLY — re-pair later"})"
            } catch (e: Exception) {
                "identity: unavailable (${e.message})"
            }
            val dev = "${Build.MANUFACTURER} ${Build.MODEL} · android ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})"
            append(
                "boot",
                "GKK secure console — every command below executes real logic.\n" +
                    "device: $dev\n$idLine\n" +
                    "type 'help' for commands."
            )
        }
    }

    fun executeCommand(rawCommand: String) {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return
        if (cmd.length > 2000) {
            append(cmd.take(60) + "\u2026", "command too long (max 2000 chars)")
            return
        }
        if (cmd == "clear") {
            _terminalBuffer.value = emptyList()
            return
        }
        scope.launch {
            val out = try {
                runCommand(cmd)
            } catch (e: Exception) {
                "error: ${e.message ?: e.javaClass.simpleName}"
            }
            append(cmd, out)
        }
    }

    private fun append(command: String, output: String, exitCode: Int = 0) {
        _terminalBuffer.value = (_terminalBuffer.value +
            KaliLogEntry(command = command, output = output, exitCode = exitCode))
            .takeLast(300)
    }

    // ------------------------------------------------------------------ router

    private fun runCommand(cmd: String): String {
        val parts = cmd.split(Regex("\\s+"))
        // 50 built-in ops tools live in OpsTools; null = not an ops tool.
        OpsTools.dispatch(ctx(), parts[0], parts.drop(1))?.let { return it }
        return when (parts[0].lowercase()) {
            "help" -> HELP
            "sysinfo" -> sysinfo()
            "netstat" -> netstat()
            "ps" -> ps()
            "ping" -> {
                val host = parts.getOrNull(1) ?: return "usage: ping <host> [count]"
                val count = parts.getOrNull(2)?.toIntOrNull() ?: 4
                ping(host, count.coerceIn(1, 10))
            }
            "dns" -> {
                val host = parts.getOrNull(1) ?: return "usage: dns <host>"
                dns(host)
            }
            "rdns" -> {
                val ip = parts.getOrNull(1) ?: return "usage: rdns <ip>"
                rdns(ip)
            }
            "scan" -> {
                if (parts.size < 3) return "usage: scan <host> <port1,port2,..>"
                scan(parts[1], parts[2])
            }
            "id" -> identity()
            "trust" -> {
                if (parts.size < 4) return "usage: trust <base64-pubkey> <name> <phone>"
                trust(parts[1], parts[2], parts[3])
            }
            "devices" -> devices()
            "dispatch" -> {
                if (parts.size < 3) return "usage: dispatch <device-id-or-name> <message…>"
                val target = parts[1]
                val text = cmd.substringAfter(target).trim()
                dispatch(target, text)
            }
            "runcmd" -> {
                if (parts.size < 4) return "usage: runcmd <device-id-or-name> <cmd> [argsJson]"
                val target = parts[1]
                val remoteCmd = parts[2]
                val argsJson = cmd.substringAfter(remoteCmd).trim().ifEmpty { "{}" }
                runcmd(target, remoteCmd, argsJson)
            }
            "inbox" -> {
                val n = parts.getOrNull(1)?.toIntOrNull() ?: 10
                inbox(n.coerceIn(1, 50))
            }
            "heartbeats" -> heartbeats()
            "monitor" -> {
                val name = parts.getOrNull(1) ?: return "usage: monitor <name>  (try: monitors)"
                monitor(name)
            }
            "monitors" -> monitors()
            "beacon" -> beacon()
            "agent" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "on" -> {
                        AgentExecutor.enabled = true
                        "agent mode ENABLED (whitelist: ping, dns, sysinfo)"
                    }
                    "off" -> {
                        AgentExecutor.enabled = false
                        "agent mode disabled"
                    }
                    else -> "usage: agent on|off   (currently ${if (AgentExecutor.enabled) "ON" else "OFF"})"
                }
            }
            "call" -> {
                val number = parts.getOrNull(1) ?: return "usage: call <number>"
                call(number)
            }
            "hangup" -> hangup()
            "mute" -> mute()
            "speaker" -> speaker()
            "calls" -> calls()
            "comms" -> comms()
            "bearer" -> bearer(parts.drop(1))
            "rtc" -> rtc(parts.drop(1))
            "netintel" -> netintel()
            "attest" -> attest(parts.drop(1))
            "trustlevel" -> {
                val name = parts.getOrNull(1) ?: return "usage: trustlevel <name> <0-100+>"
                val level = parts.getOrNull(2)?.toIntOrNull()
                    ?: return "usage: trustlevel <name> <0-100+>"
                trustlevel(name, level)
            }
            else -> "unknown command: '${parts[0]}' — type 'help'. (Fiction was removed from this console.)"
        }
    }

    // ------------------------------------------------------------------ commands

    private fun ctx(): Context =
        appCtx ?: throw IllegalStateException("console not initialized")

    private fun sysinfo(): String {
        val c = ctx()
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalGb = mi.totalMem / 1024.0 / 1024.0 / 1024.0
        return buildString {
            appendLine("model:        ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("android:      ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
            appendLine("abi:          ${Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLine("ram total:    ${"%.2f".format(totalGb)} GB")
            appendLine("ram low:      ${mi.lowMemory}")
            appendLine("fingerprint:  ${Build.FINGERPRINT.take(60)}…")
        }.trim()
    }

    private fun netstat(): String {
        val out = StringBuilder()
        var lines = 0
        for (path in listOf("/proc/net/tcp", "/proc/net/tcp6")) {
            val f = File(path)
            if (!f.canRead()) {
                out.appendLine("$path: unreadable")
                continue
            }
            for (line in f.readLines().drop(1)) {
                val p = line.trim().split(Regex("\\s+"))
                if (p.size < 4) continue
                out.appendLine("${hexAddr(p[1])} -> ${hexAddr(p[2])}  ${tcpState(p[3])}")
                if (++lines >= 40) break
            }
            if (lines >= 40) break
        }
        return if (lines == 0) "no connections visible" else out.trim().toString()
    }

    private fun hexAddr(s: String): String {
        val ipHex = s.substringBefore(":")
        val port = s.substringAfter(":").toIntOrNull(16) ?: -1
        val ip = if (ipHex.length == 8) {
            val v = ipHex.toLongOrNull(16) ?: 0L
            "${v and 0xFF}.${(v shr 8) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 24) and 0xFF}"
        } else if (ipHex.length == 32) {
            (0 until 4).joinToString(":") { i ->
                val g = ipHex.substring(i * 8, i * 8 + 8)
                val rev = (6 downTo 0 step 2).map { g.substring(it, it + 2) }.joinToString("")
                rev.substring(0, 4) + ":" + rev.substring(4, 8)
            }
        } else {
            ipHex
        }
        return "$ip:$port"
    }

    private fun tcpState(code: String): String = when (code.uppercase()) {
        "01" -> "ESTABLISHED"; "02" -> "SYN_SENT"; "03" -> "SYN_RECV"
        "04" -> "FIN_WAIT1"; "05" -> "FIN_WAIT2"; "06" -> "TIME_WAIT"
        "07" -> "CLOSE"; "08" -> "CLOSE_WAIT"; "09" -> "LAST_ACK"
        "0A" -> "LISTEN"; "0B" -> "CLOSING"; else -> code
    }

    private fun ps(): String {
        val am = ctx().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val procs = am.runningAppProcesses ?: return "process list unavailable"
        return procs.take(30).joinToString("\n") {
            "${it.pid}  ${it.processName}  [${importance(it.importance)}]"
        }
    }

    private fun importance(i: Int): String = when (i) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "fg"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
        else -> "$i"
    }

    private fun ping(host: String, count: Int): String {
        return try {
            val proc = ProcessBuilder("ping", "-c", count.toString(), "-W", "2", host)
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(20, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return "ping $host: timed out"
            }
            val out = proc.inputStream.bufferedReader().readText().trim()
            val tail = out.lines().takeLast(3).joinToString("\n")
            "ping $host (exit ${proc.exitValue()}):\n$tail"
        } catch (e: Exception) {
            "ping failed: ${e.message}"
        }
    }

    private fun dns(host: String): String {
        return try {
            InetAddress.getAllByName(host).joinToString("\n") {
                "${it.hostAddress}  (${it.hostName})"
            }
        } catch (e: Exception) {
            "dns failed for '$host': ${e.message}"
        }
    }

    private fun rdns(ip: String): String {
        return try {
            val a = InetAddress.getByName(ip)
            "$ip -> ${a.hostName}"
        } catch (e: Exception) {
            "reverse dns failed for '$ip': ${e.message}"
        }
    }

    private fun scan(host: String, portsCsv: String): String {
        val ports = portsCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull()?.takeIf { p -> p in 1..65535 } }
        if (ports.isEmpty()) return "no valid ports in '$portsCsv'"
        if (ports.size > 64) return "max 64 ports per scan"
        val addr = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            return "cannot resolve '$host': ${e.message}"
        }
        val sb = StringBuilder()
        for (p in ports) {
            val t0 = System.currentTimeMillis()
            val open = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(addr, p), 1500)
                    true
                }
            } catch (e: Exception) {
                false
            }
            val ms = System.currentTimeMillis() - t0
            sb.appendLine("$host:$p  ${if (open) "OPEN   (${ms}ms)" else "closed"}")
        }
        return sb.trim().toString()
    }

    private fun identity(): String {
        return try {
            val kp = DeviceKeys.getOrCreateIdentity()
            val fp = EnvelopeCrypto.deviceIdHex(kp.public)
            val words = DeviceKeys.verificationWords(kp.public)
            "device id:  $fp\n" +
                "words:      $words   (read these to your peer when pairing)\n" +
                "pubkey:     ${DeviceKeys.exportPublicKeyB64(kp.public)}\n" +
                "keystore:   ${if (DeviceKeys.isKeystoreBacked) "hardware-backed" else "MEMORY ONLY"}"
        } catch (e: Exception) {
            "identity unavailable: ${e.message}"
        }
    }

    private fun trust(b64: String, name: String, phone: String): String {
        return try {
            val pub = DeviceKeys.importPeerPublicKey(b64)
            val idHex = EnvelopeCrypto.deviceIdHex(pub)
            val ok = DispatchManager.registry().addOrUpdate(
                PairedDevice(idHex, name, DeviceKeys.exportPublicKeyB64(pub), phone)
            )
            if (!ok) return "invalid public key"
            val words = DeviceKeys.verificationWords(pub)
            AutomationEngine.raise("OK", "paired device: $name ($idHex)")
            "paired '$name'  id=$idHex\nverify words with peer: $words"
        } catch (e: Exception) {
            "pairing failed: ${e.message}"
        }
    }

    private fun devices(): String {
        val all = DispatchManager.registry().all()
        if (all.isEmpty()) return "no paired devices — use: trust <b64> <name> <phone>"
        return all.joinToString("\n") {
            "${it.idHex}  ${it.name}  ${it.phoneNumber}  (seq↑${it.lastSeqSent} ↓${it.lastSeqSeen})"
        }
    }

    private fun dispatch(target: String, text: String): String {
        if (text.isBlank()) return "nothing to send"
        if (text.toByteArray().size > 4000) return "message too long (max ~4000 bytes)"
        val dev = findDevice(target) ?: return "no such device: '$target' (try: devices)"
        val r = BearerManager.sendText(dev, text)
        return r.fold(
            onSuccess = { via -> "sent to ${dev.name} via $via — sealed Envelope v1" },
            onFailure = { e -> "dispatch failed: ${e.message}" }
        )
    }

    private fun runcmd(target: String, remoteCmd: String, argsJson: String): String {
        val dev = findDevice(target) ?: return "no such device: '$target' (try: devices)"
        val args = try {
            JSONObject(argsJson)
        } catch (e: Exception) {
            return "bad argsJson: ${e.message}"
        }
        val r = DispatchManager.dispatchJson(
            dev, "cmd",
            JSONObject().put("cmd", remoteCmd).put("args", args)
        )
        return r.fold(
            onSuccess = { "command '$remoteCmd' dispatched to ${dev.name} — reply arrives in inbox" },
            onFailure = { e -> "dispatch failed: ${e.message}" }
        )
    }

    private fun findDevice(target: String): PairedDevice? {
        val reg = DispatchManager.registry()
        reg.get(target)?.let { return it }
        return reg.all().firstOrNull {
            it.idHex.startsWith(target.lowercase()) || it.name.equals(target, ignoreCase = true)
        }
    }

    private fun inbox(n: Int): String {
        val msgs = InboxStore.messages.value.takeLast(n)
        if (msgs.isEmpty()) return "inbox empty — no verified inbound envelopes yet"
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        return msgs.joinToString("\n") {
            "[${fmt.format(Date(it.at))}] ${it.senderName} <${it.kind}> ${it.text.take(120)}"
        }
    }

    private fun heartbeats(): String {
        val board = HeartbeatManager.board.value
        if (board.isEmpty()) return "no heartbeats yet — start HeartbeatManager or wait for peers"
        val now = System.currentTimeMillis()
        return board.joinToString("\n") {
            val ageS = (now - it.lastSeenAt) / 1000
            val live = if (ageS < 120) "ONLINE " else "silent "
            "$live ${it.name} (${it.deviceIdHex.take(8)})  ${ageS}s ago  batt ${it.batteryPct}%"
        }
    }

    private fun monitor(name: String): String {
        val m = MonitorRegistry.get(name) ?: return "no such monitor: '$name' (try: monitors)"
        val r = m.reading.value
        return "${r.name} [${r.status}] ${r.summary}\n${r.detail}"
    }

    private fun monitors(): String {
        return MonitorRegistry.all.joinToString("\n") {
            val r = it.reading.value
            "${r.name}: [${r.status}] ${r.summary}"
        }
    }

    private fun call(number: String): String {
        val r = VoiceCallManager.placeCall(number)
        return r.fold(
            onSuccess = { "calling $number \u2026 (overlay appears if permitted)" },
            onFailure = { e -> "call failed: ${e.message}" }
        )
    }

    private fun hangup(): String {
        val r = VoiceCallManager.endCall()
        return r.fold(
            onSuccess = { "call ended" },
            onFailure = { e -> "hangup failed: ${e.message}" }
        )
    }

    private fun mute(): String {
        val to = !VoiceCallManager.isMuted()
        return VoiceCallManager.setMuted(to).fold(
            onSuccess = { if (to) "microphone muted" else "microphone live" },
            onFailure = { e -> "mute failed: ${e.message}" }
        )
    }

    private fun speaker(): String {
        val to = !VoiceCallManager.isSpeakerOn()
        return VoiceCallManager.setSpeaker(to).fold(
            onSuccess = { if (to) "speaker on" else "speaker off" },
            onFailure = { e -> "speaker failed: ${e.message}" }
        )
    }

    private fun calls(): String {
        val r = VoiceCallManager.recentCalls(10)
        return r.fold(
            onSuccess = { list ->
                if (list.isEmpty()) "no recent calls"
                else {
                    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
                    list.joinToString("\n") {
                        "${fmt.format(Date(it.date))}  ${it.type}  ${it.number}  (${it.durationSec}s)"
                    }
                }
            },
            onFailure = { e -> "call log unavailable: ${e.message}" }
        )
    }

    /**
     * Prefilled, confirmed SMS configuration report. Everything here is
     * baked in or probed live - nothing to configure by hand.
     */
    private fun comms(): String {
        val c = ctx()
        fun perm(p: String) =
            ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
        val tm = c.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simReady = try {
            tm?.simState == TelephonyManager.SIM_STATE_READY
        } catch (e: Exception) {
            false
        }
        val recvRegistered = c.packageManager.queryBroadcastReceivers(
            android.content.Intent("android.intent.action.DATA_SMS_RECEIVED"), 0
        ).any { it.activityInfo?.name?.contains("SmsEnvelopeReceiver") == true }
        val paired = try {
            DispatchManager.registry().all().size
        } catch (e: Exception) {
            -1
        }
        val ident = try {
            EnvelopeCrypto.deviceIdHex(DeviceKeys.getOrCreateIdentity().public)
        } catch (e: Exception) {
            "unavailable: ${e.message}"
        }
        val ok = { b: Boolean -> if (b) "CONFIRMED" else "MISSING" }
        return buildString {
            appendLine("== SMS bearer - prefilled configuration ==")
            appendLine("SEND_SMS permission:    ${ok(perm(Manifest.permission.SEND_SMS))}")
            appendLine("RECEIVE_SMS permission: ${ok(perm(Manifest.permission.RECEIVE_SMS))}")
            appendLine("SIM state:              ${if (simReady) "READY" else "NOT READY - SMS needs a SIM"}")
            appendLine("data-SMS receiver:      ${if (recvRegistered) "registered" else "NOT FOUND"}")
            appendLine("port:                   19841 (built-in, both ends)")
            appendLine("segments:               128 bytes (10 header + 118 payload)")
            appendLine("envelope:               v1 ECDH+HKDF+AES-256-GCM, anti-replay")
            appendLine("paired peers:           ${if (paired >= 0) paired else "registry unavailable"}")
            appendLine("identity:               $ident")
            appendLine("== voice ==")
            appendLine("CALL_PHONE:             ${ok(perm(Manifest.permission.CALL_PHONE))}")
            appendLine("call state:             ${VoiceCallManager.callState.value}")
            append("overlay permission:     ${if (android.provider.Settings.canDrawOverlays(c)) "granted" else "not granted - overlay disabled"}")
        }.toString()
    }

    // ---------- bearers ----------

    private fun bearer(args: List<String>): String {
        if (args.isEmpty()) return BearerConfig.report()
        return when (args[0]) {
            "advanced" -> {
                val on = args.getOrNull(1) ?: return "usage: bearer advanced on|off"
                BearerConfig.setAdvancedSmsSend(on == "on")
                "advanced SMS send ${if (on == "on") "ON" else "OFF"}"
            }
            "webrtc" -> {
                val on = args.getOrNull(1) ?: return "usage: bearer webrtc on|off"
                BearerConfig.setWebrtcFallback(on == "on")
                "WebRTC fallback ${if (on == "on") "ON" else "OFF"}"
            }
            "order" -> {
                val order = args.getOrNull(1)?.split(",") ?: return "usage: bearer order sms,webrtc"
                if (BearerConfig.setPriority(order)) "bearer priority: ${BearerConfig.priority.value.joinToString(" -> ")}"
                else "invalid order (use sms and/or webrtc)"
            }
            else -> "usage: bearer [advanced on|off] [webrtc on|off] [order sms,webrtc]"
        }
    }

    private fun rtc(args: List<String>): String {
        val sub = args.getOrNull(0) ?: return "usage: rtc discover|status|connect <name>|close <name>"
        return when (sub) {
            "discover" -> {
                val peers = MdnsDiscovery.peers.value
                if (peers.isEmpty()) {
                    "no WebRTC peers discovered on the LAN yet — both devices need the app " +
                        "running with IP connectivity"
                } else {
                    peers.values.joinToString("\n") {
                        "${it.idHex.take(8)}\u2026  ${it.ip}:${it.port}"
                    }
                }
            }
            "status" -> {
                val conns = WebRtcBearer.connections.value
                if (conns.isEmpty()) "no WebRTC sessions"
                else conns.entries.joinToString("\n") { (id, c) ->
                    val name = findDevice(id)?.name ?: id.take(8)
                    "$name: ${c.state} (${c.detail})"
                }
            }
            "connect" -> {
                val name = args.getOrNull(1) ?: return "usage: rtc connect <name>"
                val dev = findDevice(name) ?: return "no such device: '$name'"
                WebRtcBearer.connect(dev).fold(
                    onSuccess = { "connecting to ${dev.name}: $it" },
                    onFailure = { e -> "rtc connect failed: ${e.message}" }
                )
            }
            "close" -> {
                val name = args.getOrNull(1) ?: return "usage: rtc close <name>"
                val dev = findDevice(name) ?: return "no such device: '$name'"
                WebRtcBearer.close(dev.idHex)
                "closed WebRTC session to ${dev.name}"
            }
            else -> "usage: rtc discover|status|connect <name>|close <name>"
        }
    }

    private fun netintel(): String {
        return try {
            NetIntel.format(NetIntel.probe())
        } catch (e: Exception) {
            "netintel failed: ${e.message}"
        }
    }

    // ---------- attestation (admin) ----------

    private fun attest(args: List<String>): String {
        val c = ctx()
        val sub = args.getOrNull(0)
        if (sub == null) {
            // Local claim.
            return "== this device ==\n" + DeviceAttestation.collectLocal(c).summary()
        }
        val name = args.getOrNull(1) ?: return "usage: attest [send <name>|verify <name>]"
        val dev = findDevice(name) ?: return "no such device: '$name'"
        return when (sub) {
            "send" -> {
                val claim = DeviceAttestation.collectLocal(c)
                DispatchManager.dispatchJson(dev, "attest", claim.toJson()).fold(
                    onSuccess = { "attestation claim sent to ${dev.name} (encrypted)" },
                    onFailure = { e -> "attest send failed: ${e.message}" }
                )
            }
            "verify" -> {
                val stored = DeviceAttestation.getClaim(c, dev.idHex)
                    ?: return "no stored attestation for ${dev.name} — " +
                        "have them run 'attest send <your-name>' (or request via agent: runcmd)"
                "== stored claim for ${dev.name} ==\n" + stored.summary() +
                    "\n(fresh claims are change-detected automatically on arrival)"
            }
            else -> "usage: attest [send <name>|verify <name>]"
        }
    }

    private fun trustlevel(name: String, level: Int): String {
        if (level < 0) return "level must be >= 0"
        val dev = findDevice(name) ?: return "no such device: '$name'"
        return if (DispatchManager.registry().setTrustLevel(dev.idHex, level)) {
            "trust level for ${dev.name} set to $level" +
                (if (level >= DeviceAttestation.ADMIN_TRUST_LEVEL) " (ADMIN — may request attestation)" else "")
        } else {
            "failed to set trust level"
        }
    }

    private fun beacon(): String {
        val c = ctx()
        val loc = try {
            val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            return "beacon failed: location permission not granted"
        } catch (e: Exception) {
            return "beacon failed: ${e.message}"
        } ?: return "beacon failed: no location fix available"
        val devices = DispatchManager.registry().all()
        if (devices.isEmpty()) return "beacon failed: no paired devices"
        val locStr = "${"%.6f".format(loc.latitude)},${"%.6f".format(loc.longitude)}"
        var sent = 0
        val errs = mutableListOf<String>()
        for (dev in devices) {
            val r = DispatchManager.dispatchJson(
                dev, "sos",
                JSONObject().put("loc", locStr).put("batt", -1)
            )
            r.onSuccess { sent++ }.onFailure { e -> errs.add("${dev.name}: ${e.message}") }
        }
        AutomationEngine.raise("CRIT", "SOS beacon sent to $sent device(s) at $locStr")
        return buildString {
            appendLine("SOS beacon → $sent/${devices.size} devices @ $locStr")
            errs.forEach { appendLine("  FAILED: $it") }
        }.trim().toString()
    }

    companion object {
        private val HELP = """
            GKK secure console — every command is real.
            ── device ──────────────────────────────
            sysinfo              hardware / OS / memory (real)
            netstat              live TCP connections from /proc/net/tcp
            ps                   running app processes
            ── network ─────────────────────────────
            ping <host> [n]      ICMP ping via system ping binary
            dns <host>           resolve host → addresses
            rdns <ip>            reverse DNS
            scan <host> <p,..>   TCP connect scan (max 64 ports)
            ── secure channel ──────────────────────
            id                   show this device's id, words, pubkey
            trust <b64> <n> <ph> pair a peer (verify the 4 words aloud)
            devices              paired devices + replay cursors
            dispatch <t> <msg>   encrypted message → device (data SMS)
            runcmd <t> <c> [js]  remote command → paired device
            inbox [n]            verified inbound envelopes
            heartbeats           fleet proof-of-life board
            beacon               encrypted SOS + GPS → all paired
            agent on|off         allow paired devices to run commands here
            ── voice ─────────────────────────────
            call <number>        real carrier voice call
            hangup               end the current call
            mute / speaker       toggle mic mute / speakerphone
            calls                recent call log (real)
            comms                SMS+voice prefilled config self-check
            ops                  list all 50 built-in ops tools
            ── bearers ─────────────────────────────
            bearer               show bearer flags (SMS advanced, WebRTC fallback)
            bearer advanced on|off
            bearer webrtc on|off
            bearer order sms,webrtc
            rtc discover         mDNS peers on the LAN (real)
            rtc connect <name>   WebRTC data-channel fallback session
            rtc status|close <name>
            netintel             local IPs + public IP + NAT type (STUN)
            ── admin ───────────────────────────────
            attest               this device's hardware claim
            attest send <name>   send claim encrypted to a peer
            attest verify <name> show stored peer claim
            trustlevel <name> <n>  set peer trust (100+ = admin)
            ── observe ─────────────────────────────
            monitors             list the 15 live monitors
            monitor <name>       current reading of one monitor
            clear                clear the console
        """.trimIndent()
    }
}
