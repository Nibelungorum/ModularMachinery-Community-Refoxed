# Multiblock Detector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-game multiblock detector item and `/mmcr export` command that export selected world blocks as `BlockArray` Java code.

**Architecture:** Store only selection metadata on the detector `ItemStack` via a NeoForge data component. Keep interaction capture in an item plus server-side player interaction event handler, keep export validation/orchestration in a command, and keep rotation/text/file generation in pure utility classes covered by unit tests.

**Tech Stack:** Java 21, Minecraft 26.1.2 mappings, NeoForge deferred registers/events/data components, Brigadier commands, Gradle test/compile tasks.

## Global Constraints

- Work in `.worktrees/debug-multiblock-detector` on branch `debug/multiblock-detector`.
- Preserve existing code style and do not introduce unrelated refactors.
- New classes must include Javadoc author line: `@author howxu <dev@howxu.cn>`.
- Do not cache selected region block contents in the item; read current world state at export time.
- World and `BlockState` access stays on the server thread; async work only processes immutable snapshots and writes text files.
- Export API targets `BlockArray` plus `BlockPredicate.OfBlock`; do not emit symbolic pattern builder output.
- At minimum verify with `./gradlew compileJava --no-daemon`.

---

## File Structure

- Create `src/main/java/cn/howxu/mmcr/internal/item/MultiblockDetectorItem.java`: handles right-click block and shift-right-click air behavior.
- Create `src/main/java/cn/howxu/mmcr/internal/item/MultiblockDetectorSelection.java`: immutable data component payload for controller/face/first/second positions.
- Create `src/main/java/cn/howxu/mmcr/registry/ModDataComponents.java`: registers the detector selection data component.
- Create `src/main/java/cn/howxu/mmcr/internal/event/MultiblockDetectorHandler.java`: records middle-click controller selection on the server event bus.
- Create `src/main/java/cn/howxu/mmcr/internal/command/ExportCommand.java`: registers `/mmcr export`, validates held detector, scans selected region, schedules async generation/write, and reports completion.
- Create `src/main/java/cn/howxu/mmcr/internal/export/MultiblockExportService.java`: pure snapshot rotation, Java text rendering, and unique filename logic.
- Modify `src/main/java/cn/howxu/mmcr/MMCR.java`: register data components and command.
- Modify `src/main/java/cn/howxu/mmcr/registry/ModItems.java`: register `multiblock_detector` and expose it in creative tab item map.
- Modify `src/main/java/cn/howxu/mmcr/datagen/ModelGen.java`: generate flat item model for detector.
- Modify `src/main/java/cn/howxu/mmcr/datagen/Translations.java`: add English and Chinese item names.
- Create `src/main/resources/assets/mmcr/textures/item/multiblock_detector.png` only if no reusable minimal asset is acceptable; otherwise use the existing generated model with the wrench texture as a placeholder to avoid asset churn.
- Create `src/test/java/cn/howxu/mmcr/internal/export/MultiblockExportServiceTest.java`: cover rotation normalization, generated text, and filename collision numbering.

---

### Task 1: Data Component And Detector Item

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/item/MultiblockDetectorSelection.java`
- Create: `src/main/java/cn/howxu/mmcr/registry/ModDataComponents.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/item/MultiblockDetectorItem.java`
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModItems.java`
- Modify: `src/main/java/cn/howxu/mmcr/MMCR.java`

**Interfaces:**
- Produces: `MultiblockDetectorSelection` record with nullable `BlockPos controllerPos`, nullable `Direction controllerFace`, nullable `BlockPos firstPos`, nullable `BlockPos secondPos`.
- Produces: `ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION` for `ItemStack#get`, `set`, and `remove`.
- Produces: `ModItems.MULTIBLOCK_DETECTOR` for command and event validation.

- [ ] **Step 1: Add selection payload**

Create `MultiblockDetectorSelection` with a `Codec` and `StreamCodec`, plus helpers `withController(BlockPos, Direction)`, `withFirst(BlockPos)`, `withSecond(BlockPos)`, and `isComplete()`.

- [ ] **Step 2: Register data component**

Create `ModDataComponents` with a deferred data component register for `multiblock_detector_selection`, using the selection codec and stream codec.

- [ ] **Step 3: Register the mod bus hook**

Call `ModDataComponents.register(modBus)` in `MMCR` before item use can access the component.

- [ ] **Step 4: Implement item interactions**

Create `MultiblockDetectorItem` extending `Item`: `useOn` records first/second position based on shift state, and `use` clears selection on shift-right-click air.

- [ ] **Step 5: Register detector item**

Add `MULTIBLOCK_DETECTOR` to `ModItems`, register id `multiblock_detector`, and add it to `ITEMS` after the wrench.

- [ ] **Step 6: Verify compile surface**

Run `./gradlew compileJava --no-daemon`. Expected: either pass or expose API signature issues to fix before Task 2.

---

### Task 2: Middle-Click Controller Capture

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/event/MultiblockDetectorHandler.java`

**Interfaces:**
- Consumes: `ModItems.MULTIBLOCK_DETECTOR` and `ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION`.
- Produces: server-side controller position and clicked face stored on the held detector stack.

- [ ] **Step 1: Find NeoForge event API by compile feedback**

Prefer the project's existing `PlayerInteractEvent` style. Use the server-visible middle-click/pick-block event if available; otherwise use the closest NeoForge interaction-key event that exposes `BlockHitResult` and item stack.

- [ ] **Step 2: Implement exact detector lookup**

Only write to the held `multiblock_detector`; ignore all other items.

- [ ] **Step 3: Store controller metadata**

On server-side block hit, write `controllerPos` and `controllerFace` using `BlockHitResult#getBlockPos()` and `getDirection()`, preserving existing first/second positions.

