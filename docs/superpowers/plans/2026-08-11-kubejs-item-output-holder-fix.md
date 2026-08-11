# KubeJS Item Output Holder Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure KubeJS-declared ordinary and chanced machine item outputs carry registry-backed holders and can be saved in item output buses.

**Architecture:** `MachineRecipeBuilderJS` currently creates direct item holders for its two non-component output methods. Replace both with the normal registered-item `ItemStack` constructor. Add a focused unit test to the existing KubeJS compatibility test package that checks the resulting holders have registry keys.

**Tech Stack:** Java 21, Minecraft 26.1.2, NeoForge, JUnit Jupiter, AssertJ, Gradle.

## Global Constraints

- Change only `MachineRecipeBuilderJS` ordinary and chanced output construction plus its focused regression test.
- Do not change the component-output path, which decodes via `ItemStack.CODEC` and KubeJS world registry ops.
- Preserve unrelated dirty-worktree changes.
- Validate Java changes with Gradle; do not run `./gradlew runClient --no-daemon`.

---

### Task 1: Preserve Registry-Backed Output Holders

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java:16-17,93-102`
- Test: `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJSTest.java`

**Interfaces:**
- Consumes: `MachineRecipeBuilderJS.itemOutput(String itemId, int count)` and `chancedItemOutput(String itemId, int count, float chance)`.
- Produces: entries in `MachineRecipeBuilderJS.outputs` whose `ItemStack.typeHolder().unwrapKey()` is present.

- [ ] **Step 1: Write the failing test**

Add to `MachineBuilderJSTest`:

```java
@Test
void item_outputs_use_registry_backed_holders() {
    var builder = new MachineRecipeBuilderJS(MMCR.id("holder_test"))
            .itemOutput("mmcr:item_output_bus", 1)
            .chancedItemOutput("mmcr:item_input_bus", 2, 0.5F);

    assertThat(builder.outputs)
            .extracting(stack -> stack.typeHolder().unwrapKey())
            .allMatch(java.util.Optional::isPresent);
    assertThat(builder.outputs)
            .extracting(stack -> stack.typeHolder().unwrapKey().orElseThrow().identifier())
            .containsExactly(MMCR.id("item_output_bus"), MMCR.id("item_input_bus"));
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest.item_outputs_use_registry_backed_holders --no-daemon
```

Expected: FAIL because each stack uses `Holder.direct(...)`, so `unwrapKey()` is empty.

- [ ] **Step 3: Implement the minimal production fix**

In `MachineRecipeBuilderJS`, replace both direct-holder constructors:

```java
outputs.add(new ItemStack(Holder.direct(item(itemId), DataComponentMap.EMPTY), count));
```

with:

```java
outputs.add(new ItemStack(item(itemId), count));
```

Remove the now-unused imports:

```java
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest.item_outputs_use_registry_backed_holders --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Run compile verification**

Run:

```bash
./gradlew compileJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit only task files**

```bash
git add docs/superpowers/specs/2026-08-11-kubejs-item-output-holder-design.md docs/superpowers/plans/2026-08-11-kubejs-item-output-holder-fix.md src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java src/test/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJSTest.java
git commit -m "fix: preserve KubeJS item output holders"
```
