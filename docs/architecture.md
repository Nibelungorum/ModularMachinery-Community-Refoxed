# MMCR 26.1.2 架构（一句话：翻译 + 三段映射）

> 本文档定位：**翻译路线图**。MMCE 1.12.2 → MMCR 26.1.2 不是重新设计，而是把 MMCE 的概念逐一映射到 NeoForge 26.1.2 的对应物。**不发明新东西**。

## 0. 心智模型（一句话先讲清）

> MMCR 是「在 NeoForge 26.1.2 上，把一台「控制器方块 + 周围方块（外壳 + 输入输出仓）」绑定成一个 named recipe container（machine），其中 recipe 通过 NeoForge `Recipe<?>` 体系注册，结构匹配 + tick 执行 + 物品/流体/能量 IO 全部走 NeoForge 官方 capability。」

下面所有内容都是这句话的细化。

## 1. 翻译策略（先说方法）

| 翻译手法 | 说明 | 适用情形 |
|---|---|---|
| **直译** | MMCE 类的 API 形态 1:1 对应 NeoForge 同名 API | 工具类、简单数据结构 |
| **借用** | MMCE 自写的功能直接换成 NeoForge / 社区已有的等价物 | 旧 Forge 自带功能（Fluid、Energy、ItemHandler） |
| **重新映射** | MMCE 的抽象概念拆散，归并到 NeoForge 的多个对应物 | RequirementType / ComponentType / 注册机制 |
| **删除** | MMCE 的功能在 NeoForge 时代已无意义 | GSON 双阶段、Universal Bucket、acceptableRemoteVersions 等 |
| **保留接口 / TODO** | MMCE 的功能先空着，预留扩展点 | 并行 / 多线程 / 升级 / 智能接口 |

**严格遵守**：每写一个 MMCE → MMCR 映射，写明属于上面 5 类哪一类。无映射 = 不写。

## 2. 模块映射表（MMCE 包 → MMCR 包）

