# FM Radio Tuner & RF Diagnostics Project

A production-grade Android application engineered specifically to interact with Qualcomm Snapdragon FM hardware (`qcom.fmradio`), Samsung framework interfaces, and Audio HAL routing on devices like the **Samsung Galaxy Tab A9+ 5G (`SM-X216B`, Snapdragon 695 SM6375)** running Android 16.

## Architecture & Features
- **Runtime Capability Probe**: Dynamically inspects firmware endpoints (`qcom.fmradio.FmReceiver`, `com.sec.android.app.fm.FmRadio`, Binder services, and AudioDeviceInfo HAL routes).
- **Strict No-Simulation Guarantee**: Per security and authenticity mandates, if direct OS application sandboxing or SELinux policies restrict hardware RF interface access on non-system signed apps, the app reports exact diagnostics and access restrictions rather than generating fake or synthesized audio/reception.
- **Antenna Path Monitoring**: Continuously evaluates wired 3.5mm headsets, USB-C analog adapters, and digital USB DAC paths.
- **Modern Android Stack**: Built with Kotlin Coroutines/Flow, Jetpack Compose Material 3 (Tablet landscape/portrait optimized), MVVM architecture, Room database for persistent preset storage, and foreground media playback services.

## CI/CD Workflow
Includes an automated GitHub Actions CI workflow supporting JDK 21 and Gradle 9.3.x for building Debug and Release artifacts.
