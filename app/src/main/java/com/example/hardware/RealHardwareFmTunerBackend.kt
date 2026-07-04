package com.example.hardware

import android.content.Context
import android.util.Log
import com.example.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Concrete implementation that attempts real Qualcomm / Samsung / Audio HAL methods via reflection.
 * Per strict requirements: If real hardware access is restricted or unavailable, it transparently
 * reports the hardware error state instead of simulating or generating fake audio/reception.
 */
class RealHardwareFmTunerBackend(
    private val context: Context,
    private val capabilities: BackendCapabilities,
    private val onLog: (String, String, String) -> Unit
) : FmTunerBackend {

    private val _currentStation = MutableStateFlow(
        FmStation(
            frequencyMhz = 87.5f,
            stationName = "UNRESTRICTED / TUNING",
            programService = "--",
            radioText = "Ready for hardware tune",
            programType = "None",
            rssi = 0,
            isStereo = true
        )
    )
    override val currentStation: StateFlow<FmStation> = _currentStation.asStateFlow()

    private val _isPoweredOn = MutableStateFlow(false)
    override val isPoweredOn: StateFlow<Boolean> = _isPoweredOn.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _volumeLevel = MutableStateFlow(0.8f)
    override val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    private var qcomReceiverInstance: Any? = null

    override suspend fun powerOn(): Boolean {
        onLog("HardwareBackend", "INFO", "Attempting real hardware power ON using backend: ${capabilities.backendType.displayName}")
        
        when (capabilities.backendType) {
            FmBackendType.QUALCOMM_FMRADIO_HAL -> {
                try {
                    val clazz = Class.forName("qcom.fmradio.FmReceiver")
                    val constructor = clazz.getConstructor(String::class.java, Class.forName("qcom.fmradio.FmRxEvCallbacksAdaptor"))
                    qcomReceiverInstance = constructor.newInstance("/dev/radio0", null)
                    val enableMethod = clazz.getMethod("enable", Class.forName("qcom.fmradio.FmConfig"))
                    val result = enableMethod.invoke(qcomReceiverInstance, null) as? Boolean ?: false
                    _isPoweredOn.value = result
                    onLog("Qualcomm HAL", if (result) "SUCCESS" else "WARN", "qcom.fmradio.FmReceiver.enable() returned $result")
                    return result
                } catch (e: Throwable) {
                    onLog("Qualcomm HAL", "ERROR", "Real hardware enable failed: ${e.message ?: e.javaClass.simpleName}")
                    _isPoweredOn.value = false
                    return false
                }
            }
            FmBackendType.SAMSUNG_FM_FRAMEWORK -> {
                try {
                    val clazz = Class.forName("com.sec.android.app.fm.FmRadio")
                    val method = clazz.getMethod("powerOn")
                    method.invoke(null)
                    _isPoweredOn.value = true
                    onLog("Samsung Framework", "SUCCESS", "Invoked real com.sec.android.app.fm.FmRadio.powerOn()")
                    return true
                } catch (e: Throwable) {
                    onLog("Samsung Framework", "ERROR", "Real framework powerOn failed: ${e.message}")
                    _isPoweredOn.value = false
                    return false
                }
            }
            FmBackendType.SAMSUNG_BINDER_SERVICE -> {
                onLog("Binder Service", "ERROR", "Direct Binder service transaction requires system AID_SYSTEM credentials.")
                _isPoweredOn.value = false
                return false
            }
            FmBackendType.GENERIC_AUDIO_HAL -> {
                onLog("Audio HAL", "WARN", "AudioDeviceInfo probe found FM route, but hardware tuner control requires HAL daemon access.")
                _isPoweredOn.value = false
                return false
            }
            FmBackendType.NONE_RESTRICTED -> {
                onLog("Sandbox Check", "RESTRICTED", "Hardware tuner access blocked by Android 16 unprivileged sandbox policy. No fake reception generated.")
                _isPoweredOn.value = false
                return false
            }
        }
    }

    override suspend fun powerOff(): Boolean {
        onLog("HardwareBackend", "INFO", "Powering OFF hardware receiver")
        _isPoweredOn.value = false
        qcomReceiverInstance = null
        return true
    }

    override suspend fun tune(frequencyMhz: Float): Boolean {
        val clamped = frequencyMhz.coerceIn(87.5f, 108.0f)
        onLog("HardwareBackend", "INFO", "Attempting real tune to ${clamped}MHz")
        
        if (!capabilities.isAccessible || capabilities.backendType == FmBackendType.NONE_RESTRICTED) {
            onLog("Tuner Error", "RESTRICTED", "Cannot tune ${clamped}MHz: Real hardware interface inaccessible. (Strict NO SIMULATION policy enforced)")
            _currentStation.value = _currentStation.value.copy(
                frequencyMhz = clamped,
                stationName = "HW RESTRICTED",
                programService = "NO SIG",
                radioText = "Real FM hardware interface blocked by SELinux/Platform sandbox",
                rssi = 0
            )
            return false
        }

        try {
            if (capabilities.backendType == FmBackendType.QUALCOMM_FMRADIO_HAL && qcomReceiverInstance != null) {
                val clazz = qcomReceiverInstance!!.javaClass
                val setFreqMethod = clazz.getMethod("setStation", Int::class.java)
                val freqKHz = (clamped * 1000).toInt()
                val res = setFreqMethod.invoke(qcomReceiverInstance, freqKHz) as? Boolean ?: false
                if (res) {
                    val getRssiMethod = clazz.getMethod("getRssi")
                    val rssiVal = getRssiMethod.invoke(qcomReceiverInstance) as? Int ?: 0
                    _currentStation.value = _currentStation.value.copy(
                        frequencyMhz = clamped,
                        stationName = "${clamped} MHz",
                        rssi = rssiVal
                    )
                }
                return res
            }
        } catch (e: Throwable) {
            onLog("Tuner Error", "ERROR", "Hardware tune exception: ${e.message}")
        }

        _currentStation.value = _currentStation.value.copy(
            frequencyMhz = clamped,
            stationName = "HW RESTRICTED",
            programService = "--",
            radioText = "Direct HAL tune failed on non-system application process",
            rssi = 0
        )
        return false
    }

    override suspend fun seekUp(): Boolean {
        val nextFreq = (_currentStation.value.frequencyMhz + 0.1f).let { if (it > 108.0f) 87.5f else (Math.round(it * 10.0f) / 10.0f) }
        return tune(nextFreq)
    }

    override suspend fun seekDown(): Boolean {
        val prevFreq = (_currentStation.value.frequencyMhz - 0.1f).let { if (it < 87.5f) 108.0f else (Math.round(it * 10.0f) / 10.0f) }
        return tune(prevFreq)
    }

    override suspend fun autoScan(onStationFound: (FmStation) -> Unit): List<FmStation> {
        _isScanning.value = true
        onLog("Hardware Scan", "INFO", "Starting hardware frequency scan across 87.5MHz - 108.0MHz...")
        val foundList = mutableListOf<FmStation>()

        if (!capabilities.isAccessible || capabilities.backendType == FmBackendType.NONE_RESTRICTED) {
            delay(800)
            onLog("Hardware Scan", "RESTRICTED", "Scan terminated: OS sandbox restricts real RF tuner probing. No simulated or fake stations generated.")
            _isScanning.value = false
            return emptyList()
        }

        // If real HAL accessible, scan through step frequencies
        var current = 87.5f
        while (current <= 108.0f && _isScanning.value) {
            val success = tune(current)
            if (success && _currentStation.value.rssi > -85) {
                foundList.add(_currentStation.value)
                onStationFound(_currentStation.value)
            }
            delay(150)
            current = Math.round((current + 0.2f) * 10.0f) / 10.0f
        }

        _isScanning.value = false
        return foundList
    }

    override suspend fun setMute(muted: Boolean) {
        _isMuted.value = muted
        onLog("Hardware Control", "INFO", "Set Mute: $muted")
    }

    override suspend fun setVolume(volume: Float) {
        _volumeLevel.value = volume.coerceIn(0f, 1f)
    }

    override suspend fun setStereo(stereo: Boolean) {
        _currentStation.value = _currentStation.value.copy(isStereo = stereo)
        onLog("Hardware Control", "INFO", "Requested Stereo Mode: $stereo")
    }
}
