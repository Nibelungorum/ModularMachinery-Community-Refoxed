# Vertical Controller Facing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in UP/DOWN controller placement, matching, and export normalization for multiblock structures while preserving existing horizontal behavior and export format.

**Architecture:** Introduce one shared six-way transform pair in `BlockRotator`, then route matching, build placement, and export normalization through it. Gate vertical behavior with `MachineControllerSpec.allowVerticalFacing`, default false, and keep a fixed roll convention instead of adding roll state. Extend controller blockstate/datagen to enumerate six faces.

**Tech Stack:** Java 21, Minecraft/NeoForge 26.1.2 APIs already used in the project, JUnit 5, AssertJ, Gradle.

## Global Constraints

- Do not change Minecraft, NeoForge, Gradle, or dependency versions.
- Preserve current horizontal SOUTH/EAST/NORTH/WEST coordinate behavior.
- Keep `/mmcr export` output in the existing `BlockArray.builder().pattern(...).set(...).build()` Java format.
- Do not implement vertical roll variants; each of `UP` and `DOWN` has exactly one fixed orientation.
- Default machine definitions remain horizontal-only unless explicitly opted in through `MachineControllerSpec` or `MachineBuilderJS`.
- Run `./gradlew test --tests cn.howxu.mmcr.api.machine.BlockRotatorTest --tests cn.howxu.mmcr.api.machine.BlockArrayTest --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --tests cn.howxu.mmcr.api.machine.MachineControllerSpecTest --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest --no-daemon` before completion.
- Run `./gradlew compileJava --no-daemon` before completion.

---

## File Structure

- Modify `src/main/java/cn/howxu/mmcr/api/machine/BlockRotator.java`: own all structure offset transforms. Add six-way forward and inverse transforms.
- Modify `src/main/java/cn/howxu/mmcr/api/machine/BlockArrayCache.java`: use the six-way forward transform for pattern and tag offsets.
- Modify `src/main/java/cn/howxu/mmcr/internal/export/MultiblockExportService.java`: use the inverse transform for export normalization.
- Modify `src/main/java/cn/howxu/mmcr/internal/command/BuildCommand.java`: use six-way transform and six-way controller property when placing debug-built machines.
- Modify `src/main/java/cn/howxu/mmcr/api/machine/MachineControllerSpec.java`: add the vertical-facing opt-in flag.
- Modify `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java`: expose builder opt-in methods and pass the flag into `MachineControllerSpec`.
- Modify `src/main/java/cn/howxu/mmcr/internal/block/MachineControllerBlock.java`: change `FACING` to six-way and gate vertical placement by machine definition.
- Modify `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`: reject vertical formation for machines that did not opt in.
- Modify `src/main/java/cn/howxu/mmcr/datagen/MachineControllerVariants.java`: enumerate six-facing blockstate variants.
- Modify tests under `src/test/java/cn/howxu/mmcr/...`: lock down transforms, export round-trip, spec defaults, KubeJS opt-in, and placement consistency.

---

### Task 1: Six-Way Structure Transform

