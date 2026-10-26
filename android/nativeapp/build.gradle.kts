// EffectDroid Native — EffectCraft's native Linux arm64 build retargeted to aarch64-linux-android,
// packaged as a GameActivity cdylib. The .so is produced by scripts/build-native.sh (CI:
// android-native.yml) and lands in src/main/jniLibs/arm64-v8a/.
//
// Without the .so this module still assembles, but the APK crashes on launch — the workflow
// only publishes it after a successful cargo-ndk build (docs/ANDROID.md).

plugins {
    id("com.android.application")
}

/** Same scheme as :webapp — "v0.17.0" → 1700. */
fun versionCodeOf(name: String): Int {
    val parts = name.removePrefix("v").split(".", "-", "_")
    val major = parts.getOrNull(0)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    return (major * 10000 + minor * 100 + patch).coerceAtLeast(1)
}

val appVersion: String = System.getenv("VERSION_NAME")
    ?.takeIf { it.isNotBlank() }
    ?: (findProperty("versionName") as? String)
    ?: "0.17.0-dev"

android {
    namespace = "com.ideaatmosphere.effectdroid.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ideaatmosphere.effectdroid.nativeapp"
        // androidx.games:games-activity + the GameActivity glue; matches the upstream AGDK
        // example (rust-mobile/rust-android-examples/agdk-eframe) which requires 31.
        minSdk = 31
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion.removePrefix("v")
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-key signing keeps the artifact sideloadable (see :webapp for the note on
            // real release signing).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // GameActivity: the Java side of android-activity's `game-activity` backend (required by
    // eframe's `accesskit` feature on Android — see crates/eframe/src/lib.rs upstream).
    implementation("androidx.games:games-activity:4.4.0")
}
