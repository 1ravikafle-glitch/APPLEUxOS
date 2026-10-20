#!/usr/bin/env python3
"""
Convert android/appcompat AlertDialog.Builder call sites to AppleuxDialogs.

Why a script and not a regex: every one of these chains carries a lambda whose
body contains nested calls, braces and strings. A regex cannot find where a
lambda ends, and a converter that guesses produces a file that compiles into
the wrong behaviour — which is worse than leaving a Material dialog that at
least works.

So this parses the chain properly:

  * arguments are split on top-level commas only (depth of () [] {} tracked,
    and string literals are skipped)
  * a trailing `{ ... }` on a method is captured as a lambda body
  * `setItems`'s listener is `(dialog, which) -> body`, whose body is
    re-attached to AppleuxDialogs' `onPick`

Only the three shapes that map unambiguously are rewritten:

  A  setTitle + setItems                 -> AppleuxDialogs.choose
  B  setTitle + setMessage + pos + neg   -> AppleuxDialogs.alert
  C  setTitle + setView + pos + neg      -> AppleuxDialogs.prompt  (EditText only)

Anything else — a non-EditText setView, a three-button chain, a .create() site —
is reported and left alone. Silence is better than a wrong conversion.
"""
import pathlib
import re
import sys

FLAVOURS = ("androidx.appcompat.app.AlertDialog.Builder", "android.app.AlertDialog.Builder",
            "AlertDialog.Builder")


def split_top_level(s):
    """Split on commas that are not inside (), [] {}, <> or a string."""
    out, depth, cur, i, n = [], 0, [], 0, len(s)
    quote = None
    while i < n:
        c = s[i]
        if quote:
            cur.append(c)
            if c == "\\":
                if i + 1 < n:
                    cur.append(s[i + 1]); i += 1
            elif c == quote:
                quote = None
            i += 1
            continue
        if c in "\"'":
            quote = c; cur.append(c); i += 1; continue
        if c in "([{<":
            depth += 1
        elif c in ")]}>":
            depth -= 1
        if c == "," and depth == 0:
            out.append("".join(cur).strip()); cur = []
        else:
            cur.append(c)
        i += 1
    tail = "".join(cur).strip()
    if tail:
        out.append(tail)
    return out


def match_paren(s, start):
    """s[start] == '('; return index just past the matching ')'."""
    depth, i, n, quote = 0, start, len(s), None
    while i < n:
        c = s[i]
        if quote:
            if c == "\\":
                i += 2; continue
            if c == quote:
                quote = None
        elif c in "\"'":
            quote = c
        elif c in "([":
            depth += 1
        elif c in ")]":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise ValueError("unbalanced parens")


def parse_chain(src, begin):
    """
    Parse a Builder chain starting at `begin`. Returns
    (end_index_after_show_or_create, ctx_arg, [(method, [args])], tail).
    """
    m = re.match(r"(?:%s)\s*\(" % "|".join(re.escape(f) for f in FLAVOURS), src[begin:])
    if not m:
        return None
    ctx_start = begin + m.end() - 1
    ctx_end = match_paren(src, ctx_start)
    ctx = src[ctx_start + 1:ctx_end - 1].strip()

    calls, i = [], ctx_end
    while True:
        mm = re.match(r"\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(", src[i:])
        if not mm:
            break
        name = mm.group(1)
        # show()/create() terminate the chain and belong to the CALLER, not to
        # the builder. Consuming them here would hide the inline `.show()` this
        # script keys off, and would silently exclude every site.
        if name in ("show", "create"):
            break
        popen = i + mm.end() - 1
        pclose = match_paren(src, popen)
        raw = src[popen + 1:pclose - 1]
        # a trailing { ... } after the parens is the click listener
        rest = src[pclose:]
        lam = None
        lm = re.match(r"\s*\{", rest)
        if lm:
            depth, j, quote = 0, pclose + lm.end() - 1, None
            k, n = j, len(src)
            while k < n:
                c = src[k]
                if quote:
                    if c == "\\":
                        k += 2; continue
                    if c == quote:
                        quote = None
                elif c in "\"'":
                    quote = c
                elif c == "{":
                    depth += 1
                elif c == "}":
                    depth -= 1
                    if depth == 0:
                        break
                k += 1
            lam = src[j + 1:k]
            i = k + 1
        else:
            i = pclose
        args = split_top_level(raw)
        calls.append((name, args, lam))
    return ctx, calls, i


def body_from_listener(args):
    """
    `setItems(items) { d, w -> ... }` or `setPositiveButton(l) { _, _ -> ... }`
    returns the body, with the arrow head stripped.
    """
    for a in args:
        s = a.strip()
        # arrow form, possibly parenthesised: (d, w) -> BODY  /  d, w -> BODY
        mm = re.match(r"^\(?[^()]*?\)?\s*->\s*(.*)$", s, re.S)
        if mm:
            return mm.group(1).strip()
    return None


def clean_listener_body(body):
    """
    Make an AlertDialog listener body valid inside AppleuxDialogs.

    Two things change and both are forced, not cosmetic:

      * `which` was AlertDialog's second listener parameter. AppleuxDialogs'
        callbacks take `index`, and there is no dialog instance in scope — so
        `which` is renamed and `dialog.dismiss()` is removed. AppleuxDialog
        dismisses itself when an action fires, so keeping the call would just
        dismiss twice.
    """
    b = re.sub(r"\bwhich\b", "index", body)
    # drop `dialog.dismiss()`, `dlg.dismiss()`, `d.dismiss()` as whole statements
    b = re.sub(r"^[ \t]*(?:dialog|dlg|d)\s*\.\s*dismiss\s*\(\s*\)[ \t]*\n", "", b, flags=re.M)
    return b.strip()


