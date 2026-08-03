# MMCR 注册与 datagen 整理 — 设计规范

> 第二轮迭代:清理首期 MVP 留下的样板、收紧可扩展性、把数据与代码解耦。
> 独立于 `2026-08-02-mmcr-mvp-design.md`,仅依赖它 §3.1 In Scope 划定的资产。

## 1. 目标(一句话)

> 把 `MMCRRegistries` 单文件 107 行样板、5 块共用 1 张贴图、lang 数据与 Provider 耦合等首期遗留问题清理掉,
> 同时为后续接入 MEK 气体、魔源等第三方端口种类保留"加一个实现就完事"的扩展点。

## 2. 设计约束(决策清单)

| # | 决策 | 理由 |
|---|---|---|
| R1 | **IO 端口三块合并为单一 `IOPortBlock` 类,取消 ItemBusBlock/FluidHatchBlock/EnergyHatchBlock 三个子类** | 三类均只持 `IO_TYPE` 状态 + 同 `useWithoutItem` 切方向;为后续 kind 横展去重 |
| R2 | **`IOPortBlockEntity` 抽象基类,3 个 native 子类继续保留各自 IO 逻辑** | 行为(能力暴露、tick 控制)在子类里;接口不去覆盖——v1 行为不变 |
| R3 | **引入 `IOPortKind` 接口作扩展点**,含 `id()` + `entityFactory()` + 可选钩子(`primaryCapability()` / `tick(be)`) | 后续接 MEK/Botania 只需写一个 `IOPortKind` 实现 + `MMCRPortKinds.register(...)` |
| R4 | **端口命名走"乙"方案**:`io_port_<kind>_basic` 形式,**tier 后缀常驻**,便于以后加 reinforced 等 tier 不破坏命名 | v1 仅 `_basic` 一种 tier,预留位置 |
| R5 | **Registry 拆 4 文件**:`MMCRBlocks`、`MMCRItems`、`MMCRBlockEntities`、`MMCRRecipeTypes`(创意 tab 暂留 MMCR.java 内,后续可拆) | 仿照 `I-Hate-YiChen-Doll` 的多文件 `registry/ModXxx.java` 结构,降低单文件复杂度 |
| R6 | **每 Registry 用 `LinkedHashMap<String, DeferredHolder<X,Y>>` 静态字段 + `Arrays.stream(NAMES).forEach` 注册循环** | 与参考项目结构对齐,新增条目 = 数组/Map 加一行 |
| R7 | **Lang 数据抽到独立 `MMCRTranslations.java`**,`MMCRLanguageProvider` 仅读取 + `add()` | 翻译修改不需要进入 Provider 类,符合用户"方便编辑"诉求 |
| R8 | **5 块各持独立贴图**,从 `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/` 对应原文件复制并改名 | 现在共用 1 张 `casing.png` 是 bug |
| R9 | **`java.util.function.Supplier` 与 `cn.howxu.*` 内联 FQN 全部 import 化** | 现有 `MMCRRegistries.java` 出现 16 次 `java.util.function.Supplier`,`MMCR.java` 出现 3 处 inline FQN |
| R10 | **不触碰行为逻辑**:Controller IO 检测、机器结构匹配、KubeJS 桥接、`MMCRCapabilities`、`PktMachineStatePayload` 全部不动 | 本轮专注结构性整理,不引入行为风险 |
| R11 | **不增加新依赖**,不改 `build.gradle` | 仅 org 内的代码 + 资源改动 |

## 3. 模块详解

### 3.1 Block & BlockEntity 层

**新文件与去处:**

| 动作 | 类 | 文件 |
|---|---|---|
| 保留 | `MachineControllerBlock` | `internal/block/MachineControllerBlock.java`(不动) |
| 保留 | `MachineCasingBlock` | `internal/block/MachineCasingBlock.java`(不动) |
| 新增 | `IOPortBlock` | `internal/block/IOPortBlock.java` |
| 删除 | `ItemBusBlock` | `internal/block/ItemBusBlock.java` |
| 删除 | `FluidHatchBlock` | `internal/block/FluidHatchBlock.java` |
| 删除 | `EnergyHatchBlock` | `internal/block/EnergyHatchBlock.java` |
| 新增 | `IOPortBlockEntity` | `internal/tile/IOPortBlockEntity.java`(abstract) |
| 保留 | `ItemBusBlockEntity` | 改继承 `IOPortBlockEntity`,`kind()` 返回 `MMCRPortKinds.ITEM` |
| 保留 | `FluidHatchBlockEntity` | 改继承 `IOPortBlockEntity`,`kind()` 返回 `MMCRPortKinds.FLUID` |
| 保留 | `EnergyHatchBlockEntity` | 改继承 `IOPortBlockEntity`,`kind()` 返回 `MMCRPortKinds.ENERGY` |

