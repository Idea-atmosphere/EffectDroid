# EffectDroid — porting EffectCraft to Android

EffectCraft ([storytold/effectcraft](https://github.com/storytold/effectcraft)) ships a **web
build** (wasm, `effectcraft-web`) and **Linux arm64 builds** (eframe/egui native app +
`effectcraft-cli`). EffectDroid ports both to Android:

| Upstream artifact | Android port | Where | Status |
|---|---|---|---|
| `effectcraft-web-<v>.zip` (static wasm site) | **WebView APK** — the full GUI (same engine, same egui UI) running in a native Android shell | `android/webapp` (`EffectDroid`) | ✅ primary path |
| `effectcraft-*-linux-aarch64.*` (eframe GUI) | **Native arm64 APK** — `aarch64-linux-android` build of the same UI on a GameActivity window | `android/nativeapp` (`EffectDroid Native`) + `native/effectcraft-android` | 🧪 experimental |
| `effectcraft-cli` | **Android arm64 binary** (Bionic; runs in `adb shell` or Termux) | produced by `scripts/build-native.sh cli` | ✅ headless |

Everything runs **on-device and offline**: projects, media, settings and renders never leave
the phone/tablet (upstream's storage model — OPFS/IndexedDB in the WebView port, the app's
internal data dir in the native port).

## Architecture

```
EffectDroid/
├── android/                        # Gradle project (AGP 8.7, Gradle 8.9, JDK 17)
│   ├── webapp/                     # ← web → Android port
│   │   └── src/main/
│   │       ├── kotlin/…/MainActivity.kt        # immersive WebView, file chooser, link routing
│   │       ├── kotlin/…/WebAssetsPathHandler.kt # assets → https://appassets.androidassets.net
│   │       ├── kotlin/…/DownloadBridge.kt       # blob: saves → Download/EffectCraft (streamed)
│   │       └── assets/web/           # filled by scripts/build-web.sh (CI; not committed)
│   └── nativeapp/                  # ← linux arm64 → Android port (GameActivity + jniLibs)
├── native/effectcraft-android/     # drop-in eframe entry crate, copied into the upstream
│                                   # workspace as apps/effectcraft-android/ by the build script
├── patches/                        # EffectDroid's upstream diffs (mobile UI), applied
│                                   # to every pinned checkout by the build scripts
├── scripts/
│   ├── build-web.sh                # pinned upstream + wasm toolchain → webapp assets
│   ├── fetch-web-assets.sh         # stock release zip alternative (no mobile patches)
│   ├── apply-upstream-patches.sh   # patches/*.patch → the pinned checkout (idempotent)
│   └── build-native.sh             # clone upstream (pinned) + patches + cargo-ndk (cli | gui | apk)
└── .github/workflows/
    ├── android-web.yml             # → effectdroid-web-<v>.apk (also attached to tag releases)
    └── android-native.yml          # → effectcraft-cli-android-arm64.tar.gz + GUI .so + native APK
```

Both ports are **built from the pinned upstream release** (`EFFECTCRAFT_REF`, default
`v0.17.0`) at build time — no fork of the EffectCraft tree lives in this repository. The only
upstream-derived code is the small Android entry crate (`native/effectcraft-android`), written
against upstream's public APIs (`EffectcraftApp`, `effectcraft_host`, `effectcraft_engine`),
plus the small documented diffs in `patches/` (mobile responsiveness — see below), which every
build script applies to the fresh checkout.

### Why a WebView for the web port?

Upstream's web build *is* the full desktop app: the same Rust engine, effects, codecs and egui
UI compiled to wasm. Wrapping it in a WebView gives Android:

- **secure context** (`https://appassets.androidassets.net` via `WebViewAssetLoader`) →
  WebGPU (falling back to WebGL2), Web Workers, OPFS, Web Audio — no cleartext, no server;
- **correct MIME types** (custom path handler serves `.wasm` as `application/wasm`, required
  for streaming compilation);
- **native file picking** (`WebChromeClient.onShowFileChooser` → SAF, multi-select) for
  File ▸ Open / Import;
- **native saving** (injected hook intercepts upstream's `<a download href=blob:…>` clicks and
  streams the bytes into `Download/EffectCraft/` through `MediaStore` — bounded memory);
- links (Help, Discord, getartcraft.com) leave to the system browser; the shell itself never
  navigates away.

Upstream needs **no COOP/COEP headers** (no `SharedArrayBuffer` — see
`packaging/web/README.md` upstream), so the asset-loader approach is complete.

## 1. The WebView APK (web port)

### Build it (CI — recommended)

Push to `main`, open a PR, or run **Actions → Android (web port) → Run workflow**
(`inputs.ref` picks the EffectCraft release, default `v0.17.0`). The run compiles the wasm
bundle from source (`scripts/build-web.sh`: `cargo xtask web` with `wasm-bindgen` 0.2.129 +
`wasm-opt`, applying `patches/` first) and its artifacts contain
`effectdroid-web-<v>.apk` — release build, signed with the debug key so it installs directly:

```sh
adb install effectdroid-web-0.17.0.apk
```

Pushing a tag `v*` additionally attaches the APK to a GitHub release of this repository.

### Build it (local)

Requirements: JDK 17, Android SDK (compileSdk 35). The Gradle wrapper downloads Gradle 8.9 on
first run. Assets, pick one:

```sh
scripts/build-web.sh                    # wasm from pinned source + mobile patches (needs
                                        # wasm32 target, wasm-bindgen-cli = lockfile version,
                                        # optionally wasm-opt — upstream docs/web.md)
# — or the stock release zip without the mobile patches:
scripts/fetch-web-assets.sh v0.17.0     # ~60 MB release zip (needs gh/curl + unzip)

cd android && ./gradlew :webapp:assembleRelease
# android/webapp/build/outputs/apk/release/webapp-release.apk
```

`assembleRelease` refuses to run without the assets and tells you which script to run.

### Device requirements

- **Android 8.0+** (minSdk 26). Storage, workers, OPFS degrade gracefully on old WebViews
  (upstream falls back IndexedDB → memory).
- An **evergreen WebView** (updated by the Play Store / system updates). WebGPU needs a recent
  Chromium — otherwise the app runs on WebGL2, which every supported device has.
- ~250 MB free for the app + wasm; large projects want a device with 4 GB+ RAM.

### Release signing

CI signs with the **debug key** so the APK is sideloadable. For Play or public distribution,
create a keystore and swap the `signingConfig` in `android/webapp/build.gradle.kts`
(`signingConfigs.create("release")` + `storeFile`/`storePassword`/`keyAlias`/`keyPassword`,
ideally from CI secrets).

### Known limitations (web port)

| Limitation | Notes |
|---|---|
| On-screen keyboard | egui draws a desktop-style UI on a canvas; Android only opens the IME for focusable DOM fields. Text fields work with a hardware keyboard (Bluetooth/USB). Touch keyboards for canvas text fields needs an egui-side hook — tracked upstream. |
| Service worker | Disabled on purpose (`?nosw`): assets are bundled, so it would only duplicate storage. PWA-install semantics from the web don't apply. |
| Orientation | Allowed freely (`configChanges` handled, no reload); the UI re-lays out on resize. |
| Downloads on API ≤ 28 | Written to `Android/data/<pkg>/files/Download/EffectCraft/` (scoped storage, no permission). API 29+: public `Download/EffectCraft/`. |

## Mobile responsiveness (both ports)

Desktop-first UIs are unusable on a ~390 pt phone screen; `patches/0001-mobile-responsive-ui.patch`
fixes that once for both ports (the wasm build and the native build apply the same `patches/`):

- **Phone workspace** — windows narrower than 720 pt adopt a dedicated layout: a full-width
  tab strip (viewer, layer, project, effect controls, presets, properties, preview) over the
  timeline. Tabs shrink widest-first and overflow into the strip's overflow menu (upstream
  `craft-ui::tabs::fit`), so every panel stays reachable at 390 pt; no fixed 300 pt side
  columns eat the screen. It only replaces an untouched `Default` layout — first run, or a
  wide window rotated into portrait; a layout the user rearranged (or any workspace they
  chose) is never clobbered. `Window ▸ Workspace` lists **Phone** like a built-in, next to
  Small Screen / Minimal / … .
- **Touch-sized controls** while narrow: 44×36 pt hit targets, wider item spacing, a 32 pt
  menu strip; wide windows get the theme's own sizes back (follows rotation — the shell
  already handles `orientation|screenSize` via `configChanges`).
