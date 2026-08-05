# Pattern Multiblock Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change `/mmcr export` Java output from per-position `blocks.put(...)` code to compact `BlockArray.builder().pattern(...).set(...).build()` code with inline block predicates.

**Architecture:** Keep export capture unchanged. Extend `BlockArray.Builder` so pattern rows can represent arbitrary width/depth/height structures, then update `MultiblockExportService.renderJava(...)` to convert normalized snapshot entries into layered pattern text and inline `.set(...)` bindings. Avoid generated local `Block` variables and avoid generated `Map<BlockPos, BlockPredicate>` scaffolding.

**Tech Stack:** Java 21, Minecraft/NeoForge classes already used by the project, JUnit 5, AssertJ, Gradle.

## Global Constraints

- Preserve existing controller-facing normalization in `MultiblockExportService.normalizeOffset(...)`.
- Do not introduce new dependencies or change Minecraft/NeoForge/Gradle versions.
- Do not generate local block variables for exported blocks.
- Use `Blocks.<CONSTANT>` for `minecraft` blocks when a matching vanilla constant exists.
- Use `BuiltInRegistries.BLOCK.getValue(Identifier.parse("namespace:path"))` for non-vanilla blocks and vanilla blocks without a known constant.
- Keep spaces as skipped air cells in pattern strings.
- Run at least `./gradlew test --tests cn.howxu.mmcr.api.machine.BlockArrayTest --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --no-daemon` before completion.

---

### Task 1: Generalize `BlockArray.Builder.pattern(...)`

