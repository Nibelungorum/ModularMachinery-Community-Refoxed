# Scrollable Text UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为普通控制器、工厂控制器和 `guicontroller_large.png` 扩展接口文本页增加按详情区域逐行滚动的公共实现。

**Architecture:** 新增 `AbstractScrollableTextScreen<M>`，统一维护详情滚动偏移量、视口配置、可视行数计算和鼠标滚轮命中判断。普通控制器、工厂控制器和 `AbstractPortScreen` 继承它；各页面继续生成并绘制自己的业务文本、颜色、标题和 tooltip。工厂控制器通过额外滚动钩子保留左侧线程列表的独立滚动。

**Tech Stack:** Java, Minecraft 26.1.2, NeoForge, JUnit 5, Gradle。

## Global Constraints

- 保持 Minecraft 26.1.2、NeoForge、Gradle 和现有依赖版本不变。
- 不修改菜单、网络同步、服务端逻辑或资源纹理。
- 不修改 `SmartInterfaceScreen`、`FactorySchedulerScreen`。
- 不覆盖或回滚工作区已有的 `Combined*` 文件改动，包括 `CombinedPortScreen`、`CombinedPortScreenTest`、`CombinedPortMenu` 和 `CombinedPortMenuTest`。
- 新增 Java 类包含 `@author howxu <dev@howxu.cn>` Javadoc。
- 不添加水平滚动、自动换行或额外滚动条。
- 测试和业务代码在同一任务中连续完成，不保留故意失败的测试。
- 禁止运行 `./gradlew runClient --no-daemon`。
- 完成 Java 修改后，串行运行 `./gradlew test --no-daemon`，再运行 `./gradlew runGameTestServer --no-daemon`。

---

## 文件与职责

**Create:**

- `src/main/java/cn/howxu/mmcr/client/gui/AbstractScrollableTextScreen.java`: 公共详情滚动状态、视口模型、行数计算、可视范围和滚轮处理。
- `src/test/java/cn/howxu/mmcr/client/gui/ScrollableTextScreenTest.java`: 公共滚动计算和边界行为的纯单元测试。

**Modify:**

- `src/main/java/cn/howxu/mmcr/client/gui/MachineControllerScreen.java`: 普通控制器继承公共基类，滚动状态详情行。
- `src/main/java/cn/howxu/mmcr/client/gui/FactoryControllerScreen.java`: 工厂控制器继承公共基类，分离右侧详情滚动与左侧线程滚动。
- `src/main/java/cn/howxu/mmcr/client/gui/AbstractPortScreen.java`: 接入公共基类并提供扩展接口文本视口。
- `src/main/java/cn/howxu/mmcr/client/gui/ExtendedItemScreen.java`: 按可见行绘制物品库存文本和 tooltip。
- `src/main/java/cn/howxu/mmcr/client/gui/ExtendedFluidScreen.java`: 按可见行绘制流体库存文本和 tooltip。
- `src/main/java/cn/howxu/mmcr/client/gui/ExtendedCombinedScreen.java`: 按统一逻辑行索引绘制物品、流体分区和 tooltip。
- `src/test/java/cn/howxu/mmcr/client/gui/FactoryControllerScreenTest.java`: 增加工厂详情行范围和已有线程滚动边界的回归测试。
- `src/test/java/cn/howxu/mmcr/client/gui/ExtendedPortScreenTest.java`: 增加扩展接口可滚动行数和分区顺序回归测试。

---

### Task 1: Add The Shared Scrollable Text Screen

**Files:**

- Create: `src/main/java/cn/howxu/mmcr/client/gui/AbstractScrollableTextScreen.java`
- Create: `src/test/java/cn/howxu/mmcr/client/gui/ScrollableTextScreenTest.java`

**Interfaces:**

- Produces `AbstractScrollableTextScreen.TextViewport` with local GUI coordinates and text metrics.
- Produces `scrollableTextViewport()`, `scrollableTextLineCount()`, `visibleTextLineCount()`, `firstVisibleTextLine()`, `lastVisibleTextLineExclusive()`, `visibleTextRow(int)` and `textLineY(int)` for subclasses.
- Produces `handleAdditionalScroll(double, double, double, double)` as the hook used by the factory controller's left thread list.

