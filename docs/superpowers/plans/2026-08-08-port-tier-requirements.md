# Port Tier Requirements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add formation-only multiblock port tier requirements and update built-in machines to use varied item, fluid, and energy minimum tiers.

**Architecture:** Keep the new tier API separate from existing exact-id port count requirements. `PortTierRequirementSpec` validates actual `IOPortKind` instances collected from matched structure positions, while `PortRequirementSpec` continues to count exact kind ids. Built-in machine patterns should accept all registered tiers in each intended port family, then tier requirements decide whether the concrete ports are high enough.

**Tech Stack:** Java 21, Minecraft 26.1.2, NeoForge, Gradle, JUnit 5, AssertJ.

## Global Constraints

- Use the smallest correct code changes and preserve existing package/style conventions.
- Do not run `./gradlew runClient --no-daemon`.
- After Java changes, verify with `./gradlew compileJava --no-daemon` and targeted tests.
- Do not modify unrelated dirty worktree files.
- New Java classes must include Javadoc author info: `@author howxu <dev@howxu.cn>`.
- This iteration is formation-only; do not change recipe execution or component lookup behavior.

---

## File Structure

- Create `src/main/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpec.java`: public API for minimum/any tier requirements and validation against `IOPortKind` values.
- Modify `src/main/java/cn/howxu/mmcr/api/machine/Machine.java`: add default `portTierRequirements()`.
- Modify `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`: add `portTierRequirements` record field and compatibility constructors.
- Modify `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`: collect matched port kinds and validate tier requirements during initial formation and cached rechecks.
- Modify `src/main/java/org/nibelungorum/DefaultMachines.java`: build all-tier port predicates and assign mixed tier requirements to default machines.
- Create `src/test/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpecTest.java`: unit tests for the new API.
- Modify `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`: add formation/revalidation coverage for minimum tier failures and passes.
- Modify `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`: verify built-in machines expose intended tier requirements.

---

