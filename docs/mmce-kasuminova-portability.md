# `github.kasuminova.mmce` 可移植性分析

> 范围：本文只分析 `reference/mmce/src/main/java/github/kasuminova/mmce/**`，即 MMCE 中 KasumiNova 新增的扩展层。原 `hellfirepvp.modularmachinery.*` 主干、`ink.ikx.mmce.*` 自动组装、`youyihj.mmce.*` 投影器、`kport.modularmagic.*` 魔法模块不在本文主范围内。
>
> 目标：列出哪些内容适合移植到当前 MMCR NeoForge 26.1.2，哪些需要重写，哪些应删除或延后。

## 1. 结论摘要

`github.kasuminova.mmce.*` 不是一个单独的小兼容包，而是 MMCE 后期核心增强层，覆盖动态 GUI、结构预览、ME/AE2 总线、GTCEu 代理、升级系统、机器事件、配方搜索优化、网络包和 mixin 补丁。它包含大量 1.12.2 Forge、旧 AE2、旧 JEI、旧渲染管线、反射和 mixin 依赖，不能整体搬运。

当前项目已经有 NeoForge 版本的机器定义、结构匹配、controller、item/fluid/energy port、recipe context、modifier 雏形、KubeJS、Jade、基础菜单等模块。因此移植策略应为：

- **优先移植语义，不搬 API**：保留 MMCE 的 feature 设计和数据流，落地到 `cn.howxu.mmcr.*` 的 NeoForge API。
- **先移植服务端核心**：事件、recipe search/context pool、upgrade、special block proxy 这类不强依赖旧客户端的内容优先级更高。
- **客户端体验分阶段做**：动态 GUI、结构预览、模型渲染能提升体验，但依赖新版渲染/JEI/菜单体系，必须重写。
- **第三方联动后置**：AE2/ME、GTCEu、Mekanism gas 类内容只在核心闭环稳定后作为独立 compat 阶段处理。
- **1.12.2 mixin 默认不移植**：只有新版 API 无法覆盖时，再为具体兼容点写新的 NeoForge/Mixin 补丁。

## 2. 当前 MMCR 基线对照

当前 `src/main/java/cn/howxu/mmcr` 已具备的相关基础：

| 能力 | 当前实现位置 | 对 `github.kasuminova.mmce` 移植的意义 |
|---|---|---|
| 机器定义 | `api.machine.Machine`, `DynamicMachine`, `BlockArray`, `StructureMatcher` | 可承接动态结构、modifier replacement、preview 数据源 |
| 控制器运行时 | `internal.tile.MachineControllerBlockEntity` | 可承接 recipe event、recipe search、running status、parallel/upgrade 后续接入 |
| Item/Fluid/Energy 端口 | `internal.tile.*BusBlockEntity`, `*HatchBlockEntity` | 可承接 component 路由与 selector tag，不需要照搬 MMCE TileEntity |
| Recipe 层 | `api.recipe.*`, `RecipeCraftingContext`, `RecipeSearchTask`, `RecipeCraftingContextPool` | 已经有 MMCE 同名语义，应继续对齐而非新建第二套 |
| Modifier 层 | `api.recipe.modifier.*` | 可承接 upgrade 与结构替换类逻辑 |
| 网络 | `internal.network.*Payload` | 旧 `IMessage` 网络包必须重写为 NeoForge payload |
| 菜单/屏幕 | `internal.menu.*` | 动态 GUI 可重映射到新版 `AbstractContainerMenu`/Screen，但不应直搬 |
| Compat | `compat.kubejs`, `compat.jade` | MMCE 的 CraftTweaker/TOP 语义应映射到 KubeJS/Jade |

## 3. 可移植性分级

### A 类：建议优先移植或继续对齐

这类内容主要是服务端逻辑、数据结构或公共语义，较少绑定 1.12.2 客户端 API。