- [ ] **Step 1: Add the viewport and scroll helper tests together with the target API**

Create tests for the pure calculations that do not require constructing a Minecraft screen:

```java
@Test
void visible_line_count_uses_scaled_font_height_and_spacing() {
    assertThat(AbstractScrollableTextScreen.visibleLineCount(100, 0.85F, 10, 9)).isEqualTo(9);
}

@Test
void visible_line_count_always_allows_one_line() {
    assertThat(AbstractScrollableTextScreen.visibleLineCount(1, 0.85F, 10, 9)).isEqualTo(1);
}

@Test
void max_scroll_offset_is_zero_when_content_fits() {
    assertThat(AbstractScrollableTextScreen.maxScrollOffset(5, 6)).isZero();
}

@Test
void content_that_fits_does_not_consume_wheel_scrolling() {
    assertThat(AbstractScrollableTextScreen.hasScrollableOverflow(5, 6)).isFalse();
    assertThat(AbstractScrollableTextScreen.hasScrollableOverflow(7, 6)).isTrue();
}

@Test
void scroll_offset_is_clamped_to_content_range() {
    assertThat(AbstractScrollableTextScreen.clampScrollOffset(-1, 12, 5)).isZero();
    assertThat(AbstractScrollableTextScreen.clampScrollOffset(99, 12, 5)).isEqualTo(7);
}

@Test
void wheel_moves_one_line_and_uses_minecraft_scroll_direction() {
    assertThat(AbstractScrollableTextScreen.scrollOffsetAfter(0, 12, 5, -1)).isEqualTo(1);
    assertThat(AbstractScrollableTextScreen.scrollOffsetAfter(7, 12, 5, 1)).isEqualTo(6);
}

@Test
void viewport_hit_test_excludes_edges_after_the_viewport() {
    AbstractScrollableTextScreen.TextViewport viewport =
            new AbstractScrollableTextScreen.TextViewport(12, 24, 152, 103, 0.85F, 10);

    assertThat(AbstractScrollableTextScreen.containsViewport(viewport, 0, 0, 12, 24)).isTrue();
    assertThat(AbstractScrollableTextScreen.containsViewport(viewport, 0, 0, 164, 126)).isFalse();
}
```

The exact helper signatures used by the tests are:

```java
static int visibleLineCount(int viewportHeight, float scale, int lineSpacing, int fontLineHeight)
static int maxScrollOffset(int lineCount, int visibleLineCount)
static boolean hasScrollableOverflow(int lineCount, int visibleLineCount)
static int clampScrollOffset(int offset, int lineCount, int visibleLineCount)
static int scrollOffsetAfter(int offset, int lineCount, int visibleLineCount, double deltaY)
static boolean containsViewport(TextViewport viewport, int left, int top, double mouseX, double mouseY)
```

- [ ] **Step 2: Implement the minimal shared base class**

Declare the package-private abstract class with the existing menu bound:

```java
abstract class AbstractScrollableTextScreen<M extends AbstractContainerMenu>
        extends AbstractContainerScreen<M> {

    protected record TextViewport(int x, int y, int width, int height,
                                  float scale, int lineSpacing) {}

    private int textScrollOffset;

    protected AbstractScrollableTextScreen(M menu, Inventory inventory,
                                           Component title, int imageWidth, int imageHeight) {
        super(menu, inventory, title, imageWidth, imageHeight);
    }

    protected abstract TextViewport scrollableTextViewport();

    protected abstract int scrollableTextLineCount();

    protected boolean handleAdditionalScroll(double mouseX, double mouseY,
                                             double deltaX, double deltaY) {
        return false;
    }
}
```

Implement the shared calculations with these rules:

