package com.example.data.secure

import android.content.Context
import android.content.SharedPreferences

/**
 * Paired devices: the trust roots of the whole system.
 *
 * A device is trusted iff the user paired it (QR scan + 4-word verification).
 * For each peer we persist:
 *  - their P-256 public key (the ONLY thing that authenticates them),
 *  - their phone number (SMS bearer addressing),
 *  - lastSeqSent / lastSeqSeen (replay protection cursors for [EnvelopeCrypto]).
 *
 * Storage: private SharedPreferences, one entry per device. The secrets that
 * matter (our private key) live in the Android Keystore, never here.
 * All methods are synchronized — pairing, sending, and the SMS receiver run
 * on different threads.
 */
data class PairedDevice(
    val idHex: String,        // 16 hex chars, EnvelopeCrypto.deviceIdHex
    val name: String,         // user label, e.g. "Field Unit 3"
    val publicKeyB64: String, // X.509 P-256, Base64
    val phoneNumber: String,  // E.164, for the SMS bearer
    val lastSeqSent: Long = 0,
    val lastSeqSeen: Long = 0,
    // Trust level: 0 = standard paired peer, >= 100 = admin peer.
    // Admin peers may request device attestation and other privileged
    // agent commands. Set explicitly by the local operator; never 100
    // by default. Console: `trustlevel <name> <0-100>`.
    val trustLevel: Int = 0
)

class DeviceRegistry(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gkk_paired_devices", Context.MODE_PRIVATE)

    private fun key(idHex: String) = "dev_" + idHex

    private fun serialize(d: PairedDevice): String =
        listOf(
            d.name.replace("\t", " "),
            d.publicKeyB64,
            d.phoneNumber.replace("\t", " "),
            d.lastSeqSent.toString(),
            d.lastSeqSeen.toString(),
            d.trustLevel.toString()
        ).joinToString("\t")

    private fun deserialize(idHex: String, raw: String): PairedDevice? {
        val parts = raw.split("\t")
        // v1 rows had 5 fields; v2 adds trustLevel. Old rows load as level 0.
        if (parts.size != 5 && parts.size != 6) return null
        return PairedDevice(
            idHex = idHex,
            name = parts[0],
            publicKeyB64 = parts[1],
            phoneNumber = parts[2],
            lastSeqSent = parts[3].toLongOrNull() ?: 0,
            lastSeqSeen = parts[4].toLongOrNull() ?: 0,
            trustLevel = if (parts.size == 6) parts[5].toIntOrNull() ?: 0 else 0
        )
    }

    @Synchronized
    fun all(): List<PairedDevice> =
        prefs.all.keys
            .filter { it.startsWith("dev_") }
            .mapNotNull { k ->
                val raw = prefs.getString(k, null) ?: return@mapNotNull null
                deserialize(k.removePrefix("dev_"), raw)
            }
            .sortedBy { it.name }

    @Synchronized
    fun get(idHex: String): PairedDevice? {
        val raw = prefs.getString(key(idHex), null) ?: return null
        return deserialize(idHex, raw)
    }

    /** Add or replace a paired device. Returns false if the public key is invalid. */
    @Synchronized
    fun addOrUpdate(device: PairedDevice): Boolean {
        if (!DeviceKeys.isValidPeerKey(device.publicKeyB64)) return false
        prefs.edit().putString(key(device.idHex), serialize(device)).apply()
        return true
    }

    @Synchronized
    fun remove(idHex: String) {
        prefs.edit().remove(key(idHex)).apply()
    }

    /**
     * Next sequence number for an outbound envelope to this device.
     * Monotonic per device, persisted immediately (a reboot must never reuse
     * a seq or the receiver will reject it as a replay).
     */
    @Synchronized
    fun nextSeqToSend(idHex: String): Long {
        val d = get(idHex) ?: throw IllegalArgumentException("unknown device $idHex")
        val next = d.lastSeqSent + 1
        prefs.edit().putString(key(idHex), serialize(d.copy(lastSeqSent = next))).apply()
        return next
    }

    /** Record the highest verified inbound seq from this device. */
    @Synchronized
    fun recordSeqSeen(idHex: String, seq: Long) {
        val d = get(idHex) ?: return
        if (seq > d.lastSeqSeen) {
            prefs.edit().putString(key(idHex), serialize(d.copy(lastSeqSeen = seq))).apply()
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Set a peer's trust level (0-100+). Returns false for unknown device. */
    @Synchronized
    fun setTrustLevel(idHex: String, level: Int): Boolean {
        val d = get(idHex) ?: return false
        prefs.edit().putString(key(idHex), serialize(d.copy(trustLevel = level))).apply()
        return true
    }
}