**Files:**
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/BlockRotatorTest.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/BlockRotator.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/BlockArrayCache.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/BuildPlacementConsistencyTest.java`

**Interfaces:**
- Consumes: existing `BlockRotator.rotateYCCW(BlockPos)` and `BlockRotator.rotateYCCWSouthUntil(BlockPos, Direction)`.
- Produces: `BlockRotator.rotateSouthTo(BlockPos pos, Direction target)` and `BlockRotator.normalizeFromFace(BlockPos offset, Direction sourceFace)`, both returning `BlockPos`.

- [ ] **Step 1: Add failing transform tests**

Append these tests to `BlockRotatorTest`:

```java
    @Test
    void rotateSouthTo_preserves_existing_horizontal_behavior() {
        BlockPos left = new BlockPos(-1, 0, 0);
        BlockPos front = new BlockPos(0, 0, 1);

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertThat(BlockRotator.rotateSouthTo(left, facing))
                    .isEqualTo(BlockRotator.rotateYCCWSouthUntil(left, facing));
            assertThat(BlockRotator.rotateSouthTo(front, facing))
                    .isEqualTo(BlockRotator.rotateYCCWSouthUntil(front, facing));
        }
    }

    @Test
    void rotateSouthTo_maps_south_axis_to_vertical_faces_with_fixed_roll() {
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 0, 1), Direction.UP))
                .isEqualTo(new BlockPos(0, 1, 0));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 1, 0), Direction.UP))
                .isEqualTo(new BlockPos(0, 0, -1));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP))
                .isEqualTo(new BlockPos(1, 0, 0));

        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 0, 1), Direction.DOWN))
                .isEqualTo(new BlockPos(0, -1, 0));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(0, 1, 0), Direction.DOWN))
                .isEqualTo(new BlockPos(0, 0, 1));
        assertThat(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.DOWN))
                .isEqualTo(new BlockPos(1, 0, 0));
    }

    @Test
    void normalizeFromFace_reverses_rotateSouthTo_for_all_faces() {
        BlockPos[] samples = {
                new BlockPos(0, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(0, 1, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(-2, 3, 4)
        };

        for (Direction facing : Direction.values()) {
            for (BlockPos sample : samples) {
                BlockPos rotated = BlockRotator.rotateSouthTo(sample, facing);
                assertThat(BlockRotator.normalizeFromFace(rotated, facing))
                        .as("face=%s sample=%s", facing, sample)
                        .isEqualTo(sample);
            }
        }
    }
```

- [ ] **Step 2: Run transform tests to verify red**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.BlockRotatorTest --no-daemon`

Expected: FAIL because `rotateSouthTo` and `normalizeFromFace` do not exist.

- [ ] **Step 3: Implement six-way transforms**

Replace `BlockRotator` body with this implementation, preserving package/imports and class javadoc style:

```java
public final class BlockRotator {

    private BlockRotator() {}

    public static BlockPos rotateYCCW(BlockPos pos) {
        return new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
    }

    /**
     * 起点 SOUTH,循环 rotateYCCW 直到 facing 等于 target。
     */
    public static BlockPos rotateYCCWSouthUntil(BlockPos pos, Direction target) {
        Direction current = Direction.SOUTH;
        BlockPos r = pos;
        while (current != target) {
            current = current.getCounterClockWise();
            r = rotateYCCW(r);
        }
        return r;
    }

    public static BlockPos rotateSouthTo(BlockPos pos, Direction target) {
        return switch (target) {
            case NORTH, SOUTH, EAST, WEST -> rotateYCCWSouthUntil(pos, target);
            case UP -> new BlockPos(pos.getX(), pos.getZ(), -pos.getY());
            case DOWN -> new BlockPos(pos.getX(), -pos.getZ(), pos.getY());
        };
    }

    public static BlockPos normalizeFromFace(BlockPos offset, Direction sourceFace) {
        return switch (sourceFace) {
            case NORTH, SOUTH, EAST, WEST -> normalizeHorizontal(offset, sourceFace);
            case UP -> new BlockPos(offset.getX(), -offset.getZ(), offset.getY());
            case DOWN -> new BlockPos(offset.getX(), offset.getZ(), -offset.getY());
        };
    }

    private static BlockPos normalizeHorizontal(BlockPos offset, Direction sourceFace) {
        Direction current = sourceFace;
        BlockPos normalized = offset;
        while (current != Direction.SOUTH) {
            current = current.getCounterClockWise();
            normalized = rotateYCCW(normalized);
        }
        return normalized;
    }
}
```

- [ ] **Step 4: Route cache and placement tests through the new transform**

In `BlockArrayCache.rotate(...)`, replace both `BlockRotator.rotateYCCWSouthUntil(..., key.facing())` calls with `BlockRotator.rotateSouthTo(..., key.facing())`.

In `BuildPlacementConsistencyTest`, replace both uses of `BlockRotator.rotateYCCWSouthUntil(entry.getKey(), ctrlFacing)` with `BlockRotator.rotateSouthTo(entry.getKey(), ctrlFacing)` and replace `BlockStateProperties.HORIZONTAL_FACING` with `BlockStateProperties.FACING`.

Add this test to `BuildPlacementConsistencyTest` after `struct_round_trip_in_each_horizontal_facing`:

```java
    @Test
    void struct_round_trip_in_each_vertical_facing_when_transform_is_used() {
        Machine machine = fixture();
        MachineRegistry.register(machine);
        for (Direction ctrlFacing : List.of(Direction.UP, Direction.DOWN)) {
            BlockPos controller = new BlockPos(50, 4, 50);
            Map<BlockPos, BlockState> world = buildPlacement(machine, controller, ctrlFacing);
            assertThat(StructureMatcher.matches(machine.pattern(), levelFor(world), controller, ctrlFacing))
                    .as("结构 facing=%s 应该 round-trip 成 form", ctrlFacing)
                    .isTrue();
        }
    }
```

Add import:

```java
import java.util.List;
```

- [ ] **Step 5: Run transform and matching tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.BlockRotatorTest --tests cn.howxu.mmcr.api.machine.BlockArrayTest --tests cn.howxu.mmcr.api.machine.BuildPlacementConsistencyTest --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit task 1**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/BlockRotator.java src/main/java/cn/howxu/mmcr/api/machine/BlockArrayCache.java src/test/java/cn/howxu/mmcr/api/machine/BlockRotatorTest.java src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java src/test/java/cn/howxu/mmcr/api/machine/BuildPlacementConsistencyTest.java
git commit -m "feat: add six-way multiblock transforms"
```

---

### Task 2: Export Normalization For Vertical Faces

**Files:**
- Modify: `src/test/java/cn/howxu/mmcr/internal/export/MultiblockExportServiceTest.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/export/MultiblockExportService.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/command/BuildCommand.java`

**Interfaces:**
- Consumes: `BlockRotator.rotateSouthTo(...)` and `BlockRotator.normalizeFromFace(...)` from Task 1.
- Produces: `MultiblockExportService.normalizeOffset(...)` supports all six `Direction` values while `renderJava(...)` output format remains unchanged.

- [ ] **Step 1: Extend export round-trip test to all six faces**

In `MultiblockExportServiceTest.normalizeOffsetRotatesBackToCapturedFace`, replace `Direction.Plane.HORIZONTAL` with `Direction.values()` and replace `BlockRotator.rotateYCCWSouthUntil(normalized, face)` with `BlockRotator.rotateSouthTo(normalized, face)`.

- [ ] **Step 2: Add vertical export format test**

Add this test to `MultiblockExportServiceTest` after `renderJavaUsesPatternBuilderAndInlineRegistryLookups`:

```java
    @Test
    void renderJavaKeepsCurrentFormatForUpFacingCapture() {
        Identifier casing = Identifier.fromNamespaceAndPath("mmcr", "basic_casing");
        Identifier controller = Identifier.fromNamespaceAndPath("mmcr", "blast_furnace_controller");

        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, controller, false),
                new MultiblockExportService.SnapshotEntry(
                        BlockRotator.rotateSouthTo(new BlockPos(0, 0, 1), Direction.UP), casing, false),
                new MultiblockExportService.SnapshotEntry(
                        BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP), casing, false)
        ), Direction.UP);

        assertThat(java).contains("BlockArray pattern = BlockArray.builder()");
        assertThat(java).contains(".pattern(\" C\")");
        assertThat(java).contains(".pattern(\" X\")");
        assertThat(java).contains(".set('C', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:blast_furnace_controller\"))))");
        assertThat(java).contains(".set('X', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:basic_casing\"))))");
    }
