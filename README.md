# Piñata Party

A NeoForge mod for **Minecraft 1.21.1 / NeoForge 21.1.266+** (the Wilburn Vanilla+ server).
It goes on the **server and every client**: it adds an entity and items, so a client without it can't join.

## What's in it

| Thing | How you get it | What it does |
|---|---|---|
| **Piñata** | `" S "`, `"WPW"`, `"W W"` — S string, W any wool, P paper | Right-click the **underside of a block** to hang it on a string (anywhere else, it hovers). Three random color schemes. |
| **Piñata Bat** | log above a stick | Hits for 5 (a wooden sword does 4), slower swing, extra knockback. 250 uses. |
| **Blindfold** | `"SWS"` — S string, W black wool | Wear it on your head. Your screen goes almost black (third-person view too, so F5 won't help). Putting it on spins you to face a random direction. |
| **Bottled Critter** | Use an empty glass bottle on a bee, chicken, rabbit, bat, frog, small slime or silverfish | Use it on a piñata to put the critter inside, or on a block to let it go. It keeps its name, age and variant. Pets can't be bottled. |

### Filling a piñata
- Right-click a hung piñata with **anything** to stuff the whole stack inside (up to 27 stacks).
- Right-click with a **Bottled Critter**, a spawn egg for one of the bottleable mobs, or a **Silk-Touch bee nest/beehive** with bees in it to add critters (up to 12, any mix).
- **Firework rockets** inside launch when it bursts instead of dropping (up to 16; the rest drop as items). They can't hurt anyone.
- Right-click with an empty hand to squeeze it. Squeezing it, or adding anything, shows how full it is: `Piñata: 5/27 stacks (214 items), 4/12 critters: 3 Bee, 1 Chicken`.
- **Sneak + right-click** with an empty hand to take it down. It keeps its filling, so you can stuff it at home and hang it at the party.
- Which mobs can be bottled is the entity-type tag `pinataparty:bottleable`; add modded mobs with a datapack.

### Breaking it
- Only a player's swing counts (no arrows, lava or explosions). Each piñata has a random toughness of 15–30: a fist does 1, the bat about 5.
- When it breaks: confetti, a papery pop, the items fly out, the rockets go up, and the critters burst out.
  - **Bees** come out angry for 15 seconds, each going after a random survival player within 10 blocks. They don't lose their stinger: they keep stinging (with a short dose of poison) until they calm down.
  - **Chickens and rabbits** run around in a panic. **Frogs and slimes** just hop out. All four stay afterwards as ordinary mobs.
  - **Bees, bats and silverfish** vanish in a puff 20 seconds after they calm down (tag `pinataparty:leaves_after_party`). Piñata silverfish can't hide in stone.
- By default nothing from a piñata can kill anyone: it never takes you below half a heart (the server is on Hard and there are no graves).
- An empty piñata just pops: nothing comes out.

## Server settings
`world/serverconfig/pinataparty-server.toml`, created on first start. Synced to clients, so the blindfold's darkness is the server's call.

| Setting | Default |
|---|---|
| `bees.angryBees` | `true` |
| `bees.beeTargets` | `EVERYONE` (or `BREAKER`) |
| `bees.beeAngerSeconds` | `15` |
| `bees.leaveAfterSeconds` | `20` (0 = they stay) |
| `bees.partyCrittersCanKill` | `false` |
| `bees.maxCritters` | `12` |
| `pinata.maxStacks` | `27` |
| `pinata.maxFireworks` | `16` |
| `pinata.minToughness` / `maxToughness` | `15` / `30` |
| `blindfold.opacity` | `0.96` (1.0 = pitch black) |
| `blindfold.spinOnPutOn` | `true` |

## Building
GitHub Actions builds every push; the jar is the `pinataparty-jar` artifact on the run.
Locally: JDK 21, then `./gradlew build` → `build/libs/pinataparty-1.1.0.jar`.
Textures are drawn by `tools/make_textures.py` (Pillow); edit the palettes or pixel art there and re-run it.
