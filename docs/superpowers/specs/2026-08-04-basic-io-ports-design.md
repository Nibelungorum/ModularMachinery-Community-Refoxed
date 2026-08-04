# MMCR Basic 输入输出端口拆分设计

日期：2026-08-04

## 1. 背景

当前实现用三个通用注册项承载物品、流体、能量端口，并通过 `IOPortBlock.IO_TYPE` 方块状态在输入和输出之间切换。对应 BlockEntity 也只保存一份同时可读写的能力。该结构与 `reference/mmce` 不一致：MMCE 为每种资源分别注册输入和输出方块，TileEntity 在构造时固定 `IOType`，外部能力访问方向由端口类型决定。

本次只移植 basic 端口的方向拆分，为后续机器组件接入保留清晰边界。

## 2. 目标

- 注册六个独立 basic 方块：
  - `item_input_bus`
  - `item_output_bus`
  - `fluid_input_hatch`
  - `fluid_output_hatch`
  - `energy_input_hatch`
  - `energy_output_hatch`
- 每个端口拥有固定的输入或输出方向，不能通过交互切换。
- 对外暴露的 NeoForge 能力严格遵守方向：
  - 物品输入：允许插入，拒绝抽取。
  - 物品输出：拒绝插入，允许抽取。
  - 流体输入：允许填充，拒绝排出。
  - 流体输出：拒绝填充，允许排出。
  - 能量输入：允许接收，拒绝抽取。
  - 能量输出：拒绝接收，允许抽取。
- GUI 物品槽遵守相同方向限制。
- 保留当前 basic 的容量、槽位数量和菜单显示能力。
- 为注册、能力方向、GUI 方向和机器端到端流转补充测试。

## 3. 非目标

- 不实现 MMCE 输入总线和输出总线的邻接自动搬运逻辑；本阶段仍由控制器直接消费输入、写入输出。
- 不实现 tier/size 变体。
- 不保留旧 `io_port_item_basic`、`io_port_fluid_basic`、`io_port_energy_basic` 注册项，也不做旧世界方块迁移。
- 不接入 AE2、Mekanism、GregTech 或其他第三方能力。

## 4. 架构

### 4.1 方块与实体

保留公共端口抽象，仅把方向从可变方块状态改为类型级别的固定定义：

- 公共 Block 基类负责金属音效、BlockEntity 创建和菜单打开。
- 六个独立 Block 注册项分别绑定一个固定方向的 BlockEntityType。
- `IOPortBlockEntity` 不再从方块状态读取 `IO_TYPE`，改为由具体子类提供固定 `ioType()`。
- 每种资源保留公共存储基类：
  - `ItemBusBlockEntity`：持有物品存储和内部读写接口。
  - `FluidHatchBlockEntity`：持有流体罐和内部读写接口。
  - `EnergyHatchBlockEntity`：持有能量存储和内部读写接口。
- 每个公共基类有明确的 `Input` 和 `Output` 子类。具体子类固定 `IOType`，并返回对应的 `IOPortKind`。

旧 `IOPortBlock.IO_TYPE` 属性、默认状态和 Shift 交互切换逻辑删除。

### 4.2 注册

`PortKinds` 继续作为端口种类描述入口，但每个描述包含独立注册 ID、固定方向和对应实体工厂。`ModBlocks` 和 `ModBlockEntities` 使用相同 ID 配对注册，`ModItems` 继续从方块注册表自动生成方块物品。

注册关系必须保证每个 BlockEntityType 只允许绑定其对应的输入或输出方块，避免一个实体重新承担混合方向。

### 4.3 菜单与 GUI

物品仍使用一个共享菜单类型和布局，但菜单持有公共 `ItemBusBlockEntity` 类型，并根据实体方向创建限制槽：

- 输入总线槽：可放入，不可取出。
- 输出总线槽：不可放入，可取出。

玩家物品栏的快捷移动必须经过这些槽的限制，不允许通过 Shift-click 绕过方向约束。控制器、流体和能量菜单继续使用现有布局；流体和能量本阶段只展示存量，不增加手动转移控件。

### 4.4 能力数据流

Block capability 注册按六个 BlockEntityType 分开完成。能力适配器只控制外部访问；控制器使用端口提供的内部存储接口，因此配方可以从输入端口消费、向输出端口写入，而不会被外部单向限制阻断。

所有实际写入和抽取操作都应标记 BlockEntity 已变化，以保持 GUI、保存数据和客户端同步的一致性。物品、流体和能量存储补充基本 NBT 保存与恢复。

### 4.5 控制器与结构

控制器的输入搜索改为匹配三个具体输入实体，输出搜索改为匹配三个具体输出实体，不再读取 `IO_TYPE` 方块状态。默认机器结构和测试结构改为接受六个 basic 端口；配方执行路径保持现有直接消费/产出逻辑。

## 5. 资源

- 删除旧三个 `io_port_*` 的翻译、模型引用和纹理引用。
- 为六个新注册 ID 生成 block/item 模型和中英文翻译。
- 继续使用现有 basic 资源作为底图；输入和输出分别使用 `reference/mmce` 的 normal 级方向标识（`overlay_inputbus_normal`、`overlay_outputbus_normal`、`overlay_fluidinputhatch_normal`、`overlay_fluidoutputhatch_normal`、`overlay_energyinputhatch_normal`、`overlay_energyoutputhatch_normal`）形成可区分的贴图。
- 模型生成不再生成 `io_type` 属性的 blockstate 变体。

## 6. 测试设计

### 6.1 注册测试

验证：

- 六个 Block、六个 Item、六个 BlockEntityType 均存在。
- 每个 Block 与对应 BlockEntityType 配对。
- 六个实体的 `ioType()` 固定且正确。
- 旧三个 `io_port_*` 不再作为生产注册项。
- 六个方块状态不包含可切换的 `IO_TYPE` 属性。

### 6.2 物品能力测试

分别放置输入总线和输出总线，通过注册到对应 BlockEntityType 的 `Capabilities.Item.BLOCK` 外部能力查询验证：

- 输入总线插入成功、抽取返回空。
- 输出总线插入返回原物品、抽取成功。
- GUI 输入槽禁止取出，GUI 输出槽禁止放入。
- 控制器内部仍能从输入总线消费并向输出总线写入。

### 6.3 流体能力测试

- 输入仓填充成功、排出返回空。
- 输出仓填充返回零、排出成功。
- 存量和容量保持 basic 现有值。

### 6.4 能量能力测试

- 输入仓接收成功、抽取返回零。
- 输出仓接收返回零、抽取成功。
- 存量、容量和单次传输上限保持 basic 现有值。

### 6.5 端到端测试

更新现有配方 GameTest，显式放置物品输入总线、物品输出总线和能量输入仓，验证结构成型、配方完成、输入消耗、输出生成和能量扣除。测试不得通过修改方块状态来制造输出端口。

## 7. 验证命令

实施完成后运行：

- `./gradlew test`
- `./gradlew runGameTestServer`
- 项目已有的 lint 和类型检查任务（以 `build.gradle` 中实际定义为准）

## 8. 决策摘要

本设计选择 MMCE 风格的方向分离，而不是继续扩展通用 IO 状态方块。资源存储和端口注册保留公共抽象以减少重复；输入/输出边界通过独立实体类型、能力适配器和 GUI 槽位同时强制执行。邻接自动搬运、tier 变体和第三方能力留待后续阶段。
