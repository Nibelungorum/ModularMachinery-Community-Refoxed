# NeoForge 26.1.2 API 映射表（Item / Energy / Fluid）

> 把 MMCE 1.12.2 自定义能力体系逐项对到 NeoForge 26.1.2 官方能力 + 工具类。
> 写机器引擎前必须先背熟这张表——所有 Requirement / Component / 方块 capability 暴露都依赖这里。

## 0. 取证来源

下表所有 NeoForge API 均通过 `reference/kubejs` 与 `reference/jei` 的真实源码反查得出（KubeJS 26.1.2-8.0.4、JEI 29.21.0.65）。不臆测任何符号——如果某个 API 没出现在两份参考代码里，我会显式标注「待 26.1.2 文档核实」。

## 1. 总览：MMCE 1.12.2 的能力体系 vs NeoForge 26.1.2

| 维度 | MMCE 1.12.2（自实现） | NeoForge 26.1.2（官方） |
|---|---|---|
| 注册 | `GameRegistry.register` + `IForgeRegistryEntry` | `DeferredRegister<T>` + `IEventBus.register()` |
| 配方 | `MachineRecipe` 自定义 + `RecipeRegistry` 自管 | `Recipe<?>` / `RecipeType<?>` / `RecipeSerializer<?>` |
| 配方发现 | 自写 `RecipeRegistry.loadRecipeRegistry()` 扫 JSON | NeoForge `OnDatapackSyncEvent` / `RecipeManager` 自动从 `data/*/recipe/*` 加载 |
| 物品搬运 | `IItemHandler` / `IItemHandlerModifiable`（Forge 内置） | `ItemStackHandler`（NeoForge 内置）+ `BlockCapability` 暴露 |
| 流体搬运 | `IFluidHandler`（Forge 内置） | `FluidStack` + `IFluidHandler` + `BlockCapability` |
| 能量搬运 | `IEnergyStorage`（Forge 内置，FE） | `IEnergyStorage`（同款，FE；**API 完全兼容 1.12.2**） |
| 能力注册 | `CapabilityManager.INSTANCE.register(...)` | `RegisterCapabilitiesEvent` + `event.registerBlockEntity(...)` |
| 数据组件（取代 NBT） | `NBTTagCompound` | `DataComponentMap` / `DataComponentType<?>` |
| Tag 系统 | `ItemStack.getTagCompound()` | `Holder<Item>` / `TagKey<Item>` |
| 配方 JSON 格式 | MMCE 自定义 GSON | NeoForge `MapCodec` / `Codec` |

**好消息：能量（FE）在 1.12.2 → 26.1.2 之间 API 形态几乎不变。** 流体加了 `FluidType` 这一层。物品被 DataComponent 替代了大部分 NBT 用途。

## 2. 注册机制映射

### 2.1 DeferredRegister

```java
// 1.12.2 MMCE 写法（伪）
GameRegistry.register(new ResourceLocation("mmcr", "controller"), controllerBlock);
// 事件侧通过 @ObjectHolder 注入

// 26.1.2 NeoForge 写法
public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks("mmcr");
public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems("mmcr");
public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "mmcr");
public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, "mmcr");
public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, "mmcr");
public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, "mmcr");
public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "mmcr");

// 在 mod 构造器里：
public MMCR(IEventBus modBus) {
    BLOCKS.register(modBus);
    ITEMS.register(modBus);
    BLOCK_ENTITIES.register(modBus);
    RECIPE_TYPES.register(modBus);
    RECIPE_SERIALIZERS.register(modBus);
    CREATIVE_TABS.register(modBus);
    ATTACHMENT_TYPES.register(modBus);
}
```

**参考证据**：
- `reference/kubejs/src/main/java/dev/latvian/mods/kubejs/KubeJSComponents.java`
- `reference/kubejs/src/main/java/dev/latvian/mods/kubejs/recipe/KubeJSRecipeSerializers.java`
- `reference/kubejs/src/main/java/dev/latvian/mods/kubejs/gui/KubeJSMenus.java`
- `reference/kubejs/src/main/java/dev/latvian/mods/kubejs/item/creativetab/KubeJSCreativeTabs.java`