### Task 1: Add Port Tier Requirement API

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpec.java`
- Test: `src/test/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpecTest.java`

**Interfaces:**
- Consumes: `IOPortKind.itemBusSize()`, `IOPortKind.fluidHatchSize()`, `IOPortKind.energyHatchSize()`, `IOPortKind.ioType()`.
- Produces: `PortTierRequirementSpec.none()`, `PortTierRequirementSpec.builder()`, `PortTierRequirementSpec.validate(Iterable<IOPortKind>)`, `PortTierRequirementSpec.requirements()`, `PortTierRequirementSpec.Requirement`, `PortTierRequirementSpec.Failure`.

- [ ] **Step 1: Write failing API tests**

Add `src/test/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpecTest.java`:

```java
package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.registry.PortKinds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortTierRequirementSpecTest {

    @Test
    void none_accepts_empty_ports() {
        assertThat(PortTierRequirementSpec.none().validate(List.of())).isEmpty();
    }

    @Test
    void any_item_input_accepts_any_item_input_tier() {
        var spec = PortTierRequirementSpec.builder().anyItemInput().build();

        assertThat(spec.validate(List.of(kind("item_input_bus_tiny")))).isEmpty();
    }

    @Test
    void minimum_energy_input_rejects_lower_tier() {
        var spec = PortTierRequirementSpec.builder()
                .minEnergyInput(EnergyHatchSize.LUDICROUS)
                .build();

        var failure = spec.validate(List.of(kind("energy_input_hatch_big")));

        assertThat(failure).hasValueSatisfying(value -> {
            assertThat(value.requirement().id()).isEqualTo("energy_input_hatch>=ludicrous");
            assertThat(value.actualPortIds()).containsExactly("energy_input_hatch_big");
        });
    }

    @Test
    void minimum_energy_input_accepts_exact_and_higher_tiers() {
        var spec = PortTierRequirementSpec.builder()
                .minEnergyInput(EnergyHatchSize.LUDICROUS)
                .build();

        assertThat(spec.validate(List.of(kind("energy_input_hatch_ludicrous")))).isEmpty();
        assertThat(spec.validate(List.of(kind("energy_input_hatch_ultimate")))).isEmpty();
    }

    @Test
    void wrong_direction_or_category_does_not_satisfy_requirement() {
        var spec = PortTierRequirementSpec.builder()
                .minFluidOutput(FluidHatchSize.HUGE)
                .build();

        assertThat(spec.validate(List.of(kind("fluid_input_hatch_vacuum")))).isPresent();
        assertThat(spec.validate(List.of(kind("energy_output_hatch_ultimate")))).isPresent();
    }

    @Test
    void item_and_fluid_minimums_accept_exact_or_higher_tiers() {
        var spec = PortTierRequirementSpec.builder()
                .minItemInput(ItemBusSize.NORMAL)
                .minFluidOutput(FluidHatchSize.HUGE)
                .build();

        assertThat(spec.validate(List.of(
                kind("item_input_bus"),
                kind("fluid_output_hatch_ludicrous")))).isEmpty();
    }

    private static cn.howxu.mmcr.internal.port.IOPortKind kind(String id) {
        return PortKinds.all().stream()
                .filter(kind -> kind.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.PortTierRequirementSpecTest --no-daemon`

Expected: FAIL because `PortTierRequirementSpec` does not exist.

- [ ] **Step 3: Implement `PortTierRequirementSpec`**

Create `src/main/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpec.java`:

```java
package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.util.IOType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Machine-level minimum tier requirements for IO ports checked during structure formation.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PortTierRequirementSpec(List<Requirement> requirements) {

    private static final PortTierRequirementSpec NONE = new PortTierRequirementSpec(List.of());

    public PortTierRequirementSpec {
        if (requirements == null) throw new IllegalArgumentException("requirements null");
        requirements = List.copyOf(requirements);
    }

    public static PortTierRequirementSpec none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return requirements.isEmpty();
    }

    public Optional<Failure> validate(Iterable<IOPortKind> ports) {
        if (ports == null) throw new IllegalArgumentException("ports null");
        List<IOPortKind> list = new ArrayList<>();
        for (IOPortKind port : ports) {
            if (port != null) list.add(port);
        }

        for (Requirement requirement : requirements) {
            if (list.stream().noneMatch(requirement::matches)) {
                List<String> actual = list.stream()
                        .filter(requirement::sameFamily)
                        .map(IOPortKind::id)
                        .toList();
                return Optional.of(new Failure(requirement, actual));
            }
        }
        return Optional.empty();
    }

    public enum PortCategory {
        ITEM,
        FLUID,
        ENERGY
    }

    public record Requirement(PortCategory category, IOType ioType, int minTier, String minTierId) {
        public Requirement {
            if (category == null) throw new IllegalArgumentException("category null");
            if (ioType == null) throw new IllegalArgumentException("ioType null");
            if (minTier < 0) throw new IllegalArgumentException("minTier must be >= 0");
            if (minTierId == null || minTierId.isBlank()) throw new IllegalArgumentException("minTierId blank");
        }

        public String id() {
            return baseId() + ">=" + minTierId;
        }

        private String baseId() {
            return switch (category) {
                case ITEM -> ioType == IOType.INPUT ? "item_input_bus" : "item_output_bus";
                case FLUID -> ioType == IOType.INPUT ? "fluid_input_hatch" : "fluid_output_hatch";
                case ENERGY -> ioType == IOType.INPUT ? "energy_input_hatch" : "energy_output_hatch";
            };
        }

        private boolean matches(IOPortKind port) {
            return sameFamily(port) && tier(port) >= minTier;
        }

        private boolean sameFamily(IOPortKind port) {
            if (port.ioType() != ioType) return false;
            return switch (category) {
                case ITEM -> port.itemBusSize().isPresent();
                case FLUID -> port.fluidHatchSize().isPresent();
                case ENERGY -> port.energyHatchSize().isPresent();
            };
        }

        private int tier(IOPortKind port) {
            return switch (category) {
                case ITEM -> port.itemBusSize().map(Enum::ordinal).orElse(-1);
                case FLUID -> port.fluidHatchSize().map(Enum::ordinal).orElse(-1);
                case ENERGY -> port.energyHatchSize().map(Enum::ordinal).orElse(-1);
            };
        }
    }

    public record Failure(Requirement requirement, List<String> actualPortIds) {
        public Failure {
            if (requirement == null) throw new IllegalArgumentException("requirement null");
            actualPortIds = List.copyOf(actualPortIds == null ? List.of() : actualPortIds);
        }
    }

    public static final class Builder {
        private final List<Requirement> requirements = new ArrayList<>();

        private Builder() {}

        public Builder anyItemInput() {
            return minItemInput(ItemBusSize.TINY);
        }

        public Builder anyItemOutput() {
            return minItemOutput(ItemBusSize.TINY);
        }

        public Builder anyFluidInput() {
            return minFluidInput(FluidHatchSize.TINY);
        }

        public Builder anyFluidOutput() {
            return minFluidOutput(FluidHatchSize.TINY);
        }

        public Builder anyEnergyInput() {
            return minEnergyInput(EnergyHatchSize.TINY);
        }

        public Builder anyEnergyOutput() {
            return minEnergyOutput(EnergyHatchSize.TINY);
        }

        public Builder minItemInput(ItemBusSize size) {
            return add(PortCategory.ITEM, IOType.INPUT, size.ordinal(), size.id());
        }

        public Builder minItemOutput(ItemBusSize size) {
            return add(PortCategory.ITEM, IOType.OUTPUT, size.ordinal(), size.id());
        }

        public Builder minFluidInput(FluidHatchSize size) {
            return add(PortCategory.FLUID, IOType.INPUT, size.ordinal(), size.id());
        }

        public Builder minFluidOutput(FluidHatchSize size) {
            return add(PortCategory.FLUID, IOType.OUTPUT, size.ordinal(), size.id());
        }

        public Builder minEnergyInput(EnergyHatchSize size) {
            return add(PortCategory.ENERGY, IOType.INPUT, size.ordinal(), size.id());
        }

        public Builder minEnergyOutput(EnergyHatchSize size) {
            return add(PortCategory.ENERGY, IOType.OUTPUT, size.ordinal(), size.id());
        }

        private Builder add(PortCategory category, IOType ioType, int minTier, String minTierId) {
            requirements.add(new Requirement(category, ioType, minTier, minTierId));
            return this;
        }

        public PortTierRequirementSpec build() {
            if (requirements.isEmpty()) return none();
            return new PortTierRequirementSpec(requirements);
        }
    }
}
```

- [ ] **Step 4: Run API tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.PortTierRequirementSpecTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit Task 1**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpec.java src/test/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpecTest.java
git commit -m "feat: add port tier requirement spec"
```

---

### Task 2: Wire Tier Requirements Into Machine Definitions

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/Machine.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`
- Test: existing compile and tests that instantiate `DynamicMachine`.

**Interfaces:**
- Consumes: `PortTierRequirementSpec.none()` from Task 1.
- Produces: `Machine.portTierRequirements()` and `DynamicMachine.portTierRequirements()`.

- [ ] **Step 1: Add failing constructor/API usage test**

Append to `src/test/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpecTest.java`:

```java
    @Test
    void dynamic_machine_defaults_to_no_tier_requirements() {
        var machine = new DynamicMachine(
                cn.howxu.mmcr.MMCR.id("tier_default_machine"),
                "Tier Default",
                new BlockArray(java.util.Map.of()));

        assertThat(machine.portTierRequirements()).isSameAs(PortTierRequirementSpec.none());
        assertThat(((Machine) machine).portTierRequirements()).isSameAs(PortTierRequirementSpec.none());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.PortTierRequirementSpecTest --no-daemon`

Expected: FAIL because `Machine.portTierRequirements()` does not exist.

- [ ] **Step 3: Add default method to `Machine`**

Modify `src/main/java/cn/howxu/mmcr/api/machine/Machine.java` so lines after `portRequirements()` read:

```java
    default PortRequirementSpec portRequirements() {
        return PortRequirementSpec.none();
    }

    default PortTierRequirementSpec portTierRequirements() {
        return PortTierRequirementSpec.none();
    }

    default RecipeFailureActions failureAction() {
        return RecipeFailureActions.getDefaultAction();
    }
```

- [ ] **Step 4: Add field and constructor forwarding to `DynamicMachine`**

Modify the record header in `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`:

```java
public record DynamicMachine(
        Identifier registryName,
        String localizedName,
        BlockArray pattern,
        MachineControllerSpec controller,
        PortRequirementSpec portRequirements,
        PortTierRequirementSpec portTierRequirements,
        List<DynamicPatternSpec> dynamicPatterns,
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements
) implements Machine {
```

Update constructors to forward `PortTierRequirementSpec.none()`:

```java
    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern) {
        this(registryName, localizedName, pattern, MachineControllerSpec.defaultsFor(registryName), PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of());
    }

    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern, MachineControllerSpec controller) {
        this(registryName, localizedName, pattern, controller, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of());
    }

    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern, List<DynamicPatternSpec> dynamicPatterns) {
        this(registryName, localizedName, pattern, MachineControllerSpec.defaultsFor(registryName), PortRequirementSpec.none(), PortTierRequirementSpec.none(), dynamicPatterns, Map.of());
    }

    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern, MachineControllerSpec controller, PortRequirementSpec portRequirements) {
        this(registryName, localizedName, pattern, controller, portRequirements, PortTierRequirementSpec.none(), List.of(), Map.of());
    }

    public DynamicMachine(
            Identifier registryName,
            String localizedName,
            BlockArray pattern,
            MachineControllerSpec controller,
            PortRequirementSpec portRequirements,
            List<DynamicPatternSpec> dynamicPatterns) {
        this(registryName, localizedName, pattern, controller, portRequirements, PortTierRequirementSpec.none(), dynamicPatterns, Map.of());
    }

    public DynamicMachine(
            Identifier registryName,
            String localizedName,
            BlockArray pattern,
            MachineControllerSpec controller,
            PortRequirementSpec portRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements) {
        this(registryName, localizedName, pattern, controller, portRequirements, PortTierRequirementSpec.none(), dynamicPatterns, modifierReplacements);
    }
