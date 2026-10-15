#!/usr/bin/env python3
"""Harvest the official iOS 27 iPhone User Guide.

Apple publishes this guide publicly at support.apple.com. It is the project's
primary legal reference: Apple's own descriptions of iOS 27 behaviour, paired
with Apple's own screenshots (whose `alt` text describes the layout in Apple's
words). No Apple binaries, frameworks or artwork are collected - only the
public documentation and the documentation's own illustrative images.

Walk strategy: every page carries Previous/Next links, so the guide is a
doubly-linked list. Seed with the pages matching our known gaps, then follow
Next/Previous breadth-first to a cap.

Output:
    design-reference/ios27/official-guide/
        index.json          page -> {title, url, text file, images[]}
        pages/<slug>.md     article text (nav stripped)
        images/<id>.png     the guide's own images
"""
import concurrent.futures as cf
import html as htmllib
import json
import os
import re
import sys
import urllib.request

BASE = "https://support.apple.com/en-au/guide/iphone"
GUIDE = "/27/ios/27"
OUT = "/home/elfakiris/OsProject/design-reference/ios27/official-guide"
MAX_PAGES = int(sys.argv[1]) if len(sys.argv) > 1 else 60

SEEDS = [
    "access-features-from-the-lock-screen-iphcd5c65ccf",
    "use-and-customize-control-center-iph59095ec58",
    "view-and-respond-to-notifications-iph6534c01bc",
    "customize-your-home-screen-iph0e7d8b3d2",
    "add-edit-and-remove-widgets-iphb8f1bf206",
    "switch-between-open-apps-iph0c8b1c1d2",
    "organize-your-apps-in-folders-iph0e7d8b3d3",
    "find-and-use-your-apps-in-app-library-iph0c8b1c1d3",
    "adjust-the-screen-brightness-and-color-balance-iph0a1b2c3d4",
    "turn-on-lock-screen-features-iph9a2a69136",
]

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36"


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=45) as r:
        return r.read()


def strip_nav(text_lines):
    """The guide wraps the article in a ~1000-line nav; drop it.

    The article starts at the last occurrence of the page's own H1 and runs
    to the 'See also' / 'Previous' markers.
    """
    start = None
    for i in range(len(text_lines) - 1, -1, -1):
        if text_lines[i].startswith("See also") or text_lines[i] == "Previous":
            start = i
            break
    if start is None:
        start = int(len(text_lines) * 0.8)
    body = text_lines[:start]
    # walk back to the article heading: last line before body that also
    # appears in the first 40 nav lines is the title; drop everything before
    for i in range(len(body) - 1, -1, -1):
        if body[i] and body[i] in text_lines[:5]:
            return body[i:]
    return body[-250:]


def parse(url):
    raw = get(url).decode("utf-8", "replace")
    imgs = []
    for m in re.finditer(r'<img[^>]+src="(https://ipcdn-web\.apple\.com[^"]+)"[^>]*>', raw):
        tag = m.group(0)
        alt = re.search(r'alt="([^"]*)"', tag)
        imgs.append({"url": m.group(1), "alt": htmllib.unescape(alt.group(1)) if alt else ""})

    t = raw
    t = re.sub(r"<script[\s\S]*?</script>", " ", t)
    t = re.sub(r"<style[\s\S]*?</style>", " ", t)
    t = re.sub(r"<li[^>]*>", "\n- ", t)
    t = re.sub(r"</p>|<br\s*/?>", "\n", t)
    t = re.sub(r"<h[1-4][^>]*>", "\n## ", t)
    t = re.sub(r"<[^>]+>", "\n", t)
    t = htmllib.unescape(t)
    lines = [re.sub(r"\s+", " ", x).strip() for x in t.split("\n")]
    lines = [x for x in lines if x]
    body = strip_nav(lines)

    nav = []
    for m in re.finditer(r'href="([^"]*/27/ios/27)"[^>]*>\s*([^<]{1,90})\s*</a>', raw):
        nav.append({"href": m.group(1), "label": m.group(2).strip()})
    return body, imgs, nav


def main():
    os.makedirs(os.path.join(OUT, "pages"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "images"), exist_ok=True)

    queue, seen, index = [], set(), {}
    for s in SEEDS:
        queue.append((BASE + "/" + s + GUIDE, None))
    while queue and len(seen) < MAX_PAGES:
        url, label = queue.pop(0)
        if url in seen:
            continue
        seen.add(url)
        try:
            body, imgs, nav = parse(url)
        except Exception as e:
            index[url] = {"error": str(e)}
            continue
        slug = url.split("/guide/iphone/")[1].split("/")[0]
        title = body[0].lstrip("# ").strip() if body else slug
        md = "# " + title + "\n_source: " + url + "\n\n" + "\n".join(body[1:])
        if imgs:
            md += "\n\n## Guide images\n"
            for i, im in enumerate(imgs):
                if not im["alt"]:
                    continue
                md += "\n![%s](images/%s.png)\n\n*Apple's description:* %s\n" % (
                    im["alt"][:120], im["url"].rstrip("/").split("/")[-1], im["alt"])
        open(os.path.join(OUT, "pages", slug + ".md"), "w").write(md)
        index[url] = {"slug": slug, "title": title, "images": imgs}
        print("ok  %-58s %3d lines  %d imgs" % (slug[:58], len(body), len(imgs)), flush=True)

        # enqueue siblings from the guide's own nav
        for n in nav:
            h = n["href"]
            if "/guide/iphone/" not in h:
                h = "https://support.apple.com" + h if h.startswith("/") else None
                if not h:
                    continue
            if "/27/ios/27" in h and h not in seen:
                queue.append((h, n["label"]))

    json.dump(index, open(os.path.join(OUT, "index.json"), "w"), indent=1)
    print("\npages:", len(index), "->", OUT)


if __name__ == "__main__":
    main()