**`IOPortBlock` 关键签名:**

```java
public class IOPortBlock extends Block implements EntityBlock {
    public static final EnumProperty<IOType> IO_TYPE = EnumProperty.create("io_type", IOType.class);
    private final String kind;
    private final Supplier<? extends BlockEntityType<? extends IOPortBlockEntity>> beType;

    public IOPortBlock(String kind,
                       Supplier<? extends BlockEntityType<? extends IOPortBlockEntity>> beType,
                       Properties props) { ... }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(IO_TYPE);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level,
                                                       BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) level.setBlock(pos, state.cycle(IO_TYPE), 3);
        return InteractionResult.SUCCESS;
    }
}
```

> `ItemBusBlock`/`FluidHatchBlock`/`EnergyHatchBlock` 原有的全部逻辑(`EntityBlock`, `IO_TYPE` 定义, `useWithoutItem`)被 `IOPortBlock` 整体接收。

**`IOPortBlockEntity` 抽象基类:**

```java
public abstract class IOPortBlockEntity extends BlockEntity {
    protected IOPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IOType ioType() { return getBlockState().getValue(IOPortBlock.IO_TYPE); }

    /** 该实体属于哪一种 kind,与 MMCRPortKinds 注册的实例对应。 */
    public abstract IOPortKind kind();

    /** 子类可覆写,默认委托给 kind().tick(this)。 */
    public void serverTick() { tick(); }

    protected void tick() { kind().tick(this); }
}
```

子类仅需:继承、改构造器返回基类、`kind()` 返回对应 `MMCRPortKinds.ITEM/FLUID/ENERGY`。

### 3.2 IOPortKind 接口(扩展点)

**新文件**:`internal/port/IOPortKind.java`

```java
public interface IOPortKind {
    String id();

    BlockEntityType.BlockEntityFactory<? extends IOPortBlockEntity> entityFactory();

    /** 默认空实现:该 kind 的主 capability(对接 Forge / 第三方 capability)。 */
    default Optional<Class<?>> primaryCapability() { return Optional.empty(); }

    /** 默认空实现:服务端 tick 钩子,用于 MEK 气体管道分发、Botania 魔力蔓延等。 */
    default void tick(IOPortBlockEntity be) {}
}
```

### 3.3 端口种类注册器

**新文件**:`registry/MMCRPortKinds.java`

```java
public final class MMCRPortKinds {

    public record Simple(
            String id,
            BlockEntityType.BlockEntityFactory<? extends IOPortBlockEntity> factory)
            implements IOPortKind {}

    private static final List<IOPortKind> REGISTRY = new CopyOnWriteArrayList<>(List.of(
            new Simple("item",   ItemBusBlockEntity::new),
            new Simple("fluid",  FluidHatchBlockEntity::new),
            new Simple("energy", EnergyHatchBlockEntity::new)
    ));

    public static void register(IOPortKind kind) { REGISTRY.add(kind); }
    public static List<IOPortKind> all() { return Collections.unmodifiableList(REGISTRY); }

    public static final IOPortKind ITEM   = REGISTRY.get(0);
    public static final IOPortKind FLUID  = REGISTRY.get(1);
    public static final IOPortKind ENERGY = REGISTRY.get(2);

    private MMCRPortKinds() {}
}
```

> 注:`CopyOnWriteArrayList` 允许 mod 在 `modBus` 监听器(如 `FMLCommonSetupEvent`)里追加 kind 而不并发触发问题。
> 第三方追加示例(MEKGasCompat,魔源同理):
> ```java
> MMCRPortKinds.register(new MMCRPortKinds.Simple("gas", GasPortBlockEntity::new));
> ```

### 3.4 Registry 文件拆分

**`registry/MMCRBlocks.java`**(参考 `ModBlocks.java`):

```java
public final class MMCRBlocks {
    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(MMCR.MODID);
    public static final LinkedHashMap<String, DeferredHolder<Block, Block>> BLOCKS = new LinkedHashMap<>();

    public static final DeferredHolder<Block, Block> CONTROLLER =
            REGISTER.registerBlock("controller", MachineControllerBlock::new);
    public static final DeferredHolder<Block, Block> CASING =
            REGISTER.registerBlock("casing", MachineCasingBlock::new);

    static {
        BLOCKS.put("controller", CONTROLLER);
        BLOCKS.put("casing", CASING);
    }

    public static void register(IEventBus bus) {
        MMCRPortKinds.all().forEach(k -> {
            String name = "io_port_" + k.id() + "_basic";
            BLOCKS.put(name, REGISTER.registerBlock(name,
                    props -> new IOPortBlock(k.id(),
                            MMCRBlockEntities.holder(name), props)));
        });
        REGISTER.register(bus);
    }
}
```

