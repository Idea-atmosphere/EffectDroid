# EffectDroid

**English** | [فارسی](README.fa.md)

**EffectDroid** ([`Idea-atmosphere/EffectDroid`](https://github.com/Idea-atmosphere/EffectDroid))
is an open-source Android port of the desktop [EffectCraft](https://github.com/storytold/effectcraft)
motion-graphics and compositing editor (pure Rust). The Rust engine, the effects graph and the egui
interface are the same code as the desktop app; what changes is the shell and the phone layout.
Upstream is **pinned** by `EFFECTCRAFT_REF` (currently **v0.17.0**, tag `362474a`) and patched at
build time — never forked (see [Upstream & patches](#upstream--patches)).

> **Status:** both CI pipelines build on every push — the web port's wasm bundle + APK, and the
> native port's CLI + experimental GUI APKs. The phone layout is iterated with device feedback
> (Home, header, menus, dialogs, touch gestures); anything still rough is listed in
> [docs/ANDROID.md](docs/ANDROID.md) → *Limitations*.

| Port | Artifact | Status |
|---|---|---|
| Web → Android (WebView APK around upstream's wasm build, full GUI, offline) | `effectdroid-web-v0.17.0` + the `webapp` APK | ✅ primary |
| Linux arm64 → Android arm64 (`effectcraft-cli` + eframe GUI APK) | `effectcraft-cli-android-arm64`, `effectdroid-native-apk` | ✅ CLI / 🧪 GUI |

> Not affiliated with EffectCraft or ArtCraft. Everything runs locally on the device; projects
> and renders never leave it.

## Built for a phone

The desktop UI is designed for a ~1400 pt window; at 390–430 pt every one of its surfaces is
adapted, following the same playbook
[PhotoDroid](https://github.com/Idea-atmosphere/PhotoDroid) uses for PhotoCraft:

- **Phone workspace by geometry.** A window whose **shorter side is under 600 pt** — either
  orientation, so a landscape phone stays included — starts on the *Phone* workspace: a
  full-width tab strip over the timeline with every panel reachable through the tabs' overflow
  menu. Window ▸ Workspace lists *Phone* like any built-in; a layout the user rearranged is never
  replaced.
- **Header.** Below 960 pt the tools bar drops the items whose menus still carry them (links →
  Help, tool pickers → Properties, snapping → View, workspace tabs → the `»` menu, which lists
  every workspace) and packs the tool slots into two rows below 720 pt, so nothing overlaps at
  any phone width or orientation.
- **Menu titles scroll.** File … Window … Help sit in a horizontal scroll strip; a swipe reaches
  what doesn't fit at 390 pt. A desktop-width bar lays out exactly as before.
- **Home screen.** Quick-start tiles go 2×2 below 480 pt, the Recent filter shrinks with the
  area, and “Clear list” hides where it collided with the heading.
- **Touch targets and text.** 44×36 pt hit targets and roomier spacing on phones; the default
  UI Scale (100) zooms ×1.15 so text stays readable — an explicit Settings ▸ Appearance scale
  still wins.
- **Gestures, not double-clicks.** A press held down does a double-click's job — apply a preset,
  open a composition, edit a comment, rename, jump to a marker — on the eight actions that used
  to need one; and list rows (effects, presets, media, project) sense clicks alone on touch, so a
  swipe scrolls the list instead of dragging the row. Rows that have a context menu keep it: a
  long press there opens the menu, as on any Android list.
- **Settings fit the screen.** Dialogs already cap to the window (`modal_space`); on a phone the
  Settings pages become a horizontal tab strip above the fields instead of the desktop sidebar,
  with the Previous/Next footer buttons paging either way.
- **System bars.** Both themes opt out of Android 15's enforced edge-to-edge (targetSdk 35), so
  the UI stays clear of the status and navigation bars. A PhotoDroid-style window-insets pipeline
  (safe areas, cutouts, keyboard avoidance fed from the OS) is future work — see
  [docs/ANDROID.md](docs/ANDROID.md).

Tablets and windows ≥ 600 pt on their shorter side keep the desktop layout. There is no on-screen
keyboard/mouse helper (PhotoDroid ships `virtual_input` / `virtual_mouse`); text fields rely on the
system IME.

## Get an APK

1. **Actions → [Android (web port)](.github/workflows/android-web.yml) → latest run →
   Artifacts** — download the bundle and `adb install` the APK from it.
2. ⚠️ Artifact **names are stable across runs** — always take the artifact from the **newest**
   green run; an older download shows an older build.
3. Tagged builds (`v*`) attach the APK to this repository's releases.

Device: Android 8.0+, evergreen WebView (WebGPU or WebGL2), ~250 MB free.

## Build locally

```sh
# Web port (JDK 17 + Android SDK; wasm tools for scripts/build-web.sh)
scripts/build-web.sh                 # source + mobile patches — or: fetch-web-assets.sh (stock zip)
cd android && ./gradlew :webapp:assembleRelease

# Native arm64 port (adds: rustup target aarch64-linux-android, NDK 27.3.13750724)
scripts/build-native.sh all
```

## Repository layout

| Path | What it is |
|---|---|
| `patches/` | `0001-mobile-responsive-ui.patch` — the whole phone adaptation, `-p1` against the pinned tag, applied to a pristine checkout before every build |
| `scripts/` | `build-web.sh`, `fetch-web-assets.sh`, `apply-upstream-patches.sh` (idempotent reverse-check loop), `build-native.sh` (cargo-ndk) |
| `android/webapp` | the WebView shell APK (asset loader over `https://appassets.androidplatform.net`, download bridge) |
| `android/nativeapp` | the APK hosting the native aarch64-linux-android GUI |
| `native/` | a drop-in Android entry crate for the pinned upstream workspace (never a fork) |
| `docs/ANDROID.md` | full porting notes: architecture, CI, signing, device requirements, limitations, upgrade flow |
| `.github/workflows/` | `android-web.yml` + `android-native.yml` — both run on every push and on the whole PR diff |

## Upstream & patches

- **Pinned, not forked.** `EFFECTCRAFT_REF` (default `v0.17.0`) selects the upstream tag; CI and
  local builds `git clone --depth 1 --branch` exactly that ref and never upstream `main`.
- **One patch carries every Android-side UI change:**
  [`patches/0001-mobile-responsive-ui.patch`](patches/0001-mobile-responsive-ui.patch) (the Phone
  workspace, header/menu/Home adaptations, touch sizing, the +1.15× phone zoom, long-press
  stand-ins and phone Settings tabs). `scripts/apply-upstream-patches.sh` applies it to the
  pristine tree and is idempotent — a re-run reverse-checks and skips it.
- **To edit:** clone the tag, run the apply script, edit the tree, then regenerate with
  `git diff > patches/0001-mobile-responsive-ui.patch` from that checkout (step-by-step in
  [docs/ANDROID.md](docs/ANDROID.md)).
- Nothing else is vendored: no copied crates, no synced tree.

## Documentation

- **[docs/ANDROID.md](docs/ANDROID.md)** — architecture of both ports, CI, signing, limitations,
  how to bump the pinned EffectCraft version and regenerate the patch.
- Upstream docs: [storytold/effectcraft](https://github.com/storytold/effectcraft)
  (`docs/web.md`, `packaging/web/README.md`).
- Sibling port: [PhotoDroid](https://github.com/Idea-atmosphere/PhotoDroid) (PhotoCraft on
  Android) — the reference for the phone-layout playbook used here.

## Licence

Like upstream EffectCraft: **MIT OR Apache-2.0**, at your option — see
[LICENSE-MIT](LICENSE-MIT), [LICENSE-APACHE](LICENSE-APACHE) and [NOTICE](NOTICE).
Third-party assets that the bundles carry (fonts, icons, app artwork) keep their own licences,
listed per file in [ATTRIBUTION.md](ATTRIBUTION.md), carried from EffectCraft upstream.
