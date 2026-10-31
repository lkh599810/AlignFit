plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alignfit.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alignfit.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // API endpoint per build flavor (Build Variants in Android Studio):
    //  - prod  (default): deployed HTTPS backend. Cleartext HTTP is disabled.
    //  - local          : backend on a dev PC. Default http://127.0.0.1:8000/ works
    //                     on a USB-connected phone after `adb reverse tcp:8000 tcp:8000`
    //                     (emulator: -Palignfit.localApiUrl=http://10.0.2.2:8000/).
    //                     Separate applicationId, so it never replaces the prod app.
    flavorDimensions += "env"
    productFlavors {
        create("prod") {
            dimension = "env"
            isDefault = true
            buildConfigField("String", "API_BASE_URL", "\"https://alignfit-api.leekanghyun.co.kr/\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
        create("local") {
            dimension = "env"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            val localUrl = (project.findProperty("alignfit.localApiUrl") as String?)
                ?: "http://127.0.0.1:8000/"
            buildConfigField("String", "API_BASE_URL", "\"$localUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
