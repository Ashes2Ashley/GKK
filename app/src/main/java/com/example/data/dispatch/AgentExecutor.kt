package com.example.data.dispatch

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.net.InetAddress

/**
 * Agent mode: this device executes commands received from PAIRED devices and
 * returns encrypted results. Disabled by default — the user must explicitly
 * enable it (console: `agent on`).
 *
 * The command whitelist is deliberately tiny and read-only: nothing here can
 * change device state, read private data, or touch other apps. Anything else
 * is rejected before it runs.
 */
object AgentExecutor {

    @Volatile
    var enabled: Boolean = false

    /**
     * [senderTrustLevel] is the paired peer's trust level (0 default,
     * >= 100 admin). Privileged commands check it.
     */
    fun execute(
        ctx: Context, cmd: String, args: JSONObject, senderTrustLevel: Int = 0
    ): JSONObject {
        if (!enabled) return err("agent mode is disabled on this device")
        return try {
            when (cmd) {
                "ping" -> {
                    val host = args.optString("host", "")
                    if (host.isBlank()) return err("missing 'host'")
                    val t0 = System.currentTimeMillis()
                    val ok = InetAddress.getByName(host).isReachable(5000)
                    JSONObject()
                        .put("ok", ok)
                        .put("ms", System.currentTimeMillis() - t0)
                        .put("host", host)
                }
                "dns" -> {
                    val host = args.optString("host", "")
                    if (host.isBlank()) return err("missing 'host'")
                    val addrs = InetAddress.getAllByName(host).map { it.hostAddress ?: "?" }
                    JSONObject().put("host", host).put("addrs", addrs.joinToString(","))
                }
                "sysinfo" -> JSONObject()
                    .put("model", Build.MODEL)
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("android", Build.VERSION.RELEASE)
                    .put("sdk", Build.VERSION.SDK_INT)
                "attest" -> {
                    // Admin peers only: returns this device's hardware claim.
                    if (senderTrustLevel < com.example.data.admin.DeviceAttestation.ADMIN_TRUST_LEVEL) {
                        return err("admin peers only (trustLevel >= 100)")
                    }
                    JSONObject().put(
                        "attestation",
                        com.example.data.admin.DeviceAttestation.collectLocal(ctx).toJson()
                    )
                }
                else -> err("not whitelisted: $cmd (allowed: ping, dns, sysinfo, attest[admin])")
            }
        } catch (e: Exception) {
            err(e.message ?: "failed")
        }
    }

    private fun err(m: String) = JSONObject().put("error", m)
}