```

In the compact constructor, add:

```java
        if (portTierRequirements == null) throw new IllegalArgumentException("portTierRequirements null");
```

- [ ] **Step 5: Run compile and API tests**

Run: `./gradlew compileJava test --tests cn.howxu.mmcr.api.machine.PortTierRequirementSpecTest --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/api/machine/Machine.java src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java src/test/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpecTest.java
git commit -m "feat: expose port tier requirements on machines"
```

---

### Task 3: Enforce Tier Requirements During Formation

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`

**Interfaces:**
- Consumes: `Machine.portTierRequirements()` and `PortTierRequirementSpec.validate(Iterable<IOPortKind>)`.
- Produces: formation rejection when matched ports are below required tiers.

- [ ] **Step 1: Add failing formation tests**

In `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`, add imports if missing:

```java
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
```

Add tests near existing required-port tests:

```java
    @Test
    void matching_structure_rejects_port_below_required_tier() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = anyEnergyInputPattern();
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("requires_ludicrous_energy_machine"),
                "Requires Ludicrous Energy",
                pattern,
                cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("requires_ludicrous_energy_machine")),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.builder().minEnergyInput(EnergyHatchSize.LUDICROUS).build(),
                List.of(),
                Map.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, energyHatch(controllerPos.offset(1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isFalse();
        assertThat(controller.getFoundMachine()).isNull();
        assertThat(controller.getLastFormationFailure()).isNotNull();
        assertThat(controller.getLastFormationFailure().portId()).isEqualTo("energy_input_hatch>=ludicrous");
    }

    @Test
    void matching_structure_accepts_port_at_required_tier() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = anyEnergyInputPattern();
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("accepts_ludicrous_energy_machine"),
                "Accepts Ludicrous Energy",
                pattern,
                cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("accepts_ludicrous_energy_machine")),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.builder().minEnergyInput(EnergyHatchSize.LUDICROUS).build(),
                List.of(),
                Map.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, energyHatch(controllerPos.offset(1, 0, 0), "energy_input_hatch_ludicrous"));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(controller.getLastFormationFailure()).isNull();
    }

    @Test
    void cached_formed_structure_revalidates_port_tier_requirements() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        BlockArray pattern = anyEnergyInputPattern();
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("cached_requires_ludicrous_energy_machine"),
                "Cached Requires Ludicrous Energy",
                pattern,
                cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("cached_requires_ludicrous_energy_machine")),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.builder().minEnergyInput(EnergyHatchSize.LUDICROUS).build(),
                List.of(),
                Map.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, energyHatch(portPos, "energy_input_hatch_ludicrous"));
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        for (int i = 0; i < Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS; i++) {
            controller.serverTick();
        }
        assertThat(controller.isFormed()).isTrue();

        Level level = levelOf(controller);
        EnergyInputHatchBlockEntity replacement = energyHatch(portPos);
        setField(BlockEntity.class, replacement, "level", level);
        level.setBlock(portPos, blockForPort(replacement).defaultBlockState(), 3);
        LevelStub.putBlockEntity(level, replacement);

        controller.serverTick();

        assertThat(controller.isFormed()).isFalse();
        assertThat(controller.getLastFormationFailure()).isNotNull();
        assertThat(controller.getLastFormationFailure().portId()).isEqualTo("energy_input_hatch>=ludicrous");
    }
```

