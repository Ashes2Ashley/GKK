package com.example.data.sim

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class SimSlotId(val label: String) {
    SLOT_1_UICC("Slot 1 (Physical UICC - Ground Primary)"),
    SLOT_2_EUICC("Slot 2 (eUICC - Airborne Tactical eSIM)")
}

data class SimHardwareState(
    val slotId: SimSlotId = SimSlotId.SLOT_1_UICC,
    val iccidMasked: String = "8901410321111855••••",
    val imsiMasked: String = "31041012345••••",
    val carrierName: String = "Tactical Defense Private 5G",
    val simState: String = "READY (PIN_VERIFIED)",
    val otaSecurityDomainState: String = "OP_SEC_INITIALIZED (SCP03 / GlobalPlatform)",
    val nvramFreeBytes: Int = 184320,
    val activeProfileId: String = "PROFILE-GOC-5G-URLLC-SECURE"
)

enum class AdminPayloadType(val label: String, val category: String) {
    APN_PROVISIONING("Carrier APN & Gateway Configuration", "Network"),
    SLICE_QOS_POLICY("5G Network Slicing & QoS Policy", "5G Core"),
    CRYPTO_KEY_ROTATION("Session Key & Certificate Rotation", "Security"),
    AIRBORNE_TELEMETRY_DISPATCH("Airborne Flight Telemetry Envelope", "Airborne"),
    REMOTE_FIRMWARE_CONFIG("Modem Baseband OTA Parameter Tuning", "Hardware")
}

@JsonClass(generateAdapter = true)
data class EncryptedPayloadEnvelope(
    val envelopeId: String = UUID.randomUUID().toString().take(8),
    val payloadType: AdminPayloadType,
    val timestamp: Long = System.currentTimeMillis(),
    val nonceBase64: String,
    val authTagBase64: String,
    val ciphertextBase64: String,
    val hmacSignature: String,
    val senderIdentity: String = "ADMIN-GOC-CONSOLE",
    val targetIccid: String = "8901410321111855••••",
    val deliveryChannel: String = "OTA_SECURE_SMS", // OTA_SECURE_SMS, WEBRTC_DATA_CHANNEL, IP_BEARER
    val status: String = "VERIFIED_AUTHENTICATED", // PENDING, DISPATCHED, VERIFIED_AUTHENTICATED, REJECTED
    val plaintextJson: String
)
