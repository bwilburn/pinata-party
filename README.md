# Piñata Party

A NeoForge mod for **Minecraft 1.21.1 / NeoForge 21.1.266+** (the Wilburn Vanilla+ server).
It goes on the **server and every client**: it adds an entity and items, so a client without it can't join.

## What's in it

| Thing | How you get it | What it does |
|---|---|---|
| **Piñata** | `" S "`, `"WPW"`, `"W W"` — S string, W any wool, P paper | Right-click the **underside of a block** to hang it on a string (anywhere else, it hovers). Three random color schemes. |
| **Piñata Bat** | log above a stick | Hits for 5 (a wooden sword does 4), slower swing, extra knockback. 250 uses. |
| **Blindfold** | `"SWS"` — S string, W black wool | Wear it on your head. Your screen goes almost black (third-person view too, so F5 won't help). Putting it on spins you to face a random direction. |
| **Bottled Bee** | Use an empty glass bottle on a bee | Use it on a piñata to put the bee inside, or on a block to let it go. |

### Filling a piñata
- Right-click a hung piñata with **anything** to stuff the whole stack inside (up to 27 stacks).
- Right-click with a **Bottled Bee**, a **bee spawn egg**, or a **Silk-Touch bee nest/beehive** that has bees in it to add bees (up to 12).
- Right-click with an empty hand to squeeze it: it tells you how much is inside.
- **Sneak + right-click** with an empty hand to take it down. It keeps its filling, so you can stuff it at home and hang it at the party.

### Breaking it
- Only a player's swing counts (no arrows, lava or explosions). Each piñata has a random toughness of 15–30: a fist does 1, the bat about 5.
- When it breaks: confetti, a bang, the items fly out, and the bees come out **angry** for 15 seconds, each going after a random survival player within 10 blocks.
- By default a piñata bee can't kill anyone: a sting never takes you below half a heart (the server is on Hard and there are no graves).
- An empty piñata drops a few cookies.

## Server settings
`world/serverconfig/pinataparty-server.toml`, created on first start. Synced to clients, so the blindfold's darkness is the server's call.

| Setting | Default |
|---|---|
| `bees.angryBees` | `true` |
| `bees.beeTargets` | `EVERYONE` (or `BREAKER`) |
| `bees.beeAngerSeconds` | `15` |
| `bees.partyBeesCanKill` | `false` |
| `bees.maxBees` | `12` |
| `pinata.maxStacks` | `27` |
| `pinata.minToughness` / `maxToughness` | `15` / `30` |
| `blindfold.opacity` | `0.96` (1.0 = pitch black) |
| `blindfold.spinOnPutOn` | `true` |

## Building
GitHub Actions builds every push; the jar is the `pinataparty-jar` artifact on the run.
Locally: JDK 21, then `./gradlew build` → `build/libs/pinataparty-1.0.1.jar`.
Textures are drawn by `tools/make_textures.py` (Pillow); edit the palettes or pixel art there and re-run it.
