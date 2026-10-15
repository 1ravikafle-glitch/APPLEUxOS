#!/usr/bin/env bash
# AppleUX debloat pass v2 — heavy, but fully reversible.
#
# Contract (v2 fixes over v1):
#   * REVERSIBLE BY CONSTRUCTION: `enable` iterates the exact same package
#     lists as `disable`, so there is no package in this file that cannot be
#     restored with one command. v1's lists were hand-duplicated per mode;
#     they are now single-sourced.
#   * IDEMPOTENT: device state is read ONCE per run (`pm list packages -d
#     --user N`) instead of one adb round-trip per package, and packages
#     already in the target state are skipped, not re-applied.
#   * SAFE DEFAULT: the default mode is `status` (read-only), not `disable`.
#     Mutating modes require --yes when stdin is not a TTY.
#   * `enable`/`disable` are both scoped to --user, so a work profile cannot
#     be left half-restored (v1 enabled across all users but disabled one).
#
# Never touched, by charter (see deliverables/m2/keep-remove-disable-list.md):
#   core platform services; telephony / SMS app / IMS; emergency broadcast;
#   location providers; accounts & auth (com.google.android.gms, .gsf);
#   KeyChain / credential storage; backup transport; package installer;
#   PermissionController; MediaProvider; DownloadsProvider; ExternalStorage;
#   WebView; stock Settings (AppleUX Settings answers android.settings.*
#   intents and stock Settings is the documented intent fallback); SystemUI
#   and Launcher3 hosts until the AppleUX replacements are verified.
#
# usage: debloat-disable.sh [disable|enable|status] [options]
#   --serial SERIAL   adb serial. Default $ANDROID_SERIAL, else sole device.
#   --tier TIER       core (default) | google | media | ime | optical
#                     | replaced | all.  `all` = core+google+media+ime+optical.
#   --user N          user id to act on (default 0).
#   --yes | -y        skip the confirmation prompt (required if stdin is not a TTY).
#   --dry-run         print what would change, mutate nothing.
#   -h | --help       this text.
#
# examples:
#   ./debloat-disable.sh status --serial emulator-5554 --tier all
#   ./debloat-disable.sh disable --tier core --dry-run
#   ./debloat-disable.sh disable --tier all --serial emulator-5554 --yes
#   ./debloat-disable.sh enable  --tier all --serial emulator-5554 --yes   # full revert
#
# NOTE --tier replaced is NOT part of `all`. Those are stock surfaces AppleUX is
# replacing (dialer, contacts, SMS) whose replacements are still PENDING. They
# are only disableable by naming the tier explicitly, so nobody loses telephony
# reachability by typing `all`.

set -euo pipefail

usage() { awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "$0"; }

# ---------------------------------------------------------------- tier tables
# A. AOSP demo / dev / sample content. No user-visible capability is lost.
PKGS_CORE="com.android.protips
com.android.traceur
com.android.spare_parts
com.android.basicnetworking
com.android.demos.wallpaper
com.android.bookmarkprovider
com.android.apps.tag
com.google.android.apps.tag
com.example.android.apis
com.android.wallpaper.livepicker"

# B. Google / Play stack. AppleUX references zero com.google.* packages
#    (verified by grep across appleux/**), so nothing here is load-bearing.
PKGS_GOOGLE="com.google.android.googlequicksearchbox
com.google.android.apps.nexuslauncher
com.google.android.apps.maps
com.google.android.apps.photos
com.google.android.apps.docs
com.google.android.youtube
com.android.chrome
com.google.android.apps.chrome
com.google.android.apps.chrome_beta
com.google.android.apps.chrome_dev
com.google.android.apps.podcasts
com.google.android.apps.books
com.google.android.apps.news
com.google.android.apps.fitness
com.google.android.apps.finance
com.google.android.familylink
com.google.android.apps.streetview
com.google.android.apps.walletnfcrel
com.google.android.apps.messaging
com.google.android.apps.wallpaper
com.google.android.wallpaper
com.google.android.apps.vr
com.google.android.apps.daydream
com.google.android.ar.core
com.google.android.adservices
com.google.android.ext.services
com.google.android.feedback
com.google.android.partnersetup"

# C. Stock AOSP media/utility apps. Replaced by design, but users do tap them.
PKGS_MEDIA="com.android.gallery3d
com.android.music
com.android.deskclock
com.android.calculator2
com.android.calendar"

