package com.example.hardware

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.model.*

class HardwareProbeEngine(private val context: Context) {

    companion object {
        private const val TAG = "HardwareProbeEngine"
    }

    fun probeAllBackends(): List<BackendCapabilities> {
        val results = mutableListOf<BackendCapabilities>()

        // 1. Probe Qualcomm FM HAL (Snapdragon SM6375 / qcom.fmradio)
        results.add(probeQualcommHal())

        // 2. Probe Samsung FM Framework
        results.add(probeSamsungFramework())

        // 3. Probe Binder Services
        results.add(probeBinderServices())

        // 4. Probe Audio HAL / AudioDeviceInfo for TYPE_FM / TYPE_FM_TUNER
        results.add(probeAudioHal())

        return results
    }

    fun selectBestBackend(probed: List<BackendCapabilities>): FmBackendType {
        // Priority order: Qualcomm HAL -> Samsung Framework -> Binder -> Audio HAL
        val accessible = probed.filter { it.isAccessible }
        if (accessible.isEmpty()) {
            return FmBackendType.NONE_RESTRICTED
        }
        val priority = listOf(
            FmBackendType.QUALCOMM_FMRADIO_HAL,
            FmBackendType.SAMSUNG_FM_FRAMEWORK,
            FmBackendType.SAMSUNG_BINDER_SERVICE,
            FmBackendType.GENERIC_AUDIO_HAL
        )
        for (type in priority) {
            if (accessible.any { it.backendType == type }) {
                return type
            }
        }
        return accessible.first().backendType
    }

    private fun probeQualcommHal(): BackendCapabilities {
        val qcomClasses = listOf(
            "qcom.fmradio.FmReceiver",
            "qcom.fmradio.FmConfig",
            "vendor.qti.hardware.fm.V1_0.IFmHci"
        )
        val loadedMethods = mutableListOf<String>()
        var classFound = false
        var restrictionReason: String? = null

        for (className in qcomClasses) {
            try {
                val clazz = Class.forName(className)
                classFound = true
                clazz.methods.forEach { m ->
                    loadedMethods.add("${clazz.simpleName}.${m.name}()")
                }
            } catch (e: ClassNotFoundException) {
                // Not found in classloader
            } catch (e: Throwable) {
                restrictionReason = "Access exception on $className: ${e.message}"
            }
        }

        if (!classFound) {
            restrictionReason = "qcom.fmradio SDK classes not embedded or exposed by Android 16 app sandbox on SM-X216B."
        }

        return BackendCapabilities(
            backendType = FmBackendType.QUALCOMM_FMRADIO_HAL,
            isAccessible = classFound && loadedMethods.isNotEmpty(),
            classLoaded = classFound,
            binderFound = false,
            accessibleMethods = loadedMethods,
            restrictionReason = restrictionReason
        )
    }

    private fun probeSamsungFramework(): BackendCapabilities {
        val samsungClasses = listOf(
            "com.sec.android.app.fm.FmRadio",
            "com.samsung.android.app.fm.FmPlayer",
            "com.sec.android.hardware.fm.FmReceiver"
        )
        val loadedMethods = mutableListOf<String>()
        var classFound = false
        var restrictionReason: String? = null

        for (className in samsungClasses) {
            try {
                val clazz = Class.forName(className)
                classFound = true
                clazz.methods.forEach { m ->
                    loadedMethods.add("${clazz.simpleName}.${m.name}()")
                }
            } catch (e: ClassNotFoundException) {
                // Not loaded
            } catch (e: Throwable) {
                restrictionReason = "Security/Access error on $className: ${e.message}"
            }
        }

        if (!classFound) {
            restrictionReason = "Samsung proprietary FM player interfaces require system signature or vendor allowlist."
        }

        return BackendCapabilities(
            backendType = FmBackendType.SAMSUNG_FM_FRAMEWORK,
            isAccessible = classFound && loadedMethods.isNotEmpty(),
            classLoaded = classFound,
            binderFound = false,
            accessibleMethods = loadedMethods,
            restrictionReason = restrictionReason
        )
    }

