plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.transitnavproject"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.transitnavproject"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ==================== CORE ANDROID ====================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ==================== COMPOSE BOM & UI ====================
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.material.icons.extended)

    // ==================== MATERIAL DESIGN ====================
    implementation(libs.material)

    // ==================== MAPS & LOCATION ====================
    implementation(libs.play.services.maps)
    implementation("com.google.maps.android:maps-compose:8.1.0")

    // ==================== OSMDROID FOR MAPS ====================
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // REMOVED: osmdroid-http-provider (not needed - included in osmdroid-android)

    // ==================== QR CODE GENERATION ====================
    implementation("com.google.zxing:core:3.5.1")

    // ==================== DATA PERSISTENCE ====================
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ==================== KOTLIN COROUTINES ====================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ==================== NETWORKING ====================
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // ==================== JSON PARSING ====================
    implementation("com.google.code.gson:gson:2.10.1")

    // ==================== SERIALIZATION ====================
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // ==================== SYSTEM UTILITIES ====================
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // ==================== PREFERENCES ====================
    implementation("androidx.preference:preference-ktx:1.2.1")

    // ==================== IMAGE LOADING ====================
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ==================== WINDOW SIZE ====================
    implementation("androidx.compose.material3:material3-window-size-class:1.1.2")

    // ==================== TESTING ====================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // ==================== DEBUG ====================
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}