### 2.2 注册时机

| 资源类型 | 注册入口 | 何时对游戏可见 |
|---|---|---|
| Block / Item / BEType | `BLOCKS.register(modBus)` | 模组构造器 |
| RecipeType / RecipeSerializer | `RECIPE_TYPES.register(modBus)` | 模组构造器 |
| Capability | `RegisterCapabilitiesEvent` | 模组构造器或 `commonSetup` |
| Datapack 注册表 | `RegistryBuilder` + `DataPackRegistryEvent.NewRegistry` | `NewRegistry` 阶段 |
| CreativeModeTab | `CREATIVE_TABS.register(modBus)` | 模组构造器 |

## 3. 物品（Item）映射

### 3.1 数据形态

| 用途 | 1.12.2 | 26.1.2 |
|---|---|---|
| 单物品实例 | `ItemStack` | `ItemStack`（同款，但**底层加了 `DataComponentMap`**） |
| 物品类型 | `Item` / `ItemBlock` | `Item` / `BlockItem`（行为同） |
| 注册 | `ItemBlock` 反射注入 | `DeferredRegister.Items.register("id", () -> new Item(props))` |
| NBT | `NBTTagCompound` | `DataComponentMap`（**首选**）+ 残余 `tag` 字段兼容 |
| 标签 | `OreDictionary.getOres(...)` | `TagKey<Item>` / `HolderSet<Item>` |
| 物品清单 / 容器 | `IItemHandler` / `ItemStackHandler` | `ItemStackHandler`（同款，NeoForge 自带） |
| Capability | `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY` | `Capabilities.ItemHandler.BLOCK` / `.ENTITY` / `.BLOCK`（`BlockCapability`） |

### 3.2 ItemStackHandler（NeoForge 内置）

```java
// 1.12.2（Forge 自带）
ItemStackHandler handler = new ItemStackHandler(9);
// 也可直接 new SimpleInventory(9)，二者并存

// 26.1.2（NeoForge 同款 API）
ItemStackHandler handler = new ItemStackHandler(9);
// 槽位大小：构造时 setSize 不可改；每个槽位可独立 setStackInSlot
handler.setSize(27);   // 注意：setSize 是动态方法，26.1.2 仍可用
handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
```

**API 完全兼容——`ItemStackHandler` 是 1.12.2 → 26.1.2 一字未改的部分。**

### 3.3 暴露给邻近方块的 ItemHandler Capability

```java
// 26.1.2 注册（必须注册才能被 neighbour / downhand 查到）
@SubscribeEvent
public static void registerCaps(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            MMCR.ITEM_BUS_BE.get(),    // BlockEntityType<?>
            (be, side) -> be.getItemHandler(side),    // (BE, Direction) -> IItemHandler
            // 默认方向可不写，Direction arg 用作多面（如 ME pattern provider）
            BlockCapabilityCache.skip()                // 禁用 cache 的占位
    );
}
```

**参考证据**：`reference/kubejs/src/main/java/dev/latvian/mods/kubejs/KubeJSModEventHandler.java:129` 的 `registerCapabilities`。

### 3.4 数据组件（DataComponent）替代旧 NBT

| 旧 MMCE 用法 | 26.1.2 替代 |
|---|---|
| `stack.getTagCompound().getInteger("custom")` | `stack.get(MMCRDataComponents.SOME_INT.get())` |
| `stack.getTagCompound().getString("owner")` | `stack.get(MMCRDataComponents.OWNER.get())` |
| `stack.hasTagCompound() && stack.getTagCompound().hasKey("foo")` | `stack.has(MMCRDataComponents.FOO.get())` |

