package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.TunerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

@android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.retryHardwareProbe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissions = mutableListOf(
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val currentStation by viewModel.currentStation.collectAsStateWithLifecycle()
                val isPoweredOn by viewModel.isPoweredOn.collectAsStateWithLifecycle()
                val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
                val selectedBackend by viewModel.selectedBackendType.collectAsStateWithLifecycle()
                val probedBackends by viewModel.probedBackends.collectAsStateWithLifecycle()
                val antennaStatus by viewModel.antennaStatus.collectAsStateWithLifecycle()
                val diagnosticLogs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()
                val presets by viewModel.presets.collectAsStateWithLifecycle()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: "tuner"

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == "tuner",
                                onClick = {
                                    navController.navigate("tuner") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(Icons.Default.Radio, contentDescription = "Tuner") },
                                label = { Text("RF Tuner") }
                            )

                            NavigationBarItem(
                                selected = currentRoute == "diagnostics",
                                onClick = {
                                    navController.navigate("diagnostics") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(Icons.Default.Info, contentDescription = "Diagnostics") },
                                label = { Text("Hardware Diagnostics") }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "tuner",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("tuner") {
                            TunerScreen(
                                station = currentStation,
                                isPoweredOn = isPoweredOn,
                                isScanning = isScanning,
                                selectedBackend = selectedBackend,
                                antennaStatus = antennaStatus,
                                presets = presets,
                                onTogglePower = { viewModel.togglePower() },
                                onTune = { freq -> viewModel.tuneFrequency(freq) },
                                onSeekUp = { viewModel.seekUp() },
                                onSeekDown = { viewModel.seekDown() },
                                onAutoScan = { viewModel.startAutoScan() },
                                onToggleFavorite = { station -> viewModel.toggleFavorite(station) }
                            )
                        }

                        composable("diagnostics") {
                            DiagnosticsScreen(
                                selectedBackend = selectedBackend,
                                probedBackends = probedBackends,
                                antennaStatus = antennaStatus,
                                diagnosticLogs = diagnosticLogs,
                                onRetryProbe = { viewModel.retryHardwareProbe() }
                            )
                        }
                    }
                }
            }
        }
    }
}

