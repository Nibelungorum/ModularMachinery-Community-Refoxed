# MMCE 控制器运行时重新对齐设计

日期：2026-08-05

## 1. 背景

当前控制器已经能完成三件事：

- 多方块结构可以成型，日志中 `formed=true`。
- 默认配方可以被注册并被控制器发现，日志中已经出现 `candidate[1 of 1] mmcr:blast_furnace_iron_to_nugget`。
- `RecipeCraftingContext` 可以执行 `simulateInputs` / `simulateOutputs`，但它找不到结构内端口。

最新运行日志显示，问题已经从“没有配方”推进到“控制器运行时没有正确使用多方块结构上下文”：

```text
[simulateInputs] recipe=mmcr:blast_furnace_iron_to_nugget controllerPos=BlockPos{x=-5, y=57, z=1} ingredients=2
[scanItemBus] scanned 0 input bus(es) in 3x3 above controller; none matched 1x of minecraft:iron_ingot
[scanOutputBus] discovered 0 output bus(es) -> 0 total slot(s) at controllerPos=BlockPos{x=-5, y=57, z=1}
[Ctrl#1] candidate[1 of 1] mmcr:blast_furnace_iron_to_nugget skipped: simulate inputs=false outputs=false priority=0 tickTime=200
```

这说明当前代码的结构检测和配方运行之间存在断层：结构匹配已经确认机器成型，但配方执行没有使用已匹配 pattern 中的组件，而是在控制器上方固定 `3x3` 范围硬扫端口。

## 2. 当前根因

`RecipeCraftingContext` 当前通过以下固定坐标规则寻找输入/输出组件：

```java
BlockPos candidate = controllerPos.offset(dx, 1, dz);
```

受影响的方法包括：

- `findAndCheckItemBus(...)`
- `findAndCheckFluidHatch(...)`
- `findAndCheckEnergyHatch(...)`
- `outputSlots()`

这和 MMCE 不一致。MMCE 的控制器在结构匹配成功后，会基于 `foundPattern` 遍历结构内 TileEntity，收集 `MachineComponentTile` 提供的组件，再让 recipe requirement 从这些组件中路由输入/输出。

因此，当前行为会出现：

- 端口参与了结构成型，但只要不在控制器上方 `3x3`，配方就看不到。
- 结构 pattern 中的 `I` 位置没有被转化为运行时组件列表。
- `formed=true` 和 `simulateInputs=false` 可以同时出现，且不是配方错误。

## 3. 目标

本阶段目标是把控制器运行时重新对齐到 MMCE 的核心模型：

- 结构匹配结果必须成为 recipe tick 的组件上下文。
- 配方 I/O 查找必须基于已成型结构内组件，而不是固定邻近范围扫描。
- 控制器 tick 只负责生命周期调度，具体 I/O 由 `RecipeCraftingContext` 通过组件上下文完成。
- 在此基础上继续移植 MMCE 的 requirement、failure action、per-tick I/O、modifier、parallel/factory 等功能。

## 4. 非目标

本阶段不直接实现完整 MMCE：

- 不一次性移植全部 `ComponentRequirement` 类型。
- 不一次性实现 `DynamicPattern`、selector tag、smart interface、upgrade bus。
- 不实现 AE2 / ME / Mekanism / ModularMagic 等第三方代理组件。
- 不重写配方 JSON / KubeJS API，除非运行时必须。
- 不把当前问题伪装成“默认配方不对”或“玩家摆法问题”。本阶段先修运行时架构断层。

## 5. MMCE 参考模型

### 5.1 结构匹配

MMCE 使用 `BlockArray` / `TaggedPositionBlockArray` 表示机器结构。结构匹配成功后，控制器保存：

- `foundMachine`
- `foundPattern`
- controller rotation / facing
- replacement / modifier 状态

MMCR 当前已有类似字段：

- `MachineControllerBlockEntity.foundMachine`
- `MachineControllerBlockEntity.foundPattern`
- `MachineControllerBlockEntity.controllerFacing`

但这些字段目前没有被 `RecipeCraftingContext` 用来发现端口。

### 5.2 组件刷新

