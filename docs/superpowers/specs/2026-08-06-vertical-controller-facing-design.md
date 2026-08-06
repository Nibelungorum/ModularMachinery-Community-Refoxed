# Vertical Controller Facing Design

## Goal

Allow selected machines to place and form with controller faces pointing `UP` or `DOWN`, while preserving the existing horizontal-only default behavior and keeping multiblock export output in the current `BlockArray.builder().pattern(...).set(...).build()` format.

## Reference Findings

- MMCR currently uses `MachineControllerBlock.FACING = BlockStateProperties.HORIZONTAL_FACING`, so controller block states cannot represent `UP` or `DOWN`.
- MMCR structure matching rotates raw templates from a SOUTH-facing basis by calling `BlockRotator.rotateYCCWSouthUntil(...)` through `BlockArrayCache` and `StructureMatcher`.
- MMCR export normalizes captured offsets back to the same SOUTH-facing template basis with `MultiblockExportService.normalizeOffset(...)`.
- MMCE has six-direction cache maps, but the UP/DOWN dynamic-pattern branch is commented out and skipped, so there is no complete MMCE implementation to port directly.
- `docs/main-roadmap.md` does not assign this feature to a future phase; it is a focused structure-matching/export enhancement.

## Design

### Fixed Roll Convention

Vertical controller facing will support one deterministic orientation per vertical face, not four roll variants. A raw template's local SOUTH/`+Z` axis maps to the controller `Direction`; its local UP/`+Y` axis is mapped by a fixed rule for `UP` and `DOWN`. This avoids adding a separate roll blockstate or extending the machine definition format.

The same transform pair must be used by matching, build placement, and export normalization:

- `rotateSouthTo(pos, facing)` maps a raw template offset into world-relative offset for the given controller face.
- `normalizeFromFace(offset, facing)` maps a captured world-relative offset back to raw SOUTH-facing template coordinates.

Horizontal behavior must remain byte-for-byte equivalent for existing tests: SOUTH is identity, EAST/NORTH/WEST follow the current YCCW sequence.

### Machine Definition Gate

`MachineControllerSpec` gets a new boolean field, `allowVerticalFacing`, defaulting to `false`. Existing dynamic-machine constructors and default machine definitions must continue to create horizontal-only controllers unless they opt in.

KubeJS machine builders get chainable methods:

- `allowVerticalFacing()` sets the flag to `true`.
- `allowVerticalFacing(boolean allow)` sets the flag explicitly.

### Placement And Formation

`MachineControllerBlock.FACING` changes from horizontal-only to the six-way `BlockStateProperties.FACING`. Placement resolves the machine from the block's `machineId` and allows vertical placement only when `machine.controller().allowVerticalFacing()` is true. When vertical is not allowed, placement falls back to the current horizontal behavior.

`MachineControllerBlockEntity.tryFormMachine(...)` rejects `UP`/`DOWN` candidate facings for machines whose controller spec does not allow vertical facing. This prevents unbound or cross-machine scans from forming a vertical structure for a horizontal-only machine.

### Export

`MultiblockExportService.normalizeOffset(...)` switches from its horizontal-only loop to `BlockRotator.normalizeFromFace(...)`. The rendered Java format remains unchanged: captured blocks still become compact `.pattern(...)` slices and `.set(...)` bindings.

### Models And Datagen

`MachineControllerVariants.full()` must enumerate all six `Direction` values because the blockstate can now contain `UP` and `DOWN`. Horizontal rotations stay unchanged. `UP` and `DOWN` receive fixed model rotations matching the chosen transform convention closely enough for controller front texture orientation.

## Testing

- Unit-test the six-way transform pair: `normalizeFromFace(rotateSouthTo(pos, face), face) == pos` for all six directions.
- Unit-test representative `UP` and `DOWN` coordinate mappings so the fixed roll convention is locked down.
- Extend `BlockArrayCache`/`StructureMatcher` tests to round-trip vertical structures when the caller explicitly uses `UP`/`DOWN`.
- Extend export tests so vertical captures normalize back to the same template format and round-trip through `rotateSouthTo(...)`.
- Extend `MachineControllerSpecTest` and `MachineBuilderJSTest` for default false and opt-in true behavior.
- Add controller block placement/state tests where feasible without a full server world; otherwise cover formation gating through direct spec and matcher tests.

## Non-Goals

- No support for four roll variants around `UP` or `DOWN` in this pass.
- No new serialized roll property, no extra machine-definition orientation enum, and no change to exported Java format.
- No changes to recipe IO, component context, or controller runtime tick behavior beyond formation gating.
