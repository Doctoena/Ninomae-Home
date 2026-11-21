#!/usr/bin/env python3
"""
Trim the Material Symbols font to the icons the app uses.

The full font (tools/fonts/material_symbols_rounded.ttf, ~14.6 MB, ~3,800 icons) would make up most of
the APK and be loaded whole at start. Icons are drawn by name through the font's ligatures
(Glyph("search")), so this scans the Kotlin sources for every lowercase string literal, keeps the
icons whose names match, and writes the trimmed font to app/src/main/res/font.

Run it again after using an icon that wasn't used before:  python3 tools/subset_symbols.py
Needs fontTools (pip install fonttools).
"""
import pathlib
import re
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
FULL = ROOT / "tools/fonts/material_symbols_rounded.ttf"
OUT = ROOT / "app/src/main/res/font/material_symbols_rounded.ttf"
SOURCES = ROOT / "app/src/main/java"


def ligatures(font):
    """Icon name -> glyph name, read from the font's ligature table."""
    cmap = font.getBestCmap()
    char_of = {g: chr(c) for c, g in cmap.items()}
    out = {}
    for lookup in font["GSUB"].table.LookupList.Lookup:
        for st in lookup.SubTable:
            if st.LookupType == 7:  # extension: the real subtable is inside
                st = st.ExtSubTable
            if getattr(st, "LookupType", None) != 4:
                continue
            for first, ligs in st.ligatures.items():
                for lig in ligs:
                    chars = [char_of.get(g) for g in [first] + list(lig.Component)]
                    if None not in chars:
                        out["".join(chars)] = lig.LigGlyph
    return out


def swaps(font):
    """Glyph -> the glyphs it can be swapped for (the filled shape when Fill is on, and the like)."""
    out = {}
    for lookup in font["GSUB"].table.LookupList.Lookup:
        for st in lookup.SubTable:
            if st.LookupType == 7:
                st = st.ExtSubTable
            t = getattr(st, "LookupType", None)
            if t == 1:
                for a, b in st.mapping.items():
                    out.setdefault(a, set()).add(b)
            elif t == 3:
                for a, alts in st.alternates.items():
                    out.setdefault(a, set()).update(alts)
    return out


def used_names():
    """Every lowercase string literal in the app's sources: icon names are among them."""
    names = set()
    for f in SOURCES.rglob("*.kt"):
        names |= set(re.findall(r'"([a-z][a-z0-9_]+)"', f.read_text()))
    return names


def main():
    font = TTFont(FULL)
    ligs = ligatures(font)
    wanted = sorted(n for n in used_names() if n in ligs)
    cmap = font.getBestCmap()
    # The letters the names are typed with, so the ligatures still form.
    letters = [cmap[ord(c)] for c in "abcdefghijklmnopqrstuvwxyz0123456789_ " if ord(c) in cmap]
    icons = {ligs[n] for n in wanted}
    # Each icon's other shapes (filled, and so on) come along, but not the icons the letters could spell.
    more = swaps(font)
    todo = list(icons)
    while todo:
        for g in more.get(todo.pop(), ()):
            if g not in icons:
                icons.add(g)
                todo.append(g)
    glyphs = sorted(icons | set(letters) | {".notdef"})

    opts = subset.Options()
    opts.layout_closure = False  # don't pull in every icon the letters could spell
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.name_languages = ["*"]
    opts.notdef_outline = True
    opts.glyph_names = False
    s = subset.Subsetter(opts)
    s.populate(glyphs=glyphs)
    s.subset(font)
    font.save(OUT)
    print(f"{len(wanted)} icons kept, {FULL.stat().st_size // 1024} KB -> {OUT.stat().st_size // 1024} KB", file=sys.stderr)


if __name__ == "__main__":
    main()
