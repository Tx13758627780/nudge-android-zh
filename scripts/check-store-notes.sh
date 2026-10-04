#!/usr/bin/env bash
#
# check-store-notes.sh — gate the USER-FACING store release notes.
#
# WHY THIS EXISTS:
#   CHANGELOG.md is the DEV-facing record: markdown, issue links, long prose explaining
#   mechanisms. The store field is a PLAIN-TEXT box a stranger reads on a phone. Deriving
#   one from the other by stripping markdown is a losing game — v1.18.1 shipped LIVE on
#   Play reading "...reading client messages ([#54](https://github.com/astraedus/nudge/
#   issues/54)). He had not...". So the store notes are now their OWN artefact, written by
#   hand per release at
#       fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
#   which is the fastlane convention IzzyOnDroid and F-Droid already read, so one file
#   serves Play, IzzyOnDroid and F-Droid.
#
#   This script is both the CI gate (a tagged build fails if the notes for that
#   versionCode are missing or invalid) and the shared validator that
#   scripts/publish-to-play.sh sources, so the rules live in exactly one place.
#
# USAGE:
#   scripts/check-store-notes.sh              # validate the CURRENT app/build.gradle.kts versionCode
#   scripts/check-store-notes.sh 55 56        # validate specific versionCodes
#   scripts/check-store-notes.sh --all        # validate every managed notes file (see below)
#
# AS A LIBRARY:
#   . scripts/check-store-notes.sh            # defines the functions, runs nothing
#
set -euo pipefail

STORE_NOTES_SUBDIR="fastlane/metadata/android/en-US/changelogs"

# Google Play hard-caps release notes at 500 CHARACTERS per locale. We measure BYTES,
# which is >= the character count for any UTF-8 text, so passing this check can never
# overflow Play — and byte length is locale-independent, unlike ${#var} / wc -m, which
# silently change meaning between a C and a UTF-8 locale on a CI runner.
STORE_NOTES_MAX_BYTES=500

# vc 53 (v1.18.0) is the first release whose notes were written as a first-class store
# artefact. 1/4/5/6.txt are the historical F-Droid changelogs for shipped versionCodes
# 1, 4, 5 and 6 — they predate this convention (they use "*" bullets), they are a record
# of what those releases actually said, and we do not rewrite history. --all skips them.
STORE_NOTES_FIRST_MANAGED_VC=53

store_notes_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
}

store_notes_path() {
  echo "$(store_notes_root)/$STORE_NOTES_SUBDIR/$1.txt"
}

# Read a versionCode out of an app/build.gradle.kts arriving on stdin. One awk process,
# no `| head`, because a closed pipe under `set -o pipefail` turns a successful parse
# into a failed command.
store_notes_parse_version_code() {
  awk 'match($0, /versionCode[[:space:]]*=[[:space:]]*[0-9]+/) {
         s = substr($0, RSTART, RLENGTH); gsub(/[^0-9]/, "", s); print s; exit
       }'
}

store_notes_current_version_code() {
  store_notes_parse_version_code < "$(store_notes_root)/app/build.gradle.kts"
}

# validate_store_notes <file> — prints every violation, returns non-zero if any.
# Never edits the file: a store note that breaks a rule is a human decision to redo, not
# something to auto-"repair" into a half sentence.
validate_store_notes() {
  local file="$1"
  local errs=0 body bytes line lineno=0

  if [ ! -f "$file" ]; then
    echo "  MISSING: $file" >&2
    return 1
  fi

  # $(cat) strips trailing newlines, which is exactly the string that gets sent to Play.
  body="$(cat "$file")"

  if [ -z "${body//[[:space:]]/}" ]; then
    echo "  EMPTY: $file" >&2
    return 1
  fi

  bytes="$(printf '%s' "$body" | LC_ALL=C wc -c | tr -d ' ')"
  if [ "$bytes" -gt "$STORE_NOTES_MAX_BYTES" ]; then
    echo "  TOO LONG: $bytes bytes (Play caps a locale at $STORE_NOTES_MAX_BYTES)" >&2
    errs=$((errs + 1))
  fi

  # Markdown and issue references are the exact things that shipped verbatim to the store.
  case "$body" in
    *'['*)   echo "  FORBIDDEN '[': markdown link or issue reference" >&2; errs=$((errs + 1)) ;;
  esac
  case "$body" in
    *']('*)  echo "  FORBIDDEN '](': markdown link" >&2; errs=$((errs + 1)) ;;
  esac
  case "$body" in
    *'#'*)   echo "  FORBIDDEN '#': issue number or markdown heading" >&2; errs=$((errs + 1)) ;;
  esac
  case "$body" in
    *'*'*)   echo "  FORBIDDEN '*': markdown emphasis or bullet (use • or -)" >&2; errs=$((errs + 1)) ;;
  esac
  case "$body" in
    *'`'*)   echo "  FORBIDDEN backtick: markdown code span" >&2; errs=$((errs + 1)) ;;
  esac
  case "$body" in
    *http*)  echo "  FORBIDDEN 'http': a URL in store notes is dead text on a phone" >&2; errs=$((errs + 1)) ;;
  esac

  # Play renders its own "What's new" framing above this text; ours would read twice.
  case "$body" in
    "What's new"*|"Whats new"*|"What’s new"*)
      echo "  FORBIDDEN header: Play adds its own 'What's new' framing" >&2
      errs=$((errs + 1)) ;;
  esac

  while IFS= read -r line; do
    lineno=$((lineno + 1))
    [ -z "${line//[[:space:]]/}" ] && continue
    case "$line" in
      "• "*|"- "*) ;;
      *) echo "  LINE $lineno: must start with '• ' or '- ' — got: ${line:0:40}" >&2
         errs=$((errs + 1)) ;;
    esac
  done <<< "$body"

  if [ "$errs" -gt 0 ]; then
    echo "  ^ in $file" >&2
    return 1
  fi

  echo "  OK: $file ($bytes bytes, $lineno lines)"
  return 0
}

store_notes_main() {
  local -a codes=()
  local root failed=0 vc f

  root="$(store_notes_root)"

  if [ "${1:-}" = "--all" ]; then
    for f in "$root/$STORE_NOTES_SUBDIR"/*.txt; do
      vc="$(basename "$f" .txt)"
      case "$vc" in (*[!0-9]*|'') continue ;; esac
      [ "$vc" -ge "$STORE_NOTES_FIRST_MANAGED_VC" ] && codes+=("$vc")
    done
    # The current versionCode must have notes even if nobody has written the file yet —
    # a globbed list can only ever validate what already exists.
    vc="$(store_notes_current_version_code)"
    case " ${codes[*]-} " in (*" $vc "*) ;; (*) codes+=("$vc") ;; esac
  elif [ "$#" -gt 0 ]; then
    codes=("$@")
  else
    codes=("$(store_notes_current_version_code)")
  fi

  echo "Checking store notes in $STORE_NOTES_SUBDIR/"
  for vc in "${codes[@]}"; do
    validate_store_notes "$(store_notes_path "$vc")" || failed=1
  done

  if [ "$failed" -ne 0 ]; then
    cat >&2 <<'MSG'

Store notes are the user-facing release notes for Play, IzzyOnDroid and F-Droid.
Write fastlane/metadata/android/en-US/changelogs/<versionCode>.txt by hand:
plain text, <= 500 bytes, one item per line starting with "• ", saying what was
fixed and what the user will notice. CHANGELOG.md stays the dev-facing record.
MSG
    return 1
  fi
  echo "All store notes valid."
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  store_notes_main "$@"
fi