| MMCE 模块 | 代表类/包 | 移植方式 | 建议阶段 | 说明 |
|---|---|---|---|---|
| 配方搜索任务 | `common.concurrent.RecipeSearchTask`, `FactoryRecipeSearchTask` | **重映射** | 短期 | 当前已有 `api.recipe.RecipeSearchTask`，应比对 MMCE 的搜索失败原因、并行搜索、缓存边界，补齐语义而不是复制类名。 |
| 上下文池 | `common.concurrent.RecipeCraftingContextPool` | **直译简化** | 短期/中期 | 当前已有同名类；可移植对象复用策略，但需避免跨 tick 保存过期 BE/capability。 |
| 同步/计时工具 | `common.concurrent.Sync`, `common.util.TimeRecorder` | **按需重写** | 中期 | `Sync` 的意图可保留；`TimeRecorder` 可用于后续性能报告，不应先引入复杂 UI/网络。 |
| 机器/配方事件 | `common.event.recipe.*`, `common.event.client.*`, `Phase` | **重映射** | 短期 | RecipeStart/Tick/ResultChance 等事件适合映射到 NeoForge EventBus 或 MMCR 私有事件总线。客户端事件可延后。 |
| Helper/Checker | `common.helper.AdvancedBlockChecker`, `AdvancedItemChecker`, `IBlockStatePredicate`, `IDynamicPatternInfo` | **重映射** | 短期 | 与结构匹配、KubeJS builder、modifier replacement 关系密切，适合沉淀为 API 接口。 |
| Pattern 特殊代理 | `common.machine.pattern.SpecialItemBlockProxy`, `SpecialItemBlockProxyRegistry` | **重映射** | 中期 | 适合支持“物品代表方块”“虚拟结构匹配”“第三方 block proxy”，但要使用新版 registry/tag。 |
| 常用工具 | `common.util.HashedItemStack`, `PatternItemFilter` | **直译/重写** | 短期 | `HashedItemStack` 需要适配 `DataComponentPatch`；`PatternItemFilter` 可用于 blueprint/preview/auto assembly。 |
| 升级数据模型 | `common.upgrade.*`, `common.upgrade.registry.*` | **重映射** | 中期 | 升级系统是 MMCE 核心增强之一，适合移植语义；但方块、GUI、脚本 API 需分阶段。 |

### B 类：可移植，但必须重写适配层

这类内容有明确功能价值，但直接绑定旧 Minecraft/Forge/AE2/JEI/渲染 API。

| MMCE 模块 | 代表类/包 | 移植方式 | 建议阶段 | 说明 |
|---|---|---|---|---|
| 动态 GUI 基础 | `client.gui.GuiContainerDynamic`, `GuiScreenDynamic`, `client.gui.widget.base.*` | **重写** | 中期 | 旧 `GuiContainer`、LWJGL Mouse、Forge 1.12 tooltip API 不可用。可以保留 Widget tree、layout、event bubbling 思路。 |
| 通用 Widget | `client.gui.widget.*`, `client.gui.widget.container.*` | **重写** | 中期 | 按新版 `AbstractWidget`、PoseStack/GuiGraphics 实现；不要复制旧渲染坐标和 GL 状态管理。 |
| 虚拟槽/JEI 槽 | `client.gui.widget.slot.*` | **重写** | JEI 阶段 | 新 JEI 29 slot API 差异大，只保留 item/fluid/gas virtual slot 的概念。 |
| 结构预览 GUI | `client.gui.widget.impl.preview.*`, `client.preivew.PreviewPanels` | **重写** | 中后期 | 价值高，但依赖世界渲染、假世界、层切换、ingredient list。建议先做 2D/文本预览，再做 3D。 |
| 客户端模型/渲染 | `client.model.*`, `client.renderer.*`, `client.resource.GeoModelExternalLoader` | **重写/部分删除** | 后期 | 旧 GeckoLib/Lumenized/Bloom 渲染不应直搬。若当前项目坚持 vanilla 模型 JSON，则只保留 controller model selection 语义。 |
| BlockModelHider | `client.world.BlockModelHider` | **重写** | 结构预览阶段 | 用于结构预览/投影时隐藏重叠模型；新版需要走客户端 render hooks。 |
| 网络包 | `common.network.Pkt*` | **重写** | 按功能随迁 | 每个旧包都要映射到 NeoForge custom payload，不能保留 `SimpleNetworkWrapper/IMessage`。 |
| TOP/Jade 信息 | `common.integration.theoneprobe.MachineryHatchInfoProvider` | **重映射** | 短期/中期 | 当前已有 Jade compat，可把 MMCE hatch/controller 展示内容迁移为 Jade provider。 |
| GTCEu 代理 | `common.integration.gregtech.*` | **重写 compat** | 后期 | 概念可移植：GT energy/fluid/item hatch 作为 MMCR component proxy。实现必须依赖新版 GTCEu API。 |
| AE2/ME 集成入口 | `common.integration.ModIntegrationAE2` | **重写 compat** | 后期 | 只保留“注册 AE2 相关升级/组件/菜单”的阶段入口。旧 AE2 API 不能直用。 |

