# MMCR 注册与 datagen 整理 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) 或 superpowers:executing-plans 逐任务执行。步骤用 `- [ ]` checkbox 标记进度。

**Goal:** 把首期遗留的注册样板,lang 与 Provider 耦合,5 块共用 1 张贴图等清理掉;为后续接 MEK 气体 / 魔源等第三方端口类型留下"写一个接口实现就完事"的扩展点。

**Architecture:** 单文件 `MMCRRegistries.java`(107 行)拆为 4 个 Registry 类(`MMCRBlocks` / `MMCRItems` / `MMCRBlockEntities` / `MMCRRecipeTypes`),沿用参考项目 `I-Hate-YiChen-Doll` 的 `Map<String, DeferredHolder>` + 循环注册风格;三块 IO 端口(Block/BlockEntity)合并为单一类 `IOPortBlock` + 抽象基类 `IOPortBlockEntity`,通过接口 `IOPortKind` 暴露扩展点;Lang 数据抽到 `MMCRTranslations`,Provider 只读不存数据;5 块各持独立贴图(从 mmce 对应贴图拷贝)。

**Tech Stack:**
- Minecraft 26.1.2 + NeoForge 26.1.2.84
- Java 25 toolchain
- `DeferredRegister` / `DeferredHolder` / `BlockEntityType`
- JUnit 5 + AssertJ(`gradlew test`)
- NeoForge datagen(`gradlew runData` 或 `gradlew build`)

## Global Constraints

每条都从 spec 复制,**所有任务都隐含遵守**:

- mod_id = `mmcr`;namespace 一致
- 包结构 `cn.howxu.mmcr.api.*` + `cn.howxu.mmcr.internal.*` + `cn.howxu.mmcr.registry.*` + `cn.howxu.mmcr.datagen.*`(沿用)
- IO 端口命名:`io_port_<kind>_basic`,tier 后缀常驻(v1 仅 `_basic`);Controller / Casing 保留短名
- 不改 `build.gradle`、不改 KubeJS compat、不改 Machine API、不改 Capability 逻辑
- 每完成一个任务至少 1 个 commit;不积压
- 现有 7 个 gametest(`src/gametest/...`)全部必须继续通过

## Reference Docs(实现前必读)

- `docs/superpowers/specs/2026-08-03-mmcr-refactor-design.md` — 本轮设计执行摘要 + DoD
- `reference/I-Hate-YiChen-Doll/src/main/java/github/ihatechpack/yichendoll/registry/ModBlocks.java` — Registry `Map<String, DeferredHolder>` + 循环风格参考
- `reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/` — 5 张目标贴图来源

## File Map(任务 1-13 完成后的全貌)

```
src/main/java/cn/howxu/mmcr/
├─ MMCR.java                              # 改动:用新 4 个 Registry,清理 FQN(任务 8)
├─ registry/
│  ├─ MMCRPortKinds.java                  # ★ 新增:扩展点 + ITEM/FLUID/ENERGY(任务 4)
│  ├─ MMCRBlocks.java                     # ★ 新增(任务 6)
│  ├─ MMCRItems.java                      # ★ 新增(任务 6)
│  ├─ MMCRBlockEntities.java              # ★ 新增(任务 6)
│  ├─ MMCRRecipeTypes.java                # ★ 新增(任务 6)
│  └─ MMCRRegistries.java                 # ❌ 删除(任务 8)
├─ internal/
│  ├─ block/
│  │  ├─ IOPortBlock.java                 # ★ 新增(任务 2)
│  │  ├─ ItemBusBlock.java                # ❌ 删除(任务 9)
│  │  ├─ FluidHatchBlock.java             # ❌ 删除(任务 9)
│  │  ├─ EnergyHatchBlock.java            # ❌ 删除(任务 9)
│  │  ├─ MachineCasingBlock.java          # 保留(不动)
│  │  └─ MachineControllerBlock.java      # 保留(不动)
│  ├─ tile/
│  │  ├─ IOPortBlockEntity.java           # ★ 新增(任务 3)
│  │  ├─ ItemBusBlockEntity.java          # 改父类 + kind()(任务 5)
│  │  ├─ FluidHatchBlockEntity.java       # 改父类 + kind()(任务 5)
│  │  ├─ EnergyHatchBlockEntity.java      # 改父类 + kind()(任务 5)
│  │  └─ MachineControllerBlockEntity.java # 改 MMCRRegistries 引用(任务 7)
│  ├─ port/
│  │  └─ IOPortKind.java                  # ★ 新增(任务 1)
│  ├─ event/MMCRCapabilities.java         # 改 MMCRRegistries 引用(任务 7)
│  ├─ machine/MMCRDefaultMachines.java    # 改 MMCRRegistries 引用(任务 7)
│  └─ api/recipe/MachineRecipe.java       # 改 MMCRRegistries 引用(任务 7)
├─ datagen/
│  ├─ MMCRTranslations.java               # ★ 新增(任务 10)
│  ├─ MMCRLanguageProvider.java           # 改读 MMCRTranslations(任务 10)
│  └─ MMCRModelProvider.java              # 重写为循环(任务 11)

src/main/resources/assets/mmcr/textures/block/
├─ casing.png                             # 已存在,不动(等效 mmce blockcasing_plain)
├─ controller.png                         # ★ 新增(任务 12)
├─ io_port_item_basic.png                 # ★ 新增(任务 12)
├─ io_port_fluid_basic.png                # ★ 新增(任务 12)
└─ io_port_energy_basic.png               # ★ 新增(任务 12)

src/test/java/cn/howxu/mmcr/
├─ test/TestBootstrap.java                # 改 MMCRRegistries 引用(任务 7)
└─ registry/MMCRPortKindsTest.java        # ★ 新增(任务 4)
```

