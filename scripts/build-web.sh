#!/usr/bin/env bash
# Build EffectCraft's web (wasm) site from the pinned upstream release — with the
# EffectDroid mobile patches (patches/*.patch) — and stage it into
# android/webapp/src/main/assets/web for the WebView APK.
#
#   scripts/build-web.sh
#
# Requirements:
#   * rustup target add wasm32-unknown-unknown
#   * wasm-bindgen-cli EXACTLY at the upstream lockfile's version (0.2.129 for
#     v0.17.0 — see upstream docs/web.md): cargo install wasm-bindgen-cli --version <v> --locked
#   * optional: wasm-opt (binaryen) — `cargo xtask web` uses it when found
# CI: .github/workflows/android-web.yml. Without a Rust toolchain,
# scripts/fetch-web-assets.sh still fetches the stock release zip (no mobile patches).
set -euo pipefail

REF="${EFFECTCRAFT_REF:-v0.17.0}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${EFFECTCRAFT_WEB_DIR:-$ROOT/.upstream/effectcraft-web}"
ASSETS="$ROOT/android/webapp/src/main/assets/web"

# --- pinned upstream checkout (cache-tolerant, same pattern as build-native.sh) ---
# rust-cache restores .upstream/.../target/ without .git, so a bare directory can exist
# without a checkout: re-clone around the cached target dir instead of failing.
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

"$ROOT/scripts/apply-upstream-patches.sh" "$WORK"

# --- toolchain gates --------------------------------------------------------------
rustup target add wasm32-unknown-unknown >/dev/null 2>&1 ||
  rustup target add wasm32-unknown-unknown
want="$(grep -A2 '^name = "wasm-bindgen"$' "$WORK/Cargo.lock" | grep -m1 '^version = ' | sed 's/version = "//; s/"//')"
# `wasm-bindgen --version` prints "wasm-bindgen <version>"; keep the last field.
have="$( { wasm-bindgen --version || true; } | awk 'NF { print $NF }' )"
have="${have:-missing}"
if [ "$have" != "$want" ]; then
  echo "error: wasm-bindgen-cli is '$have' but the lockfile needs exactly '$want'" >&2
  echo "       cargo install wasm-bindgen-cli --version $want --locked" >&2
  exit 1
fi
if command -v wasm-opt >/dev/null 2>&1; then
  echo "-> wasm-opt: $(wasm-opt --version 2>/dev/null | head -1)"
else
  echo "-> wasm-opt not found (cargo xtask web skips the size optimisation)"
fi

# --- build the site ---------------------------------------------------------------
(cd "$WORK" && cargo xtask web)

DIST="$WORK/target/web/dist"
[ -f "$DIST/index.html" ] || { echo "error: $DIST/index.html not produced" >&2; exit 1; }

# --- stage into the webapp assets (keep .gitkeep; assets are CI-filled) ------------
mkdir -p "$ASSETS"
find "$ASSETS" -mindepth 1 ! -name '.gitkeep' -delete
cp -R "$DIST"/. "$ASSETS"/
test -f "$ASSETS/index.html"
echo "-> staged $(du -sh "$ASSETS" | cut -f1) into android/webapp/src/main/assets/web"