MMCE 的关键逻辑在 `TileMultiblockMachineController.updateComponents()` / `checkAndAddComponents(...)`：

- 遍历 `foundPattern.getTileBlocksArray()` 或 pattern 中所有可能含 TileEntity 的相对位置。
- 将 pattern 相对坐标换算成世界坐标。
- 读取该位置 TileEntity。
- 如果 TileEntity 实现 `MachineComponentTile`，调用 `provideComponent()`。
- 将组件包装成 `ProcessingComponent`，记录 component、实际容器、selector tag、位置等上下文。
- reset 结构时清空组件列表。

MMCR 应先实现这个最小闭环。

### 5.3 RecipeCraftingContext

MMCE 的 `RecipeCraftingContext` 不应该自己猜世界坐标。它基于控制器已收集的组件，执行 requirement 检查、tick I/O、commit 和 failure action。

MMCR 当前 `RecipeCraftingContext` 的职责需要调整：

- 从“自己扫描控制器上方 3x3”改为“读取控制器提供的结构组件上下文”。
- 仍保留 `simulateInputs` / `simulateOutputs` / `commitInputs` / `commitOutputs` 这几个现有边界，降低改动范围。
- 后续再把 `MachineIngredient` 逐步替换或适配到 MMCE 风格 `ComponentRequirement`。

## 6. 推荐落地顺序

### 阶段 A：结构组件上下文

先实现最小组件系统，不碰高级功能。

新增或补齐：

- `MachineComponentTile`：端口 BE 实现，提供组件。
- `MachineComponent`：描述 item/fluid/energy + input/output。
- `ProcessingComponent`：记录组件实例、世界坐标、结构相对坐标、预留 selector tag。
- `MachineControllerBlockEntity.updateComponents()`：结构成型后收集组件，结构 reset 后清空组件。

端口覆盖：

- `ItemInputBusBlockEntity`
- `ItemOutputBusBlockEntity`
- `FluidInputHatchBlockEntity`
- `FluidOutputHatchBlockEntity`
- `EnergyInputHatchBlockEntity`
- `EnergyOutputHatchBlockEntity` 可先收集但暂不参与配方。

完成后，控制器日志应能输出类似：

```text
[Ctrl#1] updateComponents: machine=mmcr:blast_furnace components=3 itemInputs=1 itemOutputs=1 energyInputs=1
```

### 阶段 B：RecipeCraftingContext 使用结构组件

把 `RecipeCraftingContext` 的 I/O 查找替换为组件查找：

- item input：遍历结构内 `ItemInputBusBlockEntity`。
- item output：遍历结构内 `ItemOutputBusBlockEntity`。
- fluid input：遍历结构内 `FluidInputHatchBlockEntity`。
- energy input：遍历结构内 `EnergyInputHatchBlockEntity`。

移除或废弃固定扫描假设：

```java
controllerPos.offset(dx, 1, dz)
```

注意：第一版可以仍然让 `RecipeCraftingContext` 接收 controller / component list，而不是完整移植 MMCE 的 requirement manager。

建议构造形态：

```java
new RecipeCraftingContext(level, controllerPos, machine, components)
```

或者：

```java
new RecipeCraftingContext(controller)
```

推荐第二种，因为后续 MMCE 逻辑经常需要 controller 上下文，例如 failure action、found pattern、component group、modifier、thread 信息。

### 阶段 C：控制器 tick 生命周期对齐

在组件上下文正确后，再整理 tick：

1. server tick 只做生命周期：bind machine -> check structure -> update components -> recipe search/start -> active tick -> broadcast。
2. 结构破坏时必须清空：`foundMachine`、`foundPattern`、components、active recipe、context。
3. recipe start 前先检查 inputs/outputs。
4. active tick 不直接扫描世界，只通过 context 执行 I/O。
5. completion 时 outputs first，inputs second，避免输出失败吞输入。

### 阶段 D：更多 MMCE 功能移植

在 A/B/C 稳定后，再逐步移植：