---

## Task 1: 添加 `IOPortKind` 接口(扩展点)

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/port/IOPortKind.java`

- [ ] **Step 1: 创建文件**

完整内容:

```java
package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Optional;

/**
 * 一种 IO 端口类型的协议。新增一种 IO 端口(气体、魔源等)
 * 只需实现本接口并通过 {@link cn.howxu.mmcr.registry.MMCRPortKinds#register} 注册。
 */
public interface IOPortKind {

    /** 该 kind 的字符串 id,出现在 block 注册名里,如 "item"/"fluid"/"energy"/"gas"/"mana"。 */
    String id();

    /** 该 kind 对应的 BlockEntity 工厂。Block 注册时由这里创建对应实体。 */
    BlockEntityType.BlockEntityFactory<? extends IOPortBlockEntity> entityFactory();

    /** 该 kind 的主 capability 类型(对接 Forge / 第三方 capability)。默认无。 */
    default Optional<Class<?>> primaryCapability() { return Optional.empty(); }

    /** 该 kind 的服务端 tick 钩子,用于 MEK 气体管道分发等。默认无。 */
    default void tick(IOPortBlockEntity be) {}
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && ./gradlew compileJava 2>&1 | tail -20
```

期望:`BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git add src/main/java/cn/howxu/mmcr/internal/port/IOPortKind.java && \
  git commit -m "feat(port): add IOPortKind interface as extension point for IO port types"
```

---

## Task 2: 添加 `IOPortBlock` 通用类

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/block/IOPortBlock.java`

- [ ] **Step 1: 创建文件**

完整内容:

```java
package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class IOPortBlock extends Block implements EntityBlock {

    public static final EnumProperty<IOType> IO_TYPE = EnumProperty.create("io_type", IOType.class);

    private final String kind;
    private final Supplier<? extends BlockEntityType<?>> beType;

    public IOPortBlock(String kind,
                       Supplier<? extends BlockEntityType<?>> beType,
                       Properties props) {
        super(props);
        this.kind = kind;
        this.beType = beType;
        registerDefaultState(stateDefinition.any().setValue(IO_TYPE, IOType.INPUT));
    }

    public String kind() { return kind; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(IO_TYPE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            level.setBlock(pos, state.cycle(IO_TYPE), 3);
        }
        return InteractionResult.SUCCESS;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && ./gradlew compileJava 2>&1 | tail -20
```

期望:`BUILD SUCCESSFUL`(会因 `IOPortBlockEntity` 不存在报错 → 移到 Task 3 后再编)。

**注意:** 若 `IOPortBlockEntity` 不存在,编译器会报错 `cannot find symbol class IOPortBlockEntity`。先执行 Task 3 后再编译通过。

- [ ] **Step 3: 暂不提交,等 Task 3 后一并提交**

---

## Task 3: 添加 `IOPortBlockEntity` 抽象基类

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/tile/IOPortBlockEntity.java`

- [ ] **Step 1: 创建文件**

完整内容:

```java
package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class IOPortBlockEntity extends BlockEntity {

    protected IOPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IOType ioType() {
        return getBlockState().getValue(IOPortBlock.IO_TYPE);
    }

    public abstract IOPortKind kind();

    public void serverTick() {
        tick();
    }

    protected void tick() {
        kind().tick(this);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && ./gradlew compileJava 2>&1 | tail -20
```

期望:`BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git add src/main/java/cn/howxu/mmcr/internal/block/IOPortBlock.java \
          src/main/java/cn/howxu/mmcr/internal/tile/IOPortBlockEntity.java && \
  git commit -m "feat(block,tile): unify IO port into IOPortBlock and abstract IOPortBlockEntity"
```

---

## Task 4: 添加 `MMCRPortKinds` 注册器

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/registry/MMCRPortKinds.java`
- Create: `src/test/java/cn/howxu/mmcr/registry/MMCRPortKindsTest.java`

- [ ] **Step 1: 创建主类**

`src/main/java/cn/howxu/mmcr/registry/MMCRPortKinds.java`:

```java
package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.block.IOPortBlockEntity;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MMCRPortKinds {

    public record Simple(
            String id,
            BlockEntityType.BlockEntityFactory<? extends IOPortBlockEntity> factory)
            implements IOPortKind {}

    public static final IOPortKind ITEM   = new Simple("item",   ItemBusBlockEntity::new);
    public static final IOPortKind FLUID  = new Simple("fluid",  FluidHatchBlockEntity::new);
    public static final IOPortKind ENERGY = new Simple("energy", EnergyHatchBlockEntity::new);

    private static final List<IOPortKind> REGISTRY = new CopyOnWriteArrayList<>(
            List.of(ITEM, FLUID, ENERGY));

    public static void register(IOPortKind kind) { REGISTRY.add(kind); }

    public static List<IOPortKind> all() { return Collections.unmodifiableList(REGISTRY); }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        REGISTRY.clear();
        REGISTRY.addAll(List.of(ITEM, FLUID, ENERGY));
    }

    private MMCRPortKinds() {}
}
```

- [ ] **Step 2: 编写测试**

`src/test/java/cn/howxu/mmcr/registry/MMCRPortKindsTest.java`:

```java
package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MMCRPortKindsTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void resetRegistry() {
        MMCRPortKinds.clearForTesting();
    }

    @Test
    void native_kinds_present() {
        assertThat(MMCRPortKinds.all()).extracting(IOPortKind::id)
                .contains("item", "fluid", "energy");
    }

    @Test
    void register_appends_new_kind() {
        BlockEntityType.BlockEntityFactory<cn.howxu.mmcr.internal.tile.IOPortBlockEntity> dummyFactory =
                (BlockPos p, BlockState s) -> null;
        IOPortKind extra = new MMCRPortKinds.Simple("test_extra", dummyFactory);
        MMCRPortKinds.register(extra);
        assertThat(MMCRPortKinds.all()).hasSize(4);
        assertThat(MMCRPortKinds.all()).extracting(IOPortKind::id).contains("test_extra");
    }
}
```

> dummy factory 返回 null(测试只查注册表的可枚举性,不触发 `BlockEntityType.create()`)。`BeforeEach` 调 `clearForTesting()` 保证测试间不互相污染。

- [ ] **Step 3: 运行测试**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && ./gradlew test --tests MMCRPortKindsTest 2>&1 | tail -30
```

期望:`BUILD SUCCESSFUL`,`2 tests completed, 0 failed`。

- [ ] **Step 4: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git add src/main/java/cn/howxu/mmcr/registry/MMCRPortKinds.java \
          src/test/java/cn/howxu/mmcr/registry/MMCRPortKindsTest.java && \
  git commit -m "feat(registry): add MMCRPortKinds registry with native ITEM/FLUID/ENERGY"
```

---

## Task 5: 重构 3 个 BlockEntity 子类继承 `IOPortBlockEntity`

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/ItemBusBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/FluidHatchBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/EnergyHatchBlockEntity.java`

**Interfaces:**
- Consumes: `IOPortBlockEntity`(任务 3),`MMCRPortKinds` 的 ITEM/FLUID/ENERGY(任务 4)
- Produces: 3 个子类都实现 `kind()`,返回对应 `MMCRPortKinds.X`

- [ ] **Step 1: 先读当前 3 个 BE 文件**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  cat src/main/java/cn/howxu/mmcr/internal/tile/ItemBusBlockEntity.java \
      src/main/java/cn/howxu/mmcr/internal/tile/FluidHatchBlockEntity.java \
      src/main/java/cn/howxu/mmcr/internal/tile/EnergyHatchBlockEntity.java
```

读出每个 BE 的现有字段、方法,确保 modify 时不丢行为。

- [ ] **Step 2: 修改 `ItemBusBlockEntity`**

将父类从 `BlockEntity` 改为 `IOPortBlockEntity`,加 import,加 `kind()` 方法。

替换 `class ItemBusBlockEntity extends BlockEntity {` 为 `class ItemBusBlockEntity extends IOPortBlockEntity {`

替换构造器签名 `public ItemBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {` 为相同(继承基类同名构造器即可)。

加 import:
```java
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.MMCRPortKinds;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
```
(若已有则不重复加)

在类体内加方法:
```java
@Override
public IOPortKind kind() { return MMCRPortKinds.ITEM; }
```

- [ ] **Step 3: 修改 `FluidHatchBlockEntity`**

同上,`kind()` 返回 `MMCRPortKinds.FLUID`。

- [ ] **Step 4: 修改 `EnergyHatchBlockEntity`**

同上,`kind()` 返回 `MMCRPortKinds.ENERGY`。

- [ ] **Step 5: 编译验证**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && ./gradlew compileJava 2>&1 | tail -20
```

期望:`BUILD SUCCESSFUL`。老的 `MMCRRegistries.ITEM_BUS_BE.get()` 等引用仍存在,需在 Task 7 后清除。

- [ ] **Step 6: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git add src/main/java/cn/howxu/mmcr/internal/tile/ItemBusBlockEntity.java \
          src/main/java/cn/howxu/mmcr/internal/tile/FluidHatchBlockEntity.java \
          src/main/java/cn/howxu/mmcr/internal/tile/EnergyHatchBlockEntity.java && \
  git commit -m "refactor(tile): make IO port block entities extend abstract IOPortBlockEntity"
```

---

## Task 6: 添加 4 个新 Registry 文件

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/registry/MMCRBlocks.java`
- Create: `src/main/java/cn/howxu/mmcr/registry/MMCRItems.java`
- Create: `src/main/java/cn/howxu/mmcr/registry/MMCRBlockEntities.java`
- Create: `src/main/java/cn/howxu/mmcr/registry/MMCRRecipeTypes.java`

**Interfaces:**
- Consumes: `MMCRPortKinds`(任务 4),`IOPortBlock`(任务 2)
- Produces: `MMCRBlocks.BLOCKS: LinkedHashMap<String, DeferredHolder<Block,Block>>` + 命名常量 `CONTROLLER` / `CASING` / `IO_PORT_*`;MMCRItems/MMCRBlockEntities 同结构;MMCRRecipeTypes 提供 `MACHINE_RECIPE_TYPE` / `MACHINE_RECIPE_SERIALIZER`

- [ ] **Step 1: 创建 `MMCRBlocks.java`**

完整内容:

```java
package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineCasingBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

public final class MMCRBlocks {

    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<Block, Block>> BLOCKS = new LinkedHashMap<>();

    static {
        BLOCKS.put("controller",
                REGISTER.registerBlock("controller", MachineControllerBlock::new));
        BLOCKS.put("casing",
                REGISTER.registerBlock("casing", MachineCasingBlock::new));
        MMCRPortKinds.all().forEach(MMCRBlocks::registerIoPort);
    }

    public static final DeferredHolder<Block, Block> CONTROLLER = BLOCKS.get("controller");
    public static final DeferredHolder<Block, Block> CASING     = BLOCKS.get("casing");

    private static void registerIoPort(IOPortKind k) {
        String name = "io_port_" + k.id() + "_basic";
        // 每块自带闭包,运行时才对 MMCRBlockEntities.BES 做反向查找
        // ——这是为了避开"块注册 → BE 注册 → 块又引用 BE"的循环依赖
        Supplier<? extends BlockEntityType<?>> beTypeSupplier =
                () -> MMCRBlockEntities.BES.get(name).get();
        BLOCKS.put(name, REGISTER.registerBlock(name,
                props -> new IOPortBlock(k.id(), beTypeSupplier, props)));
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private MMCRBlocks() {}
}
```

> **关键设计:** 每个 IO port 块构造时持有一个独立的 `Supplier<BlockEntityType>`,运行时(`world.load`)才查 `MMCRBlockEntities.BES`,这样打破了"块引 BE、BE 引块"的循环初始化。

- [ ] **Step 2: 创建 `MMCRBlockEntities.java`**

完整内容:

```java
package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

public final class MMCRBlockEntities {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create((Class) BlockEntityType.class,
                    BuiltInRegistries.BLOCK_ENTITY_TYPE, MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<BlockEntityType<?>, BlockEntityType<?>>>
            BES = new LinkedHashMap<>();

    static {
        BES.put("controller", register("controller",
                () -> BlockEntityType.Builder
                        .of(MachineControllerBlockEntity::new, MMCRBlocks.CONTROLLER.get())
                        .build(null)));
        MMCRPortKinds.all().forEach(k -> {
            String name = "io_port_" + k.id() + "_basic";
            BES.put(name, register(name, () -> BlockEntityType.Builder
                    .of(k.entityFactory(), MMCRBlocks.BLOCKS.get(name).get())
                    .build(null)));
        });
    }

    /** 通配符封装的 register 助手,避免每条目都要 cast。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> register(
            String name, Supplier<BlockEntityType<?>> sup) {
        return (DeferredHolder<BlockEntityType<?>, BlockEntityType<?>>) (DeferredHolder<?, ?>)
                REGISTER.register(name, sup);
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private MMCRBlockEntities() {}
}
```

> 不再提供 `BE_SUPPLIER` 之类的占位 —— Step 1 通过闭包 + `BES.get(name).get()` 在运行时解决 BE 类型。

- [ ] **Step 3: 创建 `MMCRItems.java`**

完整内容:

```java
package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;

public final class MMCRItems {

    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(MMCR.MODID);

    public static final LinkedHashMap<String, DeferredHolder<Item, Item>> ITEMS = new LinkedHashMap<>();

    static {
        MMCRBlocks.BLOCKS.forEach((name, blockHolder) -> {
            DeferredHolder<Item, Item> itemHolder = REGISTER.register(name, () ->
                    new BlockItem(blockHolder.get(),
                            new Item.Properties().setId(
                                    ResourceKey.create(Registries.ITEM, MMCR.id(name)))));
            ITEMS.put(name, itemHolder);
        });
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private MMCRItems() {}
}
```

- [ ] **Step 4: 创建 `MMCRRecipeTypes.java`**

完整内容:

```java
package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeSerializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MMCRRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> REGISTER =
            DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, MMCR.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZER_REGISTER =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, MMCR.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> MACHINE_RECIPE_TYPE =
            REGISTER.register("machine_recipe",
                    () -> RecipeType.simple(MMCR.id("machine_recipe")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MachineRecipe>> MACHINE_RECIPE_SERIALIZER =
            SERIALIZER_REGISTER.register("machine_recipe", () -> MachineRecipeSerializer.INSTANCE);

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
        SERIALIZER_REGISTER.register(bus);
    }

    private MMCRRecipeTypes() {}
}
```

- [ ] **Step 5: 编译验证**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && ./gradlew compileJava 2>&1 | tail -30
```

期望:`BUILD SUCCESSFUL`。新文件目前还没被 `MMCR.java` 调用,旧调用依然有效。若失败检查 `Step 1` 闭包类型是否一致(`BlockEntityType<? extends IOPortBlockEntity>`)。

- [ ] **Step 6: 暂不提交,留到 Task 8(与切换消费者合并提交)**

---

## Task 7: 迁移所有消费者到新 Registry 名字

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/event/MMCRCapabilities.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/machine/MMCRDefaultMachines.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipe.java`
- Modify: `src/test/java/cn/howxu/mmcr/test/TestBootstrap.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/machine/MMCRDefaultMachinesTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/BlockArrayMatchGameTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/ControllerTickGameTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/E2ERecipeRunGameTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/EnergyHatchCapabilityGameTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/ItemBusCapabilityGameTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/FluidHatchCapabilityGameTest.java`

**Renaming table:**

| 旧引用 | 新引用 |
|---|---|
| `MMCRRegistries.CASING_BLOCK` | `MMCRBlocks.CASING` |
| `MMCRRegistries.CONTROLLER_BLOCK` | `MMCRBlocks.CONTROLLER` |
| `MMCRRegistries.ITEM_BUS_BLOCK` | `MMCRBlocks.BLOCKS.get("io_port_item_basic")` |
| `MMCRRegistries.FLUID_HATCH_BLOCK` | `MMCRBlocks.BLOCKS.get("io_port_fluid_basic")` |
| `MMCRRegistries.ENERGY_HATCH_BLOCK` | `MMCRBlocks.BLOCKS.get("io_port_energy_basic")` |
| `MMCRRegistries.ITEM_BUS_BE` | `MMCRBlockEntities.BES.get("io_port_item_basic")` |
| `MMCRRegistries.FLUID_HATCH_BE` | `MMCRBlockEntities.BES.get("io_port_fluid_basic")` |
| `MMCRRegistries.ENERGY_HATCH_BE` | `MMCRBlockEntities.BES.get("io_port_energy_basic")` |
| `MMCRRegistries.CONTROLLER_BE` | `MMCRBlockEntities.BES.get("controller")` |
| `MMCRRegistries.MACHINE_RECIPE_TYPE` | `MMCRRecipeTypes.MACHINE_RECIPE_TYPE` |
| `MMCRRegistries.MACHINE_RECIPE_SERIALIZER` | `MMCRRecipeTypes.MACHINE_RECIPE_SERIALIZER` |

旧 `ItemBusBlockEntity.java`/`FluidHatchBlockEntity.java`/`EnergyHatchBlockEntity.java` 里 `MMCRRegistries.X_BE.get()` 的引用也换成 `MMCRBlockEntities.BES.get("io_port_X_basic")`。

- [ ] **Step 1: 全文替换所有 main 文件**

逐文件编辑:
- `MMCRCapabilities.java`:替换 `import cn.howxu.mmcr.registry.MMCRRegistries` 为 `import cn.howxu.mmcr.registry.MMCRBlockEntities` + `import cn.howxu.mmcr.registry.MMCRBlocks`;按上表替换 6 处。
- `MMCRDefaultMachines.java`:换 1 处 CASING_BLOCK。
- `MachineControllerBlockEntity.java`:换 1 处 CONTROLLER_BE。
- `MachineRecipe.java`:换 2 处 RECIPE_*。
- `ItemBusBlockEntity.java` / `FluidHatchBlockEntity.java` / `EnergyHatchBlockEntity.java`:换 super() 里的 BE 引用。

具体替换方法:每个文件用你的 Edit 工具按上表一一改。

- [ ] **Step 2: 替换测试与 gametest 文件**

- `TestBootstrap.java`:换 1 处 `MMCRRegistries.CASING_BLOCK` → `MMCRBlocks.CASING`,加 import `MMCRBlocks`。
- `MMCRDefaultMachinesTest.java`:同上 1 处。
- 7 个 gametest:同规则,多个文件各 1-2 处。

- [ ] **Step 3: 编译验证**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && ./gradlew compileJava compileTestJava compileGameTestJava 2>&1 | tail -30
```

期望:`BUILD SUCCESSFUL`。

- [ ] **Step 4: 运行所有测试**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && ./gradlew test 2>&1 | tail -30
```

期望:`BUILD SUCCESSFUL`,所有 unit tests 通过(7 + 2 = 9 tests)。

- [ ] **Step 5: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git add src/main src/test src/gametest && \
  git commit -m "refactor: migrate all consumers from MMCRRegistries to MMCRBlocks/MMCRBlockEntities/MMCRRecipeTypes"
```

---

## Task 8: 切换 `MMCR.java` + 删除旧 `MMCRRegistries.java`

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/MMCR.java`
- Delete: `src/main/java/cn/howxu/mmcr/registry/MMCRRegistries.java`

- [ ] **Step 1: 编辑 `MMCR.java`(内联 CREATIVE_TABS,符合 spec R5)**

将整个类替换为:

```java
package cn.howxu.mmcr;

import cn.howxu.mmcr.config.MMCRConfig;
import cn.howxu.mmcr.internal.command.MMCRReloadCommand;
import cn.howxu.mmcr.internal.event.MMCRCapabilities;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.registry.MMCRBlockEntities;
import cn.howxu.mmcr.registry.MMCRBlocks;
import cn.howxu.mmcr.registry.MMCRItems;
import cn.howxu.mmcr.registry.MMCRRecipeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MMCR.MODID)
public class MMCR {
    public static final String MODID = "mmcr";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    // 创意 tab 注册(spec R5:暂留 MMCR.java 内,后续可拆)
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public MMCR(IEventBus modBus, ModContainer modContainer) {
        MMCRBlocks.register(modBus);
        MMCRItems.register(modBus);
        MMCRBlockEntities.register(modBus);
        MMCRRecipeTypes.register(modBus);
        CREATIVE_TABS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, MMCRConfig.SPEC);
        modBus.addListener(MMCRCapabilities::register);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent ev) ->
                MMCRReloadCommand.register(ev.getDispatcher()));
        modBus.addListener((RegisterPayloadHandlersEvent ev) -> {
            ev.registrar("1").playToClient(
                    PktMachineStatePayload.TYPE,
                    PktMachineStatePayload.STREAM_CODEC,
                    (payload, ctx) -> MMCR.LOG.debug("Received machine state: {}", payload));
        });
        CREATIVE_TABS.register(MODID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmcr"))
                .icon(() -> BuiltInRegistries.ITEM.get(MMCR.id("casing")).getDefaultInstance())
                .displayItems((params, output) ->
                        MMCRItems.ITEMS.values().forEach(h -> output.accept(h.get())))
                .build());
        LOG.info("MMCR {} loaded", modContainer.getModInfo().getVersion());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
```

要点:
- 替换 import 块,显式 import `MMCRReloadCommand` / `MMCRCapabilities` / `RegisterCommandsEvent`(消除 R9 的 FQN)
- 删 `import cn.howxu.mmcr.registry.MMCRRegistries`,换 `MMCRBlocks` / `MMCRItems` / `MMCRBlockEntities` / `MMCRRecipeTypes`
- `MMCRRegistries.X.register(modBus)` 6 行换成 5 行(`CREATIVE_TABS` 留在类内)
- 删 2 行 inline FQN lambda(method reference),改短名
- 加 `CREATIVE_TABS` 内联 DeferredRegister + DeferredHolder

> 你现在看到 `CREATIVE_TABS.register(modBus);` 在前、`CREATIVE_TABS.register(MODID, () -> ...)` 在后 —— 是因为 `register(IEventBus)` 是 DeferredRegister 的方法、第二个 `register` 是同名 DeferredHolder 工厂。先后顺序无影响(都是 lambda,延迟到事件触发)。

- [ ] **Step 2: 删除旧文件**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git rm src/main/java/cn/howxu/mmcr/registry/MMCRRegistries.java
```

- [ ] **Step 4: 编译 + 测试**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  ./gradlew compileJava compileTestJava compileGameTestJava test 2>&1 | tail -30
```

期望:`BUILD SUCCESSFUL`,无测试失败。

- [ ] **Step 5: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git add . && \
  git commit -m "refactor: switch MMCR.java to new registries; delete old MMCRRegistries.java; clean FQN in MMCR.java"
```

> 用 `git add .` 而非 `git add -u`,因为 Task 6 添加了 4 个新 Registry 类(java),`git add -u` 不会捕获。Task 6 暂未提交,这里一并加入。

---

## Task 9: 删除 3 个旧 Block 类

**Files:**
- Delete: `src/main/java/cn/howxu/mmcr/internal/block/ItemBusBlock.java`
- Delete: `src/main/java/cn/howxu/mmcr/internal/block/FluidHatchBlock.java`
- Delete: `src/main/java/cn/howxu/mmcr/internal/block/EnergyHatchBlock.java`

- [ ] **Step 1: 确认无其它文件引用它们**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  grep -rln "ItemBusBlock\|FluidHatchBlock\|EnergyHatchBlock" src/main src/test src/gametest 2>&1
```

期望:只命中 `internal/tile/{ItemBus,FluidHatch,EnergyHatch}BlockEntity.java` 和现有已被删除的同名 Block 文件本身。若有 `registry/` 或 `datagen/` 残留引用,先清掉。

- [ ] **Step 2: 删除**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git rm src/main/java/cn/howxu/mmcr/internal/block/ItemBusBlock.java \
         src/main/java/cn/howxu/mmcr/internal/block/FluidHatchBlock.java \
         src/main/java/cn/howxu/mmcr/internal/block/EnergyHatchBlock.java
```

- [ ] **Step 3: 编译验证**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  ./gradlew compileJava compileTestJava compileGameTestJava 2>&1 | tail -20
```

期望:`BUILD SUCCESSFUL`。

- [ ] **Step 4: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git commit -m "refactor(block): remove ItemBusBlock/FluidHatchBlock/EnergyHatchBlock, replaced by unified IOPortBlock"
```

---

## Task 10: 添加 `MMCRTranslations` 类 + 重构 `MMCRLanguageProvider`

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/datagen/MMCRTranslations.java`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/MMCRLanguageProvider.java`

- [ ] **Step 1: 创建 `MMCRTranslations.java`**

完整内容:

```java
package cn.howxu.mmcr.datagen;

import java.util.Map;

/** 集中存放所有语言的翻译键值,与 Provider 解耦。仅作数据存储,无业务逻辑。 */
public final class MMCRTranslations {

    public static final Map<String, Map<String, String>> ALL = Map.ofEntries(
            Map.entry("en_us", Map.ofEntries(
                    Map.entry("itemGroup.mmcr",                    "Modular Machinery: Refoxed"),
                    Map.entry("block.mmcr.controller",             "Machine Controller"),
                    Map.entry("block.mmcr.casing",                 "Machine Casing"),
                    Map.entry("block.mmcr.io_port_item_basic",     "Item IO Port"),
                    Map.entry("block.mmcr.io_port_fluid_basic",    "Fluid IO Port"),
                    Map.entry("block.mmcr.io_port_energy_basic",   "Energy IO Port"))),
            Map.entry("zh_cn", Map.ofEntries(
                    Map.entry("itemGroup.mmcr",                    "模块化机械:Refoxed"),
                    Map.entry("block.mmcr.controller",             "机器控制器"),
                    Map.entry("block.mmcr.casing",                 "机器外壳"),
                    Map.entry("block.mmcr.io_port_item_basic",     "物品 IO 端口"),
                    Map.entry("block.mmcr.io_port_fluid_basic",    "流体 IO 端口"),
                    Map.entry("block.mmcr.io_port_energy_basic",   "能量 IO 端口")))
    );

    private MMCRTranslations() {}
}
```

- [ ] **Step 2: 重写 `MMCRLanguageProvider.java`**

完整替换内容:

```java
package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.Map;

public final class MMCRLanguageProvider extends LanguageProvider {

    private final String locale;

    public MMCRLanguageProvider(PackOutput output, String locale) {
        super(output, MMCR.MODID, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        Map<String, String> map = MMCRTranslations.ALL.get(locale);
        if (map != null) map.forEach(this::add);
    }
}
```

- [ ] **Step 3: 跑一次 datagen 校验**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  ./gradlew runData 2>&1 | tail -30
```

或者更轻量:

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  ./gradlew compileJava 2>&1 | tail -10
```

再手动查生成的 en_us.json / zh_cn.json:

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  cat src/generated/resources/assets/mmcr/lang/en_us.json \
      src/generated/resources/assets/mmcr/lang/zh_cn.json 2>/dev/null || \
  echo "runData 未产出,先跑 ./gradlew runData"
```

期望:`en_us.json` 含 `block.mmcr.io_port_item_basic`、`block.mmcr.io_port_fluid_basic`、`block.mmcr.io_port_energy_basic` 共 6 条 key。

- [ ] **Step 4: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git add src/main/java/cn/howxu/mmcr/datagen/MMCRTranslations.java \
          src/main/java/cn/howxu/mmcr/datagen/MMCRLanguageProvider.java && \
  git commit -m "refactor(datagen): extract translation data into MMCRTranslations"
```

---

## Task 11: 重写 `MMCRModelProvider` 为循环

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/datagen/MMCRModelProvider.java`

- [ ] **Step 1: 完整重写文件**

```java
package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.MMCRBlocks;
import cn.howxu.mmcr.registry.MMCRItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.stream.Stream;

public final class MMCRModelProvider extends ModelProvider {

    public MMCRModelProvider(PackOutput output) {
        super(output, MMCR.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        MMCRBlocks.BLOCKS.forEach((name, blockHolder) -> {
            Block block = blockHolder.get();
            ResourceLocation texture = MMCR.id("block/" + name);
            TexturedModel.Provider provider = b -> TexturedModel.createAllSame(texture);

            if ("controller".equals(name)) {
                blockModels.createHorizontallyRotatedBlock(block, provider);
            } else {
                blockModels.createTrivialBlock(block, provider);
            }

            MMCRItems.ITEMS.get(name).ifPresent(itemHolder ->
                    itemModels.itemModelOutput.accept(itemHolder.get(),
                            ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(block))));
        });
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return MMCRBlocks.BLOCKS.values().stream()
                .map(h -> h.get().builtInRegistryHolder());
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return MMCRItems.ITEMS.values().stream()
                .map(h -> h.get().builtInRegistryHolder());
    }
}
```

- [ ] **Step 2: 跑 datagen 校验**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  ./gradlew runData 2>&1 | tail -30
```

检查生成的 model 文件:

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  find src/generated/resources/assets/mmcr/models -type f | sort
```

期望:5 个 block 的 model(包含 controller、casing、io_port_*),5 个 item 的 model,加上对应的 blockstate(若 datagen 产出)。

- [ ] **Step 3: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git add src/main/java/cn/howxu/mmcr/datagen/MMCRModelProvider.java \
          src/generated 2>/dev/null || true && \
  git commit -m "refactor(datagen): rewrite MMCRModelProvider to loop over MMCRBlocks/MMCRItems"
```

---

## Task 12: 从 mmce 拷贝 4 张贴图

**Files:**
- Create: `src/main/resources/assets/mmcr/textures/block/controller.png`
- Create: `src/main/resources/assets/mmcr/textures/block/io_port_item_basic.png`
- Create: `src/main/resources/assets/mmcr/textures/block/io_port_fluid_basic.png`
- Create: `src/main/resources/assets/mmcr/textures/block/io_port_energy_basic.png`

- [ ] **Step 1: 拷贝贴图**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  cp reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_controller.png \
     src/main/resources/assets/mmcr/textures/block/controller.png && \
  cp reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_inputbus_normal.png \
     src/main/resources/assets/mmcr/textures/block/io_port_item_basic.png && \
  cp reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_fluidinputhatch_normal.png \
     src/main/resources/assets/mmcr/textures/block/io_port_fluid_basic.png && \
  cp reference/mmce/src/main/resources/assets/modularmachinery/textures/blocks/overlay_energyinputhatch_normal.png \
     src/main/resources/assets/mmcr/textures/block/io_port_energy_basic.png && \
  ls -la src/main/resources/assets/mmcr/textures/block/
```

期望:出现 5 张 png(原有 casing.png 仍在)。

- [ ] **Step 2: 检查文件尺寸是否一致**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  for f in src/main/resources/assets/mmcr/textures/block/*.png; do \
    echo "$f: $(file -b "$f" | head -c 80)"; \
  done
```

期望:全部都是非空 PNG,长宽信息存在。**若不一致,在 commit 前用图像处理工具统一(非本任务范围,留 FIXME)。**

- [ ] **Step 3: 重新跑 datagen 让 model 引用新贴图**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  ./gradlew runData 2>&1 | tail -20
```

期望:无 missing texture warning。

- [ ] **Step 4: 提交**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git add src/main/resources/assets/mmcr/textures/block/ && \
  git commit -m "feat(asset): copy 4 distinct textures for controller and 3 IO ports from mmce"
```

---

## Task 13: 全量构建验证

- [ ] **Step 1: 运行构建**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  ./gradlew clean build 2>&1 | tail -50
```

期望:`BUILD SUCCESSFUL`,无 missing texture warning,无 deprecated API warning(若有可忽略)。

- [ ] **Step 2: 运行 gametest(若环境支持)**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  ./gradlew runGameTestServer 2>&1 | tail -50
```

或者:

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  ./gradlew execute gametest 2>&1 | tail -50
```

期望:7 个 gametest 全部通过(BlockArrayMatchGameTest, ControllerTickGameTest, E2ERecipeRunGameTest, EnergyHatchCapabilityGameTest, ItemBusCapabilityGameTest, FluidHatchCapabilityGameTest, 以及其他现有 gametest)。

- [ ] **Step 3: 量化净行数变化(可选)**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  echo "Old MMCRRegistries.java: $(wc -l < .git/refs/heads/dev/neo/26.1.2 2>/dev/null; git show HEAD~5:src/main/java/cn/howxu/mmcr/registry/MMCRRegistries.java 2>/dev/null | wc -l)" && \
  echo "New files:" && \
  wc -l src/main/java/cn/howxu/mmcr/registry/MMCRBlocks.java \
        src/main/java/cn/howxu/mmcr/registry/MMCRItems.java \
        src/main/java/cn/howxu/mmcr/registry/MMCRBlockEntities.java \
        src/main/java/cn/howxu/mmcr/registry/MMCRRecipeTypes.java \
        src/main/java/cn/howxu/mmcr/registry/MMCRPortKinds.java \
        src/main/java/cn/howxu/mmcr/internal/port/IOPortKind.java \
        src/main/java/cn/howxu/mmcr/internal/block/IOPortBlock.java \
        src/main/java/cn/howxu/mmcr/internal/tile/IOPortBlockEntity.java
```

期望:新文件总行数 ≪ 旧 `MMCRRegistries.java` 的 107 行。

- [ ] **Step 4: 验证 spec DoD(V4 验收)**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  echo "=== V4: java.util.function.Supplier 全名验证 ===" && \
  (grep -R "java\.util\.function\.Supplier" src/main/java/ && echo "FAIL: 仍有全名引用") || echo "PASS: 已全部 import 化" && \
  echo "=== V6: 旧 Block 类已删除 ===" && \
  (ls src/main/java/cn/howxu/mmcr/internal/block/ItemBusBlock.java 2>/dev/null && echo "FAIL") || echo "PASS: ItemBusBlock 已删除" && \
  (ls src/main/java/cn/howxu/mmcr/internal/block/FluidHatchBlock.java 2>/dev/null && echo "FAIL") || echo "PASS: FluidHatchBlock 已删除" && \
  (ls src/main/java/cn/howxu/mmcr/internal/block/EnergyHatchBlock.java 2>/dev/null && echo "FAIL") || echo "PASS: EnergyHatchBlock 已删除"
```

期望:全部 `PASS`。

- [ ] **Step 5: 最终提交(若有遗漏)**

```bash
cd /home/howxu/Projects/ModularMachinery-Community-Refoxed && \
  git status && \
  echo "若还有 uncommitted 改动,根据语义:'chore: post-refactor verification' 一并提交"
```

---

## 验收对照(Spec §5)

| DoD | 验证方式 | 通过条件 |
|---|---|---|
| V1 clean build | 任务 13 Step 1 | `BUILD SUCCESSFUL`,无 missing texture |
| V2 lang 产出 7 条 key | 任务 10 Step 3 | en_us.json / zh_cn.json 各含 6 个 block key + itemGroup |
| V3 5 块独立贴图 | 任务 12 | 4 张新贴图就位,Task 11 重新生成 model 引用各 block 自己的贴图 |
| V4 清理 Supplier 全名 | 任务 13 Step 4 | `grep` 空返回 |
| V5 MMCR.java FQN 清理 | 任务 8 Step 1 | `cn.howxu.mmcr.internal.event.MMCRCapabilities::register` 等消失 |
| V6 删 3 个旧 Block 类 | 任务 9 | 文件系统确认 |
| V7 净行数减少 | 任务 13 Step 3 | 新文件总行数 < 107 |

---

**Plan complete and saved to `docs/superpowers/plans/2026-08-03-mmcr-refactor.md`. 选执行方式:**
