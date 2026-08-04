# Basic 输入输出端口拆分实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Each task must be implemented by a fresh subagent, reviewed before the next task, and verified with the listed command. Do not create commits unless the user explicitly requests one.

**Goal:** 将当前三个可切换的混合 IO 端口移植为 MMCE 风格的六个 basic 输入/输出端口，并用测试锁定能力和 GUI 的单向行为。

**Architecture:** 保留 `IOPortBlock`、`IOPortBlockEntity` 和 `PortKinds` 作为公共注册框架，但移除 `IO_TYPE` 方块状态。每种资源的 BlockEntity 改为一个公共存储基类加固定方向的 Input/Output 子类；外部 NeoForge capability 使用方向适配器，控制器使用不受外部方向限制的内部存储接口。

**Tech Stack:** Java 25、NeoForge 26.1.2.84、JUnit 5 + AssertJ、NeoForge GameTest、NeoForge Item/Fluid/Energy capabilities。

## Global Constraints

- 六个注册 ID 必须是 `item_input_bus`、`item_output_bus`、`fluid_input_hatch`、`fluid_output_hatch`、`energy_input_hatch`、`energy_output_hatch`。
- 不保留 `io_port_item_basic`、`io_port_fluid_basic`、`io_port_energy_basic` 生产注册项，不做旧世界迁移。
- 不实现 MMCE 邻接自动搬运、不实现 tier/size 变体、不接入第三方能力。
- 输入能力只允许插入/接收/填充；输出能力只允许抽取/排出。
- GUI 物品槽必须遵守同样的方向限制，Shift-click 不得绕过限制。
- 控制器内部消费/产出必须继续工作，不能直接复用受限的外部 capability 适配器。
- 新增或修改的代码不添加注释，沿用现有 Java/NeoForge 风格。
- 每个行为变化必须有 JUnit 或 GameTest 覆盖；涉及方块、能力、菜单的行为必须有 GameTest 或真实菜单测试。
- 本次不执行 `git commit`。

## 文件地图

### 注册和公共协议

- Modify: `src/main/java/cn/howxu/mmcr/internal/port/IOPortKind.java` — 为端口描述增加固定 `IOType`。
- Modify: `src/main/java/cn/howxu/mmcr/registry/PortKinds.java` — 从三种资源描述改为六种方向描述。
- Modify: `src/main/java/cn/howxu/mmcr/internal/block/IOPortBlock.java` — 删除 `IO_TYPE` 状态和切换交互，保留公共菜单方块行为。
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModBlocks.java` — 使用 kind 自身 ID 注册六个方块。
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModBlockEntities.java` — 使用同一 ID 配对六个实体类型。

### Entity 层

- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/IOPortBlockEntity.java` — 把方向改为抽象固定方法。
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/ItemBusBlockEntity.java` — 改为物品公共存储基类。
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/ItemInputBusBlockEntity.java`。
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/ItemOutputBusBlockEntity.java`。
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/FluidHatchBlockEntity.java` — 改为流体公共存储基类。
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/FluidInputHatchBlockEntity.java`。
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/FluidOutputHatchBlockEntity.java`。
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/EnergyHatchBlockEntity.java` — 改为能量公共存储基类。
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/EnergyInputHatchBlockEntity.java`。
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/EnergyOutputHatchBlockEntity.java`。

### 能力、菜单和控制器

- Modify: `src/main/java/cn/howxu/mmcr/internal/event/ModCapabilities.java` — 六类 BlockEntityType 的单向 capability 适配器。
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/ItemBusMenu.java` — 使用公共物品基类和方向槽。
- Create: `src/main/java/cn/howxu/mmcr/internal/menu/DirectionalItemSlot.java` — 物品输入/输出槽位规则。
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/FluidHatchMenu.java` — 使用公共流体基类。
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/EnergyHatchMenu.java` — 使用公共能量基类。
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java` — 按具体 Input/Output 子类查找端口。
- Modify: `src/main/java/org/nibelungorum/DefaultMachines.java` — 默认结构接受六种 basic 端口。

### 资源和翻译

- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java` — 六个 block/item/container 名称。
- Delete: `src/main/resources/assets/mmcr/textures/block/io_port_item_basic.png`。
- Delete: `src/main/resources/assets/mmcr/textures/block/io_port_fluid_basic.png`。
- Delete: `src/main/resources/assets/mmcr/textures/block/io_port_energy_basic.png`。
- Create: `src/main/resources/assets/mmcr/textures/block/item_input_bus.png`。
- Create: `src/main/resources/assets/mmcr/textures/block/item_output_bus.png`。
- Create: `src/main/resources/assets/mmcr/textures/block/fluid_input_hatch.png`。
- Create: `src/main/resources/assets/mmcr/textures/block/fluid_output_hatch.png`。
- Create: `src/main/resources/assets/mmcr/textures/block/energy_input_hatch.png`。
- Create: `src/main/resources/assets/mmcr/textures/block/energy_output_hatch.png`。

### 测试

- Modify: `src/test/java/cn/howxu/mmcr/registry/PortKindsTest.java`。
- Create: `src/test/java/cn/howxu/mmcr/registry/PortRegistrationTest.java`。
- Modify: `src/test/java/cn/howxu/mmcr/test/TestBootstrap.java`。
- Modify: `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`。
- Modify: `src/gametest/java/cn/howxu/mmcr/ItemBusCapabilityGameTest.java`。
- Modify: `src/gametest/java/cn/howxu/mmcr/FluidHatchCapabilityGameTest.java`。
- Modify: `src/gametest/java/cn/howxu/mmcr/EnergyHatchCapabilityGameTest.java`。
- Modify: `src/gametest/java/cn/howxu/mmcr/E2ERecipeRunGameTest.java`。
- Create: `src/gametest/java/cn/howxu/mmcr/PortMenuDirectionGameTest.java`。

---

### Task 1: 锁定六种端口契约

**Files:**
- Modify: `src/test/java/cn/howxu/mmcr/registry/PortKindsTest.java`
- Create: `src/test/java/cn/howxu/mmcr/registry/PortRegistrationTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/test/TestBootstrap.java`

**Interfaces:**
- Consumes: 现有 `PortKinds.all()`, `ModBlocks.BLOCKS`, `ModBlockEntities.BES`, `TestBootstrap.bootstrap()`。
- Produces: 六个固定 ID 的注册契约，以及后续实体类必须实现的 `IOType ioType()` 契约。

- [ ] **Step 1: 将端口单元测试改为六项契约**

在 `PortKindsTest` 中把旧的三项断言改成以下精确集合和顺序：

```java
assertThat(PortKinds.all())
        .extracting(IOPortKind::id)
        .containsExactly(
                "item_input_bus", "item_output_bus",
                "fluid_input_hatch", "fluid_output_hatch",
                "energy_input_hatch", "energy_output_hatch");
```

为 `IOPortKind` 的方向增加断言：

```java
assertThat(PortKinds.all())
        .extracting(IOPortKind::ioType)
        .containsExactly(
                IOType.INPUT, IOType.OUTPUT,
                IOType.INPUT, IOType.OUTPUT,
                IOType.INPUT, IOType.OUTPUT);
```

保留 `register_appends_new_kind`，将 `PortKinds.Simple` 构造改成包含 `IOType.INPUT` 的新签名。

- [ ] **Step 2: 添加生产注册和无状态断言**

在 `PortRegistrationTest` 中复用 `TestBootstrap.bootstrap()`，添加以下测试逻辑：

```java
private static final List<String> IDS = List.of(
        "item_input_bus", "item_output_bus",
        "fluid_input_hatch", "fluid_output_hatch",
        "energy_input_hatch", "energy_output_hatch");