    private fun probeBinderServices(): BackendCapabilities {
        val targetServices = listOf("fm_receiver", "samsung.fm.service", "fm_radio", "qti.fm.service")
        val foundServices = mutableListOf<String>()
        var binderFound = false
        var restrictionReason: String? = null

        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val listServicesMethod = serviceManagerClass.getMethod("listServices")

            val allServices = listServicesMethod.invoke(null) as? Array<String> ?: emptyArray()
            for (serviceName in targetServices) {
                if (allServices.contains(serviceName)) {
                    val binder = getServiceMethod.invoke(null, serviceName)
                    if (binder != null) {
                        binderFound = true
                        foundServices.add(serviceName)
                    }
                }
            }
            if (!binderFound && allServices.any { it.contains("fm", ignoreCase = true) }) {
                allServices.filter { it.contains("fm", ignoreCase = true) }.forEach {
                    foundServices.add("Matched: $it")
                }
            }
        } catch (e: Throwable) {
            restrictionReason = "ServiceManager reflection blocked by Android 16 SELinux policy: ${e.message}"
        }

        if (!binderFound && restrictionReason == null) {
            restrictionReason = "No public FM Binder endpoints published in ServiceManager list."
        }

        return BackendCapabilities(
            backendType = FmBackendType.SAMSUNG_BINDER_SERVICE,
            isAccessible = binderFound,
            classLoaded = false,
            binderFound = binderFound,
            accessibleMethods = foundServices,
            restrictionReason = restrictionReason
        )
    }

    private fun probeAudioHal(): BackendCapabilities {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        val fmDevices = mutableListOf<String>()

        for (device in devices) {
            // AudioDeviceInfo.TYPE_FM = 14, TYPE_FM_TUNER = 16
            if (device.type == 14 || device.type == 16 || device.productName.toString().contains("FM", ignoreCase = true)) {
                fmDevices.add("AudioDevice[ID=${device.id}, Type=${device.type}, Name=${device.productName}]")
            }
        }

        val accessible = fmDevices.isNotEmpty()
        val reason = if (!accessible) {
            "AudioPolicy does not expose TYPE_FM (14) or TYPE_FM_TUNER (16) directly to unprivileged user space apps."
        } else null

        return BackendCapabilities(
            backendType = FmBackendType.GENERIC_AUDIO_HAL,
            isAccessible = accessible,
            classLoaded = accessible,
            binderFound = false,
            accessibleMethods = fmDevices,
            restrictionReason = reason
        )
    }

    fun evaluateAntennaStatus(): AntennaRouteStatus {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val allDevices = outputs + inputs

        var has35mm = false
        var hasUsbCAnalog = false
        var hasUsbDigital = false
        val activeNames = mutableListOf<String>()

        for (device in allDevices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                    has35mm = true
                    activeNames.add(device.productName.toString().ifEmpty { "Wired Headset (3.5mm)" })
                }
                AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    hasUsbCAnalog = true
                    activeNames.add(device.productName.toString().ifEmpty { "USB-C Headset" })
                }
                AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    hasUsbDigital = true
                    activeNames.add(device.productName.toString().ifEmpty { "USB Audio Adapter" })
                }
            }
        }

        val isAntennaReady = has35mm || hasUsbCAnalog || hasUsbDigital
        val deviceSummary = if (activeNames.isNotEmpty()) activeNames.joinToString(", ") else "Internal Speaker Only (No Antenna Connected)"

        return AntennaRouteStatus(
            hasWiredHeadset35mm = has35mm,
            hasUsbCAnalogHeadset = hasUsbCAnalog,
            hasUsbDigitalAudio = hasUsbDigital,
            activeOutputDeviceName = deviceSummary,
            isAntennaReady = isAntennaReady
        )
    }
}