Add helpers near existing pattern/port helpers:

```java
    private static BlockArray anyEnergyInputPattern() {
        return onePortPattern(new BlockPredicate.AnyOf(PortKinds.all().stream()
                .filter(kind -> kind.ioType() == IOType.INPUT && kind.energyHatchSize().isPresent())
                .map(kind -> new BlockPredicate.OfBlock(blockForPortKind(kind.id())))
                .toList()));
    }

    private static EnergyInputHatchBlockEntity energyHatch(BlockPos pos, String id) {
        var block = blockForPortKind(id);
        return new EnergyInputHatchBlockEntity(pos, block.defaultBlockState());
    }

    private static Block blockForPortKind(String id) {
        return cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get(id).get();
    }
```

If `onePortPattern(BlockPredicate predicate)` does not exist, add this overload:

```java
    private static BlockArray onePortPattern(BlockPredicate predicate) {
        return BlockArray.builder()
                .pattern("CI")
                .set('C', new BlockPredicate.OfBlock(cn.howxu.mmcr.registry.ModBlocks.controllerFor(MMCR.id("test_machine")).get()))
                .set('I', predicate)
                .build();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`

Expected: FAIL because controller formation does not validate tier requirements yet.

- [ ] **Step 3: Add tier validation in controller**

In `MachineControllerBlockEntity`, add import:

