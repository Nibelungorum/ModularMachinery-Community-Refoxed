# 阶段 3B：Pattern position modifier Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在阶段 3A 的 recipe-local modifier 链之上，移植 MMCE 风格的 `SingleBlockModifierReplacement`，使结构内指定位置的替代方块参与成型并影响当前机器的 recipe runtime 数值。

**Architecture:** `DynamicMachine` 独立保存相对坐标到 single-block replacement 的映射，基础 `BlockArray` 不混入 modifier 状态。结构匹配使用与 pattern 相同的 facing/roll 变换后的 replacement map；成型后 controller 收集实际命中的 replacement，并由 `RecipeCraftingContext` 将其与 recipe-local modifiers 合并，不修改原始 `MachineRecipe`。

**Tech Stack:** Java、Minecraft 26.1.2、NeoForge、Gradle、JUnit 5、AssertJ；使用项目已有 `BlockPredicate`、`BlockRotator`、`MachinePatternCompiler`、`RecipeModifier` 和 context pool，不新增依赖。

## Global Constraints

- 本阶段直接基于阶段 3A，不等待 JEI；JEI 未实现不改变服务端结构匹配与 recipe runtime 语义。
- 本阶段只接入 `SingleBlockModifierReplacement`；`MultiBlockModifierReplacement` 保留为后续扩展。
- 不引入新的硬依赖、旧 Forge 1.12 loader、CraftTweaker 或旧 mixin。
- 不修改 `MachineRecipe` 原始定义，不把结构 modifier 写入 recipe NBT。
- 不改变基础 `BlockArray` 的结构语义；modifier replacement 由 machine 旁路数据维护。
- 新建类必须添加 `@author howxu <dev@howxu.cn>`；不为类名添加 `MMCR` 前缀。
- selector tag 仍只由 recipe requirement tags 与 `ProcessingComponent.tags` 决定，position modifier 不重写 component tag。
- 使用当前仓库的 Java/Gradle/NeoForge API，不升级 Gradle、Minecraft、NeoForge 或现有依赖。
- 每个任务完成后只提交该任务的相关文件，提交前检查 `git status` 和 `git diff`。
- 最终必须运行 `./gradlew compileJava --no-daemon`、`./gradlew test --no-daemon`；如 `check` 可用，再运行 `./gradlew check --no-daemon`。

---

## 文件变更总览

### 数据模型与 machine authoring

- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/modifier/SingleBlockModifierReplacement.java`
  - 保存 replacement 相对位置和 `BlockPredicate`。
  - 保留旧构造器，增加带位置/predicate 的构造器与 getter/setter。
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`
  - 增加 immutable single-block replacement map。
  - 保留既有构造器，增加 replacement 读取与旋转 helper。
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java`
  - 增加接收 `SingleBlockModifierReplacement` 的 builder 入口。
  - `createObject()` 将 replacement map 传给 `DynamicMachine`。

### 结构编译与匹配

- Modify: `src/main/java/cn/howxu/mmcr/api/machine/CompiledMachinePattern.java`
  - 保存每个 compiled facing 的 replacement map。
  - 保持既有基础 pattern、bounds、component/port position API。
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/MachinePatternCompiler.java`
  - 编译基础 pattern 的同时编译 replacement map。
  - 所有坐标变换复用 `BlockRotator`。
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/StructureMatcher.java`
  - 增加 replacement-aware `matches`、`matchesRotated`、`matchesCompiled`、`firstMismatch` 重载。
  - 基础 predicate 与 replacement predicate 使用一致的匹配顺序。
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
  - 在 formation 流程中选择当前 replacement map。
  - 收集命中的 modifiers、去重、清理生命周期状态。

### recipe runtime

- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`
  - 增加接收 effective modifier list 的 runtime requirement 计算入口。
  - 保留现有无参数 API，并让它委托到 recipe-local modifier list。
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
  - 保存 structure modifiers。
  - 统一生成 recipe-local + structure modifiers 的 effective list。
  - 所有 simulate/ioTick/commit 路径使用 effective list。
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`
  - duration 计算使用 context 的 effective modifiers。
  - 保持 NBT 字段格式不变。
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextPool.java`
  - borrow 时加载当前 controller 的 structure modifier 快照。
  - return/reset 时清理该快照。
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchTask.java`
  - 搜索阶段的 input/output simulate 使用已注入 structure modifiers。

### 测试与文档

- Create: `src/test/java/cn/howxu/mmcr/api/recipe/modifier/SingleBlockModifierReplacementTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/StructureMatcherTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/CompiledMachinePatternTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`
- Modify: `docs/MAIN.md`

---

### Task 1: 建立 SingleBlock replacement 数据模型

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/modifier/SingleBlockModifierReplacement.java`
- Create: `src/test/java/cn/howxu/mmcr/api/recipe/modifier/SingleBlockModifierReplacementTest.java`

**Interfaces:**
- Consumes: `AbstractModifierReplacement` 的 immutable modifier/name/description 数据，`BlockPos`，`BlockPredicate`。
- Produces: `getPos()`, `getReplacement()`, `setPos(BlockPos)`, `copyAt(BlockPos)`，以及带 position/predicate 的构造器，供 `DynamicMachine` 和 compiled matcher 使用。

- [ ] **Step 1: Write the failing tests**

在新测试中验证以下接口和不可变列表：

