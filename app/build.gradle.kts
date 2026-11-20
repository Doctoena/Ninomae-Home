plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.ninomae.home"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ninomae.home"
        minSdk = 31 // wallpaper colours (Material You)
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.0"
    }

    // The release key lives outside the repo: its path and passwords come from ~/.gradle/gradle.properties
    // (NINOMAE_STORE_FILE, NINOMAE_STORE_PASSWORD, NINOMAE_KEY_ALIAS, NINOMAE_KEY_PASSWORD).
    // Without them, a release build is signed with the debug key so anyone can still build and install it.
    val releaseKey = providers.gradleProperty("NINOMAE_STORE_FILE").orNull?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = releaseKey
            storePassword = providers.gradleProperty("NINOMAE_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("NINOMAE_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("NINOMAE_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
