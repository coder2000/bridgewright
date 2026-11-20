# Bridgewright

A Minecraft mod that adds a **Bridge Wand**: mark two points, and it builds a bridge between them
from the slabs and fences in your inventory.

Targets **Minecraft 1.20.1** on **Forge 47.4.0+**.

## Using the wand

| Action | Effect |
| --- | --- |
| **Use on a block** | First use marks the start, second use marks the end. A third use starts over. |
| **Use in the air** | Builds the bridge between the two marked points |
| **Sneak + use in the air** | Opens the settings screen: bridge type, width and boat clearance (stored on the wand) |
| **Sneak + use on a block** | Clears the marked points |

- Click the **top of the ground** at each end. The deck is built from slabs on top of the clicked blocks.
- When the end is marked, particles show the planned bridge (green = deck, white = fences,
  sparks = masonry) and you're told roughly what materials it needs. The wand glints while both
  points are set.

## Bridge types

| Type | What it builds | Extra materials |
| --- | --- | --- |
| **Flat** | A straight deck between the two points | — |
| **Arch** | The deck curves up to a high point in the middle (about a sixth of the length higher) | — |
| **Stone Arch** | An arch with a solid curved underside that meets the banks | Full blocks matching your slabs |
| **Trestle** | A row of fences under the deck every 4 blocks, with legs down to the ground or riverbed | Fences (required) |
| **Suspension** | Fence towers at each end with cables sagging between them and hangers every 3 blocks | Fences (required), width 3+ |

- **Stone Arch** finds the full block by the slab's name: stone brick slabs → stone bricks, oak
  slabs → oak planks, quartz slabs → quartz block, and so on. A raised half step becomes a full
  block, so the arch is solid all the way through.
- Trestle legs are only built where they reach solid ground within 64 blocks.

## Boat clearance

Set in the settings screen: Off, or 1–8 blocks (default 2, enough to sail under). Wherever the bridge
crosses water it's raised so there are that many blocks of air between the water and its underside,
ramping up from the ends at half a block per block. A stone arch keeps its opening that high, and a
trestle leaves a channel without legs in the middle of the widest stretch of water.

If the ends are too close to the water for the ramps to climb high enough, the bridge is still built
and you're warned; mark the ends further back from the water to fix it.

## Materials and placement

- **Materials**: the deck uses the first slab found (offhand first, then hotbar, then the rest of
  your inventory), and only that kind of slab. Railings use the first fence the same way; walls,
  glass panes and iron bars also work. If you're short, nothing is built and you're told how many
  you need.
- With no fences in your inventory, a flat or arch bridge is built without railings. Width 1 never
  has railings.
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

Bridge type, width and clearance are per wand, set in the settings screen.

## Building

```bash
./gradlew build
```

The jar is in `build/libs/`. Needs a JDK 17 (Gradle downloads one if needed).
`tools/make_texture.py` regenerates the item texture.