**Files:**
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/BlockArray.java`

**Interfaces:**
- Consumes: `BlockArray.builder()`, `BlockArray.Builder.pattern(String... rows)`, `BlockArray.Builder.set(char, BlockPredicate)`, `BlockArray.Builder.build()`.
- Produces: `pattern(String... rows)` accepts one horizontal z-slice per call, each call may contain any positive number of rows with equal width. Rows map `row -> y`, `col -> x`, and pattern-call index -> z. If a `C` cell exists, build output is normalized so `C` becomes `BlockPos.ZERO`.

- [ ] **Step 1: Write failing tests for arbitrary-size builder patterns**

Add these tests to `BlockArrayTest` after `builder_groups_input_as_y_layers_from_arr_columns`:

```java
    @Test void builder_accepts_arbitrary_width_height_and_depth() {
        var x = new BlockPredicate.OfBlock(Blocks.STONE);
        var c = new BlockPredicate.OfBlock(Blocks.DIRT);

        var arr = BlockArray.builder()
                .pattern("XXXX", "X  X")
                .pattern("X  X", "XC X")
                .set('X', x)
                .set('C', c)
                .build();

        assertThat(arr.get(BlockPos.ZERO)).isEqualTo(c);
        assertThat(arr.get(new BlockPos(-1, -1, -1))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(2, -1, -1))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(-1, 0, 0))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(2, 0, 0))).isEqualTo(x);
        assertThat(arr.get(new BlockPos(0, 0, -1))).isNull();
    }

    @Test void builder_rejects_pattern_slices_with_inconsistent_row_widths() {
        assertThatThrownBy(() -> BlockArray.builder()
                .pattern("XX", "XXX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same width");
    }
```

Also add this import:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2: Run tests to verify red**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.BlockArrayTest --no-daemon`

Expected: FAIL because `pattern("XXXX", "X  X")` currently requires rows count to be a multiple of 3 and each row length to be exactly 3.

- [ ] **Step 3: Implement arbitrary-size pattern slices**

In `BlockArray.Builder`, replace the fixed 3x3 assumptions with per-slice dimensions:

```java
        private final LinkedHashMap<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        private final Map<Character, BlockPredicate> symbols = new LinkedHashMap<>();
        private List<List<String>> slices = List.of();
        private int width = -1;
        private int height = -1;

        public Builder pattern(String... rows) {
            if (rows.length == 0) {
                throw new IllegalArgumentException("pattern(...) must contain at least one row");
            }
            int sliceWidth = rows[0].length();
            if (sliceWidth == 0) {
                throw new IllegalArgumentException("pattern rows must not be empty");
            }
            for (String row : rows) {
                if (row.length() != sliceWidth) {
                    throw new IllegalArgumentException("All rows in a pattern slice must have the same width");
                }
            }
            if (width != -1 && sliceWidth != width) {
                throw new IllegalArgumentException("All pattern slices must have the same width");
            }
            if (height != -1 && rows.length != height) {
                throw new IllegalArgumentException("All pattern slices must have the same height");
            }

            width = sliceWidth;
            height = rows.length;
            java.util.List<List<String>> built = new java.util.ArrayList<>(this.slices.size() + 1);
            built.addAll(this.slices);
            built.add(List.of(rows));
            this.slices = List.copyOf(built);
            return this;
        }
```

Then update `build()` loop to use the stored dimensions:

```java
        public BlockArray build() {
            entries.clear();
            if (slices.isEmpty()) {
                throw new IllegalStateException("pattern(...) must be provided before build()");
            }
            BlockPos controller = null;
            int depth = slices.size();
            int xOrigin = width / 2;
            int yOrigin = height / 2;
            int zOrigin = depth / 2;
            for (int row = 0; row < height; row++) {
                int y = row - yOrigin;
                for (int slice = 0; slice < depth; slice++) {
                    int z = slice - zOrigin;
                    String chars = slices.get(slice).get(row);
                    for (int col = 0; col < width; col++) {
                        int x = col - xOrigin;
                        char c = chars.charAt(col);
                        BlockPredicate predicate = symbols.get(c);
                        if (predicate == null) continue;
                        BlockPos pos = new BlockPos(x, y, z);
                        if (c == 'C') controller = pos;
                        entries.put(pos, predicate);
                    }
                }
            }
            if (controller != null && !controller.equals(BlockPos.ZERO)) {
                LinkedHashMap<BlockPos, BlockPredicate> normalized = new LinkedHashMap<>();
                for (var entry : entries.entrySet()) {
                    normalized.put(entry.getKey().subtract(controller), entry.getValue());
                }
                entries.clear();
                entries.putAll(normalized);
            }
            return new BlockArray(Map.copyOf(entries));
        }
```

Update the Javadoc to remove the hard-coded 3x3 / multiple-of-3 statement and say each `pattern(...)` call is one z slice.

- [ ] **Step 4: Run tests to verify green**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.BlockArrayTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/BlockArray.java src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java
git commit -m "feat: generalize block array pattern builder"
```

---

### Task 2: Export `BlockArray.builder()` Pattern Java

**Files:**
- Modify: `src/test/java/cn/howxu/mmcr/internal/export/MultiblockExportServiceTest.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/export/MultiblockExportService.java`

**Interfaces:**
- Consumes: `MultiblockExportService.renderJava(List<SnapshotEntry>, Direction)` and generalized `BlockArray.Builder` from Task 1.
- Produces: Java snippet with compact imports, `BlockArray pattern = BlockArray.builder()`, one `.pattern(...)` call per z slice, inline `.set(...)` predicates, and `.build();`.

- [ ] **Step 1: Replace old export-format test with failing pattern-format expectations**

Replace `renderJavaUsesLookupsAndReusesBlockVariables` in `MultiblockExportServiceTest` with:

```java
    @Test
    void renderJavaUsesPatternBuilderAndInlineRegistryLookups() {
        Identifier casing = Identifier.fromNamespaceAndPath("mmcr", "basic_casing");
        Identifier controller = Identifier.fromNamespaceAndPath("mmcr", "blast_furnace_controller");

        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, controller, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(-1, 0, -1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, -1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(-1, 0, 1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(1, 0, 1), casing, false),
                new MultiblockExportService.SnapshotEntry(new BlockPos(0, 1, 0), Identifier.fromNamespaceAndPath("minecraft", "air"), true)
        ), Direction.SOUTH);

        assertThat(java).contains("import cn.howxu.mmcr.api.machine.BlockArray;");
        assertThat(java).contains("import cn.howxu.mmcr.api.machine.BlockPredicate;");
        assertThat(java).contains("import net.minecraft.core.registries.BuiltInRegistries;");
        assertThat(java).contains("import net.minecraft.resources.Identifier;");
        assertThat(java).doesNotContain("import net.minecraft.core.BlockPos;");
        assertThat(java).doesNotContain("import java.util.LinkedHashMap;");
        assertThat(java).doesNotContain("Map<BlockPos, BlockPredicate> blocks");
        assertThat(java).doesNotContain("blocks.put");
        assertThat(java).doesNotContain("Block basicCasing");
        assertThat(java).contains("BlockArray pattern = BlockArray.builder()");
        assertThat(java).contains(".pattern(\"X X\")");
        assertThat(java).contains(".pattern(\" C \")");
        assertThat(java).contains(".pattern(\"X X\")");
        assertThat(java).contains(".set('C', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:blast_furnace_controller\"))))");
        assertThat(java).contains(".set('X', new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"mmcr:basic_casing\"))))");
        assertThat(java).doesNotContain("minecraft:air");
        assertThat(java).contains(".build();");
    }
```

Add a second test for vanilla constants:

```java
    @Test
    void renderJavaUsesBlocksConstantsForKnownVanillaBlocks() {
        String java = MultiblockExportService.renderJava(List.of(
                new MultiblockExportService.SnapshotEntry(BlockPos.ZERO, Identifier.fromNamespaceAndPath("minecraft", "stone"), false)
        ), Direction.SOUTH);

        assertThat(java).contains("import net.minecraft.world.level.block.Blocks;");
        assertThat(java).contains(".set('X', new BlockPredicate.OfBlock(Blocks.STONE))");
        assertThat(java).doesNotContain("Identifier.parse(\"minecraft:stone\")");
    }
```

- [ ] **Step 2: Run tests to verify red**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --no-daemon`