### C 类：只保留设计参考，暂不移植

这类内容价值存在，但依赖功能链太长，或当前项目已有更合适替代。

| MMCE 模块 | 代表类/包 | 处理方式 | 原因 |
|---|---|---|---|
| ME Item/Fluid/Gas Bus | `common.tile.ME*Bus`, `common.container.ContainerME*`, `client.gui.GuiME*` | 延后 | 依赖新版 AE2、菜单、网络、pattern provider、fluid/gas 生态；应作为独立 AE2 compat 里程碑。 |
| ME Pattern Provider | `MEPatternProvider`, `GuiMEPatternProvider`, `PktMEPatternProvider*` | 延后 | 功能复杂，且新版 AE2 pattern/container API 完全不同。 |
| Gas 相关虚拟槽/总线 | `MEGas*`, `SlotGasVirtual*` | 延后/视 Mekanism 而定 | 当前核心只有 item/fluid/energy；gas 应归入 Mekanism 阶段。 |
| 性能报告 UI/命令 | `PktPerformanceReport`, `TimeRecorder` 联动 | 延后 | 先保留计时工具，不做完整报告链路。 |
| 旧 AEBase GUI | `AEBaseGuiContainerDynamic` | 删除/参考 | AE2 客户端类版本差异过大。 |
| 旧 mixin | `mixin.ae2.*`, `mixin.jei.*`, `mixin.minecraft.*` | 默认删除 | 1.12.2 目标、方法名、渲染管线、JEI/AE2 内部结构都不适用。 |

## 4. 分包详细分析

### 4.1 `common.concurrent`

**功能价值**：MMCE 用这一层解决 recipe 搜索、工厂配方搜索、上下文复用和任务执行。当前 MMCR 已经有 `RecipeSearchTask` 和 `RecipeCraftingContextPool`，说明这一块已经进入移植轨道。

**建议移植内容**：

- 搜索结果应该包含失败原因，而不是只有成功/失败布尔值。
- 搜索过程应只做模拟，不提交 IO。
- context pool 可以复用临时列表、需求匹配状态、组件过滤结果。
- 工厂/并行相关搜索先不引入线程，只保留同步可测试版本。

**不要直搬**：

- 不要在异步线程读写 Level、BlockEntity、Capability。
- 不要照搬 1.12.2 的 `TaskExecutor` 生命周期到 NeoForge server tick。

### 4.2 `common.event`

**功能价值**：MMCE 的事件层让脚本/扩展能介入 recipe start、tick、result chance、controller GUI/model render。当前 MMCR 后续如果要支持 KubeJS 事件，应该优先有一套清晰事件模型。

**建议移植内容**：

- `RecipeEvent` 基类：携带 machine、controller pos、recipe、context、phase。
- `RecipeTickEvent`：允许观察进度，不建议首期允许取消或改 IO。
- `FactoryRecipeStartEvent` / `FactoryRecipeEvent`：等 factory controller 实现后再迁移。
- `ResultChanceCreateEvent`：等 output chance/modifier 完整后再迁移。
- `ControllerGUIRenderEvent` / `ControllerModelGetEvent` / `ControllerModelAnimationEvent`：客户端渲染阶段再做。

