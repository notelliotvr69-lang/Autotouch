plugins {
    id("com.android.application")
}

android {
    namespace = "dev.autotouch.dualsenserumble"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.autotouch.dualsenserumble"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
