# JEI 独立机器配方页与产物渲染修复设计

## 1. 背景与目标

当前 JEI 集成只有 `mmcr:machine_recipe` 一个配方类型。所有机器控制器被注册为该类型的工作站，全部 `MachineRecipeDisplay` 也注册在同一页面中。这使高炉、合金炉、反应堆和裂化器等机器的配方混在一起，控制器图标也只是通用槽位。

本次改动目标：

- 每个已注册 `Machine` 拥有独立 JEI recipe type、分类页面、图标、催化剂和配方集合。
- 分类图标与唯一催化剂均使用该机器的控制器方块。
- 机器 GUI 的 JEI 配方点击区域只打开当前 controller 对应的页面。
- 修复 JEI 配方页中流体产物只显示半格、物品产物透明但仍可跳转的问题。
- 新增到 `MachineDefinitions` 的机器自动获得上述 JEI 页面，无需新增硬编码注册项。

## 2. 非目标

- 不改变 `MachineRecipe` 的配方格式、加载逻辑或执行逻辑。
- 不为未注册 controller block 的运行时机器补建方块；JEI 分类仅覆盖当前 `MachineDefinitions` 和 `ModBlocks` 初始化流程支持的机器。
- 不重做配方布局、输入输出位置、能量/时长文字或 JEI recipe transfer 的交互形式。
- 不修改参考仓库的 AE2 或 JEI 源码，只将其当前 NeoForge JEI 集成模式作为调用方式依据。

## 3. 类型与注册模型

### 3.1 每机器的 JEI 类型

`JeiMachineRecipeTypes` 从单一常量改为按机器 ID 取得 `IRecipeType<MachineRecipeDisplay>` 的映射。类型 ID 从机器注册 ID 稳定派生，例如高炉 `mmcr:blast_furnace` 对应独立 JEI 类型 `mmcr:machine_recipe/blast_furnace`（具体合法路径格式在实现中一次确定）。

该映射只服务于 JEI 注册期：机器定义和 controller block 已在 mod 构造阶段准备完毕，JEI 注册的三个阶段使用同一套映射，避免类别、配方、催化剂和 GUI 点击区域引用不同的 type 实例。

### 3.2 分类对象

`MachineRecipeCategory` 接收一个 `Machine` 和其 recipe type：

- `getRecipeType` 返回该机器的独立类型。
- `getTitle` 返回机器显示名，而非通用的“机器配方”。
- `getIcon` 使用 `ModBlocks.controllerFor(machine)` 生成 controller `ItemStack` 的 JEI drawable。
- 保持现有通用布局及 duration、energy 文本渲染。

这样高炉、合金炉、反应堆、裂化器等均有独立页，JEI 不会将另一台机器的配方显示在当前分类。

### 3.3 配方、催化剂与 GUI 跳转

`JeiPlugin` 的注册流程统一遍历 `MachineDefinitions.all()`：

1. `registerCategories`：每台机器注册一个 category。
2. `registerRecipes`：按 `MachineRecipeDisplay` 中的 `machineId` 分组，将每组加入对应 machine type。配方归属直接来自 `MachineRecipe.machineId()`；实现可在 display 构建或分组时使用 `RecipeRegistry.getRecipe(recipeId)` 作为权威来源，确保不会由配方 ID 推断机器。
3. `registerRecipeCatalysts`：每台机器只将其 controller 注册到自身 type。
4. `registerRecipeTransferHandlers`：为所有独立 type 注册同一个 transfer handler。

`MachineMenuScreen` 的 JEI click area 需要基于当前 `MachineControllerMenu` 持有的 machine/controller ID，动态解析对应 type；不能继续向 JEI 注册全局 `machine_recipe`。非 controller 菜单仍不关联机器配方页。

## 4. 产物槽位渲染

### 4.1 物品产物

物品输出必须使用 JEI 的物品 ingredient 入口构造，并将 `ItemStack` 作为该入口的 ingredient 添加。输出背景仅作为视觉 drawable，不得代替 ingredient 的类型注册。实现将对照 `reference/ae2` 的 JEI category，采用其适用于当前 JEI API 的 output slot 组合，修复 JEI 已识别条目但没有正确绘制物品图标的状态。

### 4.2 流体产物

流体输出必须构造完整 `FluidStack`，再以当前 JEI API 的 fluid ingredient 类型注册，并在同一 output slot 配置 fluid renderer。renderer 的容量取 `max(1000, amount)`，尺寸与槽位为完整的 16x16；背景和 renderer 绑定到同一槽位。

实现将对照 `reference/ae2` 的 JEI 集成，确认 fluid renderer、背景和 ingredient 的调用顺序及数据组件传递方式，消除流体产物只有半格的渲染结果。

### 4.3 保持交互

物品与流体 ingredient 仍由 JEI 原生槽位注册。因此查看用途、右键跳转及焦点筛选继续可用；修复只恢复视觉渲染，不能通过手工绘制替代 JEI ingredient 槽位。

## 5. 错误处理与边界

- 配方引用不存在的 machine ID 时不注册到任意类别，并记录一次明确警告，防止 JEI 注册因单个无效数据配方失败。
- `MachineDefinitions` 中缺少 controller 时沿用 `ModBlocks.controllerFor` 的显式失败语义，因为这表示 registry-time 初始化顺序或机器定义错误，而非可静默忽略的运行时状态。
- JEI 类型的派生 ID 必须保持稳定且无冲突；机器 ID 已是注册表唯一键，派生逻辑不得只使用 path。

## 6. 测试与验证

增加或调整 JEI 单元测试，覆盖：

- 四台默认机器生成四个不同的 recipe type/category，每个 type 的 title 和 icon 来自对应机器/controller。
- 新注册的测试机器自动出现独立 category/type。
- 每个 type 仅接收 `machineId` 相同的 display；任何 controller 只注册为自身 type 的 catalyst。
- controller 菜单的 click area 解析到自身 type，不落回全局类型。
- 物品输出与流体输出都通过 JEI 正确的 ingredient 类型和 renderer 注册；流体 renderer 容量与尺寸符合完整槽位渲染要求。

最终执行：

- `./gradlew compileJava --no-daemon`
- 可用的相关测试任务；若项目没有可单独执行的 JEI 测试，执行 `./gradlew test --no-daemon`。
- 客户端人工验证四个 controller 的 JEI 点击、产物物品可见、流体输入/输出完整显示及原有右键跳转。

## 7. 验收标准

- 高炉、合金炉、反应堆、裂化器分别拥有独立 JEI 页面。
- 任意已注册机器自动拥有独立页面，且页面仅显示 `MachineRecipe.machineId()` 相符的配方。
- 分类图标和唯一催化剂均为相应 controller block；从某 controller 或其 GUI 不能跳转到其他机器页面。
- 物品产物可见且保持 JEI 跳转行为。
- 流体产物以完整 16x16 槽位显示，不再只有半格。
- 编译和相关测试通过，未安装/未启用 JEI 的通用代码路径不引入客户端类加载问题。
