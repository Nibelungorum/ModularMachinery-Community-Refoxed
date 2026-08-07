# Task 5 Report: Dynamic Item Bus UI And JEI Transfer

## What Changed

- Made `ItemBusMenu` derive layout from the actual item bus slot count instead of fixed six-slot constants.
- Added dynamic helpers: `busSlotCount()`, `busRows()`, `playerInventorySlotStart()`, `imageHeight()`, `rowsForSlots(int)`, `imageHeightForSlots(int)`, and `playerInventorySlotStart(int)`.
- Changed item bus layout to four columns, with player inventory shifted down for buses larger than two rows.
- Updated client menu construction to receive `BlockPos` from the open-menu buffer and resolve the client-side block entity when available.
- Added an offset overload to `AbstractMachineMenu.addPlayerSlots` while preserving the existing no-offset call path.
- Updated `MachineMenuScreen` to use `ItemBusMenu.imageHeight()` for item bus screens and leave controller/fluid/energy heights unchanged.
- Updated JEI recipe transfer to build transfer info using the current menu's actual bus slot count and player inventory start.
- Updated focused tests for dynamic item bus menu sizing and JEI slot range semantics.

## Commands Run And Results

- RED: `rtk gradlew test --tests cn.howxu.mmcr.internal.menu.ItemBusMenuTest --no-daemon`
  - Result: FAILED during `compileTestJava` because the new dynamic API (`busSlotCount`, `busRows`, `rowsForSlots`, `imageHeightForSlots`, `playerInventorySlotStart`) did not exist yet.
- GREEN: `rtk gradlew test --tests cn.howxu.mmcr.internal.menu.ItemBusMenuTest --no-daemon`
  - Result: BUILD SUCCESSFUL after implementation and updating the old fixed-position assertion.
- Final focused tests: `rtk gradlew test --tests cn.howxu.mmcr.internal.menu.ItemBusMenuTest --tests cn.howxu.mmcr.compat.jei.MachineRecipeTransferHandlerTest --no-daemon`
  - Result: BUILD SUCCESSFUL.
- Compile: `rtk gradlew compileJava --no-daemon`
  - Result: BUILD SUCCESSFUL.
- Diff hygiene: `rtk git diff --check`
  - Result: no whitespace errors.

## TDD Evidence

- RED was observed before production changes: the new menu sizing tests failed because `ItemBusMenu` still only exposed fixed six-slot constants and lacked the requested dynamic helper API.
- GREEN was observed after production changes: the focused item bus menu tests and JEI slot range test passed.
- A client-screen instance test was not added because constructing real client screens pulls in client-only Minecraft runtime state. The closest focused coverage is the shared/static item bus sizing behavior in `ItemBusMenuTest`, which is what the screen consumes through `ItemBusMenu.imageHeight()`.

## Files Changed

- `src/main/java/cn/howxu/mmcr/internal/menu/ItemBusMenu.java`
- `src/main/java/cn/howxu/mmcr/internal/menu/AbstractMachineMenu.java`
- `src/main/java/cn/howxu/mmcr/registry/ModUIs.java`
- `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java`
- `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeTransferHandler.java`
- `src/test/java/cn/howxu/mmcr/internal/menu/ItemBusMenuTest.java`
- `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeTransferHandlerTest.java`
- `.superpowers/sdd/task-5-report.md`

## Self-Review

- Preserved shift-click behavior by keeping the existing two-pass merge-then-empty insertion logic and replacing only the bus slot bound with `busSlotCount`.
- Preserved JEI semantics for unsupported fluid/energy inputs and empty item inputs, while making the actual transfer range dynamic.
- Kept the old no-argument `addPlayerSlots` behavior for fluid/energy/controller menus.
- Confirmed menu registration now uses `IContainerFactory<ItemBusMenu>` so the client receives the block position.
- Did not include unrelated `.superpowers/sdd/task-1-report.md` modifications in this task.

## Concerns

- Existing deprecation warnings remain for NeoForge item/fluid/energy helper classes; they predate this task and were not changed.
- No full client runtime test was run; `./gradlew runClient --no-daemon` is explicitly forbidden.
