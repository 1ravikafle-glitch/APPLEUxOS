#!/usr/bin/env bash
# Guard against the duplicated-source failure mode documented in
# deliverables/m22/single-source-tree.md.
#
# The AOSP tree must LINK the AppleUX modules, never hold a copy of them. A
# copy is how we ended up with a 123-line SystemUI stub in the tree while the
# real 1050-line Control Center lived in appleux/ — and Soong builds the copy.
#
# Exit 0 = clean. Exit 1 = at least one problem, all of them listed.

set -o pipefail
ROOT="/home/elfakiris/OsProject"
TOP="$ROOT/android-16"
FAIL=0

ok()   { printf '  \033[32mOK\033[0m    %s\n' "$1"; }
bad()  { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAIL=1; }
note() { printf '  \033[33mNOTE\033[0m  %s\n' "$1"; }

echo "=== 1. AppleUX modules must be symlinks, not directories ==="
check_link() {
    local path="$1" want="$2"
    if [[ -L "$path" ]]; then
        local got; got="$(readlink -f "$path" 2>/dev/null)"
        if [[ "$got" == "$want" ]]; then
            ok "$path -> ${got#$ROOT/}"
        else
            bad "$path points at ${got:-broken}, expected $want"
        fi
    elif [[ -d "$path" ]]; then
        bad "$path is a REAL DIRECTORY, not a symlink. This is the bug that
         shipped a SystemUI stub to Soong. Link it instead:
         ln -s $want $path"
    elif [[ -e "$path" ]]; then
        bad "$path exists but is not a symlink"
    else
        bad "$path missing — Soong will not find the module at all"
    fi
}

check_link "$TOP/frameworks/appleux"            "$ROOT/appleux/design-system"
check_link "$TOP/packages/apps/AppleUXLauncher"  "$ROOT/appleux/launcher"
check_link "$TOP/packages/apps/AppleUXSettings"  "$ROOT/appleux/settings"
# com.appleux.systemui was DELETED 2026-10-17 (deliverables/m23); its chrome is
# now the appleux-systemui-chrome library, linked at frameworks/appleux-systemui-chrome.
check_link "$TOP/frameworks/appleux-systemui-chrome" "$ROOT/appleux/systemui/chrome"
check_link "$TOP/packages/apps/AppleUXPerformance" "$ROOT/appleux/performance"
check_link "$TOP/hardware/xiaomi-appleux/rock"   "$ROOT/device-support/rock"

echo
echo "=== 2. No stray physical copies of AppleUX anywhere in the tree ==="
# .repo holds repo's own git storage (bare clones named *.git), which is
# bookkeeping, not source. Only real source trees count as copies.
STRAY=$(find "$TOP" \
          -path "$TOP/out" -prune -o \
          -path "$TOP/.repo" -prune -o \
          -type d -name "AppleUX*" -print 2>/dev/null | \
        while read -r d; do [[ -L "$d" ]] || echo "$d"; done)
if [[ -z "$STRAY" ]]; then
    ok "no physical AppleUX* source directories in the tree"
else
    while read -r d; do bad "physical copy: ${d#$ROOT/}"; done <<< "$STRAY"
fi

echo
echo "=== 3. appleux/ is the only copy, and it is under version control ==="
for m in design-system launcher settings systemui performance; do
    if [[ -d "$ROOT/appleux/$m" ]]; then
        ok "appleux/$m present"
    else
        bad "appleux/$m missing"
    fi
done
if git -C "$ROOT/appleux" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    ok "appleux/ is a git repo ($(git -C "$ROOT/appleux" rev-list --count HEAD 2>/dev/null) commits)"
else
    bad "appleux/ is NOT a git repo — edits there are unrecoverable"
fi

