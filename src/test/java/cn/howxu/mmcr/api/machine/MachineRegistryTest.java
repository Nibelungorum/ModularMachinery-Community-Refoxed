package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineRegistryTest {

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
    }

    @Test
    void register_then_get() {
        var machine = new DynamicMachine(
                Identifier.fromNamespaceAndPath("mmcr", "test"), "Test", new BlockArray(Map.of()));

        MachineRegistry.register(machine);

        assertThat(MachineRegistry.getMachine(machine.registryName())).isEqualTo(machine);
    }

    @Test
    void get_unknown_returns_null() {
        assertThat(MachineRegistry.getMachine(
                Identifier.fromNamespaceAndPath("mmcr", "missing"))).isNull();
    }

    @Test
    void duplicate_register_throws() {
        var m1 = new DynamicMachine(Identifier.fromNamespaceAndPath("mmcr", "dup"), "X", new BlockArray(Map.of()));
        var m2 = new DynamicMachine(Identifier.fromNamespaceAndPath("mmcr", "dup"), "Y", new BlockArray(Map.of()));

        MachineRegistry.register(m1);

        assertThatThrownBy(() -> MachineRegistry.register(m2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void effective_snapshot_is_reused_until_registry_changes() {
        MachineRegistry.clearForTesting();
        var first = new DynamicMachine(Identifier.parse("mmcr:first"), "First", new BlockArray(Map.of()));
        var second = new DynamicMachine(Identifier.parse("mmcr:second"), "Second", new BlockArray(Map.of()));

        MachineRegistry.register(first);
        Map<Identifier, Machine> initial = MachineRegistry.effectiveSnapshot();

        assertThat(MachineRegistry.effectiveSnapshot()).isSameAs(initial);

        MachineRegistry.register(second);

        assertThat(MachineRegistry.effectiveSnapshot()).isNotSameAs(initial).containsEntry(second.registryName(), second);

        MachineRegistry.clearForTesting();

        assertThat(MachineRegistry.effectiveSnapshot()).isNotSameAs(initial).isEmpty();
    }

    @Test
    void dynamic_machine_exposes_immutable_replacement_map() {
        BlockPos position = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.Any(), List.of(), ItemStack.EMPTY);
        var machine = new DynamicMachine(
                Identifier.fromNamespaceAndPath("mmcr", "replacement_machine"),
                "Replacement Machine", new BlockArray(Map.of(position, new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(Identifier.fromNamespaceAndPath("mmcr", "replacement_machine")),
                PortRequirementSpec.none(), List.of(),
                Map.of(position, List.of(replacement)));

        assertThat(machine.modifierReplacementsAt(position)).hasSize(1);
        assertThatThrownBy(() -> machine.modifierReplacements().put(BlockPos.ZERO, List.of(replacement)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dynamic_machine_rejects_replacement_outside_pattern() {
        BlockPos position = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.Any(), List.of(), ItemStack.EMPTY);
        Identifier id = Identifier.fromNamespaceAndPath("mmcr", "outside_replacement_machine");

        assertThatThrownBy(() -> new DynamicMachine(
                id, "Outside Replacement Machine", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(),
                Map.of(position, List.of(replacement))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dynamic_machine_preserves_replacement_metadata() {
        BlockPos position = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("speed", new BlockPredicate.Any(), List.of(), ItemStack.EMPTY);
        Identifier id = Identifier.fromNamespaceAndPath("mmcr", "copied_replacement_machine");
        var machine = new DynamicMachine(
                id, "Copied Replacement Machine",
                new BlockArray(Map.of(position, new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(),
                Map.of(position, List.of(replacement)));

        assertThat(machine.modifierReplacementsAt(position)).singleElement()
                .extracting(SingleBlockModifierReplacement::getModifierName)
                .isEqualTo("speed");
    }

    @Test
    void installingStructuresRebuildsMergedCompiledCache() {
        var staticMachine = new DynamicMachine(Identifier.parse("mmcr:static"), "Static", new BlockArray(Map.of()));
        var dynamicId = Identifier.parse("mmcr:dynamic");
        var dynamicStructure = new MachineStructureDefinition(dynamicId, new BlockArray(Map.of()),
                PortRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);
        MachineRegistry.register(staticMachine);
        MachineDefinitions.register(MachineRegistration.builder(dynamicId).localizedName("Dynamic").build());

        MachineStructureRegistry.replaceDynamic(Map.of(dynamicId, dynamicStructure));

        Map<Identifier, Machine> installed = MachineRegistry.effectiveSnapshot();
        assertThat(installed).containsEntry(staticMachine.registryName(), staticMachine).containsKey(dynamicId);

        assertThat(MachineRegistry.getCompiled(staticMachine.registryName())).isNotNull();
        assertThat(MachineRegistry.getCompiled(dynamicId)).isNotNull();

        MachineStructureRegistry.replaceDynamic(Map.of());

        assertThat(MachineRegistry.effectiveSnapshot()).isNotSameAs(installed).doesNotContainKey(dynamicId);
        assertThat(MachineRegistry.getMachine(staticMachine.registryName())).isSameAs(staticMachine);
        assertThat(MachineRegistry.getMachine(dynamicId)).isNull();
        assertThat(MachineRegistry.getCompiled(dynamicId)).isNull();
    }

    @Test
    void dynamic_runtime_machine_preserves_registration_concurrency_capabilities() {
        var dynamicId = Identifier.parse("mmcr:dynamic_concurrency");
        var registration = MachineRegistration.builder(dynamicId)
                .localizedName("Dynamic Concurrency")
                .allowMultithreading(true)
                .allowParallelism(true)
                .maxParallelAmount(9)
                .build();
        var structure = new MachineStructureDefinition(dynamicId, new BlockArray(Map.of()), PortRequirementSpec.none(),
                List.of(), MachineStructureRequirements.EMPTY);

        var runtime = MachineStructureRegistry.toRuntimeMachine(registration, structure);

        assertThat(runtime.hasFactory()).isTrue();
        assertThat(runtime.parallelizable()).isTrue();
        assertThat(runtime.maxParallelism()).isEqualTo(9);
    }

    @Test
    void expandable_machine_compiles_every_stage_and_compatibility_returns_stage_one() {
        Identifier id = Identifier.parse("mmcr:expandable");
        MachineDefinitions.register(MachineRegistration.builder(id).expandableStructure().build());
        MachineStructureDefinition definition = new MachineStructureDefinition(id, List.of(
                MachineStructureDefinition.Declaration.full(new BlockArray(Map.of(
                        BlockPos.ZERO, new BlockPredicate.Any()))),
                MachineStructureDefinition.Declaration.extension(new BlockArray(Map.of(
                        new BlockPos(2, 0, 0), new BlockPredicate.Any())))));

        MachineRegistry.installStructures(Map.of(id, definition));

        assertThat(MachineRegistry.getCompiledStages(id)).hasSize(2);
        assertThat(MachineRegistry.getCompiled(id)).isSameAs(MachineRegistry.getCompiledStages(id).getFirst());
        assertThat(MachineRegistry.getCompiledStages(id).get(1).machine().pattern().pattern())
                .containsKey(new BlockPos(2, 0, 0));
    }

    @Test
    void unmarked_machine_rejects_multiple_structure_stages() {
        Identifier id = Identifier.parse("mmcr:not_expandable");
        MachineDefinitions.register(MachineRegistration.builder(id).build());
        MachineStructureDefinition definition = new MachineStructureDefinition(id, List.of(
                MachineStructureDefinition.Declaration.full(new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any()))),
                MachineStructureDefinition.Declaration.extension(new BlockArray(Map.of(
                        new BlockPos(1, 0, 0), new BlockPredicate.Any())))));

        assertThatThrownBy(() -> MachineRegistry.installStructures(Map.of(id, definition)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(id.toString())
                .hasMessageContaining("expandableStructure");
    }
}
