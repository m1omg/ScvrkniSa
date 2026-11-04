#!/usr/bin/env python3
"""Generates Shrink!'s icon set.

Every icon is a 24x24 line drawing made only of <path> elements, so the same
path data works for SVG (desktop) and Android vector drawables. Run:

    python3 tools/icons.py

It writes desktop/src/main/resources/.../icons/*.svg and
android/src/main/res/drawable/ic_*.xml.
"""
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESKTOP = os.path.join(ROOT, "desktop/src/main/resources/io/github/m1omg/shrink/desktop/icons")
ANDROID = os.path.join(ROOT, "android/src/main/res/drawable")


def circle(cx, cy, r):
    """A circle as two arcs (Android vectors have no <circle>)."""
    return f"M{cx - r:g} {cy:g}a{r:g} {r:g} 0 1 0 {2 * r:g} 0a{r:g} {r:g} 0 1 0 {-2 * r:g} 0z"


def gear(cx=12, cy=12, inner=7.0, outer=9.6, teeth=8, tooth=0.42):
    """Gear outline with straight-sided teeth."""
    pts = []
    step = 2 * math.pi / teeth
    for i in range(teeth):
        a = i * step - math.pi / 2
        a0, a1 = a - step / 2, a + step / 2
        t0, t1 = a - tooth / 2 * step, a + tooth / 2 * step
        # valley -> rise -> tooth top -> fall
        pts.append(("A", inner, a0, t0 - 0.06))
        pts.append(("L", outer, t0 + 0.03))
        pts.append(("A", outer, t0 + 0.03, t1 - 0.03))
        pts.append(("L", inner, t1 + 0.06))
    d = []
    first = True
    for kind, r, *ang in pts:
        if kind == "A":
            s, e = ang
            x0, y0 = cx + r * math.cos(s), cy + r * math.sin(s)
            x1, y1 = cx + r * math.cos(e), cy + r * math.sin(e)
            if first:
                d.append(f"M{x0:.2f} {y0:.2f}")
                first = False
            else:
                d.append(f"L{x0:.2f} {y0:.2f}")
            d.append(f"A{r:.2f} {r:.2f} 0 0 1 {x1:.2f} {y1:.2f}")
        else:
            (e,) = ang
            d.append(f"L{cx + r * math.cos(e):.2f} {cy + r * math.sin(e):.2f}")
    d.append("Z")
    return "".join(d)


# name -> list of (path, filled)
ICONS = {
    "compress": [("M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7", False)],
    "trim": [(circle(6, 6, 3), False), (circle(6, 18, 3), False),
             ("M20 4L8.12 15.88M14.47 14.48L20 20M8.12 8.12L12 12", False)],
    "rotate": [("M21 12a9 9 0 1 1-2.64-6.36L21 8M21 3v5h-5", False)],
    "rotate_left": [("M3 12a9 9 0 1 0 2.64-6.36L3 8M3 3v5h5", False)],
    "flip": [("M3 12a9 9 0 0 1 15.36-6.36L21 8M21 3v5h-5M21 12a9 9 0 0 1-15.36 6.36L3 16M3 21v-5h5", False)],
    "mute": [("M11 5L6 9H2v6h4l5 4V5z", False), ("M22 9l-6 6M16 9l6 6", False)],
    "audio": [("M9 18V5l12-2v13", False), (circle(6, 18, 3), False), (circle(18, 16, 3), False)],
    "convert": [("M16 3l4 4-4 4M20 7H4M8 21l-4-4 4-4M4 17h16", False)],
    "resolution": [("M8 3H5a2 2 0 0 0-2 2v3M21 8V5a2 2 0 0 0-2-2h-3M3 16v3a2 2 0 0 0 2 2h3M16 21h3a2 2 0 0 0 2-2v-3",
                    False)],
    "framerate": [("M12 14l4-4M3.34 19a10 10 0 1 1 17.32 0", False)],
    "info": [(circle(12, 12, 10), False), ("M12 16v-5M12 8h.01", False)],
    "history": [("M3 12a9 9 0 1 0 2.64-6.36L3 8M3 3v5h5M12 7v5l4 2", False)],
    "settings": [(gear(), False), (circle(12, 12, 3), False)],
    "folder": [("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", False)],
    "play": [("M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5z", True)],
    "pause": [("M6 4h4v16H6zM14 4h4v16h-4z", True)],
    "check": [("M20 6L9 17l-5-5", False)],
    "check_circle": [(circle(12, 12, 10), False), ("M8 12.5l2.5 2.5L16 9.5", False)],
    "alert": [("M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z", False),
              ("M12 9v4M12 17h.01", False)],
    "error": [(circle(12, 12, 10), False), ("M15 9l-6 6M9 9l6 6", False)],
    "film": [("M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", False),
             ("M7 3v18M17 3v18M3 7.5h4M3 12h18M3 16.5h4M17 7.5h4M17 16.5h4", False)],
    "upload": [("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12", False)],
    "chevron_down": [("M6 9l6 6 6-6", False)],
    "chevron_right": [("M9 6l6 6-6 6", False)],
    "chevron_left": [("M15 6l-6 6 6 6", False)],
    "external": [("M15 3h6v6M10 14L21 3M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6", False)],
    "search": [(circle(11, 11, 7), False), ("M21 21l-4.35-4.35", False)],
    "trash": [("M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6", False)],
    "eye": [("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", False), (circle(12, 12, 3), False)],
    "sparkles": [("M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z", False),
                 ("M19 15l.7 1.8 1.8.7-1.8.7L19 20l-.7-1.8-1.8-.7 1.8-.7z", False)],
    "menu": [("M4 6h16M4 12h16M4 18h16", False)],
    "plus": [("M12 5v14M5 12h14", False)],
    "share": [("M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7M16 6l-4-4-4 4M12 2v13", False)],
    "close": [("M18 6L6 18M6 6l12 12", False)],
    "zoom": [(circle(11, 11, 7), False), ("M21 21l-4.35-4.35M11 8v6M8 11h6", False)],
    "video": [("M3 6a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", False),
              ("M17 10l4-2.5v9L17 14", False)],
    "tools": [("M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z", False)],
}


