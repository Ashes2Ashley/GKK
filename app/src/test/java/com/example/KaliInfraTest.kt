package com.example

import com.example.data.config.RemoteConfigManager
import com.example.data.config.TacticalPreset
import com.example.data.fiveg.FiveGAdaptabilityEngine
import com.example.data.fiveg.FiveGSlice
import com.example.data.fiveg.NetworkTopology
import com.example.data.kali.KaliEnvironmentManager
import com.example.data.sim.AdminPayloadType
import com.example.data.sim.SimPayloadManager
import com.example.data.sim.SimSlotId
import com.example.data.webrtc.AirborneWebRtcManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KaliInfraTest {

    @Test
    fun `test 5g adaptability engine topology and slice transitions`() {
        FiveGAdaptabilityEngine.setTopology(NetworkTopology.AIRBORNE_LOS_LINK, "Unit Test Switch")
        assertEquals(NetworkTopology.AIRBORNE_LOS_LINK, FiveGAdaptabilityEngine.linkState.value.activeTopology)

        FiveGAdaptabilityEngine.setSlice(FiveGSlice.URLLC)
        assertEquals(FiveGSlice.URLLC, FiveGAdaptabilityEngine.linkState.value.activeSlice)
        assertEquals(1, FiveGAdaptabilityEngine.linkState.value.activeSlice.sst)

        val history = FiveGAdaptabilityEngine.adaptationHistory.value
        assertTrue("Adaptation history should record events", history.isNotEmpty())
    }

    @Test
    fun `test sim payload aes-256-gcm packaging and cryptographic verification`() {
        val testJson = """{"testApn":"private.5g","auth":"CHAP"}"""
        val envelope = SimPayloadManager.createAndSignPayload(
            type = AdminPayloadType.APN_PROVISIONING,
            payloadJson = testJson,
            targetIccid = "8901410321111855••••",
            deliveryChannel = "OTA_SECURE_SMS"
        )

        assertNotNull("Envelope should be created", envelope)
        assertTrue(envelope.ciphertextBase64.isNotEmpty())
        assertTrue(envelope.nonceBase64.isNotEmpty())
        assertTrue(envelope.authTagBase64.isNotEmpty())
        assertTrue(envelope.hmacSignature.isNotEmpty())

        // Verify and decrypt
        val decryptResult = SimPayloadManager.verifyAndDecryptEnvelope(envelope)
        assertTrue("Decryption should succeed for untampered envelope", decryptResult.isSuccess)
        assertEquals(testJson, decryptResult.getOrNull())

        // Test tampering detection
        val tamperedEnvelope = envelope.copy(ciphertextBase64 = "dGFtcGVyZWQ=")
        val tamperedResult = SimPayloadManager.verifyAndDecryptEnvelope(tamperedEnvelope)
        assertTrue("Tampered envelope should fail verification", tamperedResult.isFailure)
    }

    @Test
    fun `test webrtc airborne signaling and sdp generation`() {
        AirborneWebRtcManager.setBitrateCeiling(18000)
        assertEquals(18000, AirborneWebRtcManager.session.value.targetBitrateKbps)
        assertTrue(AirborneWebRtcManager.session.value.videoResolution.contains("4K Tactical"))

        val sdp = AirborneWebRtcManager.session.value.sdpOffer
        assertTrue("SDP should contain DTLS-SRTP audio/video bundles", sdp.contains("a=group:BUNDLE"))
        assertTrue("SDP should contain H265 codec", sdp.contains("H265"))
    }

    @Test
    fun `test kali operational environment commands`() {
        KaliEnvironmentManager.executeCommand("5g-slice-audit")
        val buffer = KaliEnvironmentManager.terminalBuffer.value
        val lastEntry = buffer.lastOrNull()
        assertNotNull(lastEntry)
        assertEquals("5g-slice-audit", lastEntry?.command)
        assertTrue(lastEntry?.output?.contains("3GPP Rel-17 Network Slices") == true)

        KaliEnvironmentManager.executeCommand("crypto-check")
        val cryptoEntry = KaliEnvironmentManager.terminalBuffer.value.lastOrNull()
        assertTrue(cryptoEntry?.output?.contains("AES-256-GCM") == true)
    }

    @Test
    fun `test modular remote config presets`() {
        RemoteConfigManager.applyPreset(TacticalPreset.AIRBORNE_PRIORITY)
        assertEquals(TacticalPreset.AIRBORNE_PRIORITY, RemoteConfigManager.config.value.activePreset)
        assertEquals(NetworkTopology.AIRBORNE_LOS_LINK, FiveGAdaptabilityEngine.linkState.value.activeTopology)

        RemoteConfigManager.applyPreset(TacticalPreset.AUTONOMOUS_TACTICAL)
        assertEquals(TacticalPreset.AUTONOMOUS_TACTICAL, RemoteConfigManager.config.value.activePreset)
        assertTrue(RemoteConfigManager.config.value.autoAdaptation)
    }
}
