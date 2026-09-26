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
        versionCode = 9
        versionName = "7.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