注册方式：
```java
public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, "mmcr");

public static final Supplier<DataComponentType<Integer>> ENERGY_BUFFER =
        DATA_COMPONENTS.register("energy_buffer",
                () -> DataComponentType.<Integer>builder().build());
```

## 4. 流体（Fluid）映射

### 4.1 数据形态变化（最大变化点）

| 用途 | 1.12.2 | 26.1.2 |
|---|---|---|
| 流体类型 | `Fluid`（如 `FluidRegistry.WATER`） | `Fluid`（`BuiltInRegistries.FLUID`）+ `FluidType`（`BuiltInRegistries.FLUID_TYPE`） |
| 实例 | `FluidStack(fluid, amount, nbt)` | `FluidStack(Fluid, int amount, DataComponentPatch patch)` |
| 注册 | `FluidRegistry.registerFluid(...)` | `DeferredRegister.create(BuiltInRegistries.FLUID, "mmcr")` |
| 流体类型属性 | `Fluid` 子类方法 | `FluidType` + `IClientFluidTypeExtensions` |
| 标签 | 自写 | `TagKey<Fluid>` / `HolderSet<Fluid>` |
| 容器 | `IFluidHandler`（Forge 自带） | `FluidStack` + `IFluidHandler`（**API 同 1.12.2**） |
| Capability | `CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY` | `Capabilities.FluidHandler.BLOCK` |

### 4.2 FluidType 注册

```java
public static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(BuiltInRegistries.FLUID_TYPE, "mmcr");

public static final Supplier<FluidType> MOLTEN_IRON_TYPE = FLUID_TYPES.register(
        "molten_iron",
        () -> new FluidType(FluidType.Properties.create()
                .density(2000)
                .viscosity(3000)
                .temperature(800)
                .lightLevel(8)
                .canDrown(false)
                .canSwim(false)
                .canHydrate(false)
        )
);
```

**重要：1.12.2 的 `Fluid` 行为（密度、粘度、亮度）现在挂在 `FluidType` 上。`Fluid` 本身只决定「这种流体在世界中如何流动 / 渲染」**。MMCE 1.12.2 直接 new `BlockFluidBase` 写 `setDensity(...)` 的写法整个迁移。

### 4.3 IFluidHandler 暴露

```java
event.registerBlockEntity(
        Capabilities.FluidHandler.BLOCK,
        MMCR.FLUID_HATCH_BE.get(),
        (be, side) -> be.getFluidHandler(side)
);
```

### 4.4 配方中的流体

```java
// 26.1.2：recipe JSON
{
  "type": "mmcr:machine_recipe",
  "machine": "mmcr:blast_furnace",
  "tick_time": 200,
  "fluid_inputs": [
    { "fluid": "minecraft:water", "amount": 1000 }
  ],
  "fluid_outputs": [
    { "fluid": "minecraft:steam", "amount": 500 }
  ]
}
```

底层 `FluidIngredient`（NeoForge 自带 `Ingredient` 的流体版）：
- `FluidIngredient.of(FluidStack)` / `FluidIngredient.tag(TagKey<Fluid>)`
- 通过 `FluidIngredient.CODEC` 序列化（NeoForge 提供）。
- **不要自己写 GSON**——直接用 Codec，Neoforge 帮你处理 datapack 加载。

## 5. 能量（Energy / FE）映射

### 5.1 数据形态（最大好消息）

| 用途 | 1.12.2 | 26.1.2 |
|---|---|---|
| 能力接口 | `IEnergyStorage` | `IEnergyStorage`（**完全一致**） |
| 内置实现 | 自写 `ForgeEnergyStorageWrapper` / `MMEnergyHandler` | NeoForge `EnergyStorage` |
| 单位 | FE（Forge Energy） | FE（Forge Energy） |
| Capability | `CapabilityEnergy.ENERGY` | `Capabilities.EnergyStorage.BLOCK` |
| 内部存储 | 任意实现 | `EnergyStorage(capacity, maxReceive, maxExtract)` |

### 5.2 暴露给邻近的能量 Capability