# D. Optional latin-IME language packs only. The base keyboard
#    (com.google.android.inputmethod.latin, .unlisted,
#    com.android.inputmethod.latin.AOSP-latin) is NEVER listed: with those gone
#    the device has no IME at all.
PKGS_IME="com.google.android.inputmethod.latin.arabic
com.google.android.inputmethod.latin.bengali
com.google.android.inputmethod.latin.devanagari
com.google.android.inputmethod.latin.farsei
com.google.android.inputmethod.latin.gujarati
com.google.android.inputmethod.latin.hebrew
com.google.android.inputmethod.latin.hangul
com.google.android.inputmethod.latin.japanese
com.google.android.inputmethod.latin.kannada
com.google.android.inputmethod.latin.korean
com.google.android.inputmethod.latin.malayalam
com.google.android.inputmethod.latin.marathi
com.google.android.inputmethod.latin.nepali
com.google.android.inputmethod.latin.nordic
com.google.android.inputmethod.latin.persian
com.google.android.inputmethod.latin.ru
com.google.android.inputmethod.latin.sinhala
com.google.android.inputmethod.latin.slavo
com.google.android.inputmethod.latin.tamil
com.google.android.inputmethod.latin.telugu
com.google.android.inputmethod.latin.thai"

# E. Features with a real user-visible cost on a POCO M5. Opt-in on purpose.
PKGS_OPTICAL="com.android.printspooler
com.android.dreams.basic
com.android.bluetoothmap
com.android.wallpaper.holospiral
com.android.wallpaper.category.cell
com.android.wallpaper.category.cloud
com.android.wallpaper.category.mountain
com.android.wallpaper.category.organic
com.android.wallpaper.category.sea"

# F. Stock surfaces AppleUX replaces, replacements NOT yet verified. Never in
#    `all`; requires naming this tier explicitly.
PKGS_REPLACED="com.android.dialer
com.android.contacts
com.android.messaging"

TIERS="core google media ime optical replaced all"

packages_for_tier() {
  case "$1" in
    core)     printf '%s\n' "$PKGS_CORE" ;;
    google)   printf '%s\n' "$PKGS_GOOGLE" ;;
    media)    printf '%s\n' "$PKGS_MEDIA" ;;
    ime)      printf '%s\n' "$PKGS_IME" ;;
    optical)  printf '%s\n' "$PKGS_OPTICAL" ;;
    replaced) printf '%s\n' "$PKGS_REPLACED" ;;
    all)      printf '%s\n%s\n%s\n%s\n%s\n' \
                "$PKGS_CORE" "$PKGS_GOOGLE" "$PKGS_MEDIA" "$PKGS_IME" "$PKGS_OPTICAL" ;;
    *)        return 1 ;;
  esac
}

# ------------------------------------------------------------------- CLI parse
MODE=""
SERIAL="${ANDROID_SERIAL:-}"
TIER="core"
USER_ID=0
ASSUME_YES=0
DRY_RUN=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    disable|enable|status) MODE="$1" ;;
    --serial)   [ "$#" -ge 2 ] || { echo "error: --serial needs a value" >&2; exit 2; }
                SERIAL="$2"; shift ;;
    --serial=*) SERIAL="${1#*=}" ;;
    --tier)     [ "$#" -ge 2 ] || { echo "error: --tier needs a value" >&2; exit 2; }
                TIER="$2"; shift ;;
    --tier=*)   TIER="${1#*=}" ;;
    --user)     [ "$#" -ge 2 ] || { echo "error: --user needs a value" >&2; exit 2; }
                USER_ID="$2"; shift ;;
    --user=*)   USER_ID="${1#*=}" ;;
    --yes|-y)   ASSUME_YES=1 ;;
    --dry-run)  DRY_RUN=1 ;;
    -h|--help)  usage; exit 0 ;;
    *)          echo "error: unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

MODE="${MODE:-status}"

case " $TIERS " in
  *" $TIER "*) ;;
  *) echo "error: unknown tier '$TIER' (one of: $TIERS)" >&2; exit 2 ;;
esac

if ! [[ "$USER_ID" =~ ^[0-9]+$ ]]; then
  echo "error: --user must be a non-negative integer (got '$USER_ID')" >&2; exit 2
fi

ADB_BIN="${ADB:-adb}"
command -v "$ADB_BIN" >/dev/null 2>&1 || {
  echo "error: adb not found in PATH (set ADB=/path/to/adb to override)" >&2; exit 127; }

