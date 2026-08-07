# JEI 独立机器配方页与产物渲染修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为每台已注册机器提供独立的 JEI 配方页面，并修复物品与流体产物槽位的视觉渲染。

**Architecture:** `JeiMachineRecipeTypes` 以机器注册 ID 建立稳定的 JEI type 映射；`JeiPlugin` 使用同一映射按机器注册分类、配方、催化剂和 transfer handler。控制器 GUI 通过动态 JEI 容器处理器读取当前菜单的 controller machine ID 来返回唯一可点击类别，槽位继续使用 JEI 原生 ingredient 注册而不做手工绘制。

**Tech Stack:** Java 21、Minecraft 26.1.2、NeoForge、JEI API、JUnit 5、AssertJ、Gradle。

## Global Constraints

- 不改变 `MachineRecipe` 的配方格式、加载逻辑或执行逻辑。
- 不新增硬依赖，不修改 Gradle、NeoForge、Minecraft 或 JEI 版本。
- 每台 `MachineDefinitions.all()` 中且已有 controller block 的机器自动注册独立页面。
- 分类图标和唯一催化剂必须使用该机器的 controller block。
- 不通过手工绘制替换 JEI ingredient slot；必须保留用途查看、右键跳转与焦点筛选。
- 修改 Java 后至少运行 `./gradlew compileJava --no-daemon`；最终运行 `./gradlew test --no-daemon`。
- 不提交构建产物、缓存、日志或 IDE 文件。

---

## 文件结构

- 修改 `src/main/java/cn/howxu/mmcr/compat/jei/JeiMachineRecipeTypes.java`：按 `Identifier` 缓存和查询 machine-specific `IRecipeType`。
- 修改 `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplays.java`：按 `machineId` 得到稳定排序的 display 分组。
- 修改 `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java`：绑定 machine/type，显示 controller icon 与 machine title，并修正 output slot ingredient 注册。
- 修改 `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeTransferHandler.java`：绑定一个具体 recipe type，使 transfer handler 可逐类别注册。
- 修改 `src/main/java/cn/howxu/mmcr/compat/jei/JeiPlugin.java`：按 machine 注册 category、recipes、catalyst、transfer，使用动态 controller GUI click-area handler。
- 修改 `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java`：验证 display 分组和跨机器隔离。
- 新建 `src/test/java/cn/howxu/mmcr/compat/jei/JeiMachineRecipeTypesTest.java`：验证 type ID 稳定性及不同机器不共享 type。
- 新建 `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategoryTest.java`：使用 JEI test doubles 验证 category machine/type/icon 与 output ingredient slot 配置。
- 修改或新建 `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeTransferHandlerTest.java`：验证 handler 返回被绑定的独立 type。

## Task 1: 机器专属 JEI 类型与配方分组

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/JeiMachineRecipeTypes.java:1-18`
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplays.java:1-34`
- Create: `src/test/java/cn/howxu/mmcr/compat/jei/JeiMachineRecipeTypesTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java:85-112`

**Interfaces:**
- Consumes: `MachineRecipe.machineId(): Identifier`, `MachineRecipeDisplay.machineId(): Identifier`, `RecipeRegistry.recipes(): List<MachineRecipe>`.
- Produces: `JeiMachineRecipeTypes.forMachine(Identifier): IRecipeType<MachineRecipeDisplay>` and `MachineRecipeDisplays.byMachine(): Map<Identifier, List<MachineRecipeDisplay>>`.

- [ ] **Step 1: 写入失败测试，定义 machine-specific type 的稳定与隔离语义。**

```java
@Test
void machineTypesAreStableAndDistinct() {
    var blastFurnace = JeiMachineRecipeTypes.forMachine(MMCR.id("blast_furnace"));
    var alloyFurnace = JeiMachineRecipeTypes.forMachine(MMCR.id("alloy_furnace"));

    assertThat(blastFurnace).isSameAs(JeiMachineRecipeTypes.forMachine(MMCR.id("blast_furnace")));
    assertThat(blastFurnace).isNotEqualTo(alloyFurnace);
    assertThat(blastFurnace.getUid()).isEqualTo(MMCR.id("machine_recipe/blast_furnace"));
}
```

- [ ] **Step 2: 运行测试，确认当前全局常量实现不能满足接口。**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.JeiMachineRecipeTypesTest --no-daemon`

Expected: FAIL，`forMachine` 不存在。

- [ ] **Step 3: 实现最小 type 映射。**

```java
private static final Map<Identifier, IRecipeType<MachineRecipeDisplay>> TYPES = new ConcurrentHashMap<>();