Expected: FAIL because current output still uses imports for `BlockPos`, `LinkedHashMap`, `Map`, generated local `Block` variables, and `blocks.put(...)`.

- [ ] **Step 3: Implement compact pattern renderer**

In `MultiblockExportService`, remove unused imports after the change: `net.minecraft.core.BlockPos` remains needed for records and normalization; `java.util.LinkedHashMap` remains useful for symbol maps; remove generated-output-only imports only from rendered text, not source imports. Add source imports if needed:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
```

Replace `renderJava(...)` body with logic that:

1. Filters out air entries.
2. Normalizes offsets using existing `normalizeOffset(...)`.
3. Sorts entries by `y`, `z`, `x`, then block ID.
4. Builds a stable `Map<Identifier, Character>` assigning `'C'` to any block ID whose path ends with `_controller` or `controller`, `'X'` to the most common non-controller block when available, then remaining symbols from `ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789` excluding already used characters.
5. Computes min/max x, y, z over rendered entries.
6. Emits one `.pattern(...)` call for every z from min to max. Each call contains every y row from min to max. Each row contains every x from min to max. Empty cells are spaces.
7. Emits `.set(...)` calls in first-seen symbol order, using inline expressions from a helper.

Add helpers with these signatures:

```java
    private static LinkedHashMap<Identifier, Character> assignSymbols(List<RenderedEntry> rendered)

    private static Character preferredSymbol(Identifier id, boolean casingCandidate)

    private static String predicateExpression(Identifier id)

    private static String vanillaBlocksConstant(String path)
```

`predicateExpression(...)` returns:

```java
new BlockPredicate.OfBlock(Blocks.STONE)
```

for known vanilla blocks, and:

```java
new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse("mmcr:basic_casing")))
```

otherwise.

Start `vanillaBlocksConstant(...)` with a small explicit switch for common exported blocks:

```java
        return switch (path) {
            case "stone" -> "STONE";
            case "cobblestone" -> "COBBLESTONE";
            case "dirt" -> "DIRT";
            case "oak_planks" -> "OAK_PLANKS";
            case "glass" -> "GLASS";
            case "iron_block" -> "IRON_BLOCK";
            case "gold_block" -> "GOLD_BLOCK";
            case "diamond_block" -> "DIAMOND_BLOCK";
            default -> null;
        };
```

Render imports conditionally:

```java
        out.append("import cn.howxu.mmcr.api.machine.BlockArray;").append(newline);
        out.append("import cn.howxu.mmcr.api.machine.BlockPredicate;").append(newline);
        if (usesRegistry) {
            out.append("import net.minecraft.core.registries.BuiltInRegistries;").append(newline);
            out.append("import net.minecraft.resources.Identifier;").append(newline);
        }
        if (usesBlocks) {
            out.append("import net.minecraft.world.level.block.Blocks;").append(newline);
        }
```

If `rendered` is empty, emit:

```java
BlockArray pattern = BlockArray.builder()
        .pattern(" ")
        .build();
```

- [ ] **Step 4: Run export tests to verify green**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Run builder and export tests together**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.BlockArrayTest --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/internal/export/MultiblockExportService.java src/test/java/cn/howxu/mmcr/internal/export/MultiblockExportServiceTest.java
git commit -m "feat: export multiblocks as block array patterns"
```

---

### Task 3: Final Compile Verification

**Files:**
- Verify only: no required source changes.

**Interfaces:**
- Consumes: completed Task 1 and Task 2.
- Produces: verified Java compilation and focused test pass.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.BlockArrayTest --tests cn.howxu.mmcr.internal.export.MultiblockExportServiceTest --no-daemon`

Expected: PASS.

- [ ] **Step 2: Run Java compile**

Run: `./gradlew compileJava --no-daemon`

Expected: PASS.

- [ ] **Step 3: Inspect diff**

Run: `git diff -- src/main/java/cn/howxu/mmcr/api/machine/BlockArray.java src/main/java/cn/howxu/mmcr/internal/export/MultiblockExportService.java src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java src/test/java/cn/howxu/mmcr/internal/export/MultiblockExportServiceTest.java`

Expected: Diff only contains builder generalization, pattern export rendering, and related tests.

- [ ] **Step 4: Commit verification-only adjustments if any**

If Task 3 required code fixes, run:

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/BlockArray.java src/main/java/cn/howxu/mmcr/internal/export/MultiblockExportService.java src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java src/test/java/cn/howxu/mmcr/internal/export/MultiblockExportServiceTest.java
git commit -m "fix: verify pattern multiblock export"
```

If Task 3 required no code fixes, do not create an empty commit.

---

## Self-Review

- Spec coverage: covered compact pattern output, removal of meaningless generated variables/imports, vanilla `Blocks` constants, registry inline expressions for modded blocks, arbitrary-size structures, and focused verification.
- Placeholder scan: no placeholder steps remain; every implementation step names exact files, methods, commands, and expected results.
- Type consistency: all referenced production interfaces already exist or are introduced in this plan with exact Java signatures.