```java
import cn.howxu.mmcr.internal.port.IOPortKind;
```

Add a helper near `countPorts(...)`:

```java
    private List<IOPortKind> portKinds(BlockArray rotatedPattern, @Nullable CompiledMachinePattern compiledPattern, Direction facing) {
        if (level == null || rotatedPattern == null) return List.of();

        List<BlockPos> positions = compiledPattern == null ? new ArrayList<>(rotatedPattern.pattern().keySet()) : compiledPattern.portPositions(facing);
        List<IOPortKind> kinds = new ArrayList<>();
        for (BlockPos relativePos : positions) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (level.getBlockEntity(worldPos) instanceof IOPortBlockEntity port) {
                kinds.add(port.kind());
            }
        }
        return List.copyOf(kinds);
    }

    private Optional<PortRequirementSpec.Failure> validatePortTiers(Machine candidate, BlockArray rotatedPattern,
                                                                    @Nullable CompiledMachinePattern compiledPattern,
                                                                    Direction facing) {
        return candidate.portTierRequirements().validate(portKinds(rotatedPattern, compiledPattern, facing))
                .map(failure -> new PortRequirementSpec.Failure(
                        failure.requirement().id(),
                        failure.actualPortIds().size(),
                        1,
                        java.util.OptionalInt.empty(),
                        PortRequirementSpec.FailureReason.MISSING));
    }
```

