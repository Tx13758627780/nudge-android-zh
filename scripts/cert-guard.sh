#!/usr/bin/env bash
#
# cert-guard.sh — signing-certificate guard for installing Nudge on the shared bench Pixel.
#
# WHY THIS EXISTS (2026-09-29):
#   `APK=debug scripts/device-qa.sh` uninstalled the release build and installed a
#   DEBUG-signed Nudge on the bench, silently. Android refuses to update an app across signing
#   keys, so every later `APK=main` / `APK=release` / `adb install -r` of a CI APK died with
#   INSTALL_FAILED_UPDATE_INCOMPATIBLE, and the next QA lane had to stop, escalate and
#   uninstall, which wiped the bench. The bench carries the RELEASE key; switching it is now an
#   explicit opt-in (ALLOW_CERT_SWITCH=1), decided by comparing the installed APK's signer
#   against the incoming APK's signer BEFORE anything is uninstalled.
#
#   The installed signer is read by pulling the installed base.apk and running apksigner on it,
#   the same tool on both sides. `dumpsys package` only exposes a 32-bit hashCode of the
#   signature in an undocumented format, and its DEBUGGABLE flag is a proxy: two debug builds
#   from different machines are both DEBUGGABLE and still incompatible.
#
# AS A LIBRARY (sourced by scripts/device-qa.sh; tested by scripts/test-cert-guard.sh):
#   . scripts/cert-guard.sh        # defines the functions, runs nothing, sets no shell options
#
# Needs `apksigner` (Android build-tools) and `adb` on PATH.

# The invocation that puts the release build back on the bench after a deliberate switch.
CERT_RESTORE_CMD="APK=main ALLOW_CERT_SWITCH=1 scripts/device-qa.sh setup"

# apk_signer_sha256 <apk> — lowercase SHA-256 of the first signer's certificate; empty and
# nonzero if the APK is unreadable, unsigned, or apksigner is missing.
apk_signer_sha256() {
  local out
  out="$(apksigner verify --print-certs "$1" 2>/dev/null |
    sed -n 's/^Signer #1 certificate SHA-256 digest: *\([0-9A-Fa-f]\{64\}\).*/\1/p' | head -1)"
  [[ -n "$out" ]] || return 1
  printf '%s\n' "$out" | tr 'A-F' 'a-f'
}

# apk_signer_dn <apk> — the first signer's distinguished name, empty if unreadable.
apk_signer_dn() {
  apksigner verify --print-certs "$1" 2>/dev/null |
    sed -n 's/^Signer #1 certificate DN: *//p' | head -1
}

# signer_label <dn> — a human name for a key, for the messages below.
signer_label() {
  case "$1" in
    "")                   printf 'an unreadable key' ;;
    *"CN=Android Debug"*) printf 'the DEBUG key (%s)' "$1" ;;
    *)                    printf 'the release key (%s)' "$1" ;;
  esac
}

# signer_is_debug <dn> — true for the Android Gradle Plugin's default debug keystore.
signer_is_debug() { [[ "$1" == *"CN=Android Debug"* ]]; }

# pull_installed_apk <serial> <app_id> <dest> — copy the installed base.apk to <dest>.
#   0 = pulled · 1 = the app is not installed · 2 = installed but the pull failed
pull_installed_apk() {
  local serial="$1" app_id="$2" dest="$3" path
  path="$(adb -s "$serial" shell pm path "$app_id" 2>/dev/null | tr -d '\r' |
    sed -n 's/^package://p' | grep -E '/base\.apk$' | head -1)"
  [[ -n "$path" ]] || return 1
  adb -s "$serial" pull "$path" "$dest" >/dev/null 2>&1 || return 2
  [[ -s "$dest" ]] || return 2
}

# cert_decision <installed_sha> <incoming_sha> <allow_switch>
#   installed_sha: a digest, "none" (not installed) or "" (installed but unreadable).
#   Prints exactly one of:
#     fresh    nothing installed — a plain install, no key to clash with
#     same     same signer — an in-place update keeps the bench state
#     switch   signers differ and ALLOW_CERT_SWITCH=1 — uninstall (wipes the bench), then install
#     refuse   signers differ and no opt-in — stop before touching the device
#     unknown  a digest could not be read — proceed; do_install's INSTALL_FAILED_UPDATE_INCOMPATIBLE
#              branch is the backstop, and it enforces the same opt-in
cert_decision() {
  local installed="$1" incoming="$2" allow="$3"
  if [[ "$installed" == "none" ]]; then echo fresh; return; fi
  if [[ -z "$installed" || -z "$incoming" ]]; then echo unknown; return; fi
  if [[ "$installed" == "$incoming" ]]; then echo same; return; fi
  if [[ "$allow" == "1" ]]; then echo switch; else echo refuse; fi
}

# cert_refusal_message <installed_label> <incoming_label> [rerun_command]
cert_refusal_message() {
  local rerun="${3:-<the same invocation>}"
  cat <<EOF
The bench Pixel already has a Nudge signed with a DIFFERENT key.
  installed: $1
  this APK:  $2
Android will not update an app across signing keys (INSTALL_FAILED_UPDATE_INCOMPATIBLE). The only
way through is to UNINSTALL it, which wipes the bench's Nudge data and grants, and leaves every
later CI/release install hitting the same wall until someone switches the bench back.
Refusing: the installed Nudge was not touched.
  To switch on purpose:          ALLOW_CERT_SWITCH=1 ${rerun}
  To put the release build back: ${CERT_RESTORE_CMD}
EOF
}

# update_incompatible_message — plain English for adb's INSTALL_FAILED_UPDATE_INCOMPATIBLE.
update_incompatible_message() {
  cat <<EOF
adb refused the install with INSTALL_FAILED_UPDATE_INCOMPATIBLE: the bench has a differently-signed
Nudge installed (probably a debug build) and Android will not update across signing keys.
Rerun with ALLOW_CERT_SWITCH=1 to uninstall it and replace it; that wipes the bench's Nudge state
and \`setup\` has to run again. To put the release build back: ${CERT_RESTORE_CMD}
EOF
}

# cert_switch_banner <installed_label> <incoming_label>
cert_switch_banner() {
  cat <<EOF
################################################################################
#  ALLOW_CERT_SWITCH=1: SWITCHING THE BENCH'S SIGNING KEY
#    from: $1
#    to:   $2
#  Uninstalling Nudge. ALL bench state for it is WIPED (data, rules, grants);
#  \`setup\` must run before any other case means anything.
################################################################################
EOF
}

# debug_left_on_bench_message — printed when a run leaves a debug-signed Nudge installed.
debug_left_on_bench_message() {
  cat <<EOF
################################################################################
#  The bench Pixel is now carrying a DEBUG-signed Nudge.
#  Every CI/release APK will fail to install over it until it is switched back:
#    ${CERT_RESTORE_CMD}
################################################################################
EOF
}
