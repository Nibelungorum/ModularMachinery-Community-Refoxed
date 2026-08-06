# Port Formation Requirements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add machine-definition-driven port count requirements that can prevent a matching multiblock from forming when required ports are missing or excessive.

**Architecture:** Add a focused `PortRequirementSpec` value object in the machine API, expose it from `Machine`, and store it on `DynamicMachine`. `MachineControllerBlockEntity` keeps `StructureMatcher` unchanged, then counts live `IOPortBlockEntity` instances in the matched rotated pattern before mutating controller formed state.

**Tech Stack:** Java 25, Minecraft 26.1.2, NeoForge 7.1.38 userdev, JUnit 5, AssertJ, Gradle.

## Global Constraints

- Do not infer required ports from recipes.
- Do not change `BlockArray` pattern matching semantics.
- Do not require every machine to use input/output item, fluid, and energy ports.
- Do not implement MMCE `ComponentRestriction` compatibility unless a future config importer needs it.
- Do not add broad GUI work in the first implementation pass.
- Preserve current behavior for machines that do not declare port requirements.
- Use `IOPortKind.id()` strings consistently; do not compare localized names.
- Keep controller mutation clear: no `setFormed(true)`, no `foundMachine` assignment, and no `components` mutation until requirements pass.
- Follow existing code style and keep changes minimal.

---

## File Structure

- Create `src/main/java/cn/howxu/mmcr/api/machine/PortRequirementSpec.java`: Immutable requirement model, builder, validation result, and count wrapper.
- Modify `src/main/java/cn/howxu/mmcr/api/machine/Machine.java`: Expose default `portRequirements()` returning no requirements.
- Modify `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`: Add `PortRequirementSpec` record component and compatibility constructors.
- Modify `src/main/java/org/nibelungorum/DefaultMachines.java`: Declare built-in blast furnace requirements at the machine definition site.
- Modify `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`: Count matched pattern ports before formation and reject unsatisfied candidates.
- Test `src/test/java/cn/howxu/mmcr/api/machine/PortRequirementSpecTest.java`: Pure validation tests for min/max/no requirements.
- Modify `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`: Assert built-in blast furnace requirements are present.
- Modify `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`: Add focused controller formation tests using existing `LevelStub` and unsafe test helpers.
- Modify `src/test/java/cn/howxu/mmcr/LevelStub.java`: Add a combined block/block-entity test factory and support `setBlock(...)` so controller formation tests can observe formed state changes.

---

