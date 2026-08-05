# Energy IO MMCE Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align controller energy tick behavior with MMCE-style aggregate input simulation and ordered commit.

**Architecture:** Add `EnergyRecipeIo` as a focused helper under `cn.howxu.mmcr.api.recipe.helper`. `RecipeCraftingContext` keeps component discovery and delegates energy tick consumption to the helper.

**Tech Stack:** Java 25, NeoForge `IEnergyStorage`, JUnit 5, AssertJ, Gradle.

## Global Constraints

- Preserve existing recipe state-machine behavior and controller tick scheduling.
- Only change energy input semantics: multi-hatch aggregation, simulate-before-commit, no mutation on shortage.
- Do not introduce full MMCE `ComponentRequirement` hierarchy or recipe parallelism.
- Keep changes minimal and follow existing package/style conventions.

---

### Task 1: Energy IO Helper

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIo.java`
- Test: `src/test/java/cn/howxu/mmcr/api/recipe/helper/EnergyRecipeIoTest.java`

**Interfaces:**
- Consumes: `List<? extends IEnergyStorage>`, `int requiredFe`, `int multiplier`
- Produces: `EnergyRecipeIo.consumeInputs(...) -> boolean`

- [ ] Write failing tests for single hatch success, multi-hatch aggregation, insufficient aggregate no mutation, and transfer-limit-aware simulation.
- [ ] Run `./gradlew test --tests cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIoTest --no-daemon` and verify failures are due to missing helper.
- [ ] Implement `EnergyRecipeIo` with aggregate simulated extraction before any real extraction.
- [ ] Re-run the focused test and verify it passes.

### Task 2: Context Integration

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`

**Interfaces:**
- Consumes: `EnergyRecipeIo.consumeInputs(...)` from Task 1
- Produces: `ioTick` and energy `simulateInputs` using all live input hatches in controller component order

- [ ] Replace `findAndCheckEnergyHatch` usage for energy ticks with `liveEnergyInputs()` plus `EnergyRecipeIo`.
- [ ] Update energy simulation to aggregate across all live input hatches without mutating them.
- [ ] Remove the obsolete single-hatch finder if no longer used.
- [ ] Run `./gradlew compileJava --no-daemon` and fix any compile errors.

### Task 3: Verification

**Files:**
- Verify only; no planned production changes.

- [ ] Run focused helper tests.
- [ ] Run `./gradlew test --no-daemon` or the narrowest stable project test set if full tests are too slow.
- [ ] Run `./gradlew compileJava --no-daemon` after final edits.

## Self-Review

- Spec coverage: helper aggregation, simulate-before-commit, ordered commit, empty-list shortage, and transfer-limit shortage are covered.
- Placeholder scan: no TBD/TODO/fill-in placeholders remain.
- Type consistency: helper consumes `IEnergyStorage`; context maps hatches to their exposed energy storages.