**`registry/MMCRItems.java`**:同模式遍历,产出 `IO_PORT_ITEM_*`、`CONTROLLER_ITEM`、`CASING_ITEM` 三种 `BlockItem`。

**`registry/MMCRBlockEntities.java`**:同模式遍历 `MMCRPortKinds`,每个 kind 注册一个 `BlockEntityType<IOPortBlockEntity>(factory, blockRef)`。
> 暴露 `holder(String blockName)` 帮助类用于 `MMCRBlocks` 反向引用。

**`registry/MMCRRecipeTypes.java`**:`MACHINE_RECIPE_TYPE` / `MACHINE_RECIPE_SERIALIZER` 搬到这里单文件保持简短。

**移除**:`registry/MMCRRegistries.java` 单文件(整体删除)。

**`MMCR.java`** 内构造器更改为:

```java
MMCRBlocks.register(modBus);
MMCRItems.register(modBus);
MMCRBlockEntities.register(modBus);
MMCRRecipeTypes.register(modBus);
// MMCRCreativeTabs 暂留原状
```

### 3.5 Datagen 简化

**`datagen/MMCRModelProvider.java`** 重写核心循环:

```java
@Override
protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
    MMCRBlocks.BLOCKS.forEach((name, holder) -> {
        Block block = holder.get();
        ResourceLocation texture = MMCR.id("block/" + name);
        TexturedModel.Provider provider = b -> TexturedModel.createAllSame(texture);
        if (name.equals("controller")) {
            blockModels.createHorizontallyRotatedBlock(block, provider);
        } else {
            blockModels.createTrivialBlock(block, provider);
        }
        MMCRItems.ITEMS.get(name).ifPresent(itemHolder ->
            itemModels.itemModelOutput.accept(itemHolder.get(),
                ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(block))));
    });
}

@Override protected Stream<? extends Holder<Block>> getKnownBlocks() {
    return MMCRBlocks.BLOCKS.values().stream().map(h -> h.get().builtInRegistryHolder());
}
@Override protected Stream<? extends Holder<Item>> getKnownItems() {
    return MMCRItems.ITEMS.values().stream().map(h -> h.get().builtInRegistryHolder());
}
```

### 3.6 Lang 抽离

**新文件**:`datagen/MMCRTranslations.java`(纯数据):

```java
public final class MMCRTranslations {
    public static final Map<String, Map<String, String>> ALL = Map.of(
        "en_us", Map.ofEntries(
            Map.entry("itemGroup.mmcr",          "Modular Machinery: Refoxed"),
            Map.entry("block.mmcr.controller",   "Machine Controller"),
            Map.entry("block.mmcr.casing",       "Machine Casing"),
            Map.entry("block.mmcr.io_port_item_basic",   "Item IO Port"),
            Map.entry("block.mmcr.io_port_fluid_basic",  "Fluid IO Port"),
            Map.entry("block.mmcr.io_port_energy_basic", "Energy IO Port")
        ),
        "zh_cn", Map.ofEntries(
            Map.entry("itemGroup.mmcr",          "模块化机械:Refoxed"),
            Map.entry("block.mmcr.controller",   "机器控制器"),
            Map.entry("block.mmcr.casing",       "机器外壳"),
            Map.entry("block.mmcr.io_port_item_basic",   "物品 IO 端口"),
            Map.entry("block.mmcr.io_port_fluid_basic",  "流体 IO 端口"),
            Map.entry("block.mmcr.io_port_energy_basic", "能量 IO 端口")
        )
    );

    private MMCRTranslations() {}
}
```

**`datagen/MMCRLanguageProvider.java`** 收缩为:

```java
public final class MMCRLanguageProvider extends LanguageProvider {
    private final String locale;
    public MMCRLanguageProvider(PackOutput output, String locale) {
        super(output, MMCR.MODID, locale);
        this.locale = locale;
    }
    @Override protected void addTranslations() {
        Map<String,String> map = MMCRTranslations.ALL.get(locale);
        if (map != null) map.forEach(this::add);
    }
}
```

### 3.7 贴图方案

**`src/main/resources/assets/mmcr/textures/block/` 新增 4 张,`casing.png` 继续使用(等效):**

| 新名 | 来自 mmce 路径 | 用途 |
|---|---|---|
| `casing.png`(已存在) | `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/blockcasing_plain.png` | 机器外壳(尺寸/格式一致即可,无需复制覆盖) |
| `controller.png` | `reference/mmce/.../overlay_controller.png` | 控制器 |
| `io_port_item_basic.png` | `reference/mmce/.../overlay_inputbus_normal.png` | 物品 IO 端口 |
| `io_port_fluid_basic.png` | `reference/mmce/.../overlay_fluidinputhatch_normal.png` | 流体 IO 端口 |
| `io_port_energy_basic.png` | `reference/mmce/.../overlay_energyinputhatch_normal.png` | 能量 IO 端口 |

