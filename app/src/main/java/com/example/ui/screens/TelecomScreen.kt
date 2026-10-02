package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.telecom.SipCallState
import com.example.data.telecom.WebRtcState
import com.example.ui.UiState
import com.example.ui.components.JsonCodeView

@Composable
fun TelecomScreen(
    uiState: UiState,
    onSmsRecipientChange: (String) -> Unit,
    onSmsBodyChange: (String) -> Unit,
    onSendSms: (Context) -> Unit,
    onStartWebRtc: () -> Unit,
    onCloseWebRtc: () -> Unit,
    onSipDialChange: (String) -> Unit,
    onRegisterSip: () -> Unit,
    onInitiateSipCall: () -> Unit,
    onEndSipCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) } // 0: SMS, 1: WebRTC, 2: SIP

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("SMS Gateway") },
                icon = { Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("WebRTC P2P") },
                icon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            Tab(
                selected = subTab == 2,
                onClick = { subTab = 2 },
                text = { Text("SIP VoIP") },
                icon = { Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (subTab) {
                0 -> SmsSection(
                    uiState = uiState,
                    onRecipientChange = onSmsRecipientChange,
                    onBodyChange = onSmsBodyChange,
                    onSend = { onSendSms(context) }
                )
                1 -> WebRtcSection(
                    uiState = uiState,
                    onStart = onStartWebRtc,
                    onClose = onCloseWebRtc
                )
                2 -> SipSection(
                    uiState = uiState,
                    onDialChange = onSipDialChange,
                    onRegister = onRegisterSip,
                    onCall = onInitiateSipCall,
                    onEndCall = onEndSipCall
                )
            }
        }
    }
}

@Composable
fun SmsSection(
    uiState: UiState,
    onRecipientChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CellTower, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Built-in SMS Telephony Gateway", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Text(
                text = "Dispatches SMS through Android SmsManager with automatic intent fallback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = uiState.smsRecipient,
                onValueChange = onRecipientChange,
                label = { Text("Phone Number") },
                placeholder = { Text("+1 (555) 019-9000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("sms_recipient_input"),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = uiState.smsBodyText,
                onValueChange = onBodyChange,
                label = { Text("Message Body") },
                modifier = Modifier.fillMaxWidth().height(100.dp).testTag("sms_body_input"),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val len = uiState.smsBodyText.length
                val segs = if (len <= 160) 1 else (len / 153) + 1
                Text("Characters: $len / 160 (Segments: $segs)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSend,
                enabled = uiState.smsRecipient.isNotBlank() && uiState.smsBodyText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("send_sms_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send SMS via Telephony Gateway")
            }

            if (uiState.smsStatusMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.smsStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    if (uiState.smsHistory.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("SMS Dispatch Log", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                uiState.smsHistory.take(5).forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.recipient, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                Text(item.status, style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669))
                            }
                            Text(item.messageText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebRtcSection(
    uiState: UiState,
    onStart: () -> Unit,
    onClose: () -> Unit
) {
    val session = uiState.webRtcSession

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
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
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("WebRTC P2P Signaling Engine", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                val stateColor = when (session.state) {
                    WebRtcState.CONNECTED -> Color(0xFF059669)
                    WebRtcState.FAILED -> Color(0xFFDC2626)
                    else -> MaterialTheme.colorScheme.primary
                }
                Surface(color = stateColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = session.state.name,
                        color = stateColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Session ID: ${session.sessionId} • Peer: ${session.peerId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(10.dp))

            Text("STUN / TURN ICE Servers:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            session.iceServers.forEach { ice ->
                Text("• ${ice.urls}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.primary)
            }

            if (session.state == WebRtcState.CONNECTED) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = Color(0xFFD1FAE5), shape = RoundedCornerShape(6.dp)) {
                        Text("RTT: ${session.roundTripTimeMs}ms", color = Color(0xFF065F46), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                        Text("ICE Candidates: ${session.iceCandidatesCount}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f).height(44.dp).testTag("start_webrtc_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Initialize WebRTC Offer")
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close Session")
                }
            }

            if (session.localSdpOffer.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("SDP Offer (Session Description Protocol):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                JsonCodeView(json = session.localSdpOffer)
            }
        }
    }
}

@Composable
fun SipSection(
    uiState: UiState,
    onDialChange: (String) -> Unit,
    onRegister: () -> Unit,
    onCall: () -> Unit,
    onEndCall: () -> Unit
) {
    val session = uiState.sipSession

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
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
                    Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SIP Compatibility Engine", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                val statusColor = when (session.state) {
                    SipCallState.REGISTERED -> Color(0xFF059669)
                    SipCallState.IN_CALL -> Color(0xFF2563EB)
                    SipCallState.ERROR -> Color(0xFFDC2626)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = session.state.name,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("URI: ${session.profile.getSipUri()} • Transport: ${session.profile.transport} (${session.profile.port})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth().height(42.dp).testTag("register_sip_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("REGISTER with SIP Registrar")
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.sipDialNumber,
                    onValueChange = onDialChange,
                    label = { Text("Dial SIP Number / Extension") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("sip_dial_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                if (session.state == SipCallState.IN_CALL || session.state == SipCallState.CALLING) {
                    Button(
                        onClick = onEndCall,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(52.dp).testTag("end_sip_call_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End Call")
                    }
                } else {
                    Button(
                        onClick = onCall,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier.height(52.dp).testTag("start_sip_call_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call")
                    }
                }
            }

            if (session.callLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("SIP Packet Transactions:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                session.callLogs.take(3).forEach { log ->
                    Text("• $log", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