- **Header** (the 40 pt tools bar): below 720 pt it grows to 76 pt and packs the twelve tool
  slots into two rows; the items whose menus still carry them are dropped so nothing overlaps
  (Discord/links → Help, tool pickers → Properties, snapping → View, workspace tabs → the
  `»` menu — which lists every workspace, Phone included).
- **Menu titles** scroll horizontally when they don't fit (swipe to reach Window/Help); a
  desktop-width bar is unchanged.
- **Home screen** (the first thing on a phone): quick-start tiles go 2×2 below 480 pt, the
  Recent filter shrinks with the area, "Clear list" hides (it collided with the heading).
- **Readability**: the default UI Scale (100) gets a +15% zoom on narrow windows; any
  explicit Settings ▸ Appearance ▸ UI Scale still wins.
- Still available as before: **Settings ▸ Appearance ▸ UI Scale** (whole-UI zoom), the web
  port's pinch-zoom (the viewport meta does not lock scaling), draggable split handles and
  the tab overflow menu.

Tune the behaviour by editing `patches/` — both build scripts re-apply them to every fresh
checkout and fail loudly when a new `EFFECTCRAFT_REF` no longer matches.

## 2. The native arm64 port (Linux arm64 → Android)

Upstream's Linux arm64 build is the eframe desktop app. Android is a different ABI (Bionic,
not glibc) and a different window system, so "porting" it means **retargeting** the same Rust
code to `aarch64-linux-android`:

