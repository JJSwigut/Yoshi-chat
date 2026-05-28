import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.yoshichat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.yoshichat"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8787\"")
        buildConfigField("String", "AGENTS_BASE_URL", "\"http://10.0.2.2:8002\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val androidSdkRoot =
    providers.environmentVariable("ANDROID_SDK_ROOT")
        .orElse(providers.environmentVariable("ANDROID_HOME"))
        .orElse(providers.provider { "${System.getProperty("user.home")}/Library/Android/sdk" })

val adbPath =
    androidSdkRoot.map { sdkRoot ->
        val adbFile = file("$sdkRoot/platform-tools/adb")
        if (adbFile.exists()) adbFile.absolutePath else "adb"
    }

tasks.register("installAndLaunchDebug") {
    group = "run"
    description = "Installs the debug APK and launches MainActivity on the single connected emulator/device."

    dependsOn("installDebug")

    doLast {
        val adb = adbPath.get()
        exec {
            commandLine(adb, "wait-for-device")
        }
        exec {
            commandLine(
                adb,
                "shell",
                "am",
                "start",
                "-n",
                "${android.defaultConfig.applicationId}/${android.defaultConfig.applicationId}.MainActivity",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
