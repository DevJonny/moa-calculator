plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.devjonny.moa"
    // Wear Compose 1.7 needs compileSdk 37+.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "io.github.devjonny.moa"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // A shared debug key so builds from any machine can update the app on the watch in place.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.activity.compose)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    testImplementation(libs.junit)
}