adb_args=()
[ -n "$SERIAL" ] && adb_args+=(-s "$SERIAL")
adbq() { "$ADB_BIN" ${adb_args[@]+"${adb_args[@]}"} "$@"; }

state="$(adbq get-state 2>/dev/null || true)"
if [ "$state" != "device" ]; then
  echo "error: no device${SERIAL:+ for serial '$SERIAL'} (adb get-state: '${state:-no such device}')" >&2
  exit 1
fi

mapfile -t PKGS < <(packages_for_tier "$TIER" | sed '/^[[:space:]]*$/d' | sort -u)

# Single read of device state — this is the idempotency source of truth and
# turns an O(n) adb round-trip loop into 2 round-trips total.
INSTALLED="$(adbq shell pm list packages 2>/dev/null | tr -d '\r' || true)"
DISABLED="$(adbq shell pm list packages --user "$USER_ID" -d 2>/dev/null | tr -d '\r' || true)"
if [ -z "$DISABLED" ]; then
  # Older `pm` without --user support.
  DISABLED="$(adbq shell pm list packages -d 2>/dev/null | tr -d '\r' || true)"
fi

is_installed() { grep -qxF "package:$1" <<<"$INSTALLED"; }
is_disabled()  { grep -qxF "package:$1" <<<"$DISABLED"; }

act() { # act <state-word> <package>
  printf '  %-18s %s\n' "$1" "$2"
}

# -------------------------------------------------------------- confirm & run
if [ "$MODE" = "disable" ] && [ "$DRY_RUN" -eq 0 ] && [ "$ASSUME_YES" -eq 0 ]; then
  if [ -t 0 ]; then
    printf 'About to DISABLE %d packages on %s (user %s).\n' "${#PKGS[@]}" "${SERIAL:-<default>}" "$USER_ID"
    printf 'Re-enable everything with: %s enable --tier %s --user %s --yes\n' "$0" "$TIER" "$USER_ID"
    read -r -p 'Proceed? [y/N] ' reply
    case "$reply" in y|Y|yes|YES) ;; *) echo "aborted"; exit 1 ;; esac
  else
    echo "error: 'disable' would mutate the device but stdin is not a TTY." >&2
    echo "       Re-run with --yes (or --dry-run to preview)." >&2
    exit 2
  fi
fi

printf 'mode=%s tier=%s user=%s serial=%s packages=%d%s\n' \
  "$MODE" "$TIER" "$USER_ID" "${SERIAL:-<default>}" "${#PKGS[@]}" \
  "$([ "$DRY_RUN" -eq 1 ] && echo ' dry-run' || true)"

changed=0; skipped=0; absent=0

for p in "${PKGS[@]}"; do
  if ! is_installed "$p"; then
    absent=$((absent + 1))
    [ "$MODE" = "status" ] || act "absent" "$p"
    continue
  fi

  case "$MODE" in
    status)
      if is_disabled "$p"; then act "disabled" "$p"; else act "enabled" "$p"; fi
      ;;
    disable)
      if is_disabled "$p"; then
        skipped=$((skipped + 1)); act "already-disabled" "$p"
      elif [ "$DRY_RUN" -eq 1 ]; then
        changed=$((changed + 1)); act "would-disable" "$p"
      else
        out="$(adbq shell pm disable-user --user "$USER_ID" "$p" 2>&1 | tr -d '\r' | tail -n 1 || true)"
        changed=$((changed + 1)); act "disable" "$p -> ${out##*: }"
        DISABLED="${DISABLED}"$'\n'"package:$p"
      fi
      ;;
    enable)
      if ! is_disabled "$p"; then
        skipped=$((skipped + 1)); act "already-enabled" "$p"
      elif [ "$DRY_RUN" -eq 1 ]; then
        changed=$((changed + 1)); act "would-enable" "$p"
      else
        out="$(adbq shell pm enable --user "$USER_ID" "$p" 2>&1 | tr -d '\r' | tail -n 1 || true)"
        changed=$((changed + 1)); act "enable" "$p -> ${out##*: }"
        DISABLED="$(grep -vxF "package:$p" <<<"$DISABLED" || true)"
      fi
      ;;
  esac
done

echo "---"
printf 'summary: changed=%d skipped=%d not-installed=%d\n' "$changed" "$skipped" "$absent"
[ "$MODE" = "status" ] || echo "revert with: $0 enable --tier $TIER --user $USER_ID${SERIAL:+ --serial $SERIAL} --yes"