```java
event.registerBlockEntity(
        Capabilities.EnergyStorage.BLOCK,
        MMCR.ENERGY_HATCH_BE.get(),
        (be, side) -> be.getEnergyStorage(side)
);
```

### 5.3 配方中的能量

```java
// JSON:
{
  "type": "mmcr:machine_recipe",
  "machine": "mmcr:electric_furnace",
  "tick_time": 40,
  "energy_per_tick": 80       // FE / tick
}
```

注意 **NeoForge FE API 与 1.12.2 Forge Energy 完全一致**：
- `receiveEnergy(int maxReceive, boolean simulate)`
- `extractEnergy(int maxExtract, boolean simulate)`
- `getEnergyStored()` / `getMaxEnergyStored()`
- `canReceive()` / `canExtract()`

## 6. Capability 统一注册入口

**1.12.2 vs 26.1.2 关键差异**：

| 1.12.2 模式 | 26.1.2 模式 |
|---|---|
| `CapabilityManager.INSTANCE.register(...)` 在 `preInit` 阶段 | `RegisterCapabilitiesEvent` 在模组构造器内 `@SubscribeEvent static` 监听 |
| `CapabilityToken<T>` 通用 token | `BlockCapability<T, C>` 用 `Capabilities.X.BLOCK` |
| 实体能力注入 `AttachCapabilitiesEvent` | 实体能力通过 `event.registerBlockEntity(...)` 一站式 |

**单例注册方式（必须改）**：
```java
// 1.12.2
@Mod.EventHandler
public void preInit(FMLPreInitializationEvent e) {
    CapabilityManager.INSTANCE.register(IMMCRComponent.class, ...);
}

// 26.1.2
@SubscribeEvent
public static void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(...);
    event.registerBlock(...);
    event.registerEntity(...);
}
```

## 7. 配方系统映射

### 7.1 配方形式

| 1.12.2 MMCE | 26.1.2 MMCR |
|---|---|
| 自写 `MachineRecipe` + JSON 扫盘 | Neoforge `Recipe<?>` + JSON 在 `data/<ns>/recipe/<id>.json` |
| `RecipeRegistry` 自己维护 `Map<ResourceLocation, MachineRecipe>` | `RecipeManager.getRecipes()` / `byType()` |
| 自写 GSON `PRELOAD_GSON` 两阶段 | `MapCodec<T>` + `Codec<T>` |
| 自写 `RecipeCraftingContext`（运行期状态） | `Recipe.input` / `Recipe.assemble(...)` 风格 + MMCR 自己维护执行期上下文 |

### 7.2 MMCR 自定义 Recipe 的骨架

```java
public record MachineRecipe(
        ResourceLocation machineId,        // 属于哪台机器
        int tickTime,                        // 总 tick
        List<MachineIngredient> inputs,      // 统一抽象：Item / Fluid / Energy 都进来
        List<MachineStack> outputs
) implements Recipe<RecipeInput> {

    @Override
    public boolean matches(RecipeInput input, Level level) {
        // 控制器在结构成立 + 检查 RecipeInput 时调用
        // 输入对应「物品仓 + 流体仓 + 能量仓的快照」
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        // 不需要返回 ItemStack——MMCR 走自己的 output channel
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MMCR.MACHINE_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return MMCR.MACHINE_RECIPE_TYPE.get();
    }
}
```

`MachineIngredient` 用 sealed interface 模拟：
```java
public sealed interface MachineIngredient {
    record ItemIngredient(Ingredient item, int count) implements MachineIngredient {}
    record FluidIngredient(FluidIngredient fluid, int amount) implements MachineIngredient {}
    record EnergyIngredient(int fePerTick) implements MachineIngredient {}
}
```

**注意：MMCE 1.12.2 把 Item / Fluid / Energy / Gas 当作不同 RequirementType 注册——首期 MMCR 把它们收编成 `MachineIngredient` 的 sealed 子类，类型闭合、避免 KubeJS 重新发明轮子。**