> mmce 路径通过 `bash` `cp` 复制到 mmcr 资源目录;不修改源文件,纯导入。

### 3.8 FQN / import 简化

**`registry/MMCRBlocks.java`(以及 Items/BlockEntities/RecipeTypes):**
- `import java.util.function.Supplier;`
- 各处 `Supplier<...>` 引用直接用短名
- `MMCRRegistries.X.get()` 自引用改为 `X.get()`(若有)
- 不再 `import cn.howxu.mmcr.registry.MMCRRegistries`(整文件删除)

**`MMCR.java`:**
加 import:
```java
import cn.howxu.mmcr.internal.event.MMCRCapabilities;
import cn.howxu.mmcr.internal.command.MMCRReloadCommand;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
```
改行:
```java
modBus.addListener(MMCRCapabilities::register);
NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent ev) ->
        MMCRReloadCommand.register(ev.getDispatcher()));
```
其余 `net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent` 已 import,无需变。

## 4. 范围边界

### 4.1 In Scope

1. Block 类合并(3 → 1 抽象 + 1 通用);BlockEntity 重构(子类继承基类)
2. Registry 拆分(1 文件 → 4 文件)
3. `IOPortKind` 接口 + `MMCRPortKinds` 注册器
4. `MMCRModelProvider` 改写为循环
5. `MMCRLanguageProvider` 与 `MMCRTranslations` 拆分
6. 5 张独立贴图就位
7. `Supplier` 与 `MMCR.java` FQN 清理

### 4.2 Out of Scope(明确不动)

- Controller 行为、`MachineRegistry`、`BlockArray`、`MachineRecipe`、`MachineIngredient`、`StructureMatcher` 全套机器 API
- `MMCRCapabilities`、`PktMachineStatePayload`、`MMCRReloadCommand`
- KubeJS 桥接(`compat/kubejs/*`)
- `MMCRConfig` / `MMCRDefaultMachines` 等内部机器工厂
- `RegisterPayloadHandlersEvent` 现有 Pkt 注册(已含在 R10)
- 接入 MEK/Botania 的真实实现(留接口、留注册入口,本轮不写 compat mod)

## 5. 验收标准

| # | 标准 | 验证方式 |
|---|---|---|
| V1 | `gradle build` 在干净的 `.superpowers/` + `build/` 下通过,无 missing texture warning | `cd repo && ./gradlew build` |
| V2 | `en_us.json` / `zh_cn.json` 在 `src/generated/resources/...` 正确产出 5 个 block 翻译 + 1 个 itemGroup + 1 个 config title | 检查 generated 文件存在且含所有 7 条 key |
| V3 | 5 块在游戏中放置后,外观肉眼可见不同 | 视觉检验(mc 客户端放置 5 块截图对比) |
| V4 | 仓库内不再出现 `java.util.function.Supplier` 全名 | `grep -R "java\.util\.function\.Supplier" src/main/java/` 返回空 |
| V5 | `cn.howxu.mmcr.internal.event.MMCRCapabilities::register` 全名 lambda 在 `MMCR.java` 中消失 | grep 验证 |
| V6 | `ItemBusBlock.java` / `FluidHatchBlock.java` / `EnergyHatchBlock.java` 三个文件已删除 | 文件系统检查 |
| V7 | 总改动行数净减少(删 3 个 Block 类 + 1 个 MMCRRegistries 加 4 个新 Registry 文件 + 1 个抽象基类 + 1 个 IOPortKind + 1 个 MMCRPortKinds 后,净行数 < 原来 107 行的 `MMCRRegistries.java`) | `wc -l` 量化 |

## 6. 风险与回退

| 风险 | 影响 | 回退 |
|---|---|---|
| IOPortBlock 状态默认值变化造成老存档 BlockState 失效 | 老存档 controller/casing 状态错位 | (本轮不动 Controller/Casing;只在 IOPortBlock 上合并行为,Controller `FACING/FORMED/ACTIVE` 不受影响) |
| 老的 `block.mmcr.item_bus` 等 lang key 改名后,已有翻译 mod 失效 | i18n 翻译贡献者看到 key 列表变了 | 接受,v1 玩家基数低 |
| 5 张新贴图尺寸若与原 casing.png 像素不一致(若 mmce 来源不同尺寸) | 模型拉伸/模糊 | 检查文件长宽,若不一致用图像处理工具统一(由 ops/asset 流程处理,不归本轮代码改动) |

## 7. 相关文档

- `docs/superpowers/specs/2026-08-02-mmcr-mvp-design.md` — 上游首期设计,定义本轮的资产边界
- `reference/I-Hate-YiChen-Doll/src/main/java/github/ihatechpack/yichendoll/registry/ModBlocks.java` — Registry array-loop 注册风格参考
- `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/` — 5 张目标贴图来源
