plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.motofbt.app"; compileSdk = 35
    defaultConfig { applicationId = "com.motofbt.app"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2.0" }
    buildFeatures { viewBinding = true }
    androidResources { noCompress += "task" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mediapipe:tasks-vision:0.10.29")
}