### 7.3 RecipeSerializer 用 Codec 写

```java
public class MachineRecipeSerializer implements RecipeSerializer<MachineRecipe> {

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("machine").forGetter(MachineRecipe::machineId),
                    Codec.INT.fieldOf("tick_time").forGetter(MachineRecipe::tickTime),
                    MachineIngredient.CODEC.listOf().fieldOf("inputs").forGetter(MachineRecipe::inputs),
                    MachineStack.CODEC.listOf().fieldOf("outputs").forGetter(MachineRecipe::outputs)
            ).apply(instance, MachineRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> STREAM_CODEC =
            StreamCodec.of(MachineRecipeSerializer::toNetwork, MachineRecipeSerializer::fromNetwork);

    @Override
    public MapCodec<MachineRecipe> codec() { return CODEC; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() { return STREAM_CODEC; }
}
```

注册：`RECIPE_SERIALIZERS.register("machine_recipe", () -> MachineRecipeSerializer.INSTANCE)`。

## 8. 关键 API 速查表（首期必背）

| API | 用途 | 出处 |
|---|---|---|
| `DeferredRegister.createBlocks(ns)` | Block 注册 | KubeJS `KubeJSMenus` 等 |
| `DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, ns)` | RecipeType 注册 | KubeJS `KubeJSRecipeSerializers` |
| `RegisterCapabilitiesEvent.registerBlockEntity(cap, beType, fn)` | 给 BlockEntity 挂能力 | KubeJS `KubeJSModEventHandler:129` |
| `Capabilities.ItemHandler.BLOCK` | 物品能力 token | NeoForge |
| `Capabilities.FluidHandler.BLOCK` | 流体能力 token | NeoForge |
| `Capabilities.EnergyStorage.BLOCK` | 能量能力 token | NeoForge |
| `ItemStackHandler` | 物品容器实现 | NeoForge（同 1.12.2） |
| `EnergyStorage(capacity, maxReceive, maxExtract)` | 能量容器实现 | NeoForge（同 1.12.2） |
| `FluidStack(Fluid, int)` / `FluidStack(Fluid, int, DataComponentPatch)` | 流体实例 | NeoForge |
| `FluidType.Properties.create()...` | 流体属性构建 | NeoForge |
| `Ingredient.CODEC` / `FluidIngredient.CODEC` | 物品 / 流体 ingredient 序列化 | NeoForge |
| `Recipe<T>` / `RecipeSerializer<T>` | 配方接口 | NeoForge |
| `MapCodec<T>` / `Codec<T>` / `RecordCodecBuilder` | 数据序列化 | Mojang + NeoForge |
| `DataComponentType<T>` | 物品数据组件 | NeoForge |

## 9. 风险点

1. **Fluid / FluidType 双层** 是 1.12.2 → 26.1.2 的最大认知差。MMCE 自己 new `BlockFluidBase` 的写法**整个废弃**。首期 MMCR 不实现流体方块——只消费别人注册的流体（如 `minecraft:water`），流出 `FluidStack`。

2. **DataComponent 完全替代 NBT** 是第二大认知差。MMCE 大量 `NBTTagCompound` 自定义 key 写法需要逐个映射——首期策略：**先用 `DataComponentMap` 当纯 NBT 用**（`CompoundTag` 数据组件），后续再分拆。

3. **Capability 是 `BlockCapability`** 而不是通用 `Capability`。首期不会感觉到差异（所有主流 API 仍是 `IItemHandler` / `IFluidHandler` / `IEnergyStorage`），但写 addon 时需要 `cap.getCapability(ctx)` 这种「带 context」的风格。

4. **1.12.2 的 `World` / `Entity` 在 26.1.2 是 `Level` / `Entity`**，API 形态高度相似但命名不同。

5. **`BlockPos` 在两个版本都是 `BlockPos`**，但 26.1.2 是 `record`，访问器命名略变（`getX()` → `getX()` 同名，但 immutable）。