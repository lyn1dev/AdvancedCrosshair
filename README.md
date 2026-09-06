# 🎯 Advanced Crosshair 

Advanced Crosshair is a lightweight client-side mod that changes the color of your crosshair based on your ability to hit or critical hit an enemy player:

- 🔴 **Red crosshair** when you're in range and can land a **regular hit**. (Sprint hit, sweep hit)

- 🔵 **Blue crosshair** when you're able to land a **critical hit** (falling, midair, etc).

## 🧩 Features

- **Compatible with ANY texturepack.** The crosshair that changes color is the one your active resource pack provides — the mod tints the vanilla sprite instead of drawing its own shape, so pack artwork is preserved. Unlike Crosshair Indicator.

- Uses the **vanilla attack indicator** exactly as the game draws it.

- Fully client-side. No configuration. No dependancies.

## Configuration

Open the settings from **Mod Menu**, or edit `config/advancedcrosshair.json` by hand.

| Setting | Default | Notes |
|---|---|---|
| Advanced Crosshair | ON | Master switch. Off means a completely untouched vanilla HUD. |
| Normal hit color | ON, `#FFFF3333` | Any `#AARRGGBB` or `#RRGGBB` hex code, alpha included. |
| Critical hit color | ON, `#FF0080FF` | Turning this off falls back to the normal hit color, since a crit-capable moment is still a hit. |
| Crosshair scale | 100% | 50% to 500%. The attack indicator moves down to stay clear of a scaled-up crosshair. |
| Crosshair with F3 open | OFF | On, the normal crosshair is drawn instead of the debug one. |

Mod Menu is optional. Without it the mod still works and the JSON file is still read.

## 📍 Why is this useful?

- Very useful for landing **Punish Crits** and **W-Release Crits** effectively. 
  
- Can help newer players understand and practice hit timing and mechanics

### Give your PvP skills a major upgrade – Advanced Crosshair helps you hit smarter, not harder!