### Task 1: Port Requirement API

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/machine/PortRequirementSpec.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/Machine.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`
- Test: `src/test/java/cn/howxu/mmcr/api/machine/PortRequirementSpecTest.java`

**Interfaces:**
- Consumes: `Machine.registryName()`, `MachineControllerSpec.defaultsFor(Identifier)`.
- Produces: `PortRequirementSpec.none()`, `PortRequirementSpec.builder()`, `PortRequirementSpec.validate(PortRequirementSpec.PortCounts)`, `PortRequirementSpec.PortCounts.of(Map<String, Integer>)`, `Machine.portRequirements()`, `DynamicMachine(..., PortRequirementSpec)`.

- [ ] **Step 1: Write pure validation tests**

Create `src/test/java/cn/howxu/mmcr/api/machine/PortRequirementSpecTest.java`:

```java
package cn.howxu.mmcr.api.machine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortRequirementSpecTest {

    @Test
    void none_has_no_failure_for_empty_counts() {
        assertThat(PortRequirementSpec.none().validate(PortRequirementSpec.PortCounts.empty())).isEmpty();
    }

    @Test
    void min_requirement_reports_missing_port() {
        PortRequirementSpec spec = PortRequirementSpec.builder()
                .min("energy_input_hatch", 1)
                .build();

        var failure = spec.validate(PortRequirementSpec.PortCounts.empty());

        assertThat(failure).hasValueSatisfying(value -> {
            assertThat(value.portId()).isEqualTo("energy_input_hatch");
            assertThat(value.reason()).isEqualTo(PortRequirementSpec.FailureReason.MISSING);
            assertThat(value.actual()).isZero();
            assertThat(value.requiredMin()).isEqualTo(1);
            assertThat(value.requiredMax()).isEmpty();
        });
    }

    @Test
    void min_requirement_passes_when_actual_count_is_enough() {
        PortRequirementSpec spec = PortRequirementSpec.builder()
                .min("energy_input_hatch", 1)
                .build();

        assertThat(spec.validate(PortRequirementSpec.PortCounts.of(Map.of("energy_input_hatch", 1)))).isEmpty();
    }

    @Test
    void max_requirement_reports_too_many_ports() {
        PortRequirementSpec spec = PortRequirementSpec.builder()
                .range("item_input_bus", 0, 1)
                .build();

        var failure = spec.validate(PortRequirementSpec.PortCounts.of(Map.of("item_input_bus", 2)));

        assertThat(failure).hasValueSatisfying(value -> {
            assertThat(value.portId()).isEqualTo("item_input_bus");
            assertThat(value.reason()).isEqualTo(PortRequirementSpec.FailureReason.TOO_MANY);
            assertThat(value.actual()).isEqualTo(2);
            assertThat(value.requiredMin()).isZero();
            assertThat(value.requiredMax()).hasValue(1);
        });
    }

    @Test
    void invalid_ranges_are_rejected() {
        assertThatThrownBy(() -> PortRequirementSpec.builder().min("item_input_bus", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("min");
        assertThatThrownBy(() -> PortRequirementSpec.builder().range("item_input_bus", 2, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max");
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.PortRequirementSpecTest --no-daemon`

Expected: FAIL because `PortRequirementSpec` does not exist.

- [ ] **Step 3: Implement `PortRequirementSpec`**

Create `src/main/java/cn/howxu/mmcr/api/machine/PortRequirementSpec.java`:

```java
package cn.howxu.mmcr.api.machine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Machine-level port count requirements checked before a matched structure forms.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PortRequirementSpec(Map<String, CountRange> requirements) {

    private static final PortRequirementSpec NONE = new PortRequirementSpec(Map.of());

    public PortRequirementSpec {
        if (requirements == null) throw new IllegalArgumentException("requirements null");
        requirements = Map.copyOf(requirements);
    }

    public static PortRequirementSpec none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return requirements.isEmpty();
    }

    public Optional<Failure> validate(PortCounts counts) {
        if (counts == null) throw new IllegalArgumentException("counts null");
        for (var entry : requirements.entrySet()) {
            String portId = entry.getKey();
            CountRange range = entry.getValue();
            int actual = counts.count(portId);
            if (actual < range.min()) {
                return Optional.of(new Failure(portId, actual, range.min(), range.max(), FailureReason.MISSING));
            }
            if (range.max().isPresent() && actual > range.max().getAsInt()) {
                return Optional.of(new Failure(portId, actual, range.min(), range.max(), FailureReason.TOO_MANY));
            }
        }
        return Optional.empty();
    }

    public record CountRange(int min, OptionalInt max) {
        public CountRange {
            if (min < 0) throw new IllegalArgumentException("min must be >= 0");
            if (max == null) throw new IllegalArgumentException("max null");
            if (max.isPresent() && max.getAsInt() < min) throw new IllegalArgumentException("max must be >= min");
        }

        public static CountRange min(int min) {
            return new CountRange(min, OptionalInt.empty());
        }

        public static CountRange range(int min, int max) {
            return new CountRange(min, OptionalInt.of(max));
        }
    }

    public record Failure(String portId, int actual, int requiredMin, OptionalInt requiredMax, FailureReason reason) {}

    public enum FailureReason {
        MISSING,
        TOO_MANY
    }

    public record PortCounts(Map<String, Integer> counts) {
        public PortCounts {
            if (counts == null) throw new IllegalArgumentException("counts null");
            Map<String, Integer> copy = new LinkedHashMap<>();
            for (var entry : counts.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) throw new IllegalArgumentException("port id blank");
                if (entry.getValue() == null || entry.getValue() < 0) throw new IllegalArgumentException("count must be >= 0");
                copy.put(entry.getKey(), entry.getValue());
            }
            counts = Map.copyOf(copy);
        }

        public static PortCounts empty() {
            return new PortCounts(Map.of());
        }

        public static PortCounts of(Map<String, Integer> counts) {
            return new PortCounts(counts);
        }

        public int count(String portId) {
            return counts.getOrDefault(portId, 0);
        }
    }

    public static final class Builder {
        private final Map<String, CountRange> requirements = new LinkedHashMap<>();

        private Builder() {}

        public Builder min(String portId, int min) {
            return put(portId, CountRange.min(min));
        }

        public Builder range(String portId, int min, int max) {
            return put(portId, CountRange.range(min, max));
        }

        private Builder put(String portId, CountRange range) {
            if (portId == null || portId.isBlank()) throw new IllegalArgumentException("port id blank");
            requirements.put(portId, range);
            return this;
        }

        public PortRequirementSpec build() {
            if (requirements.isEmpty()) return none();
            return new PortRequirementSpec(requirements);
        }
    }
}
```

- [ ] **Step 4: Expose requirements on `Machine`**

Modify `src/main/java/cn/howxu/mmcr/api/machine/Machine.java` to add this method after `controller()`:

```java
    default PortRequirementSpec portRequirements() {
        return PortRequirementSpec.none();
    }
```

The full file should be:

```java
package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

public sealed interface Machine permits DynamicMachine {
    Identifier registryName();

    String localizedName();

    BlockArray pattern();

    MachineControllerSpec controller();

    default PortRequirementSpec portRequirements() {
        return PortRequirementSpec.none();
    }

    default RecipeFailureActions failureAction() {
        return RecipeFailureActions.getDefaultAction();
    }
}
```

- [ ] **Step 5: Store requirements on `DynamicMachine`**

Modify `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java` to:

```java
package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

public record DynamicMachine(
        Identifier registryName,
        String localizedName,
        BlockArray pattern,
        MachineControllerSpec controller,
        PortRequirementSpec portRequirements
) implements Machine {
    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern) {
        this(registryName, localizedName, pattern, MachineControllerSpec.defaultsFor(registryName), PortRequirementSpec.none());
    }

    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern, MachineControllerSpec controller) {
        this(registryName, localizedName, pattern, controller, PortRequirementSpec.none());
    }

    public DynamicMachine {
        if (registryName == null) throw new IllegalArgumentException("registryName null");
        if (localizedName == null) throw new IllegalArgumentException("localizedName null");
        if (pattern == null) throw new IllegalArgumentException("pattern null");
        if (controller == null) throw new IllegalArgumentException("controller null");
        if (portRequirements == null) throw new IllegalArgumentException("portRequirements null");
    }
}
```

- [ ] **Step 6: Run focused API tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.PortRequirementSpecTest --no-daemon`

Expected: PASS.

- [ ] **Step 7: Run compile check**

Run: `./gradlew compileJava --no-daemon`

Expected: PASS. If constructor call sites fail, update them to use the existing three-arg or four-arg overloads unless the caller needs explicit requirements.

- [ ] **Step 8: Commit**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/Machine.java \
        src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java \
        src/main/java/cn/howxu/mmcr/api/machine/PortRequirementSpec.java \
        src/test/java/cn/howxu/mmcr/api/machine/PortRequirementSpecTest.java
git commit -m "feat(machine): add port requirement spec"
```

---

### Task 2: Built-In Blast Furnace Requirements

**Files:**
- Modify: `src/main/java/org/nibelungorum/DefaultMachines.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`

**Interfaces:**
- Consumes: `PortRequirementSpec.builder().min(String, int).build()`, `Machine.portRequirements()`.
- Produces: Built-in blast furnace machine with `item_input_bus`, `item_output_bus`, and `energy_input_hatch` minimums set to `1`.

- [ ] **Step 1: Add failing default machine assertions**

In `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`, add imports:

```java
import cn.howxu.mmcr.registry.PortKinds;
```

In `ensureRegistered_registers_default_blast_furnace_once()`, after the controller assertion, add:

```java
        assertThat(machine.portRequirements().requirements())
                .containsKeys(PortKinds.ITEM_INPUT.id(), PortKinds.ITEM_OUTPUT.id(), PortKinds.ENERGY_INPUT.id());
        assertThat(machine.portRequirements().requirements().get(PortKinds.ITEM_INPUT.id()).min()).isEqualTo(1);
        assertThat(machine.portRequirements().requirements().get(PortKinds.ITEM_OUTPUT.id()).min()).isEqualTo(1);
        assertThat(machine.portRequirements().requirements().get(PortKinds.ENERGY_INPUT.id()).min()).isEqualTo(1);
```

- [ ] **Step 2: Run default machine test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.machine.DefaultMachinesTest --no-daemon`

Expected: FAIL because the blast furnace has no port requirements yet.

- [ ] **Step 3: Add requirements to `DefaultMachines`**

Modify imports in `src/main/java/org/nibelungorum/DefaultMachines.java`:

```java
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.registry.PortKinds;
```

Replace the return block at the end of `blastFurnace(...)` with:

```java
        PortRequirementSpec portRequirements = PortRequirementSpec.builder()
                .min(PortKinds.ITEM_INPUT.id(), 1)
                .min(PortKinds.ITEM_OUTPUT.id(), 1)
                .min(PortKinds.ENERGY_INPUT.id(), 1)
                .build();
        Machine definition = MachineDefinitions.get(BLAST_FURNACE_ID);
        return definition == null
                ? new DynamicMachine(BLAST_FURNACE_ID, "高炉", pattern, MachineControllerSpec.defaultsFor(BLAST_FURNACE_ID), portRequirements)
                : new DynamicMachine(BLAST_FURNACE_ID, "高炉", pattern, definition.controller(), portRequirements);
```

Also add this import because the replacement uses `MachineControllerSpec`:

```java
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
```

- [ ] **Step 4: Run default machine test**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.machine.DefaultMachinesTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/java/org/nibelungorum/DefaultMachines.java \
        src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java
git commit -m "feat(machine): require blast furnace ports"
```

---

### Task 3: Controller Formation Validation

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/LevelStub.java`

**Interfaces:**
- Consumes: `Machine.portRequirements()`, `PortRequirementSpec.PortCounts.of(Map<String, Integer>)`, `PortRequirementSpec.validate(...)`, `IOPortBlockEntity.kind().id()`.
- Produces: `MachineControllerBlockEntity.getLastFormationFailure()`, read-only port counting before `onStructureFormed(...)`, failed candidates return `false` without mutating formed state.

- [ ] **Step 1: Extend `LevelStub` for formation tests**

Modify `src/test/java/cn/howxu/mmcr/LevelStub.java`.

Add this factory after `createWithBlockEntities(...)`:

```java
    public static Level create(Map<BlockPos, Block> blocks, List<BlockEntity> blockEntities) {
        Level level = create(blocks);
        ((TestLevel) level).blockEntities = blockEntities.stream()
                .collect(java.util.stream.Collectors.toMap(BlockEntity::getBlockPos, be -> be));
        return level;
    }
```

Change `createFromStates(...)` so the test level keeps a mutable map:

```java
    private static Level createFromStates(Map<BlockPos, BlockState> blocks) {
        try {
            var level = (TestLevel) unsafe().allocateInstance(TestLevel.class);
            level.blocks = new HashMap<>(blocks);
            return level;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create level stub", e);
        }
    }
```

Add this override inside `TestLevel` after `getBlockEntity(...)`:

```java
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            blocks.put(pos, state);
            return true;
        }
```

- [ ] **Step 2: Add failing controller tests**

In `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`, add imports:

```java
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
```

Add these tests before helper methods:

```java
    @Test
    void matching_structure_without_requirements_forms() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("no_requirement_machine"), "No Requirement", pattern);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, net.minecraft.core.Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(controller.getFoundMachine()).isSameAs(machine);
        assertThat(controller.getLastFormationFailure()).isNull();
        assertThat(controller.isFormed()).isTrue();
    }

    @Test
    void matching_structure_missing_required_port_does_not_form() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("requires_energy_machine"),
                "Requires Energy",
                pattern,
                cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("requires_energy_machine")),
                PortRequirementSpec.builder().min(PortKinds.ENERGY_INPUT.id(), 1).build());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, net.minecraft.core.Direction.SOUTH);

        assertThat(formed).isFalse();
        assertThat(controller.getFoundMachine()).isNull();
        assertThat(controller.isFormed()).isFalse();
        assertThat(controller.getComponents()).isEmpty();
        assertThat(controller.getLastFormationFailure()).isNotNull();
        assertThat(controller.getLastFormationFailure().portId()).isEqualTo(PortKinds.ENERGY_INPUT.id());
    }

    @Test
    void matching_structure_with_required_port_forms() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("energy_input_hatch").get());
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("requires_energy_machine"),
                "Requires Energy",
                pattern,
                cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("requires_energy_machine")),
                PortRequirementSpec.builder().min(PortKinds.ENERGY_INPUT.id(), 1).build());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, energyHatch(controllerPos.offset(1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, net.minecraft.core.Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(controller.getFoundMachine()).isSameAs(machine);
        assertThat(controller.getLastFormationFailure()).isNull();
        assertThat(controller.isFormed()).isTrue();
    }
```

Add helper methods near existing helpers:

```java
    private static BlockArray onePortPattern(Block portBlock) {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(portBlock));
        return new BlockArray(blocks);
    }

    private static MachineControllerBlockEntity controllerForFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            IOPortBlockEntity port) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = cn.howxu.mmcr.registry.ModBlocks.controllerFor(machine).get();
        var controllerState = controllerBlock.defaultBlockState()
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FORMED, false)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING, net.minecraft.core.Direction.SOUTH);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(port.getBlockPos(), blockForPort(port));
        Level level = LevelStub.create(blocks, List.of(port));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, port, "level", level);
        return controller;
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            ItemInputBusBlockEntity bus = (ItemInputBusBlockEntity) unsafe.allocateInstance(ItemInputBusBlockEntity.class);
            setField(BlockEntity.class, bus, "type", null);
            setField(BlockEntity.class, bus, "worldPosition", pos);
            setField(BlockEntity.class, bus, "blockState", Blocks.CHEST.defaultBlockState());
            return bus;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate item input bus", e);
        }
    }

    private static boolean invokeTryFormMachine(MachineControllerBlockEntity controller, DynamicMachine machine, net.minecraft.core.Direction facing) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("tryFormMachine", cn.howxu.mmcr.api.machine.Machine.class, net.minecraft.core.Direction.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, machine, facing);
    }
```

- [ ] **Step 3: Run controller tests to verify failure**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`

Expected: FAIL because `getLastFormationFailure()` and port validation do not exist. If the helper fails before that due to test setup, fix only the helper setup until the failure is about missing functionality.

- [ ] **Step 4: Add controller failure field and getter**

In `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`, add imports:

```java
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
```

Add field near `lastFailureUnloc`:

```java
    private @Nullable PortRequirementSpec.Failure lastFormationFailure;
```

Add getter near `getLastFailureUnloc()`:

```java
    public @Nullable PortRequirementSpec.Failure getLastFormationFailure() { return lastFormationFailure; }
```

- [ ] **Step 5: Add read-only port counting helper**

In `MachineControllerBlockEntity`, add this method near `updateComponents()`:

```java
    private PortRequirementSpec.PortCounts countPorts(BlockArray rotatedPattern) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (level == null || rotatedPattern == null) return PortRequirementSpec.PortCounts.empty();

        for (BlockPos relativePos : rotatedPattern.pattern().keySet()) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (!(level.getBlockEntity(worldPos) instanceof IOPortBlockEntity port)) continue;
            String portId = port.kind().id();
            counts.merge(portId, 1, Integer::sum);
        }
        return PortRequirementSpec.PortCounts.of(counts);
    }
