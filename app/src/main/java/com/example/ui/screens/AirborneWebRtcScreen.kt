package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.webrtc.AirborneTelemetry
import com.example.data.webrtc.WebRtcInfrastructureSession
import com.example.data.webrtc.WebRtcLinkState
import com.example.ui.components.JsonCodeView

@Composable
fun AirborneWebRtcScreen(
    telemetry: AirborneTelemetry,
    session: WebRtcInfrastructureSession,
    onInitiateSignaling: () -> Unit,
    onDisconnect: () -> Unit,
    onSendDataChannelMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var customCommand by remember { mutableStateOf("") }
    var showSdpInspection by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Airborne Node Telemetry HUD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("airborne_node_hud_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
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
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AirplanemodeActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = telemetry.callsign,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Ground Relay: ${telemetry.groundRelayStation}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${telemetry.batteryPercent}%",
                                color = Color(0xFF10B981),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tactical Avionics HUD Grid
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvionicsBox(label = "Altitude", value = "${telemetry.altitudeMeters}m", sub = "${(telemetry.altitudeMeters * 3.28).toInt()} ft AGL", modifier = Modifier.weight(1f))
                    AvionicsBox(label = "Speed", value = "${telemetry.groundSpeedKmh}", sub = "km/h Ground", modifier = Modifier.weight(1f))
                    AvionicsBox(label = "Heading", value = "${telemetry.yawHeadingDeg}°", sub = "Yaw Azimuth", modifier = Modifier.weight(1f))
                    AvionicsBox(label = "Euler Attitude", value = "P:${telemetry.pitchDeg}°", sub = "R:${telemetry.rollDeg}°", modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "GPS Nav: ${telemetry.latitude}, ${telemetry.longitude} • RF Link Quality: ${telemetry.rfLinkQualityPercent}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tactical WebRTC Video & Sensor Stream Canvas Simulation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tactical_stream_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B111E)),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE AIRBORNE WEBRTC STREAM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                    Text(
                        text = session.videoResolution,
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tactical Reticle Visual HUD
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF030712))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val primaryColor = Color(0xFF00E5FF)
                        val gridColor = Color(0xFF00E5FF).copy(alpha = 0.2f)

                        // Artificial horizon lines
                        drawLine(
                            color = primaryColor.copy(alpha = 0.7f),
                            start = Offset(cx - 80f, cy),
                            end = Offset(cx - 20f, cy),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = primaryColor.copy(alpha = 0.7f),
                            start = Offset(cx + 20f, cy),
                            end = Offset(cx + 80f, cy),
                            strokeWidth = 2f
                        )

                        // Center reticle
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.8f),
                            radius = 16f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.5f)
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = 3f,
                            center = Offset(cx, cy)
                        )

                        // Range rings
                        drawCircle(
                            color = gridColor,
                            radius = 65f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
                        )
                    }

                    // Tactical HUD telemetry overlay text
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "AIR-SPEED: ${telemetry.groundSpeedKmh} KM/H",
                            color = Color(0xFF00E5FF),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        )
                        Text(
                            text = "ALTITUDE:  ${telemetry.altitudeMeters} M AGL",
                            color = Color(0xFF00E5FF),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "RTT: ${session.measuredRttMs} MS",
                            color = Color(0xFF10B981),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        )
                        Text(
                            text = "BITRATE: ${session.targetBitrateKbps} KBPS",
                            color = Color(0xFF00E5FF),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "DTLS 1.3 / SRTP-AEAD-AES-256-GCM SECURE TUNNEL",
                            color = Color(0xFF38BDF8),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tx: ${session.transmittedBytes / 1024 / 1024} MB • Rx: ${session.receivedBytes / 1024 / 1024} MB",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFF94A3B8)
                    )

                    Row {
                        IconButton(onClick = onInitiateSignaling, modifier = Modifier.size(32.dp).testTag("webrtc_renegotiate_btn")) {
                            Icon(Icons.Default.Refresh, contentDescription = "Renegotiate", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // WebRTC Infrastructure Controls & Signaling Inspection
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Session Infrastructure & DTLS", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    OutlinedButton(
                        onClick = { showSdpInspection = !showSdpInspection },
                        modifier = Modifier.testTag("toggle_sdp_btn")
                    ) {
                        Text(if (showSdpInspection) "Hide SDP" else "Inspect SDP")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Peer Session ID: ${session.sessionId} • State: ${session.state}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ICE Candidate: ${session.iceCandidatePair}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)

                AnimatedVisibility(visible = showSdpInspection) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text("Local SDP Offer (RFC 4566 / 8866):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(4.dp))
                        JsonCodeView(json = session.sdpOffer)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onInitiateSignaling,
                        modifier = Modifier.weight(1f).testTag("webrtc_connect_btn")
                    ) {
                        Text("Establish WebRTC Mesh")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f).testTag("webrtc_disconnect_btn")
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }

        // Tactical Data Channel Command Dispatcher
        Card(
            modifier = Modifier.fillMaxWidth().testTag("data_channel_dispatcher_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Podcasts, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("P2P Data Channel Command Dispatch", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Transmit high-priority command and waypoint packets to airborne node with zero delay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Command Chips
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("quick_cmd_rtb")
                    ) {
                        Box(modifier = Modifier.clickable { onSendDataChannelMessage("CMD:RTB_IMMEDIATE") }.padding(8.dp), contentAlignment = Alignment.Center) {
                            Text("RTB Return", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.clickable { onSendDataChannelMessage("CMD:HOLD_ORBIT_400M") }.padding(8.dp), contentAlignment = Alignment.Center) {
                            Text("Hold Orbit", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.clickable { onSendDataChannelMessage("CMD:SWITCH_IR_CAMERA") }.padding(8.dp), contentAlignment = Alignment.Center) {
                            Text("Toggle IR", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customCommand,
                        onValueChange = { customCommand = it },
                        placeholder = { Text("Enter tactical command packet...") },
                        modifier = Modifier.weight(1f).testTag("custom_data_channel_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (customCommand.isNotBlank()) {
                                onSendDataChannelMessage(customCommand)
                                customCommand = ""
                            }
                        },
                        modifier = Modifier.testTag("send_data_channel_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Telemetry History Log
                Text("Data Channel Transmission Stream:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF030712), RoundedCornerShape(8.dp)).padding(10.dp)) {
                    session.telemetryHistory.take(6).forEach { log ->
                        Text(
                            text = log,
                            color = if (log.contains("TX DATA_CH")) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AvionicsBox(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
