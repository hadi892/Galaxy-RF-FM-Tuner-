package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DiagnosticsScreen(
    selectedBackend: FmBackendType,
    probedBackends: List<BackendCapabilities>,
    antennaStatus: AntennaRouteStatus,
    diagnosticLogs: List<DiagnosticLogItem>,
    onRetryProbe: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Qualcomm SM6375 RF Diagnostics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Samsung Galaxy Tab A9+ 5G (SM-X216B)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onRetryProbe) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-Probe")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("System Environment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Divider()
                    DiagRow("Device Model:", "Samsung Galaxy Tab A9+ 5G (SM-X216B)")
                    DiagRow("Chipset / SoC:", "Qualcomm Snapdragon 695 5G (SM6375)")
                    DiagRow("CPU Arch:", "ARM64-v8a")
                    DiagRow("OS Target:", "Android 16 (API Level 36) / SDK ${Build.VERSION.SDK_INT}")
                    DiagRow("Selected Backend:", selectedBackend.displayName)
                    DiagRow("Antenna Status:", if (antennaStatus.isAntennaReady) "READY (${antennaStatus.activeOutputDeviceName})" else "NO ANTENNA DETECTED")
                }
            }
        }

        item {
            Text("Runtime Capability Probe Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(probedBackends) { cap ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (cap.isAccessible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cap.backendType.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Badge(containerColor = if (cap.isAccessible) Color(0xFF4CAF50) else Color(0xFFE53935)) {
                            Text(if (cap.isAccessible) "ACCESSIBLE" else "RESTRICTED", color = Color.White, modifier = Modifier.padding(4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(cap.backendType.description, style = MaterialTheme.typography.bodySmall)
                    
                    if (cap.restrictionReason != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "Restriction / Reason: ${cap.restrictionReason}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (cap.accessibleMethods.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Exposed Endpoints / Methods:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        cap.accessibleMethods.take(4).forEach { method ->
                            Text(" • $method", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        item {
            Text("Live Hardware Diagnostic Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(diagnosticLogs) { log ->
            LogItemRow(log)
        }
    }
}

@Composable
fun DiagRow(label: String, valText: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LogItemRow(item: DiagnosticLogItem) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                formatter.format(Date(item.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Badge(
                containerColor = when (item.status) {
                    "SUCCESS" -> Color(0xFF388E3C)
                    "ERROR" -> Color(0xFFD32F2F)
                    "RESTRICTED" -> Color(0xFFF57C00)
                    else -> MaterialTheme.colorScheme.primary
                }
            ) {
                Text(item.status, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(item.category, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(item.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