public static IRecipeType<MachineRecipeDisplay> forMachine(Identifier machineId) {
    return TYPES.computeIfAbsent(machineId, id -> IRecipeType.create(
            Identifier.fromNamespaceAndPath(id.getNamespace(), "machine_recipe/" + id.getPath()),
            MachineRecipeDisplay.class));
}
```

删除 `MACHINE_RECIPE`，让所有调用方都在后续任务迁移至 `forMachine`。若 ID path 含嵌套路径，保持其原始 path 拼接，禁止只使用最后一段或只使用 namespace。

- [ ] **Step 4: 写入并运行 display 分组失败测试。**

在 `MachineRecipeDisplayTest` 登记同机器不同优先级和另一机器各一条配方，断言：

```java
assertThat(MachineRecipeDisplays.byMachine())
        .containsOnlyKeys(MMCR.id("blast_furnace"), MMCR.id("alloy_furnace"));
assertThat(MachineRecipeDisplays.byMachine().get(MMCR.id("blast_furnace")))
        .extracting(MachineRecipeDisplay::recipeId)
        .containsExactly(MMCR.id("high"), MMCR.id("low"));
```

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest --no-daemon`

Expected: FAIL，`byMachine` 不存在。

- [ ] **Step 5: 实现确定性 display 分组。**

```java
public static Map<Identifier, List<MachineRecipeDisplay>> byMachine() {
    return all().stream().collect(Collectors.groupingBy(
            MachineRecipeDisplay::machineId,
            LinkedHashMap::new,
            Collectors.toList()));
}
```

保留 `all()` 作为已有测试与调用方的平铺、确定性排序视图；`byMachine()` 必须以 `all()` 的排序为来源，确保每个分组中的 priority/ID 顺序不变。

- [ ] **Step 6: 运行两组测试。**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.JeiMachineRecipeTypesTest --tests cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest --no-daemon`

Expected: PASS。

- [ ] **Step 7: 提交。**

```bash
git add src/main/java/cn/howxu/mmcr/compat/jei/JeiMachineRecipeTypes.java src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplays.java src/test/java/cn/howxu/mmcr/compat/jei/JeiMachineRecipeTypesTest.java src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java
git commit -m "feat(jei): split recipe types by machine"
```

## Task 2: 分类图标、标题和产物槽位渲染

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java:1-119`
- Create: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategoryTest.java`

**Interfaces:**
- Consumes: `Machine.registryName(): Identifier`, machine display-name API, `ModBlocks.controllerFor(Machine)`, `JeiMachineRecipeTypes.forMachine(Identifier)`.
- Produces: `MachineRecipeCategory(IGuiHelper, Machine)` and per-machine `getRecipeType()`, `getTitle()`, `getIcon()` behavior.

- [ ] **Step 1: 写入 category 构造和 output slot 的失败测试。**

使用当前仓库 JEI API 的 test double 或 Mockito（仅当构建已有依赖提供时）捕获 builder 调用，断言：

```java
assertThat(category.getRecipeType()).isSameAs(JeiMachineRecipeTypes.forMachine(machine.registryName()));
assertThat(category.getIcon()).isNotNull();
assertThat(recordedOutputItemStacks).containsExactly(display.itemOutputs().getFirst());
assertThat(recordedOutputFluids).containsExactly(display.fluidOutputs().getFirst());
assertThat(recordedFluidRenderer).containsExactly(1L, false, 16, 16);
```

测试样本使用小于 1000 mB 的流体产物，以证明它被强制完整绘制。

- [ ] **Step 2: 运行测试，确认旧通用分类与 output renderer 参数不满足要求。**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeCategoryTest --no-daemon`

Expected: FAIL，旧构造器不接受 machine，且流体 renderer 使用 `max(1000, amount)` / `showCapacity=true`。

- [ ] **Step 3: 将分类绑定到机器。**

```java
private final Machine machine;
private final IRecipeType<MachineRecipeDisplay> recipeType;
private final IDrawable icon;

public MachineRecipeCategory(IGuiHelper guiHelper, Machine machine) {
    this.machine = machine;
    this.recipeType = JeiMachineRecipeTypes.forMachine(machine.registryName());
    this.icon = guiHelper.createDrawableItemLike(ModBlocks.controllerFor(machine).get());
}

@Override
public Component getTitle() {
    return Component.translatable(machine.localizedName());
}
```

