plugins {
    id("com.android.application")
}

android {
    namespace = "com.questtools.questlink"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    buildFeatures { prefab = true }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }

    defaultConfig {
        applicationId = "com.questtools.questlink"
        minSdk = 32
        targetSdk = 35
        versionCode = 13
        versionName = "7.12-dev"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("org.khronos.openxr:openxr_loader_for_android:1.1.49")
}
