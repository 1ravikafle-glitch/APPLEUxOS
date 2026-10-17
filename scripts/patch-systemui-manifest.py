#!/usr/bin/env python3
"""One-off: declare the signature|privileged permissions in the SystemUI manifest.

Written as a file rather than a heredoc because heredoc quoting was mangling
leading whitespace and the anchor match silently failed twice.
"""
import io
import sys

PATH = "/home/elfakiris/OsProject/appleux/systemui/src/main/AndroidManifest.xml"

ANCHOR = '<uses-permission android:name="android.permission.EXPAND_WINDOWS" />'

ADDITIONS = [
    "STATUS_BAR",
    "EXPAND_STATUS_BAR",
    "WRITE_SECURE_SETTINGS",
    "PACKAGE_USAGE_STATS",
    "CHANGE_WIFI_STATE",
    "BLUETOOTH_SCAN",
]

COMMENT = (
    "    <!-- Signature|privileged grants. These MUST be declared here as well\n"
    "         as granted in device/xiaomi/rock/permissions/\n"
    "         privapp-permissions-appleux.xml: a grant for an undeclared\n"
    "         permission is dead, and a declared signature|privileged permission\n"
    "         with no grant is denied at runtime. The module is privileged:true,\n"
    "         or none of these would be grantable at all. -->\n"
)

src = io.open(PATH, encoding="utf-8").read()

if src.count(ANCHOR) != 1:
    sys.exit("anchor count = %d (expected 1)" % src.count(ANCHOR))

block = "\n    " + ANCHOR + "\n" + COMMENT
for perm in ADDITIONS:
    line = '<uses-permission android:name="android.permission.%s" />' % perm
    if line in src:
        continue
    block += "    " + line + "\n"

src = src.replace(ANCHOR, block.rstrip("\n"), 1)
io.open(PATH, "w", encoding="utf-8").write(src)
print("PATCHED")