- Treat `TextViewport.height` and `lineSpacing` as local GUI units.
- Compute scaled height with `floor(viewportHeight * scale)`.
- Compute scaled font height and row spacing with `ceil(value * scale)` and clamp both to at least one pixel.
- Use `max(fontHeight, rowSpacing)` as the effective row stride so rows cannot overlap.
- Return at least one visible line.
- Clamp the private offset using `max(0, lineCount - visibleLineCount)`.
- Interpret negative `deltaY` as moving down one line, matching the existing factory list behavior.

Add these protected helpers for subclasses:

```java
protected final int visibleTextLineCount()
protected final int firstVisibleTextLine()
protected final int lastVisibleTextLineExclusive()
protected final boolean isTextLineVisible(int lineIndex)
protected final int visibleTextRow(int lineIndex)
protected final int textLineY(int visibleRow)
protected final void clampTextScrollOffset()
protected final void resetTextScrollOffset()
```

`visibleTextLineCount()` uses `font.lineHeight` and the current `TextViewport`. `textLineY(visibleRow)` returns `viewport.y() + visibleRow * viewport.lineSpacing()` so each subclass can preserve its existing pose scaling and local/global coordinate conventions.

Implement `mouseScrolled` as follows:

```java
@Override
public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
    TextViewport viewport = scrollableTextViewport();
    int lineCount = scrollableTextLineCount();
    int visibleLines = visibleLineCount(viewport.height(), viewport.scale(),
            viewport.lineSpacing(), font.lineHeight);
    if (containsViewport(viewport, leftPos, topPos, mouseX, mouseY)) {
        if (!hasScrollableOverflow(lineCount, visibleLines)) return false;
        textScrollOffset = scrollOffsetAfter(textScrollOffset, lineCount, visibleLines, deltaY);
        return true;
    }
    return handleAdditionalScroll(mouseX, mouseY, deltaX, deltaY)
            || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
}
```

Ensure `firstVisibleTextLine()` and `lastVisibleTextLineExclusive()` clamp the offset before returning indices, so menu snapshots that shrink cannot leave an invalid range.

- [ ] **Step 3: Run the shared focused tests**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.client.gui.ScrollableTextScreenTest --no-daemon
```

Expected: `ScrollableTextScreenTest` passes, with no changes to the two pre-existing `CombinedPortScreen` worktree files.

- [ ] **Step 4: Commit the shared abstraction**

```bash
git add src/main/java/cn/howxu/mmcr/client/gui/AbstractScrollableTextScreen.java \
        src/test/java/cn/howxu/mmcr/client/gui/ScrollableTextScreenTest.java
git commit -m "feat: add shared scrollable text screen"
```

---

### Task 2: Migrate The Ordinary Controller Screen

**Files:**

- Modify: `src/main/java/cn/howxu/mmcr/client/gui/MachineControllerScreen.java`
- Modify: `src/test/java/cn/howxu/mmcr/client/gui/MenuScreenTest.java`

**Interfaces:**

- Consumes the shared `TextViewport` and protected visible-line helpers from Task 1.
- Produces a scrollable list containing the detail rows after the fixed title and fixed two-color status row.

- [ ] **Step 1: Define the controller detail viewport and logical row list**

Change the superclass to `AbstractScrollableTextScreen<MachineControllerMenu>` and add the two required methods:

```java
@Override
protected TextViewport scrollableTextViewport() {
    int bodyY = titleLabelY + DETAIL_LINE_SPACING * 2;
    int bottom = IMAGE_HEIGHT - PLAYER_INVENTORY_HEIGHT_WITH_HOTBAR
            - RECIPE_LOCK_BUTTON_SIZE - 12 - 4;
    return new TextViewport(titleLabelX, bodyY, IMAGE_WIDTH - titleLabelX - 8,
            Math.max(1, bottom - bodyY), DETAIL_SCALE, DETAIL_LINE_SPACING);
}

