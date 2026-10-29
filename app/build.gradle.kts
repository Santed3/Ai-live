plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.screenbubble"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.screenbubble"
        minSdk = 30
        targetSdk = 34
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = 100 + build
        versionName = "1.0.$build"
    }

    signingConfigs {
        create("release") {
            storeFile = file("screenbubble.keystore")
            storePassword = "screenbubble"
            keyAlias = "screenbubble"
            keyPassword = "screenbubble"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