```

- [ ] **Step 3: Run export tests to verify red**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --no-daemon`

Expected: FAIL because `normalizeOffset(...)` cannot handle `UP/DOWN` with the horizontal loop.

- [ ] **Step 4: Update export normalization and debug build placement**

In `MultiblockExportService.normalizeOffset(...)`, replace the method body with:

```java
        return BlockRotator.normalizeFromFace(offset, controllerFace);
```

In `BuildCommand.placeMachine(...)`, replace `.setValue(BlockStateProperties.HORIZONTAL_FACING, ctrlFacing)` with `.setValue(BlockStateProperties.FACING, ctrlFacing)` and replace `BlockRotator.rotateYCCWSouthUntil(entry.getKey(), ctrlFacing)` with `BlockRotator.rotateSouthTo(entry.getKey(), ctrlFacing)`.

- [ ] **Step 5: Run export tests**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit task 2**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/internal/export/MultiblockExportService.java src/main/java/cn/howxu/mmcr/internal/command/BuildCommand.java src/test/java/cn/howxu/mmcr/internal/export/MultiblockExportServiceTest.java
git commit -m "feat: support vertical multiblock export normalization"
```

---

### Task 3: Machine Definition Vertical Opt-In

**Files:**
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/MachineControllerSpecTest.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/MachineControllerSpec.java`
- Modify: `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJSTest.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java`

**Interfaces:**
- Produces: `MachineControllerSpec(..., boolean allowVerticalFacing)` canonical record field.
- Produces: `MachineBuilderJS.allowVerticalFacing()` and `MachineBuilderJS.allowVerticalFacing(boolean allow)`.

- [ ] **Step 1: Add failing spec tests**

In `MachineControllerSpecTest.defaults_derive_controller_id_and_safe_textures_from_machine_id`, add:

```java
        assertThat(spec.allowVerticalFacing()).isFalse();
```

