# MMCR 多方块组件上下文移植阶段设计

日期：2026-08-04

## 1. 背景

上一阶段已经把 MMCR 的结构检测从单次布尔匹配推进到 MMCE 风格的控制器状态骨架：控制器保存 `foundMachine`、`foundPattern` 和匹配朝向，并能复验已成型结构或扫描注册表候选机器。

下一阶段不应直接进入 `DynamicPattern`。MMCE 的动态结构、selector tag、配方 requirement 和智能接口都依赖一个更基础的能力：控制器必须知道已匹配结构里有哪些组件方块、这些组件位于哪些结构坐标、它们向配方提供什么输入/输出容器。

当前 MMCR 仍然在 `MachineControllerBlockEntity` 内用控制器附近固定 `3x3` 范围查找 IO：`findAndCheckItemBus`、`findAndCheckFluidHatch`、`findAndCheckEnergyHatch` 和 `outputSlots` 都只扫描 `getBlockPos().offset(dx, 1, dz)`。这不是 MMCE 的结构内组件机制，会导致端口只要离开固定邻近范围，即使仍在 pattern 中，也不能参与配方。

## 2. 本阶段目标

- 将配方 IO 查找从“控制器附近固定范围扫描”改为“基于 `foundPattern` 的结构内组件列表”。
- 建立 MMCR 版 `MachineComponent` / `ProcessingComponent` 的最小骨架，先覆盖 item input/output、fluid input、energy input。
- 在结构成型后收集组件，在结构 reset 或结构变化后清空组件。
- 提供按 IO 类型和资源类型查找组件的内部 API，供当前配方执行路径使用。
- 为后续 MMCE `ComponentSelectorTag`、动态结构展开、升级总线、智能接口和第三方 component proxy 保留扩展点。

## 3. 非目标

- 不实现 `DynamicPattern` 可变长度结构。
- 不实现 `TaggedPositionBlockArray` 和 selector tag 的完整语义。
- 不实现 MMCE 的 `MachineComponentManager` 共享检测、owner 管理和跨机器冲突处理。
- 不实现 `MachineComponentProxyRegistry` 的第三方 TileEntity 代理。
- 不实现 upgrade bus、smart interface、parallel controller、factory controller。
- 不重写当前 recipe requirement 系统；只让现有 `MachineIngredient` 消费结构内组件。

## 4. MMCE 对应位置

### 4.1 控制器组件刷新入口

MMCE 对应：

- `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/tiles/base/TileMultiblockMachineController.java`
- `updateComponents()`，约 757-797 行。

核心行为：

- 如果 `foundMachine`、`foundPattern`、`controllerRotation` 或 replacement 状态缺失，则清空 `foundComponents`、`generalComponents`、`foundModifiers`、`foundSmartInterfaces`，并 reset 机器。
- 每次允许结构检查时，清空旧组件与 modifier，再遍历 `foundPattern.getTileBlocksArray()`。
- 对 pattern 中每个 TileEntity 位置调用 `checkAndAddComponents(...)`。
- 如果没有分组组件，就把 `generalComponents` 放入默认组 `0L`；否则每个分组都合并通用组件。
- 组件刷新后再更新 modifier、multi-block modifier 和结构颜色。

MMCR 对应建议：

- 在 `MachineControllerBlockEntity.onStructureFormed(...)` 之后调用 `updateComponents()`。
- 在 `resetMachine()` 中清空 `foundComponents`。
- 第一版不做 group 合并、modifier、颜色分发，只保留“结构内组件列表”。

### 4.2 单位置组件发现

MMCE 对应：

- `TileMultiblockMachineController.checkAndAddComponents(...)`，约 799-845 行。

核心行为：

- `realPos = ctrlPos.add(pos)`，把 pattern 相对坐标转成世界坐标。
- 如果区块未加载直接跳过。
- 如果 TileEntity 实现 `MachineComponentTile`，调用 `provideComponent()`。
- 否则尝试 `MachineComponentProxyRegistry.INSTANCE.proxy(te)`。
- 如果 TileEntity 实现 `MachineCombinationComponent`，追加多个组件。
- 从 `foundPattern.getTag(pos)` 取 `ComponentSelectorTag`。
- 对每个组件调用 `addComponent(component, tag, te, found)`。
- 顺手识别 parallel controller、upgrade bus、smart interface。

MMCR 对应建议：

- 新增最小接口，例如 `MachineComponentTile`，由端口 BE 实现：`MachineComponent provideComponent()`。
- `MachineControllerBlockEntity` 遍历 `foundPattern.pattern().keySet()`，对每个相对坐标换算世界坐标并读取 `level.getBlockEntity(realPos)`。
- 第一版只接受 MMCR 自有端口 BE，不做 proxy、不做 combination。
- tag 参数暂时为 `null` 或空 Optional，保留在 `ProcessingComponent` 字段中。

### 4.3 组件容器包装

MMCE 对应：

- `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/tiles/base/MachineComponentTile.java`
- `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/crafting/helper/ProcessingComponent.java`
- `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/machine/MachineComponent.java`

核心行为：

- `MachineComponentTile.provideComponent()` 让 TileEntity 提供一个配方组件。
- `ProcessingComponent<T>(MachineComponent<T> component, T providedComponent, ComponentSelectorTag tag)` 把组件定义、实际容器和 selector tag 绑定在一起。
- `MachineComponent` 的子类描述资源类型和 IO 类型，例如 item bus、fluid hatch、energy hatch。

MMCR 对应建议：

- 用 sealed/record 简化第一版：
  - `MachineComponent.ItemInput(ItemInputBusBlockEntity bus)`
  - `MachineComponent.ItemOutput(ItemOutputBusBlockEntity bus)`
  - `MachineComponent.FluidInput(FluidInputHatchBlockEntity hatch)`
  - `MachineComponent.EnergyInput(EnergyInputHatchBlockEntity hatch)`