```

Also add import:

```java
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
```

Because `MachineControllerBlockEntity` is in the same package, the import is optional; omit it if the compiler flags it as redundant by project style.

- [ ] **Step 6: Validate before formation mutation**

Replace `tryFormMachine(...)` with:

```java
    private boolean tryFormMachine(Machine candidate, Direction facing) {
        BlockArray rotatedPattern = BlockArrayCache.get(candidate.pattern(), facing);
        if (!StructureMatcher.matchesRotated(rotatedPattern, level, getBlockPos())) return false;

        var failure = candidate.portRequirements().validate(countPorts(rotatedPattern));
        if (failure.isPresent()) {
            recordFormationFailure(candidate, failure.get());
            return false;
        }

        lastFormationFailure = null;
        onStructureFormed(candidate, rotatedPattern, facing);
        return true;
    }
```

Add this helper near `tryFormMachine(...)`:

```java
    private void recordFormationFailure(Machine candidate, PortRequirementSpec.Failure failure) {
        if (failure.equals(lastFormationFailure)) return;
        lastFormationFailure = failure;
        String max = failure.requiredMax().isPresent() ? Integer.toString(failure.requiredMax().getAsInt()) : "unbounded";
        LOG.info("[Ctrl#{}] formation rejected: pos={} machine={} port={} actual={} requiredMin={} requiredMax={} reason={}",
                instanceId, getBlockPos(), candidate.registryName(), failure.portId(), failure.actual(), failure.requiredMin(), max, failure.reason());
    }