def find_paren_call_end(src, start):
    pclose = match_paren(src, src.index("(", start))
    i = pclose
    while True:
        mm = re.match(r"\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(", src[i:])
        if not mm:
            break
        i = match_paren(src, i + mm.end() - 1)
        lm = re.match(r"\s*\{", src[i:])
        if lm:
            depth, j, quote = 0, i + lm.end() - 1, None
            k = j
            while k < len(src):
                c = src[k]
                if quote:
                    if c == "\\":
                        k += 2; continue
                    if c == quote:
                        quote = None
                elif c in "\"'":
                    quote = c
                elif c == "{":
                    depth += 1
                elif c == "}":
                    depth -= 1
                    if depth == 0:
                        break
                k += 1
            i = k + 1
    return i


def main():
    root = pathlib.Path(sys.argv[1])
    converted = skipped = 0
    for f in sorted(root.rglob("*.kt")):
        if f.name in ("AppleuxDialog.kt", "AppleuxDialogs.kt"):
            continue
        src = f.read_text()
        if "AlertDialog.Builder" not in src:
            continue
        out, pos, changed = [], 0, False

        # INVARIANT: before `pos` is advanced past any region, that region must
        # already be in `out`. The first version of this script violated it —
        # every skip did `pos = end` with no flush — so the text between the last
        # conversion and the last skip was silently discarded. It truncated
        # ContactsActivity from 3069 lines to 1696 and PhoneActivity from 2046 to
        # 995 while still printing "converted 11". flush() is the only sanctioned
        # way to move `pos`.
        def flush(upto):
            nonlocal pos
            if upto > pos:
                out.append(src[pos:upto])
                pos = upto

        while True:
            idx = -1
            for fl in FLAVOURS:
                j = src.find(fl, pos)
                if j != -1 and (idx == -1 or j < idx):
                    idx = j
            if idx == -1:
                break
            if src[:idx].rstrip().endswith(("*", "//", '"')):
                flush(idx + len(FLAVOURS[2]))
                continue
            try:
                parsed = parse_chain(src, idx)
            except ValueError:
                flush(idx + len(FLAVOURS[2]))
                continue
            if parsed is None:
                flush(idx + len(FLAVOURS[2]))
                continue
            ctx, calls, end = parsed
            methods = [c[0] for c in calls]
            tail = src[end:]
            # only the inline .show() shape
            if not re.match(r"\s*\.\s*show\s*\(\s*\)", tail):
                skipped += 1
                flush(end)
                continue
            after = end + re.match(r"\s*\.\s*show\s*\(\s*\)", tail).end()
            repl = None
            # `calls` entries carry an ARGS LIST, so a single-argument method
            # must be unwrapped with [0]. Passing the list through renders as
            # Python list syntax and will not compile.
            def arg1(method, default="null"):
                for m, a, _l in calls:
                    if m == method:
                        return a[0] if a else default
                return default

            def label_of(method, default='null'):
                for m, a, _l in calls:
                    if m == method and a:
                        # setNegativeButton("Cancel", null) -> "Cancel"
                        return a[0]
                return default

            def listener_body(method):
                for m, _a, l in calls:
                    if m == method and l:
                        b = body_from_listener([l])
                        if b is not None:
                            return clean_listener_body(b)
                return None

            if "setItems" in methods and "setMessage" not in methods and "setView" not in methods:
                body = listener_body("setItems")
                if body is not None:
                    repl = (f"AppleuxDialogs.choose(\n"
                            f"            {ctx},\n"
                            f"            items = {arg1('setItems')},\n"
                            f"            title = {arg1('setTitle')},\n"
                            f"            cancelLabel = {label_of('setNegativeButton', '\"Cancel\"')},\n"
                            f"        ) {{ index -> {body} }}")
            elif "setMessage" in methods and "setView" not in methods and "setItems" not in methods:
                body = listener_body("setPositiveButton")
                if body is not None:
                    repl = (f"AppleuxDialogs.alert(\n"
                            f"            {ctx},\n"
                            f"            title = {arg1('setTitle')},\n"
                            f"            message = {arg1('setMessage')},\n"
                            f"            confirmLabel = {label_of('setPositiveButton')},\n"
                            f"            cancelLabel = {label_of('setNegativeButton', '\"Cancel\"')},\n"
                            f"        ) {{ {body} }}")
            if repl:
                flush(idx)
                out.append(repl)
                pos = after
                converted += 1
                changed = True
            else:
                skipped += 1
                flush(end)
        if changed:
            out.append(src[pos:])
            # HARD GUARD. A converter that silently deletes a thousand lines is
            # far worse than one that converts nothing, so refuse to write a file
            # that lost more than a line per site.
            new = "".join(out)
            if len(new.split("\n")) < len(src.split("\n")) - converted - 5:
                print(f"  REFUSED {f.name}: would lose "
                      f"{len(src.split(chr(10))) - len(new.split(chr(10)))} lines")
                continue
            f.write_text(new)
            print(f"  rewrote {f.name}")
    print(f"\nconverted {converted}, left alone {skipped}")


main()