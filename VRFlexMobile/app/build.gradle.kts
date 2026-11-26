plugins { id("com.android.application") }
android {
 namespace = "com.vrflex.mobile"
 compileSdk = 35
 defaultConfig { applicationId = "com.vrflex.mobile"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2.0" }
}
dependencies {
 implementation("com.github.osservatorionessuno:libadb-android-bc:3.2.0")
 implementation("org.bouncycastle:bcprov-jdk15to18:1.84")
 implementation("org.bouncycastle:bcpkix-jdk15to18:1.84")
}