@Test
void all_basic_ports_are_registered_without_io_state() {
    for (String id : IDS) {
        Block block = ModBlocks.BLOCKS.get(id).get();
        assertThat(block).isNotNull();
        assertThat(ModItems.ITEMS).containsKey(id);
        assertThat(ModBlockEntities.BES).containsKey(id);
        assertThat(block.defaultBlockState().properties())
                .noneMatch(property -> property.getName().equals("io_type"));
    }
    assertThat(ModBlocks.BLOCKS).doesNotContainKeys(
            "io_port_item_basic", "io_port_fluid_basic", "io_port_energy_basic");
}
```

固定方向实体的 `ioType()` 断言放在 Task 3 的真实 GameTest 中，避免单元测试依赖尚未绑定到 Minecraft 注册表的 `BlockEntityType.get()`；本单元测试只验证六个 Block/Item/BlockEntityType key 和无 `io_type` 状态。

- [ ] **Step 3: 先运行红灯测试**

Run: `./gradlew test --tests cn.howxu.mmcr.registry.PortKindsTest --tests cn.howxu.mmcr.registry.PortRegistrationTest`

Expected: 编译失败或断言失败，原因是当前仍只有三个 `io_port_*` 注册项且 `IOPortKind` 没有 `ioType()`。

- [ ] **Step 4: 更新测试引导绑定**

在 `TestBootstrap.bootstrap()` 中删除旧三项 `bind` 调用，为六个新 ID 各绑定一个 vanilla 方块。使用不同方块只为避免 holder 未绑定，不把 vanilla 方块类型作为生产行为断言：

```java
bind(ModBlocks.BLOCKS.get("item_input_bus"), Blocks.CHEST);
bind(ModBlocks.BLOCKS.get("item_output_bus"), Blocks.CHEST);
bind(ModBlocks.BLOCKS.get("fluid_input_hatch"), Blocks.BARREL);
bind(ModBlocks.BLOCKS.get("fluid_output_hatch"), Blocks.BARREL);
bind(ModBlocks.BLOCKS.get("energy_input_hatch"), Blocks.COPPER_BLOCK);
bind(ModBlocks.BLOCKS.get("energy_output_hatch"), Blocks.COPPER_BLOCK);
```

Run: `./gradlew test --tests cn.howxu.mmcr.registry.PortKindsTest --tests cn.howxu.mmcr.registry.PortRegistrationTest`

Expected: 仍因实现尚未完成而失败，但失败点应只剩未实现的端口协议或注册，不再是旧测试数据。

### Task 2: 实现固定方向的 Entity 层和六项注册

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/port/IOPortKind.java`
- Modify: `src/main/java/cn/howxu/mmcr/registry/PortKinds.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/IOPortBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/ItemBusBlockEntity.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/ItemInputBusBlockEntity.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/ItemOutputBusBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/FluidHatchBlockEntity.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/FluidInputHatchBlockEntity.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/FluidOutputHatchBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/EnergyHatchBlockEntity.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/EnergyInputHatchBlockEntity.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/EnergyOutputHatchBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/block/IOPortBlock.java`
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModBlocks.java`
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModBlockEntities.java`

**Interfaces:**
- Consumes: Task 1 的 ID/方向契约。
- Produces: `IOPortKind.ioType()`, 六个可实例化 BlockEntity 类，以及 `ModBlocks.BLOCKS`/`ModBlockEntities.BES` 的六项配对。

- [ ] **Step 1: 扩展端口描述接口和注册表**

将 `IOPortKind` 增加固定方向方法，保留现有 capability/tick 扩展方法：

```java
String id();
IOType ioType();
BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory();
```

将 `PortKinds.Simple` 改为：

```java
public record Simple(
        String id,
        IOType ioType,
        BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory)
        implements IOPortKind {}
```

注册六项，工厂必须指向六个具体类：

```java
public static final IOPortKind ITEM_INPUT =
        new Simple("item_input_bus", IOType.INPUT, ItemInputBusBlockEntity::new);
public static final IOPortKind ITEM_OUTPUT =
        new Simple("item_output_bus", IOType.OUTPUT, ItemOutputBusBlockEntity::new);
public static final IOPortKind FLUID_INPUT =
        new Simple("fluid_input_hatch", IOType.INPUT, FluidInputHatchBlockEntity::new);
public static final IOPortKind FLUID_OUTPUT =
        new Simple("fluid_output_hatch", IOType.OUTPUT, FluidOutputHatchBlockEntity::new);
public static final IOPortKind ENERGY_INPUT =
        new Simple("energy_input_hatch", IOType.INPUT, EnergyInputHatchBlockEntity::new);
public static final IOPortKind ENERGY_OUTPUT =
        new Simple("energy_output_hatch", IOType.OUTPUT, EnergyOutputHatchBlockEntity::new);
```

`REGISTRY` 和 `clearForTesting()` 必须使用上述六项并保持该顺序。

- [ ] **Step 2: 把公共实体改为存储基类**