def svg(paths, stroke=1.8):
    body = []
    for d, filled in paths:
        if filled:
            body.append(f'  <path d="{d}" fill="#000000"/>')
        else:
            body.append(f'  <path d="{d}" fill="none" stroke="#000000" stroke-width="{stroke}" '
                        f'stroke-linecap="round" stroke-linejoin="round"/>')
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">\n'
            + "\n".join(body) + "\n</svg>\n")


def vector(paths, stroke=1.8):
    body = []
    for d, filled in paths:
        if filled:
            body.append(f'    <path android:fillColor="#FFFFFFFF" android:pathData="{d}"/>')
        else:
            body.append(f'    <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" '
                        f'android:strokeWidth="{stroke}" android:strokeLineCap="round" '
                        f'android:strokeLineJoin="round" android:pathData="{d}"/>')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by tools/icons.py, do not edit. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp"\n'
            '    android:viewportWidth="24" android:viewportHeight="24"\n'
            '    android:tint="?attr/colorControlNormal">\n'
            + "\n".join(body) + "\n</vector>\n")


LOGO_CORNERS = ("M300 400V330a30 30 0 0 1 30-30h70M624 300h70a30 30 0 0 1 30 30v70"
                "M724 624v70a30 30 0 0 1-30 30h-70M400 724h-70a30 30 0 0 1-30-30v-70")
LOGO_TRIANGLE = "M450 395v234a18 18 0 0 0 27 15.6l196-117a18 18 0 0 0 0-31.2l-196-117A18 18 0 0 0 450 395z"


def logo_svg():
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#7B5CFF"/>
      <stop offset="0.55" stop-color="#4F7BFF"/>
      <stop offset="1" stop-color="#22C7F0"/>
    </linearGradient>
    <linearGradient id="shine" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.28"/>
      <stop offset="0.5" stop-color="#FFFFFF" stop-opacity="0"/>
    </linearGradient>
  </defs>
  <rect x="64" y="64" width="896" height="896" rx="200" fill="url(#bg)"/>
  <rect x="64" y="64" width="896" height="896" rx="200" fill="url(#shine)"/>
  <path d="{LOGO_CORNERS}" fill="none" stroke="#FFFFFF" stroke-width="56" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="{LOGO_TRIANGLE}" fill="#FFFFFF"/>
</svg>
'''


def main():
    os.makedirs(DESKTOP, exist_ok=True)
    for name, paths in ICONS.items():
        with open(os.path.join(DESKTOP, name + ".svg"), "w") as f:
            f.write(svg(paths))
    with open(os.path.join(DESKTOP, "logo.svg"), "w") as f:
        f.write(logo_svg())
    if os.path.isdir(os.path.dirname(ANDROID)):
        os.makedirs(ANDROID, exist_ok=True)
        for name, paths in ICONS.items():
            with open(os.path.join(ANDROID, "ic_" + name + ".xml"), "w") as f:
                f.write(vector(paths))
    print(f"wrote {len(ICONS)} icons")


if __name__ == "__main__":
    main()
