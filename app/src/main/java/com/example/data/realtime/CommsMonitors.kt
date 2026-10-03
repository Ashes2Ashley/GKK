package com.example.data.realtime

import android.content.Context
import com.example.data.dispatch.AutomationEngine
import com.example.data.dispatch.DispatchManager
import com.example.data.dispatch.HeartbeatManager
import com.example.data.dispatch.InboxStore
import com.example.data.kali.KaliEnvironmentManager
import com.example.data.sms.DataSmsTransport

/**
 * Comms monitors: live telemetry of the secure channel itself. Every reading
 * comes from a real StateFlow fed by real events — no polling of fiction.
 */

/** Live stream of verified inbound chat messages. */
object ChatFeedMonitor : BaseMonitor("chat") {
    override fun start(ctx: Context) = launchLoop(2000) {
        val msgs = InboxStore.messages.value.filter { it.kind == "msg" }
        val last = msgs.lastOrNull()
        emit(
            "${msgs.size} message(s)",
            last?.let { "${it.senderName}: ${it.text.take(80)}" } ?: "no verified messages yet"
        )
    }
}

/** Live data-SMS traffic log (segments sent / received / failed). */
object SmsTrafficMonitor : BaseMonitor("sms") {
    override fun start(ctx: Context) = launchLoop(2000) {
        val evs = DataSmsTransport.events.value
        val last = evs.lastOrNull()
        val out = evs.count { it.direction == "OUT" }
        val inn = evs.count { it.direction == "IN" }
        emit(
            "out $out · in $inn",
            last?.let { "${it.direction} ${it.phone}: ${it.detail.take(90)}" }
                ?: "no SMS traffic yet"
        )
    }
}

/** Composite link ticker: best available bearer, live. */
object LinkTickerMonitor : BaseMonitor("link") {
    override fun start(ctx: Context) = launchLoop(3000) {
        val wifi = MonitorRegistry.get("wifi")?.reading?.value
        val cell = MonitorRegistry.get("cell")?.reading?.value
        val parts = mutableListOf<String>()
        wifi?.let {
            if (it.status == MonitorStatus.OK && !it.summary.contains("not connected") &&
                !it.summary.contains("off") && it.summary != "—"
            ) parts.add("wifi ${it.summary}")
        }
        cell?.let {
            if (it.status == MonitorStatus.OK && !it.summary.contains("permission") &&
                it.summary != "—"
            ) parts.add("cell ${it.summary}")
        }
        emit(
            parts.joinToString(" · ").ifEmpty { "no link" },
            "live composite of wifi + cell monitors"
        )
    }
}

/** Live tail of the operator console output. */
object ConsoleStreamMonitor : BaseMonitor("console") {
    override fun start(ctx: Context) = launchLoop(2000) {
        val last = KaliEnvironmentManager.terminalBuffer.value.lastOrNull()
        emit(
            last?.let { "$ ${it.command.take(40)}" } ?: "console idle",
            last?.output?.take(140) ?: ""
        )
    }
}

/** Live automation/autopilot event feed. */
object AlertFeedMonitor : BaseMonitor("alerts") {
    override fun start(ctx: Context) = launchLoop(2000) {
        val evs = AutomationEngine.events.value
        val last = evs.lastOrNull()
        emit(
            "${evs.size} alert(s)",
            last?.let { "[${it.severity}] ${it.text.take(110)}" } ?: "no alerts"
        )
    }
}

/** Last measured encrypted dispatch → reply round-trip. */
object RoundTripMonitor : BaseMonitor("rtt") {
    override fun start(ctx: Context) = launchLoop(2000) {
        val ms = DispatchManager.lastRoundTripMs.value
        emit(
            ms?.let { "$it ms" } ?: "no dispatches yet",
            "last encrypted dispatch → reply"
        )
    }
}

/** Live fleet proof-of-life board. */
object FleetBoardMonitor : BaseMonitor("fleet") {
    override fun start(ctx: Context) = launchLoop(5000) {
        val board = HeartbeatManager.board.value
        val now = System.currentTimeMillis()
        val online = board.count { now - it.lastSeenAt < 120_000 }
        emit(
            "$online/${board.size} online",
            board.joinToString(" · ") {
                "${it.name}: ${(now - it.lastSeenAt) / 1000}s ago"
            }.ifEmpty { "no heartbeats yet" }
        )
    }
}