**落地建议**：先做私有 Java event API，再桥接 KubeJS；不要让 KubeJS 类型污染核心 API。

### 4.3 `common.helper`

**功能价值**：这是动态结构、复杂匹配和 controller 抽象的基础。当前 MMCR 已有 `BlockArray`、`StructureMatcher`，可以直接吸收这里的语义。

**建议移植内容**：

- `IBlockStatePredicate`：映射为新版 `BlockState`/`LevelReader`/`BlockPos` predicate。
- `AdvancedBlockChecker`：支持 tag、方块属性、方向、block entity 条件。
- `AdvancedItemChecker`：适配新版 `ItemStack` DataComponent，替代 1.12 NBT 判断。
- `IDynamicPatternInfo`：用于未来动态结构、upgrade replacement、preview。
- `MachineController` / `IMachineController`：不要新增平行控制器抽象；应合并到当前 `MachineControllerBlockEntity` 对外接口。

### 4.4 `common.machine.pattern`

**功能价值**：SpecialItemBlockProxy 解决“结构里某些方块/物品不是普通 block state”的表达问题，例如第三方机器方块、虚拟块、脚本定义的替代块。

**建议移植内容**：

- 建立 `PatternProxyRegistry`，按 `ResourceLocation` 注册 proxy。
- proxy 输入应使用新版 `ItemStack`、`BlockState`、`HolderLookup.Provider`。
- 只让结构匹配和导出使用 proxy，不要让 runtime recipe IO 直接依赖 proxy。

**优先级**：中期。当前已有 pattern export 和 multiblock detector 时，这一块能增强脚本表达力。

### 4.5 `common.upgrade`

**功能价值**：升级系统是 MMCE 相比原 Modular Machinery 的重要扩展，能把结构替换、数值修饰、机器能力提升统一到升级物品/方块上。

**建议拆分迁移**：

| 子阶段 | 内容 | 目标形态 |
|---|---|---|
| 1 | 数据模型 | `UpgradeType`, `MachineUpgrade`, `UpgradeInfo` 映射到 `api.upgrade` |
| 2 | 注册 | 用 NeoForge DeferredRegister/普通 registry 管理 upgrade 定义 |
| 3 | 应用点 | controller 成型后扫描 upgrade bus 或 pattern replacement |
| 4 | Modifier 接入 | upgrade 产生 `RecipeModifier`，影响 duration/input/output/chance |
| 5 | GUI/Jade | 展示已安装升级、可用升级、冲突原因 |

**注意**：当前项目已有 `api.recipe.modifier.*`，升级系统不要另建一套数值修改逻辑，应产出统一 `RecipeModifier`。

### 4.6 `common.integration.theoneprobe`

**功能价值**：展示 controller/hatch 信息。当前项目已有 Jade compat，因此这块很适合早期吸收。

**建议移植内容**：

- controller：结构是否成型、机器 id、active recipe、进度、缺失原因。
- hatch/bus：IO 类型、方向、容量、当前存量。
- upgrade/parallel/smart interface 信息等到对应功能实现后再加入。

**处理方式**：不要移植 TOP API，直接映射到 `compat.jade`。

### 4.7 `common.integration.gregtech`

**功能价值**：通过 proxy 让 GTCEu 的 energy/fluid/item hatch 作为 MMCE 组件参与 recipe。当前 MMCR 如果未来要深度兼容 GTCEu，这个设计很值得保留。

**建议移植内容**：

- `MachineComponentProxy` 思路：第三方 BE 不继承 MMCR 接口，也能被包装成 `ProcessingComponent`。
- `GTItemBusProxy`、`GTFluidHatchProxy`、`GTEnergyHatchProxy` 的职责划分。
- `GTBlockMachineProxy` 作为 pattern proxy 的思路。

**不要直搬**：所有 GTCEu 1.12 API、类名、capability 判断都需要换成当前目标版本 API。未确认 GTCEu 版本前不要写实现。

### 4.8 `common.integration.ModIntegrationAE2` 与 ME 系列

