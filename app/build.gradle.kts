plugins { id("com.android.application") }
android {
    namespace = "com.kays.truthserum"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kays.truthserum.v3"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.0"
        ndk.abiFilters += "arm64-v8a"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        noCompress += listOf("onnx", "wav")
    }
}
dependencies {
    implementation(files("libs/sherpa-onnx-1.13.4.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
}
