plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.streamhub.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.streamhub.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // youtubedl-android ships native Python/ffmpeg for 64-bit ARM only.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keystoreFile = file("streamhub.keystore")
    signingConfigs {
        if (keystorePassword != null && keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "streamhub"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }

    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.2")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