**功能价值**：ME bus/pattern provider 是 MMCE 的高级自动化体验，价值高但依赖重。

**可移植目标**：

- ME item input/output bus 映射为 AE2 storage/network 交互组件。
- ME fluid bus 视新版 AE2/附属是否支持 fluid API 决定。
- ME pattern provider 映射为自动把 pattern 输入/输出转换为 MMCR recipe 或 recipe transfer。
- ME bus GUI 使用新版 AE2/NeoForge menu/screen 重写。

**阶段建议**：后期单独开 `AE2 compat` spec，不与核心 runtime 混做。

### 4.9 `client.gui` 与 `client.gui.widget`

**功能价值**：MMCE 的动态 GUI 系统解决复杂 controller、结构预览、ME bus、pattern provider 的 UI 组合问题。

**建议保留的设计**：

- `WidgetController` 统一 update/render/input/tooltip 生命周期。
- `WidgetGui` 记录 GUI origin/size。
- container widget 支持 row/column/scrolling/selectable。
- preview widget 与 ingredient list 分离。
- virtual slot 只负责展示/交互，不直接持有真实 inventory。

**必须重写的部分**：

- 鼠标输入从 LWJGL/`Mouse.getEventX()` 改为新版 Screen 回调参数。
- 渲染从旧 GL state/`drawTexturedModalRect` 改为 `GuiGraphics`。
- tooltip 从旧 `drawHoveringText` 改为新版 tooltip API。
- slot/JEI 交互按 JEI 29 API 重写。

**建议阶段**：先让现有 screen 足够可用，再引入轻量 widget 层。不要一开始复制完整 MMCE widget 树。

### 4.10 `client.model`, `client.renderer`, `client.resource`, `client.world`

**功能价值**：动态 controller 模型、GeckoLib 外部模型、Bloom 渲染、结构预览隐藏方块。

**当前建议**：

- 短期不移植 GeckoLib/Bloom 相关实现。
- 可保留“机器定义可指定 controller model/texture”的数据入口，但落地到 vanilla model JSON 或 datagen。
- 结构预览阶段再考虑假世界渲染和 block model hiding。
- 如果未来引入 GeckoLib，需要重新评估依赖和客户端/服务端边界。

### 4.11 `mixin`

**现状**：MMCE 有 early/late loader，并根据是否存在 JEI、AE2、NAE2 加载不同 mixin。具体 mixin 目标是 1.12.2 的 RenderGlobal、TileEntityRendererDispatcher、JEI RecipesGui/RecipeLayout、AE2 interface terminal/pattern multi tool 等。

**处理结论**：默认不移植。

**原因**：

- 目标类和方法签名基本全部过期。
- NeoForge 与 JEI/AE2 新版通常提供更正式的扩展点。
- 过早引入 mixin 会增加维护成本和启动风险。

**例外**：当新版 AE2/JEI 没有公开 API 支持某个必要行为时，为该行为单独写新 mixin，并在文档里记录目标类、原因和替代方案。

## 5. 按优先级排列的可移植清单

### P0：已经在当前项目中部分存在，应继续对齐 MMCE

| 功能 | MMCE 来源 | 当前目标 | 验收点 |
|---|---|---|---|
| Recipe search 语义 | `common.concurrent.RecipeSearchTask` | `api.recipe.RecipeSearchTask` | 成功/失败原因明确，模拟和提交分离。 |
| Context pool | `common.concurrent.RecipeCraftingContextPool` | `api.recipe.RecipeCraftingContextPool` | 不复用失效 BE/capability，测试覆盖 roundtrip。 |
| Requirement/component 路由 | helper + machine component 相关语义 | `api.recipe.helper.*`, `ProcessingComponent` | pattern 内端口参与 IO，pattern 外端口不参与。 |
| Modifier replacement | `client.util.UpgradeIngredient`, upgrade replacement 相关 | `api.recipe.modifier.*` | single/multi replacement 能转成统一 modifier 或 pattern metadata。 |
| Jade 展示 | TOP provider | `compat.jade.*` | 控制器和 hatch/bus 信息可读。 |

