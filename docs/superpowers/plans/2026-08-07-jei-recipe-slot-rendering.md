# JEI Recipe Slot Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render machine recipe item inputs with their required quantity and retain the correct output item stack in JEI recipe slots.

**Architecture:** Keep `MachineRecipeDisplay` as the immutable JEI-facing representation of runtime recipe data. In `MachineRecipeCategory`, convert each input `Ingredient` to its displayed item stacks with the corresponding required count before handing them to JEI, while output slots continue receiving the runtime output `ItemStack`.

**Tech Stack:** Java 21, NeoForge 26.1.2, JEI API, JUnit 5, AssertJ, Gradle.

## Global Constraints

- Preserve current runtime recipe and modifier behavior.
- Do not add dependencies or unrelated refactors.
- Use JEI slot APIs so item stack counts render through its standard renderer.
- Validate Java changes with Gradle; do not run `runClient`.

---

### Task 1: Cover JEI Display Ingredient Data

**Files:**
- Modify: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplay.java`

**Interfaces:**
- Consumes: `MachineRecipeDisplay.from(MachineRecipe recipe)`.
- Produces: `MachineRecipeDisplay#itemInputs()` plus `itemInputCounts()` preserving an input's item identity and required count; `itemOutputs()` preserving runtime output identity and count.

- [ ] **Step 1: Write the failing test**

Add a recipe with eight coal as its item input and an iron nugget output, then assert the display retains the coal ingredient, count `8`, output item `Items.IRON_NUGGET`, and output count `4`.

```java
assertThat(display.itemInputs().getFirst().test(new ItemStack(Items.COAL))).isTrue();
assertThat(display.itemInputCounts()).containsExactly(8);
assertThat(display.itemOutputs()).singleElement().satisfies(output -> {
    assertThat(output.is(Items.IRON_NUGGET)).isTrue();
    assertThat(output.getCount()).isEqualTo(4);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest --no-daemon`

Expected: the quantity assertion fails while the test fixture still uses its former input amount.

- [ ] **Step 3: Write minimal implementation**

Keep `MachineRecipeDisplay.from` collecting `item.item()` and `item.count()` for each input requirement and copying each runtime `MachineOutput.ItemOutput#stack()` into `itemOutputs`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest --no-daemon`

Expected: PASS.

### Task 2: Render Sized Item Inputs in JEI Slots

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java`
- Test: `src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java`

**Interfaces:**
- Consumes: `MachineRecipeDisplay#itemInputs()`, `MachineRecipeDisplay#itemInputCounts()`, and `MachineRecipeDisplay#itemOutputs()`.
- Produces: JEI input slots populated with every candidate `ItemStack` copied at the required count; output slots populated with their runtime output stack.

- [ ] **Step 1: Write the failing test**

Use the Task 1 display fixture to require eight coal, asserting the display model exposes the precise count which the category must use when creating JEI item stacks.

```java
assertThat(display.itemInputCounts()).containsExactly(8);
assertThat(display.itemInputs().getFirst().getItems())
        .allSatisfy(stack -> assertThat(stack.is(Items.COAL)).isTrue());
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest --no-daemon`

Expected: FAIL before the fixture is changed from its old two-item input.

- [ ] **Step 3: Write minimal implementation**

Replace direct `Ingredient` insertion in `MachineRecipeCategory#setRecipe` with:

```java
Ingredient ingredient = recipe.itemInputs().get(slot.index());
int count = recipe.itemInputCounts().get(slot.index());
builder.addInputSlot(slot.x(), slot.y())
        .setStandardSlotBackground()
        .addItemStacks(ingredient.getItems().stream()
                .map(stack -> stack.copyWithCount(count))
                .toList());
```

Keep output slot insertion as `.add(recipe.itemOutputs().get(slot.index()))` so the output item's identity, components, and amount reach JEI unchanged.

- [ ] **Step 4: Run tests and compile**

Run: `./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeDisplayTest --no-daemon`

Expected: PASS.

Run: `./gradlew compileJava --no-daemon`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/howxu/mmcr/compat/jei/MachineRecipeCategory.java src/test/java/cn/howxu/mmcr/compat/jei/MachineRecipeDisplayTest.java docs/superpowers/plans/2026-08-07-jei-recipe-slot-rendering.md
git commit -m "fix(jei): render machine recipe item counts"
```
