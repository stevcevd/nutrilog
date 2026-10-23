plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.nutrilog"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.nutrilog"
        minSdk = 26
        targetSdk = 34
        // Always increasing, so a new build can replace the installed one.
        versionCode = (System.currentTimeMillis() / 60000L).toInt()
        versionName = "5.1"
    }

    // Same key on every build = Android treats a new APK as an UPDATE of the installed app.
    signingConfigs {
        create("nutrilog") {
            storeFile = rootProject.file("nutrilog.keystore")
            storePassword = "nutrilog"
            keyAlias = "nutrilog"
            keyPassword = "nutrilog"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("nutrilog") }
        release {
            signingConfig = signingConfigs.getByName("nutrilog")
            isMinifyEnabled = false
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.fragment:fragment:1.8.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
}
