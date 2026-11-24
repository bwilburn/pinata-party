#!/usr/bin/env python3
"""Draws every texture in the mod. Run from the project root:  python3 tools/make_textures.py
Needs Pillow. Writes into src/main/resources/assets/pinataparty/textures/."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/pinataparty/textures"

def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])

PALETTES = [
    # 0: classic rainbow
    ["#ff4f9a", "#ffb000", "#ffe94a", "#4fd46a", "#2fb8ff", "#9a5cff"],
    # 1: watermelon / pastel
    ["#ff6f91", "#ffc2d1", "#7ee0b0", "#2ea36b", "#fff3b0", "#ff9f68"],
    # 2: sunset
    ["#ff3d3d", "#ff8c1a", "#ffd23f", "#ff5fa2", "#b44cff", "#ff8c1a"],
]

# Model boxes: (u, v, w, h, d) matching PinataModel.createBodyLayer
BOXES = {
    "body": (0, 0, 7, 8, 12),
    "neck": (40, 0, 4, 9, 4),
    "head": (0, 21, 6, 5, 8),
    "ear_l": (30, 21, 2, 3, 1),
    "ear_r": (38, 21, 2, 3, 1),
    "tail": (30, 27, 2, 2, 4),
    "leg_f": (0, 36, 3, 5, 3),
    "leg_b": (14, 36, 3, 5, 3),
}
STRING = (58, 0, 1, 16, 1)

def box_faces(u, v, w, h, d):
    """Yields (name, x0, y0, fw, fh) for the six faces of a Minecraft cube UV layout."""
    yield "top", u + d, v, w, d
    yield "bottom", u + d + w, v, w, d
    yield "east", u, v + d, d, h
    yield "north", u + d, v + d, w, h
    yield "west", u + d + w, v + d, d, h
    yield "south", u + d + w + d, v + d, w, h

def pinata_texture(pal):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    cols = [hexc(c) for c in pal]
    for name, (u, v, w, h, d) in BOXES.items():
        for face, x0, y0, fw, fh in box_faces(u, v, w, h, d):
            for y in range(fh):
                for x in range(fw):
                    if face in ("top", "bottom"):
                        band = (x0 + x + y) // 2  # diagonal stripes on top and bottom
                        c = cols[band % len(cols)]
                    else:
                        gy = y0 + y
                        band = gy // 2           # 2 px horizontal stripes on the sides
                        c = cols[band % len(cols)]
                        # crepe-paper fringe: the stripe above hangs down in a zigzag
                        if gy % 2 == 0 and (x0 + x) % 2 == 0 and y > 0:
                            c = shade(cols[(band - 1) % len(cols)], 0.92)
                    if name.startswith("leg") and face != "top" and y >= fh - 1:
                        c = hexc("#3a2a1a")  # little dark hooves
                    px[x0 + x, y0 + y] = c
    # eyes on the head's front face
    u, v, w, h, d = BOXES["head"]
    fx, fy = u + d, v + d
    for ex in (fx + 1, fx + w - 2):
        px[ex, fy + 1] = hexc("#ffffff")
        px[ex, fy + 2] = hexc("#111111")
    # a smile
    for sx in range(fx + 2, fx + w - 2):
        px[sx, fy + 4] = hexc("#7a1030")
    # the string
    u, v, w, h, d = STRING
    for face, x0, y0, fw, fh in box_faces(u, v, w, h, d):
        for y in range(fh):
            for x in range(fw):
                px[x0 + x, y0 + y] = hexc("#e8dcc0") if (y0 + y) % 3 else hexc("#cbbd9c")
    return img

def from_art(art, key):
    h = len(art)
    w = len(art[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), hexc(key[ch]))
    return img

# 16x16 item icons ----------------------------------------------------------

PINATA_ICON = [
    "................",
    "...ee...........",
    "..pppppp........",
    ".pKppppp........",
    ".ooooooo........",
    "....yyyy........",
    "....gggg........",
    "....bbbbbbbbbbb.",
    "....ppppppppppp.",
    "....oooooooooooo",
    "....yyyyyyyyyyy.",
    "....ggggggggggg.",
    ".....vv.....vv..",
    ".....pp.....pp..",
    ".....hh.....hh..",
    "................",
]
PINATA_KEY = {"p": "#ff4f9a", "o": "#ffb000", "y": "#ffe94a", "g": "#4fd46a", "b": "#2fb8ff",
              "v": "#9a5cff", "e": "#ff8ac0", "K": "#111111", "h": "#3a2a1a"}

BAT_ICON = [
    "...........ddd..",
    "..........dLLLd.",
    ".........dLLLLd.",
    "........dLLLLLd.",
    ".......dLLLLLd..",
    "......dLLLLLd...",
    ".....dLLLLd.....",
    "....dLLLd.......",
    "...dLLd.........",
    "..dGGd..........",
    "..dGGd..........",
    ".dGGd...........",
    ".dGd............",
    "dKKd............",
    "dKd.............",
    ".d..............",
]
BAT_KEY = {"d": "#4a2e14", "L": "#c08a4a", "G": "#d23a3a", "K": "#2a2a2a"}

BLINDFOLD_ICON = [
    "................",
    "................",
    "................",
    "................",
    "..kkkkkkkkkkk...",
    ".kKKKKKKKKKKKk..",
    ".kKSKKKKKKKSKk..",
    ".kKKKKKKKKKKKkk.",
    "..kkkkkkkkkkk.kk",
    "..............k.",
    ".............kk.",
    "................",
    "................",
    "................",
    "................",
    "................",
]
BLINDFOLD_KEY = {"k": "#111118", "K": "#2a2a3a", "S": "#44445a"}

BOTTLE_ICON = [
    "................",
    "......cccc......",
    "......cwwc......",
    ".......gg.......",
    "......g..g......",
    ".....g....g.....",
    "....g.WW...g....",
    "....gYKYKY.g....",
    "....gYKYKYsg....",
    "....g.YKY..g....",
    "....g......g....",
    "....g.....hg....",
    ".....gggggg.....",
    "................",
    "................",
    "................",
]
BOTTLE_KEY = {"c": "#8a5a2b", "w": "#b07a40", "g": "#cfe8f5", "W": "#e8f6ff", "Y": "#ffcf1a",
              "K": "#2a1a0a", "s": "#3a2a10", "h": "#ffffff"}

def blindfold_armor():
    """64x32 helmet layer: a cloth band around the head at eye level, knot at the back."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    band = hexc("#1c1c26")
    hi = hexc("#2e2e40")
    dark = hexc("#111118")
    # side faces of the head cube are at v = 8..15; eyes sit on rows 11-13
    for x in range(0, 32):
        for y in (11, 12, 13):
            c = band
            if y == 11:
                c = hi
            elif y == 13 and x % 2 == 0:
                c = dark
            img.putpixel((x, y), c)
    # knot and two tails on the back face (u 24..31)
    for (x, y) in [(27, 10), (28, 10), (27, 14), (28, 14), (26, 15), (29, 15)]:
        img.putpixel((x, y), dark)
    return img

def main():
    (ROOT / "entity/pinata").mkdir(parents=True, exist_ok=True)
    (ROOT / "item").mkdir(parents=True, exist_ok=True)
    (ROOT / "models/armor").mkdir(parents=True, exist_ok=True)
    for i, pal in enumerate(PALETTES):
        pinata_texture(pal).save(ROOT / f"entity/pinata/pinata_{i}.png")
    from_art(PINATA_ICON, PINATA_KEY).save(ROOT / "item/pinata.png")
    from_art(BAT_ICON, BAT_KEY).save(ROOT / "item/pinata_bat.png")
    from_art(BLINDFOLD_ICON, BLINDFOLD_KEY).save(ROOT / "item/blindfold.png")
    from_art(BOTTLE_ICON, BOTTLE_KEY).save(ROOT / "item/bottled_bee.png")
    blindfold_armor().save(ROOT / "models/armor/blindfold_layer_1.png")
    print("textures written to", ROOT)

if __name__ == "__main__":
    main()
