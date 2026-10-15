#!/usr/bin/env python3
"""Resolve and vendor the transitive AndroidX runtime closure for AppleUX.

Reads Maven POMs from Google's Maven repo, walks compile/runtime scopes,
honours <properties>, parent POMs and imported BOMs, and downloads each
artifact in the packaging aapt2/d8 actually needs (aar -> extract
classes.jar, jar -> use directly).

Usage:  scripts/fetch-androidx-deps.py [--refresh]
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AAR_DIR = os.path.join(ROOT, "thirdparty", "aar")
EXTRACT_DIR = os.path.join(ROOT, "thirdparty", "extract")
LIBS_DIR = os.path.join(ROOT, "thirdparty", "libs")

REPOS = [
    "https://dl.google.com/dl/android/maven2",
    "https://repo1.maven.org/maven2",
]
NS = "{http://maven.apache.org/POM/4.0.0}"

# Everything the launcher/settings apps need at compile and runtime time.
# These are roots, not exclusive members: their own classes ship inside the APK.
ROOTS = [
    ("androidx.appcompat", "appcompat", "1.7.0"),
    ("androidx.recyclerview", "recyclerview", "1.3.2"),
    ("androidx.swiperefreshlayout", "swiperefreshlayout", "1.1.0"),
    ("androidx.dynamicanimation", "dynamicanimation", "1.0.0"),
    ("androidx.appcompat", "appcompat-resources", "1.7.0"),
]

# RUNTIME scopes only: we ship these inside the APK.
SCOPES = {"compile", "runtime", "compile-only"}


def fetch(url: str, dest: str) -> bool:
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return True
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    try:
        r = subprocess.run(
            ["curl", "-sSL", "--fail", "--max-time", "120", "-o", dest, url],
            capture_output=True,
        )
        if r.returncode != 0 or not os.path.exists(dest) or os.path.getsize(dest) == 0:
            if os.path.exists(dest):
                os.remove(dest)
            return False
        return True
    except Exception:
        return False


def text(node, tag, default=None):
    if node is None:
        return default
    el = node.find(NS + tag)
    if el is None or el.text is None:
        return default
    return el.text.strip()


def get_pom(group, artifact, version, cache):
    """Return parsed POM root element, or None when unavailable."""
    key = (group, artifact, version)
    if key in cache:
        return cache[key]
    for repo in REPOS:
        path = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
        dest = os.path.join(AAR_DIR, "_poms", f"{artifact}-{version}.pom")
        if fetch(f"{repo}/{path}", dest):
            try:
                root = ET.parse(dest).getroot()
                cache[key] = root
                return root
            except ET.ParseError:
                continue
    cache[key] = None
    return None


def subst(value, props):
    if not value:
        return value
    for _ in range(5):
        new = re.sub(r"\$\{([^}]+)\}", lambda m: props.get(m.group(1), m.group(0)), value)
        if new == value:
            break
        value = new
    return value


def project_info(group, artifact, version, cache, seen=None):
    """(effective props, dependencyManagement map) for a POM."""
    seen = seen or set()
    if (group, artifact, version) in seen:
        return {}, {}
    seen.add((group, artifact, version))
    pom = get_pom(group, artifact, version, cache)
    if pom is None:
        return {}, {}

    props = {}
    for p in pom.findall(f"{NS}properties/*"):
        props[p.tag.replace(NS, "")] = (p.text or "").strip()

    # parent properties first
    parent = pom.find(NS + "parent")
    if parent is not None:
        pg = text(parent, "groupId")
        pa = text(parent, "artifactId")
        pv = text(parent, "version")
        pprops, pmgmt = project_info(pg, pa, pv, cache, seen)
        props = {**pprops, **props}
        # parent supplies dependencyManagement too
        pass

    pom_group = text(pom, "groupId", group)
    props.setdefault("project.groupId", pom_group)
    props.setdefault("project.version", version)
    props.setdefault("project.artifactId", artifact)

    mgmt = {}
    dm = pom.find(NS + "dependencyManagement")
    if dm is not None:
        # BOM imports first so that explicit entries in this POM win
        for d in dm.findall(f"{NS}dependencies/{NS}dependency"):
            if text(d, "type") == "pom" and text(d, "scope") == "import":
                ig = text(d, "groupId")
                ia = text(d, "artifactId")
                iv = subst(text(d, "version"), props)
                if ig and ia and iv:
                    _, imp = project_info(ig, ia, iv, cache, seen)
                    for k, v in imp.items():
                        mgmt.setdefault(k, v)
        for d in dm.findall(f"{NS}dependencies/{NS}dependency"):
            if text(d, "type") == "pom" and text(d, "scope") == "import":
                continue
            g = subst(text(d, "groupId"), props)
            a = text(d, "artifactId")
            v = subst(text(d, "version"), props)
            if v and g and a:
                mgmt[(g, a)] = (v, "compile")
    return props, mgmt


def walk(group, artifact, version, cache, out, props_stack):
    key = (group, artifact, version)
    if key in out:
        return
    out[key] = False  # cycle guard

    props, mgmt = project_info(group, artifact, version, cache)
    merged = dict(props)
    merged.update(props_stack.get(group, {}))

    pom = get_pom(group, artifact, version, cache)
    if pom is None:
        del out[key]
        return

    deps = pom.find(NS + "dependencies")
    if deps is None:
        return
    for d in deps.findall(NS + "dependency"):
        g = text(d, "groupId")
        a = text(d, "artifactId")
        scope = text(d, "scope", "compile")
        optional = text(d, "optional", "false") == "true"
        if scope not in SCOPES or optional or not g or not a:
            continue
        if g.startswith("com.android") or g.startswith("com.google.android"):
            continue
        v = subst(text(d, "version"), merged) or mgmt.get((g, a), (None, None))[0]
        if not v:
            continue
        v = subst(v, merged)
        if not re.match(r"^\d", v):
            continue
        walk(g, a, v, cache, out, props_stack)


def vkey(v: str):
    """Sortable version key: numeric parts compared numerically."""
    return [int(p) if p.isdigit() else p for p in re.split(r"[.\-]", v)]


# Artifacts that must never be vendored: the Kotlin runtime comes from the
# local 2.1.21 compiler we already build against, and these shims are either
# multiplatform metadata with no classes or pure duplicates.
BLOCKLIST_GA = {
    ("org.jetbrains.kotlin", "kotlin-stdlib"),
    ("org.jetbrains.kotlin", "kotlin-stdlib-common"),
    ("org.jetbrains.kotlin", "kotlin-stdlib-jdk7"),
    ("org.jetbrains.kotlin", "kotlin-stdlib-jdk8"),
    ("org.jetbrains.kotlin", "kotlin-stdlib-jdk6"),
}


def main() -> int:
    refresh = "--refresh" in sys.argv
    for d in (AAR_DIR, EXTRACT_DIR, LIBS_DIR):
        os.makedirs(d, exist_ok=True)

    if refresh:
        for d in (AAR_DIR, EXTRACT_DIR, LIBS_DIR):
            subprocess.run(["rm", "-rf", d], check=False)
            os.makedirs(d, exist_ok=True)

    cache: dict = {}
    resolved: dict = {}
    for g, a, v in ROOTS:
        walk(g, a, v, cache, resolved, {})

    # conflict resolution: one version per (group, artifact), highest wins
    winners: dict = {}
    for (g, a, v) in resolved:
        if (g, a) in BLOCKLIST_GA:
            continue
        cur = winners.get((g, a))
        if cur is None or vkey(v) > vkey(cur):
            winners[(g, a)] = v

    added, empty, failed = [], [], []
    for (g, a), v in sorted(winners.items()):
        base = f"{a}-{v}"
        dest_jar = os.path.join(LIBS_DIR, f"{base}.jar")
        if os.path.exists(dest_jar):
            added.append(base)
            continue

        staged = None
        for repo in REPOS:
            path = f"{g.replace('.', '/')}/{a}/{v}/{base}.aar"
            dest = os.path.join(AAR_DIR, f"{base}.aar")
            if fetch(f"{repo}/{path}", dest):
                ex = os.path.join(EXTRACT_DIR, base)
                os.makedirs(ex, exist_ok=True)
                r = subprocess.run(["unzip", "-o", "-q", dest, "-d", ex],
                                   capture_output=True, text=True)
                if r.returncode != 0:
                    print(f"  unzip failed {base}: {r.stderr.strip()[:120]}")
                cj = os.path.join(ex, "classes.jar")
                if os.path.isfile(cj):
                    staged = cj
                break
        if staged is None:
            for repo in REPOS:
                path = f"{g.replace('.', '/')}/{a}/{v}/{base}.jar"
                dest = os.path.join(AAR_DIR, f"{base}.jar")
                if fetch(f"{repo}/{path}", dest):
                    staged = dest
                    break
        if staged is None:
            failed.append(base)
            continue

        # a KMP metadata jar or a POM-only stub carries no classes; shipping it
        # only bloats the APK and can collide with the real artifact
        n = subprocess.run(
            f"unzip -l {staged} | grep -c '\\.class$'",
            shell=True, capture_output=True, text=True,
        ).stdout.strip() or "0"
        if n == "0":
            # Multiplatform modules publish an empty metadata artifact under the
            # base coordinates and the real code under a -jvm / -android sibling.
            for suffix in ("-jvm", "-android"):
                sib = f"{a}{suffix}-{v}"
                got = None
                for repo in REPOS:
                    sp = f"{g.replace('.', '/')}/{a}{suffix}/{v}/{sib}.jar"
                    if fetch(f"{repo}/{sp}", os.path.join(AAR_DIR, f"{sib}.jar")):
                        got = os.path.join(AAR_DIR, f"{sib}.jar")
                        break
                if got is None:
                    for repo in REPOS:
                        sp = f"{g.replace('.', '/')}/{a}{suffix}/{v}/{sib}.aar"
                        sd = os.path.join(AAR_DIR, f"{sib}.aar")
                        if fetch(f"{repo}/{sp}", sd):
                            exs = os.path.join(EXTRACT_DIR, sib)
                            os.makedirs(exs, exist_ok=True)
                            subprocess.run(["unzip", "-o", "-q", sd, "-d", exs],
                                           capture_output=True)
                            if os.path.isfile(os.path.join(exs, "classes.jar")):
                                got = os.path.join(exs, "classes.jar")
                            break
                if got is None:
                    continue
                sn = subprocess.run(
                    f"unzip -l {got} | grep -c '\\.class$'",
                    shell=True, capture_output=True, text=True,
                ).stdout.strip() or "0"
                if sn != "0":
                    subprocess.run(["cp", got, dest_jar], check=True)
                    added.append(f"{base} -> {sib}")
                    staged = dest_jar
                    break
            if staged is None:
                empty.append(base)
                continue
        else:
            subprocess.run(["cp", staged, dest_jar], check=True)
            added.append(base)

    print(f"resolved {len(resolved)} transitive coordinates "
          f"-> {len(winners)} after conflict resolution")
    print(f"vendored {len(added)} jars into thirdparty/libs")
    if empty:
        print(f"skipped {len(empty)} empty (multiplatform metadata / stubs):")
        for e in empty:
            print("  " + e)
    if failed:
        print("FAILED to fetch:")
        for f in failed:
            print("  " + f)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())