#!/usr/bin/env bash
#
# test-store-notes.sh — JVM-free tests for the store-notes validator.
#
# The validator exists because markdown reached the LIVE Play listing, so the thing worth
# testing is that each markup form it was written to catch actually FAILS it. Every case
# below is a class of defect, not one file: a bad file is generated on the fly and fed to
# the same validate_store_notes() that CI and publish-to-play.sh call.
#
# Run: scripts/test-store-notes.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/check-store-notes.sh
. "$ROOT/scripts/check-store-notes.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

pass=0
fail=0

# expect <accept|reject> <name> <body...>
expect() {
  local want="$1" name="$2"; shift 2
  local file="$TMP/case.txt" rc=0
  printf '%s\n' "$@" > "$file"
  validate_store_notes "$file" >/dev/null 2>&1 || rc=1
  if { [ "$want" = accept ] && [ "$rc" -eq 0 ]; } || { [ "$want" = reject ] && [ "$rc" -ne 0 ]; }; then
    echo "  ok   — $name"
    pass=$((pass + 1))
  else
    echo "  FAIL — $name (wanted $want, validator rc=$rc)" >&2
    fail=$((fail + 1))
  fi
}

echo "validate_store_notes:"
expect accept "a plain bullet list"                 "• Blocking no longer switches itself off after an update." "• One arrival is one entry in your history."
expect accept "hyphen bullets"                      "- Fixed a delay that asked twice."
expect accept "parentheses and quotes are fine"     '• Open Instagram to Following (experimental, off by default), not "For You".'

expect reject "a markdown link"                     "• Fixed the hold bug ([#64](https://github.com/astraedus/nudge/issues/64))."
expect reject "a bare issue number"                 "• Fixed the hold bug (#64)."
expect reject "a raw URL"                           "• Details at https://github.com/astraedus/nudge/releases"
expect reject "markdown emphasis"                   "• **Blocking** could switch itself off."
expect reject "an asterisk bullet"                  "* Blocking could switch itself off."
# shellcheck disable=SC2016  # the backticks are the literal defect under test, not a subshell
expect reject "a code span"                         '• Fixed the `SittingTracker` clock.'
expect reject "a markdown heading"                  "# 1.18.2" "• Fixed blocking."
expect reject "our own What's new header"           "What's new in v1.18.2:" "• Fixed blocking."
expect reject "prose that is not a bullet"          "Fixed blocking."
expect reject "an empty file"                       ""
expect reject "over the 500-byte Play cap"          "• $(head -c 520 < /dev/zero | tr '\0' 'x')"

# A missing file is the defect that shipped the essay: the publish path must SEE it.
rc=0; validate_store_notes "$TMP/nope.txt" >/dev/null 2>&1 || rc=1
if [ "$rc" -ne 0 ]; then echo "  ok   — a missing file"; pass=$((pass + 1));
else echo "  FAIL — a missing file was accepted" >&2; fail=$((fail + 1)); fi

# Every notes file the repo ships under the managed convention must itself be valid —
# discovered from disk, so a new release's file is covered without touching this test.
echo "shipped notes files:"
rc=0; "$ROOT/scripts/check-store-notes.sh" --all >/dev/null || rc=1
if [ "$rc" -eq 0 ]; then echo "  ok   — check-store-notes.sh --all"; pass=$((pass + 1));
else echo "  FAIL — check-store-notes.sh --all" >&2; fail=$((fail + 1)); fi

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
