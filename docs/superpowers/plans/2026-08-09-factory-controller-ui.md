# 工厂处理器控制器 UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 工厂处理器存在时，控制器 UI 只显示并行仓数量与已安装线程容量，不显示会在多线程竞争下失真的并行数。

**Architecture:** 在 `MachineControllerMenu` 添加一个临时 `DataSlot`，服务器从关联 `FactorySchedulerBlockEntity.threadCount()` 读取线程容量并同步给客户端。`MachineMenuScreen` 根据工厂处理器状态在“并行数”与“当前线程数”两种行之间选择；不向控制器实体增加任何线程计数的存档字段。

**Tech Stack:** Java 25、Minecraft 26.1.2、NeoForge、Gradle、JUnit 5、AssertJ。

## Global Constraints

- 线程数必须使用 `FactorySchedulerBlockEntity.threadCount()`，即基础线程 1 加线程分配器物品数量。
- 不使用 `activeLaneCount()`，它是动态运行状态且会受输入与并行竞争影响。
- 线程数经控制器菜单 `DataSlot` 同步，客户端不得从控制器实体或工厂处理器库存重新计算。
- 不在 `MachineControllerBlockEntity` 增加线程数持久化字段；工厂处理器库存已有持久化。
- 有工厂处理器时显示并行仓数量和当前线程数，不显示当前并行数或最大并行数。
- 无工厂处理器时保留现有“并行数: 当前 / 最大”显示。
- Jade 不在本计划范围内。

---

## 文件结构

- 修改 `src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java`：新增并同步 `factoryThreadCount` 数据槽，并暴露客户端安全 getter。
- 修改 `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java`：工厂模式改渲染线程数行，普通模式继续渲染并行数行。
- 修改 `src/main/java/cn/howxu/mmcr/datagen/Translations.java`：新增英语和简体中文线程数翻译键。
- 修改 `src/test/java/cn/howxu/mmcr/internal/menu/MachineControllerMenuTest.java`：验证线程容量数据槽与客户端实体已加载时的数据槽优先级。
- 修改 `src/test/java/cn/howxu/mmcr/client/gui/MenuScreenTest.java`：验证工厂模式选线程数行，普通模式选并行数行。
- 修改 `src/test/java/cn/howxu/mmcr/datagen/TranslationsTest.java`：验证新增英中翻译键。

### Task 1: 同步工厂线程容量

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java:24-98,172-194`
- Test: `src/test/java/cn/howxu/mmcr/internal/menu/MachineControllerMenuTest.java:85-116`

**Interfaces:**
- Consumes: `MachineControllerBlockEntity#getFactoryController(): @Nullable FactorySchedulerBlockEntity`
- Consumes: `FactorySchedulerBlockEntity#threadCount(): int`
- Produces: `MachineControllerMenu#factoryThreadCount(): int`
- Produces: 菜单第 10 个数据槽（索引 `10`），值为工厂处理器线程容量；无工厂处理器时为 `0`。

- [ ] **Step 1: 写入失败的客户端菜单同步测试**

在 `client_menu_updates_parallel_display_from_synced_data_slots` 后加入以下测试，先假定新增槽位索引为 `10`：

```java
@Test
void client_menu_updates_factory_thread_count_from_synced_data_slot() {
    MachineControllerMenu menu = new MachineControllerMenu(1, emptyInventory());

    assertThat(menu.factoryThreadCount()).isZero();
    menu.setData(10, 3);

    assertThat(menu.factoryThreadCount()).isEqualTo(3);
}
```

扩展 `client_menu_uses_synced_parallel_data_when_the_client_controller_is_available`：在设置索引 `9` 后设置 `menu.setData(10, 3)`，并断言：

```java
assertThat(menu.factoryThreadCount()).isEqualTo(3);
```