`Machine.localizedName()` 已是当前 machine 定义提供的翻译键；不要新增重复字段或硬编码翻译键。

- [ ] **Step 4: 修正物品和流体输出槽的 JEI ingredient 注册。**

物品输出保留 `addOutputSlot(...).setOutputSlotBackground()`，并以 `ItemStack` 的 JEI 原生入口添加 stack。流体输出在同一个 output slot 上按下列方式设置：

```java
builder.addOutputSlot(slot.x(), slot.y())
        .setOutputSlotBackground()
        .setFluidRenderer(1, false, 16, 16)
        .add(stack.getFluid(), stack.getAmount(), stack.getComponentsPatch());
```

`setFluidRenderer(1, false, 16, 16)` 是 JEI API 文档给出的“小于一桶仍完整显示”的配置。不要改写 `FluidStack` 的 amount 或 components；amount 仍提供给 JEI tooltip 和 ingredient identity。

- [ ] **Step 5: 运行 category 测试。**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeCategoryTest --no-daemon`

Expected: PASS。

- [ ] **Step 6: 提交。**

```bash
git add src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategoryTest.java
git commit -m "fix(jei): render machine outputs correctly"
```

## Task 3: 按机器注册 JEI 页面、催化剂和 transfer handler

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/JeiPlugin.java:1-58`
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeTransferHandler.java:22-64`
- Modify: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeTransferHandlerTest.java:1-17`
- Create: `src/test/java/cn/howxu/mmcr/compat/jei/JeiPluginRegistrationTest.java`

**Interfaces:**
- Consumes: `MachineDefinitions.all(): Collection<Machine>`, `MachineRecipeDisplays.byMachine(): Map<Identifier, List<MachineRecipeDisplay>>`, `JeiMachineRecipeTypes.forMachine(Identifier)`.
- Produces: 每台机器一个 category、仅本机 recipe displays、仅本机 controller catalyst，及绑定 type 的 `MachineRecipeTransferHandler`。

- [ ] **Step 1: 为 transfer handler 写失败测试。**

```java
@Test
void handlerUsesItsMachineRecipeType() {
    var type = JeiMachineRecipeTypes.forMachine(MMCR.id("blast_furnace"));
    var handler = new MachineRecipeTransferHandler(helper, type);

    assertThat(handler.getRecipeType()).isSameAs(type);
}
```

`helper` 沿用现有测试的 mock/test helper 构造方式；不要调用真实 JEI runtime。

- [ ] **Step 2: 运行测试，确认 handler 仍固定引用旧全局 type。**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeTransferHandlerTest --no-daemon`

Expected: FAIL，构造器签名不存在或 `getRecipeType()` 不相同。

- [ ] **Step 3: 将 handler 绑定到构造时传入的 type。**

```java
private final IRecipeType<MachineRecipeDisplay> recipeType;

public MachineRecipeTransferHandler(IRecipeTransferHandlerHelper helper, IRecipeType<MachineRecipeDisplay> recipeType) {
    this.helper = helper;
    this.recipeType = recipeType;
    this.basicHandler = helper.createUnregisteredRecipeTransferHandler(
            helper.createBasicRecipeTransferInfo(ItemBusMenu.class, ModUIs.ITEM_BUS.get(), recipeType,
                    ItemBusMenu.BUS_SLOT_START, ItemBusMenu.BUS_SLOT_COUNT,
                    ItemBusMenu.PLAYER_INVENTORY_SLOT_START, ItemBusMenu.PLAYER_INVENTORY_SLOT_COUNT));
}

@Override
public IRecipeType<MachineRecipeDisplay> getRecipeType() {
    return recipeType;
}
```

- [ ] **Step 4: 为 JEI plugin 注册写失败测试。**

使用 `IRecipeCategoryRegistration`、`IRecipeRegistration` 与 `IRecipeCatalystRegistration` 的捕获型 test double，准备两台 machine 和三条配方。分别调用 plugin 注册方法并断言：

```java
assertThat(categories).extracting(MachineRecipeCategory::getRecipeType)
        .containsExactlyInAnyOrder(blastType, alloyType);
assertThat(recipesByType.get(blastType)).extracting(MachineRecipeDisplay::machineId)
        .containsOnly(MMCR.id("blast_furnace"));
