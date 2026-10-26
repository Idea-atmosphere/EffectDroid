#!/usr/bin/env bash
# Build EffectCraft for Android arm64 — EffectDroid's "Linux arm64 → Android" port.
#
#   scripts/build-native.sh cli    # effectcraft-cli (headless render / MCP) for Android
#   scripts/build-native.sh gui    # libeffectcraft_android.so (eframe GUI) → :nativeapp jniLibs
#   scripts/build-native.sh apk    # assemble the :nativeapp release APK (needs the gui step first)
#   scripts/build-native.sh all    # cli + gui + apk
#
# Requirements: rustup (stable + aarch64-linux-android), Android SDK with NDK
# (default 27.2.12479018, override ANDROID_NDK_VERSION), cargo-ndk (auto-installed),
# JDK 17 for the apk step. EFFECTCRAFT_REF pins the upstream release (default v0.17.0).
# CI: .github/workflows/android-native.yml.
set -euo pipefail

WHAT="${1:-all}"
REF="${EFFECTCRAFT_REF:-v0.17.0}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${EFFECTCRAFT_DIR:-$ROOT/.upstream/effectcraft}"
NDK_VERSION="${ANDROID_NDK_VERSION:-27.2.12479018}"
JNILIBS="$ROOT/android/nativeapp/src/main/jniLibs/arm64-v8a"

# --- pinned upstream checkout -------------------------------------------------
# CI restores `.upstream/effectcraft/target/` from rust-cache without the checkout
# itself, so the directory can exist while `.git` does not — re-clone around the
# cached target dir instead of failing with "already exists and is not empty".
if [ -d "$WORK/.git" ]; then
  git -C "$WORK" fetch --depth 1 origin "refs/tags/$REF" 2>/dev/null ||
    git -C "$WORK" fetch --depth 1 origin "$REF"
  git -C "$WORK" checkout -f FETCH_HEAD
else
  KEEP=""
  if [ -d "$WORK" ]; then
    if [ -d "$WORK/target" ]; then
      KEEP="$(mktemp -d)"
      mv "$WORK/target" "$KEEP/target"
    fi
    rm -rf "$WORK"
  fi
  mkdir -p "$(dirname "$WORK")"
  git clone --depth 1 --branch "$REF" https://github.com/storytold/effectcraft.git "$WORK"
  if [ -n "$KEEP" ]; then
    mv "$KEEP/target" "$WORK/target"
    rmdir "$KEEP"
  fi
fi
echo "-> upstream $REF @ $(git -C "$WORK" rev-parse --short HEAD)"

# --- drop the Android entry crate into the upstream workspace (apps/*) ---------
rm -rf "$WORK/apps/effectcraft-android"
cp -R "$ROOT/native/effectcraft-android" "$WORK/apps/effectcraft-android"

# --- winit android backend feature for the CLI graph ---------------------------
# effectcraft-cli → ui-egui → eframe → winit → android-activity. winit's android-activity
# dependency is non-optional, but android-activity needs exactly one activity backend
# feature or it fails with E0583 (`mod activity_impl` has no file without one). The GUI
# crate selects `game-activity` (GameActivity); the headless CLI gets the lighter
# `native-activity` glue. Separate cargo invocations → no feature clash between the two.
python3 - "$WORK/apps/effectcraft-cli/Cargo.toml" <<'PYEOF'
import sys
p = sys.argv[1]
s = open(p).read()
if "android-native-activity" not in s:
    s = s.rstrip("\n") + (
        "\n\n# EffectDroid Android port: select winit's android backend for this graph.\n"
        "[target.'cfg(target_os = \"android\")'.dependencies]\n"
        "winit = { version = \"0.30\", default-features = false,"
        " features = [\"android-native-activity\"] }\n"
    )
    open(p, "w").write(s)
    print("-> patched effectcraft-cli manifest: winit android-native-activity")
else:
    print("-> effectcraft-cli manifest already selects an android backend")
PYEOF

# --- toolchain ----------------------------------------------------------------
rustup target add aarch64-linux-android >/dev/null 2>&1 || rustup target add aarch64-linux-android
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  for candidate in "${ANDROID_NDK_ROOT:-}" "${ANDROID_HOME:-}/ndk/$NDK_VERSION"; do
    if [ -n "$candidate" ] && [ -d "$candidate" ]; then ANDROID_NDK_HOME="$candidate"; break; fi
  done
