# MMCR 调试扳手设计

日期：2026-08-04

## 1. 背景

MMCR 当前为开发者提供的可观测手段仅限于 GUI 与日志。当机器出现"卡料"、"能量对不上"、"流体没灌进去"等问题时，需要在不打开 GUI 的前提下快速确认 `IOPortBlockEntity` 内部的实际存量与方向。

其他大型 mod（如 AE2、Mekanism）都提供了"扳手/扳手类工具"用于离线打印方块实体状态。本设计为 MMCR 增加一款最小可用的同类调试工具。

## 2. 目标

- 注册一个新物品 `mmcr:wrench`（调试扳手），仅作为调试用途。
- 玩家手持扳手右键任意 6 种 IOPort（item bus、fluid hatch、energy hatch × input/output）时，在服务端将该方块实体的内部储量信息通过聊天消息发送给该玩家。
- 扳手右键**不会**打开原有 IO 端口菜单、不会消耗扳手耐久、不会对目标方块产生任何修改。
- 仅在创造模式物品栏内提供。`/give` 等作弊命令亦可拿到；不在标签中暴露任何非调试物品。
- 任意玩家（非 OP 限制）手持扳手都可触发，仅用于开发自测，不发送广播。

## 3. 非目标

- 不实现对 MachineController、DebugInfiniteSourceBlock、其他 mod 方块的查询（保持最小可用范围）。
- 不持久化任何调试数据。
- 不实现扳手对机器结构（多 block 结构）的旋转、装配、拆卸等"功能性"操作——这与本设计"只读打印"的定位冲突，避免未来误用为通用工具。
- 不在客户端显示任何 HUD/Overlay；只走服务端聊天消息。
- 不发送广播给同服其他玩家；不写入日志文件。

## 4. 架构

### 4.1 物品定义

新建 `cn.howxu.mmcr.internal.item.WrenchItem`，`extends net.minecraft.world.item.Item`：

- 构造：`super(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, MMCR.id("wrench"))))`。
- 不重写 `useOn` / `interactLivingEntity`；右键交互完全由事件总线处理。
- `@author howxu <dev@howxu.cn>` 标注。

### 4.2 注册

修改 `cn.howxu.mmcr.registry.ModItems`，追加手动注册项（不动既有 BlockItem 自动循环）：

- 新增 `public static final DeferredHolder<Item, Item> WRENCH = REGISTER.register("wrench", WrenchItem::new)`。
- 同时 `ITEMS.put("wrench", WRENCH)` 让 `MMCR.CREATIVE_TABS.displayItems` 自动收录。
- `WrenchItem` 类路径与构造函数引用都从 `cn.howxu.mmcr.internal.item` 包导入。

### 4.3 资源

- 贴图：`assets/mmcr/textures/item/wrench.png`，16×16（文件由开发者自行准备；缺失时 datagen 仍可生成模型 JSON，但游戏内会显示紫黑缺失纹理）。
- 模型：通过 `ModelGen.registerModels` 末尾追加一行 `itemModels.generateFlatItem(ModItems.WRENCH.get(), ModelLocationUtils.getModelLocation(ModItems.WRENCH.get()))` 自动产出 `assets/mmcr/models/item/wrench.json`，与现有所有方块/物品一致。
- 翻译：在 `cn.howxu.mmcr.datagen.Translations.ALL` 的 `en_us` 与 `zh_cn` 各追加 `Map.entry("item.mmcr.wrench", "Wrench" / "调试扳手")`。

### 4.4 事件处理器

新建 `cn.howxu.mmcr.internal.event.WrenchDebugHandler`：

