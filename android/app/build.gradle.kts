plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.compose")
}

// Kotlin 1.9.24 ke liye compatible Compose Compiler version
compose {
    kotlinCompilerPluginVersion = "1.5.10"
}

android {
    namespace = "com.piyushos.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.piyushos.app"
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
    // NB: buildFeatures.compose yahan nahi chahiye - org.jetbrains.compose plugin
    // khud Android ke liye compose build feature enable kar deta hai
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    // On-device OCR - jab UI tree me text na milo tab screenshot se text nikaalne ke liye
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