```

- [ ] **Step 7: Clear formation failure on reset and successful form**

In `onStructureFormed(...)`, before `setChanged();`, ensure:

```java
        lastFormationFailure = null;
```

In `resetMachine()`, near `lastFailureUnloc = null;`, add:

```java
        lastFormationFailure = null;
```

- [ ] **Step 8: Run controller tests**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`

Expected: PASS.

- [ ] **Step 9: Compile**

Run: `./gradlew compileJava --no-daemon`

Expected: PASS.

- [ ] **Step 10: Commit**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java \
        src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java \
        src/test/java/cn/howxu/mmcr/LevelStub.java
git commit -m "feat(controller): validate formation port requirements"
```

---

### Task 4: End-to-End Default Behavior and Final Verification

**Files:**
- Modify if needed: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`
- Modify if needed: `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`

**Interfaces:**
- Consumes: Built-in blast furnace `PortRequirementSpec`, controller formation validation.
- Produces: Regression coverage for default blast furnace port combination behavior.

- [ ] **Step 1: Add or confirm built-in combination tests**

If Task 3 tests did not explicitly use the built-in blast furnace, add two tests to `MachineControllerBlockEntityTest`:

```java
    @Test
    void built_in_blast_furnace_rejects_three_arbitrary_ports() throws Exception {
        DefaultMachines.ensureRegistered();
        DynamicMachine machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("blast_furnace"));
        BlockPos controllerPos = new BlockPos(20, 4, 20);
        MachineControllerBlockEntity controller = controllerForDefaultBlastFurnace(
                machine,
                controllerPos,
                itemInputBus(controllerPos.offset(0, 0, -2)),
                itemInputBus(controllerPos.offset(-1, 0, -1)),
                itemInputBus(controllerPos.offset(1, 0, -1)));

        boolean formed = invokeTryFormMachine(controller, machine, net.minecraft.core.Direction.SOUTH);

        assertThat(formed).isFalse();
        assertThat(controller.getLastFormationFailure()).isNotNull();
    }

    @Test
    void built_in_blast_furnace_forms_with_required_ports() throws Exception {
        DefaultMachines.ensureRegistered();
        DynamicMachine machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("blast_furnace"));
        BlockPos controllerPos = new BlockPos(20, 4, 20);
        MachineControllerBlockEntity controller = controllerForDefaultBlastFurnace(
                machine,
                controllerPos,
                itemInputBus(controllerPos.offset(0, 0, -2)),
                itemOutputBus(controllerPos.offset(-1, 0, -1)),
                energyHatch(controllerPos.offset(1, 0, -1)));

        boolean formed = invokeTryFormMachine(controller, machine, net.minecraft.core.Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(controller.getLastFormationFailure()).isNull();
    }
```