```
MMCE 1.12.2                                      MMCR 26.1.2
─────────────────────────────────                ────────────────────────────
hellfirepvp.modularmachinery                     cn.howxu.mmcr
  ├─ ModularMachinery.java                         └─ MMCR.java                       [直译+重映射]
  ├─ CommonProxy.java / ClientProxy.java           ├─ MMCRClient.java                  [重映射]
  ├─ common.util.*                                 ├─ util/                            [借用+直译]
  ├─ common.machine.*                              ├─ machine/                         [直译]
  │   ├─ MachineRegistry                           │   ├─ MachineRegistry              [直译]
  │   ├─ DynamicMachine                            │   ├─ DynamicMachine               [重映射]
  │   ├─ BlockArray                                 │   ├─ BlockArray                   [借用+直译]
  │   └─ TaggedPositionBlockArray                  │   └─ BlockPredicate              [重新映射：与 BlockArray 合并]
  ├─ common.crafting.*                             ├─ recipe/                          [借用+重映射]
  │   ├─ MachineRecipe                             │   ├─ MachineRecipe（record, implements Recipe<>） [重新映射]
  │   ├─ RecipeRegistry                            │   ├─ RecipeRegistry               [直译，包一层 NeoForge RecipeManager]
  │   ├─ RecipeAdapter / RecipeAdapterRegistry    │   └─ （OUT，§4.6）                   [删除]
  │   ├─ RecipeCraftingContext                     │   ├─ RecipeContext                [重新映射]
  │   ├─ ActiveMachineRecipe                       │   └─ ActiveRecipe                 [重新映射]
  │   └─ RequirementType / RequirementTypeRegistry│   └─ MachineIngredient (sealed)   [重新映射：收编为 sealed]
  ├─ common.modifier.*                             ├─ modifier/                        [保留接口 / TODO]
  │   ├─ RecipeModifier                            │   └─ RecipeModifier               [TODO：仅最小集]
  │   └─ ModifierRegistry                          │   └─ （空）                          [TODO]
  ├─ common.block[.prop].*                         ├─ block/                           [重映射]
  │   ├─ BlockController                           │   └─ MachineControllerBlock       [直译+重映射]
  │   ├─ BlockCasing                               │   └─ MachineCasingBlock           [直译]
  │   ├─ BlockInputBus / OutputBus                 │   └─ ItemBusBlock                  [合并 IN/OUT]
  │   ├─ BlockFluidInputHatch / OutputHatch        │   └─ FluidHatchBlock               [合并 IN/OUT]
  │   └─ BlockEnergyInputHatch / OutputHatch       │   └─ EnergyHatchBlock              [合并 IN/OUT]
  ├─ common.tiles[.base].*                         ├─ tile/                            [重映射]
  │   ├─ TileMultiblockMachineController           │   └─ MachineControllerBlockEntity  [直译+重映射]
  │   ├─ TileItemBus                               │   └─ ItemBusBlockEntity            [直译]
  │   ├─ TileFluidTank                             │   └─ FluidHatchBlockEntity         [直译]
  │   ├─ TileEnergyHatch                           │   └─ EnergyHatchBlockEntity        [直译]
  │   └─ TileMachineComponent + base classes       │   └─ （无需独立 base，全部用 NeoForge BlockEntity）
  ├─ common.event.*                                ├─ event/                           [借用+重映射]
  │   ├─ MachineEvent / RecipeEvent                │   └─ MachineEvent / RecipeEvent   [重映射：基于 NeoForge EventBus]
  │   └─ Phase                                     │   └─ Phase                         [直译]
  ├─ common.network.*                              ├─ network/                         [重映射]
  │   └─ 15 个 PktXxx                               │   └─ 1 个 PktMachineStatePayload    [删除 14 个 + 借用 NeoForge CustomPacketPayload]
  ├─ common.command.*                              ├─ command/                         [直译+重映射]
  │   └─ 5 个 /mm 命令                              │   └─ /mmcr reload                  [删除 4 个 + 借用 NeoForge Commands]
  ├─ common.data.Config                            ├─ config/                          [借用 ModConfigSpec]
  ├─ common.integration.{crafttweaker,...}         ├─ kubejs/                          [重映射：整体从 CraftTweaker 迁 KubeJS]
  └─ common.registry.*                             ├─ registry/                        [借用 DeferredRegister]

github.kasuminova.mmce.*                          [删除：KasumiNova 加的全是高级特性]
ink.ikx.mmce.*                                    [删除：自动组装，Phase 5 再加]
kport.modularmagic.*                              [删除：魔法合成，Phase 6 再加]
youyihj.mmce.*                                    [删除：投影器，Phase 5 再加]
com.cleanroommc.client.*                          [删除：cleanroommc 移植预览渲染，Phase 5 再加]
```

**结论**：MMCE 700+ 文件，MMCR 首期目标约 40-60 个 .java + 若干 .json。

## 3. 类级映射（关键类的逐一翻译）

### 3.1 Mod 入口

| MMCE 1.12.2 `ModularMachinery.java` | MMCR 26.1.2 `MMCR.java` |
|---|---|
| `@Mod(modid = "modularmachinery", dependencies = "...", ...)` | `@Mod("mmcr")`；**无硬依赖**；JEI、KubeJS 都标 `optional` |
| `static { FluidRegistry.enableUniversalBucket(); }` | **删除**：1.21 时代没有 Universal Bucket |
| `SimpleNetworkWrapper NET_CHANNEL = ...`（15 个 Pkt 注册） | `RegisterPayloadHandlersEvent` 注册 1 个 `PktMachineStatePayload` |
| `EXECUTE_MANAGER = new TaskExecutor()`（fork/join） | **删除**：`TaskExecutor` 是为多线程准备；首期单 tick 单 RecipeSearchTask |
| `EVENT_BUS = new EventBus()`（私有） | `NeoForge.EVENT_BUS` 借来用；不再持有私有 bus |

### 3.2 机器系统