`IOPortBlockEntity` 删除对 `IOPortBlock.IO_TYPE` 的依赖，定义固定方向接口：

```java
public abstract IOType ioType();
public abstract IOPortKind kind();
```

`ItemBusBlockEntity`、`FluidHatchBlockEntity`、`EnergyHatchBlockEntity` 保留当前公共存储字段和 raw getter，但改为 `abstract`，其具体子类只负责传入对应 BlockEntityType 并实现：

```java
@Override
public IOType ioType() {
    return IOType.INPUT;
}
```

输出子类返回 `IOType.OUTPUT`。每个子类的 `kind()` 必须返回对应的 `PortKinds.ITEM_INPUT` 等常量，而不是按方块状态推断。

存储字段继续满足现有 basic 值：物品 9 槽、流体 8000、能量容量/接收/抽取上限 100000。

- [ ] **Step 3: 改造 Block 和 DeferredRegister 配对**

`IOPortBlock` 保留一个公共类，构造器持有 `IOPortKind` 和对应的 `BlockEntityType` supplier；删除 `IO_TYPE` 字段、`registerDefaultState` 对 `IO_TYPE` 的设置、`createBlockStateDefinition` 对 `IO_TYPE` 的注册，以及 `useWithoutItem` 中的 `state.cycle(IO_TYPE)` 调用。

`ModBlocks.registerIoPort` 使用 `kind.id()` 作为注册名，不再拼接 `io_port_`：

```java
private static void registerIoPort(IOPortKind kind) {
    String name = kind.id();
    Supplier<? extends BlockEntityType<?>> beTypeSupplier =
            () -> ModBlockEntities.BES.get(name).get();
    BLOCKS.put(name, REGISTER.registerBlock(name,
            properties -> new IOPortBlock(kind, beTypeSupplier, properties)));
}
```

`ModBlockEntities` 同样以 `kind.id()` 作为 key，并把 `kind.entityFactory()` 绑定到 `ModBlocks.BLOCKS.get(name).get()`。

- [ ] **Step 4: 删除切换交互并保留菜单入口**

`IOPortBlock.useWithoutItem` 只保留服务端打开菜单，不再检查 Shift 或修改方块状态。菜单分派按新 ID 的六项分组：

```java
case "item_input_bus", "item_output_bus" -> new ItemBusMenu(
        containerId, playerInv,
        level.getBlockEntity(pos) instanceof ItemBusBlockEntity bus ? bus : null);
case "fluid_input_hatch", "fluid_output_hatch" -> new FluidHatchMenu(
        containerId, playerInv,
        level.getBlockEntity(pos) instanceof FluidHatchBlockEntity hatch ? hatch : null);
case "energy_input_hatch", "energy_output_hatch" -> new EnergyHatchMenu(
        containerId, playerInv,
        level.getBlockEntity(pos) instanceof EnergyHatchBlockEntity hatch ? hatch : null);
```

菜单 owner 在 Task 4 改为公共资源基类。

- [ ] **Step 5: 编译并运行注册测试**

Run: `./gradlew test --tests cn.howxu.mmcr.registry.PortKindsTest --tests cn.howxu.mmcr.registry.PortRegistrationTest`

Expected: PASS。

### Task 3: 实现单向外部能力和存储持久化

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/event/ModCapabilities.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/ItemBusBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/FluidHatchBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/EnergyHatchBlockEntity.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/ItemBusCapabilityGameTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/FluidHatchCapabilityGameTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/EnergyHatchCapabilityGameTest.java`

**Interfaces:**
- Consumes: Task 2 的六个具体 BE 类型和 raw storage getter。
- Produces: 对外 `Capabilities.Item.BLOCK`、`Capabilities.Fluid.BLOCK`、`Capabilities.Energy.BLOCK` 的单向访问；内部 raw getter 不受限制。

- [ ] **Step 1: 为能力方向写红灯 GameTest**

每个现有 capability GameTest 改为同时放置 input/output 两种方块。测试通过真实 BlockEntity capability 查询拿到外部 handler，不直接把 raw storage getter 当作外部能力断言；获取每个实体后同时断言输入实体的 `ioType() == IOType.INPUT`、输出实体的 `ioType() == IOType.OUTPUT`。

物品方向断言形态：

```java
BlockEntity be = helper.getLevel().getBlockEntity(inputPos);
ResourceHandler<ItemResource> input = Capabilities.Item.BLOCK.getCapability(
        helper.getLevel(), inputPos, helper.getLevel().getBlockState(inputPos), be, null);
