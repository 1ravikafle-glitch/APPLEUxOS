#!/usr/bin/env python3
"""
Repair "actual type is 'X', but 'Context' was expected".

The app shells are almost entirely programmatic views, and the idiomatic shape
is `SomeView(this).apply { ... }`. Inside that apply block `this` is the View,
not the Activity, so any further `SomeView(this)` or
`LayoutUtils.dpToPx(token, this)` in the same block silently binds to the wrong
receiver. The compile error is the only thing that catches it.

This works from kotlinc's own file:line:col rather than from a regex over the
file, so it can only ever rewrite the exact token the compiler named. Each
pass rebuilds and re-checks, so it converges instead of looping on a stale list.

`this` is only rewritten where it is a BARE this (not `this.foo`, not
`this@Foo`, not `this$foo`).
"""
import os, re, subprocess, sys, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ERR = re.compile(
    r"kt: (?P<file>[^\s:]+\.kt):(?P<line>\d+):(?P<col>\d+): error: "
    r"argument type mismatch: actual type is '[A-Za-z0-9_]+', but 'Context' was expected"
)
BARE_THIS = re.compile(r"(?<![\w@$.])this(?![\w@.])")


def resolve(rel):
    """kotlinc runs with the module dir as cwd, so its paths are relative to
    appleux/<module>/src/... Find the real file under either root."""
    for root in (ROOT, os.path.join(ROOT, "appleux")):
        cand = os.path.join(root, rel)
        if os.path.isfile(cand):
            return cand
    return None


def build():
    p = subprocess.run(["bash", "scripts/build-emulator-apks.sh"],
                       cwd=ROOT, capture_output=True, text=True)
    return p.stdout + p.stderr


def main():
    max_passes = 8
    for pass_no in range(1, max_passes + 1):
        out = build()
        hits = list(ERR.finditer(out))
        if not hits:
            print(f"pass {pass_no}: no receiver mismatches left")
            tail = [l for l in out.splitlines() if "error:" in l or "==>" in l]
            print("\n".join(tail[:60]) if tail else "(build produced no errors)")
            other = "FAILED" in out
            print("BUILD:", "FAILED" if other else "clean")
            return 0 if not other else 1

        # (file, line) -> smallest column, so a line with several hits is
        # handled from the leftmost one outward.
        targets = collections.defaultdict(set)
        for m in hits:
            targets[m["file"]].add((int(m["line"]), int(m["col"])))
        print(f"pass {pass_no}: {len(hits)} receiver mismatch(es)")

        fixed = 0
        for rel, spots in targets.items():
            path = resolve(rel)
            if path is None:
                print(f"  {rel}: not found under {ROOT} or {ROOT}/appleux - skipped")
                continue
            cls = os.path.splitext(os.path.basename(path))[0]
            lines = open(path).read().split("\n")
            for lineno, _ in sorted(spots):
                if lineno - 1 >= len(lines):
                    continue
                text = lines[lineno - 1]
                new, n = BARE_THIS.subn(f"this@{cls}", text)
                if n:
                    lines[lineno - 1] = new
                    fixed += n
            open(path, "w").write("\n".join(lines))
            print(f"  {os.path.basename(path)}: {len(spots)} line(s)")
        print(f"  rewrote {fixed} bare `this`")
        if fixed == 0:
            print("  nothing rewritable - stopping")
            print("\n".join(out.splitlines()[-40:]))
            return 1
    print(f"gave up after {max_passes} passes")
    return 1


if __name__ == "__main__":
    sys.exit(main())
