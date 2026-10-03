package com.example.data.webrtc

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LAN peer discovery for the WebRTC bearer, via mDNS (NsdManager).
 *
 * Each device advertises `_gkk-sig._udp` with its signaling port and a TXT
 * record carrying its Envelope v1 device ID. Discovery resolves advertised
 * peers to (ip, port) so WebRTC signaling can start without any central
 * server. Entries expire after 120s without re-resolution.
 *
 * Real Android NSD — no fake peer lists.
 */
object MdnsDiscovery {

    private const val TAG = "MdnsDiscovery"
    private const val SERVICE_TYPE = "_gkk-sig._udp."
    private const val EXPIRY_MS = 120_000L

    data class Peer(val idHex: String, val ip: String, val port: Int, val lastSeen: Long)

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()

    @Volatile
    private var nsd: NsdManager? = null
    @Volatile
    private var myIdHex: String? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun init(ctx: Context, idHex: String, signalPort: Int) {
        if (nsd != null) return
        myIdHex = idHex
        val manager = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: run {
                Log.w(TAG, "NsdManager unavailable on this device")
                return
            }
        nsd = manager
        register(manager, idHex, signalPort)
        discover(manager)
    }

    fun findFor(idHex: String): Peer? {
        val p = _peers.value[idHex] ?: return null
        return if (System.currentTimeMillis() - p.lastSeen > EXPIRY_MS) null else p
    }

    fun stop() {
        try {
            discoveryListener?.let { nsd?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(TAG, "stop discovery: ${e.message}")
        }
        try {
            registrationListener?.let { nsd?.unregisterService(it) }
        } catch (e: Exception) {
            Log.w(TAG, "unregister: ${e.message}")
        }
        discoveryListener = null
        registrationListener = null
        nsd = null
    }

    private fun register(manager: NsdManager, idHex: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "gkk-" + idHex.take(8)
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("id", idHex)
            setAttribute("v", "1")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(si: NsdServiceInfo) {
                Log.i(TAG, "advertised as ${si.serviceName}")
            }

            override fun onRegistrationFailed(si: NsdServiceInfo, code: Int) {
                Log.w(TAG, "advertise failed: $code")
            }

            override fun onServiceUnregistered(si: NsdServiceInfo) {}
            override fun onUnregistrationFailed(si: NsdServiceInfo, code: Int) {}
        }
        registrationListener = listener
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "registerService: ${e.message}")
        }
    }

    private fun discover(manager: NsdManager) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(si: NsdServiceInfo, code: Int) {
                Log.w(TAG, "resolve failed for ${si.serviceName}: $code")
            }

            override fun onServiceResolved(si: NsdServiceInfo) {
                try {
                    val id = si.attributes["id"]?.toString(Charsets.UTF_8) ?: return
                    if (id == myIdHex) return // ignore ourselves
                    val ip = si.host?.hostAddress ?: return
                    val peer = Peer(id, ip, si.port, System.currentTimeMillis())
                    _peers.value = _peers.value + (id to peer)
                    Log.i(TAG, "discovered peer ${id.take(8)}… at $ip:${si.port}")
                } catch (e: Exception) {
                    Log.w(TAG, "resolve handling: ${e.message}")
                }
            }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {
                Log.i(TAG, "mDNS discovery started")
            }

            override fun onServiceFound(si: NsdServiceInfo) {
                if (si.serviceType.contains("gkk-sig")) {
                    try {
                        nsd?.resolveService(si, resolveListener)
                    } catch (e: Exception) {
                        Log.w(TAG, "resolveService: ${e.message}")
                    }
                }
            }

            override fun onServiceLost(si: NsdServiceInfo) {
                // Best-effort: entries expire via findFor() anyway.
                Log.i(TAG, "service lost: ${si.serviceName}")
            }

            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, code: Int) {
                Log.w(TAG, "discovery start failed: $code")
            }

            override fun onStopDiscoveryFailed(t: String, code: Int) {}
        }
        discoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices: ${e.message}")
        }
    }
}
