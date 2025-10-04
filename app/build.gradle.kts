plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

android {
    namespace = "jp.saitama.orange.mycompass"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.saitama.orange.mycompass"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Enable 16 KB page size alignment for native libraries
            useLegacyPackaging = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Lifecycle-aware Compose utils
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended:1.7.6")

    // OSMDroid for OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // SceneView for 3D rendering (latest version with 16KB alignment support)
    implementation("io.github.sceneview:sceneview:2.3.0")

    // ARSceneView for AR rendering (latest version with 16KB alignment support)
    implementation("io.github.sceneview:arsceneview:2.3.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}