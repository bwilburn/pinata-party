#!/usr/bin/env python3
"""Writes the game-test arena: 7x6x7, stone floor and ceiling, air between."""
from pathlib import Path
import nbtlib
from nbtlib.tag import Compound, List, Int, String

SX, SY, SZ = 7, 6, 7
blocks = []
for x in range(SX):
    for y in range(SY):
        for z in range(SZ):
            state = 1 if y in (0, SY - 1) else 0
            blocks.append(Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(state)}))
root = Compound({
    "DataVersion": Int(3955),  # Minecraft 1.21.1
    "size": List[Int]([Int(SX), Int(SY), Int(SZ)]),
    "palette": List[Compound]([Compound({"Name": String("minecraft:air")}),
                               Compound({"Name": String("minecraft:stone")})]),
    "blocks": List[Compound](blocks),
    "entities": List[Compound]([]),
})
out = Path(__file__).resolve().parent.parent / "src/main/resources/data/pinataparty/structure/arena.nbt"
nbtlib.File(root, root_name="").save(out, gzipped=True)
print("wrote", out)