- [ ] **Step 4: Send feedback and consume event**

Send a concise `[MMCR]` system message with position and face, and prevent duplicate vanilla handling when the event API supports it.

---

### Task 3: Export Service Pure Logic

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/export/MultiblockExportService.java`
- Create: `src/test/java/cn/howxu/mmcr/internal/export/MultiblockExportServiceTest.java`

**Interfaces:**
- Produces: `record SnapshotEntry(BlockPos offset, Identifier blockId, boolean air)`.
- Produces: `String renderJava(List<SnapshotEntry> entries, Direction controllerFace)`.
- Produces: `BlockPos normalizeOffset(BlockPos offset, Direction controllerFace)` that converts world-relative offset into the south-facing API default.
- Produces: `Path nextExportPath(Path gameDir, LocalDateTime timestamp)`.

- [ ] **Step 1: Add rotation tests**

Assert offsets round-trip against existing `BlockRotator.rotateYCCWSouthUntil`: exported normalized offset rotated back to the captured controller face matches the original world offset.

- [ ] **Step 2: Add renderer tests**

Assert generated Java includes imports, `LinkedHashMap`, registry lookup locals reused per unique block id, per-coordinate `blocks.put(...)`, and `new BlockArray(Map.copyOf(blocks))`.

- [ ] **Step 3: Add filename tests**

Assert `yyyy-MM-dd-HH-mm-ss-多方块导出-1.txt` is selected first, and `-2.txt` is selected when `-1.txt` exists.

- [ ] **Step 4: Implement pure logic**

Implement tests with no world access and deterministic ordering by normalized Y, Z, X, then block id.

- [ ] **Step 5: Run unit test**

Run `./gradlew test --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --no-daemon`. Expected: pass.

---

### Task 4: `/mmcr export` Command

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/command/ExportCommand.java`
- Modify: `src/main/java/cn/howxu/mmcr/MMCR.java`

**Interfaces:**
- Consumes: detector selection component and `MultiblockExportService`.
- Produces: `/mmcr export` command under the existing root literal.

- [ ] **Step 1: Register command**

Add `ExportCommand.register(ev.getDispatcher())` beside reload/build registration.

- [ ] **Step 2: Validate held detector count**

Check main hand and off hand and require exactly one detector; fail with clear system feedback for zero or two.

- [ ] **Step 3: Validate complete selection**

Require controller position, controller face, first position, and second position.

- [ ] **Step 4: Validate region contains controller**

Compute inclusive min/max corners and reject if controller is outside.

- [ ] **Step 5: Protect against oversized regions**

Use a conservative constant such as `MAX_EXPORT_VOLUME = 32768`, include actual volume in the failure message.

- [ ] **Step 6: Snapshot world on server thread**

Iterate inclusive region, read `BlockState`, skip no entries yet but record `air` so renderer can omit it. Store only `BlockPos offset`, block registry id, and air flag.

- [ ] **Step 7: Async render and write**

Submit snapshot to a single-thread executor in `MultiblockExportService`; write file off-thread, then use `server.execute(...)` for success/failure player feedback.

- [ ] **Step 8: Log write failures**

Use `MMCR.LOG.error` with exception and notify player without throwing on the server thread.

---

### Task 5: Datagen, Resources, And Final Verification

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/datagen/ModelGen.java`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java`
- Optional create: `src/main/resources/assets/mmcr/textures/item/multiblock_detector.png`

**Interfaces:**
- Consumes: `ModItems.MULTIBLOCK_DETECTOR`.
- Produces: generated flat item model and localized names.

- [ ] **Step 1: Generate flat item model**

Add `itemModels.generateFlatItem(ModItems.MULTIBLOCK_DETECTOR.get(), ModelTemplates.FLAT_ITEM)`.

- [ ] **Step 2: Add translations**

Add `item.mmcr.multiblock_detector` as `Multiblock Detector` and `多方块检测工具`.

- [ ] **Step 3: Decide texture minimally**

If generated model requires a texture at compile/datagen time, add a tiny item texture or reuse the wrench texture reference by model customization. Prefer no new binary asset unless required.

- [ ] **Step 4: Run verification**

Run `./gradlew test --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --no-daemon` and `./gradlew compileJava --no-daemon`.

- [ ] **Step 5: Inspect git diff**

Run `git status --short` and `git diff --stat`; confirm no unrelated generated/build artifacts are included.

- [ ] **Step 6: Commit implementation**

Commit intended files with message `feat: add multiblock detector export tool` after verification passes.

---

## Self-Review

- Spec coverage: item selection, data component storage, middle-click controller face, `/mmcr export`, south-facing normalization, async file writing, datagen/localization, and validation errors are all mapped to tasks.
- No placeholders: all tasks identify concrete files, interfaces, commands, and expected checks.
- Type consistency: `MultiblockDetectorSelection`, `ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION`, `ModItems.MULTIBLOCK_DETECTOR`, and `MultiblockExportService` are consistently named across tasks.