| MMCE 类 | MMCR 类 | 翻译手法 | 备注 |
|---|---|---|---|
| `AbstractMachine` | `cn.howxu.mmcr.machine.Machine`（抽象 record + 默认实现） | 直译 | record 风格；`registryName` / `localizedName` / `pattern` / `recipeType` |
| `DynamicMachine` | `cn.howxu.mmcr.machine.DynamicMachine` | 直译 | KubeJS builder 最终 `build()` 出的就是这个类 |
| `MachineRegistry` | `cn.howxu.mmcr.machine.MachineRegistry` | 直译 | 单例 `Map<ResourceLocation, Machine>` |
| `MachineLoader` | **删除** | 删除 | KubeJS 启动时同步 `MMCR.machines.register(...)`，无需异步 / 双阶段 |
| `MachineComponent`（IOType、ItemBus / FluidHatch / EnergyHatch 三类） | `cn.howxu.mmcr.component.MachineComponent<T>` | 直译 | 接口签名基本不变；具体子类只实现 `ItemComponent` / `FluidComponent` / `EnergyComponent` 三种 |
| `TaggedPositionBlockArray`（带 selector tag） | `cn.howxu.mmcr.machine.BlockArray`（**合并**） | 重新映射 | selector tag → 在 KubeJS builder 里用 `event.create('mmcr:item_input').tag('mmcr:item_bus')` 的方式表达，BlockArray 本身不存 tag |
| `BlockArray` | `cn.howxu.mmcr.machine.BlockArray`（records `Map<BlockPos, BlockPredicate>`） | 直译 | JSON 反序列化器删；KubeJS builder 构造 |
| `BlockArrayCache`（按 facing 缓存） | **删除** | 删除 | 首期每 tick 重新做旋转匹配，开销可接受 |
| `PlayerStructureSelectionHelper`（蓝图工具） | **删除** | 删除 | Phase 5 蓝图工具再加 |

### 3.3 配方系统

| MMCE 类 | MMCR 类 | 翻译手法 | 备注 |
|---|---|---|---|
| `MachineRecipe` | `cn.howxu.mmcr.recipe.MachineRecipe`（`record implements Recipe<RecipeInput>`） | 重新映射 | `inputs: List<MachineIngredient>` + `outputs: List<MachineStack>`；NeoForge `Recipe<?>` 形态 |
| `RecipeRegistry` | `cn.howxu.mmcr.recipe.RecipeRegistry` | 直译 | 但底层 `RecipeManager` 是 NeoForge 的；我们只是 `byType(MACHINE_RECIPE_TYPE)` |
| `RecipeAdapter`（抽象） | **删除** | 删除 | CraftTweaker / IC2 / TE 等适配器是 MMCE 1.12.2 时代的桥接产物；首期不做 mod 桥接 |
| `RecipeAdapterRegistry` | **删除** | 删除 | 同上 |
| `RecipeCraftingContext` | `cn.howxu.mmcr.recipe.RecipeContext` | 直译 | 但**单线程**——无对象池 |
| `ActiveMachineRecipe` | `cn.howxu.mmcr.recipe.ActiveRecipe` | 直译 | `recipe` / `remainingTick` / `parallelism`（恒为 1） |
| `RecipeCraftingContextPool` | **删除** | 删除 | 多线程的产物 |
| `PreparedRecipe`（CraftTweaker 接口） | **删除** | 删除 | KubeJS 用 `KubeRecipe` + 自家的工厂方法 |

### 3.4 Requirement / Component（重新映射：合并 sealed）

**1.12.2 形态**：`Map<ResourceLocation, RequirementType<?, ?>>` + 每个 RequirementType 是独立类（`RequirementItem` / `RequirementFluid` / `RequirementEnergy` / `RequirementGas` / ...）。

**26.1.2 形态**：`sealed interface MachineIngredient` + 三种 record 子类。

```java
public sealed interface MachineIngredient {
    record ItemIngredient(Ingredient item, int count) implements MachineIngredient {}
    record FluidIngredient(FluidIngredient fluid, int amount) implements MachineIngredient {}
    record EnergyIngredient(int fePerTick) implements MachineIngredient {}
}
```