Add `itemOutputBus(...)` helper mirroring `itemInputBus(...)`, but allocate `ItemOutputBusBlockEntity`.

Add `controllerForDefaultBlastFurnace(...)` helper:

```java
    private static MachineControllerBlockEntity controllerForDefaultBlastFurnace(
            DynamicMachine machine,
            BlockPos controllerPos,
            IOPortBlockEntity first,
            IOPortBlockEntity second,
            IOPortBlockEntity third) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = cn.howxu.mmcr.registry.ModBlocks.controllerFor(machine).get();
        var controllerState = controllerBlock.defaultBlockState()
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FORMED, false)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING, net.minecraft.core.Direction.SOUTH);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);

        Map<BlockPos, Block> blocks = new HashMap<>();
        for (var entry : machine.pattern().pattern().entrySet()) {
            blocks.put(controllerPos.offset(entry.getKey()), switch (entry.getValue()) {
                case BlockPredicate.OfBlock of -> of.block();
                case BlockPredicate.AnyOf ignored -> cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get();
                default -> cn.howxu.mmcr.registry.ModBlocks.CASING.get();
            });
        }
        blocks.put(first.getBlockPos(), blockForPort(first));
        blocks.put(second.getBlockPos(), blockForPort(second));
        blocks.put(third.getBlockPos(), blockForPort(third));

        Level level = LevelStub.create(blocks, List.of(first, second, third));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, first, "level", level);
        setField(BlockEntity.class, second, "level", level);
        setField(BlockEntity.class, third, "level", level);
        return controller;
    }

    private static Block blockForPort(IOPortBlockEntity port) {
        return cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get(port.kind().id()).get();
    }
```

