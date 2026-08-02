# KubeJS 集成（可选绑定层）

> MMCR → KubeJS 桥接层设计。
> **核心原则：MMCR 是 Java API 优先；KubeJS 只是一个可选的脚本入口。没装 KubeJS 时 MMCR 必须能正常工作。**

## 0. 设计红线

1. **MMCR 的 `src/main/java/cn/howxu/mmcr/api/**` 下任何代码都不得 import 任何 `dev.latvian.mods.*` / `com.cleanroommc.*` / 第三方 KubeJS API**。
2. **MMCR 的 mod 主类、Recipe 系统、Block 系统都不得在静态初始化阶段引用 KubeJS**——否则无 KubeJS 时类加载失败。
3. **KubeJS 桥接代码单独放 `cn.howxu.mmcr.kubejs` 子包**，由 KubeJS 自己的 ServiceLoader 反射加载；不在我们自己的 Mod 构造器里硬引。
4. **KubeJS 桥接层不出现在「运行时找不到 KubeJS」就崩的位置**——所有插件方法都是 default method，子类按需 override。

## 1. KubeJS 是怎么发现插件的（取证）

**证据**：`reference/kubejs/src/main/java/dev/latvian/mods/kubejs/plugin/KubeJSPlugins.java`

```java
// 第 51 行
var pluginData = contents.readFile("kubejs.plugins.txt");
if (pluginData != null) {
    loadFromFile(new String(pluginData, StandardCharsets.UTF_8).lines(), modId, loadClientPlugins);
}

// 第 77-95 行（解析单行）：
String[] line = s.split(" ");
for (int i = 1; i < line.length; i++) {
    if (line[i].equalsIgnoreCase("client")) { ... }      // 仅 client 侧
    else if (!ModList.get().isLoaded(line[i])) { ... }  // 仅当指定 mod 已加载
}

// 第 98 行
Class.forName(line[0])     // FQN 反射加载插件类
```

**关键事实**：
- KubeJS 扫描**所有 mod JAR**（包括不依赖 KubeJS 的 mod）的 `kubejs.plugins.txt`。
- 一行 = 一个插件 FQN，可选第二参数 `client` / `modid1 modid2` 控制加载条件。
- **加载条件是「mod 已加载」**，不是「KubeJS 已加载」——`KubeJSPlugins.load()` 本身由 KubeJS 调用，所以 KubeJS 永远在场。
- 反射加载：如果 `Class.forName` 抛异常（多半是缺 KubeJS 自身的依赖），会被吞掉，只 log error。

**结论**：MMCR 的 `kubejs.plugins.txt` 写「FQN」即可。KubeJS 没装 → 没人调它 → 类不会被加载 → 永远不会崩。

## 2. 我们的插件长什么样

### 2.1 文件清单

```
src/main/resources/kubejs.plugins.txt                        # 插件声明（文本，非 JSON）
src/main/java/cn/howxu/mmcr/kubejs/MMCRKubeJSPlugin.java     # 插件入口
src/main/java/cn/howxu/mmcr/kubejs/MachineBuilderJS.java      # builder 类型
src/main/java/cn/howxu/mmcr/kubejs/MachineRecipeBuilderJS.java
src/main/java/cn/howxu/mmcr/kubejs/MachineRecipeSchema.java   # 配方 schema（程序化注册）
src/main/java/cn/howxu/mmcr/kubejs/MachineRecipeFactory.java  # 配方 factory
src/main/java/cn/howxu/mmcr/kubejs/MMCREvents.java           # 事件群
src/main/java/cn/howxu/mmcr/kubejs/wrappers/BlockArrayWrapper.java
src/main/java/cn/howxu/mmcr/kubejs/wrappers/ComponentTypeWrapper.java
```

### 2.2 kubejs.plugins.txt（唯一入口声明）

```text
cn.howxu.mmcr.kubejs.MMCRKubeJSPlugin
```

**不加** `client` / `modid` 后缀——KubeJS 在场即加载。

### 2.3 MMCRKubeJSPlugin 骨架