**为什么 sealed？**：
- 1.12.2 时代 `RegistryRequirementTypes` 用 ResourceLocation 索引是「KubeJS-friendly」思路——但 NeoForge 时代我们走 sealed + RecordCodecBuilder + KubeJS TypeWrapper 路径，更类型安全。
- KubeJS 端用 `RecipeSchema` 的 JSON 把字符串翻译成 record 字段，无需 ResourceLocation 注册。
- 未来加新 Requirement（升级卡等）→ 加新 record 子类 + 适配 `matches` / `consume` / `produce`。

### 3.5 方块 / Tile（重新映射：合并 IN/OUT）

**1.12.2 形态**：`BlockInputBus` / `BlockOutputBus` 各 9 等级 = 18 个 Block 类 + 18 个 Tile 类 + 18 个 ItemBlock。

**26.1.2 形态**：`ItemBusBlock`（一个 Block 类）+ `ItemBusBlockEntity`（按 IOType 区分方向），仅 1 等级。

| MMCE 1.12.2 | MMCR 26.1.2 |
|---|---|
| `BlockInputBus`（9 等级：`tiny` `small` `normal` ...） | `ItemBusBlock`（一个属性 `io_type`，加一个 `tier` 属性） |
| `TileItemInputBus` / `TileItemOutputBus` | `ItemBusBlockEntity`（构造参数 `IOType`，单类） |
| `ItemBusType` 枚举 9 个值 | `ItemBusTier` 枚举：1 个值 `NORMAL`（首期不分级） |
| `BlockFluidInputHatch` / `BlockFluidOutputHatch`（6+6 等级） | `FluidHatchBlock` + `FluidHatchBlockEntity` + `FluidHatchTier` |
| `BlockEnergyInputHatch` / `BlockEnergyOutputHatch`（4+4 等级） | `EnergyHatchBlock` + `EnergyHatchBlockEntity` + `EnergyHatchTier` |
| `BlockUpgradeBus` | **删除**（Phase 5） |
| `BlockSmartInterface` | **删除**（Phase 4） |
| `BlockParallelController` | **删除**（Phase 3） |
| `BlockFactoryController` | **删除**（Phase 3） |
| `BlockController` | `MachineControllerBlock` + `MachineControllerBlockEntity` |
| `BlockCasing`（6 个 CasingType） | `MachineCasingBlock`（一个 `casing_type` 属性，首期只出 `PLAIN` 一种） |
| 全部 ME 系列（`BlockME*`） | **删除**（Phase 6） |
| `BlockCustomName` / `BlockVariants` 等基类 | **删除**：26.1.2 直接用 `BlockBehaviour.Properties` + DataComponent |

### 3.6 渲染（删除为主）

| MMCE 类 | MMCR 状态 |
|---|---|
| `MachineControllerRenderer` / `BloomGeoModelRenderer` / `ControllerModelRenderManager` | **删除**（无 GeckoLib） |
| `MachineControllerModel` / `DynamicMachineModelRegistry` | **删除** |
| `MixinRenderGlobal` / `MixinTileEntityRendererDispatcher` | **删除**（首期零 Mixin） |
| `MachineStructurePreviewPanel` / `WorldSceneRendererWidget` | **删除**（首期无结构预览） |
| `WorldSceneRenderer` / `FBOWorldSceneRenderer` / `ImmediateWorldSceneRenderer` | **删除** |
| `com.cleanroommc.client.*` | **整包删除** |
| `Lumenized` bloom | **删除**（NeoForge 渲染已够） |

**结论**：MMCE 50+ 个渲染 / 预览 / GeckoLib 文件，**首期全部 OUT**。

### 3.7 网络（重新映射：合并到 1 包）

**1.12.2 形态**：15 个 `PktXxx`（`PktSyncSelection` / `PktSmartInterfaceUpdate` / `PktParallelControllerUpdate` / `PktAutoAssemblyRequest` / `PktMEPatternProvider*` 等）。

