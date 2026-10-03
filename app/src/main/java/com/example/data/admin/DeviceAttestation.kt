package com.example.data.admin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Device attestation for admin users.
 *
 * Collects the real hardware/software identifiers this device can actually
 * read and binds them to a paired-device record:
 *  - IMEI (TelephonyManager.imei; needs READ_PHONE_STATE; on Android 10+
 *    non-privileged apps get null — reported honestly, never faked)
 *  - ANDROID_ID (Settings.Secure — real, per-device)
 *  - Build.FINGERPRINT / MODEL / MANUFACTURER (real build constants)
 *
 * Flow: `attest` shows local attestation. `attest send <peer>` seals it in
 * an encrypted envelope (kind "attest") to a paired peer. The receiver
 * stores the claim. `attest verify <peer>` compares the stored claim against
 * a freshly received one — mismatch means the peer's hardware changed
 * (reinstall, new phone, or cloning) and the admin should re-pair.
 *
 * Remote `attest` agent requests are answered ONLY for admin-trust peers
 * (trustLevel >= 100); everyone else gets "admin peers only".
 */
object DeviceAttestation {

    const val ADMIN_TRUST_LEVEL = 100

    private const val PREFS = "gkk_attestation"
    private const val K_PREFIX = "claim_"

    data class Claim(
        val imei: String?,
        val androidId: String?,
        val fingerprint: String?,
        val model: String?,
        val manufacturer: String?,
        val claimedAt: Long
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("imei", imei)
            .put("androidId", androidId)
            .put("fingerprint", fingerprint)
            .put("model", model)
            .put("manufacturer", manufacturer)
            .put("claimedAt", claimedAt)

        fun summary(): String = buildString {
            appendLine("IMEI:         ${imei ?: "UNAVAILABLE (needs READ_PHONE_STATE / privileged)"}")
            appendLine("ANDROID_ID:   ${androidId ?: "UNAVAILABLE"}")
            appendLine("fingerprint:  ${fingerprint ?: "?"}")
            appendLine("model:        ${manufacturer ?: "?"} ${model ?: "?"}")
            append("claimed at:   ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(java.util.Date(claimedAt))}")
        }.toString()

        /** Stable identity key: what we compare for change detection. */
        fun identityKey(): String =
            listOf(imei ?: "-", androidId ?: "-", fingerprint ?: "-").joinToString("|")

        companion object {
            fun fromJson(o: JSONObject): Claim = Claim(
                imei = o.optString("imei").ifBlank { null },
                androidId = o.optString("androidId").ifBlank { null },
                fingerprint = o.optString("fingerprint").ifBlank { null },
                model = o.optString("model").ifBlank { null },
                manufacturer = o.optString("manufacturer").ifBlank { null },
                claimedAt = o.optLong("claimedAt", 0)
            )
        }
    }

    /** Read this device's real identifiers. Never invents values. */
    fun collectLocal(ctx: Context): Claim {
        var imei: String? = null
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                imei = if (Build.VERSION.SDK_INT >= 26) {
                    try {
                        tm?.imei?.ifBlank { null }
                    } catch (e: SecurityException) {
                        null
                    }
                } else {
                    @Suppress("DEPRECATION")
                    try {
                        tm?.deviceId?.ifBlank { null }
                    } catch (e: SecurityException) {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            imei = null
        }
        val androidId = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
        return Claim(
            imei = imei,
            androidId = androidId,
            fingerprint = Build.FINGERPRINT,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            claimedAt = System.currentTimeMillis()
        )
    }

    fun storeClaim(ctx: Context, deviceIdHex: String, claim: Claim) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(K_PREFIX + deviceIdHex, claim.toJson().toString())
            .apply()
    }

    fun getClaim(ctx: Context, deviceIdHex: String): Claim? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(K_PREFIX + deviceIdHex, null) ?: return null
        return try {
            Claim.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun clearClaim(ctx: Context, deviceIdHex: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(K_PREFIX + deviceIdHex).apply()
    }

    /**
     * Compare a fresh claim against the stored one for change detection.
     * Returns a human-readable verdict.
     */
    fun verifyAgainstStored(ctx: Context, deviceIdHex: String, fresh: Claim): String {
        val stored = getClaim(ctx, deviceIdHex)
            ?: return "no stored attestation for this peer — run 'attest send' first, " +
                "or the peer never sent one"
        return if (stored.identityKey() == fresh.identityKey()) {
            "MATCH — hardware identity unchanged since " +
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                    .format(java.util.Date(stored.claimedAt))
        } else {
            "MISMATCH — peer hardware identity CHANGED.\n" +
                "stored: ${stored.summary()}\n--- fresh claim ---\n${fresh.summary()}\n" +
                "Possible new phone, factory reset, or cloning. Re-pair before trusting."
        }
    }
}
