"""Bottled-critter icons: one shared bottle, a 6x6 critter inside. Imported by make_textures.py."""
FRAME = [
    "................",
    "......cccc......",
    "......cwwc......",
    ".......gg.......",
    "......g..g......",
    ".....g....g.....",
    "....g......g....",
    "....g......g....",
    "....g......g....",
    "....g......g....",
    "....g......g....",
    "....g......g....",
    ".....gggggg.....",
    "................",
    "................",
    "................",
]
FRAME_KEY = {"c": "#8a5a2b", "w": "#b07a40", "g": "#cfe8f5"}

# 6x6 interiors drawn at rows 6-11, cols 5-10 ('.' = glass, shows a faint tint)
CRITTERS = {
    "bee": (["......",
             ".YKYK.",
             "WYKYKs",
             ".YKYK.",
             "..W...",
             "......"],
            {"Y": "#ffcf1a", "K": "#2a1a0a", "W": "#e8f6ff", "s": "#3a2a10"}),
    "chicken": (["...R..",
                 "..WWO.",
                 "..WKW.",
                 "WWWWW.",
                 ".WWWW.",
                 "..O.O."],
                {"W": "#f4f4f0", "R": "#d8282a", "O": "#f2b21a", "K": "#111111"}),
    "rabbit": ([".T..T.",
                ".T..T.",
                ".TTTT.",
                ".TKTK.",
                "TTTPTT",
                ".TTTT."],
               {"T": "#a67c52", "K": "#111111", "P": "#e88aa0"}),
    "bat": (["......",
             "D.BB.D",
             "DDBBDD",
             "DDRRDD",
             "D.BB.D",
             "......"],
            {"D": "#3b2a20", "B": "#5a4232", "R": "#c8302a"}),
    "frog": ([".G..G.",
              "GKGGKG",
              "GGGGGG",
              "GLLLLG",
              ".GGGG.",
              "G....G"],
             {"G": "#6a9a3a", "K": "#111111", "L": "#d8c89a"}),
    "slime": (["......",
               ".SSSS.",
               ".SKSK.",
               ".SSSS.",
               ".SKKS.",
               ".SSSS."],
              {"S": "#7fcf5a", "K": "#2e6a2a"}),
    "silverfish": (["......",
                    ".L.L..",
                    "GHGHGK",
                    "HGHGHG",
                    ".L.L..",
                    "......"],
                   {"G": "#4f585c", "H": "#7d888d", "K": "#111111", "L": "#3a4245"}),
}

def critter_icon(name, hexc, Image):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(FRAME):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), hexc(FRAME_KEY[ch]))
    # faint glass tint inside, a highlight, then the critter
    for y in range(5, 12):
        for x in range(5, 11):
            if FRAME[y][x] == ".":
                img.putpixel((x, y), hexc("#cfe8f5", 70))
    art, key = CRITTERS[name]
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((5 + x, 6 + y), hexc(key[ch]))
    img.putpixel((10, 11), hexc("#ffffff"))
    return img


def generic_icon(hexc, Image):
    """For mobs added by datapack that have no icon of their own: a shadowy something."""
    img = critter_icon("bee", hexc, Image)
    for y in range(6, 12):
        for x in range(5, 11):
            img.putpixel((x, y), hexc("#cfe8f5", 70))
    for (x, y) in [(6, 8), (7, 7), (8, 7), (9, 8), (6, 9), (7, 9), (8, 9), (9, 9), (7, 10), (8, 10)]:
        img.putpixel((x, y), hexc("#4a4a5a"))
    img.putpixel((7, 8), hexc("#f0e060"))
    img.putpixel((8, 8), hexc("#f0e060"))
    img.putpixel((10, 11), hexc("#ffffff"))
    return img