- `native/effectcraft-android/` is a thin cdylib entry (`android_main` → `eframe::run_native`)
  that boots the identical `EffectcraftApp` — engine, session, GPU compositor — with
  desktop-only hooks left unset. `scripts/build-native.sh` drops it into the pinned upstream
  clone as `apps/effectcraft-android/` (the workspace globs `apps/*`), so every dependency is
  upstream's own reviewed revision.
- **Why GameActivity:** `effectcraft-ui-egui` enables eframe's `accesskit` feature
  unconditionally, and eframe rejects `native-activity` together with `accesskit`
  (`crates/eframe/src/lib.rs` upstream) → `android-game-activity`, hosted by a small Java
  `GameActivity` subclass (`android/nativeapp`).
- **Config/state** live in the app's internal storage
  (`<internal>/EffectCraftData`, exported as `EFFECTCRAFT_CONFIG_DIR`).

### What works / what doesn't (native — read this)

| Area | Status |
|---|---|
| Engine, effects, rendering (Vulkan via wgpu), timeline, UI | ✅ same code as Linux arm64 |
| Persistence: settings, session, crash recovery | ✅ internal app storage |
| Headless CLI (`effectcraft-cli`), MCP server, on-device renders | ✅ built by CI |
| File dialogs (rfd) | ❌ rfd has no Android backend — hooks unset; use the WebView port for file workflows (SAF) |
| Audio preview (cpal) | ❌ not wired in the Android entry — silent preview |
| On-screen keyboard | ⚠️ same egui canvas caveat as the web port (GameActivity IME plumbing exists, egui doesn't request it) |
| System theme / portals | ❌ desktop-only; use the in-app theme setting |

The native port is therefore **experimental**: it proves the Linux arm64 build runs on Android
arm64, while the WebView port is the daily driver with working files/audio.

### Build it (CI)

Every push/PR touching `native/`, `android/nativeapp/` or the workflow produces:

- `effectcraft-cli-android-arm64.tar.gz` (headless CLI);
- `libeffectcraft_android.so` and `effectdroid-native-<v>.apk` (native GUI — experimental).

`workflow_dispatch` stays available for manual runs (`build_gui: false` skips the GUI/APK
steps); dispatch needs write access to the Actions API.

The first GUI run is a cold Rust build of the whole workspace (tens of minutes; ~15 min with
a warm cargo cache). Treat red GUI builds as upstream API drift — the failure log points at
the entry crate (`native/effectcraft-android/src/lib.rs`), which is the only EffectDroid-owned
Rust code.

### Build it (local)

```sh
# one-time: rustup target add aarch64-linux-android; Android SDK + NDK 27.2.12479018; JDK 17
export ANDROID_HOME=…                    # sdkmanager "ndk;27.2.12479018"
scripts/build-native.sh cli              # → artifacts/effectcraft-cli-0.17.0-android-arm64.tar.gz
scripts/build-native.sh all              # cli + GUI .so + effectdroid-native-0.17.0.apk
```

Using the CLI on a device:

```sh
adb push artifacts/effectcraft-cli-*/effectcraft-cli /data/local/tmp/
adb shell chmod 755 /data/local/tmp/effectcraft-cli
adb shell /data/local/tmp/effectcraft-cli --help     # or run inside Termux
```

The binary links only Bionic + `libvulkan` (system) — no root, no Termux packages.

## Pinning / upgrading EffectCraft

Everything is keyed to one ref:

```sh
export EFFECTCRAFT_REF=v0.18.0   # or pass to scripts/fetch-web-assets.sh / workflow input
```

Suggested upgrade flow: update the default in both workflows + both scripts + the version in
`native/effectcraft-android/Cargo.toml`, re-check `patches/` against the new tag (the build
fails with a clear error if a patch no longer applies), run both workflows, smoke-test on a
device.

## Attribution & license

EffectCraft is © its authors, MIT **or** Apache-2.0 (<https://github.com/storytold/effectcraft>;
see its `NOTICE`, `ATTRIBUTION.md` and license files). EffectDroid fetches and builds it at
build time; no upstream code is vendored except the Android entry crate, which is written
against upstream's public API. EffectDroid's own code is MIT (see `LICENSE`).
