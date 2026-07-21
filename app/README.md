# greenie-auto KMP app

Kotlin Multiplatform app (Android + iOS) with **Gradle 9.0.0**.

## Run Android

1. Open [app](app) in Android Studio.
2. Sync Gradle.
3. Run `androidApp` on emulator/device.

## Run iOS

1. Build iOS framework from terminal in [app](app):
   - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
2. Open iOS project in Xcode (create from template if missing), then integrate shared framework.