```java
package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleBlockModifierReplacementTest {
    @Test
    void stores_position_predicate_and_modifiers() {
        RecipeModifier modifier = new RecipeModifier("item", RecipeModifier.IOType.INPUT, 2F,
                RecipeModifier.Operation.ADD, false);
        BlockPos pos = new BlockPos(1, 2, 3);
        var replacement = new SingleBlockModifierReplacement(
                "speed", pos, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(modifier), "speed", ItemStack.EMPTY);

        assertThat(replacement.getPos()).isEqualTo(pos);
        assertThat(replacement.getReplacement()).isEqualTo(new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK));
        assertThat(replacement.getModifiers()).containsExactly(modifier);
    }

    @Test
    void set_pos_returns_same_replacement_and_rejects_null() {
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPos(1, 0, 0), new BlockPredicate.Any(),
                List.of(), "", ItemStack.EMPTY);

        assertThat(replacement.setPos(new BlockPos(2, 0, 0))).isSameAs(replacement);
        assertThat(replacement.getPos()).isEqualTo(new BlockPos(2, 0, 0));
        assertThatThrownBy(() -> replacement.setPos(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modifier_list_is_not_mutable_through_constructor_input() {
        List<RecipeModifier> modifiers = new ArrayList<>();
        var replacement = new SingleBlockModifierReplacement(
                "speed", BlockPos.ZERO, new BlockPredicate.Any(),
                modifiers, "", ItemStack.EMPTY);
        modifiers.add(new RecipeModifier("item", RecipeModifier.IOType.INPUT, 1F,
                RecipeModifier.Operation.ADD, false));

        assertThat(replacement.getModifiers()).isEmpty();
        assertThatThrownBy(() -> replacement.getModifiers().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacementTest --no-daemon
```

Expected: FAIL during test compilation because the position/predicate constructor and accessors do not exist.

- [ ] **Step 3: Implement the minimal data model**

在 `SingleBlockModifierReplacement` 中增加 `BlockPos pos`、`BlockPredicate replacement` 字段；带参数构造器校验 `pos` 和 `replacement` 非空；旧构造器保留并将未绑定字段置为 `null`，供旧 API 创建后再调用 `setPos`。新增实现形态如下：

```java
private @Nullable BlockPos pos;
private final BlockPredicate replacement;

public SingleBlockModifierReplacement(
        String modifierName,
        BlockPos pos,
        BlockPredicate replacement,
        List<RecipeModifier> modifiers,
        String description,
        ItemStack descriptiveStack) {
    super(modifierName, modifiers, description, descriptiveStack);
    this.pos = requirePosition(pos);
    this.replacement = Objects.requireNonNull(replacement, "replacement");
}

public @Nullable BlockPos getPos() {
    return pos;
}

public BlockPredicate getReplacement() {
    return replacement;
}

public SingleBlockModifierReplacement setPos(BlockPos pos) {
    this.pos = requirePosition(pos);
    return this;
}

public SingleBlockModifierReplacement copyAt(BlockPos newPos) {
    return new SingleBlockModifierReplacement(
            modifierName, newPos, replacement, modifiers, description, descriptiveStack);
}

private static BlockPos requirePosition(BlockPos pos) {
    return Objects.requireNonNull(pos, "pos");
}
```

保留现有三个构造器，使用 `replacement = new BlockPredicate.Any()` 和 `pos = null`，但 machine registration 时拒绝未绑定 position 的 replacement；这样不会破坏已有编译调用方，也不会把未绑定对象误当成 controller replacement。

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacementTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`，该测试类全部通过。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/modifier/SingleBlockModifierReplacement.java src/test/java/cn/howxu/mmcr/api/recipe/modifier/SingleBlockModifierReplacementTest.java
git commit -m "feat(modifier): add single block replacement metadata"
```

---

