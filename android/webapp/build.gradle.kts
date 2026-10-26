// EffectDroid Web — EffectCraft's official web build (wasm) in a native WebView shell.
//
// The web assets under src/main/assets/web/ are NOT committed; CI (android-web.yml) or
// scripts/fetch-web-assets.sh fills them in from the upstream EffectCraft release
// `effectcraft-web-<version>.zip` before assembling.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** "v0.17.0" / "0.17.0" → 1700 (0.17.0 → major*10000 + minor*100 + patch, at least 1). */
fun versionCodeOf(name: String): Int {
    val parts = name.removePrefix("v").split(".", "-", "_")
    val major = parts.getOrNull(0)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    return (major * 10000 + minor * 100 + patch).coerceAtLeast(1)
}

// VERSION_NAME comes from CI (the EffectCraft release tag); override locally with -PversionName=…
val appVersion: String = System.getenv("VERSION_NAME")
    ?.takeIf { it.isNotBlank() }
    ?: (findProperty("versionName") as? String)
    ?: "0.17.0-dev"

android {
    namespace = "com.ideaatmosphere.effectdroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ideaatmosphere.effectdroid"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion.removePrefix("v")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideloadable out of the box: signed with the debug key so `assembleRelease`
            // produces an installable APK without a keystore. For Play/distribution, replace
            // with a real signingConfig (see docs/ANDROID.md).
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Fail early with a helpful message instead of producing an APK with an empty WebView.
val checkWebAssets = tasks.register("checkWebAssets") {
    val index = layout.projectDirectory.file("src/main/assets/web/index.html")
    doLast {
        if (!index.asFile.exists()) {
            throw GradleException(
                "Missing EffectCraft web assets (src/main/assets/web/index.html).\n" +
                    "Run scripts/build-web.sh (wasm source + mobile patches) or\n" +
                    "scripts/fetch-web-assets.sh (stock release zip) from the repo root,\n" +
                    "or let the 'Android (web port)' GitHub Actions workflow build it."
            )
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(checkWebAssets) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // WebViewAssetLoader: serves the bundled site over https://appassets.androidassets.net
    // so the page runs in a secure context (WebGPU, OPFS, workers) with no cleartext traffic.
    implementation("androidx.webkit:webkit:1.11.0")
}