@Override
protected int scrollableTextLineCount() {
    return detailLines(menu).size();
}
```

Extract the rows currently drawn after the status line into a package-private `static List<ControllerStatusLine> detailLines(MachineControllerMenu menu)`. Preserve this exact order:

1. Valid machine levels from `menu.foundLevelIds()`.
2. Last failure, when present.
3. Host/module state lines.
4. Parallel slot count, when positive and formed.
5. Parallelism.
6. Active recipe progress, when the total tick is positive.
7. Redstone paused line, when paused.

Keep the title and the existing status label/status value pair fixed so their separate colors and alignment do not change.

- [ ] **Step 2: Render only the visible detail rows**

Keep `extractLabels` and its existing `DETAIL_SCALE` pose. Draw the title and status as before. Replace the unbounded detail loops with:

```java
List<ControllerStatusLine> lines = detailLines(menu);
clampTextScrollOffset();
int first = firstVisibleTextLine();
int last = lastVisibleTextLineExclusive();
for (int index = first; index < last; index++) {
    ControllerStatusLine line = lines.get(index);
    int y = textLineY(visibleTextRow(index));
    graphics.text(font, line.text(), x, y, line.color(), true);
}
```

Use the existing transformed `x` coordinate and pose scaling. Do not change `levelLine`, failure localization, status colors, recipe lock behavior or background rendering.

- [ ] **Step 3: Add ordinary-controller regression assertions**

Add tests in `MenuScreenTest` for the extracted detail-line behavior using a client `MachineControllerMenu` snapshot. Assert that level, failure, module, parallel and progress rows retain their existing order and that an empty detail list has a zero maximum scroll offset through the shared helper.

Keep the existing module-status, recipe-lock and progress-percent tests unchanged.

- [ ] **Step 4: Run the focused controller tests**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.client.gui.MenuScreenTest --no-daemon
```

Expected: all existing ordinary-controller assertions and the new detail-row assertions pass.

- [ ] **Step 5: Commit the ordinary-controller migration**

```bash
git add src/main/java/cn/howxu/mmcr/client/gui/MachineControllerScreen.java \
        src/test/java/cn/howxu/mmcr/client/gui/MenuScreenTest.java
git commit -m "feat: scroll ordinary controller details"
```

---

### Task 3: Migrate The Factory Controller Without Merging Its Two Scroll Areas

**Files:**

- Modify: `src/main/java/cn/howxu/mmcr/client/gui/FactoryControllerScreen.java`
- Modify: `src/test/java/cn/howxu/mmcr/client/gui/FactoryControllerScreenTest.java`

**Interfaces:**

- Consumes `AbstractScrollableTextScreen` detail scrolling from Task 1.
- Produces `handleAdditionalScroll` behavior for the existing left thread list.

- [ ] **Step 1: Define the factory detail viewport and detail rows**

Change the superclass to `AbstractScrollableTextScreen<FactoryControllerMenu>`. Use the existing factory detail start at `x = 113` and the detail body start immediately after the fixed title/status rows:

```java
@Override
protected TextViewport scrollableTextViewport() {
    int bodyY = 12 + DETAIL_LINE_SPACING * 2;
    int bottom = IMAGE_HEIGHT - PLAYER_INVENTORY_HEIGHT_WITH_HOTBAR
            - RECIPE_LOCK_BUTTON_SIZE - 12 - 4;
    return new TextViewport(113, bodyY, IMAGE_WIDTH - 113 - 8,
            Math.max(1, bottom - bodyY), DETAIL_TEXT_SCALE, DETAIL_LINE_SPACING);
}

@Override
protected int scrollableTextLineCount() {
    return detailLines(menu).size();
}
```

Extract the existing detail rows after the fixed status row into `static List<ControllerStatusLine> detailLines(FactoryControllerMenu menu)`, preserving the current order: levels, selected failure, parallel slots, parallelism, redstone pause, factory thread count and selected progress.

- [ ] **Step 2: Render the visible right-side detail rows**

Keep the fixed machine title and selected-thread status rendering. Replace only the unbounded right-side detail drawing with the shared visible range. Clamp the new detail offset every frame before indexing the list. Keep the existing left thread row overlays, selected-thread behavior, recipe lock button and scrollbar handle unchanged.

- [ ] **Step 3: Move left-thread wheel handling into the additional-scroll hook**

