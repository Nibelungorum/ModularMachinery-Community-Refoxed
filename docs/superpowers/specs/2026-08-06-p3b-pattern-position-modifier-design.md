# 阶段 3B：Pattern position modifier 设计

## 1. 背景与目标

阶段 3A 已完成 recipe-local static modifier runtime chain。阶段 3B 在此基础上移植 MMCE 的结构位置级 modifier replacement：某个相对坐标上的特定方块可以替代机器 pattern 该位置的基础方块，并在替代方块实际位于已匹配结构中时，为当前 recipe 追加 modifier。

本阶段直接基于阶段 3A 实现，不等待 JEI。JEI 未实现不改变服务端结构匹配与 recipe runtime 语义。

目标：

- `SingleBlockModifierReplacement` 能绑定相对坐标、replacement predicate 和 modifier 列表。
- replacement 只在对应 machine 的 matched pattern 内参与结构匹配。
- replacement 位置随 machine facing、竖直 controller 的 roll-facing 正确旋转。
- 成型后只收集实际命中的 replacement，并将其 modifier 与 recipe-local modifier 合并。
- modifier 能影响 duration、item/fluid/energy input、item/fluid output 以及 output chance。
- selector tag 的 component 路由语义保持不变。

## 2. 非目标

- 本阶段不实现 JEI category、recipe transfer 或结构预览。
- 本阶段只接入 `SingleBlockModifierReplacement`；已有 `MultiBlockModifierReplacement` 保留为后续扩展，不在本阶段扩大匹配协议。
- 不引入新的硬依赖、旧 Forge 1.12 loader、CraftTweaker 或旧 mixin。
- 不修改 `MachineRecipe` 原始定义，不把结构 modifier 写入 recipe NBT。
- 不改变基础 `BlockArray` 的结构语义；modifier replacement 由 machine 旁路数据维护。

## 3. MMCE 对齐与当前代码适配

MMCE 的对应流程是：

1. `DynamicMachine` 保存 `relative position -> SingleBlockModifierReplacement`。
2. machine 构造 matching replacement map。
3. 结构匹配在基础 block information 不命中时尝试该位置的 replacement。
4. machine 成型后重新检查实际 world block，收集命中的 replacement modifier。
5. controller 创建 `RecipeCraftingContext` 时追加已收集 modifier。

MMCR 保持该数据流，但使用现有类型适配 NeoForge 26.1.2：

- MMCE `BlockInformation` → MMCR `BlockPredicate`。
- MMCE `ModifierReplacementMap` → MMCR 的不可变位置映射及 per-facing compiled replacement map。
- MMCE 的 controller-facing 旋转 → 复用 `BlockRotator.rotateSouthTo`。
- MMCE 的 context modifier → 在 `RecipeCraftingContext` 中合并 recipe modifiers 与结构 modifiers。

## 4. 数据模型

### 4.1 SingleBlockModifierReplacement

`SingleBlockModifierReplacement` 新增以下状态：

- `BlockPos pos`：machine 原始 SOUTH 模板坐标。
- `BlockPredicate replacement`：可替代基础 pattern 的 predicate。
- 既有 `modifierName`、`modifiers`、`description`、`descriptiveStack` 保持不变。

提供位置与 predicate 的 getter，并提供设置位置的链式 API以兼容 MMCE 的 builder 风格。保留现有构造器，新增构造器不破坏已有调用方。

replacement predicate 复用当前 `BlockPredicate.matches(BlockState)`，因此支持现有 block、block state、tag、AnyOf 等 predicate。位置旋转是本阶段必须保证的语义；predicate 本身沿用当前 pattern cache 的不可变复用方式。

### 4.2 DynamicMachine

`DynamicMachine` 增加不可变的 single-block replacement 集合，逻辑上为：

```text
Map<BlockPos, List<SingleBlockModifierReplacement>>
```

旧构造器继续创建空 replacement map。新增 Java/KubeJS builder 入口将 replacement 按相对位置加入 machine 定义；machine 构造完成后对 map、列表和 replacement 数据做 defensive copy。

提供：

- 读取全部 position replacements。
- 按位置读取 replacements。
- 用于 machine definition 构造的追加入口。

同一位置允许多个 replacement；匹配时任一 predicate 命中即可。

### 4.3 CompiledMachinePattern

`CompiledMachinePattern` 为每个支持的 facing 保存旋转后的 replacement map，与现有 rotated pattern、bounding box、component positions、port positions 一起构建。

compiled 数据必须不可变。未配置 replacement 的 machine 返回空 map，不增加结构匹配的额外分支成本以外的行为变化。

## 5. 结构匹配数据流

### 5.1 编译与旋转

`MachinePatternCompiler.compile` 对每个 facing：

1. 取得现有旋转后的基础 `BlockArray`。
2. 将每个原始 replacement 坐标用 `BlockRotator.rotateSouthTo(pos, facing, rollFacing)` 转换。
3. 保留其 predicate、modifier name 和 modifier 列表。
4. 写入当前 facing 的 compiled replacement map。

水平 facing 使用现有 SOUTH/NORTH/EAST/WEST 变换；UP/DOWN 使用现有 roll-facing 路径。基础 pattern 与 replacement map 必须使用同一个变换函数，不能各自实现旋转公式。

### 5.2 结构匹配

`StructureMatcher` 增加带 replacement map 的匹配路径，同时保留现有无 replacement 的 API 以兼容测试与调用方。