### Task 2: 将 replacement 注册到 DynamicMachine 与 KubeJS builder

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java`
- Modify: `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJSTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/MachineRegistryTest.java`

**Interfaces:**
- Consumes: Task 1 的 `SingleBlockModifierReplacement`。
- Produces: `DynamicMachine.modifierReplacements()`, `modifierReplacementsAt(BlockPos)`、带 replacement map 的 canonical constructor，以及 `MachineBuilderJS.addModifier(SingleBlockModifierReplacement)`。

- [ ] **Step 1: Write the failing tests**

在 `MachineBuilderJSTest` 增加 builder 传递测试：

```java
@Test
void builder_passes_single_block_replacements_to_machine() {
    var replacement = new SingleBlockModifierReplacement(
            "speed", new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
            List.of(), "", ItemStack.EMPTY);
    var builder = new MachineBuilderJS("mmcr:builder_replacement")
            .addModifier(replacement);

    DynamicMachine machine = builder.createObject();

    assertThat(machine.modifierReplacementsAt(new BlockPos(1, 0, 0)))
            .containsExactly(replacement);
}
```

在 machine API 测试中验证 map/list defensive copy：

```java
@Test
void dynamic_machine_exposes_immutable_replacement_map() {
    var replacement = new SingleBlockModifierReplacement(
            "speed", new BlockPos(1, 0, 0), new BlockPredicate.Any(),
            List.of(), "", ItemStack.EMPTY);
    var machine = new DynamicMachine(
            Identifier.fromNamespaceAndPath("mmcr", "replacement_machine"),
            "Replacement Machine", new BlockArray(Map.of()),
            MachineControllerSpec.defaultsFor(Identifier.fromNamespaceAndPath("mmcr", "replacement_machine")),
            PortRequirementSpec.none(), List.of(),
            Map.of(new BlockPos(1, 0, 0), List.of(replacement)));

    assertThat(machine.modifierReplacementsAt(new BlockPos(1, 0, 0))).containsExactly(replacement);
    assertThatThrownBy(() -> machine.modifierReplacements().put(BlockPos.ZERO, List.of(replacement)))
            .isInstanceOf(UnsupportedOperationException.class);
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest --tests cn.howxu.mmcr.api.machine.MachineRegistryTest --no-daemon
```

Expected: FAIL during test compilation because `DynamicMachine` has no replacement map constructor/accessor and `MachineBuilderJS.addModifier` does not exist.

- [ ] **Step 3: Add immutable replacement storage**

将 `DynamicMachine` record 增加字段：

```java
Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements
```

canonical constructor 中复制每个 key、value list，并拒绝 `null` position、`null` replacement、未绑定 position 或空 replacement predicate。旧构造器全部委托到新的 canonical constructor，并传入 `Map.of()`。添加：

```java
public List<SingleBlockModifierReplacement> modifierReplacementsAt(BlockPos pos) {
    return modifierReplacements.getOrDefault(pos, List.of());
}
```

添加旋转 helper，供 compiled 与 vertical fallback 共用同一公式：

```java
public Map<BlockPos, List<SingleBlockModifierReplacement>> rotatedModifierReplacements(
        Direction facing, Direction rollFacing) {
    Map<BlockPos, List<SingleBlockModifierReplacement>> rotated = new LinkedHashMap<>();
    Direction normalizedRoll = facing.getAxis().isVertical() ? rollFacing : Direction.SOUTH;
    for (var entry : modifierReplacements.entrySet()) {
        BlockPos rotatedPos = BlockRotator.rotateSouthTo(entry.getKey(), facing, normalizedRoll);
        List<SingleBlockModifierReplacement> replacements = entry.getValue().stream()
                .map(replacement -> replacement.copyAt(rotatedPos))
                .toList();
        rotated.put(rotatedPos, replacements);
    }
    return Map.copyOf(rotated);
}
```

`copyAt(BlockPos)` 返回保留 name、predicate、modifiers、description、descriptiveStack 的新 replacement，避免把原始 SOUTH 坐标对象改成某个 facing 的坐标。

- [ ] **Step 4: Add builder forwarding**

在 `MachineBuilderJS` 增加：

```java
public transient Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements = new LinkedHashMap<>();

public MachineBuilderJS addModifier(SingleBlockModifierReplacement replacement) {
    Objects.requireNonNull(replacement, "replacement");
    BlockPos pos = Objects.requireNonNull(replacement.getPos(), "replacement.pos");
    modifierReplacements.computeIfAbsent(pos, ignored -> new ArrayList<>()).add(replacement);
    return this;
}
```

`createObject()` 改为调用带 replacement map 的 `DynamicMachine` 构造器。不得把 map 暴露为 builder 外部可变的 machine state；canonical constructor 负责最终复制。

- [ ] **Step 5: Run the focused tests and verify they pass**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest --tests cn.howxu.mmcr.api.machine.MachineRegistryTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`，旧 builder 构造测试与新增 replacement 测试全部通过。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java src/test/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJSTest.java src/test/java/cn/howxu/mmcr/api/machine/MachineRegistryTest.java
git commit -m "feat(machine): expose position modifier replacements"
```

---

### Task 3: 为 compiled pattern 和所有 facing 建立 replacement map

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/CompiledMachinePattern.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/MachinePatternCompiler.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/CompiledMachinePatternTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java`

**Interfaces:**
- Consumes: `DynamicMachine.rotatedModifierReplacements(Direction, Direction)`。
- Produces: `CompiledMachinePattern.modifierReplacements(Direction)` 与 `modifierReplacements(Direction, Direction)`；horizontal 旧 accessor 继续有效，vertical fallback 可读取同一旋转 helper。

- [ ] **Step 1: Write the failing tests**

在 `CompiledMachinePatternTest` 增加：

```java
@Test
void compiled_pattern_contains_rotated_replacements_for_horizontal_facing() {
    Identifier id = Identifier.fromNamespaceAndPath("mmcr", "compiled_replacement");
    var replacement = new SingleBlockModifierReplacement(
            "speed", new BlockPos(-1, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
            List.of(), "", ItemStack.EMPTY);
    var machine = new DynamicMachine(
            id, "Compiled Replacement", new BlockArray(Map.of(
                    BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE))),
            MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(),
            Map.of(new BlockPos(-1, 0, 0), List.of(replacement)));

    CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

    assertThat(compiled.modifierReplacements(Direction.EAST))
            .containsKey(new BlockPos(0, 0, 1));
}

@Test
void vertical_roll_uses_same_coordinate_transform_as_block_array() {
    Identifier id = Identifier.fromNamespaceAndPath("mmcr", "compiled_vertical_replacement");
    var replacement = new SingleBlockModifierReplacement(
            "speed", new BlockPos(1, 0, 0), new BlockPredicate.Any(),
            List.of(), "", ItemStack.EMPTY);
    var machine = new DynamicMachine(id, "Compiled Vertical Replacement",
            new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any())),
            MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(),
            Map.of(new BlockPos(1, 0, 0), List.of(replacement)));

    assertThat(machine.rotatedModifierReplacements(Direction.UP, Direction.EAST))
            .containsKey(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP, Direction.EAST));
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.machine.CompiledMachinePatternTest --tests cn.howxu.mmcr.api.machine.BlockArrayTest --no-daemon
```

Expected: FAIL during test compilation because compiled replacement accessors and the machine rotation helper are missing.

- [ ] **Step 3: Add immutable compiled replacement data**

在 `CompiledMachinePattern` 增加：

```java
Map<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> modifierReplacements
```

canonical constructor 对 enum map、position map、replacement list 做深层 defensive copy；添加：

```java
public Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements(Direction facing) {
    return modifierReplacements.getOrDefault(facing, Map.of());
}

public Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements(
        Direction facing, Direction rollFacing) {
    if (!facing.getAxis().isVertical()) return modifierReplacements(facing);
    return machine.rotatedModifierReplacements(facing, rollFacing);
}
```

保留现有五参数 constructor，委托到新 constructor 并传入 empty map，避免既有 tests 与 callers 需要同步修改。

- [ ] **Step 4: Compile horizontal replacement maps**

在 `MachinePatternCompiler.compile` 的现有 horizontal facing 循环中添加：

```java
modifierReplacements.put(facing,
        machine.rotatedModifierReplacements(facing, Direction.SOUTH));
```

不要重复实现坐标旋转；`BlockArrayCache` 继续只负责基础 pattern，replacement 由 `DynamicMachine.rotatedModifierReplacements` 生成。`componentPositions`、`portPositions`、bounds 仍只从基础 rotated pattern 计算，replacement 不扩大结构范围。

- [ ] **Step 5: Run the focused tests and verify they pass**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.machine.CompiledMachinePatternTest --tests cn.howxu.mmcr.api.machine.BlockArrayTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`，horizontal compiled map 与 vertical rotation helper 的坐标断言通过。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/CompiledMachinePattern.java src/main/java/cn/howxu/mmcr/api/machine/MachinePatternCompiler.java src/test/java/cn/howxu/mmcr/api/machine/CompiledMachinePatternTest.java src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java
git commit -m "feat(machine): compile position modifier rotations"
```

---

### Task 4: 让 StructureMatcher 支持 replacement-aware formation

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/StructureMatcher.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/StructureMatcherTest.java`

**Interfaces:**
- Consumes: `Map<BlockPos, List<SingleBlockModifierReplacement>>`，compiled replacement accessor。
- Produces: replacement-aware overloads：
  - `matches(BlockArray, Level, BlockPos, Direction, Map<...>)`
  - `matchesRotated(BlockArray, Level, BlockPos, Map<...>)`
  - `matchesCompiled(CompiledMachinePattern, Direction, Direction, Level, BlockPos)`
  - `firstMismatch(BlockArray, Level, BlockPos, Map<...>)`

- [ ] **Step 1: Write the failing tests**

在 `StructureMatcherTest` 增加基础替换、位置隔离和多个 replacement 测试：

```java
@Test
void replacement_allows_only_the_configured_position_to_match() {
    BlockPos replacementPos = new BlockPos(1, 0, 0);
    BlockArray pattern = new BlockArray(Map.of(
            BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
            replacementPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
    var replacement = new SingleBlockModifierReplacement(
            "speed", replacementPos, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
            List.of(), "", ItemStack.EMPTY);
    var level = LevelStub.create(Map.of(
            BlockPos.ZERO, Blocks.STONE,
            replacementPos, Blocks.GOLD_BLOCK));

    assertThat(StructureMatcher.matchesRotated(pattern, level, BlockPos.ZERO,
            Map.of(replacementPos, List.of(replacement)))).isTrue();
}

@Test
void replacement_at_another_position_does_not_match() {
    BlockPos expected = new BlockPos(1, 0, 0);
    BlockPos wrong = new BlockPos(2, 0, 0);
    BlockArray pattern = new BlockArray(Map.of(
            BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
            expected, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
    var replacement = new SingleBlockModifierReplacement(
            "speed", wrong, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
            List.of(), "", ItemStack.EMPTY);

    assertThat(StructureMatcher.matchesRotated(pattern,
            LevelStub.create(Map.of(BlockPos.ZERO, Blocks.STONE, expected, Blocks.GOLD_BLOCK)),
            BlockPos.ZERO, Map.of(wrong, List.of(replacement)))).isFalse();
}

@Test
void multiple_replacements_at_one_position_use_any_matching_predicate() {
    BlockPos pos = new BlockPos(1, 0, 0);
    BlockArray pattern = new BlockArray(Map.of(pos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
    var first = new SingleBlockModifierReplacement("first", pos,
            new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), "", ItemStack.EMPTY);
    var second = new SingleBlockModifierReplacement("second", pos,
            new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK), List.of(), "", ItemStack.EMPTY);

    assertThat(StructureMatcher.matchesRotated(pattern,
            LevelStub.create(Map.of(pos, Blocks.DIAMOND_BLOCK)), BlockPos.ZERO,
            Map.of(pos, List.of(first, second)))).isTrue();
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.machine.StructureMatcherTest --no-daemon
```

Expected: FAIL during test compilation because replacement-aware overloads do not exist.

- [ ] **Step 3: Implement the matching overloads**

抽取单坐标判断 helper，保持基础 API 委托到 empty map：

```java
private static boolean matchesEntry(
        BlockPredicate expected,
        BlockState actual,
        List<SingleBlockModifierReplacement> replacements) {
    if (expected.matches(actual)) return true;
    for (SingleBlockModifierReplacement replacement : replacements) {
        if (replacement.getReplacement().matches(actual)) return true;
    }
    return false;
}
```

`firstMismatch` 迭代 pattern entry 时使用 `replacements.getOrDefault(entry.getKey(), List.of())`；不允许拿其他坐标的 replacement。`matchesCompiled` 先使用当前 facing/roll 的 compiled bounds，再将 compiled replacement map 传给同一个 `matchesRotated` 路径。

- [ ] **Step 4: 将 controller formation 改为传递 replacement map**

在 `MachineControllerBlockEntity` 中增加 helper：

```java
private Map<BlockPos, List<SingleBlockModifierReplacement>> replacementsFor(
        Machine candidate, CompiledMachinePattern compiled, Direction facing, BlockArray rotatedPattern) {
    Direction rollFacing = getBlockState().getValue(MachineControllerBlock.ROLL_FACING);
    if (compiled != null && compiled.rotatedPattern(facing) == rotatedPattern) {
        return compiled.modifierReplacements(facing, rollFacing);
    }
    if (candidate instanceof DynamicMachine dynamic) {
        return dynamic.rotatedModifierReplacements(facing, rollFacing);
    }
    return Map.of();
}
```

`tryFormMachine` 在基础 pattern 匹配前获得 replacement map；compiled path 调用 replacement-aware `matchesCompiled`，fallback path 调用 replacement-aware `matchesRotated`。`structureMismatchDiagnostic` 使用同一 map 调用 replacement-aware `firstMismatch`，确保日志与 formation 结果一致。

- [ ] **Step 5: Run the focused tests and verify they pass**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.machine.StructureMatcherTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`，基础 matcher 回归测试与 replacement-aware 测试全部通过。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/StructureMatcher.java src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java src/test/java/cn/howxu/mmcr/api/machine/StructureMatcherTest.java
git commit -m "feat(machine): match single block replacements"
```

---

### Task 5: 在 controller 生命周期中收集命中的 modifiers

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`

**Interfaces:**
- Consumes: Task 4 的 replacement-aware formation map。
- Produces: `getFoundModifiers()`, `foundModifierList()`、structure formation/reset 时的命中快照，供 context pool/search 使用。

- [ ] **Step 1: Write the failing tests**

在 controller 测试中增加命中收集的可观察行为；构造一个 pattern 基础位置为 iron、world 放 gold、replacement modifier 为 input add：

```java
@Test
void formed_controller_exposes_only_matching_position_modifiers() {
    var replacement = replacementAt(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK, "speed", 2F);
    var machine = machineWithReplacements(replacement);
    MachineDefinitions.register(machine);

    MachineControllerBlockEntity controller = controllerFor(machine);
    placeControllerAndReplacement(controller, machine, Blocks.GOLD_BLOCK);
    tickUntilFormed(controller);

    assertThat(controller.getFoundModifiers()).containsKey("speed");
    assertThat(controller.foundModifierList()).extracting(RecipeModifier::getModifier)
            .containsExactly(2F);
}

@Test
void duplicate_modifier_name_is_applied_once_and_reset_clears_it() {
    var first = replacementAt(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK, "speed", 2F);
    var second = replacementAt(new BlockPos(2, 0, 0), Blocks.DIAMOND_BLOCK, "speed", 4F);
    var machine = machineWithReplacements(first, second);
    MachineDefinitions.register(machine);

    MachineControllerBlockEntity controller = controllerFor(machine);
    placeControllerAndReplacement(controller, machine, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK);
    tickUntilFormed(controller);

    assertThat(controller.getFoundModifiers()).containsKey("speed");
    breakStructureBlock(controller);

    assertThat(controller.getFoundModifiers()).isEmpty();
}
```

测试 helper 必须使用现有 `LevelStub`、machine registration 和 controller tick 工具，不新增测试专用 production API。新增的 replacement fixture 使用以下明确签名，并复用该测试类已有的 `controllerForFormation`、`invokeTryFormMachine`、`levelOf` 和 `setField`：

```java
private static SingleBlockModifierReplacement replacementAt(
        BlockPos pos, Block block, String name, float value) {
    return new SingleBlockModifierReplacement(
            name, pos, new BlockPredicate.OfBlock(block),
            List.of(new RecipeModifier("item", RecipeModifier.IOType.INPUT,
                    value, RecipeModifier.Operation.ADD, false)),
            "", ItemStack.EMPTY);
}

private static DynamicMachine machineWithReplacements(
        SingleBlockModifierReplacement... replacements) {
    Identifier id = MMCR.id("position_modifier_test");
    Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
    pattern.put(BlockPos.ZERO, new BlockPredicate.Any());
    Map<BlockPos, List<SingleBlockModifierReplacement>> modifierMap = new LinkedHashMap<>();
    for (SingleBlockModifierReplacement replacement : replacements) {
        pattern.put(replacement.getPos(), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
        modifierMap.computeIfAbsent(replacement.getPos(), ignored -> new ArrayList<>()).add(replacement);
    }
    return new DynamicMachine(id, "Position Modifier Test", new BlockArray(pattern),
            MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(), modifierMap);
}
```

`placeControllerAndReplacement` 负责在现有 `levelOf(controller)` 中将每个 replacement 的 world position 设置为其 predicate 对应 block，并通过已有 `LevelStub.putBlockEntity` 保留 controller/port block entity；`tickUntilFormed` 循环调用已有 `invokeTryFormMachine`，直到 `isFormed()` 或测试最大轮数 4。两者签名固定为：

```java
private static void placeControllerAndReplacement(
        MachineControllerBlockEntity controller,
        DynamicMachine machine,
        Block... replacementBlocks) throws Exception

private static void tickUntilFormed(
        MachineControllerBlockEntity controller,
        DynamicMachine machine) throws Exception
```

`replacementBlocks` 按 `machine.modifierReplacements().values()` 的迭代顺序对应 replacement entries；`tickUntilFormed` 在 `controller.isFormed()` 仍为 false 时继续调用 `invokeTryFormMachine(controller, machine, Direction.SOUTH)`，四轮后用 AssertJ 断言 formed。

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon
```

Expected: FAIL during test compilation because `getFoundModifiers()`/`foundModifierList()` and collection logic do not exist。

- [ ] **Step 3: Add found modifier state and collection**

在 controller 增加：

```java
private final Map<String, List<RecipeModifier>> foundModifiers = new LinkedHashMap<>();

public Map<String, List<RecipeModifier>> getFoundModifiers() {
    return Map.copyOf(foundModifiers);
}

public List<RecipeModifier> foundModifierList() {
    return foundModifiers.values().stream().flatMap(List::stream).toList();
}
```

在 `onStructureFormed` 中先清空，再按 rotated replacement map 检查实际 `level.getBlockState(worldPos)`；只有 replacement predicate 命中时执行：

```java
foundModifiers.putIfAbsent(replacement.getModifierName(), replacement.getModifiers());
```

同一 name 只保留第一次命中的 modifier list，保持 MMCE 的 name-keyed 去重语义。方法应接收 formation 时使用的 exact rotated map，不能重新从 raw position 计算而忽略 facing/roll。

在 `resetMachine`、machine replacement、结构版本变化和 context invalidation 前清空 map；`updateComponents` 只继续维护 component/tag，不在其中收集 modifier。

- [ ] **Step 4: Expose a stable snapshot for recipe search**

`foundModifierList()` 返回新 list；其中每个 modifier 来自 replacement 的 immutable `getModifiers()`。Recipe search 借用 context 时只读取该 snapshot，不持有 controller 的 mutable map reference。

- [ ] **Step 5: Run the focused test and verify it passes**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`，命中、去重、reset 清理测试通过，既有 controller lifecycle 测试不回归。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java
git commit -m "feat(machine): collect matched position modifiers"
```

---

### Task 6: 合并 recipe-local 与 structure modifiers

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextPool.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchTask.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`

**Interfaces:**
- Consumes: `MachineControllerBlockEntity.foundModifierList()`。
- Produces: `RecipeCraftingContext.structureModifiers()`, `setStructureModifiers(List<RecipeModifier>)`, `effectiveModifiers(MachineRecipe)`；`MachineRecipe.runtimeRequirements(List<RecipeModifier>)`；Active recipe duration computed from the same effective list used by context simulate/commit。

- [ ] **Step 1: Write failing runtime modifier tests**

在 `MachineRecipeTest` 验证带额外 modifiers 的 runtime requirement，不修改 raw recipe：

```java
@Test
void runtime_requirements_accept_structure_modifiers_without_mutating_raw_recipe() {
    MachineRecipe recipe = new MachineRecipe(
        Identifier.fromNamespaceAndPath("mmcr", "effective_modifiers"),
        Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
        20,
        List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
        List.of(),
        List.of(new RecipeModifier("item", RecipeModifier.IOType.INPUT, 1F,
                RecipeModifier.Operation.MULTIPLY, false)),
        0,
        1);
List<RecipeModifier> effective = List.of(
        new RecipeModifier("item", RecipeModifier.IOType.INPUT, 2F,
                RecipeModifier.Operation.ADD, false));

assertThat(recipe.runtimeRequirements(effective).getFirst()).isInstanceOf(ItemRequirement.class);
assertThat(((ItemRequirement) recipe.runtimeRequirements(effective).getFirst()).count()).isEqualTo(6);
assertThat(((ItemRequirement) recipe.requirements().getFirst()).count()).isEqualTo(2);
}
```

在 `RecipeCraftingContextTest` 验证 context 的 effective list 与 pool reset：

```java
@Test
void structure_modifiers_are_added_after_recipe_modifiers_and_cleared_on_reset() {
    RecipeCraftingContext context = new RecipeCraftingContext(controllerWithComponents());
RecipeModifier recipeModifier = new RecipeModifier("item", RecipeModifier.IOType.INPUT, 1F,
        RecipeModifier.Operation.MULTIPLY, false);
MachineRecipe recipe = new MachineRecipe(
        Identifier.fromNamespaceAndPath("mmcr", "context_effective_modifiers"),
        Identifier.fromNamespaceAndPath("mmcr", "test_machine"),
        20, List.of(), List.of(), List.of(recipeModifier), 0, 1);
RecipeModifier structure = new RecipeModifier("item", RecipeModifier.IOType.INPUT, 2F,
        RecipeModifier.Operation.ADD, false);

context.setStructureModifiers(List.of(structure));

assertThat(context.structureModifiers()).containsExactly(structure);
assertThat(context.effectiveModifiers(recipe)).containsExactly(recipeModifier, structure);

context.resetTransientState();

assertThat(context.structureModifiers()).isEmpty();
}
```

- [ ] **Step 2: Run focused tests and verify they fail**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon
```

Expected: FAIL during test compilation because the overloads and structure modifier state do not exist。

- [ ] **Step 3: Add effective modifier overloads to MachineRecipe**

保留当前 `runtimeRequirements()` 与 `runtimeMachineOutputs()`，新增：

```java
public List<MachineRequirement> runtimeRequirements(List<RecipeModifier> extraModifiers) {
    List<RecipeModifier> effective = combineModifiers(extraModifiers);
    if (effective.isEmpty()) return requirements;
    List<MachineRequirement> derived = new ArrayList<>(requirements.size());
    for (MachineRequirement requirement : requirements) {
        derived.add(applyModifiers(requirement, effective));
    }
    return List.copyOf(derived);
}

public List<MachineOutput> runtimeMachineOutputs(List<RecipeModifier> extraModifiers) {
    List<RecipeModifier> effective = combineModifiers(extraModifiers);
    return machineOutputs().stream()
            .map(output -> output.applyModifiers(effective))
            .toList();
}

private List<RecipeModifier> combineModifiers(List<RecipeModifier> extraModifiers) {
    if (extraModifiers == null || extraModifiers.isEmpty()) return modifiers;
    ArrayList<RecipeModifier> combined = new ArrayList<>(modifiers.size() + extraModifiers.size());
    combined.addAll(modifiers);
    combined.addAll(extraModifiers);
    return List.copyOf(combined);
}
```

将现有 `applyModifiers(requirement)` 改为 `applyModifiers(requirement, List<RecipeModifier>)`，现有无参数方法传入 `modifiers`。保留 Codec getter `modifiers()` 不变，保证 raw serialization 与 equality 行为不变。

- [ ] **Step 4: Add structure modifier snapshot to RecipeCraftingContext**

新增字段和方法：

```java
private List<RecipeModifier> structureModifiers = List.of();

public void setStructureModifiers(List<RecipeModifier> modifiers) {
    structureModifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
}

public List<RecipeModifier> structureModifiers() {
    return structureModifiers;
}

public List<RecipeModifier> effectiveModifiers(MachineRecipe recipe) {
    ArrayList<RecipeModifier> result = new ArrayList<>(recipe.modifiers().size() + structureModifiers.size());
    result.addAll(recipe.modifiers());
    result.addAll(structureModifiers);
    return List.copyOf(result);
}
```

`resetTransientState()` 将 `structureModifiers = List.of()`。所有当前 `recipe.runtimeRequirements()` 调用改为 `recipe.runtimeRequirements(effectiveModifiers(recipe))`，包括 `ioTick`、input/output simulate、commit 前失败检查和 requirement loops。不得把 structure modifiers 写回 recipe。

- [ ] **Step 5: Make ActiveMachineRecipe duration use the same snapshot**

新增 context-aware duration refresh：

```java
public void refreshTotalTick(RecipeCraftingContext context) {
    this.totalTick = IntegrationTypeHelper.asInt(
            IntegrationTypeHelper.applyDuration(
                    context.effectiveModifiers(recipe), recipe.getRecipeTotalTickTime()));
}
```

`ActiveMachineRecipe.start(context)` 在成功 commit input 后调用 `refreshTotalTick(context)`；构造器仍按 recipe-local modifiers 初始化，保证现有 direct constructor 测试兼容。`start` 成功后、context 已设置 structure snapshot 后的 total tick 必须成为最终 duration。NBT 字段和序列化逻辑不变。

- [ ] **Step 6: Inject snapshot through RecipeCraftingContextPool and search**

在 `RecipeCraftingContextPool.borrow` 创建或 reset context 后执行：

```java
context.setStructureModifiers(controller.foundModifierList());
```

`returnContext` 继续调用 `resetTransientState()`，确保 pooled context 不携带旧 structure modifiers。`RecipeSearchTask.compute()` 无需自己拼 list；它调用 context 的 simulate API，context 已持有 snapshot。`MachineControllerBlockEntity.applySearchResult` 在 `next.start(nextContext)` 前后保持同一 context，避免 search 与 start 使用不同 modifier 集合。

- [ ] **Step 7: Run focused tests and verify they pass**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.recipe.MachineRecipeTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.api.recipe.RecipeSearchTaskTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`；recipe-local regression、structure modifier 合并、context pool reset 和 search path 全部通过。

- [ ] **Step 8: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextPool.java src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchTask.java src/test/java/cn/howxu/mmcr/api/recipe/MachineRecipeTest.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java
git commit -m "feat(recipe): apply structure position modifiers at runtime"
```

---

### Task 7: 补齐朝向、selector tag 与 runtime acceptance tests

**Files:**
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/StructureMatcherTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/CompiledMachinePatternTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`

**Interfaces:**
- Consumes: Tasks 1–6 的 public API 和 controller lifecycle。
- Produces: 阶段 3B 的回归与验收证据，不改变 production API。

- [ ] **Step 1: Add all horizontal rotation assertions**

使用同一 raw position 和 `BlockRotator.rotateSouthTo` 构造四个水平 world layout：

```java
for (Direction facing : Direction.Plane.HORIZONTAL) {
    BlockPos worldPos = controllerPos.offset(BlockRotator.rotateSouthTo(rawPos, facing));
    Level level = LevelStub.create(Map.of(controllerPos, Blocks.STONE, worldPos, Blocks.GOLD_BLOCK));
    assertThat(StructureMatcher.matches(
            pattern, level, controllerPos, facing, replacementMap)).isTrue();
}
```

同时断言基础 pattern 的 bounds、component positions、port positions 没有因为 replacement map 而增加位置。

- [ ] **Step 2: Add vertical roll assertions**

对 `Direction.UP` 和 `Direction.DOWN` 使用四个 horizontal `rollFacing`，断言：

```java
BlockPos expected = BlockRotator.rotateSouthTo(rawPos, facing, rollFacing);
assertThat(dynamicMachine.rotatedModifierReplacements(facing, rollFacing)).containsKey(expected);
```

使用 `MachineControllerBlock.ROLL_FACING` 的现有 state 构造 controller，验证 fully rotationally symmetric candidate 的四个 roll 都读取对应 replacement map，不把某一 roll 的 position 用到另一 roll。

- [ ] **Step 3: Add selector-tag coexistence regression**

构造两个相同类型、不同 `ProcessingComponent.tags` 的 bus，recipe requirement 限定一个 tag，再给 position replacement 添加 item input modifier。断言 effective modifier 改变数量，但被搜索的 component 集合仍只包含 requirement tag 对应的 bus；不要修改 `ProcessingComponent.matchesTag`。

- [ ] **Step 4: Add output chance and duration assertions**

用 recipe-local modifier 和 structure modifier 分别覆盖：

```java
List<RecipeModifier> structureModifiers = List.of(
        new RecipeModifier("duration", RecipeModifier.IOType.INPUT, 2F,
                RecipeModifier.Operation.MULTIPLY, false),
        new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 2F,
                RecipeModifier.Operation.ADD, false),
        new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 0.5F,
                RecipeModifier.Operation.MULTIPLY, true));
```

断言：

- total tick 使用 duration modifier；
- output stack count 使用 non-chance output modifier；
- output chance 使用 `affectsChance = true` 的 modifier 并经过 clamp；
- raw `recipe.modifiers()`、`requirements()` 和 Codec roundtrip 不变。

- [ ] **Step 5: Run the complete focused acceptance suite**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.machine.BlockArrayTest --tests cn.howxu.mmcr.api.machine.StructureMatcherTest --tests cn.howxu.mmcr.api.machine.CompiledMachinePatternTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`，所有阶段 3B rotation、vertical roll、selector tag、duration/output/chance 和 lifecycle 测试通过。

- [ ] **Step 6: Commit**

```bash
git add src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java src/test/java/cn/howxu/mmcr/api/machine/StructureMatcherTest.java src/test/java/cn/howxu/mmcr/api/machine/CompiledMachinePatternTest.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java
git commit -m "test(stage3b): cover rotated position modifiers"
```

---

### Task 8: 回填项目路线图并执行最终验证

**Files:**
- Modify: `docs/MAIN.md`

**Interfaces:**
- Consumes: Tasks 1–7 的通过测试与最终 commit 状态。
- Produces: 阶段 3B 完成后的路线图状态、当前基线和验证记录。

- [ ] **Step 1: Update the stage status and baseline**

在 `docs/MAIN.md`：

- §1.2 将 “P3B pattern position modifier 仍待做” 改为已完成，并准确描述 single-block replacement、结构内匹配、旋转和 runtime modifier 合并。
- §2.0 将阶段 3B 状态从 `⬜ 未开始` 改为 `✅ 完成`。
- §2.4 将“未开始任务”改为已完成项，并保留 MultiBlock replacement 为后续边界。
- 更新 §2.4 验收描述，明确 JEI 仍未实现，但本阶段不依赖 JEI。
- §5 追加阶段 3B implementation commit 与最终验证命令，使用真实 commit hash，不填写占位内容。

- [ ] **Step 2: Run full compile and test verification**

Run:

```bash
./gradlew compileJava --no-daemon
./gradlew test --no-daemon
```

Expected: 两个命令均输出 `BUILD SUCCESSFUL`，无 test failure。若执行 `check` 不受项目既有配置阻塞，再运行：

```bash
./gradlew check --no-daemon
```

Expected: `BUILD SUCCESSFUL`；任何既有 warning 只记录，不修改无关依赖或构建配置。

- [ ] **Step 3: Inspect final diff and status**

Run:

```bash
git status --short

git diff --check $(git merge-base HEAD dev/neo/26.1.2)..HEAD

git log --oneline -10
```

Expected：

- 只有阶段 3B 源码、测试、`docs/MAIN.md` 和阶段文档计划相关文件发生变化；
- `git diff --check` 无 whitespace error；
- 无 `build/`、缓存、IDE 文件或未授权依赖变更进入 git。

- [ ] **Step 4: Commit documentation and final verification record**

```bash
git add docs/MAIN.md
git commit -m "docs: mark stage 3B complete"
```

最后再次运行：

```bash
./gradlew compileJava --no-daemon && ./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL`。

---

## Implementation Self-Review Checklist

- [ ] `SingleBlockModifierReplacement` 的旧构造器仍能编译，未绑定对象不会进入 machine replacement map。
- [ ] `DynamicMachine` 的 replacement map、列表和 modifier snapshot 不向调用方泄漏可变引用。
- [ ] 基础 pattern 命中优先于 replacement；只有对应坐标的 replacement 可以放宽匹配。
- [ ] `firstMismatch` 与真正 formation 使用完全相同的 replacement 判断。
- [ ] horizontal facing、vertical facing 和 roll-facing 使用 `BlockRotator` 的同一公式。
- [ ] compiled path 与 vertical fallback path 不会使用不同的 replacement 位置。
- [ ] 相同 modifier name 只应用一次；结构 reset 和 context pool return 会清理旧 modifier。
- [ ] recipe search、active recipe duration、ioTick、simulate、commit 共享同一 effective modifier list。
- [ ] selector tag 仍只由原有 `ProcessingComponent` 和 requirement tag 路由。
- [ ] recipe raw serialization、NBT 字段和阶段 3A 行为没有被结构 modifier 污染。
- [ ] `MultiBlockModifierReplacement` 未被本阶段无意接入。
- [ ] `docs/MAIN.md` 的阶段状态、当前基线和验证结果与真实 git/test 输出一致。