- [ ] **Step 2: 运行测试确认其因缺少 getter 而失败**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --no-daemon
```

Expected: `compileTestJava` 失败，报 `cannot find symbol: method factoryThreadCount()`。

- [ ] **Step 3: 在菜单中添加 DataSlot 与客户端安全 getter**

在字段区紧接 `factoryControllerPresent` 后添加：

```java
private final DataSlot factoryThreadCount;
```

在服务端构造函数中，紧接 `factoryControllerPresent` 数据槽后添加：

```java
this.factoryThreadCount = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
    @Override public int get() {
        var factory = owner.getFactoryController();
        return factory == null ? 0 : factory.threadCount();
    }
    @Override public void set(int value) {}
});
```

在客户端 `BlockPos` 构造函数中，紧接 `factoryControllerPresent` 初始化后添加：

```java
this.factoryThreadCount = addDataSlot(DataSlot.standalone());
```

在 `hasFactoryController()` 后添加：

```java
public int factoryThreadCount() {
    if (owner == null) return factoryThreadCount.get();
    var factory = owner.getFactoryController();
    return factory == null ? 0 : factory.threadCount();
}
```

不要把此值写入 `MachineControllerBlockEntity` 的 NBT 或字段。该数值来自 `FactorySchedulerBlockEntity` 已持久化的库存内容，菜单关闭后不应保留镜像状态。

- [ ] **Step 4: 运行菜单测试确认通过**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`，新测试证明客户端即使已加载本地控制器实体也读取同步槽位的值 `3`。

- [ ] **Step 5: 提交数据同步变更**

```bash
git add src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java src/test/java/cn/howxu/mmcr/internal/menu/MachineControllerMenuTest.java
git commit -m "feat: sync factory thread capacity to controller menu"
```

### Task 2: 切换控制器 UI 的工厂显示行

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java:333-395`
- Test: `src/test/java/cn/howxu/mmcr/client/gui/MenuScreenTest.java:66-77`

**Interfaces:**
- Consumes: `MachineControllerMenu#hasFactoryController(): boolean`
- Consumes: `MachineControllerMenu#factoryThreadCount(): int`
- Produces: `MachineMenuScreen#controllerWorkLine(int parallelism, int maxParallelism, boolean hasFactoryController, int factoryThreadCount): Component`
- Produces: `MachineMenuScreen#factoryThreadLine(int threadCount): Component`

- [ ] **Step 1: 替换现有工厂并行数回归测试为失败用例**

将 `controller_parallel_line_keeps_current_parallelism_for_factory_controllers` 替换为：

```java
@Test
void controller_work_line_uses_thread_count_when_a_factory_controller_is_present() {
    assertThat(MachineMenuScreen.controllerWorkLine(7, 524, true, 3).getString())
            .isEqualTo("gui.mmcr.controller.threads");
}

@Test
void controller_work_line_uses_parallelism_without_a_factory_controller() {
    assertThat(MachineMenuScreen.controllerWorkLine(7, 524, false, 0).getString())
            .isEqualTo("gui.mmcr.controller.parallel");
}
```

在 `controller_detail_lines_use_parallel_and_parallel_slot_labels` 中增加：

```java
assertThat(MachineMenuScreen.factoryThreadLine(3).getString())
        .isEqualTo("gui.mmcr.controller.threads");
```

- [ ] **Step 2: 运行屏幕测试确认缺少 UI 助手而失败**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.client.gui.MenuScreenTest --no-daemon
```

Expected: `compileTestJava` 失败，报 `cannot find symbol: method controllerWorkLine(...)` 和 `factoryThreadLine(int)`。

- [ ] **Step 3: 实现工厂线程数行和显示选择**

在 `renderControllerStatus` 的已成型分支中，保留并行仓数量行，并用以下代码替换当前 `controllerParallelLine(...)` 调用：

```java
Component workLine = controllerWorkLine(
        menu.currentParallelism(),
        menu.maxParallelism(),
        menu.hasFactoryController(),
        menu.factoryThreadCount());
scaledY = renderScaledWrappedLine(g, workLine,
        scaledX, scaledY, scaledWidth, STATUS_LABEL_COLOR);
```

删除不再需要的 `totalParallelLine` 与 `controllerParallelLine`。在 `parallelLine` 后新增：

```java
static Component controllerWorkLine(int parallelism, int maxParallelism,
                                    boolean hasFactoryController, int factoryThreadCount) {
    return hasFactoryController ? factoryThreadLine(factoryThreadCount) : parallelLine(parallelism, maxParallelism);
}