每个 pattern entry 的判断顺序：

1. 基础 predicate 命中：该坐标通过。
2. 基础 predicate 未命中且该坐标存在 replacements：按顺序尝试 replacement predicates，任一命中则通过。
3. 没有命中 replacement：返回 mismatch。

`firstMismatch` 也使用相同规则，保证诊断结果与真正的 formation 结果一致。replacement 不会改变 pattern 的 bounding box，也不会把结构范围外方块纳入匹配。

### 5.3 成型后的命中收集

controller 在 machine 成型或结构刷新时：

1. 使用当前已旋转 replacement map 检查世界中对应位置的 block state。
2. 只有实际命中的 replacement 才加入 `foundModifiers`。
3. 使用 modifier name 去重；相同 replacement 被重复遍历不能重复叠加。
4. 结构 reset、machine 更换、结构版本变化时清空 `foundModifiers`。

结构匹配允许 replacement 只负责 formation；命中收集负责 runtime modifier。两者使用同一 facing 与同一相对坐标，避免“能成型但没有 modifier”或“未参与成型却生效”。

## 6. Recipe runtime 合并

`RecipeCraftingContext` 持有当前 controller 成型得到的 structure modifiers，但不修改 `MachineRecipe`。

运行时 modifier 顺序固定为：

```text
recipe.modifiers() + structure modifiers
```

现有无参数 runtime API 继续保留；新增或内部复用带 effective modifier list 的计算路径。以下路径全部使用 effective list：

- `runtimeRequirements`。
- `simulateInputs` / `simulateOutputs`。
- `ioTick`。
- `commitInputs` / `commitOutputs`。
- active recipe duration 初始化与 start 后刷新。

结构 modifier 对输入、输出、energy、duration、chance 的行为与 3A 的 `RecipeModifier` 规则完全一致，包括 operation 顺序、零除保护、数量取整和 chance clamp。

selector tag 仍只通过 recipe requirement tags 与 `ProcessingComponent.tags` 参与路由。position modifier 不添加、删除或重写 component tag，因此 selector tag 行为保持原样。

## 7. 存档与生命周期

结构 modifier 是 machine/world 状态的派生数据，不单独序列化进 recipe：

- active recipe NBT 继续保存 recipe id、tick、total tick、parallelism 和已有 data。
- controller 重新形成结构时重新计算 found modifiers。
- context pool reset 时清理 transient modifier state，禁止旧 controller 或旧 structure version 的 modifiers 泄漏到新 context。
- 结构脏标导致 context 刷新时，新的 context 读取当前 found modifiers。

如果结构重建后 replacement 状态变化，按现有 structureVersion 语义处理，旧 context 不继续使用旧 component 与 modifier 快照。

## 8. 测试策略

### 8.1 数据与匹配

- replacement 保存坐标、predicate 和 modifier 列表，并保持不可变暴露。
- 基础 predicate 不命中时，目标坐标的 replacement 可以使结构匹配成功。
- replacement 位于结构外或位于其他相对坐标时不能放宽匹配。
- 同一坐标多个 replacement 任一命中即可；其他坐标的 replacement 不串用。
- `firstMismatch` 与 `matches` 对 replacement 的判断一致。

### 8.2 朝向与 compiled cache

- SOUTH、NORTH、EAST、WEST 的 replacement 坐标与基础 pattern 使用同一旋转结果。
- UP/DOWN 的 roll-facing 坐标正确，且四次水平 roll 回到原坐标。
- compiled path 与非 compiled path 对同一世界布局返回相同结果。
- replacement 不改变 bounding box 和 component/port position 统计。

### 8.3 Runtime modifier

- 成型后只收集实际命中的 replacement。
- 相同 modifier name 只应用一次。
- recipe-local 与 position modifiers 同时存在时，duration、input、output、energy 和 output chance 均使用合并结果。
- 原始 `MachineRecipe`、Codec roundtrip 和既有 3A 测试结果不受影响。
- selector tag 路由测试确认 position modifier 不改变 component 选择范围。
- reset/reform 后旧 modifier 不会残留到新 recipe context。

## 9. 验收标准

- `./gradlew compileJava --no-daemon` 通过。
- `./gradlew test --no-daemon` 通过。
- 结构内 replacement 能影响 IO、输出和 chance。
- 旋转与镜像语义下 replacement 位置正确；当前 MMCR 结构系统没有独立镜像状态，因此镜像验收通过同一 pattern 的旋转/布局测试覆盖，不能引入新的镜像语义。
- selector tag 现有测试通过。
- 未安装 JEI 时编译与测试不依赖 JEI。
- 完成后回填 `docs/MAIN.md` 的阶段 3B 状态与当前基线。

## 10. 风险与边界

- 当前 `BlockPredicate.OfBlockState` 使用 state identity 匹配；本阶段不新增 directional block-state rotation 体系，replacement 位置旋转沿用现有 pattern predicate 语义。
- `MultiBlockModifierReplacement` 仍未接入；如后续需要按 MMCE 完整移植多方块 replacement，应单独追加 spec，避免把本阶段的 single-block 验收边界扩大。
- recipe search、active recipe duration 和 context pool 必须共享同一 effective modifier 来源，否则可能出现搜索阶段与运行阶段数值不一致；implementation plan 必须把这三处作为同一原子改动验证。
