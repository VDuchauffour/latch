// Latest release tag (v1.2.3 -> 1.2.3), so local builds match the last release; 0.1.0 when there is no tag or no git.
val latestTagVersion: String =
    runCatching {
        providers
            .exec {
                commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
            .removePrefix("v")
    }.getOrNull()
        ?.takeIf { it.isNotEmpty() } ?: "0.1.0"

// CI passes the tag being released (`-PversionName=1.2.3`); local builds use the latest tag.
val appVersionName: String = providers.gradleProperty("versionName").getOrElse(latestTagVersion)

// Monotonic versionCode derived from the semver: 1.2.3 -> 10203 (minor/patch must stay below 100).
val appVersionCode: Int =
    appVersionName
        .substringBefore('-')
        .split('.')
        .map { it.toInt() }
        .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// Release signing key, provided by CI from repository secrets. Without it, release falls back to the debug key.
val releaseKeystore: String? = System.getenv("LATCH_KEYSTORE_FILE")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.latch"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.latch"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("LATCH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LATCH_KEY_ALIAS")
                keyPassword = System.getenv("LATCH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the release key in CI; locally the debug key, so `assembleRelease` still produces an
            // installable APK out of the box.
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