### P1：核心闭环稳定后建议做

| 功能 | MMCE 来源 | 当前目标 | 验收点 |
|---|---|---|---|
| Recipe lifecycle events | `common.event.recipe.*` | `api.event` 或 internal event bus + KubeJS bridge | recipe start/tick/complete/fail 可观察，取消语义明确。 |
| Advanced checker | `common.helper.Advanced*Checker` | `api.machine` predicate/checker | block state、tag、item data component 匹配稳定。 |
| Special block/item proxy | `common.machine.pattern.*` | pattern proxy registry | 导出/匹配能表达非普通方块条件。 |
| Upgrade 数据模型 | `common.upgrade.*` | `api.upgrade.*` | upgrade 能声明类型、冲突、描述和 modifier 输出。 |
| 性能计时基础 | `TimeRecorder` | internal profiling utility | 不带 UI，仅日志或 debug 命令可读。 |

### P2：作为独立功能阶段

| 功能 | MMCE 来源 | 当前目标 | 验收点 |
|---|---|---|---|
| 动态 GUI/Widget | `client.gui.widget.*` | 轻量 NeoForge widget 层 | controller/upgrade/preview UI 能复用组件。 |
| 结构预览 | `client.gui.widget.impl.preview.*`, `client.world.*` | 2D 后 3D preview | 可展示结构层、缺失块、材料清单。 |
| JEI 虚拟槽和 recipe transfer | `client.gui.widget.slot.*`, `mixin.jei.*` | JEI 29 category/transfer | 不使用旧 mixin，slot 显示与转移正确。 |
| Parallel/factory search | `FactoryRecipeSearchTask`, `TaskExecutor` | server-safe scheduler | 异步不触碰 world，commit 回主线程。 |
| Upgrade bus GUI | upgrade + GUI | `UpgradeBusBlockEntity/Menu/Screen` | 安装、卸载、冲突提示、Jade 展示完整。 |

### P3：第三方 compat 后期做

| 功能 | MMCE 来源 | 当前目标 | 验收点 |
|---|---|---|---|
| AE2 ME item bus | `MEItemInputBus`, `MEItemOutputBus` | `compat.ae2` | 能从 AE2 网络输入/输出 item。 |
| AE2 ME fluid bus | `MEFluidInputBus`, `MEFluidOutputBus` | `compat.ae2` | 取决于新版 AE2 fluid 支持。 |
| ME pattern provider | `MEPatternProvider` | `compat.ae2.pattern` | pattern 与 MMCR recipe/transfer 对接。 |
| GTCEu component proxy | `common.integration.gregtech.*` | `compat.gtceu` | GT hatch 能作为 MMCR component。 |
| Gas/Mekanism | `MEGas*`, `SlotGasVirtual*` | `compat.mekanism` | gas requirement/component 完整后再做。 |

## 6. 不建议移植清单

| 内容 | 原因 |
|---|---|
| `MMCEEarlyMixinLoader`, `MMCELateMixinLoader` 原样实现 | 1.12.2 Forge/MixinBooter 生命周期不适用于 NeoForge。 |
| `mixin.minecraft.*` 原目标 | 新版渲染管线已变，不能按旧 RenderGlobal/TESR patch 搬。 |
| `mixin.jei.*` 原目标 | JEI 29 API 和内部结构不同，优先用公开 API。 |
| `mixin.ae2.*` / `mixin.ae2.nae2.*` 原目标 | 旧 AE2/NAE2 类和行为不适用。 |
| `AEBaseGuiContainerDynamic` 原样 | 绑定旧 AE2 GUI 基类。 |
| 旧 `GuiME*` 原样 | 绑定旧 AE2 fluid slot、reflection、buttonList/guiSlots。 |
| GeckoLib/Lumenized/Bloom 旧实现 | 当前首期不引入第三方渲染库；旧 API 不适用。 |
| 旧网络包 `IMessage`/`SimpleNetworkWrapper` | 必须用 NeoForge custom payload。 |
| 旧 CraftTweaker/ZenScript 思路 | 当前项目以 KubeJS/Java API/datapack JSON 为入口。 |

