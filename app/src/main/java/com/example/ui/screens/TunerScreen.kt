package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FmPresetEntity
import com.example.model.*

@Composable
fun TunerScreen(
    station: FmStation,
    isPoweredOn: Boolean,
    isScanning: Boolean,
    selectedBackend: FmBackendType,
    antennaStatus: AntennaRouteStatus,
    presets: List<FmPresetEntity>,
    onTogglePower: () -> Unit,
    onTune: (Float) -> Unit,
    onSeekUp: () -> Unit,
    onSeekDown: () -> Unit,
    onAutoScan: () -> Unit,
    onToggleFavorite: (FmStation) -> Unit
) {
    var showDirectInput by remember { mutableStateOf(false) }
    var inputFreqText by remember { mutableStateOf("") }

    if (showDirectInput) {
        AlertDialog(
            onDismissRequest = { showDirectInput = false },
            title = { Text("Direct Frequency Tuning") },
            text = {
                Column {
                    Text("Enter frequency in MHz (87.5 - 108.0):", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputFreqText,
                        onValueChange = { inputFreqText = it },
                        placeholder = { Text("e.g. 101.9") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val freq = inputFreqText.toFloatOrNull()
                    if (freq != null && freq in 87.5f..108.0f) {
                        onTune(freq)
                    }
                    showDirectInput = false
                }) {
                    Text("Tune")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectInput = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Hardware Banner & Antenna Route
            HardwareBannerCard(selectedBackend, antennaStatus)
        }

        item {
            // Main Digital Tuner Display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isPoweredOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isPoweredOn) "TUNER ACTIVE" else "POWER OFF / STANDBY",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPoweredOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge(containerColor = if (station.isStereo) Color(0xFF4CAF50) else Color(0xFF9E9E9E)) {
                                Text(if (station.isStereo) "STEREO" else "MONO", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("RSSI: ${station.rssi} dBm", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Large LCD Frequency Display
                    Text(
                        text = String.format("%.1f", station.frequencyMhz),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 64.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "MHz",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = station.stationName.ifEmpty { "QUALCOMM FM BROADCAST" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "RDS/RT: ${station.radioText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Primary Hardware Controls Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSeekDown,
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        ) {
                            Icon(Icons.Default.FastRewind, contentDescription = "Seek Down", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }

                        Button(
                            onClick = onTogglePower,
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPoweredOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power Toggle")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPoweredOn) "POWER OFF" else "POWER ON")
                        }

                        IconButton(
                            onClick = onSeekUp,
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = "Seek Up", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(onClick = { showDirectInput = true }) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Direct Freq")
                        }

                        OutlinedButton(onClick = onAutoScan, enabled = !isScanning) {
                            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isScanning) "Scanning..." else "Auto Scan")
                        }

                        IconButton(onClick = { onToggleFavorite(station) }) {
                            Icon(
                                imageVector = if (presets.any { it.frequency == station.frequencyMhz }) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Quick Tuning Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (presets.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No saved station presets. Run Auto Scan or add favorites.")
                    }
                }
            }
        } else {
            item {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presets) { p ->
                        PresetChipCard(p) { onTune(p.frequency) }
                    }
                }
            }
        }
    }
}

@Composable
fun HardwareBannerCard(selectedBackend: FmBackendType, antennaStatus: AntennaRouteStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hardware Backend: ${selectedBackend.displayName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(selectedBackend.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Antenna Route: ${antennaStatus.activeOutputDeviceName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PresetChipCard(preset: FmPresetEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${preset.frequency} MHz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(preset.stationName.take(12), style = MaterialTheme.typography.bodySmall)
        }
    }
}
