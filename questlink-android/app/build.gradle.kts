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
        versionCode = 13
        versionName = "7.12"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