```java
package cn.howxu.mmcr.kubejs;

import cn.howxu.mmcr.api.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.kubejs.wrappers.BlockArrayWrapper;
import cn.howxu.mmcr.kubejs.wrappers.ComponentTypeWrapper;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.registry.BuilderTypeRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import net.minecraft.resources.Identifier;

public class MMCRKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerBuilderTypes(BuilderTypeRegistry registry) {
        registry.register(MachineBuilderJS.TYPE);
        registry.register(MachineRecipeBuilderJS.TYPE);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        // 全局对象：MMCR.machines / MMCR.recipes
        bindings.add("MMCR", MMCR.class);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(MMCREvents.GROUP);
    }

    @Override
    public void registerTypeWrappers(TypeWrapperRegistry registry) {
        registry.register(BlockArrayWrapper.TYPE_INFO, BlockArrayWrapper::wrap);
        registry.register(ComponentTypeWrapper.TYPE_INFO, ComponentTypeWrapper::wrap);
    }

    @Override
    public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
        // 不在这里注册；改用 JSON 文件（见 §3）
        // 这里只做动态注册（如果未来需要）
    }
}
```

**注意**：上面代码**编译时**会依赖 KubeJS API。这是允许的——因为 `kubejs-neoforge` 已经在 `build.gradle` 里（`runtimeOnly`）。但在 IDE 看不到 KubeJS 源码时会飘红，这是预期行为，不是 bug。

## 3. 配方 Schema 程序化注册（无 JSON）

> **不做 JSON schema 文件**。KubeJS 提供 Java API `RecipeSchema` + `RecipeKey` 直接构造——结果与 JSON 等价，但无任何资源文件。

**证据**：
- `reference/kubejs/src/main/java/dev/latvian/mods/kubejs/recipe/schema/RecipeSchema.java`
- `reference/kubejs/src/main/java/dev/latvian/mods/kubejs/recipe/RecipeKey.java`
- `reference/kubejs/src/main/java/dev/latvian/mods/kubejs/recipe/component/*.java`（所有 `RecipeComponent` 都有 `static final` 实例）

放在 `cn.howxu.mmcr.kubejs.MachineRecipeSchema`：

```java
public final class MachineRecipeSchema {
    public static final RecipeKey<ResourceLocation> MACHINE =
            new RecipeKey<>(StringComponent.ID, "machine", ComponentRole.OTHER)
                    .noFunctions();

    public static final RecipeKey<Integer> TICK_TIME =
            new RecipeKey<>(NumberComponent.NON_NEGATIVE_INT, "tick_time", ComponentRole.OTHER);

    public static final RecipeKey<List<SizedIngredient>> ITEM_INPUTS =
            new RecipeKey<>(ListRecipeComponent.create(SizedIngredientComponent.SIZED_INGREDIENT, true, false), "item_inputs", ComponentRole.INPUT)
                    .optional(List.of())
                    .exclude();

    public static final RecipeKey<List<SizedFluidIngredient>> FLUID_INPUTS =
            new RecipeKey<>(ListRecipeComponent.create(SizedFluidIngredientComponent.SIZED_FLUID_INGREDIENT, true, false), "fluid_inputs", ComponentRole.INPUT)
                    .optional(List.of())
                    .exclude();

    public static final RecipeKey<Integer> ENERGY_PER_TICK =
            new RecipeKey<>(NumberComponent.NON_NEGATIVE_INT, "energy_per_tick", ComponentRole.OTHER)
                    .optional(0);

    public static final RecipeKey<List<ItemStack>> ITEM_OUTPUTS =
            new RecipeKey<>(ListRecipeComponent.create(ItemStackComponent.ITEM_STACK, true, false), "item_outputs", ComponentRole.OUTPUT)
                    .optional(List.of())
                    .exclude();

    public static final RecipeKey<List<FluidStack>> FLUID_OUTPUTS =
            new RecipeKey<>(ListRecipeComponent.create(FluidStackComponent.FLUID_STACK, true, false), "fluid_outputs", ComponentRole.OUTPUT)
                    .optional(List.of())
                    .exclude();

    public static final RecipeSchema SCHEMA = new RecipeSchema(
            MACHINE, TICK_TIME,
            ITEM_INPUTS, FLUID_INPUTS,
            ENERGY_PER_TICK,
            ITEM_OUTPUTS, FLUID_OUTPUTS
    );
}
```