Remove the existing unconditional `mouseScrolled` override. Implement:

```java
@Override
protected boolean handleAdditionalScroll(double mouseX, double mouseY,
                                         double deltaX, double deltaY) {
    if (!mouseOverThreadList((int) mouseX, (int) mouseY)) return false;
    scrollOffset = clampScrollOffset(scrollOffset - (int) Math.signum(deltaY),
            menu.threads().size());
    return true;
}
```

`mouseOverThreadList` must use `THREAD_ROW_X`, `THREAD_ROW_Y`, `THREAD_ROW_WIDTH`, `THREAD_ROW_HEIGHT`, `THREAD_ROW_GAP` and `VISIBLE_THREADS`, translated by `leftPos` and `topPos`. The right detail viewport must be checked by the base class before this hook, so one wheel event cannot move both offsets.

- [ ] **Step 4: Add factory scroll regression assertions**

Extend `FactoryControllerScreenTest` with assertions for:

- The detail line count remaining independent from `clampScrollOffset` used by the left thread list.
- A detail viewport with content larger than its visible range producing a positive maximum offset.
- The existing selected failure behavior still hiding the aggregate failure for an active selected thread.

- [ ] **Step 5: Run the focused factory tests**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.client.gui.FactoryControllerScreenTest --no-daemon
```

Expected: all factory controller tests pass and the existing left-thread helper tests remain valid.

- [ ] **Step 6: Commit the factory-controller migration**

```bash
git add src/main/java/cn/howxu/mmcr/client/gui/FactoryControllerScreen.java \
        src/test/java/cn/howxu/mmcr/client/gui/FactoryControllerScreenTest.java
git commit -m "feat: scroll factory controller details"
```

---

### Task 4: Migrate The Extended Port Text Pages

**Files:**

- Modify: `src/main/java/cn/howxu/mmcr/client/gui/AbstractPortScreen.java`
- Modify: `src/main/java/cn/howxu/mmcr/client/gui/ExtendedItemScreen.java`
- Modify: `src/main/java/cn/howxu/mmcr/client/gui/ExtendedFluidScreen.java`
- Modify: `src/main/java/cn/howxu/mmcr/client/gui/ExtendedCombinedScreen.java`
- Modify: `src/test/java/cn/howxu/mmcr/client/gui/ExtendedPortScreenTest.java`

**Interfaces:**

- Consumes the shared `TextViewport`, visible-line range and row-position helpers from Task 1.
- Preserves `AbstractPortScreen` tooltip registration and Auto IO page behavior.

- [ ] **Step 1: Give `AbstractPortScreen` the shared text viewport**

Change `AbstractPortScreen<M>` to extend `AbstractScrollableTextScreen<M>`. Update its constructor to pass the existing 176-pixel text UI width to the new base constructor:

```java
protected AbstractPortScreen(M menu, Inventory inventory, Component title, int imageHeight) {
    super(menu, inventory, title, 176, imageHeight);
    inventoryLabelY = HIDDEN_INVENTORY_LABEL_Y;
}
```

Add shared constants based on the existing extended-menu slot layout:

```java
protected static final int TEXT_VIEW_X = 12;
protected static final int TEXT_VIEW_Y = 24;
protected static final int TEXT_VIEW_BOTTOM = 127;
```

Implement the common viewport using `TEXT_DETAIL_SCALE`, `TEXT_DETAIL_LINE_SPACING`, `imageWidth` and `imageHeight`:

```java
@Override
protected final TextViewport scrollableTextViewport() {
    return new TextViewport(TEXT_VIEW_X, TEXT_VIEW_Y,
            imageWidth - TEXT_VIEW_X - 8,
            Math.min(TEXT_VIEW_BOTTOM, imageHeight) - TEXT_VIEW_Y,
            TEXT_DETAIL_SCALE, TEXT_DETAIL_LINE_SPACING);
}
```

The bottom `127` leaves four pixels before the extended-menu player inventory row at `y = 131`. The Auto IO page remains outside this text viewport and continues to use its existing button and slot layout.

- [ ] **Step 2: Migrate the item and fluid text pages**

Make `ExtendedItemScreen` and `ExtendedFluidScreen` implement `scrollableTextLineCount()` as `1` for empty storage and `1 + nonEmptyEntries.size()` for non-empty storage. Keep `displayLines` and `tooltipLines` public/package-private behavior unchanged.

In each `extractLabels` method, keep the title fixed, then use logical line indices:

- Empty storage: line index `0` is the green empty line.
- Non-empty storage: line index `0` is the green `stored` header and each entry starts at index `1`.

Only call `graphics.text` when `isTextLineVisible(lineIndex)` is true. Compute the local y coordinate from `textLineY(visibleTextRow(lineIndex))`. Register an entry tooltip at the same local y and preserve the existing scaled width calculation. Call `clampTextScrollOffset()` after building the entries and before drawing.

- [ ] **Step 3: Migrate the combined text page with one logical index across both sections**

Implement `scrollableTextLineCount()` as:

```java
return 1 + nonEmptyItems(menu.itemEntries()).size()
        + 1 + nonEmptyFluids(menu.fluidEntries()).size();