helper.assertTrue(input != null, "Input item capability is present");
try (Transaction tx = Transaction.openRoot()) {
    int inserted = input.insert(0, ItemResource.of(Items.IRON_INGOT), 4, tx);
    int extracted = input.extract(0, ItemResource.of(Items.IRON_INGOT), 1, tx);
    helper.assertTrue(inserted == 4, "Input capability inserts");
    helper.assertTrue(extracted == 0, "Input capability rejects extraction");
    tx.commit();
}
```

输出端先通过 BE 的 raw handler 放入一件物品，再断言外部 `insert` 为零、`extract` 大于零。流体测试使用 `ResourceHandler<FluidResource>` 的 `insert`/`extract` 对称断言；能量测试使用 `EnergyHandler` 的 `insert`/`extract` 对称断言。每组操作使用 `try (Transaction tx = Transaction.openRoot())` 并在成功写入后调用 `tx.commit()`。

- [ ] **Step 2: 让三个 legacy adapter 接受方向策略**

在 `ModCapabilities` 中把适配器构造器增加 `boolean canInsert` / `boolean canExtract`，并在实际变更前拒绝反向操作：

```java
@Override
public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
    if (!canInsert) return 0;
    updateSnapshots(tx);
    ItemStack remainder = handler.insertItem(slot, resource.toStack(amount), false);
    return amount - remainder.getCount();
}

@Override
public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
    if (!canExtract) return 0;
    ItemStack stack = handler.getStackInSlot(slot);
    if (stack.isEmpty() || !ItemResource.of(stack).equals(resource)) return 0;
    updateSnapshots(tx);
    return handler.extractItem(slot, amount, false).getCount();
}
```

Fluid adapter 在 `insert`/`extract` 前分别检查 `canInsert`/`canExtract`；Energy adapter 在 `insert`/`extract` 前做同样检查。六个 BlockEntityType 的注册分别传入：输入 `(true, false)`，输出 `(false, true)`。

- [ ] **Step 3: 保证内部存储操作标记变更**

为 `ItemStackHandler` 和 `FluidTank` 使用匿名子类覆盖 `onContentsChanged` 并调用外层 BlockEntity 的 `setChanged()`；为 `EnergyStorage` 覆盖 `receiveEnergy`/`extractEnergy`，在非模拟且实际数量大于零时调用 `setChanged()`。这样只选择一种变化通知路径，拒绝操作不会标记脏数据。

三个公共存储基类使用当前 NeoForge 的 `ValueInput`/`ValueOutput` 生命周期保存数据：

```java
@Override
protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    handler.serialize(output.child("inventory"));
}

@Override
protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    handler.deserialize(input.childOrEmpty("inventory"));
}
```

流体和能量基类对各自的 `tank`/`storage` 使用同样的 `serialize`/`deserialize` 调用，并保留各自默认容量。

- [ ] **Step 4: 运行能力 GameTest**

Run: `./gradlew runGameTestServer`

Expected: 物品、流体、能量的输入/输出方向断言全部通过；旧三项方块引用造成的编译错误应已在测试改造中消失。

### Task 4: 改造菜单方向和端口资源

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/menu/DirectionalItemSlot.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/ItemBusMenu.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/FluidHatchMenu.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/EnergyHatchMenu.java`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java`
- Delete: `src/main/resources/assets/mmcr/textures/block/io_port_item_basic.png`
- Delete: `src/main/resources/assets/mmcr/textures/block/io_port_fluid_basic.png`
- Delete: `src/main/resources/assets/mmcr/textures/block/io_port_energy_basic.png`
- Create: `src/main/resources/assets/mmcr/textures/block/item_input_bus.png`
- Create: `src/main/resources/assets/mmcr/textures/block/item_output_bus.png`
- Create: `src/main/resources/assets/mmcr/textures/block/fluid_input_hatch.png`
- Create: `src/main/resources/assets/mmcr/textures/block/fluid_output_hatch.png`
- Create: `src/main/resources/assets/mmcr/textures/block/energy_input_hatch.png`
- Create: `src/main/resources/assets/mmcr/textures/block/energy_output_hatch.png`
- Create: `src/gametest/java/cn/howxu/mmcr/PortMenuDirectionGameTest.java`

**Interfaces:**
- Consumes: Task 2 的 `ioType()`、公共资源基类和新菜单分派。
- Produces: 服务端菜单槽位方向约束、六个资源模型/翻译，以及菜单行为 GameTest。

- [ ] **Step 1: 写菜单方向红灯测试**

在 `PortMenuDirectionGameTest` 中服务端创建两个 `ItemBusMenu`，分别传入输入和输出实体，获取前九个端口槽位并断言：

```java
Slot inputSlot = inputMenu.getSlot(0);
helper.assertTrue(inputSlot.mayPlace(new ItemStack(Items.IRON_INGOT)), "Input slot accepts placement");
helper.assertTrue(!inputSlot.mayPickup(player), "Input slot rejects pickup");

