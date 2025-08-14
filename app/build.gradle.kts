plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.csvcharts"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.csvcharts"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Pour signer en CI: configure signingConfigs + secrets GitHub (voir plus bas)
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Compose Compiler via plugin (pas besoin de versionner manuellement)
    composeCompiler {
        enableStrongSkippingMode.set(true)
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // --- Compose BOM (août 2025) ---
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity Compose (setContent, ActivityResult en Compose)
    implementation("androidx.activity:activity-compose:1.10.1")

    // MPAndroidChart (LineChart)
    implementation("com.github.Philjay:mpandroidchart:3.1.0")
}
