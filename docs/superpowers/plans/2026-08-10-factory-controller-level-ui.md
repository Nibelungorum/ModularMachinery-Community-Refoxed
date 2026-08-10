# 工厂控制器等级 UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在工厂控制器界面显示已同步的机器等级，并让普通与工厂控制器一致显示“等级不足”失败原因。

**Architecture:** 工厂界面复用普通控制器已有的控制器解析和等级文本生成方式，不向工厂快照添加冗余等级字段。等级失败继续作为瞬态控制器状态，通过现有菜单 `DataSlot` 与工厂菜单快照/回退路径同步，且不写入 NBT。

**Tech Stack:** Java 25、NeoForge 26.1.2、JUnit 5、Gradle。

## Global Constraints

- 不修改等级匹配、配方筛选、JEI 展示或其他失败原因的优先级。
- 等级失败文案固定为“等级不足”，不显示具体等级项或数值。
- 机器等级使用现有控制器状态同步；失败原因是运行态，禁止写入控制器或工厂调度器 NBT。
- 保留工作区中不属于本功能的已有修改。

---

### Task 1: 同步等级失败原因

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/MachineControllerMenu.java:253-267`
- Modify: `src/main/java/cn/howxu/mmcr/internal/menu/ControllerMenuState.java:52-66`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/menu/MachineControllerMenuTest.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/menu/FactoryControllerMenuTest.java`

**Interfaces:**
- Consumes: `MachineControllerBlockEntity#getLastFailureUnloc()` 返回翻译键。
- Produces: 菜单编码 `4` 与翻译键 `gui.mmcr.controller.failure.level_insufficient` 的双向映射。

- [ ] **Step 1: 写入失败测试**

在两个菜单测试中断言等级失败键可被客户端菜单读取：

```java
assertThat(menu.lastFailureMessage())
        .isEqualTo("gui.mmcr.controller.failure.level_insufficient");
```

工厂菜单断言：

```java
assertThat(menu.lastFailureUnloc())
        .isEqualTo("gui.mmcr.controller.failure.level_insufficient");
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --tests cn.howxu.mmcr.internal.menu.FactoryControllerMenuTest --no-daemon`

Expected: FAIL，等级失败键尚未由数据槽编码或解码。

- [ ] **Step 3: 实现最小同步与翻译改动**

在两处失败编码方法中加入同一条映射：

```java
if ("gui.mmcr.controller.failure.level_insufficient".equals(key)) return 4;
```

并在两个 `switch` 中加入：

```java
case 4 -> "gui.mmcr.controller.failure.level_insufficient";
```

在英文和简体中文翻译表加入该键；简体中文值为 `等级不足`。不修改任何 NBT 读写方法。

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --tests cn.howxu.mmcr.internal.menu.FactoryControllerMenuTest --no-daemon`

Expected: PASS。

### Task 2: 工厂控制器等级显示

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/client/gui/FactoryControllerScreen.java:156-189`
- Test: `src/test/java/cn/howxu/mmcr/client/gui/FactoryControllerScreenTest.java`

**Interfaces:**
- Consumes: `FactoryControllerMenu#controllerPos()`、客户端 `MachineControllerBlockEntity` 的已同步等级快照，以及 `MachineMenuScreen` 的等级行格式。
- Produces: 工厂控制器状态区在上次失败原因之前显示每个已匹配等级行。

- [ ] **Step 1: 写入失败测试**

为工厂屏幕的等级行构造测试，断言形成后控制器的同步等级生成：

```java
assertThat(renderedText)
        .contains("热能冶炼线圈: 铁块");
```

并断言该文本的渲染顺序位于上次失败行之前。

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew test --tests cn.howxu.mmcr.client.gui.FactoryControllerScreenTest --no-daemon`

Expected: FAIL，工厂屏幕尚未提取或渲染等级行。

- [ ] **Step 3: 实现最小 UI 改动**

在 `FactoryControllerScreen.extractRenderState` 的状态行之后、失败行之前，按普通 `MachineMenuScreen` 当前的等级行生成方式：通过 `menu.controllerPos()` 解析客户端 `MachineControllerBlockEntity`，遍历已同步的等级快照并渲染每行。无等级时不增加行高；无法解析客户端控制器时不显示等级。不得在 `FactoryControllerSnapshot` 中新增等级字段或在 NBT 中缓存等级。

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew test --tests cn.howxu.mmcr.client.gui.FactoryControllerScreenTest --no-daemon`

Expected: PASS。

### Task 3: 回归验证与提交

**Files:**
- Modify: 本计划前两项涉及的生产与测试文件。

- [ ] **Step 1: 运行针对性测试**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.menu.MachineControllerMenuTest --tests cn.howxu.mmcr.internal.menu.FactoryControllerMenuTest --tests cn.howxu.mmcr.client.gui.FactoryControllerScreenTest --no-daemon`

Expected: PASS。

- [ ] **Step 2: 编译生产代码**

Run: `./gradlew compileJava --no-daemon`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 检查变更并提交**

Run: `git diff --check && git status --short`

Expected: 无空白错误，只暂存本功能文件，不纳入用户已有的无关修改。
