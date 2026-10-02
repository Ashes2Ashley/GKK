package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.startup.StartupHealthMetrics
import com.example.ui.UiState
import com.example.ui.components.JsonCodeView

@Composable
fun AdminScreen(
    uiState: UiState,
    startupHealth: StartupHealthMetrics,
    onToggleStrictTls: (Boolean) -> Unit,
    onToggleCertPinning: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val rootCfg = uiState.rootConfig

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Startup Stabilization Health Metrics
        Card(
            modifier = Modifier.fillMaxWidth().testTag("startup_health_card"),
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
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Startup Stabilization & Health", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Surface(color = Color(0xFFD1FAE5), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "Stabilized",
                            color = Color(0xFF065F46),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "System stabilized on app launch with pre-warmed connection pool and SQLite initialization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(label = "Startup Launch", value = "${startupHealth.startupDurationMs}ms", color = Color(0xFF059669), modifier = Modifier.weight(1f))
                    MetricBox(label = "Network Type", value = startupHealth.activeNetworkType.take(8), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    MetricBox(label = "Bandwidth", value = if (startupHealth.estimatedDownstreamKbps > 0) "${startupHealth.estimatedDownstreamKbps / 1000} Mbps" else "Optimal", color = Color(0xFF2563EB), modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (startupHealth.isDatabasePrewarmed) "✓ Room DB Pre-warmed" else "• DB Initializing...",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp),
                            color = Color(0xFF059669)
                        )
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (startupHealth.isNetworkPrewarmed) "✓ DNS & Pool Pre-warmed" else "• DNS resolving...",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp),
                            color = Color(0xFF059669)
                        )
                    }
                }
            }
        }

        // Root Configuration & Administration
        Card(
            modifier = Modifier.fillMaxWidth().testTag("root_config_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Root Project Configurations", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Preconfigured root build configurations, Gradle settings, and runtime parameters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                ArchitectureItem(label = "Application ID", value = rootCfg.applicationId)
                ArchitectureItem(label = "Target & Compile SDK", value = "Android 15 / API ${rootCfg.compileSdk} (Min SDK ${rootCfg.minSdk})")
                ArchitectureItem(label = "Build Toolchain", value = "Kotlin ${rootCfg.kotlinVersion} • AGP ${rootCfg.agpVersion} • ${rootCfg.javaVersion}")
                ArchitectureItem(label = "Persistence & Serialization", value = "Room ${rootCfg.roomVersion} • Moshi ${rootCfg.moshiVersion}")
                ArchitectureItem(label = "Network Pipeline", value = "Retrofit ${rootCfg.retrofitVersion} • ${rootCfg.tlsEnforcement}")

                Spacer(modifier = Modifier.height(12.dp))

                Text("ProGuard / R8 Protection Rules:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(4.dp))
                JsonCodeView(json = rootCfg.proguardRulesSummary)
            }
        }

        // Security Policies
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Security Enforcement Policies", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Strict TLS 1.3 Protocol", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Rejects legacy cipher suites and enforces perfect forward secrecy", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.isStrictTls,
                        onCheckedChange = onToggleStrictTls,
                        modifier = Modifier.testTag("strict_tls_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Certificate Pinning (SPKI SHA-256)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Pins server public key hashes against MITM interception", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.isCertPinning,
                        onCheckedChange = onToggleCertPinning,
                        modifier = Modifier.testTag("cert_pinning_switch")
                    )
                }
            }
        }
    }
}
