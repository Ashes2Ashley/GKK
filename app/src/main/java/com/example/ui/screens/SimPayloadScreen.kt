package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sim.AdminPayloadType
import com.example.data.sim.EncryptedPayloadEnvelope
import com.example.data.sim.SimHardwareState
import com.example.data.sim.SimPayloadManager
import com.example.data.sim.SimSlotId
import com.example.data.telecom.SmsGatewayManager
import com.example.ui.components.JsonCodeView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimPayloadScreen(
    hardwareState: SimHardwareState,
    envelopes: List<EncryptedPayloadEnvelope>,
    onSelectSlot: (SimSlotId) -> Unit,
    onCreatePayload: (AdminPayloadType, String, String, String) -> Unit,
    onDispatchEnvelope: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedType by remember { mutableStateOf(AdminPayloadType.APN_PROVISIONING) }
    var selectedChannel by remember { mutableStateOf("OTA_SECURE_SMS") }
    var payloadJsonInput by remember {
        mutableStateOf(
            """{
  "apn": "tactical.5g.private",
  "protocol": "IPv4v6",
  "authType": "CHAP",
  "roamingAllowed": true,
  "mtu": 1500
}"""
        )
    }

    var inspectedEnvelope by remember { mutableStateOf<EncryptedPayloadEnvelope?>(null) }
    var verificationResultMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SIM / eSIM Hardware Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sim_hardware_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Administrative SIM/eSIM Module",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "SECURE DOMAIN READY",
                            color = Color(0xFF10B981),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Slot Switcher Tabs
                TabRow(
                    selectedTabIndex = if (hardwareState.slotId == SimSlotId.SLOT_1_UICC) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = hardwareState.slotId == SimSlotId.SLOT_1_UICC,
                        onClick = { onSelectSlot(SimSlotId.SLOT_1_UICC) },
                        text = { Text("Slot 1: UICC Ground") },
                        modifier = Modifier.testTag("sim_tab_slot1")
                    )
                    Tab(
                        selected = hardwareState.slotId == SimSlotId.SLOT_2_EUICC,
                        onClick = { onSelectSlot(SimSlotId.SLOT_2_EUICC) },
                        text = { Text("Slot 2: eUICC Airborne") },
                        modifier = Modifier.testTag("sim_tab_slot2")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SIM Telemetry Information
                SimDetailRow(label = "Target ICCID", value = hardwareState.iccidMasked)
                SimDetailRow(label = "IMSI Identity", value = hardwareState.imsiMasked)
                SimDetailRow(label = "Carrier Profile", value = hardwareState.carrierName)
                SimDetailRow(label = "Hardware State", value = hardwareState.simState)
                SimDetailRow(label = "Security Domain", value = hardwareState.otaSecurityDomainState)
                SimDetailRow(label = "Active Profile ID", value = hardwareState.activeProfileId)
            }
        }

        // Administrative Payload Creator & Cryptographic Packager
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("payload_generator_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Secure Administrative Payload Studio",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Packaged with AES-256-GCM authenticated cipher and HMAC-SHA256 signature for non-repudiation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Select Administrative Payload Type:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))

                // Payload Type Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AdminPayloadType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedType = type
                                    payloadJsonInput = getDefaultJsonForType(type)
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = type.label,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Category: ${type.category}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Delivery Channel Selector
                Text("Select Secure Delivery Bearer:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedChannel == "OTA_SECURE_SMS",
                        onClick = { selectedChannel = "OTA_SECURE_SMS" },
                        label = { Text("OTA Secure SMS") },
                        modifier = Modifier.testTag("channel_sms")
                    )
                    FilterChip(
                        selected = selectedChannel == "WEBRTC_DATA_CHANNEL",
                        onClick = { selectedChannel = "WEBRTC_DATA_CHANNEL" },
                        label = { Text("WebRTC Mesh") },
                        modifier = Modifier.testTag("channel_webrtc")
                    )
                    FilterChip(
                        selected = selectedChannel == "IP_BEARER",
                        onClick = { selectedChannel = "IP_BEARER" },
                        label = { Text("IP Bearer") },
                        modifier = Modifier.testTag("channel_ip")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Administrative Configuration (JSON):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = payloadJsonInput,
                    onValueChange = { payloadJsonInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("payload_json_editor"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        onCreatePayload(selectedType, payloadJsonInput, hardwareState.iccidMasked, selectedChannel)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("encrypt_and_sign_btn")
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Encrypt & Sign Payload (AES-256-GCM)")
                }
            }
        }

        // Cryptographic Envelope Inspector Modal/Card
        if (inspectedEnvelope != null) {
            val env = inspectedEnvelope!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inspected_envelope_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, Color(0xFF06B6D4))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Envelope: ${env.envelopeId}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                        OutlinedButton(
                            onClick = { inspectedEnvelope = null; verificationResultMsg = null },
                            modifier = Modifier.testTag("close_inspector_btn")
                        ) {
                            Text("Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Ciphertext (AES-256-GCM Base64):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Text(text = env.ciphertextBase64, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp), color = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("96-bit Nonce (IV): ${env.nonceBase64} • 128-bit Auth Tag: ${env.authTagBase64}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                    Text("HMAC-SHA256 Signature: ${env.hmacSignature.take(24)}...", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF10B981)))

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val res = SimPayloadManager.verifyAndDecryptEnvelope(env)
                                verificationResultMsg = if (res.isSuccess) {
                                    "✓ Cryptographic Signature Authenticated. Decrypted Payload: \n${res.getOrNull()}"
                                } else {
                                    "✗ Verification Failed: ${res.exceptionOrNull()?.message}"
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("verify_envelope_btn")
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify & Decrypt")
                        }

                        Button(
                            onClick = {
                                if (env.deliveryChannel == "OTA_SECURE_SMS") {
                                    // Trigger dispatch via SMS Gateway Manager
                                    SmsGatewayManager.sendDirectSms(context, "+15550199", "SECURE_OTA_PAYLOAD:${env.envelopeId}:${env.ciphertextBase64.take(40)}")
                                }
                                onDispatchEnvelope(env.envelopeId)
                            },
                            modifier = Modifier.weight(1f).testTag("dispatch_envelope_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dispatch OTA")
                        }
                    }

                    if (verificationResultMsg != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF030712),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = verificationResultMsg!!,
                                color = if (verificationResultMsg!!.startsWith("✓")) Color(0xFF10B981) else Color(0xFFEF4444),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Administrative Envelopes List
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("payload_history_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Dispatched Administrative Envelopes (${envelopes.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                envelopes.forEach { env ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { inspectedEnvelope = env; verificationResultMsg = null },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = env.envelopeId,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${env.deliveryChannel}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = env.payloadType.label,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Target: ${env.targetIccid} • ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(env.timestamp))}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                color = if (env.status.contains("VERIFIED") || env.status.contains("ACK")) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (env.status.contains("ACK")) "ACKNOWLEDGED" else "VERIFIED",
                                    color = if (env.status.contains("ACK")) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
    }
}