- 或者保留 MMCE 形态：`MachineComponent<T>` + `providedComponent()`，方便后续扩展。
- 当前阶段推荐后者，因为后续 selector tag、requirement 过滤和 proxy 更接近 MMCE。

### 4.4 Pattern 内方块查询

MMCE 对应：

- `TileMultiblockMachineController.getBlocksInPatternInternal(...)`，约 1257-1273 行。
- `TileMultiblockMachineController.getBlockPossInPatternInternal(...)`，约 1275-1290 行。
- `getBlockPosInPattern(...)` 多个重载，约 1219-1255 行。

核心行为：

- 遍历 `foundPattern.getPattern().keySet()`。
- 将 pattern 相对坐标转换成世界坐标并读取 block state。
- 根据传入 predicate 统计数量或返回结构内相对坐标。

MMCR 对应建议：

- 新增内部方法：`matchedWorldPositions()` 或 `forEachMatchedBlock(BiConsumer<BlockPos relative, BlockPos world>)`。
- 第一版只供组件收集使用，不暴露 KubeJS/命令 API。
- 后续需要 CraftTweaker/KubeJS 查询结构内方块时，再公开只读 API。

### 4.5 具体端口组件来源

MMCE 对应：

- `TileItemInputBus.provideComponent()`：返回 `MachineComponent.ItemBus(IOType.INPUT)`，容器是 `TileItemInputBus.this.inventory`。
- `TileItemOutputBus.provideComponent()`：返回 `MachineComponent.ItemBus(IOType.OUTPUT)`。
- `TileFluidInputHatch.provideComponent()`：返回 fluid input component。
- `TileFluidOutputHatch.provideComponent()`：返回 fluid output component。
- `TileEnergyInputHatch` / energy component：为能量 requirement 提供输入容器。

MMCR 当前对应：

- `src/main/java/cn/howxu/mmcr/internal/tile/ItemInputBusBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/ItemOutputBusBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/FluidInputHatchBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/FluidOutputHatchBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/EnergyInputHatchBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/EnergyOutputHatchBlockEntity.java`

这些类已经固定 `ioType()` 和 `kind()`，下一阶段只需要让它们实现一个轻量 `MachineComponentTile`，把自身或自身 handler 包装成组件。

## 5. 建议落地结构

### 5.1 新增 API/内部类

建议新增：

- `src/main/java/cn/howxu/mmcr/api/machine/MachineComponent.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/MachineComponentTile.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/ProcessingComponent.java`

其中：

- `MachineComponent` 描述资源类型、IO 方向、组件容器。
- `MachineComponentTile` 由端口 BE 实现，提供 `MachineComponent<?> provideComponent()`。
- `ProcessingComponent` 记录组件、世界坐标、相对坐标和预留 tag。

### 5.2 控制器字段

在 `MachineControllerBlockEntity` 中新增：

- `List<ProcessingComponent<?>> foundComponents`，保存当前结构内组件。
- 按资源类型过滤的私有 helper，例如 `itemInputs()`、`itemOutputs()`、`fluidInputs()`、`energyInputs()`。

暂不引入 MMCE 的 `Map<Long, Map<TileEntity, ProcessingComponent<?>>>` 分组结构。原因是 MMCR 当前没有 selector tag 和 group ID，先用线性列表更简单；字段命名保持可迁移，后续加 tag/group 时再扩展。

### 5.3 配方 IO 替换

替换以下固定范围扫描：

- `findAndCheckItemBus(...)`
- `findAndCheckFluidHatch(...)`
- `findAndCheckEnergyHatch(...)`
- `outputSlots()`

新逻辑：

- item input：遍历结构内 `ItemInputBusBlockEntity` component。
- item output：遍历结构内 `ItemOutputBusBlockEntity` component。
- fluid input：遍历结构内 `FluidInputHatchBlockEntity` component。
- energy input：遍历结构内 `EnergyInputHatchBlockEntity` component。

这一步完成后，端口只要在 matched pattern 中，不必位于控制器附近 `3x3`，就能参与配方。

## 6. 测试重点

- 结构成型后，控制器收集 pattern 内所有端口组件。
- 结构 reset 后，组件列表清空，active recipe 被停止。
- item input bus 不在控制器邻近 `3x3`，但在 pattern 内时，配方仍能消费物品。
- item output bus 不在控制器邻近 `3x3`，但在 pattern 内时，配方仍能输出物品。
- fluid input hatch 和 energy input hatch 使用结构内组件查找。
- 不在 pattern 内的端口不应被配方使用，即使它离控制器很近。

## 7. 风险

- 当前 `BlockArray` 只保存 predicate，不知道某个位置是否“应该是组件”。第一版可以扫描所有 matched pattern 坐标上的 BlockEntity；后续 selector tag 需要扩展 pattern 数据结构。
- 组件列表刷新时机必须和 `foundPattern` 同步，否则会出现结构已 reset 但配方继续使用旧端口的问题。
- 如果一个 pattern predicate 是 `AnyOf` 或 tag，实际方块可能是端口也可能不是端口；组件收集应以实际 BlockEntity 是否实现 `MachineComponentTile` 为准。
- 后续如果加入动态结构，必须保证动态展开后的 pattern 才用于组件收集，而不是原始未展开 pattern。

## 8. 完成定义

本阶段完成后，MMCR 的控制器不再依赖固定邻近范围查找 IO。结构成型后，控制器会基于 `foundPattern` 收集实际结构内组件，现有配方执行路径通过组件上下文消费输入和写入输出。后续可以在此基础上继续移植 selector tag、动态结构、upgrade bus 和 smart interface。