Slot outputSlot = outputMenu.getSlot(0);
helper.assertTrue(!outputSlot.mayPlace(new ItemStack(Items.IRON_INGOT)), "Output slot rejects placement");
helper.assertTrue(outputSlot.mayPickup(player), "Output slot accepts pickup");
```

同时覆盖 `quickMoveStack`：从玩家栏向输入端口可移动，从输出端口向玩家栏可移动，反向操作返回 `ItemStack.EMPTY`。

- [ ] **Step 2: 实现方向槽**

`DirectionalItemSlot` 继承当前使用的 `SlotItemHandler`，构造器接收 `IOType`；只覆盖两个行为：

```java
@Override
public boolean mayPlace(ItemStack stack) {
    return ioType == IOType.INPUT;
}

@Override
public boolean mayPickup(Player player) {
    return ioType == IOType.OUTPUT;
}
```

`ItemBusMenu.addBusSlots` 改为接收 `ItemBusBlockEntity`，使用其 raw `ItemStackHandler` 和 `owner.ioType()` 创建九个 `DirectionalItemSlot`。不要把外部 capability adapter 传给菜单，否则控制器内部读写会被错误限制。

- [ ] **Step 3: 统一流体/能量菜单 owner 类型**

将 `FluidHatchMenu.owner` 改为 `FluidHatchBlockEntity` 公共基类，将 `EnergyHatchMenu.owner` 改为 `EnergyHatchBlockEntity` 公共基类；`tank()` 和 `storage()` 继续返回 raw storage。保留当前玩家栏布局和 `stillValid` 行为。

- [ ] **Step 4: 更新翻译和模型输入**

在 `Translations.ALL` 中移除旧 block/item key，增加六个 block/item key，并增加六个方向明确的 container key，例如：

```java
Map.entry("block.mmcr.item_input_bus", "Item Input Bus"),
Map.entry("block.mmcr.item_output_bus", "Item Output Bus"),
Map.entry("container.mmcr.item_input_bus", "Item Input Bus"),
Map.entry("container.mmcr.item_output_bus", "Item Output Bus")
```

为 fluid/energy 使用同样命名规则，并提供对应中文翻译。`IOPortBlock.titleFor` 使用新 container key。

`ModelGen` 无需新增逻辑：保留普通无属性端口走 `createTrivialBlock`，确认 `textureFor(name)` 能命中六个新 PNG；不新增 `io_type` blockstate 生成逻辑。

- [ ] **Step 5: 准备方向贴图并运行菜单测试**

将当前三个 basic 贴图作为底图，使用 `reference/mmce` 的 normal 级方向 overlay 合成为六个目标 PNG。不得修改控制器或 GUI 纹理。

Run: `./gradlew runGameTestServer`

Expected: `PortMenuDirectionGameTest` 的槽位和快捷移动断言通过，六个端口能生成可加载的模型和翻译。

### Task 5: 更新控制器、默认结构和 E2E 测试

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/main/java/org/nibelungorum/DefaultMachines.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/E2ERecipeRunGameTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/test/TestBootstrap.java`

**Interfaces:**
- Consumes: Task 2 的六个具体实体类和新 Block 注册 ID；Task 3 的 raw 内部存储接口。
- Produces: 不依赖 `IO_TYPE` 的结构匹配、配方输入/输出流转和回归测试。

- [ ] **Step 1: 更新控制器输入/输出搜索**

