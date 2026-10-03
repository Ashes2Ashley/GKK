package com.example.data.net

import java.net.NetworkInterface

/**
 * IP intelligence: real local interface enumeration + public IP / NAT type
 * via STUN (no HTTP, no third-party "what's my IP" service).
 *
 * Admin tool: the local console operator is the admin of their own device.
 */
object NetIntel {

    data class Report(
        val localIps: List<String>,
        val publicIp: String?,
        val publicPort: Int?,
        val natType: String,
        val detail: String
    )

    /** All non-loopback IPv4/IPv6 addresses on real interfaces. */
    fun localIps(): List<String> {
        val out = mutableListOf<String>()
        try {
            val ifs = NetworkInterface.getNetworkInterfaces() ?: return out
            while (ifs.hasMoreElements()) {
                val ni = ifs.nextElement()
                try {
                    if (!ni.isUp || ni.isLoopback) continue
                } catch (e: Exception) {
                    continue
                }
                val addrs = ni.addresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (!a.isLoopbackAddress) out.add("${ni.name}: ${a.hostAddress}")
                }
            }
        } catch (e: Exception) {
            // No interfaces readable; caller sees the empty list honestly.
        }
        return out.sorted()
    }

    fun firstLocalIpv4(): String? =
        localIps().firstOrNull { it.contains(".") }?.substringAfter(": ")?.trim()

    /**
     * Full probe: local interfaces + STUN public mapping + NAT class.
     * Blocking (UDP timeouts); call off the main thread.
     */
    fun probe(): Report {
        val locals = localIps()
        val nat = StunClient.classifyNat(firstLocalIpv4())
        return Report(locals, nat.publicIp, nat.publicPort, nat.natType, nat.detail)
    }

    fun format(r: Report): String = buildString {
        appendLine("== IP intelligence ==")
        if (r.localIps.isEmpty()) appendLine("local:  (no interfaces readable)")
        else r.localIps.forEach { appendLine("local:  $it") }
        appendLine("public: ${r.publicIp ?: "unresolved"}${r.publicPort?.let { ":$it" } ?: ""}")
        appendLine("NAT:    ${r.natType}")
        append("note:   ${r.detail}")
    }.toString()
}
