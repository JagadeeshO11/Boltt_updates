plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jagadeesh.bolttisland"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jagadeesh.bolttisland"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.1"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