在 `MachineControllerBlockEntity` 删除 `IOType` import 和所有基于 `bus.ioType()` 的判断，改为具体类型。物品输入搜索的条件和读取代码保持以下形式：

```java
if (level.getBlockEntity(getBlockPos().offset(dx, 1, dz))
        instanceof ItemInputBusBlockEntity bus) {
    IItemHandler handler = bus.getItemHandler(null);
    int count = 0;
    for (int slot = 0; slot < handler.getSlots(); slot++) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (ingredient.item().test(stack)) count += stack.getCount();
    }
    if (count >= ingredient.count()) return bus;
}
```

流体输入条件改为 `instanceof FluidInputHatchBlockEntity hatch` 并继续检查 `ingredient.fluid()` 与罐内数量；能量输入条件改为 `instanceof EnergyInputHatchBlockEntity hatch` 并继续检查所需总能量。输出槽搜索只接受 `ItemOutputBusBlockEntity`，并继续使用其 raw handler。

- [ ] **Step 2: 更新默认机器结构 API**

`DefaultMachines.ensureRegistered` 获取六个新端口；`blastFurnace` 使用以下七参数签名：

```java
public static Machine blastFurnace(
        Block casing,
        Block itemInput,
        Block itemOutput,
        Block fluidInput,
        Block fluidOutput,
        Block energyInput,
        Block energyOutput)
```

并把结构中的 IO 谓词构造成六项 `AnyOf`：

```java
BlockPredicate ioPort = new BlockPredicate.AnyOf(List.of(
        new BlockPredicate.OfBlock(itemInput),
        new BlockPredicate.OfBlock(itemOutput),
        new BlockPredicate.OfBlock(fluidInput),
        new BlockPredicate.OfBlock(fluidOutput),
        new BlockPredicate.OfBlock(energyInput),
        new BlockPredicate.OfBlock(energyOutput)));
```

只保留一个公开且被测试使用的签名，避免继续接受旧混合端口参数。

- [ ] **Step 3: 更新默认结构单元测试**

`DefaultMachinesTest` 的 bootstrap 参数和 `portPredicate()` 改为六个新 block，并添加断言：六个端口都能匹配 IO 位置，普通 casing/controller 仍保持原断言。

- [ ] **Step 4: 更新 E2E GameTest**

删除 `IOPortBlock`/`IOType` import，改用明确方块：

```java
helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
helper.setBlock(energyPos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
```

保持配方输入、输出和能量断言，并增加输入物品确实减少、能量确实扣除的断言，确保测试验证的是完整方向流转而不是只检查输出存在。

- [ ] **Step 5: 运行控制器回归测试**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.machine.DefaultMachinesTest && ./gradlew runGameTestServer`

Expected: 默认结构和 E2E 配方测试通过，源码中不再有生产代码对 `IOPortBlock.IO_TYPE` 或旧 `io_port_*` 的引用。

### Task 6: 全量验证和静态检查

**Files:**
- Modify only files required by failing verification; do not perform unrelated cleanup.

**Interfaces:**
- Consumes: Tasks 1–5 的全部实现和测试。
- Produces: 可编译、单元测试和 GameTest 全部通过的 basic 端口拆分。

- [ ] **Step 1: 搜索旧协议引用**

Run: `git grep -n -E 'io_port_(item|fluid|energy)_basic|IOPortBlock\.IO_TYPE|state\.cycle\(IO_TYPE\)' -- src || true`

Expected: 没有生产代码或测试引用旧 ID/可切换状态；如果输出只来自设计文档，则不需要修改文档。

- [ ] **Step 2: 运行单元测试和 Java 编译**

Run: `./gradlew test compileJava compileGametestJava`

Expected: `BUILD SUCCESSFUL`，所有 JUnit 测试通过，main 和 gametest source set 均编译成功。

- [ ] **Step 3: 运行 GameTest**

Run: `./gradlew runGameTestServer`

Expected: 六类端口能力测试、菜单方向测试、结构测试和 E2E 配方测试全部通过；日志中没有端口注册或能力注册异常。

- [ ] **Step 4: 检查变更范围**

Run: `rtk git status --short && rtk git diff --stat && rtk git diff --check`

Expected: 只有本计划涉及的 Java、资源和测试文件发生变化；不执行提交。

