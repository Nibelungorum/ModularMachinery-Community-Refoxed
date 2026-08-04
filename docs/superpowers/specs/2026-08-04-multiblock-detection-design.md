# MMCR 多方块检测机制移植阶段设计

日期：2026-08-04

## 1. 背景

当前项目已经有最小可用的机器结构检测：`MachineControllerBlockEntity.serverTick()` 每 tick 通过 `StructureMatcher.matches(machine.pattern(), level, controllerPos, facing)` 检查当前机器的静态 `BlockArray`，再直接根据结果切换 `FORMED` 状态并驱动配方执行。

这个实现能支撑 MVP，但与 `reference/mmce` 的控制器级检测机制差距较大。MMCE 的核心逻辑集中在 `TileMultiblockMachineController.checkStructure()` 和 `TileMachineController.checkAllPatterns()`：控制器保存已匹配机器、已旋转结构、朝向和替换信息；结构成型后优先复验已知结构；未成型时才按蓝图、父机器或注册表候选机器重新匹配。

本阶段文档只定义后续移植范围，不在当前提交中实现代码。

## 2. 阶段目标

- 将检测流程从“每 tick 对当前机器做一次布尔匹配”升级为 MMCE 风格的控制器状态机。
- 在控制器实体上保存结构匹配结果，包括当前 `foundMachine`、`foundPattern`、控制器朝向和成型状态。
- 引入旋转后结构缓存，避免每次匹配都重新对 pattern 坐标做运行时旋转。
- 保留当前 NeoForge 26.1.2 的 `Machine`、`BlockArray`、`BlockPredicate` 和配方执行模型，不直接照搬 1.12.2 TileEntity 架构。
- 为后续蓝图、动态结构、组件选择标签和结构预览留出接口边界。

## 3. 非目标

- 不在本阶段实现 MMCE 的 `DynamicPattern` 可变长度结构。
- 不实现 `TaggedPositionBlockArray` 的组件选择标签和动态 selector tag。
- 不实现蓝图物品、投影、结构预览、自动组装或 GUI 提示。
- 不移植 MMCE 的异步配方线程、事件系统、工厂控制器和并行控制器。
- 不改变当前机器注册 DSL 的公开形态，除非检测状态需要少量只读访问方法。
- 不做旧存档迁移；当前项目还处于移植阶段，优先保持代码简单。

## 4. MMCE 参考机制

### 4.1 `checkStructure()` 的职责

MMCE 的基础控制器检测流程可以拆成三段：

- **检查是否允许检测**：受检测间隔、tick 状态、区块加载和控制器状态影响。
- **复验已成型结构**：如果已有 `foundMachine` 和 `foundPattern`，先检查区块是否加载，再用缓存 pattern 复验；失败时 reset 机器状态。
- **查找新结构**：如果没有已匹配结构，按顺序尝试蓝图机器、父机器、注册表中所有可自动匹配机器。

这套流程的关键不是 API 名称，而是把“结构发现”和“结构复验”分开。当前项目每 tick 都只有一次无缓存的发现式匹配，后续需要改成“先复验、必要时再发现”。

### 4.2 `checkAllPatterns()` 的职责

普通机器控制器在 MMCE 中会遍历 `MachineRegistry`，跳过需要蓝图或工厂专用的机器，对每个候选机器取旋转缓存后的 `TaggedPositionBlockArray`，匹配成功后调用 `onStructureFormed()` 并停止搜索。

迁移到 MMCR 时应保留这几个语义：

- 只扫描允许自动匹配的机器。
- 每个候选机器使用与控制器朝向一致的旋转缓存 pattern。
- 第一个匹配成功的机器成为控制器的当前机器。
- 匹配成功后立即更新控制器状态，不继续搜索其它机器。

### 4.3 `BlockArrayCache` 的价值

MMCE 会缓存每个机器 pattern 在不同控制器朝向下的旋转结果。这样结构检测只需要遍历缓存后的世界坐标偏移，不需要在每个方块匹配时临时旋转。

MMCR 当前的 `StructureMatcher` 在每个 pattern entry 上调用 `BlockRotator.rotateYCCWSouthUntil(...)`。后续应把旋转前移到缓存层：

- 原始 `BlockArray` 仍是机器定义的来源。
- `BlockArrayCache` 根据 `Machine` 或 `BlockArray` 加 `Direction` 返回旋转后的不可变 `BlockArray`。
- `StructureMatcher` 可以增加接收“已旋转 pattern”的入口，减少重复坐标转换。

## 5. 目标架构

### 5.1 控制器检测状态

在 `MachineControllerBlockEntity` 中增加一组服务端状态，语义对齐 MMCE，但命名可以贴合当前代码：

- `Machine foundMachine`：当前结构成功匹配的机器。
- `BlockArray foundPattern`：按控制器朝向旋转后的结构 pattern。
- `Direction controllerFacing`：匹配时使用的控制器朝向。
- `int lastStructureCheckTick` 或等价计数：用于检测间隔。
- `int structureCheckDelay` / `maxStructureCheckDelay`：后续可配置；第一阶段可先用常量。

当前 `machine` 字段可以继续作为配方执行使用的机器引用，但后续要明确它与 `foundMachine` 的关系：结构成型后 `machine = foundMachine`，结构 reset 时清空 active recipe 和成型状态。