In `spec_rejects_null_values`, update constructor calls to include `false`:

```java
                () -> new MachineControllerSpec(null, texture, texture, texture, texture, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MachineControllerSpec(id, null, texture, texture, texture, false));
```

Add this test:

```java
    @Test
    void spec_can_opt_into_vertical_controller_facing() {
        Identifier id = MMCR.id("blast_furnace_controller");
        Identifier texture = MMCR.id("block/basic_controller");

        MachineControllerSpec spec = new MachineControllerSpec(id, texture, texture, texture, texture, true);

        assertThat(spec.allowVerticalFacing()).isTrue();
    }
```

- [ ] **Step 2: Add failing KubeJS builder test**

In `MachineBuilderJSTest`, update existing `new MachineControllerSpec(...)` expected value to include `false` as the final argument.

Add this test:

```java
    @Test
    void allow_vertical_facing_sets_controller_spec_flag() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .allowVerticalFacing()
                .createObject();

        assertThat(machine.controller().allowVerticalFacing()).isTrue();
    }
```

- [ ] **Step 3: Run spec/builder tests to verify red**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.MachineControllerSpecTest --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest --no-daemon`

Expected: FAIL because the record field and builder methods do not exist.

- [ ] **Step 4: Add record field and default false**

Change `MachineControllerSpec` record header to:

```java
public record MachineControllerSpec(
        Identifier id,
        Identifier frontTexture,
        Identifier sideTexture,
        Identifier topTexture,
        Identifier bottomTexture,
        boolean allowVerticalFacing) {
```

Update `defaultsFor(...)` constructor call to include `false`:

```java
        return new MachineControllerSpec(
                controllerId,
                basicController,
                basicCasing,
                basicCasing,
                basicCasing,
                false);
```

- [ ] **Step 5: Add KubeJS builder flag**

In `MachineBuilderJS`, add field near texture fields:

```java
    public transient boolean allowVerticalFacing = false;
```

Add methods before `registerObject()`:

```java
    public MachineBuilderJS allowVerticalFacing() {
        return allowVerticalFacing(true);
    }

    public MachineBuilderJS allowVerticalFacing(boolean allow) {
        this.allowVerticalFacing = allow;
        return this;
    }
```

Update `controllerSpec()` constructor call to pass the flag:

```java
                controllerBottomTexture != null ? controllerBottomTexture : defaults.bottomTexture(),
                allowVerticalFacing);
