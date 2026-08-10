# JEI Data Component Item Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 MMCR 物品输入的 Data Component predicate 在可无损时导出为带组件的原生 `ItemStack`，并让不可无损导出的约束通过 JEI Tooltip 保留，而不让 JEI 依赖 MMCR predicate。

**Architecture:** `DataComponentPredicateSet` 提供基于组件 codec 的精确 `DataComponentPatch` 导出，只接受 `ComponentPredicate.Exact`。`MachineRecipeDisplay` 使用该 patch 构造 JEI 输入栈，否则回退为基础候选物品。`MachineRecipeCategory` 仅追加通用约束 Tooltip；JEI 继续使用原生 `ItemStack` renderer 和 Tooltip 流程。实现顺序固定为 Task 1 -> Task 2 -> Task 3 -> Task 4，因为后续任务直接消费前一任务公开的接口。

**Tech Stack:** Java, Minecraft 26.1.2, NeoForge, JEI API from `reference/jei`, JUnit 5, AssertJ, Gradle.

## Global Constraints

- 不得在显示导出路径中硬编码 `DataComponents.ENCHANTMENTS` 或任何具体组件类型。
- 保持 `DataComponentPredicateSet.matches` 的实际匹配逻辑不变。
- `MachineRecipeDisplay.ItemInputDisplay.stacks` 保持 `List<ItemStack>`，继续调用 `IRecipeSlotBuilder.addItemStacks`。
- 非精确 predicate 不得被猜测成具体组件值。
- 保留工作区中无关的用户改动，只修改每个任务列出的文件。
- 禁止运行 `./gradlew runClient --no-daemon`。
- 不新增 Java 类；如果实现确实需要新增类，必须添加 `@author howxu <dev@howxu.cn>` Javadoc。

---

### Task 1: 添加通用精确 Predicate 导出

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/component/DataComponentPredicateSet.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/component/ComponentPredicates.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/component/ComponentPredicateTest.java`

**Interfaces:**
- Consumes: `DataComponentPredicateSet.values`, `ComponentPredicate.Exact`, `DataComponentType.codec()`。
- Produces: `Optional<DataComponentPatch> DataComponentPredicateSet.exactPatch()` 和 `boolean DataComponentPredicateSet.hasNonExactValues()`。

- [ ] **Step 1: 编写失败测试**

在 `ComponentPredicateTest` 中增加三个测试：

1. Exact 自定义名称能导出 patch：

```java
assertThat(predicates.exactPatch()).isPresent();
assertThat(predicates.exactPatch().orElseThrow().get(DataComponents.CUSTOM_NAME))
        .isEqualTo(Component.literal("Required"));
```

2. `ComponentPredicate.range(1, 4)` 不能导出 patch：

```java
assertThat(predicates.exactPatch()).isEmpty();
assertThat(predicates.hasNonExactValues()).isTrue();
```

3. Exact `DataComponents.ENCHANTMENTS` 通过该组件自己的 codec 生成后，`exactPatch()` 返回相同的 `ItemEnchantments`。测试不得调用或引用任何附魔专用 helper。

- [ ] **Step 2: 运行失败测试**

Run:

```bash
./gradlew test --tests 'cn.howxu.mmcr.api.recipe.component.ComponentPredicateTest' --no-daemon
```

Expected: 因为 `exactPatch()` 和 `hasNonExactValues()` 尚不存在而编译失败。

- [ ] **Step 3: 实现通用 patch 导出**

在 `DataComponentPredicateSet` 中增加：

```java
public Optional<DataComponentPatch> exactPatch() {
    DataComponentPatch.Builder patch = DataComponentPatch.builder();
    for (var entry : values.entrySet()) {
        if (!(entry.getValue() instanceof ComponentPredicate.Exact exact)) {
            return Optional.empty();
        }
        Object value = ComponentPredicates.exactValue(entry.getKey(), exact);
        if (value == null) {
            return Optional.empty();
        }
        setPatchValue(patch, entry.getKey(), value);
    }
    return Optional.of(patch.build());
}

public boolean hasNonExactValues() {
    return values.values().stream()
            .anyMatch(predicate -> !(predicate instanceof ComponentPredicate.Exact));
}
```

实际实现中用一个私有泛型 helper 调用 `DataComponentPatch.Builder#set`，保持 `DataComponentType<T>` 与 codec 返回值的类型对应；禁止通过组件类型分支处理附魔或其他内置组件。

