#!/usr/bin/env bash
# Fetch EffectCraft's official web build (effectcraft-web-<version>.zip from the upstream
# GitHub release) into android/webapp/src/main/assets/web/. Stock zip — WITHOUT EffectDroid's
# mobile patches; scripts/build-web.sh builds from source with patches/ applied (CI default).
#
# Usage: scripts/fetch-web-assets.sh [release-tag]     (default: $EFFECTCRAFT_REF or v0.17.0)
# Needs: unzip, curl or gh (gh preferred — pattern-matches the asset name).
set -euo pipefail

REF="${1:-${EFFECTCRAFT_REF:-v0.17.0}}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/android/webapp/src/main/assets/web"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "-> EffectCraft release: $REF"
if command -v gh >/dev/null 2>&1; then
  gh release download "$REF" -R storytold/effectcraft -p 'effectcraft-web-*.zip' -D "$TMP"
else
  VER="${REF#v}"
  curl -fL --retry 3 -o "$TMP/effectcraft-web-$VER.zip" \
    "https://github.com/storytold/effectcraft/releases/download/$REF/effectcraft-web-$VER.zip"
fi

ZIP="$(find "$TMP" -maxdepth 1 -name 'effectcraft-web-*.zip' | head -1)"
[ -n "$ZIP" ] || { echo "error: no effectcraft-web-*.zip downloaded for $REF" >&2; exit 1; }
unzip -q "$ZIP" -d "$TMP/out"

# The zip holds the static site at its root or in effectcraft-web-<version>/ (packaging/web/README.md).
if [ -f "$TMP/out/index.html" ]; then
  SRC="$TMP/out"
else
  INDEX="$(find "$TMP/out" -name index.html | head -1)"
  [ -n "$INDEX" ] || { echo "error: no index.html inside $(basename "$ZIP")" >&2; exit 1; }
  SRC="$(dirname "$INDEX")"
fi

rm -rf "$DEST"
mkdir -p "$DEST"
cp -R "$SRC"/. "$DEST"/
test -f "$DEST/index.html"
echo "-> web assets ready: ${DEST#"$ROOT"/} ($(du -sh "$DEST" | cut -f1))"
