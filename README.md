# EffectDroid

**[EffectCraft](https://github.com/storytold/effectcraft) on Android** — ports of the official
**web build** and the **Linux arm64 build** of EffectCraft (open-source motion graphics &
compositing, pure Rust) to Android.

| Port | Artifact | Status |
|---|---|---|
| Web → Android | `effectdroid-web-<v>.apk` — WebView shell around upstream's wasm build (full GUI, offline) | ✅ primary |
| Linux arm64 → Android arm64 | `effectcraft-cli` for `aarch64-linux-android` + `effectdroid-native-<v>.apk` (eframe GUI on GameActivity) | ✅ CLI / 🧪 GUI |

> Not affiliated with EffectCraft or ArtCraft. Everything runs locally on the device; projects
> and renders never leave it.

## Get an APK

1. **Actions → [Android (web port)](.github/workflows/android-web.yml) → Run workflow** —
   download `effectdroid-web-*.apk` from the run artifacts and `adb install` it.
2. Tagged builds (`v*`) attach the APK to this repository's releases.

Device: Android 8.0+, evergreen WebView (WebGPU or WebGL2), ~250 MB free.

## Build locally

```sh
# Web port (JDK 17 + Android SDK; wasm tools for scripts/build-web.sh)
scripts/build-web.sh                 # source + mobile patches — or: fetch-web-assets.sh (stock zip)
cd android && ./gradlew :webapp:assembleRelease

# Native arm64 port (adds: rustup target aarch64-linux-android, NDK 27.2.12479018)
scripts/build-native.sh all
```

## Repository layout

```
android/webapp      WebView port of EffectCraft's web build
android/nativeapp   APK hosting the native aarch64-linux-android build
native/             drop-in Android entry crate for the pinned upstream workspace
scripts/            build-web.sh (wasm + patches → assets), fetch-web-assets.sh (stock zip),
                    apply-upstream-patches.sh, build-native.sh (cargo-ndk)
docs/ANDROID.md     full porting notes, device requirements, limitations, upgrade flow
```

## Documentation

- **[docs/ANDROID.md](docs/ANDROID.md)** — architecture, both ports, CI, signing, limitations,
  how to bump the pinned EffectCraft version.
- Upstream docs: [storytold/effectcraft](https://github.com/storytold/effectcraft)
  (`docs/web.md`, `packaging/web/README.md`).

## License

EffectDroid's own code: [MIT](LICENSE). EffectCraft: MIT OR Apache-2.0 by its authors — see
[NOTICE](NOTICE) and upstream.