### 5.2 检测入口

`serverTick()` 应拆成更清晰的阶段：

- 绑定默认机器或读取控制器指定机器。
- 调用 `doStructureCheck()`，由它决定是否实际执行检测。
- 根据 `isFormed()` 和 `foundMachine` 驱动配方查找与执行。
- 广播状态。

`doStructureCheck()` 负责间隔控制，`checkStructure()` 负责 MMCE 风格状态机，避免 `serverTick()` 继续堆积检测细节。

### 5.3 结构 reset 与 formed 更新

新增 `resetMachine(boolean markDirty)` 或等价私有方法，统一处理结构失败后的状态清理：

- 清空 `foundMachine` 和 `foundPattern`。
- 清空当前 active recipe 和 tick 进度。
- 将控制器方块状态 `FORMED` 设为 false。
- 需要时 `setChanged()` 并触发客户端同步。

新增 `onStructureFormed(Machine machine, BlockArray rotatedPattern)` 或等价方法：

- 设置 `foundMachine`、`foundPattern`、`machine`。
- 将 `FORMED` 设为 true。
- 重置检测延迟。
- 标记并同步状态。

### 5.4 候选机器选择

第一阶段建议只支持两类候选：

- 控制器自身绑定的默认机器：保持当前内置控制器与默认机器的行为。
- 注册表中所有自动匹配机器：对齐 MMCE 普通控制器的 `checkAllPatterns()`。

蓝图机器、父机器、工厂专用过滤和 KubeJS 细粒度开关先只保留方法边界，不引入未使用字段。

### 5.5 区块加载边界

MMCE 在复验前会检查 pattern bounding box 是否已加载，避免未加载区块导致结构误判和资源消耗。NeoForge 阶段可先提供 `BlockArray.boundsAround(controllerPos)` 或等价方法，再在 `checkStructure()` 中跳过未加载区域。

如果当前 `BlockArray` 没有 min/max 信息，可以在构建或缓存时计算边界。不要在每次检测时重复扫描边界，除非 pattern 很小且实现更简单。

## 6. 分阶段落地建议

### 阶段 A：静态结构检测状态化

- 新增控制器的 `foundMachine` / `foundPattern` 状态。
- 拆出 `doStructureCheck()`、`checkStructure()`、`checkAllPatterns()`、`resetMachine()`、`onStructureFormed()`。
- 保持当前 `StructureMatcher` 的布尔匹配逻辑，先不引入旋转缓存。
- 验证：已有 GameTest 和单元测试继续通过，结构破坏后 active recipe 被清空。

### 阶段 B：旋转 pattern 缓存

- 增加 `BlockArrayCache`，缓存 `BlockArray + Direction` 的旋转结果。
- 调整 `StructureMatcher` 支持直接匹配已旋转 pattern。
- 验证：四个水平朝向的结构匹配结果不变，重复 tick 不重新生成旋转 pattern。

### 阶段 C：候选扫描与检测间隔

- 将控制器从“只能匹配自身绑定机器”扩展为“可扫描所有自动匹配机器”。
- 引入基础检测间隔和失败后延迟增长。
- 验证：多个机器注册时，控制器可识别第一个匹配结构；未成型状态不会每 tick 全表重扫。

### 阶段 D：后续动态结构预留

- 设计 `DynamicPattern` 的现代化接口，但不直接耦合进静态检测主路径。
- 为可变长度结构的匹配结果定义 `size`、`matchFacing` 和展开后的 pattern。
- 等静态检测稳定后再接入组件标签、蓝图和预览。

## 7. 测试重点

- 静态结构四向匹配仍正确。
- 已成型结构在未变化时不会被 reset。
- 任一 required block 被破坏后，控制器变为未成型并停止当前配方。
- 结构恢复后，控制器能重新成型并重新开始配方。
- 多个机器注册时，自动匹配只选择符合结构的机器。
- 未加载区块不应把已成型结构误判为彻底失败；第一阶段如暂不实现区块加载检查，需要在风险中明确。
- 检测缓存不能跨机器或跨朝向污染结果。

## 8. 风险与注意事项

- `machine` 与 `foundMachine` 的语义如果混用，容易导致配方查找使用旧机器。实现时应集中封装状态切换。
- 当前 IO 端口搜索仍按控制器附近固定范围查找，不是真正基于 pattern 组件位置；这会影响后续 selector tag，但不阻塞基础结构检测状态化。
- `BlockPredicate.OfBlockState` 当前使用引用比较，若后续结构匹配要严格匹配属性，需要确认现代 `BlockState` 的比较语义是否足够。
- 注册表扫描顺序会影响多个结构重叠时的匹配结果；如果需要稳定行为，应明确 `MachineRegistry` 的迭代顺序。
- 检测间隔引入后，测试需要避免假设“放置或破坏后下一 tick 立刻变化”，除非测试显式绕过延迟。

## 9. 完成定义

该阶段完成后，项目应具备 MMCE 多方块检测机制的基础骨架：控制器能缓存已匹配结构、复验结构、失败 reset、必要时扫描候选机器，并且这些行为由测试覆盖。动态结构、蓝图和组件标签仍作为后续阶段处理。
