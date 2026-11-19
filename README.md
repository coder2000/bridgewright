# Bridgewright

A Minecraft mod that adds a **Bridge Wand**: mark two points, and it builds a bridge between them
from the slabs and fences in your inventory.

Targets **Minecraft 1.20.1** on **Forge 47.4.0+**.

## Using the wand

| Action | Effect |
| --- | --- |
| **Use on a block** | First use marks the start, second use marks the end. A third use starts over. |
| **Use in the air** | Builds the bridge between the two marked points |
| **Sneak + use in the air** | Cycles the width: 1 → 3 → 5 → 7 → 1 (stored on the wand, default 3) |
| **Sneak + use on a block** | Clears the marked points |

- Click the **top of the ground** at each end. The deck is built from slabs on top of the clicked blocks.
- When the end is marked, particles show the planned deck (green = walkway, white = railing).
  The wand glints while both points are set.
- **Materials**: the deck uses the first slab found (offhand first, then hotbar, then the rest of
  your inventory), and only that kind of slab. Railings use the first fence the same way; walls,
  glass panes and iron bars also work. If you're short, nothing is built and you're told how many
  you need.
- With no fences in your inventory, the bridge is built without railings. Width 1 never has railings.
- Ends at different heights make a **ramp** using top and bottom slabs, rising half a block at a time.
  You're warned if it climbs more than half a block per block (too steep to walk).
- Bridges can run at any angle, not just straight along X or Z.
- Only air, water, grass and other replaceable blocks are filled; anything solid in the way is left
  alone. Slabs and fences placed in water are waterlogged.
- Respects spawn protection and claim/protection mods (fires Forge's block place event).
- Creative mode doesn't use up materials, and falls back to oak if you carry none.

### Recipe

Shapeless: **stick + string + any wooden slab + any wooden fence**.

## Configuration

`config/bridgewright-common.toml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `maxLength` | 128 | Longest bridge, in blocks of horizontal distance |
| `maxWidth` | 7 | Widest the wand can be set to (odd widths, including railings) |

## Building

```bash
./gradlew build
```

The jar is in `build/libs/`. Needs a JDK 17 (Gradle downloads one if needed).
`tools/make_texture.py` regenerates the item texture.