assertThat(catalystsByType.get(blastType)).containsExactly(new ItemStack(ModBlocks.controllerFor(blast).get()));
```

断言 alloy category 没有 blast 配方或 blast catalyst。

- [ ] **Step 5: 运行 plugin 注册测试，确认旧实现将数据放入一个全局 type。**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.JeiPluginRegistrationTest --no-daemon`

Expected: FAIL，旧 plugin 只注册一个 category/type，全部 displays 与 catalysts 混合。

- [ ] **Step 6: 实现按 machine 的 plugin 注册。**

```java
public void registerCategories(IRecipeCategoryRegistration registration) {
    var guiHelper = registration.getJeiHelpers().getGuiHelper();
    MachineDefinitions.all().forEach(machine ->
            registration.addRecipeCategories(new MachineRecipeCategory(guiHelper, machine)));
}

public void registerRecipes(IRecipeRegistration registration) {
    var displaysByMachine = MachineRecipeDisplays.byMachine();
    MachineDefinitions.all().forEach(machine -> registration.addRecipes(
            JeiMachineRecipeTypes.forMachine(machine.registryName()),
            displaysByMachine.getOrDefault(machine.registryName(), List.of())));
}

public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
    MachineDefinitions.all().forEach(machine -> registration.addCraftingStation(
            JeiMachineRecipeTypes.forMachine(machine.registryName()),
            new ItemStack(ModBlocks.controllerFor(machine).get())));
}

public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
    var helper = registration.getTransferHelper();
    MachineDefinitions.all().forEach(machine -> {
        var type = JeiMachineRecipeTypes.forMachine(machine.registryName());
        registration.addRecipeTransferHandler(new MachineRecipeTransferHandler(helper, type), type);
    });
}
```

未知 machine ID 的 recipe 不会出现在 `MachineDefinitions.all()` 的循环中，因此不注册到任何类别。添加一次 `MMCR.LOGGER.warn`，列出该 ID 和 recipe ID，便于定位无效数据，而不让单条配方中断 JEI 注册。

- [ ] **Step 7: 运行 transfer 与 plugin 注册测试。**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeTransferHandlerTest --tests cn.howxu.mmcr.compat.jei.JeiPluginRegistrationTest --no-daemon`

Expected: PASS。

- [ ] **Step 8: 提交。**

```bash
git add src/main/java/cn/howxu/mmcr/compat/jei/JeiPlugin.java src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeTransferHandler.java src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeTransferHandlerTest.java src/test/java/cn/howxu/mmcr/compat/jei/JeiPluginRegistrationTest.java
git commit -m "feat(jei): register recipes per machine"
```

## Task 4: 控制器 GUI 的动态配方跳转

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/JeiPlugin.java:registerGuiHandlers`
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java:98-107`
- Create: `src/test/java/cn/howxu/mmcr/internal/menu/MachineControllerMenuTest.java`（若已有同名测试则修改）
- Modify: `src/test/java/cn/howxu/mmcr/compat/jei/JeiPluginRegistrationTest.java`

**Interfaces:**
- Consumes: `MachineControllerMenu.resolvedOwner(): MachineControllerBlockEntity`, `MachineControllerBlockEntity.getMachine(): Machine`, `JeiMachineRecipeTypes.forMachine(Identifier)`.
- Produces: `MachineControllerMenu.machineId(): @Nullable Identifier`，以及只在当前 controller machine type 上生效的 `IGuiContainerHandler<MachineMenuScreen>`。

- [ ] **Step 1: 写入菜单 machine ID 解析的失败测试。**

为直接 owner、客户端按 block-pos 延迟解析、无 owner/无 level 三种菜单状态分别断言：

```java
assertThat(menu.machineId()).isEqualTo(MMCR.id("blast_furnace"));
assertThat(unresolvedClientMenu.machineId()).isNull();
```

- [ ] **Step 2: 运行菜单测试。**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --no-daemon`

Expected: FAIL，`machineId()` 不存在。

- [ ] **Step 3: 在菜单中提供非客户端类的 machine ID 查询。**

```java
public @Nullable Identifier machineId() {
    MachineControllerBlockEntity controller = resolvedOwner();
    Machine machine = controller == null ? null : controller.getMachine();
    return machine == null ? null : machine.registryName();
}
```

不得在 common menu 中导入 JEI 或 `MachineMenuScreen`；该菜单 API 仅暴露 machine ID。

- [ ] **Step 4: 写入动态 click-area handler 的失败测试。**

