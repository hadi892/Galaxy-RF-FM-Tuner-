package com.example.model

enum class FmBackendType(val displayName: String, val description: String) {
    LINUX_V4L2_DRIVER(
        "Linux V4L2 Driver (/dev/radio0)",
        "Direct Linux V4L2 character device driver probe (/dev/radio0) & vendor JNI libraries."
    ),
    QUALCOMM_FMRADIO_HAL(
        "Qualcomm FM HAL (qcom.fmradio)",
        "Direct Qualcomm Snapdragon FM hardware interface via qcom.fmradio.FmReceiver / vendor HAL."
    ),
    SAMSUNG_FM_FRAMEWORK(
        "Samsung FM Framework",
        "Samsung proprietary FM Player service (com.sec.android.app.fm / com.samsung.android.app.fm)."
    ),
    SAMSUNG_BINDER_SERVICE(
        "Vendor Binder Service",
        "System ServiceManager probe for 'fm_receiver' or 'samsung.fm.service'."
    ),
    GENERIC_AUDIO_HAL(
        "Audio HAL / Policy Probe",
        "Direct hardware audio routing via AudioDeviceInfo.TYPE_FM / TYPE_FM_TUNER."
    ),
    NONE_RESTRICTED(
        "Direct Access Restricted / Hardware Sandbox",
        "Real FM tuner hardware interfaces are restricted by OS sandbox, SELinux policy, or require vendor platform signature."
    )
}

data class BackendCapabilities(
    val backendType: FmBackendType,
    val isAccessible: Boolean,
    val classLoaded: Boolean,
    val binderFound: Boolean,
    val accessibleMethods: List<String>,
    val restrictionReason: String? = null
)

data class AntennaRouteStatus(
    val hasWiredHeadset35mm: Boolean,
    val hasUsbCAnalogHeadset: Boolean,
    val hasUsbDigitalAudio: Boolean,
    val activeOutputDeviceName: String,
    val isAntennaReady: Boolean
)

data class FmStation(
    val frequencyMhz: Float,
    val stationName: String = "",
    val programService: String = "",
    val radioText: String = "",
    val programType: String = "",
    val rssi: Int = 0,
    val isStereo: Boolean = true,
    val isFavorite: Boolean = false
)

data class DiagnosticLogItem(
    val timestamp: Long,
    val category: String,
    val status: String,
    val detail: String
)

data class HardwareDiagnosticReport(
    val deviceModel: String,
    val chipset: String,
    val osVersion: String,
    val sdkInt: Int,
    val selectedBackend: FmBackendType,
    val probedBackends: List<BackendCapabilities>,
    val antennaStatus: AntennaRouteStatus,
    val permissionsGranted: Map<String, Boolean>,
    val diagnosticLogs: List<DiagnosticLogItem>
)