```

Refactor `drawItems` and `drawFluids` to pass and return a logical line index rather than an absolute y coordinate. The items section consumes its section header or empty line, followed by item entries; the fluids section continues from the next index. For each visible entry, use `visibleTextRow(lineIndex)` for y and register its existing tooltip. Preserve the item-before-fluid order and all existing `displayLines` output used by tests.

- [ ] **Step 4: Add extended-port scroll and tooltip regression assertions**

Extend `ExtendedPortScreenTest` with tests that assert:

- Empty item and fluid storage has one logical line.
- A combined port with two item entries and one fluid entry has five logical lines, including both section lines.
- Existing display-line order and exact tooltip contents remain unchanged.
- The shared visible range can select a later entry without changing the logical entry order.

- [ ] **Step 5: Run the focused extended-port tests**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.client.gui.ExtendedPortScreenTest --no-daemon
```

Expected: all existing and new extended-port text tests pass, including the `guicontroller_large.png` texture assertions.

- [ ] **Step 6: Commit the extended-port migration**

```bash
git add src/main/java/cn/howxu/mmcr/client/gui/AbstractPortScreen.java \
        src/main/java/cn/howxu/mmcr/client/gui/ExtendedItemScreen.java \
        src/main/java/cn/howxu/mmcr/client/gui/ExtendedFluidScreen.java \
        src/main/java/cn/howxu/mmcr/client/gui/ExtendedCombinedScreen.java \
        src/test/java/cn/howxu/mmcr/client/gui/ExtendedPortScreenTest.java
git commit -m "feat: scroll extended port text pages"
```

---

### Task 5: Full Verification And Change Review

**Files:**

- Verify only the files listed in Tasks 1-4 are changed by this feature.
- Do not stage any existing `Combined*` file unless the user separately requests it, including `CombinedPortScreen.java`, `CombinedPortScreenTest.java`, `CombinedPortMenu.java` and `CombinedPortMenuTest.java`.

- [ ] **Step 1: Run the complete unit test suite**

Run and wait for completion:

```bash
./gradlew test --no-daemon
```

Expected: Gradle exits successfully and all unit tests pass.

- [ ] **Step 2: Run the complete GameTest server suite**

Only after the unit test command completes, run:

```bash
./gradlew runGameTestServer --no-daemon
```

Expected: Gradle exits successfully and the GameTest server reports no failed tests.

- [ ] **Step 3: Review the final diff and worktree**

Run:

```bash
git diff --check
git status --short
```

Confirm that:

- The ordinary controller, factory controller and extended port text pages share the same scroll state/input abstraction.
- The factory left thread list and right detail area have separate offsets.
- Text outside the configured viewport is not drawn.
- Existing colors, title positions, tooltip content, Auto IO behavior and texture paths remain unchanged.
- The user's pre-existing `CombinedPortScreen` modifications remain present and unstaged by this feature.
