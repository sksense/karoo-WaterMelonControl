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
val releaseSigningPropertyNames = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigningConfig = releaseSigningPropertiesFile.exists() &&
    releaseSigningPropertyNames.all { !releaseSigningProperties.getProperty(it).isNullOrBlank() } &&
    rootProject.file(releaseSigningProperties.getProperty("storeFile", "missing")).exists()
val expectedReleaseCertificateSha256 =
    "66fab07e67d1965f5131469715c4f1b067fc3ee281197ed0e0ac170d68536c30"
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
        versionCode = 38
        versionName = "1.4.1"
        manifestPlaceholders["karooManifestUrl"] = karooManifestUrl.get()
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigningConfig) {
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

val validateProductionSigning = tasks.register("validateProductionSigning") {
    description = "Fails production release builds when signing configuration is missing"
    group = "verification"

    doLast {
        check(hasReleaseSigningConfig) {
            "Production signing configuration is missing or incomplete. Expected keystore/watermeloncontrol-release.properties and its configured keystore."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateProductionSigning)
}

val verifyProductionCertificate = tasks.register("verifyProductionCertificate") {
    description = "Verifies the release APK uses the expected production certificate"
    group = "verification"
    dependsOn("assembleRelease")

    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        check(apk.exists()) { "Release APK not found: $apk" }

        val buildToolsDir = android.sdkDirectory.resolve("build-tools")
            .listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name.substringBefore('.').toIntOrNull() ?: 0 }
            ?: error("Android build-tools not found")
        val apksigner = buildToolsDir.resolve("apksigner")
        check(apksigner.canExecute()) { "apksigner not found: $apksigner" }

        val output = ProcessBuilder(apksigner.absolutePath, "verify", "--print-certs", apk.absolutePath)
            .redirectErrorStream(true)
            .start()
            .run {
                val text = inputStream.bufferedReader().readText()
                check(waitFor() == 0) { "apksigner verification failed:\n$text" }
                text
            }
        check(output.lowercase().contains(expectedReleaseCertificateSha256)) {
            "Release certificate mismatch. Expected SHA-256 $expectedReleaseCertificateSha256"
        }
    }
}

tasks.register("verifyProductionRelease") {
    description = "Builds and verifies the production-signed release APK"
    group = "verification"
    dependsOn(verifyProductionCertificate)
}

tasks.register("generateManifest") {
    description = "Generates manifest.json with current Karoo release metadata"
    group = "build"

    doLast {
        val baseUrl = System.getenv("BASE_URL")
            ?: "https://github.com/sksense/karoo-WaterMelonControl/releases/download/v${android.defaultConfig.versionName}"
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

    testImplementation(libs.junit)
}