在 `MMCRKubeJSPlugin.registerRecipeSchemas` 注册：

```java
@Override
public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
    registry.register(MMCR.id("machine_recipe"), MachineRecipeSchema.SCHEMA);
}
```

`MMCR.id(name)` 工具方法：

```java
public final class MMCR {
    public static final String MODID = "mmcr";
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MODID, path); }
}
```

**对应脚本调用**（与原 JSON 版完全等价）：
```javascript
ServerEvents.recipes(event => {
    event.recipes.mmcr.machine_recipe('mmcr:iron_compressor', 40, {
        item_inputs: ['2x minecraft:iron_ingot'],
        item_outputs: [Item.of('minecraft:iron_plate')],
        energy_per_tick: 80
    });
});
```

**关键点**：
- `RecipeSchema` 的 7 个 key 用 Java 字段直接声明——**编译期校验**，不会因为 JSON 拼错而运行时崩。
- `SizedIngredientComponent.SIZED_INGREDIENT` / `FluidStackComponent.FLUID_STACK` 等组件直接复用 KubeJS 内置实例——KubeJS 知道怎么把 `'2x minecraft:iron_ingot'` 解析成 `SizedIngredient`，我们不重写 Rhino 类型转换。
- 不放任何资源文件——**整个 kubejs 集成包内零 JSON**。

## 4. KubeJS 端 Builder 设计

### 4.1 MachineBuilderJS（核心：多方块结构定义）

> **多方块结构（MMCE §5 的 `BlockArray`）就是用 KubeJS 注册的，不走 JSON。** 这是 1.12.2 时代最大的体验债——玩家必须手写 JSON 才能添加一个多方块；现在 KubeJS 脚本可以一行注册。

```javascript
// 调用形态（与 MMCE §23.2 MachineBuilder 对位）：
StartupEvents.registry('mmcr:machine', event => {
    event.create('mmcr:blast_furnace')
        .localizedName('Blast Furnace')
        .pattern(`
            CCC
            CFB
            CCC
        `, { C: 'mmcr:casing', F: 'minecraft:furnace' });
});
```

**关键差异 vs MMCE 1.12.2**：

| MMCE 1.12.2 写法 | MMCR 26.1.2 KubeJS 写法 |
|---|---|
| 写 JSON 文件到 `assets/mmcr/machinery/blast_furnace.json`，GSON 解析 `BlockArray` | KubeJS 脚本一行 `.pattern(...)` 注册 |
| 写法：`{ "pattern": ["CCC","CFB","CCC"], "key": { "C": {...}, "F": {...} } }` | `.pattern(string, map)` —— 字符串按行 / 拆字解析 |
| `MachineLoader.discoverDirectory(...)` 扫盘 | `MMCR.machines` 同步注册到 `MachineRegistry` |
| `JsonDeserializer` 两阶段加载 | 一次性 register，no preload |

**实现路径**：
- `MachineBuilderJS` extends KubeJS `BuilderBase<Machine>`。
- `pattern(String s, Map<String, Object> keys)` 拆字符串：
  - 按行拆，得到 2D 字符网格。
  - 按字符查 keys map，转成 `BlockPredicate`（`Block` / `BlockState` / `TagKey` / "air"）。
  - 计算 bounding box，把每格（按 controller 朝向归一化）放进 `BlockArray`。
- `register()` → `MachineRegistry.register(builder.build())`。