In both places that currently run `foundMachine.portRequirements().validate(...)` or `candidate.portRequirements().validate(...)`, add the tier validation immediately after the count validation succeeds:

```java
                failure = validatePortTiers(foundMachine, foundPattern, foundCompiledPattern, facing);
                if (failure.isPresent()) {
                    recordFormationFailure(foundMachine, failure.get());
                    resetMachine(false);
                    return;
                }
```

For initial formation, use:

```java
        failure = validatePortTiers(candidate, rotatedPattern, compiled, facing);
        if (failure.isPresent()) {
            recordFormationFailure(candidate, failure.get());
            return false;
        }
```

- [ ] **Step 4: Run controller tests**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

Run:

```bash
git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java
git commit -m "feat: enforce port tier requirements during formation"
```

---

### Task 4: Update Built-In Machines For All-Tier Ports

**Files:**
- Modify: `src/main/java/org/nibelungorum/DefaultMachines.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`

**Interfaces:**
- Consumes: `PortTierRequirementSpec.builder()` from Task 1 and `DynamicMachine` constructor from Task 2.
- Produces: built-in machines with all-tier port pattern predicates and mixed tier requirements.

- [ ] **Step 1: Add failing default machine tests**

In `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`, add imports if missing:

```java
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
```

Add tests:

```java
    @Test
    void built_in_machines_define_expected_port_tier_requirements() {
        DefaultMachines.ensureRegistered();

        assertThat(requirementIds(MachineRegistry.getMachine(MMCR.id("blast_furnace"))))
                .contains("energy_input_hatch>=ludicrous", "item_input_bus>=normal");
        assertThat(requirementIds(MachineRegistry.getMachine(MMCR.id("alloy_furnace"))))
                .contains("item_input_bus>=reinforced", "energy_input_hatch>=big");
        assertThat(requirementIds(MachineRegistry.getMachine(MMCR.id("cracker"))))
                .contains("fluid_output_hatch>=huge", "energy_input_hatch>=reinforced", "item_input_bus>=normal");
        assertThat(requirementIds(MachineRegistry.getMachine(MMCR.id("reactor"))))
                .contains("energy_output_hatch>=ultimate", "fluid_input_hatch>=big", "fluid_output_hatch>=ludicrous");
    }

    private static List<String> requirementIds(Machine machine) {
        return machine.portTierRequirements().requirements().stream()
                .map(PortTierRequirementSpec.Requirement::id)
                .toList();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.machine.DefaultMachinesTest --no-daemon`

Expected: FAIL because default machines still have no tier requirements.

- [ ] **Step 3: Add all-tier predicate helper**

In `src/main/java/org/nibelungorum/DefaultMachines.java`, add imports:

```java
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.util.IOType;
```

Add helper methods near the bottom of the class:

```java
    private static BlockPredicate portFamily(IOType ioType, PortTierRequirementSpec.PortCategory category) {
        return new BlockPredicate.AnyOf(PortKinds.all().stream()
                .filter(kind -> kind.ioType() == ioType)
                .filter(kind -> matchesCategory(kind, category))
                .map(kind -> new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(kind.id()).get()))
                .toList());
    }

    private static boolean matchesCategory(IOPortKind kind, PortTierRequirementSpec.PortCategory category) {
        return switch (category) {
            case ITEM -> kind.itemBusSize().isPresent();
            case FLUID -> kind.fluidHatchSize().isPresent();
            case ENERGY -> kind.energyHatchSize().isPresent();
        };
    }
```

- [ ] **Step 4: Replace normal-only default port predicates**

Update each built-in machine’s `BlockPredicate.AnyOf` to use `portFamily(...)` for the port categories that slot should accept. Example for blast furnace:

```java
        BlockPredicate ioPort = new BlockPredicate.AnyOf(List.of(
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ITEM),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.FLUID),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.FLUID),
                portFamily(IOType.INPUT, PortTierRequirementSpec.PortCategory.ENERGY),
                portFamily(IOType.OUTPUT, PortTierRequirementSpec.PortCategory.ENERGY)));
```

Use the same pattern to preserve each existing machine’s allowed categories:

