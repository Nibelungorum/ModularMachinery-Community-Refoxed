# Hatch GUI Bar Overlay Design

## Scope

Bring the MMCE energy and fluid hatch bar visuals into the current NeoForge menu screen. Keep the existing `guitank.png` background, menu dimensions, synced amounts, text, and interaction behavior unchanged.

## Assets

Copy MMCE's `reference/mmce/src/main/resources/assets/modularmachinery/textures/gui/guibar.png` to `src/main/resources/assets/mmcr/textures/gui/guibar.png` without changing its layout. The implementation depends on the original texture coordinates.

## Rendering

`MachineMenuScreen` keeps rendering the 20 by 61 pixel bar at the current hatch coordinates.

For a fluid hatch:

- Render the tinted still-fluid sprite according to the current amount, as it does now.
- Render the `guibar.png` region `(176, 0, 20, 61)` over the whole bar after the fluid. This provides MMCE's frame and tick-mark overlay.

For an energy hatch:

- Replace the solid red `fill` call with the `guibar.png` region `(196, 61 - filled, 20, filled)`.
- The cropped region preserves MMCE's energy fill and its built-in scale appearance.

The current amount-to-pixel calculation and minimum one-pixel display for non-empty storage remain unchanged. Rendering does nothing when capacity is non-positive.

## Validation

- `./gradlew compileJava --no-daemon` succeeds.
- A non-empty fluid hatch shows its fluid beneath the bar frame/ticks.
- A non-empty energy hatch shows the MMCE textured, graduated energy fill rather than a plain red rectangle.
- Empty and full storage render without source-coordinate overflow.