**BlockPredicate 的内部表示**（参考 MMCE §5.1 的 `BlockInformation`）：
```java
public sealed interface BlockPredicate {
    record Air() implements BlockPredicate {}
    record Any() implements BlockPredicate {}
    record OfBlock(Block block) implements BlockPredicate {}
    record OfBlockState(BlockState state) implements BlockPredicate {}
    record OfTag(TagKey<Block> tag) implements BlockPredicate {}
    record AnyOf(List<BlockPredicate> any) implements BlockPredicate {}    // OR
}
```
- 字符串「`_`」/ 「`.`」/ 空格 → `Air`（空气，结构匹配时不参与判定）。
- 字符串「`?`」 /「`*`」 → `Any`（任意方块）。
- keys map 里 key 是「字符」→ BlockPredicate。
- 解析失败抛 `IllegalArgumentException`，KubeJS 会自动把行号 / 文件名带上。

### 4.2 MachineRecipeBuilderJS

```javascript
ServerEvents.recipes(event => {
    event.recipes.mmcr.machine_recipe('mmcr:iron_compressor', 40)
        .itemInput('2x minecraft:iron_ingot')
        .itemOutput('minecraft:iron_plate')
        .energyPerTick(80);
});
```

实现路径：
- 直接对应 `MachineRecipe` record 的构造参数。
- 调用 `RecipeRegistry.register(MachineRecipe)`。

### 4.2 MachineRecipeBuilderJS

```javascript
ServerEvents.recipes(event => {
    event.recipes.mmcr.machine_recipe('mmcr:iron_compressor', 40)
        .itemInput('2x minecraft:iron_ingot')
        .itemOutput('minecraft:iron_plate')
        .energyPerTick(80);
});
```

实现路径：
- 直接对应 `MachineRecipe` record 的构造参数。
- 调用 `RecipeRegistry.register(MachineRecipe)`。

## 5. 事件暴露

### 5.1 事件群定义

```java
package cn.howxu.mmcr.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandle;
import net.minecraft.world.level.Level;

public interface MMCREvents {
    EventGroup GROUP = EventGroup.of("MMCREvents");

    // KubeJS 端访问：
    // MMCREvents.machineTick(handler) / .recipeComplete(handler)
    EventHandle<MachineTickJS> MACHINE_TICK = GROUP.server("machineTick", () -> MachineTickJS.class);
    EventHandle<RecipeCompleteJS> RECIPE_COMPLETE = GROUP.server("recipeComplete", () -> RecipeCompleteJS.class);
}
```

### 5.2 事件触发（MMCR → KubeJS 方向）

MMCR 自身的事件总线在每个 Recipe 完成 / 每个 Tick 触发事件；通过 `MMCRKubeJSPlugin.afterInit()` 把 MMCR 的事件 hook 接到 KubeJS 的事件总线。

```java
public class MMCRKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void afterInit() {
        // MMCR 主事件 hook 接到 KubeJS 事件总线
        MMCR.events().onMachineTick(event -> {
            // 把 MMCR 事件包成 KubeJS 端可见的 MachineTickJS，调用所有已注册 handler
            MMCREvents.MACHINE_TICK.post(event);   // post 是 KubeJS 端 API
        });
        MMCR.events().onRecipeComplete(event -> {
            MMCREvents.RECIPE_COMPLETE.post(event);
        });
    }
}
```

**注意**：`afterInit()` 是 `FMLLoadCompleteEvent` 之后调，所以此时 MMCR 主事件总线已经就位。

## 6. 检测 KubeJS 是否在场（运行时）

如果 MMCR 想在「无 KubeJS」与「有 KubeJS」分支中做不同行为（比如日志是否打印「KubeJS 已挂载」），用：

```java
import net.neoforged.fml.ModList;

boolean hasKubeJS = ModList.get().isLoaded("kubejs");
```

**不要**在静态初始化器里做这个判断——ModList 在 mod 构造器阶段可能尚未完整。判断放在 `commonSetup` / `FMLLoadCompleteEvent` 之后。

## 7. 没装 KubeJS 时，MMCR 的行为