- `@EventBusSubscriber(modid = MMCR.MODID, bus = EventBusSubscriber.Bus.GAME)` 注册到 `NeoForge.EVENT_BUS`。
- `@SubscribeEvent static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)`：

  1. 仅服务端：`if (event.getLevel().isClientSide()) return;`
  2. 仅主手：`if (event.getHand() != InteractionHand.MAIN_HAND) return;`
  3. 仅扳手：`ItemStack held = event.getEntity().getItemInHand(event.getHand()); if (!held.is(ModItems.WRENCH.get())) return;`
  4. 仅 IOPort：`BlockEntity be = event.getLevel().getBlockEntity(event.getPos()); if (!(be instanceof IOPortBlockEntity port)) return;`
  5. 分发到三个私有静态方法：
     - `printItemBus(ServerPlayer player, BlockPos pos, ItemBusBlockEntity bus)`
     - `printFluidHatch(ServerPlayer player, BlockPos pos, FluidHatchBlockEntity hatch)`
     - `printEnergyHatch(ServerPlayer player, BlockPos pos, EnergyHatchBlockEntity hatch)`
  6. 阻止原交互：`event.setUseItem(Event.Result.DENY); event.setUseBlock(Event.Result.DENY); event.setCancellationResult(InteractionResult.SUCCESS);`，这样既不开 IO 端口菜单，也不消耗扳手耐久、不触发挥动。

### 4.5 输出格式

所有消息通过 `ServerPlayer.sendSystemMessage(Component)` 发送（仅发给该玩家，不广播）。统一前缀 `[MMCR] <kind显示名> @ (<x>, <y>, <z>)`，其中 kind 显示名复用 `container.mmcr.<kind_id>` 翻译键（已存在）。

#### ItemBus

```
[MMCR] 物品输入总线 @ (12, 64, -8)
  Slot 0: 铁锭 x32/64
  Slot 1: (空)
  Slot 2: 煤炭 x10/64
  Slot 3: (空)
  Slot 4: (空)
  Slot 5: (空)
  共 42 个物品,占用 2/6 槽
```

- 6 个槽按 `0..5` 顺序逐行输出。
- 空槽写 `(空)`，不写 `air`。
- 物品名取 `stack.getHoverName()`；数量格式 `<count>/<maxStackSize>`。
- 末行摘要：所有 `stack.getCount()` 总和与占用槽位（`!stack.isEmpty()` 计数）。

#### FluidHatch

```
[MMCR] 流体输入仓 @ (12, 64, -8)
  流体: 水 4000 / 8000 mB
```

- 空罐：`流体: (空) 0 / 8000 mB`。
- 名取 `tank.getFluid().getHoverName()`。

#### EnergyHatch

```
[MMCR] 能量输入仓 @ (12, 64, -8)
  能量: 12345 / 100000 FE
```

- 数值取 `storage.getEnergyStored()` / `storage.getMaxEnergyStored()`。

### 4.6 边界处理

- 玩家右键非 IOPort 但持有扳手：本监听器 `return`，事件不取消，原右键逻辑照常（如右键地面右键基本方块）。
- 玩家在创造模式对着空气右键扳手：本监听器 `return`。
- 服务端多人同时右键同一端口：各自打印自己的消息，互不干扰（各自 `sendSystemMessage`）。
- 扳手右键被服务端的"右键被阻止"事件先一步拦截：本监听器晚于它，不会触发——这是可接受的边界情况。

## 5. 测试设计

本设计不引入单元测试：

- 事件 + 服务端聊天消息的端到端断言超出 GameTest 框架的常规能力，且会引入脆弱的字符串匹配。
- 现有 GameTest 套件与本特性无重叠。
- 验证以手动进游戏 + `./gradlew compileJava` / `build` 为主。

未来若 AE2 / 官方提供更友好的服务端聊天断言 API，可补一个 GameTest。

## 6. 验证命令

实施完成后依次执行：

- `./gradlew compileJava --no-daemon`
- `./gradlew build --no-daemon`
- 手动进游戏：创造模式拿到扳手，依次右键 6 种 IOPort，确认聊天栏输出与 §4.5 一致。

## 7. 决策摘要

选择 `PlayerInteractEvent.RightClickBlock` + 独立 `WrenchItem` 的组合而非修改 `IOPortBlock.useWithoutItem`：

- 不影响现有 IO 端口右键菜单流程；
- 未来增加新 `IOPortKind`（气体、魔源等）只需在分发处再加一个 `instanceof` 分支，无需触碰方块类；
- 与 AGENTS.md「最小改动、不优化相邻代码」一致。