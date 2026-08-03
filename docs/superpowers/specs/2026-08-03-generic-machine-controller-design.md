# 通用多方块机器控制器 — 设计规范

## 1. 目标

把“创建多方块机器”改成机器优先的通用方式:用户只定义机器结构与元数据,不再手动创建某个具体机器的控制器方块。每台机器自动派生独立控制器 block/item/block entity 绑定,资源模型由机器定义生成,不再把 blockstate、block model、item model 写死为全局 `controller`。

## 2. 已确认决策

| # | 决策 | 说明 |
|---|---|---|
| D1 | 每台机器拥有独立控制器 block/item/id | 例如 `mmcr:blast_furnace` 自动派生 `mmcr:blast_furnace_controller` |
| D2 | 创建机器时不要求显式创建控制器方块 | KubeJS/API 只注册机器,控制器由机器注册流程自动加入 block/item/BE 注册集合 |
| D3 | 三个 IO 端口继续通用化 | item/fluid/energy 端口等级和值由 MMCR 自己决定,保持类似 reference/mmce 的可扩展端口体系 |
| D4 | 控制器贴图由 `Machine` 指定 | 主面贴图可指定,其余五面可一次指定,也可逐面覆盖 |
| D5 | 资源生成不能硬编码控制器 | blockstate、block model、item model 都从机器控制器定义生成 |

## 3. API 设计

### 3.1 Machine 控制器规格

新增 `MachineControllerSpec` record,作为机器控制器的唯一元数据来源。

```java
public record MachineControllerSpec(
        Identifier id,
        Identifier frontTexture,
        Identifier sideTexture,
        Identifier topTexture,
        Identifier bottomTexture) {

    public static MachineControllerSpec defaultsFor(Identifier machineId) { ... }
}
```

默认规则:

- 控制器 id 默认为 `machineId.withPath(machineId.getPath() + "_controller")`。
- 主面贴图默认 `block/<controller_path>_front` 或 `block/<controller_path>` 中择一固定策略。
- 其余面默认 `block/casing`,保证旧高炉即使未指定贴图也能生成可用模型。

`Machine` 接口增加:

```java
MachineControllerSpec controller();
```

`DynamicMachine` 构造器保留兼容重载,未传 spec 时使用 `MachineControllerSpec.defaultsFor(registryName)`。

### 3.2 Java/KubeJS Builder API

`MachineBuilderJS` 增加控制器贴图 API,参数统一接受 `String` 或 `Identifier`。

一次指定五面:

```java
controllerTextures(Identifier front, Identifier otherFive)
controllerTextures(String front, String otherFive)
```

逐面指定:

```java
controllerFrontTexture(Identifier texture)
controllerSideTexture(Identifier texture)
controllerTopTexture(Identifier texture)
controllerBottomTexture(Identifier texture)
```

可选完整指定:

```java
controllerTextures(Identifier front, Identifier side, Identifier top, Identifier bottom)
```

返回值均为 `MachineBuilderJS`,保持链式调用。`createObject()` 构造 `DynamicMachine(id, localizedName, pattern, controllerSpec)`。

## 4. 注册模型

### 4.1 机器定义先收集

新增机器定义收集层,避免 `MachineRegistry.register(...)` 只在运行期保存机器而无法参与 DeferredRegister。

建议新增 `MachineDefinitions` 或扩展 `MachineRegistry`:

- startup/KubeJS/default machines 先把 `Machine` 放入有序定义集合。
- `ModBlocks` 静态注册阶段遍历当前定义集合,为每台机器注册 controller block。
- `MachineRegistry` 继续作为运行时机器查询表,但不再承担 block 注册职责。

如果 NeoForge/KubeJS 时序无法保证脚本机器在 DeferredRegister 前完成,先实现内建机器路径,并把 KubeJS 机器控制器注册限定在 startup registry event 可达的阶段。不得在游戏运行后动态注册 block。

### 4.2 Blocks / Items / BlockEntities

`ModBlocks`:

- 移除或废弃全局 `CONTROLLER` 作为业务入口。
- 保留旧字段仅作迁移兼容时,不得再被新路径使用。
- 为每个 `Machine` 注册 `MachineControllerBlock(machineId)`。
- 暴露 `controllerFor(Machine machine)` / `controllerFor(Identifier machineId)`。

`ModItems`:

- 遍历所有 block holder 自动注册对应 `BlockItem`。
- 机器控制器 item id 与 controller block id 一致。