在 `ComponentPredicates` 中让 `exactValue` 只执行以下逻辑：非 `Exact` 返回 `null`，Exact 值通过现有 registry-aware `COMPONENT_OPS` 和对应 `type.codec()` 解析。删除 `exactEnchantmentValue`，并移除随之不再使用的 `DataComponents`、`Identifier`、`ResourceKey`、`BuiltInRegistries`、`ItemEnchantments` 导入。不要修改 `matches` 的通用编码比较路径。`DataComponentPatch.Builder#set` 的调用必须位于一个私有泛型方法中，签名固定为 `private static <T> void setPatchValue(DataComponentPatch.Builder builder, DataComponentType<T> type, Object value)`，在方法内将 codec 结果安全转换为 `T` 后执行 `builder.set(type, typedValue)`。

- [ ] **Step 4: 运行测试**

Run:

```bash
./gradlew test --tests 'cn.howxu.mmcr.api.recipe.component.ComponentPredicateTest' --no-daemon
```

Expected: PASS。

- [ ] **Step 5: 提交任务 1**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/component/DataComponentPredicateSet.java \
        src/main/java/cn/howxu/mmcr/api/recipe/component/ComponentPredicates.java \
        src/test/java/cn/howxu/mmcr/api/recipe/component/ComponentPredicateTest.java
git commit -m "feat: export exact component predicates as patches"
```

---

### Task 2: 从导出契约构造 JEI 输入栈

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplay.java:37-90`
- Test: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java`

**Interfaces:**
- Consumes: `DataComponentPredicateSet.exactPatch()`、`DataComponentPredicateSet.hasNonExactValues()`、`ItemRequirement.item().items()`。
- Produces: `ItemInputDisplay.stacks()` 中的精确组件栈或基础回退栈，以及 `ItemInputDisplay.hasUnexportedComponentConstraints()`。

- [ ] **Step 1: 编写显示行为测试**

调整现有 `MachineRecipeDisplayTest` 中依赖 `displayStack` 的测试，覆盖以下行为：

1. Exact 自定义名称生成带 `CUSTOM_NAME` 的输入栈。
2. Exact 附魔生成带相同 `ENCHANTMENTS` 的输入栈。
3. `TextValue` 生成不带伪造组件的基础钻石剑，且 `hasUnexportedComponentConstraints()` 为 `true`。
4. `Range` 生成基础候选栈，且 `hasUnexportedComponentConstraints()` 为 `true`。

使用 `ItemStack.get(DataComponents...)`、`isComponentsPatchEmpty()` 和新的 display flag 断言。测试不应引用 `exactEnchantmentValue`。

- [ ] **Step 2: 运行显示测试并确认旧实现不满足新契约**

Run:

```bash
./gradlew test --tests 'cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest' --no-daemon
```

Expected: 在移除 `displayStack` 调用和增加 display flag 前，测试编译失败或旧的 TextValue 断言失败。

- [ ] **Step 3: 修改 `MachineRecipeDisplay.from`**

将当前：

```java
item.components().displayStack(holder.value(), item.count())
```

替换为按以下规则生成栈：

```java
List<ItemStack> stacks = item.item().items()
        .map(holder -> item.components().exactPatch()
                .map(patch -> new ItemStack(holder, item.count(), patch))
                .orElseGet(() -> new ItemStack(holder, item.count())))
        .toList();
boolean hasUnexportedConstraints = item.components().hasNonExactValues()
        || item.components().exactPatch().isEmpty() && !item.components().isEmpty();
itemInputs.add(new ItemInputDisplay(stacks, item.count(), item.consumeChance(), hasUnexportedConstraints));
```

使用当前 26.1.2 API 的 `ItemStack(Holder<Item>, int, DataComponentPatch)` 构造器直接创建带 patch 的栈，不需要引入 `ItemStackTemplate`。不要在 `MachineRecipeDisplay` 中解析 predicate 内容。`exactPatch()` 只调用一次并保存到局部变量，避免对同一输入重复计算。

给 `ItemInputDisplay` 增加 `boolean hasUnexportedComponentConstraints` record 字段，并保留旧三参数构造器，用默认 `false` 适配没有组件约束的现有调用点。其 compact constructor 继续复制 stacks。

- [ ] **Step 4: 运行显示测试**

Run:

```bash
./gradlew test --tests 'cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest' --no-daemon
```

Expected: PASS，且输出组件测试、布局测试、排序测试均保持通过。

- [ ] **Step 5: 提交任务 2**

```bash
git add src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplay.java \
        src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java