- blast furnace: item input/output, fluid input/output, energy input/output;
- alloy furnace: item input/output, energy input;
- cracker: item input/output, fluid output, energy input, plus existing `Blocks.WEATHERED_COPPER`;
- reactor: item input/output, fluid input/output, energy output, plus existing `Blocks.BLUE_ICE` optional slot.

- [ ] **Step 5: Add mixed tier requirements to `DynamicMachine` construction**

For blast furnace, create:

```java
        PortTierRequirementSpec tierRequirements = PortTierRequirementSpec.builder()
                .minEnergyInput(EnergyHatchSize.LUDICROUS)
                .minItemInput(ItemBusSize.NORMAL)
                .build();
```

Return:

```java
        return new DynamicMachine(
                BLAST_FURNACE_ID,
                "高炉",
                pattern,
                MachineControllerSpec.defaultsFor(BLAST_FURNACE_ID),
                portRequirements,
                tierRequirements,
                List.of(),
                Map.of());
```

For alloy furnace:

```java
        PortTierRequirementSpec tierRequirements = PortTierRequirementSpec.builder()
                .minItemInput(ItemBusSize.REINFORCED)
                .minEnergyInput(EnergyHatchSize.BIG)
                .build();
```

For cracker:

```java
        PortTierRequirementSpec tierRequirements = PortTierRequirementSpec.builder()
                .minFluidOutput(FluidHatchSize.HUGE)
                .minEnergyInput(EnergyHatchSize.REINFORCED)
                .minItemInput(ItemBusSize.NORMAL)
                .build();
```

For reactor:

```java
        PortTierRequirementSpec tierRequirements = PortTierRequirementSpec.builder()
                .minEnergyOutput(EnergyHatchSize.ULTIMATE)
                .minFluidInput(FluidHatchSize.BIG)
                .minFluidOutput(FluidHatchSize.LUDICROUS)
                .build();
```

Pass each `tierRequirements` into the new `DynamicMachine` constructor.

- [ ] **Step 6: Run default machine tests**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.machine.DefaultMachinesTest --no-daemon`

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

Run:

```bash
git add src/main/java/org/nibelungorum/DefaultMachines.java src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java
git commit -m "feat: add default machine tier combinations"
```

---

### Task 5: Final Verification

**Files:**
- Verify all files changed in Tasks 1-4.

**Interfaces:**
- Consumes: all completed task outputs.
- Produces: verified build state ready for in-game testing.

- [ ] **Step 1: Run compile**

Run: `./gradlew compileJava --no-daemon`

Expected: PASS.

- [ ] **Step 2: Run targeted tests**

Run:

```bash
./gradlew test --tests cn.howxu.mmcr.api.machine.PortTierRequirementSpecTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.internal.machine.DefaultMachinesTest --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Inspect diff**

Run: `git diff -- src/main/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpec.java src/main/java/cn/howxu/mmcr/api/machine/Machine.java src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java src/main/java/org/nibelungorum/DefaultMachines.java src/test/java/cn/howxu/mmcr/api/machine/PortTierRequirementSpecTest.java src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`

Expected: Diff only contains tier requirement API, formation validation, default machine tier combinations, and tests.

- [ ] **Step 4: Commit final verification note if needed**

If Task 5 required fixes, commit them:

```bash
git add <fixed-files>
git commit -m "fix: complete port tier requirement verification"
```

If Task 5 required no fixes, do not create an empty commit.

---

## Self-Review

- Spec coverage: Tasks cover the new API, default no-op behavior, formation-only validation, all-tier default patterns, mixed built-in machine requirements, and verification.
- Placeholder scan: No task contains unresolved placeholders; each code-writing step includes concrete signatures or snippets.
- Type consistency: The plan consistently uses `PortTierRequirementSpec`, `Requirement.id()`, `Failure.actualPortIds()`, and existing `ItemBusSize`, `FluidHatchSize`, `EnergyHatchSize`, `IOType`, and `IOPortKind` types.

Plan complete and saved to `docs/superpowers/plans/2026-08-08-port-tier-requirements.md`. Two execution options:

1. Subagent-Driven (recommended) - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. Inline Execution - execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
