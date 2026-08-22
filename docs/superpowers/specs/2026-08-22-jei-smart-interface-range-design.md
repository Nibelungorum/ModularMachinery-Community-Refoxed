# JEI 智能接口范围显示设计

## 目标

将 JEI 中智能接口要求的真正范围从 `min - max` 改为 `[min, max]`，同时调整悬停 tooltip。单值要求保持现有的 `x` 显示形式。

## 方案

在 `MachineRecipeDisplay.valueText` 中统一负责范围格式化：

- 当 `minValue == maxValue` 时返回格式化后的单值。
- 当两者不同时返回 `[minValue, maxValue]`。
- 继续使用现有的整数与浮点数格式化规则。

JEI 配方显示和悬停 tooltip 都使用 `SmartInterfaceDisplay.value`，因此只修改这一处即可保证两种显示一致，不改动翻译键或运行时范围判断逻辑。

## 验证

- 覆盖单值输入，确认仍显示 `x`。
- 覆盖不同最小值和最大值，确认显示 `[min, max]`。
- 确认 JEI 配方显示与悬停 tooltip 使用相同格式化结果。
- 运行项目要求的 Gradle 测试与 GameTest 服务端验证。
