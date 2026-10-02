package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.websocket.WebSocketStatus
import com.example.ui.UiState
import com.example.ui.components.JsonCodeView

@Composable
fun CloudflareEdgeScreen(
    uiState: UiState,
    webSocketStatus: WebSocketStatus,
    onUpdateCloudflareConfig: (String, String, Boolean) -> Unit,
    onTestTunnel: () -> Unit,
    onWebSocketUrlChange: (String) -> Unit,
    onWebSocketInputChange: (String) -> Unit,
    onConnectWebSocket: () -> Unit,
    onDisconnectWebSocket: () -> Unit,
    onSendWebSocketMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var tunnelInput by remember(uiState.cloudflareConfig.tunnelUrl) { mutableStateOf(uiState.cloudflareConfig.tunnelUrl) }
    var workerInput by remember(uiState.cloudflareConfig.workerUrl) { mutableStateOf(uiState.cloudflareConfig.workerUrl) }
    var anonymizeInput by remember(uiState.cloudflareConfig.anonymizeHeaders) { mutableStateOf(uiState.cloudflareConfig.anonymizeHeaders) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cloudflare Tunnel & Worker Card
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
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFFF38020), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cloudflare Tunnel & Worker Edge",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(color = Color(0xFFD1FAE5), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "Active",
                            color = Color(0xFF065F46),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Seamless tunneling integration with anonymous edge routing and Cloudflare Zero Trust authentication.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = tunnelInput,
                    onValueChange = {
                        tunnelInput = it
                        onUpdateCloudflareConfig(it, workerInput, anonymizeInput)
                    },
                    label = { Text("Cloudflare Tunnel URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cf_tunnel_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = workerInput,
                    onValueChange = {
                        workerInput = it
                        onUpdateCloudflareConfig(tunnelInput, it, anonymizeInput)
                    },
                    label = { Text("Cloudflare Worker Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cf_worker_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Anonymize Interactions", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Strips CF-Connecting-IP, X-Forwarded-For & Device IDs", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = anonymizeInput,
                        onCheckedChange = {
                            anonymizeInput = it
                            onUpdateCloudflareConfig(tunnelInput, workerInput, it)
                        },
                        modifier = Modifier.testTag("cf_anonymize_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onTestTunnel,
                        modifier = Modifier.weight(1f).height(44.dp).testTag("test_tunnel_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Tunnel Health")
                    }
                    OutlinedButton(
                        onClick = { copyToClipboard("Worker Script", uiState.generatedWorkerScript) },
                        modifier = Modifier.weight(1f).height(44.dp).testTag("copy_worker_script_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Worker Code")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.cloudflareTunnelStatus,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Guided Setup (Login via Cloudflare):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Text("1. Run `npx wrangler login` in your terminal to authenticate with Cloudflare.\n2. Paste the generated worker script into `src/index.js`.\n3. Execute `npx wrangler deploy` to launch your edge gateway.\n4. Route all app traffic seamlessly through your Cloudflare Tunnel.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Real-Time WebSocket Bi-Directional Stream
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
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("WebSocket Real-Time Stream", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    val statusColor = when (webSocketStatus) {
                        WebSocketStatus.CONNECTED -> Color(0xFF059669)
                        WebSocketStatus.CONNECTING -> Color(0xFFF59E0B)
                        WebSocketStatus.ERROR -> Color(0xFFDC2626)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = webSocketStatus.name,
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.webSocketUrl,
                        onValueChange = onWebSocketUrlChange,
                        label = { Text("WebSocket URL") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("ws_url_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (webSocketStatus == WebSocketStatus.CONNECTED) {
                        Button(
                            onClick = onDisconnectWebSocket,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Disconnect")
                        }
                    } else {
                        Button(
                            onClick = onConnectWebSocket,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(52.dp).testTag("ws_connect_button")
                        ) {
                            Text("Connect")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.webSocketInput,
                        onValueChange = onWebSocketInputChange,
                        label = { Text("Send payload") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("ws_message_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    IconButton(
                        onClick = onSendWebSocketMessage,
                        enabled = webSocketStatus == WebSocketStatus.CONNECTED && uiState.webSocketInput.isNotBlank(),
                        modifier = Modifier.size(48.dp).testTag("ws_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