`ModBlockEntities`:

- 为每台机器控制器注册独立 `BlockEntityType<MachineControllerBlockEntity>`。
- `MachineControllerBlockEntity` 构造时通过 block 或 BE type 解析机器 id,不再硬编码 `BES.get("controller")`。

## 5. 结构匹配与建造

### 5.1 Pattern 中的控制器

默认机器 pattern 里控制器位置仍由 `C` 表达,但 predicate 应绑定到该机器自己的 controller block。

高炉示例从:

```java
DefaultMachines.blastFurnace(casing, ModBlocks.CONTROLLER.get(), itemPort, fluidPort)
```

改为:

```java
DefaultMachines.blastFurnace(casing, itemPort, fluidPort)
```

构造 `DynamicMachine` 后,controller predicate 使用机器控制器 block 或由 builder 自动插入。

### 5.2 BuildCommand

`BuildCommand.placeMachine(...)` 从机器取控制器 block:

```java
BlockState controllerState = ModBlocks.controllerFor(machine).get().defaultBlockState()
        .setValue(BlockStateProperties.HORIZONTAL_FACING, ctrlFacing);
```

不再引用 `ModBlocks.CONTROLLER`。放置后强制 tick 的逻辑保留。

### 5.3 MachineControllerBlockEntity 绑定机器

删除硬编码默认高炉绑定:

```java
setMachine(MachineRegistry.getMachine(MMCR.id("blast_furnace")));
```

改为通过 controller block/spec 解析 machine id:

- controller block 持有 `Identifier machineId`;或
- block entity type 注册 supplier 捕获 machine id;或
- `ModBlocks.machineForController(block)` 反查。

推荐 controller block 持有 `machineId`,BE 初始化时从 block 读取,简单且不依赖 NBT。

## 6. 资源生成

`ModelGen` 对机器控制器走专用路径:

- blockstate: horizontal facing + `formed` + `active` 状态仍映射同一个 orientable model,除非后续增加 active/formed 覆盖贴图。
- block model: parent 使用 orientable/cube_orientable; `front` 来自 `controller.frontTexture()`。
- side/top/bottom 分别来自 `sideTexture/topTexture/bottomTexture`。
- item model: 指向 controller block model,不再写死 `controller`。

普通 casing 与 IO port 继续按现有通用模型生成。

## 7. 测试与验收

| # | 验收标准 | 验证方式 |
|---|---|---|
| V1 | 默认高炉注册后存在 `mmcr:blast_furnace_controller` 控制器 block/item | 单元测试检查 holder/map |
| V2 | 高炉 pattern 的 `C` predicate 指向高炉控制器,不是全局 `controller` | 更新 `DefaultMachinesTest` |
| V3 | `/mmcr build mmcr:blast_furnace` 放置的是高炉控制器 | 更新 `BuildPlacementConsistencyTest` 或 GameTest |
| V4 | `MachineControllerBlockEntity` 能从自身 controller block 绑定正确机器 | 单元测试或 GameTest |
| V5 | datagen 输出高炉控制器 blockstate/block model/item model,且贴图来自 Machine spec | provider 测试或生成文件检查 |
| V6 | 源码中新路径不再引用 `ModBlocks.CONTROLLER` 作为机器控制器 | grep 验证,允许迁移兼容字段存在但不被业务使用 |

## 8. 风险与边界

- Minecraft/NeoForge 不允许运行期新增 block,所以 KubeJS 机器定义必须发生在 registry 可用阶段;若脚本时序不满足,需要明确限制为 startup machine definitions。
- 旧存档里的 `mmcr:controller` 若存在,本轮不承诺自动迁移到具体机器控制器。
- 这次只做控制器通用化与资源动态化,不新增 active/formed 多贴图状态。
- IO port 等级和值只保留通用注册结构,不在本轮设计里扩展新 tier。

## 9. 实施顺序建议

1. 新增 `MachineControllerSpec`,扩展 `Machine` / `DynamicMachine` / `MachineBuilderJS`。
2. 调整默认高炉定义,让控制器从机器 spec 派生。
3. 改 `ModBlocks` / `ModItems` / `ModBlockEntities` 生成机器控制器注册项。
4. 改 `MachineControllerBlock` / `MachineControllerBlockEntity` 绑定机器 id。
5. 改 `BuildCommand` 与相关测试,移除业务路径里的全局 controller。
6. 改 `ModelGen` 生成机器控制器资源。
7. 运行单元测试与 datagen/build 验证。