private fun getDefaultJsonForType(type: AdminPayloadType): String = when (type) {
    AdminPayloadType.APN_PROVISIONING -> """{
  "apn": "tactical.5g.private",
  "protocol": "IPv4v6",
  "authType": "CHAP",
  "roamingAllowed": true,
  "mtu": 1500
}"""
    AdminPayloadType.SLICE_QOS_POLICY -> """{
  "sliceId": "URLLC-AIRBORNE-01",
  "sst": 1,
  "sd": "0x000001",
  "5qi": 1,
  "priorityLevel": 10,
  "latencyBoundMs": 5.0
}"""
    AdminPayloadType.CRYPTO_KEY_ROTATION -> """{
  "keyRotationId": "KEY-ROT-2026-Q4",
  "cipher": "AES-256-GCM",
  "curve": "X25519",
  "validityEpoch": 1790899200,
  "keyDigest": "SHA256:4a8b1c2d9e..."
}"""
    AdminPayloadType.AIRBORNE_TELEMETRY_DISPATCH -> """{
  "uavCallsign": "AERO-VALKYRIE-09",
  "command": "SET_ORBIT_WAYPOINT",
  "targetLat": 34.0522,
  "targetLon": -118.2437,
  "altitudeMeters": 1400,
  "sensorTrigger": "IR_ACTIVE"
}"""
    AdminPayloadType.REMOTE_FIRMWARE_CONFIG -> """{
  "modemProfile": "3GPP_REL17_SA",
  "mimoMode": "4x4_DL_2x2_UL",
  "carrierAggregation": ["n78", "n258"],
  "txPowerBackoffDbm": 0.0
}"""
}