使用 `IGuiHandlerRegistration` 捕获注册的 `IGuiContainerHandler<MachineMenuScreen>`，将带不同 `MachineControllerMenu.machineId()` 的 screen 传入并断言：

```java
assertThat(handler.getGuiClickableAreas(blastScreen, 10, 30))
        .extracting(area -> area.getRecipeTypes())
        .containsExactly(List.of(JeiMachineRecipeTypes.forMachine(MMCR.id("blast_furnace"))));
assertThat(handler.getGuiClickableAreas(unresolvedScreen, 10, 30)).isEmpty();
```

采用当前 JEI `IGuiClickableArea` 的实际访问器名称；不要改回 `addRecipeClickArea`，因为它只能静态绑定所有 type。

- [ ] **Step 5: 运行 plugin 注册测试，确认静态 click area 不能按 controller 筛选。**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.JeiPluginRegistrationTest --no-daemon`

Expected: FAIL，旧实现注册全局 `machine_recipe` click area。

- [ ] **Step 6: 用动态 GUI container handler 替换静态 click area 注册。**

```java
registration.addGuiContainerHandler(MachineMenuScreen.class, new IGuiContainerHandler<MachineMenuScreen>() {
    @Override
    public Collection<IGuiClickableArea> getGuiClickableAreas(MachineMenuScreen screen, double mouseX, double mouseY) {
        if (!(screen.getMenu() instanceof MachineControllerMenu menu)) return List.of();
        Identifier machineId = menu.machineId();
        if (machineId == null) return List.of();
        return List.of(IGuiClickableArea.createBasic(8, 24, 160, 24,
                JeiMachineRecipeTypes.forMachine(machineId)));
    }
});
```

如果 `MachineMenuScreen` 没有公开 `getMenu()`，添加最小 package-visible accessor 返回现有 `menu`，只用于 client-side JEI handler；禁止改变 screen 渲染或布局。

- [ ] **Step 7: 运行菜单与 plugin 测试。**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --tests cn.howxu.mmcr.compat.jei.JeiPluginRegistrationTest --no-daemon`

Expected: PASS。

- [ ] **Step 8: 提交。**

```bash
git add src/main/java/cn/howxu/mmcr/compat/jei/JeiPlugin.java src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java src/test/java/cn/howxu/mmcr/internal/menu/MachineControllerMenuTest.java src/test/java/cn/howxu/mmcr/compat/jei/JeiPluginRegistrationTest.java
git commit -m "fix(jei): open recipes for current controller"
```

## Task 5: 完整验证与客户端验收

**Files:**
- Modify only if test/compile failures require an in-scope correction: files from Tasks 1-4.

**Interfaces:**
- Consumes: 所有前述 task 的 JEI types、categories、registrations 和菜单 click handler。
- Produces: 已通过编译、自动化测试和手动 JEI 验收的改动集。

- [ ] **Step 1: 运行 JEI 相关测试集。**

Run: `./gradlew test --tests 'cn.howxu.mmcr.compat.jei.*' --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --no-daemon`

Expected: PASS。

- [ ] **Step 2: 编译主源码。**

Run: `./gradlew compileJava --no-daemon`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 运行完整测试。**

Run: `./gradlew test --no-daemon`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 在开发客户端进行人工 JEI 验收。**

Run: `./gradlew runClient --no-daemon`

在 JEI 中依次检查高炉、合金炉、反应堆、裂化器：

1. 每个 controller 方块图标只打开自身分类，页面标题与图标匹配该机器。
2. 当前 controller GUI 的 `(8, 24, 160, 24)` 点击区只打开本机配方。
3. 配方物品产物可见，右键查看用途仍能跳转。
4. 少于 1000 mB 的流体产物显示完整 16x16 流体图案，不再只显示半格。
5. JEI 没有重复分类、未知机器配方没有错误归属。

- [ ] **Step 5: 检查改动并提交最终修正。**

Run: `git status --short && git diff --check`

Expected: 无 whitespace error；只暂存本任务修改的源码与测试文件，保留用户已有 `.github/workflows/ci.yml` 和未跟踪 `reference/gtceu/` 不变。

```bash
git add src/main/java/cn/howxu/mmcr/compat/jei src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java src/test/java/cn/howxu/mmcr/compat/jei src/test/java/cn/howxu/mmcr/internal/menu/MachineControllerMenuTest.java
git commit -m "test(jei): verify machine recipe pages"
```

仅当 Step 5 有未提交的 in-scope 修正时创建该 commit；若没有新修改，不创建空提交。
