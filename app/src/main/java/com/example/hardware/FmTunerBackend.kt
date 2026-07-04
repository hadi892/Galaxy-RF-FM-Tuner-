package com.example.hardware

import com.example.model.*
import kotlinx.coroutines.flow.StateFlow

interface FmTunerBackend {
    val currentStation: StateFlow<FmStation>
    val isPoweredOn: StateFlow<Boolean>
    val isScanning: StateFlow<Boolean>
    val isMuted: StateFlow<Boolean>
    val volumeLevel: StateFlow<Float>

    suspend fun powerOn(): Boolean
    suspend fun powerOff(): Boolean
    suspend fun tune(frequencyMhz: Float): Boolean
    suspend fun seekUp(): Boolean
    suspend fun seekDown(): Boolean
    suspend fun autoScan(onStationFound: (FmStation) -> Unit): List<FmStation>
    suspend fun setMute(muted: Boolean)
    suspend fun setVolume(volume: Float)
    suspend fun setStereo(stereo: Boolean)
}
