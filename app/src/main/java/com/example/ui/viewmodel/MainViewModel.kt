package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FmPresetEntity
import com.example.model.*
import com.example.repository.FmRadioRepository
import com.example.service.FmRadioService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FmRadioRepository(application)

    val currentStation: StateFlow<FmStation> = repository.currentStation
    val isPoweredOn: StateFlow<Boolean> = repository.isPoweredOn
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val probedBackends: StateFlow<List<BackendCapabilities>> = repository.probedBackends
    val selectedBackendType: StateFlow<FmBackendType> = repository.selectedBackendType
    val antennaStatus: StateFlow<AntennaRouteStatus> = repository.antennaStatus
    val diagnosticLogs: StateFlow<List<DiagnosticLogItem>> = repository.diagnosticLogs

    val presets: StateFlow<List<FmPresetEntity>> = repository.allPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePower() {
        viewModelScope.launch {
            val isOn = repository.togglePower()
            if (isOn) {
                val serviceIntent = Intent(getApplication(), FmRadioService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(serviceIntent)
                } else {
                    getApplication<Application>().startService(serviceIntent)
                }
            }
        }
    }

    fun tuneFrequency(freq: Float) {
        viewModelScope.launch {
            repository.tuneFrequency(freq)
        }
    }

    fun seekUp() {
        viewModelScope.launch {
            repository.seekUp()
        }
    }

    fun seekDown() {
        viewModelScope.launch {
            repository.seekDown()
        }
    }

    fun startAutoScan() {
        viewModelScope.launch {
            repository.startAutoScan()
        }
    }

    fun toggleFavorite(station: FmStation) {
        viewModelScope.launch {
            repository.toggleFavoritePreset(station)
        }
    }

    fun deletePreset(freq: Float) {
        viewModelScope.launch {
            repository.deletePreset(freq)
        }
    }

    fun retryHardwareProbe() {
        viewModelScope.launch {
            repository.probeAndInitialize()
        }
    }
}