- [ ] **Step 2: Run built-in related tests**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.internal.machine.DefaultMachinesTest --no-daemon`

Expected: PASS.

- [ ] **Step 3: Run full unit tests**

Run: `./gradlew test --no-daemon`

Expected: PASS.

- [ ] **Step 4: Run compile check**

Run: `./gradlew compileJava --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit test/cleanup changes if any**

If Step 1 added new tests or fixed helpers, run:

```bash
git add src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java \
        src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java
git commit -m "test(controller): cover blast furnace port requirements"
```

If there are no changes, do not create an empty commit.

- [ ] **Step 6: Final status check**

Run: `git status --short`

Expected: no uncommitted files related to this feature. If unrelated files appear, leave them untouched and mention them in handoff.

---

## Self-Review Notes

- Spec coverage: Tasks cover API model, machine definition storage, built-in blast furnace requirements, controller pre-formation validation, failure diagnostics, compatibility for machines with no requirements, and tests for passing/failing cases.
- Placeholder scan: No `TBD`, `TODO`, `FIXME`, or placeholder-only steps are intentionally left in this plan.
- Type consistency: Plan uses `PortRequirementSpec`, `PortRequirementSpec.CountRange`, `PortRequirementSpec.Failure`, `PortRequirementSpec.PortCounts`, and `Machine.portRequirements()` consistently across tasks.