fi
[ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "${ANDROID_NDK_HOME:-}" ] || {
  echo "error: Android NDK not found — install ndk;$NDK_VERSION or set ANDROID_NDK_HOME" >&2
  exit 1
}
export ANDROID_NDK_HOME
command -v cargo-ndk >/dev/null 2>&1 || cargo install cargo-ndk

strip_bin() { # best-effort NDK llvm-strip (keeps artifacts/APKs small)
  local f="$1"
  local strip
  strip="$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" -name llvm-strip 2>/dev/null | head -1)"
  [ -n "$strip" ] && "$strip" --strip-unneeded "$f" 2>/dev/null || true
}

OUT_DIR="$ROOT/artifacts"
mkdir -p "$OUT_DIR"
VERSION="${REF#v}"

build_cli() {
  echo "-> building effectcraft-cli (aarch64-linux-android, release)"
  (cd "$WORK" && cargo ndk -t arm64-v8a build --release -p effectcraft-cli)
  local bin="$WORK/target/aarch64-linux-android/release/effectcraft-cli"
  [ -f "$bin" ] || { echo "error: $bin not produced" >&2; exit 1; }
  strip_bin "$bin"
  local pkg="$OUT_DIR/effectcraft-cli-$VERSION-android-arm64"
  rm -rf "$pkg" && mkdir -p "$pkg"
  cp "$bin" "$pkg/"
  cat > "$pkg/README.txt" <<EOF
EffectCraft headless CLI for Android arm64 (bionic), built from storytold/effectcraft $REF.

Run on a device (adb shell or Termux):
  adb push effectcraft-cli /data/local/tmp/
  adb shell chmod 755 /data/local/tmp/effectcraft-cli
  adb shell /data/local/tmp/effectcraft-cli --version

The binary links only against the system Bionic libraries (libc, libm, libdl, libvulkan
for GPU renders) — no root required. See docs/ANDROID.md.
EOF
  tar -C "$OUT_DIR" -czf "$pkg.tar.gz" "$(basename "$pkg")"
  rm -rf "$pkg"
  echo "-> $pkg.tar.gz"
}

build_gui() {
  echo "-> building libeffectcraft_android.so (eframe GUI, aarch64-linux-android, release)"
  mkdir -p "$JNILIBS"
  # cargo-ndk copies into an arch subdirectory of -o; stage then place the lib where
  # Gradle's jniLibs layout (jniLibs/<abi>/*.so) expects it.
  local stage so
  stage="$(mktemp -d)"
  (cd "$WORK" && cargo ndk -t arm64-v8a -o "$stage" build --release -p effectcraft-android)
  so="$(find "$stage" -name 'libeffectcraft_android.so' -print -quit)"
  [ -n "$so" ] || { echo "error: libeffectcraft_android.so not produced" >&2; exit 1; }
  cp "$so" "$JNILIBS/libeffectcraft_android.so"
  rm -rf "$stage"
  strip_bin "$JNILIBS/libeffectcraft_android.so"
  local out="$JNILIBS/libeffectcraft_android.so"
  echo "-> $out ($(du -h "$out" | cut -f1))"
}

build_apk() {
  [ -f "$JNILIBS/libeffectcraft_android.so" ] || {
    echo "error: run 'scripts/build-native.sh gui' first (missing libeffectcraft_android.so)" >&2
    exit 1
  }
  echo "-> assembling :nativeapp release APK"
  (cd "$ROOT/android" && VERSION_NAME="$VERSION" ./gradlew --no-daemon :nativeapp:assembleRelease)
  local apk
  apk="$(find "$ROOT/android/nativeapp/build/outputs/apk/release" -name '*.apk' | head -1)"
  [ -n "$apk" ] || { echo "error: no APK produced" >&2; exit 1; }
  cp "$apk" "$OUT_DIR/effectdroid-native-$VERSION.apk"
  echo "-> $OUT_DIR/effectdroid-native-$VERSION.apk"
}

case "$WHAT" in
  cli) build_cli ;;
  gui) build_gui ;;
  apk) build_apk ;;
  all) build_cli; build_gui; build_apk ;;
  *) echo "usage: $0 [cli|gui|apk|all]" >&2; exit 2 ;;
esac
echo "done."
