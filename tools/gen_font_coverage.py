#!/usr/bin/env python3
"""Generate assets/font_coverage.bin from the four bundled fonts.

For every codepoint, pick the first font (by fallback priority 1..4) whose cmap
maps it to a real glyph (not .notdef). Emit sorted, disjoint
[startCp, endCp, fontIdx] ranges (12 bytes each) so the app can binary-search at
runtime without calling Paint.hasGlyph (unreliable for large fonts on some ROMs).

Run from the repo root:  python tools/gen_font_coverage.py
"""
import os
import struct

from fontTools.ttLib import TTFont

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONTS = [
    ("app/src/main/assets/fonts/TumanPUA.ttf", 1),
    ("app/src/main/assets/fonts/LXGWWenKaiGBScreen.ttf", 2),
    ("app/src/main/assets/fonts/PlangothicP1.ttf", 3),
    ("app/src/main/assets/fonts/PlangothicP2.ttf", 4),
]
OUT = os.path.join(REPO, "app/src/main/assets/font_coverage.bin")


def covered_codepoints(font_path):
    font = TTFont(os.path.join(REPO, font_path), lazy=True)
    try:
        cmap = font.getBestCmap()
        return {cp for cp, glyph in cmap.items() if glyph != ".notdef"}
    finally:
        font.close()


def main():
    assigned = {}
    for path, idx in FONTS:
        for cp in covered_codepoints(path):
            assigned.setdefault(cp, idx)

    ranges = []
    for cp, idx in sorted(assigned.items()):
        if ranges and ranges[-1][1] + 1 == cp and ranges[-1][2] == idx:
            ranges[-1][1] = cp
        else:
            ranges.append([cp, cp, idx])

    with open(OUT, "wb") as fh:
        fh.write(struct.pack("<i", len(ranges)))
        for start, end, idx in ranges:
            fh.write(struct.pack("<IIBBH", start, end, idx, 0, 0))
    print("wrote %d ranges / %d codepoints -> %s" % (len(ranges), len(assigned), OUT))


if __name__ == "__main__":
    main()