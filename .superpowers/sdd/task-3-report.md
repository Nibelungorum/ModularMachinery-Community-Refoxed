# Task 3 Report: Apply Item, Fluid, And Energy Sizes To Block Entities

## What Changed

- Added `IOPortSizeTest` to cover item bus slot counts, fluid hatch capacities, and energy hatch capacities for tiny/normal/high-end variants.
- Changed `ItemBusBlockEntity`, `FluidHatchBlockEntity`, and `EnergyHatchBlockEntity` to initialize runtime storage from the active `IOPortKind` size metadata instead of hardcoded default capacities.
- Added `kindFromState` and `typeFromState` helpers so concrete port block entities resolve the actual registered variant from the `IOPortBlock` in their `BlockState`.
- Updated item/fluid/energy input and output block entities to retain the resolved runtime kind and report it through `kind()`.
- Updated test bootstrap and controller tests so variant port blocks/entities can be instantiated in unit tests and Unsafe-allocated test ports have a populated `kind` field.

## Commands Run And Results

- RED: `rtk gradlew test --tests cn.howxu.mmcr.internal.tile.IOPortSizeTest --no-daemon` failed with expected hardcoded storage assertions after test fixture fixes:
  - `expected: 1 but was: 6`
  - `expected: 100 but was: 8000`
  - `expected: 2048 but was: 100000`
- GREEN sizing: `rtk gradlew test --tests cn.howxu.mmcr.internal.tile.IOPortSizeTest --no-daemon` passed.
- Focused regression: `rtk gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon` passed.
- Compile check: `rtk gradlew compileJava --no-daemon` passed.

## TDD Evidence

- Wrote `IOPortSizeTest` before production changes.
- Initial RED attempts exposed test fixture gaps for unbound variant holders and frozen registries; after fixing the fixture, the test failed for the intended behavior: storage still used hardcoded defaults.
- Implemented the smallest production change to initialize storage from `kind().itemBusSize()`, `kind().fluidHatchSize()`, and `kind().energyHatchSize()` via the resolved runtime kind.
- Re-ran sizing test and focused recipe/controller regression tests successfully.

## Files Changed

- `src/main/java/cn/howxu/mmcr/internal/tile/ItemBusBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/FluidHatchBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/EnergyHatchBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/IOPortBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/ItemInputBusBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/ItemOutputBusBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/FluidInputHatchBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/FluidOutputHatchBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/EnergyInputHatchBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/EnergyOutputHatchBlockEntity.java`
- `src/test/java/cn/howxu/mmcr/internal/tile/IOPortSizeTest.java`
- `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`
- `src/test/java/cn/howxu/mmcr/test/TestBootstrap.java`

## Self-Review

- Confirmed runtime storage now depends on the registered port kind rather than the abstract input/output class default.
- Kept save/load and public handler getter behavior unchanged.
- Avoided changing recipe/controller production logic; only adjusted tests that Unsafe-allocate ports and therefore bypass constructors.
- Did not run `./gradlew runClient --no-daemon`.

## Concerns

- Test bootstrap now registers/binds all built-in port blocks for unit tests so variant `BlockState` carries `IOPortBlock.kind()`. This is test-only but broader than the original fixture, because variant storage cannot be verified through vanilla placeholder blocks.
- Existing unrelated modification to `.superpowers/sdd/task-1-report.md` was present and intentionally not included in this task commit.
