# JEI Data Component Item Input 导出设计

## 目标

让带 `DataComponentPredicateSet` 的机器物品输入以 JEI 原生可处理的 `ItemStack` 导出，并确保 JEI 原生渲染和 Tooltip 能显示完整组件信息。JEI 不应依赖或理解 MMCR 自定义 predicate。

## 现状与问题

`MachineRecipeDisplay.from` 当前调用 `DataComponentPredicateSet.displayStack` 生成输入显示栈，然后通过 `IRecipeSlotBuilder.addItemStacks` 交给 JEI。JEI 的默认 `ItemStackRenderer` 会直接渲染 `ItemStack`，并通过 `ItemStack.getTooltipLines` 生成 Tooltip，因此 JEI 这一层已经支持任意 Data Component。

问题在于 predicate 到 `ItemStack` 的反向转换。现有实现通过 `exactValue` 并对附魔做特殊解析，无法正确表达范围、部分文本、部分 map/list 或第三方组件，且会把匹配条件错误地当成确定的物品状态。

## 设计

### Predicate 导出边界

`DataComponentPredicateSet` 提供一个面向显示导出的、可失败的通用转换。只有当所有 predicate 都是可通过对应 `DataComponentType` codec 无歧义解析的 `Exact` 时，才生成 `DataComponentPatch`；否则返回空结果。

该转换不得按具体组件类型写特判，不得手动重建附魔或其他组件。`TextValue`、`Range`、`MapValue`、`ListValue` 等非精确 predicate 不参与组件 patch 导出。

### JEI 显示模型

`MachineRecipeDisplay.ItemInputDisplay.stacks` 继续使用 `List<ItemStack>`。这是 JEI 的原生输入类型，且 `ItemStack` 自身携带组件 patch、数量和物品类型。

生成输入显示栈时：

1. 从输入 `Ingredient` 枚举候选物品。
2. 若 predicate set 可导出精确 patch，为每个候选物品创建带该 patch 的 `ItemStack`。
3. 若 predicate set 不可导出，创建不带伪造组件的基础 `ItemStack`。
4. 将结果交给现有 `jeiSlot.addItemStacks`，不在 JEI 层添加 predicate 解析逻辑。

### Tooltip

JEI 原生 Tooltip 保持不变。对于精确 patch，JEI 调用 Minecraft 原生 `ItemStack.getTooltipLines`，显示名称、附魔、Lore、Mod Data Component Tooltip 等内容。

对于无法无损导出的 predicate，`MachineRecipeCategory` 追加一段约束 Tooltip，明确显示该输入仍有额外匹配条件。该回调只追加内容，不替换 JEI 原生 Tooltip；消耗概率和 Keep 信息继续沿用现有回调。

约束文本应基于 predicate 的已有序列化/字符串表示生成，不能为每个 Mod 的组件类型添加解析分支。若当前文本不足以让用户理解约束，则使用稳定、简洁的通用提示，而不是伪造组件值。

## 数据流

```text
MachineIngredient.ItemIngredient
  -> ItemRequirement
  -> MachineRecipeDisplay.from
  -> DataComponentPredicateSet 导出精确 DataComponentPatch（可选）
  -> ItemStack / 基础 ItemStack
  -> ItemInputDisplay.stacks
  -> jeiSlot.addItemStacks
  -> JEI TypedItemStack 保存组件 patch
  -> Minecraft ItemStackRenderer 渲染与生成 Tooltip
```

## 错误处理

- 未注册或 codec 无法解析的组件不应导致 JEI 注册失败；该输入回退为基础 ItemStack。
- 非精确 predicate 不应被当作精确值写入显示栈。
- 输入 Ingredient 没有候选物品时，保持现有空列表行为。
- 匹配逻辑 `DataComponentPredicateSet.matches` 不改动，显示导出失败不得影响实际配方匹配。

## 验证

- 精确自定义名称和精确附魔通过通用 codec 导出后，显示栈包含对应组件。
- 任意已注册、可 codec 解析的 Mod Data Component 可以导出，不需要组件特判。
- Range、TextValue、MapValue、ListValue 等非精确 predicate 回退为基础物品，并产生约束 Tooltip。
- JEI 显示栈仍能通过 `addItemStacks` 进入槽位，输出栈和流体槽不受影响。
- 运行相关单元测试，并执行 `./gradlew compileJava --no-daemon`。
