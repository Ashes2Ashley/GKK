package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.config.ModularRemoteConfig
import com.example.data.config.SystemLogItem
import com.example.data.config.TacticalPreset

@Composable
fun SecurityConfigScreen(
    config: ModularRemoteConfig,
    logs: List<SystemLogItem>,
    onApplyPreset: (TacticalPreset) -> Unit,
    onBitrateCeilingChange: (Int) -> Unit,
    onToggleCertPinning: (Boolean) -> Unit,
    onToggleAutoAdaptation: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var logFilterCategory by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = logs.filter { item ->
        (logFilterCategory == "ALL" || item.category == logFilterCategory) &&
        (searchQuery.isBlank() || item.message.contains(searchQuery, ignoreCase = true) || item.category.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Modular Remote Configuration Presets
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_config_presets_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Modular Remote Configuration", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dynamic remote reconfiguration of 5G slicing, WebRTC bitrates, and security levels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                TacticalPreset.entries.forEach { preset ->
                    val isSelected = config.activePreset == preset
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onApplyPreset(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preset.desc,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // Robust Encryption & Security Protocols Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("security_protocols_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Robust Encryption & Zero-Trust Policies", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Strict SPKI Certificate Pinning", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Pins SHA-256 public key certificates against MITM interception", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.strictCertPinning,
                        onCheckedChange = onToggleCertPinning,
                        modifier = Modifier.testTag("cert_pinning_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Autonomous 5G Link Adaptation", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Dynamic handoff between Ground 5G and Airborne Mesh based on RF metrics", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.autoAdaptation,
                        onCheckedChange = onToggleAutoAdaptation,
                        modifier = Modifier.testTag("auto_adapt_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "WebRTC Max Bitrate Ceiling: ${config.webrtcBitrateCeilingKbps} Kbps (${config.webrtcBitrateCeilingKbps / 1000} Mbps)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
                Slider(
                    value = config.webrtcBitrateCeilingKbps.toFloat(),
                    onValueChange = { onBitrateCeilingChange(it.toInt()) },
                    valueRange = 1000f..40000f,
                    steps = 10,
                    modifier = Modifier.testTag("bitrate_ceiling_slider")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Security Status Badges
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecurityStatusBadge("DTLS 1.3", "SRTP AEAD", Color(0xFF10B981), modifier = Modifier.weight(1f))
                    SecurityStatusBadge("AES-256-GCM", "SIM Payloads", Color(0xFF10B981), modifier = Modifier.weight(1f))
                    SecurityStatusBadge("Zero-Trust", "5G URLLC", Color(0xFF06B6D4), modifier = Modifier.weight(1f))
                }
            }
        }

        // Comprehensive Real-Time Monitoring & Telemetry Event Log
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("system_logs_card"),
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
                    Text("Real-Time Telemetry & Audit Logs", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    IconButton(onClick = onClearLogs, modifier = Modifier.size(28.dp).testTag("clear_logs_btn")) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Logs", modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "5G-NR", "WEBRTC", "SIM-OTA", "SECURITY").forEach { cat ->
                        FilterChip(
                            selected = logFilterCategory == cat,
                            onClick = { logFilterCategory = cat },
                            label = { Text(cat, fontSize = 10.sp) },
                            modifier = Modifier.testTag("filter_$cat")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search logs...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("log_search_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF030712), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Text(
                            text = "No log records found matching criteria.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                        )
                    } else {
                        filteredLogs.take(15).forEach { item ->
                            val color = when (item.level) {
                                "SUCCESS" -> Color(0xFF10B981)
                                "SECURE" -> Color(0xFF00E5FF)
                                "WARN" -> Color(0xFFF59E0B)
                                else -> Color(0xFF94A3B8)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "[${item.timestamp}] [${item.category}] ",
                                    color = Color(0xFF64748B),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                )
                                Text(
                                    text = item.message,
                                    color = color,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
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
fun SecurityStatusBadge(title: String, subtitle: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = color))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}
