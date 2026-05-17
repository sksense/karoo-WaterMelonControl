plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

import java.util.Properties

val releaseSigningPropertiesFile = rootProject.file("keystore/watermeloncontrol-release.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.exists()) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}
val defaultReleaseBaseUrl =
    "https://github.com/sksense/karoo-WaterMelonControl/releases/latest/download"
val karooManifestUrl =
    providers.gradleProperty("karooManifestUrl").orElse("$defaultReleaseBaseUrl/manifest.json")

android {
    namespace = "com.watermeloncontrol.widget"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.watermeloncontrol.widget"
        minSdk = 26
        targetSdk = 34
        versionCode = 29
        versionName = "1.3.2"
        manifestPlaceholders["karooManifestUrl"] = karooManifestUrl.get()
    }

    signingConfigs {
        if (releaseSigningPropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties["storeFile"] as String)
                storePassword = releaseSigningProperties["storePassword"] as String
                keyAlias = releaseSigningProperties["keyAlias"] as String
                keyPassword = releaseSigningProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningPropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.register("generateManifest") {
    description = "Generates manifest.json with current Karoo release metadata"
    group = "build"

    doLast {
        val baseUrl = System.getenv("BASE_URL") ?: defaultReleaseBaseUrl
        val manifestFile = file("$projectDir/manifest.json")
        val manifest = mapOf(
            "label" to "WaterMelonControl",
            "packageName" to "com.watermeloncontrol.widget",
            "iconUrl" to "$baseUrl/WaterMelonControl-icon.webp",
            "latestApkUrl" to "$baseUrl/WaterMelonControl.apk",
            "latestVersion" to android.defaultConfig.versionName,
            "latestVersionCode" to android.defaultConfig.versionCode,
            "developer" to "sksense",
            "description" to "WaterMelonControl adds Karoo data page widgets for sideloaded media apps, including now playing, playback controls, tap-to-open, and device music volume control.",
            "releaseNotes" to (System.getenv("RELEASE_NOTES") ?: ""),
            "tags" to listOf("entertainment")
        )

        manifestFile.writeText(groovy.json.JsonBuilder(manifest).toPrettyString())
        println("Generated manifest.json with version ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})")
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material)
}
