package com.example.repository

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.FmPresetEntity
import com.example.hardware.*
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FmRadioRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val presetDao = db.fmPresetDao()
    private val probeEngine = HardwareProbeEngine(context)

    private val _probedBackends = MutableStateFlow<List<BackendCapabilities>>(emptyList())
    val probedBackends: StateFlow<List<BackendCapabilities>> = _probedBackends.asStateFlow()

    private val _selectedBackendType = MutableStateFlow(FmBackendType.NONE_RESTRICTED)
    val selectedBackendType: StateFlow<FmBackendType> = _selectedBackendType.asStateFlow()

    private val _antennaStatus = MutableStateFlow(
        AntennaRouteStatus(false, false, false, "Scanning...", false)
    )
    val antennaStatus: StateFlow<AntennaRouteStatus> = _antennaStatus.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogItem>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogItem>> = _diagnosticLogs.asStateFlow()

    private var activeBackend: FmTunerBackend? = null

    val currentStation: StateFlow<FmStation> = MutableStateFlow(
        FmStation(87.5f, "Initialize...", "--", "Probing hardware interfaces...", "--")
    ).also { state ->
        CoroutineScope(Dispatchers.IO).launch {
            activeBackend?.currentStation?.collect { (state as MutableStateFlow).value = it }
        }
    }.asStateFlow()

    val isPoweredOn: StateFlow<Boolean> = MutableStateFlow(false).also { state ->
        CoroutineScope(Dispatchers.IO).launch {
            activeBackend?.isPoweredOn?.collect { (state as MutableStateFlow).value = it }
        }
    }.asStateFlow()

    val isScanning: StateFlow<Boolean> = MutableStateFlow(false).also { state ->
        CoroutineScope(Dispatchers.IO).launch {
            activeBackend?.isScanning?.collect { (state as MutableStateFlow).value = it }
        }
    }.asStateFlow()

    val allPresets: Flow<List<FmPresetEntity>> = presetDao.getAllPresets()

    init {
        probeAndInitialize()
    }

    fun addLog(category: String, status: String, detail: String) {
        val item = DiagnosticLogItem(System.currentTimeMillis(), category, status, detail)
        _diagnosticLogs.update { (listOf(item) + it).take(150) }
    }

    fun probeAndInitialize() {
        CoroutineScope(Dispatchers.IO).launch {
            addLog("System Discovery", "PROBE", "Starting automatic hardware capability discovery on Samsung SM-X216B...")
            val probed = probeEngine.probeAllBackends()
            _probedBackends.value = probed

            val best = probeEngine.selectBestBackend(probed)
            _selectedBackendType.value = best
            addLog("Backend Selection", "SELECTED", "Selected backend: ${best.displayName}")

            val selectedCap = probed.find { it.backendType == best } ?: BackendCapabilities(
                best, false, false, false, emptyList(), "None found"
            )

            activeBackend = RealHardwareFmTunerBackend(context, selectedCap) { cat, stat, det ->
                addLog(cat, stat, det)
            }

            refreshAntennaStatus()

            // Monitor activeBackend flows
            launch {
                activeBackend?.currentStation?.collect { st ->
                    (currentStation as MutableStateFlow).value = st
                }
            }
            launch {
                activeBackend?.isPoweredOn?.collect { p ->
                    (isPoweredOn as MutableStateFlow).value = p
                }
            }
            launch {
                activeBackend?.isScanning?.collect { sc ->
                    (isScanning as MutableStateFlow).value = sc
                }
            }
        }
    }

    fun refreshAntennaStatus() {
        val ant = probeEngine.evaluateAntennaStatus()
        _antennaStatus.value = ant
        addLog("Antenna Check", if (ant.isAntennaReady) "READY" else "WARN", "Active audio routing: ${ant.activeOutputDeviceName}")
    }

    suspend fun togglePower(): Boolean {
        refreshAntennaStatus()
        val backend = activeBackend ?: return false
        return if (isPoweredOn.value) {
            backend.powerOff()
        } else {
            backend.powerOn()
        }
    }

    suspend fun tuneFrequency(freq: Float): Boolean {
        refreshAntennaStatus()
        return activeBackend?.tune(freq) ?: false
    }

    suspend fun seekUp(): Boolean {
        refreshAntennaStatus()
        return activeBackend?.seekUp() ?: false
    }

    suspend fun seekDown(): Boolean {
        refreshAntennaStatus()
        return activeBackend?.seekDown() ?: false
    }

    suspend fun startAutoScan() {
        refreshAntennaStatus()
        activeBackend?.autoScan { station ->
            CoroutineScope(Dispatchers.IO).launch {
                presetDao.insertPreset(
                    FmPresetEntity(station.frequencyMhz, station.stationName, false, station.rssi, station.programType)
                )
            }
        }
    }

    suspend fun toggleFavoritePreset(station: FmStation) {
        val preset = FmPresetEntity(
            station.frequencyMhz,
            station.stationName.ifEmpty { "${station.frequencyMhz} MHz" },
            !station.isFavorite,
            station.rssi,
            station.programType
        )
        presetDao.insertPreset(preset)
        addLog("Preset Manager", "SAVE", "Updated preset ${station.frequencyMhz} MHz (Fav: ${!station.isFavorite})")
    }

    suspend fun deletePreset(freq: Float) {
        presetDao.deleteByFrequency(freq)
        addLog("Preset Manager", "DELETE", "Removed preset $freq MHz")
    }
}