static Component factoryThreadLine(int threadCount) {
    return Component.translatable("gui.mmcr.controller.threads",
            Component.literal(NUMBER_FORMAT.format(threadCount)));
}
```

工厂处理器存在时，该选择只产生线程数翻译组件，不得调用或拼接并行数、最大并行数。

- [ ] **Step 4: 运行屏幕测试确认通过**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.client.gui.MenuScreenTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`，工厂分支得到 `gui.mmcr.controller.threads`，普通分支得到 `gui.mmcr.controller.parallel`。

- [ ] **Step 5: 提交 UI 选择变更**

```bash
git add src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java src/test/java/cn/howxu/mmcr/client/gui/MenuScreenTest.java
git commit -m "feat: show factory thread capacity in controller UI"
```

### Task 3: 添加翻译并验证生成内容

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java:218-220,446-449`
- Modify: `src/test/java/cn/howxu/mmcr/datagen/TranslationsTest.java`

**Interfaces:**
- Produces: `gui.mmcr.controller.threads` 翻译键。
- Produces: 英语值 `Current Threads: %s`，简体中文值 `当前线程数: %s`。

- [ ] **Step 1: 写入失败的翻译测试**

在 `TranslationsTest` 的控制器 UI 翻译断言旁添加：

```java
assertEquals("Current Threads: %s", english.get("gui.mmcr.controller.threads"));
assertEquals("当前线程数: %s", chinese.get("gui.mmcr.controller.threads"));
```

使用该测试已有的语言映射变量名；如果实际变量名不同，只调整变量名，不改变键与期望值。

- [ ] **Step 2: 运行翻译测试确认失败**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.datagen.TranslationsTest --no-daemon
```

Expected: 断言失败，新增键的实际值为 `null`。

- [ ] **Step 3: 添加英语与简体中文翻译项**

在英语映射的 `parallel_slots` 和 `parallel` 之间添加：

```java
Map.entry("gui.mmcr.controller.threads",               "Current Threads: %s"),
```

在简体中文映射的 `parallel_slots` 和 `parallel` 之间添加：

```java
Map.entry("gui.mmcr.controller.threads",               "当前线程数: %s"),
```

- [ ] **Step 4: 运行翻译与相关 UI 测试确认通过**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.datagen.TranslationsTest --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --tests cn.howxu.mmcr.client.gui.MenuScreenTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: 完整编译并提交翻译变更**

Run:

```bash
rtk gradlew compileJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`。

```bash
git add src/main/java/cn/howxu/mmcr/datagen/Translations.java src/test/java/cn/howxu/mmcr/datagen/TranslationsTest.java
git commit -m "feat: translate factory thread capacity"
```

### Task 4: 最终验证和工作树检查

**Files:**
- Verify only: `src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java`
- Verify only: `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java`
- Verify only: `src/main/java/cn/howxu/mmcr/datagen/Translations.java`
- Verify only: `src/test/java/cn/howxu/mmcr/internal/menu/MachineControllerMenuTest.java`
- Verify only: `src/test/java/cn/howxu/mmcr/client/gui/MenuScreenTest.java`
- Verify only: `src/test/java/cn/howxu/mmcr/datagen/TranslationsTest.java`

- [ ] **Step 1: 运行全部目标测试和 Java 编译**

Run:

```bash
rtk gradlew test --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --tests cn.howxu.mmcr.client.gui.MenuScreenTest --tests cn.howxu.mmcr.datagen.TranslationsTest --no-daemon
rtk gradlew compileJava --no-daemon
```

Expected: 两个命令均为 `BUILD SUCCESSFUL`。

- [ ] **Step 2: 检查变更范围和空白错误**

Run:

```bash
rtk git diff --check
rtk git status --short
```

Expected: 无空白错误；只保留本计划相关文件的预期改动，绝不回退其他协作者改动。

- [ ] **Step 3: 审阅持久化边界**

确认 `MachineControllerBlockEntity` 未新增线程容量字段、`saveAdditional` / `loadAdditional` 未因本功能修改；`FactorySchedulerBlockEntity` 的库存序列化保持原样。线程容量只能由菜单 DataSlot 在 UI 打开期间同步。
