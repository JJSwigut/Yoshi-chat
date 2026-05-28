package com.example.yoshichat.data.config

import android.os.Build
import com.example.yoshichat.BuildConfig

object DevServerConfig {
    val isEmulator: Boolean = detectEmulator()

    val connectionMode: String =
        if (isEmulator) {
            "emulator"
        } else {
            "physical-usb-reverse"
        }

    val apiBaseUrl: String =
        if (isEmulator) {
            BuildConfig.API_BASE_URL
        } else {
            "http://127.0.0.1:8787"
        }

    val agentsBaseUrl: String =
        if (isEmulator) {
            BuildConfig.AGENTS_BASE_URL
        } else {
            "http://127.0.0.1:8002"
        }

    private fun detectEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            manufacturer.contains("genymotion") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            brand.startsWith("generic") && device.startsWith("generic") ||
            product.contains("sdk") ||
            product.contains("emulator")
    }
}
