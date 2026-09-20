plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.piyushos.canary"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.piyushos.canary"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // ZERO dependencies - yeh ekdum minimal app hai.
    // Agar YEH bhi crash kare to problem PiyushOS ki code me nahi,
    // phone ke system/install process me hai.
}
