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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.model.ApiLogEntry
import com.example.ui.UiState
import com.example.ui.components.JsonCodeView

@Composable
fun MoshiInspectorScreen(
    uiState: UiState,
    apiLogs: List<ApiLogEntry>,
    onMoshiInputChange: (String) -> Unit,
    onTestMoshi: () -> Unit,
    onTestIsoDateAdapter: () -> Unit,
    onBaseUrlInputChange: (String) -> Unit,
    onApplyBaseUrl: () -> Unit,
    onResetBaseUrl: () -> Unit,
    onClearLogs: () -> Unit,
    onClearCache: () -> Unit,
    onSetBearerToken: (String) -> Unit,
    onNewHeaderKeyChange: (String) -> Unit,
    onNewHeaderValueChange: (String) -> Unit,
    onAddHeader: () -> Unit,
    onRemoveHeader: (String) -> Unit,
    onSetChaosLatency: (Long) -> Unit,
    onSetChaosErrorCode: (Int) -> Unit,
    onSetChaosCorruptJson: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
        // Architecture Overview & Base URL
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Retrofit + Moshi Architecture",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                ArchitectureItem(label = "Retrofit Service", value = "RetrofitClient.apiService (MoshiConverterFactory)")
                ArchitectureItem(label = "Custom Moshi Adapters", value = "IsoDateAdapter, HexColorAdapter, SafeIntAdapter, KotlinJsonAdapterFactory")
                ArchitectureItem(label = "Active Base URL", value = uiState.baseUrl)

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.baseUrlInput,
                        onValueChange = onBaseUrlInputChange,
                        modifier = Modifier.weight(1f).testTag("base_url_input"),
                        label = { Text("Base URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onApplyBaseUrl, modifier = Modifier.testTag("apply_base_url_button")) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Apply URL", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onResetBaseUrl, modifier = Modifier.testTag("reset_base_url_button")) {
                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = "Reset URL", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (uiState.baseUrlSuccessMsg != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.baseUrlSuccessMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Feature 3: Dynamic Headers & Authentication
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dynamic Headers & Authentication",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Headers injected dynamically into all OkHttp & Retrofit requests by CustomHeaderInterceptor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.bearerToken,
                    onValueChange = onSetBearerToken,
                    modifier = Modifier.fillMaxWidth().testTag("bearer_token_input"),
                    label = { Text("Bearer Authorization Token") },
                    placeholder = { Text("e.g. eyJhbGciOi...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.newHeaderKey,
                        onValueChange = onNewHeaderKeyChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("Header Key") },
                        placeholder = { Text("X-Client-Id") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = uiState.newHeaderValue,
                        onValueChange = onNewHeaderValueChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("Value") },
                        placeholder = { Text("mobile-v2") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    IconButton(
                        onClick = onAddHeader,
                        modifier = Modifier.size(44.dp).testTag("add_header_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Header", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (uiState.customHeaders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Active Headers (${uiState.customHeaders.size}):", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    uiState.customHeaders.forEach { (k, v) ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$k: $v", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
                                IconButton(onClick = { onRemoveHeader(k) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feature 7: Chaos Engineering Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chaos Engineering & Resilience",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Test how the app handles network latency, HTTP failures, and malformed JSON payloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Simulated Network Latency:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0L to "None", 500L to "500ms", 1500L to "1.5s", 3000L to "3s").forEach { (ms, lbl) ->
                        FilterChip(
                            selected = uiState.chaosLatencyMs == ms,
                            onClick = { onSetChaosLatency(ms) },
                            label = { Text(lbl, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Simulated HTTP Status Error:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 401 to "401 Auth", 404 to "404 Not Found", 500 to "500 Internal").forEach { (code, lbl) ->
                        FilterChip(
                            selected = uiState.chaosErrorCode == code,
                            onClick = { onSetChaosErrorCode(code) },
                            label = { Text(lbl, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Simulate Malformed JSON", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Triggers Moshi JsonDataException", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.chaosCorruptJson,
                        onCheckedChange = onSetChaosCorruptJson,
                        modifier = Modifier.testTag("chaos_corrupt_switch")
                    )
                }
            }
        }

        // Feature 9: OkHttp Disk Cache Inspector
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
                        Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OkHttp Response Cache",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedButton(onClick = onClearCache, shape = RoundedCornerShape(8.dp)) {
                        Text("Clear Cache", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(label = "Cache Hits", value = "${uiState.cacheHitCount}", color = Color(0xFF059669), modifier = Modifier.weight(1f))
                    MetricBox(label = "Network Calls", value = "${uiState.cacheNetworkCount}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    MetricBox(label = "Total Requests", value = "${uiState.cacheRequestCount}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
        }

        // Feature 1: Moshi Custom Adapters Playground
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Moshi JSON Parser Playground",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Test custom adapters (IsoDateAdapter, HexColorAdapter, SafeIntAdapter) and codegen adapters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onTestIsoDateAdapter, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Test IsoDateAdapter", fontSize = 11.sp)
                    }
                    Button(onClick = onTestMoshi, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Parse JSON", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.moshiJsonInput,
                    onValueChange = onMoshiInputChange,
                    modifier = Modifier.fillMaxWidth().height(120.dp).testTag("moshi_test_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(10.dp)
                )

                if (uiState.moshiTestOutput != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    JsonCodeView(json = uiState.moshiTestOutput)
                }
            }
        }

        // Feature 10: Live Network Call Audit Logs & cURL Exporter
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
                    Column {
                        Text(
                            text = "Live API Network Logs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${apiLogs.size} calls logged with cURL generation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (apiLogs.isNotEmpty()) {
                        IconButton(onClick = onClearLogs, modifier = Modifier.testTag("clear_logs_button")) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear Logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (apiLogs.isEmpty()) {
                    Text(
                        text = "No network calls logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        apiLogs.take(10).forEach { log ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                color = (if (log.method == "GET") Color(0xFF2563EB) else Color(0xFF059669)).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = log.method,
                                                    color = if (log.method == "GET") Color(0xFF2563EB) else Color(0xFF059669),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = log.endpoint.take(30),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "${log.statusCode}",
                                                color = if (log.isSuccess) Color(0xFF059669) else Color(0xFFDC2626),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(text = "${log.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (log.curlCommand.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { copyToClipboard("cURL", log.curlCommand) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Terminal, contentDescription = "Copy cURL", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchitectureItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