**26.1.2 形态**：1 个 `PktMachineStatePayload`，承载「控制器当前状态 / 配方进度」。其余 Pkt 全部删除（其功能 OUT）。

```java
public record PktMachineStatePayload(BlockPos pos, MachineStateSnapshot snapshot)
        implements CustomPacketPayload {
    public static final Type<PktMachineStatePayload> TYPE = new Type<>(MMCR.id("machine_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PktMachineStatePayload::pos,
                    MachineStateSnapshot.STREAM_CODEC, PktMachineStatePayload::snapshot,
                    PktMachineStatePayload::new);
}
```

注册：`PlayPayloadContext` + `RegisterPayloadHandlersEvent`。

### 3.8 命令（删除 4 / 保留 1）

**1.12.2 形态**：5 个 `/mm` 子命令。

**26.1.2 形态**：1 个 `/mmcr reload`（KubeJS 脚本 reload + RecipeManager 重建）。其他删除。

```java
Commands.literal("mmcr")
    .then(Commands.literal("reload")
        .requires(src -> src.hasPermission(2))
        .executes(ctx -> { ... reload KubeJS scripts ... return 1; })
    );
```

### 3.9 配置（借用 ModConfigSpec）

**1.12.2 形态**：`Config.java` 自写 getter / setter / 默认值。

**26.1.2 形态**：`ModConfigSpec.Builder` 链式 + TOML 序列化：

```java
public final class MMCRConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MACHINE_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue ENERGY_CONSUMPTION_MULTIPLIER;

    static {
        var b = new ModConfigSpec.Builder();
        MACHINE_CHECK_INTERVAL_TICKS = b.comment("...").defineInRange("machine_check_interval_ticks", 20, 1, 600);
        ENERGY_CONSUMPTION_MULTIPLIER = b.comment("...").defineInRange("energy_consumption_multiplier", 1.0, 0.0, 100.0);
        SPEC = b.build();
    }
}
```

注册：`ModContainer.registerConfig(ModConfig.Type.COMMON, MMCRConfig.SPEC)`。

## 4. Recipe 执行流（端到端翻译）

MMCE 1.12.2 流程：

```
Controller tick
  → doControllerTick
    → checkStructure                          [MMCR 保留]
    → if (!formed) return                     [MMCR 保留]
    → recipeSearchTask.computeTask()          [MMCR 简化：单线程同步跑]
      → 遍历 MachineRecipe
        → RecipeCraftingContext.checkStartResult
          → IOInventory / ItemHandler 检查
        → if success, RecipeStartEvent
    → if (activeRecipe != null)
      → recipe.tick()
        → 消耗输入 / 产出输出 / 扣能量
      → if (remainingTick <= 0) finish
```

MMCR 26.1.2 流程：

```
controller.serverTick()                            [BlockEntityTicker<MachineControllerBlockEntity>]
  → if (!level.getServer().isTickRunAt(pos)) return
  → checkStructure()                               [NeoForge Level + BlockPos]
    → if (!formed) { activeRecipe = null; return; }
  → if (activeRecipe == null) tryStartNewRecipe()
    → for (MachineRecipe r : RecipeManager.byType(MACHINE_RECIPE_TYPE).values())
        → if (r.matches(currentInput, level)) { activeRecipe = r; break; }
    → postMachineTick event
  → if (activeRecipe != null) tickRecipe()
    → 扣能量（fePerTick）
    → if (++tickCount >= activeRecipe.tickTime) finishRecipe()
      → 输出物品到 itemBus.output
      → 输出流体到 fluidHatch.output
      → postRecipeComplete event
```

## 5. 资源映射

### 5.1 翻译清单

