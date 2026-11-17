plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.viyt.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.viyt.app"
        minSdk = 29
        targetSdk = 35
        versionCode = (System.getenv("VIYT_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.${System.getenv("VIYT_VERSION_CODE") ?: "0"}"
    }

    // viyt's own signing key, so every new build installs as an update.
    signingConfigs {
        create("viyt") {
            storeFile = rootProject.file("signing/viyt.jks")
            // Decoded from the VIYT_KEYSTORE_B64 GitHub secret by the build workflow; never committed.
            storePassword = System.getenv("VIYT_STORE_PASSWORD")
            keyAlias = "viyt"
            keyPassword = System.getenv("VIYT_STORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("viyt")
        }
        debug {
            signingConfig = signingConfigs.getByName("viyt")
        }
    }

    // One APK per CPU type keeps the download small (yt-dlp ships a Python runtime).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            // yt-dlp, ffmpeg and qjs run as real executables, so they must be extracted to disk.
            useLegacyPackaging = true
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
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // yt-dlp + ffmpeg for Android
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    // Compose UI
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    // Player
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // Thumbnails
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