echo
echo "=== 4. PRODUCT_PACKAGES must only name real, installable modules ==="
# android_library modules have no APK to install. Putting one here fails at
# install time, hours into a build, not at Soong analysis time.
declare -A IS_LIB=(
    [appleux-design-system]=1
    [appleux-performance-lib]=1
    [appleux-device-support-rock]=1
    [androidx_runtime]=1
)
# runtime_resource_overlay modules DO produce an installable overlay APK, so
# FrameworkOverlayRock and friends are legitimately in PRODUCT_PACKAGES and
# must not be flagged here.
MK="$TOP/device/xiaomi/rock/appleux.mk"
if [[ -f "$MK" ]]; then
    # Read only the PRODUCT_PACKAGES assignment, not the whole file.
    PKGS=$(awk '/^PRODUCT_PACKAGES/{f=1;next} f&&/\\/{gsub(/\\/,"");printf "%s ",$0;next} f{print;exit}' "$MK")
    for m in $PKGS; do
        m="${m%% *}"
        [[ -z "$m" ]] && continue
        if [[ -n "${IS_LIB[$m]:-}" ]]; then
            bad "$m is in PRODUCT_PACKAGES but it is a library, not an APK"
        elif grep -rq "name: \"$m\"" --include=Android.bp \
                "$TOP/packages/apps/AppleUXLauncher" "$TOP/packages/apps/AppleUXSettings" \
                "$TOP/packages/apps/AppleUXPerformance" \
                "$ROOT/appleux" "$ROOT/device-support" "$TOP/device/xiaomi/rock/rro_overlay" 2>/dev/null; then
            ok "$m resolves to a module"
        else
            bad "$m in PRODUCT_PACKAGES but no module defines it"
        fi
    done
else
    bad "$MK missing"
fi

echo
echo "=== 5. Priv-app grants must match the manifests ==="
ALLOW="$TOP/device/xiaomi/rock/permissions/privapp-permissions-appleux.xml"
if [[ -f "$ALLOW" ]]; then
    ok "allowlist present"
    # Every signature|privileged permission needs privileged:true, or the grant
    # is inert because the APK landed in /system/app.
    for mod in "$ROOT/appleux/launcher" "$ROOT/appleux/settings" "$ROOT/appleux/systemui"; do
        name=$(basename "$mod")
        if ! grep -q "privileged: true" "$mod/Android.bp" 2>/dev/null; then
            bad "$name/Android.bp lacks privileged: true — its grants will be denied"
        else
            ok "$name is privileged"
        fi
    done
    # Grant declared in the allowlist but not in the manifest = dead grant.
    python3 - "$ALLOW" "$ROOT/appleux" <<'PY'
import io, re, os, sys
allow, root = sys.argv[1], sys.argv[2]
pkgs = {
    "com.appleux.launcher": os.path.join(root, "launcher", "src/main/AndroidManifest.xml"),
    "com.appleux.settings": os.path.join(root, "settings", "src/main/AndroidManifest.xml"),
    "com.appleux.systemui": os.path.join(root, "systemui", "src/main/AndroidManifest.xml"),
}
xml = io.open(allow, encoding="utf-8").read()
blocks = re.findall(r'<privapp-permissions package="([^"]+)">(.*?)</privapp-permissions>',
                    xml, re.S)
bad = 0
for pkg, body in blocks:
    path = pkgs.get(pkg)
    if not path or not os.path.isfile(path):
        continue
    man = io.open(path, encoding="utf-8").read()
    for perm in re.findall(r'<permission name="([^"]+)"', body):
        # A permission can be satisfied two ways: requested via
        # <uses-permission>, or ENFORCED on a component via
        # android:permission. BIND_SYSTEM_UI_SERVICES belongs on the
        # <service> as android:permission — putting it in <uses-permission>
        # would be wrong, since it is the permission we demand of callers,
        # not one we call with.
        declared = ('android:name="%s"' % perm) in man
        enforced  = ('android:permission="%s"' % perm) in man
        if not (declared or enforced):
            print("  \033[31mFAIL\033[0m  %s granted %s but the manifest neither "
                  "declares nor enforces it" % (pkg, perm))
            bad = 1
if not bad:
    print("  \033[32mOK\033[0m    every allowlist grant is declared or enforced")
sys.exit(bad)
PY
    [[ $? -ne 0 ]] && FAIL=1
else
    bad "allowlist missing at $ALLOW"
fi

echo
echo "=== 6. Allowlist must actually be copied into the image ==="
if grep -q "privapp-permissions-appleux.xml:\$(TARGET_COPY_OUT_SYSTEM)/etc/permissions" "$MK" 2>/dev/null; then
    ok "PRODUCT_COPY_FILES installs the allowlist"
else
    bad "allowlist is not copied to /system/etc/permissions — every grant is dead"
fi

echo
if [[ $FAIL -eq 0 ]]; then
    printf '\033[32msingle-source-tree checks passed\033[0m\n'
else
    printf '\033[31msingle-source-tree checks FAILED\033[0m\n'
fi
exit $FAIL