- per-tick energy drain：`ioTick(currentTick)`。
- failure action：`RESET` / `STILL` / `DECREASE`。
- fluid output。
- recipe modifiers：duration、input/output amount、chance 等。
- requirement routing：从 `MachineIngredient` 过渡到 MMCE 风格 `ComponentRequirement`。
- selector tag / component group。
- dynamic pattern。
- smart interface / upgrade bus。
- parallel controller / factory controller。
- recipe search task / context pool。
- recipe event：start、tick、failure、finish。

## 7. 默认配方说明

当前默认配方仅用于验证最小运行链路：

- id：`mmcr:blast_furnace_iron_to_nugget`
- machine：`mmcr:blast_furnace`
- input：`1x minecraft:iron_ingot`
- energy：`1 FE/t × 200 tick = 200 FE`
- output：`1x minecraft:iron_nugget`
- tickTime：`200`

它不是最终玩法设计，只是一个 smoke test。当前日志已经证明配方可以被找到，真正失败点是结构组件没有进入运行时。

后续可以把默认配方替换成更符合高炉语义的配方，例如：

- `iron_ingot -> steel_ingot`，如果项目已有钢锭。
- `raw_iron + fuel/energy -> iron_ingot`。
- 或仅保留测试配方，但放到测试机器而不是正式高炉。

这属于数据设计问题，不应和本阶段 runtime 修复混在一起。

## 8. 验收标准

阶段 A/B/C 完成后，应满足：

- 高炉结构成型后，控制器能列出结构内所有端口组件。
- 物品输入总线只要在 matched pattern 内，即使不在控制器上方 `3x3`，也能被配方使用。
- 物品输出端口只要在 matched pattern 内，即使不在控制器上方 `3x3`，也能接收输出。
- 能量输入端口只要在 matched pattern 内，也能被 energy ingredient 使用。
- 不在 matched pattern 内的端口不能被配方使用，即使离控制器很近。
- 结构 reset 后，组件列表和 active recipe 同步清空。
- `/mmcr reload` 只能刷新机器/配方注册，不应该成为修复组件上下文的必要条件。

建议验证：

- `./gradlew compileJava --no-daemon`
- 现有相关单测。
- 一个 GameTest 或手动验证：端口位于 pattern 内但不在控制器上方 `3x3`，配方仍能启动。

## 9. 风险

- `BlockArray` 当前只保存 predicate，无法直接知道某个位置是否“语义上是输入总线”。第一版应以实际 BlockEntity 是否实现 `MachineComponentTile` 为准。
- `AnyOf` pattern 允许多个端口类型占同一个 `I` 位置，因此组件发现必须基于实际方块实体，而不是 pattern 字符。
- 如果组件缓存没有和 `foundPattern` 生命周期同步，可能出现结构已经破坏但配方继续使用旧端口。
- 如果 context 只复制组件列表，后续端口被拆除时可能持有失效引用。第一版可以每次 simulate/commit 时校验 BE 仍存在且位置仍在结构内。
- 后续引入 dynamic pattern 后，组件收集必须基于展开后的 pattern，而不是原始 pattern。

## 10. 与现有文档关系

本设计是以下文档的重新排序和收敛：

- `docs/superpowers/specs/2026-08-04-multiblock-component-context-design.md`：本问题的直接修复基础，应优先执行。
- `docs/superpowers/specs/2026-08-04-controller-crafting-tick-design.md`：已有 tick/context 拆分仍有效，但其中“当前 controller search rules”需要替换为结构组件上下文。
- `docs/superpowers/specs/2026-08-04-controller-runtime-mmce-faithful-design.md`：per-tick energy、fluid output、failure action 应在组件上下文完成后再做。
- `docs/MMCE.md`：作为完整 MMCE 功能全景，后续移植功能应继续按该文档拆分。

## 11. 建议下一步

下一步不要继续调默认配方，也不要继续在 `RecipeCraftingContext` 里加更多固定范围扫描。

建议直接实施阶段 A：

1. 给端口 BE 增加 `MachineComponentTile.provideComponent()`。
2. 控制器在 `onStructureFormed(...)` 后基于 `foundPattern` 收集组件。
3. 日志输出收集到的组件数量和坐标。
4. 先不改 recipe commit，只用日志确认组件上下文正确。

确认组件上下文正确后，再实施阶段 B，替换 `RecipeCraftingContext` 的 I/O 查找。
