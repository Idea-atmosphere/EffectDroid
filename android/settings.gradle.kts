pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EffectDroid"

// :webapp    — EffectCraft's official web build (wasm) wrapped in a WebView (the web → Android port).
// :nativeapp — the native aarch64-linux-android build of EffectCraft (the Linux arm64 → Android port),
//              packaged as a GameActivity cdylib (jniLibs/arm64-v8a/libeffectcraft_android.so).
include(":webapp", ":nativeapp")