git commit -m "feat: export component predicates to JEI item stacks"
```

---

### Task 3: 追加不可导出约束 Tooltip

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java`
- Test: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java`

**Interfaces:**
- Consumes: `ItemInputDisplay.hasUnexportedComponentConstraints()` 和现有 `appendInputTooltip` callback。
- Produces: JEI 原生 ItemStack Tooltip 后追加一行稳定的“存在额外匹配条件”提示。

- [ ] **Step 1: 添加失败的 Tooltip 相关测试**

增加一个无需启动客户端 renderer 的纯逻辑断言，确认带 `TextValue` / `Range` 的 `ItemInputDisplay` 标记为 `true`，空组件和 exact 组件标记为 `false`。同时确认现有 `Keep` / 消耗概率文本的测试行为不变。

不要尝试在单元测试中实例化 JEI 的实际 Tooltip builder；JEI 原生链路已经由 `reference/jei` 源码确认使用 `ItemStack.getTooltipLines(...)`，这里测试 MMCR 提供给回调的数据即可。

- [ ] **Step 2: 修改 `appendInputTooltip`**

保留现有 Keep / 消耗概率逻辑，并追加：

```java
if (item.hasUnexportedComponentConstraints()) {
    tooltip.add(Component.translatable("jei.mmcr.machine_recipe.component_constraints"));
}
```

该行必须通过 `addRichTooltipCallback` 追加在 JEI 原生 ItemStack Tooltip 之后，不得调用 `tooltip.clear()`、`tooltip.clearIngredient()` 或替换原生内容。不要把 predicate 序列化成未经用户要求的原始 JSON；使用稳定的通用翻译文本即可。

- [ ] **Step 3: 添加中英文翻译**

在 `Translations` 的英文和中文 JEI machine recipe Map 中分别加入：

```java
Map.entry("jei.mmcr.machine_recipe.component_constraints", "Additional component requirements apply")
Map.entry("jei.mmcr.machine_recipe.component_constraints", "还有额外的组件匹配条件")
```

沿用 `Translations` 现有 Map 结构、排序和格式，不修改其他翻译。不要新建静态语言 JSON 文件；本项目语言资源由 DataGen 生成。

- [ ] **Step 4: 运行相关测试**

Run:

```bash
./gradlew test --tests 'cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest' --no-daemon
```

Expected: PASS。

- [ ] **Step 5: 提交任务 3**

```bash
git add src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java \
        src/main/java/cn/howxu/mmcr/datagen/Translations.java \
        src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java
git commit -m "feat: explain unexported component constraints in JEI"
```

---

### Task 4: 完整验证并审查变更

**Files:**
- Verify: `src/main/java/cn/howxu/mmcr/api/recipe/component/ComponentPredicates.java`
- Verify: `src/main/java/cn/howxu/mmcr/api/recipe/component/DataComponentPredicateSet.java`
- Verify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplay.java`
- Verify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java`
- Verify: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java`

- [ ] **Step 1: 搜索残留的附魔特判和旧显示 API**

Run:

```bash
rg "exactEnchantmentValue|displayStack\(" src/main/java src/test/java
```

Expected: 不再有 `exactEnchantmentValue`；不再有 JEI 显示路径调用 `DataComponentPredicateSet.displayStack`。如果 `displayStack` 因其他明确用途仍存在，必须确认它不再被 JEI 使用并在计划外不要删除。

- [ ] **Step 2: 运行完整测试**

Run:

```bash
./gradlew test --no-daemon
```

Expected: PASS。

- [ ] **Step 3: 编译 Java**

Run:

```bash
./gradlew compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 检查最终 diff 和工作区**

Run:

```bash
git diff HEAD~3..HEAD --stat
git status --short
```

确认 diff 只包含本功能的实现、测试和翻译；不要回滚其他协作者改动。若任务 1 到 3 因现有工作区提交历史无法严格对应三个 commit，只检查实际 diff 内容，不强行重写提交。

- [ ] **Step 5: 最终提交或报告遗留风险**

若所有测试和编译通过，只 stage 本功能实际修改的文件，使用项目风格提交最终收尾变更：

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/component/ComponentPredicates.java \
        src/main/java/cn/howxu/mmcr/api/recipe/component/DataComponentPredicateSet.java \
        src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java \
        src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplay.java \
        src/main/java/cn/howxu/mmcr/datagen/Translations.java \
        src/test/java/cn/howxu/mmcr/api/recipe/component/ComponentPredicateTest.java \
        src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java
git commit -m "test: verify JEI component item input export"
```

若验证失败，记录精确失败命令、错误位置和是否为现有环境问题，不声称修复完成。
