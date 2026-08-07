# Task 1 Report

## Result

Implemented the bounded directional machine recipe layout planner and updated the JEI recipe category to consume its `RegionPlan` API.

## Changes

- `MachineRecipeLayout` now creates bounded 4x8 input and output regions.
- Fluid entries precede item entries within each region.
- Overflow reserves the final visible cell as an ellipsis plan (`entry == null`) and exposes hidden entries plus overflow flags.
- `MachineRecipeCategory` now dispatches non-null planned entries by `EntryPlan.kind()` and `EntryPlan.index()` without introducing a unified slot abstraction.
- Layout tests cover ordered fluid/item inputs, four-column wrapping, input overflow, and output overflow isolation.

## Test Results

### Red

Command:

```bash
./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeLayoutTest --no-daemon
```

Result: `BUILD FAILED in 12s` during `:compileTestJava`.

Expected missing-layout API errors were present, including:

```text
找不到符号: 方法 inputs()
找不到符号: 方法 outputs()
找不到符号: 类 EntryPlan
找不到符号: 方法 hasInputOverflow()
找不到符号: 方法 hasOutputOverflow()
```

The initial test also had one unrelated generic inference error for `List<ItemIngredient>` passed to `List<MachineIngredient>`; the fixture was explicitly typed as `MachineIngredient` before implementing production code.

### Green

Command:

```bash
./gradlew test --tests cn.howxu.mmcr.compat.jei.MachineRecipeLayoutTest --no-daemon
```

Output:

```text
BUILD SUCCESSFUL in 11s
17 actionable tasks: 3 executed, 14 up-to-date
```

## Concerns

- The ellipsis plan is intentionally skipped by `MachineRecipeCategory`; visual rendering or interaction for it is not implemented because that belongs outside task 1's layout-planning scope.
- Gradle emits existing deprecation notices, but the focused test completes successfully.
