plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.io.File
import java.io.FileInputStream
import java.util.Properties

// Release signing credentials live in local.properties (gitignored) and the
// keystore at ~/.config/knucklegame/release.keystore (outside the repo).
// Without them the release APK builds unsigned.
val kgProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use(::load)
}

android {
    namespace = "com.example.knucklegame"
    compileSdk {
        version = release(37)
    }

    // Release signing credentials live in local.properties (gitignored) and the
    // keystore at ~/.config/knucklegame/release.keystore (outside the repo).
    // Without them the release APK builds unsigned.
    signingConfigs {
        create("release") {
            storeFile = File(System.getProperty("user.home"), ".config/knucklegame/release.keystore")
            storePassword = kgProps.getProperty("kg.storePassword")
            keyAlias = "knucklegame"
            keyPassword = kgProps.getProperty("kg.keyPassword")
        }
    }

    defaultConfig {
        applicationId = "com.example.knucklegame"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (kgProps.getProperty("kg.storePassword") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.sceneview)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
