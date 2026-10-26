#!/usr/bin/env bash
# Apply EffectDroid's upstream patches (patches/*.patch) to a pinned EffectCraft checkout.
#
#   scripts/apply-upstream-patches.sh <checkout-dir>
#
# Idempotent: a patch that is already in the tree is detected with a reverse check and
# skipped. A patch that applies to neither state fails the build — expected when
# EFFECTCRAFT_REF moves past the revisions the patch was written against; update patches/.
set -euo pipefail

WORK="${1:?usage: apply-upstream-patches.sh <checkout-dir>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REF="${EFFECTCRAFT_REF:-the pinned checkout}"

shopt -s nullglob
applied=0
for p in "$ROOT"/patches/*.patch; do
  if git -C "$WORK" apply --reverse --check -p1 "$p" >/dev/null 2>&1; then
    echo "-> patch already applied: $(basename "$p")"
  elif git -C "$WORK" apply --check -p1 "$p" >/dev/null 2>&1; then
    git -C "$WORK" apply -p1 "$p"
    echo "-> patch applied: $(basename "$p")"
    applied=$((applied + 1))
  else
    echo "error: $(basename "$p") does not apply to the $REF checkout" >&2
    echo "       (update patches/ when EFFECTCRAFT_REF moves past the pinned revision)" >&2
    exit 1
  fi
done
if [ "$applied" -gt 0 ]; then
  echo "-> $applied patch(es) applied to $WORK"
fi
