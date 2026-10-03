// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

// Generates the app's baseline profile by running real journeys (startup,
// scrolling Home, the tabs) on a connected device:
//   ./gradlew :app:generateReleaseBaselineProfile
// It reinstalls and then removes the app, so the device's app data is lost.
android {
    namespace = "com.whiplash.music.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // The emulator or phone that's plugged in; no managed devices.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
