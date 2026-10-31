plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.havenvpn.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.havenvpn.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.wireguard.android:tunnel:1.0.20250531")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