| 场景 | 行为 |
|---|---|
| 模组加载 | 正常，KubeJS 缺位不影响 MMCR 初始化 |
| 玩家放机器 | 用 Java API 预注册的几台示例机器（如 `mmcr:basic_furnace`） |
| 玩家写配方 | 仅通过 KubeJS 脚本；MMCR **不支持** datapack JSON 配方（与 D2 一致） |
| JEI 显示 | JEI recipe category 仍能枚举 `RecipeManager` 里的所有 `MachineRecipe` |
| 性能 | 无差异（KubeJS 桥接层一行都不执行） |

## 8. 资源 / Classpath 影响

- `kubejs-neoforge` 标注 `runtimeOnly`（**当前 `build.gradle` 已经是这样**），意味着：
  - **编译时** 不需要 KubeJS 在 classpath（但 IDE 会飘红，需要另开 `compileOnly` 或在 IDE 里手动添加 sources——这是开发者体验问题，不是构建问题）。
  - **运行时** 必须 KubeJS 在场才用到，但缺 KubeJS 不会崩。
- 如果编译时想飘红问题也消失：把 `kubejs-neoforge` 改为 `compileOnly`，运行时 `runtimeOnly` 保留。这是建议改动。

**实测当前 `build.gradle`**：
```gradle
// KubeJS
api("dev.latvian.mods:kubejs-neoforge:${kubejs_version}")
runtimeOnly("dev.latvian.mods:kubejs-neoforge:${kubejs_version}")
```

`api` + `runtimeOnly` 同时存在很奇怪（重复声明）。建议改为：
```gradle
compileOnly("dev.latvian.mods:kubejs-neoforge:${kubejs_version}")
runtimeOnly("dev.latvian.mods:kubejs-neoforge:${kubejs_version}")
```

让 `MMCRKubeJSPlugin` 只在运行时被 KubeJS 加载，编译时也能找到类型。

## 9. 与 MMCE 1.12.2 CraftTweaker 的对比

| MMCE 1.12.2 | MMCR 26.1.2 |
|---|---|
| `@ZenClass("mods.modularmachinery.MachineBuilder")` | KubeJS builder（不用注解，KubeJS 反射字段名） |
| `@ZenMethod` | KubeJS `BuilderBase` 模式 + `register()` |
| `@ZenRegister`（CraftTweaker 加载注解） | `kubejs.plugins.txt`（**纯文本，无注解**） |
| CraftTweaker 必须存在 | KubeJS 可选 |
| ZenScript `mods.modularmachinery.MachineBuilder.createBuilder(...)` | KubeJS `StartupEvents.registry('mmcr:machine', e => e.create(...))` |
| 自定义 `@ZenMethod` 注解 | KubeJS `BindingRegistry` + `TypeWrapperRegistry` |

**迁移收益**：KubeJS 的 builder + schema JSON 模式让 addons 自己写扩展点比 CraftTweaker 更省事——不用学注解和 ZenScript。

## 10. 测试矩阵

| 测试场景 | 期望 | 验证方式 |
|---|---|---|
| 装 MMCR + KubeJS | 脚本可写机器 + 配方 | 在 `world/server_scripts` 写 `StartupEvents.registry('mmcr:machine', e => e.create('test:demo'))` |
| 装 MMCR 不装 KubeJS | 启动不报错 | 启动日志无 `Failed to load plugin cn.howxu.mmcr.kubejs.MMCRKubeJSPlugin` |
| 装 MMCR 不装 KubeJS | 启动正常；只能通过 Java API 注册机器 / 配方 |
| KubeJS 在场但 MMCR 异常 | KubeJS 不应连带崩溃 | 检查 KubeJS 日志，确认 MMCR 插件错误被单独捕获 |

## 11. 不做的事

- ❌ KubeJS **不**做 MMCR 的注册入口必经路径——必须有纯 Java API 备选。
- ❌ KubeJS **不**做机器 / 配方的运行时校验——MMCR 主代码必须能独立验证 KubeJS 没装的情况。
- ❌ KubeJS **不**做 mod 专属标签（如事件订阅）的「硬绑定」——所有 KubeJS 钩子走 MMCR 自身事件总线。
- ❌ KubeJS **不**做机器可视化 GUI（结构预览）——那是 Phase 5 / `docs/scope.md §7` 的事。