package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.realtime.MonitorRegistry
import com.example.data.realtime.MonitorStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live monitors: all 15 real-time monitors rendered generically.
 * Every value on this screen comes from a live API — or the card says
 * exactly why it's unavailable. Nothing here is mock data.
 */
@Composable
fun LiveScreen() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { MonitorRegistry.startAll(ctx) }
    DisposableEffect(Unit) { onDispose { MonitorRegistry.stopAll() } }

    val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(MonitorRegistry.all, key = { it.name }) { monitor ->
            val r by monitor.reading.collectAsState()
            val dot = when (r.status) {
                MonitorStatus.OK -> Color(0xFF059669)
                MonitorStatus.UNAVAILABLE -> Color(0xFFD97706)
                MonitorStatus.ERROR -> Color(0xFFDC2626)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            r.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Surface(
                            color = dot.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                r.status.name,
                                color = dot,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(r.summary, style = MaterialTheme.typography.bodyMedium)
                    if (r.detail.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            r.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (r.updatedAt > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "updated ${timeFmt.format(Date(r.updatedAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
