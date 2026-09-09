import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Recomputed on every configuration, so the value changes on every build. That
// forces BuildConfig to be regenerated and guarantees a fresh stamp.
val buildStamp: String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date())

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "net.seb.dmxorcist"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.seb.dmxorcist"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "BUILD_TIME", "\"$buildStamp\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // USB serial driver (FTDI, CH340, CP210x, CDC-ACM)
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
}
