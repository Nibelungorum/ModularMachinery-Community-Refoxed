# 架构概览

## 机器等级系统

机器等级由启动期的 `MachineLevelRegistry` 管理。等级类型提供显示名称；每个具体等级关联一个类型、唯一的 priority、精确方块状态和 `LevelModifier`。注册表只在 KubeJS 启动脚本加载期间开放，随后冻结，以保证结构和配方重载期间等级定义稳定。

服务器脚本通过 `MMCR.levelSlot(typeId)` 将类型化等级槽位写入 `MachineStructureDefinition`。结构匹配先验证槽位方块属于对应类型，再在控制器形成时解析具体等级。每种类型只能解析出一个具体等级：同类型多个槽位存在混级时，形成失败并记录不匹配位置；不同类型独立解析。等级槽位没有客户端结构预览渲染，这是当前有意保留的限制。

`MachineRecipe` 保存可选的 `LevelRequirement(typeId, levelId)`。配方搜索使用等级 priority 比较，要求满足 `actual.priority >= required.priority`。候选配方按配方 priority 排序；输入兼容的最高 priority 配方会先被检查，等级不足时不会回退运行较低 priority 的同输入配方。

运行时，`RecipeCraftingContext` 从已形成机器的等级快照生成效果。类型 ID 顺序保证乘数合并稳定：持续时间、能耗和产出 multiplier 相乘，parallelism 与 factory thread bonus 相加。等级 multiplier 在普通配方 modifier 和结构 modifier 之前生效。数值缩放向下取整；正数物品和流体产出在等级缩放后最少保留一个单位，避免低于 1 的正 multiplier 抹除原有产出。

完整的脚本 API、生命周期和示例见 [KubeJS 集成](kubejs-integration.md)。
