package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.model.BenchmarkMetrics

@Composable
fun BenchmarkScreen(
    metrics: BenchmarkMetrics,
    onRunBenchmark: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedBurst by remember { mutableStateOf(5) }
    val burstOptions = listOf(5, 10, 20)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Network Benchmark Suite",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Stress test Retrofit + Moshi pipeline throughput, parse time, and HTTP latency.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Select Request Burst Size:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    burstOptions.forEach { count ->
                        FilterChip(
                            selected = selectedBurst == count,
                            onClick = { selectedBurst = count },
                            label = { Text("$count Requests") },
                            modifier = Modifier.testTag("benchmark_burst_$count")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onRunBenchmark(selectedBurst) },
                    enabled = !metrics.isRunning,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_benchmark_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (metrics.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running (${metrics.completedRequests}/${metrics.totalRequests})...")
                    } else {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run $selectedBurst Requests Benchmark")
                    }
                }

                if (metrics.isRunning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val progress = if (metrics.totalRequests > 0) metrics.completedRequests.toFloat() / metrics.totalRequests else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Metrics Results Grid
        if (metrics.completedRequests > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("benchmark_results_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Benchmark Statistics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricBox(label = "Avg Latency", value = "${String.format("%.1f", metrics.avgLatencyMs)} ms", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        MetricBox(label = "Min Latency", value = "${metrics.minLatencyMs} ms", color = Color(0xFF059669), modifier = Modifier.weight(1f))
                        MetricBox(label = "Max Latency", value = "${metrics.maxLatencyMs} ms", color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val successRate = if (metrics.completedRequests > 0) (metrics.successfulRequests * 100) / metrics.completedRequests else 0
                        MetricBox(label = "Success Rate", value = "$successRate%", color = Color(0xFF059669), modifier = Modifier.weight(1f))
                        MetricBox(label = "Successful", value = "${metrics.successfulRequests}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        MetricBox(label = "Failed", value = "${metrics.failedRequests}", color = if (metrics.failedRequests > 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Latency Breakdown per Request:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val max = metrics.maxLatencyMs.coerceAtLeast(1L)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.latencies.forEachIndexed { index, lat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    modifier = Modifier.width(28.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val fraction = (lat.toFloat() / max).coerceIn(0.05f, 1f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (lat < 300) Color(0xFF10B981) else if (lat < 800) Color(0xFFF59E0B) else Color(0xFFEF4444)
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${lat}ms",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.width(55.dp),
                                    color = MaterialTheme.colorScheme.onSurface
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
fun MetricBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}
