plugins {
    id("com.android.application")
}

android {
    namespace = "com.questtools.questlink"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.questtools.questlink"
        minSdk = 32
        targetSdk = 35
        versionCode = 12
        versionName = "7.11"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
