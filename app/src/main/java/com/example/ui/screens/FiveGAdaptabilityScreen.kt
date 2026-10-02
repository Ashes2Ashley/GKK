package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.fiveg.AdaptationEvent
import com.example.data.fiveg.FiveGSlice
import com.example.data.fiveg.LinkPerformanceState
import com.example.data.fiveg.NetworkTopology

@Composable
fun FiveGAdaptabilityScreen(
    linkState: LinkPerformanceState,
    adaptationHistory: List<AdaptationEvent>,
    onSelectTopology: (NetworkTopology) -> Unit,
    onSelectSlice: (FiveGSlice) -> Unit,
    onToggleAutoAdaptation: (Boolean) -> Unit,
    onFecChange: (Int) -> Unit,
    onSimulateRfDegradation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top RF Signal Telemetry HUD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rf_telemetry_hud_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (linkState.linkHealthScore > 75) Color(0xFF10B981)
                                    else if (linkState.linkHealthScore > 40) Color(0xFFF59E0B)
                                    else Color(0xFFEF4444)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "5G NR Dynamic Link Telemetry",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Health: ${linkState.linkHealthScore}%",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // RF KPI Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiStatBox(
                        title = "RSRP",
                        value = "${linkState.rfMetrics.rsrpDbm} dBm",
                        color = if (linkState.rfMetrics.rsrpDbm > -90) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatBox(
                        title = "SINR",
                        value = "${linkState.rfMetrics.sinrDb} dB",
                        color = Color(0xFF06B6D4),
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatBox(
                        title = "Latency",
                        value = "${linkState.latencyMs} ms",
                        color = if (linkState.latencyMs < 10.0) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatBox(
                        title = "Loss",
                        value = "${linkState.packetLossPercent}%",
                        color = if (linkState.packetLossPercent < 0.5) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Band: ${linkState.rfMetrics.frequencyBand} • ${linkState.rfMetrics.modulationScheme}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "↓ ${linkState.downlinkMbps} Mbps | ↑ ${linkState.uplinkMbps} Mbps",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Network Topologies Selection & Live Handoff Matrix
        Text(
            text = "Adaptive Network Topologies (Airborne & Ground)",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TopologyOptionCard(
                topology = NetworkTopology.GROUND_CELLULAR_5G,
                isActive = linkState.activeTopology == NetworkTopology.GROUND_CELLULAR_5G,
                icon = Icons.Default.CellTower,
                frequency = "Sub-6 GHz / mmWave Macro gNodeB",
                latencyEstimate = "~5-8 ms",
                onSelect = { onSelectTopology(NetworkTopology.GROUND_CELLULAR_5G) },
                testTag = "topology_ground_5g"
            )

            TopologyOptionCard(
                topology = NetworkTopology.AIRBORNE_LOS_LINK,
                isActive = linkState.activeTopology == NetworkTopology.AIRBORNE_LOS_LINK,
                icon = Icons.Default.Flight,
                frequency = "Directional C-Band 5.8 GHz UAV Mesh",
                latencyEstimate = "~10-12 ms",
                onSelect = { onSelectTopology(NetworkTopology.AIRBORNE_LOS_LINK) },
                testTag = "topology_airborne_los"
            )

            TopologyOptionCard(
                topology = NetworkTopology.SATELLITE_NTN,
                isActive = linkState.activeTopology == NetworkTopology.SATELLITE_NTN,
                icon = Icons.Default.Public,
                frequency = "3GPP Rel-17 LEO S-Band Satellite",
                latencyEstimate = "~35-42 ms",
                onSelect = { onSelectTopology(NetworkTopology.SATELLITE_NTN) },
                testTag = "topology_satellite_ntn"
            )

            TopologyOptionCard(
                topology = NetworkTopology.HYBRID_MULTI_PATH,
                isActive = linkState.activeTopology == NetworkTopology.HYBRID_MULTI_PATH,
                icon = Icons.Default.Hub,
                frequency = "Aggregated Ground + Airborne Multi-Path",
                latencyEstimate = "~6-9 ms (Max Redundancy)",
                onSelect = { onSelectTopology(NetworkTopology.HYBRID_MULTI_PATH) },
                testTag = "topology_hybrid_multipath"
            )
        }

        // Automated Adaptability Controller & RF Fade Simulator
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("adaptability_controller_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Autonomous Link Adaptability",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "Auto-switches to airborne mesh upon signal fade (< -110 dBm or > 2% loss)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = linkState.autoAdaptationEnabled,
                        onCheckedChange = onToggleAutoAdaptation,
                        modifier = Modifier.testTag("auto_adaptation_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Forward Error Correction (FEC) Redundancy: ${linkState.fecRedundancyPercent}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Slider(
                    value = linkState.fecRedundancyPercent.toFloat(),
                    onValueChange = { onFecChange(it.toInt()) },
                    valueRange = 0f..50f,
                    steps = 5,
                    modifier = Modifier.testTag("fec_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // RF Degradation Trigger Button (Interactive testing)
                Button(
                    onClick = onSimulateRfDegradation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444).copy(alpha = 0.9f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulate_rf_fade_button")
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inject RF Fade & Test Automated Failover")
                }
            }
        }

        // 5G Network Slicing & QoS Policies
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("network_slicing_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "5G Network Slicing & QoS Engine",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dynamically provision dedicated slices for airborne video, telemetry, or URLLC control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                FiveGSlice.entries.forEach { slice ->
                    val isSelected = linkState.activeSlice == slice
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectSlice(slice) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = slice.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "SST: ${slice.sst} • SD: ${slice.sd} • 5QI: ${slice.default5qi} | ${slice.slaDesc}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Adaptation & Handover Event Log
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("adaptation_events_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Real-Time Adaptation & Handover History",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                adaptationHistory.take(5).forEach { event ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "[${event.type}]",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${event.resultingLatencyMs}ms",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF10B981)
                                )
                            }
                            Text(
                                text = event.reason,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopologyOptionCard(
    topology: NetworkTopology,
    isActive: Boolean,
    icon: ImageVector,
    frequency: String,
    latencyEstimate: String,
    onSelect: () -> Unit,
    testTag: String
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "border_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) borderColor else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = topology.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = frequency,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (isActive) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = Color(0xFF10B981),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = latencyEstimate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun KpiStatBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = color
            )
        }
    }
}