```

- [ ] **Step 6: Update all direct constructor call sites**

Search for `new MachineControllerSpec(` and update every constructor call to pass `false` unless the test explicitly opts in. Known files include:

```text
src/test/java/cn/howxu/mmcr/api/machine/MachineControllerSpecTest.java
src/test/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJSTest.java
src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java
```

- [ ] **Step 7: Run spec/builder tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.MachineControllerSpecTest --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest --no-daemon`

Expected: PASS.

- [ ] **Step 8: Commit task 3**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/MachineControllerSpec.java src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java src/test/java/cn/howxu/mmcr/api/machine/MachineControllerSpecTest.java src/test/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJSTest.java
git commit -m "feat: add vertical controller opt-in"
```

---

### Task 4: Controller Blockstate, Placement, And Formation Gate

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/block/MachineControllerBlock.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/MachineControllerVariants.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/block/MachineControllerBlockTest.java`

**Interfaces:**
- Consumes: `MachineControllerSpec.allowVerticalFacing()` from Task 3.
- Produces: controller block states use `BlockStateProperties.FACING`, and vertical placement/formation is rejected unless the machine opts in.

- [ ] **Step 1: Add direct controller property tests**

Add imports to `MachineControllerBlockTest`:

```java
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
```

Add tests:

```java
    @Test
    void controller_facing_property_accepts_vertical_values() {
        MachineControllerBlock block = new MachineControllerBlock(MMCR.id("test"), net.minecraft.world.level.block.Block.Properties.of());

        assertThat(block.defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.UP)
                .getValue(MachineControllerBlock.FACING)).isEqualTo(Direction.UP);
        assertThat(MachineControllerBlock.FACING).isEqualTo(BlockStateProperties.FACING);
    }
```

- [ ] **Step 2: Run controller block test to verify red**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.block.MachineControllerBlockTest --no-daemon`

Expected: FAIL because `MachineControllerBlock.FACING` is still horizontal-only.

- [ ] **Step 3: Change controller facing property and placement gate**

In `MachineControllerBlock`, change:

```java
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
```

to:

```java
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
```

Replace `getStateForPlacement(...)` with:

```java
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction nearest = ctx.getNearestLookingDirection().getOpposite();
        Direction fallback = ctx.getHorizontalDirection().getOpposite();
        Direction facing = isVerticalAllowed() ? nearest : fallback;
        if (!isVerticalAllowed() && facing.getAxis().isVertical()) {
            facing = fallback;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    private boolean isVerticalAllowed() {
        Machine machine = MachineDefinitions.get(machineId);
        return machine != null && machine.controller().allowVerticalFacing();
    }
```

- [ ] **Step 4: Gate formation in block entity**

In `MachineControllerBlockEntity.tryFormMachine(...)`, add this guard at the top:

```java
        if (facing.getAxis().isVertical() && !candidate.controller().allowVerticalFacing()) return false;
```

- [ ] **Step 5: Update datagen variants to six directions**

In `MachineControllerVariants.full()`, replace `Direction.Plane.HORIZONTAL` loop with `Direction.values()`.

Update `rotationFor(...)` to include vertical cases:

```java
    private static VariantMutator rotationFor(Direction facing) {
        return switch (facing) {
            case NORTH -> v -> v;
            case EAST -> VariantMutator.Y_ROT.withValue(Quadrant.R90);
            case SOUTH -> VariantMutator.Y_ROT.withValue(Quadrant.R180);
            case WEST -> VariantMutator.Y_ROT.withValue(Quadrant.R270);
            case UP -> VariantMutator.X_ROT.withValue(Quadrant.R270);
            case DOWN -> VariantMutator.X_ROT.withValue(Quadrant.R90);
        };
    }
```

- [ ] **Step 6: Update remaining horizontal property references**

Search test and main sources for `BlockStateProperties.HORIZONTAL_FACING` and replace only controller-related uses with `BlockStateProperties.FACING`. Do not change unrelated vanilla block tests.

- [ ] **Step 7: Run controller and compile checks**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.block.MachineControllerBlockTest --no-daemon`

Expected: PASS.

Run: `./gradlew compileJava --no-daemon`

Expected: PASS.

- [ ] **Step 8: Commit task 4**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/internal/block/MachineControllerBlock.java src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java src/main/java/cn/howxu/mmcr/datagen/MachineControllerVariants.java src/test/java/cn/howxu/mmcr/internal/block/MachineControllerBlockTest.java
git commit -m "feat: gate vertical controller placement"
```

---

### Task 5: Final Verification And Roadmap Note

**Files:**
- Modify: `docs/main-roadmap.md`
- Verify: all files changed in Tasks 1-4.

**Interfaces:**
- Consumes: completed six-way transform, export normalization, opt-in flag, and controller gating.
- Produces: verified implementation and a roadmap baseline note.

- [ ] **Step 1: Update roadmap baseline**

In `docs/main-roadmap.md` under `### 已基本落地`, add one bullet after the existing `StructureMatcher`/controller bullets:

```markdown
- 多方块结构支持机器级 opt-in 的 UP/DOWN 控制器朝向检测；默认机器仍保持水平四向，导出格式保持 `BlockArray.builder()`。
```

- [ ] **Step 2: Run targeted tests**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.machine.BlockRotatorTest --tests cn.howxu.mmcr.api.machine.BlockArrayTest --tests cn.howxu.mmcr.api.machine.BuildPlacementConsistencyTest --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --tests cn.howxu.mmcr.api.machine.MachineControllerSpecTest --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest --tests cn.howxu.mmcr.internal.block.MachineControllerBlockTest --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Run compile**

Run: `./gradlew compileJava --no-daemon`

Expected: PASS.

- [ ] **Step 4: Inspect git diff for unrelated changes**

Run: `git diff --stat`

Expected: only files listed in this plan plus the spec/plan docs changed.

- [ ] **Step 5: Commit final docs/verification note**

Run:

```bash
git add docs/main-roadmap.md docs/superpowers/specs/2026-08-06-vertical-controller-facing-design.md docs/superpowers/plans/2026-08-06-vertical-controller-facing.md
git commit -m "docs: plan vertical controller facing"
```

---

## Self-Review

- Spec coverage: transform pair, opt-in flag, placement gate, formation gate, export normalization, datagen variants, and tests are each mapped to a task.
- Placeholder scan: no TBD/TODO/fill-in steps remain; each code-changing step includes exact code or exact replacement instructions.
- Type consistency: `rotateSouthTo`, `normalizeFromFace`, and `allowVerticalFacing` names are used consistently across tasks.