| MMCE 资源 | MMCR 资源 | 翻译手法 |
|---|---|---|
| `assets/<modid>/blockstates/<name>.json`（1.12.2 格式） | 同路径 + 新格式 | 直译 + 改 namespace |
| `assets/<modid>/models/block/<name>.json` | 同路径 | 直译（模型格式 26.1.2 兼容 1.12.2） |
| `assets/<modid>/models/item/<name>.json` | `assets/<modid>/item/<name>.json` 或 `models/item/<name>.json` | 重映射：26.1.2 优先 item/ 目录 |
| `assets/<modid>/textures/blocks/*.png` | 同路径（改为 `textures/block/*.png`） | 重映射（命名约定现代化） |
| `assets/<modid>/textures/items/*.png` | 同路径（改为 `textures/item/*.png`） | 重映射 |
| `assets/<modid>/textures/gui/*.png` | 同路径 | 直译 |
| `assets/<modid>/lang/en_US.lang` | `assets/<modid>/lang/en_us.json` | 重新映射（format 完全改了） |
| `assets/<modid>/lang/zh_CN.lang` | `assets/<modid>/lang/zh_cn.json` | 同上 |

### 5.2 资源总账（首期需要带的）

| 资源类型 | MMCE 数量 | MMCR 数量（首期） |
|---|---|---|
| blockstates JSON | ~30 | ~5（controller / casing / item_bus / fluid_hatch / energy_hatch） |
| 模型 JSON | ~60 | ~10（每方块 1-2 个） |
| 物品模型 | ~10 | ~5 |
| 贴图 | 149 PNG | ~10（最小集） |
| 语言文件 | 2 个 .lang | 2 个 .json |
| 配方数据 | 0（KubeJS 自写） | 0 |

**资源策略**：**重用 MMCE 已有的所有贴图**——把它们 1:1 拷过来，只改文件路径（`textures/blocks/` → `textures/block/`）和命名空间（`modularmachinery` → `mmcr`）。模型 JSON 同理。**只在发现 26.1.2 解析报错时才修**。

## 6. 模块边界（package layout）

```
cn.howxu.mmcr
├─ api/                              # 公开 API（addon 调用）
│   ├─ MMCR.java                     # 入口类（machines() / recipes() / events()）
│   ├─ machine/
│   │   ├─ Machine.java
│   │   ├─ DynamicMachine.java
│   │   ├─ MachineRegistry.java
│   │   └─ BlockArray.java
│   ├─ recipe/
│   │   ├─ MachineRecipe.java
│   │   ├─ RecipeRegistry.java
│   │   ├─ RecipeContext.java
│   │   └─ ActiveRecipe.java
│   ├─ ingredient/                   # sealed MachineIngredient
│   │   ├─ MachineIngredient.java
│   │   ├─ ItemIngredient.java
│   │   ├─ FluidIngredient.java
│   │   └─ EnergyIngredient.java
│   ├─ component/
│   │   └─ MachineComponent.java
│   ├─ event/
│   │   ├─ MachineEvent.java
│   │   └─ RecipeEvent.java
│   └─ util/
│      └─ IOType.java
├─ internal/                         # 实现细节（addon 勿用）
│   ├─ MMCR.java                     # 主类（NeoForge 入口）
│   ├─ registry/                     # DeferredRegister 集合
│   ├─ block/                        # 所有 Block 子类
│   ├─ tile/                         # 所有 BlockEntity 子类
│   ├─ network/                      # PktMachineStatePayload
│   ├─ config/                       # MMCRConfig
│   ├─ command/                      # /mmcr reload
│   └─ client/                       # 客户端类（按 Dist.CLIENT split）
├─ kubejs/                           # KubeJS 桥接（可选绑定）
│   ├─ MMCRKubeJSPlugin.java
│   ├─ MachineBuilderJS.java
│   ├─ MachineRecipeBuilderJS.java
│   ├─ MachineRecipeFactory.java
│   ├─ MMCREvents.java
│   └─ wrappers/
│      ├─ BlockArrayWrapper.java
│      └─ ComponentTypeWrapper.java
└─ resources/
   ├─ data/mmcr/recipe/<示例>.json   # NeoForge 自动加载（即使无 KubeJS 也能跑）
   ├─ data/mmcr/kubejs/recipe_schema/machine_recipe.json
   ├─ assets/mmcr/blockstates/*.json
   ├─ assets/mmcr/models/{block,item}/*.json
   ├─ assets/mmcr/textures/{block,item,gui}/*.png
   ├─ assets/mmcr/lang/{en_us,zh_cn}.json
   └─ META-INF/
      ├─ neoforge.mods.toml
      └─ kubejs.plugins.txt
```