## 7. 推荐落地顺序

### 阶段 1：核心语义补齐

1. 对照 MMCE `RecipeSearchTask`，补齐当前 recipe search 的失败原因、模拟边界和 context 生命周期。
2. 引入 recipe lifecycle event 的 Java API 雏形，不立刻暴露 KubeJS 可变能力。
3. 完善 advanced checker/predicate，用于结构匹配、导出和后续 pattern proxy。
4. 把 Jade provider 补成 controller + port 可诊断面板。

### 阶段 2：Upgrade 与 Pattern 扩展

1. 建立 `api.upgrade` 数据模型，不写 GUI。
2. 将 upgrade 输出统一映射为 `RecipeModifier` 或 pattern metadata。
3. 添加 pattern proxy registry，支持特殊 item/block 表达。
4. 在 controller 成型后计算 upgrade/effective modifier，并纳入 recipe context。

### 阶段 3：客户端可用性

1. 先做轻量动态 widget，不完整复制 MMCE widget 包。
2. 做 controller/port/upgrade 的统一 screen 组件。
3. 做结构材料清单和分层 2D preview。
4. 最后评估 3D preview、block model hiding 和自定义渲染。

### 阶段 4：第三方联动

1. JEI category/transfer，避免 mixin。
2. AE2 item bus，再评估 fluid/pattern provider。
3. GTCEu proxy。
4. Mekanism gas。

## 8. 迁移时的命名建议

不要把 `github.kasuminova.mmce` 包名原样搬到主源码。建议按当前项目结构归档：

| MMCE 包 | MMCR 目标包建议 |
|---|---|
| `github.kasuminova.mmce.common.concurrent` | `cn.howxu.mmcr.api.recipe` 或 `cn.howxu.mmcr.internal.recipe` |
| `github.kasuminova.mmce.common.event` | `cn.howxu.mmcr.api.event` / `cn.howxu.mmcr.internal.event` |
| `github.kasuminova.mmce.common.helper` | `cn.howxu.mmcr.api.machine` / `cn.howxu.mmcr.internal.machine` |
| `github.kasuminova.mmce.common.upgrade` | `cn.howxu.mmcr.api.upgrade` / `cn.howxu.mmcr.internal.upgrade` |
| `github.kasuminova.mmce.common.integration.gregtech` | `cn.howxu.mmcr.compat.gtceu` |
| `github.kasuminova.mmce.common.integration.ModIntegrationAE2` | `cn.howxu.mmcr.compat.ae2` |
| `github.kasuminova.mmce.client.gui.widget` | `cn.howxu.mmcr.client.gui.widget` |
| `github.kasuminova.mmce.client.renderer/model/world` | `cn.howxu.mmcr.client.render` / `client.preview` |

## 9. 验收标准

每个被迁移的功能都应满足：

- 不引入 1.12.2 Forge/旧 Minecraft 类名、反射字段名、旧 JEI/AE2 API。
- 核心 API 不硬依赖 KubeJS、JEI、Jade、AE2、GTCEu、Mekanism。
- 服务端逻辑不引用客户端类。
- capability 访问遵循 NeoForge 当前 API。
- 网络同步使用 NeoForge payload，并有明确 client/server 方向。
- recipe IO 坚持 simulate -> commit，不在搜索阶段修改库存、流体或能量。
- 可选 compat 未安装时，主 mod 可正常启动。

## 10. 最小推荐 TODO

如果下一步要真正开始移植，建议先开三个独立 spec/plan：

1. **MMCE recipe event/search 对齐**：补齐 `RecipeSearchTask`、失败原因、事件雏形。
2. **MMCE upgrade model port**：只做数据模型和 modifier 接入，不做 GUI/方块。
3. **Pattern proxy + advanced checker**：让结构匹配表达力接近 MMCE，为后续 preview/upgrade/third-party proxy 铺路。

这三个都完成后，再进入动态 GUI、结构预览、AE2/GTCEu 兼容会更稳。
