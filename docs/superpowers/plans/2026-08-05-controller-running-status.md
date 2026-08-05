# Controller Running Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a distinct running status and a percentage progress line with one dot per 5% while a controller recipe is active.

**Architecture:** Keep the change client-side in `MachineMenuScreen`, reusing the active recipe and tick counter already read for the existing progress line. Add small package-visible helpers so the progress and dot behavior is covered by the existing GUI unit test.

**Tech Stack:** Java, NeoForge client GUI, JUnit 5, AssertJ, Gradle.

## Global Constraints

- Use the existing controller menu state; do not add new network sync fields.
- Preserve the current controller GUI layout and colors.
- Keep changes minimal and avoid unrelated refactoring.

---

### Task 1: Controller Running Text

**Files:**
- Modify: `src/test/java/cn/howxu/mmcr/client/gui/MenuScreenTest.java`
- Modify: `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java`

**Interfaces:**
- Consumes: `MachineMenuScreen.controllerStatusKey(boolean formed, boolean active)`.
- Produces: `MachineMenuScreen.progressPercent(int current, int total)` and `MachineMenuScreen.progressDots(int percent)`.

- [ ] **Step 1: Write failing tests**

```java
@Test
void controller_status_key_reports_running_when_active() {
    assertThat(MachineMenuScreen.controllerStatusKey(true, true)).isEqualTo("gui.mmcr.controller.running");
}

@Test
void progress_dots_add_one_dot_per_five_percent() {
    assertThat(MachineMenuScreen.progressPercent(35, 100)).isEqualTo(35);
    assertThat(MachineMenuScreen.progressDots(0)).isEmpty();
    assertThat(MachineMenuScreen.progressDots(4)).isEmpty();
    assertThat(MachineMenuScreen.progressDots(5)).isEqualTo(".");
    assertThat(MachineMenuScreen.progressDots(35)).isEqualTo(".......");
    assertThat(MachineMenuScreen.progressDots(100)).isEqualTo("....................");
}
```

- [ ] **Step 2: Verify red**

Run: `./gradlew test --tests cn.howxu.mmcr.client.gui.MenuScreenTest --no-daemon`
Expected: FAIL because active status is still `formed` and progress helper methods do not exist.

- [ ] **Step 3: Implement minimal code**

Change active status to `gui.mmcr.controller.running`, compute percent with clamped current ticks, append `progressDots(percent)` to the progress translation argument.

- [ ] **Step 4: Verify green**

Run: `./gradlew test --tests cn.howxu.mmcr.client.gui.MenuScreenTest --no-daemon`
Expected: PASS.

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: BUILD SUCCESSFUL.