**关键约束**：
- `api/` 下代码**不能** import `internal/` 或 `kubejs/`（单向依赖）。
- `internal/` 可以 import `api/` 和自己的东西。
- `kubejs/` 可以 import `api/` 和 KubeJS API（classpath 在 KubeJS 加载时才存在）。

## 7. 与 MMCE 的兼容性 / 不兼容项

| 兼容 | 不兼容 |
|---|---|
| ✅ 概念名称 `Machine` / `MachineRecipe` / `BlockArray` / `BlockArrayCache` 命名延用 | ❌ JSON 机器定义文件**不再支持**（KubeJS 替代） |
| ✅ 控制器逻辑（结构匹配 + tick 调度）**核心算法不变** | ❌ CraftTweaker 集成**完全删除**（用 KubeJS） |
| ✅ 多方块结构匹配**算法不变** | ❌ 所有第三方 mod 联动**删除** |
| ✅ 物品 / 流体 / 能量 IO 模型**核心不变** | ❌ 并行 / 多线程 / 工厂 / 升级 / 智能接口**全部 OUT** |
| ✅ RecipeModifier / Phase 概念**接口预留** | ❌ GSON 两阶段加载**删除**（用 Codec） |
| ✅ CustomPacketPayload 用法**与 MMCE 类似** | ❌ `ModularMachinery.MODID = "modularmachinery"` → `"mmcr"` |
| ✅ Capability 模型**直接借用 NeoForge 同款 API** | ❌ MMCE 1.12.2 datapack `data/<modid>/machinery/*.json` **不再扫描**（首期只 KubeJS / Java API 注册） |

## 8. 一图概览（首期）

```
        KubeJS Script (optional)              Java API (required)
                │                                  │
                ▼                                  ▼
        ┌──────────────────────────────────────────────┐
        │            MachineRegistry + RecipeRegistry  │
        └──────┬───────────────────────────────────┬───┘
               │                                   │
               ▼                                   ▼
        Machine (record)                  MachineRecipe (record impl Recipe<>)
               │                                   │
               │ uses                              │ uses
               ▼                                   ▼
        BlockArray (Map<BlockPos, BlockPredicate>)  MachineIngredient (sealed)
               │                                   ├─ ItemIngredient (Ingredient + count)
               │                                   ├─ FluidIngredient (FluidIngredient + amount)
               │                                   └─ EnergyIngredient (fePerTick)
               │
               │ bound to
               ▼
        MachineControllerBlockEntity
               │ owns
               ▼
        StructureMatcher  ──tick──▶  checkStructure()  ──found──▶  RecipeSearchTask
                                                                      │
                                                                      ▼
                                                              ActiveRecipe
                                                                      │
                                                                  tickTick()
                                                                      │
                                                                      ▼
                                                              ItemBus / FluidHatch / EnergyHatch
                                                                      │
                                                                  NeoForge capabilities
                                                                      │
                                                                      ▼
                                                              Players / pipes / wires
```

## 9. 实施节奏（与 `scope.md §7` 一致）

1. ✅ 文档（scope / api-mapping / kubejs-integration / architecture）
2. ⏳ 编写规范 spec（`docs/superpowers/specs/2026-XX-XX-mmcr-mvp-design.md`）
3. ⏳ 写实施计划（writing-plans 技能）
4. ⏳ 实施（按计划推进）
5. ⏳ 跑通「3x3x3 立方体 + 控制器 + 3 个仓 + 1 个 KubeJS 配方」
6. ⏳ 提交

Phase 2+（JEI / 并行 / 多线程 / 升级 / ME / ModularMagic）**每阶段独立开 spec**，不复用本架构文档。