package com.example.data.sim

import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SimPayloadManager {

    // Master administrative derivation key (256 bits) for demonstration & validation
    private val ADMIN_AES_KEY_BYTES = byteArrayOf(
        0x5F.toByte(), 0x1A.toByte(), 0x8C.toByte(), 0x33.toByte(),
        0x90.toByte(), 0xBB.toByte(), 0x11.toByte(), 0x4D.toByte(),
        0x7E.toByte(), 0x02.toByte(), 0xA4.toByte(), 0x67.toByte(),
        0x38.toByte(), 0xC1.toByte(), 0xDF.toByte(), 0x09.toByte(),
        0x2B.toByte(), 0x88.toByte(), 0x15.toByte(), 0xF3.toByte(),
        0x44.toByte(), 0x76.toByte(), 0xAA.toByte(), 0xCD.toByte(),
        0x10.toByte(), 0x22.toByte(), 0x99.toByte(), 0x51.toByte(),
        0xEE.toByte(), 0xFA.toByte(), 0x70.toByte(), 0x64.toByte()
    )

    private val HMAC_SECRET_BYTES = "Kali-Infra-Administrative-Hmac-Auth-Secret".toByteArray(StandardCharsets.UTF_8)

    private val _hardwareState = MutableStateFlow(SimHardwareState())
    val hardwareState: StateFlow<SimHardwareState> = _hardwareState.asStateFlow()

    private val _envelopes = MutableStateFlow<List<EncryptedPayloadEnvelope>>(emptyList())
    val envelopes: StateFlow<List<EncryptedPayloadEnvelope>> = _envelopes.asStateFlow()

    init {
        // Preload sample pre-verified administrative payloads
        createAndSignPayload(
            type = AdminPayloadType.APN_PROVISIONING,
            payloadJson = """
                {
                  "apn": "tactical.5g.private",
                  "protocol": "IPv4v6",
                  "authType": "CHAP",
                  "roamingAllowed": true,
                  "encryption": "IPsec-ESP-GCM"
                }
            """.trimIndent(),
            targetIccid = "8901410321111855••••",
            deliveryChannel = "OTA_SECURE_SMS"
        )

        createAndSignPayload(
            type = AdminPayloadType.SLICE_QOS_POLICY,
            payloadJson = """
                {
                  "sliceId": "URLLC-TACTICAL-ALPHA",
                  "sst": 1,
                  "sd": "0x000001",
                  "5qi": 1,
                  "maxBitrateDownlink": "150Mbps",
                  "maxBitrateUplink": "80Mbps",
                  "latencyCeilingMs": 5.0
                }
            """.trimIndent(),
            targetIccid = "8901260799120045••••",
            deliveryChannel = "WEBRTC_DATA_CHANNEL"
        )
    }

    fun selectSimSlot(slot: SimSlotId) {
        val newState = if (slot == SimSlotId.SLOT_1_UICC) {
            SimHardwareState(
                slotId = SimSlotId.SLOT_1_UICC,
                iccidMasked = "8901410321111855••••",
                imsiMasked = "31041012345••••",
                carrierName = "Tactical Defense Private 5G",
                simState = "READY (PIN_VERIFIED)",
                otaSecurityDomainState = "OP_SEC_INITIALIZED (SCP03)",
                activeProfileId = "PROFILE-GOC-5G-URLLC-SECURE"
            )
        } else {
            SimHardwareState(
                slotId = SimSlotId.SLOT_2_EUICC,
                iccidMasked = "8901260799120045••••",
                imsiMasked = "31041098765••••",
                carrierName = "Airborne Tactical Relay eSIM",
                simState = "ACTIVE_DOWNLOADED",
                otaSecurityDomainState = "eUICC-GSMA-RSP-V3",
                activeProfileId = "PROFILE-AIRBORNE-MESH-RELAY"
            )
        }
        _hardwareState.value = newState
    }

    /**
     * Real AES-256-GCM encryption with 96-bit random IV and 128-bit authentication tag,
     * plus HMAC-SHA256 signature for complete cryptographic authentication.
     */
    fun createAndSignPayload(
        type: AdminPayloadType,
        payloadJson: String,
        targetIccid: String = _hardwareState.value.iccidMasked,
        deliveryChannel: String = "OTA_SECURE_SMS"
    ): EncryptedPayloadEnvelope {
        val random = SecureRandom()
        val iv = ByteArray(12) // 96-bit IV recommended for AES-GCM
        random.nextBytes(iv)

        val secretKey = SecretKeySpec(ADMIN_AES_KEY_BYTES, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv) // 128-bit authentication tag
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val plaintextBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
        val encryptedWithTag = cipher.doFinal(plaintextBytes)

        // In Java AES/GCM, cipher.doFinal appends the 16-byte (128-bit) tag to the ciphertext
        val tagLength = 16
        val ciphertextLength = encryptedWithTag.size - tagLength
        val ciphertextOnly = ByteArray(ciphertextLength)
        val tagOnly = ByteArray(tagLength)
        System.arraycopy(encryptedWithTag, 0, ciphertextOnly, 0, ciphertextLength)
        System.arraycopy(encryptedWithTag, ciphertextLength, tagOnly, 0, tagLength)

        val nonceBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ciphertextBase64 = Base64.encodeToString(ciphertextOnly, Base64.NO_WRAP)
        val tagBase64 = Base64.encodeToString(tagOnly, Base64.NO_WRAP)

        // Compute HMAC-SHA256 signature over nonce + ciphertext + tag
        val hmacData = "$nonceBase64.$ciphertextBase64.$tagBase64".toByteArray(StandardCharsets.UTF_8)
        val hmacMac = Mac.getInstance("HmacSHA256")
        hmacMac.init(SecretKeySpec(HMAC_SECRET_BYTES, "HmacSHA256"))
        val signatureBytes = hmacMac.doFinal(hmacData)
        val hmacSignature = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

        val envelope = EncryptedPayloadEnvelope(
            envelopeId = "ENV-" + UUID.randomUUID().toString().take(8).uppercase(),
            payloadType = type,
            timestamp = System.currentTimeMillis(),
            nonceBase64 = nonceBase64,
            authTagBase64 = tagBase64,
            ciphertextBase64 = ciphertextBase64,
            hmacSignature = hmacSignature,
            senderIdentity = "ADMIN-GOC-CONSOLE",
            targetIccid = targetIccid,
            deliveryChannel = deliveryChannel,
            status = "VERIFIED_AUTHENTICATED",
            plaintextJson = payloadJson
        )

        _envelopes.value = listOf(envelope) + _envelopes.value
        return envelope
    }

    /**
     * Verifies cryptographic signature and decrypts AES-256-GCM payload envelope.
     */
    fun verifyAndDecryptEnvelope(envelope: EncryptedPayloadEnvelope): Result<String> {
        return try {
            // 1. Verify HMAC-SHA256
            val hmacData = "${envelope.nonceBase64}.${envelope.ciphertextBase64}.${envelope.authTagBase64}".toByteArray(StandardCharsets.UTF_8)
            val hmacMac = Mac.getInstance("HmacSHA256")
            hmacMac.init(SecretKeySpec(HMAC_SECRET_BYTES, "HmacSHA256"))
            val expectedSig = Base64.encodeToString(hmacMac.doFinal(hmacData), Base64.NO_WRAP)

            if (expectedSig != envelope.hmacSignature) {
                return Result.failure(SecurityException("Cryptographic HMAC signature mismatch! Tampered payload."))
            }

            // 2. Decrypt AES-256-GCM
            val iv = Base64.decode(envelope.nonceBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(envelope.ciphertextBase64, Base64.NO_WRAP)
            val tag = Base64.decode(envelope.authTagBase64, Base64.NO_WRAP)

            val cipherWithTag = ByteArray(ciphertext.size + tag.size)
            System.arraycopy(ciphertext, 0, cipherWithTag, 0, ciphertext.size)
            System.arraycopy(tag, 0, cipherWithTag, ciphertext.size, tag.size)

            val secretKey = SecretKeySpec(ADMIN_AES_KEY_BYTES, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(cipherWithTag)
            val json = String(decryptedBytes, StandardCharsets.UTF_8)
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun dispatchEnvelope(envelopeId: String) {
        val updated = _envelopes.value.map {
            if (it.envelopeId == envelopeId) {
                it.copy(status = "DISPATCHED_ACKNOWLEDGED")
            } else {
                it
            }
        }
        _envelopes.value = updated
    }
}
