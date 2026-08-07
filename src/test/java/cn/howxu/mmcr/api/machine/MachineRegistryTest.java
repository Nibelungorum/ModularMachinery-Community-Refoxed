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
        MachineRegistry.clearForTesting();
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
    void dynamic_machine_exposes_immutable_replacement_map() {
        BlockPos position = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement(
                "speed", position, new BlockPredicate.Any(),
                List.of(), "", ItemStack.EMPTY);
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
        var replacement = new SingleBlockModifierReplacement(
                "speed", position, new BlockPredicate.Any(),
                List.of(), "", ItemStack.EMPTY);
        Identifier id = Identifier.fromNamespaceAndPath("mmcr", "outside_replacement_machine");

        assertThatThrownBy(() -> new DynamicMachine(
                id, "Outside Replacement Machine", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(),
                Map.of(position, List.of(replacement))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dynamic_machine_deep_copies_replacement_metadata() {
        BlockPos position = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement(
                "speed", position, new BlockPredicate.Any(),
                List.of(), "", ItemStack.EMPTY);
        Identifier id = Identifier.fromNamespaceAndPath("mmcr", "copied_replacement_machine");
        var machine = new DynamicMachine(
                id, "Copied Replacement Machine",
                new BlockArray(Map.of(position, new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(),
                Map.of(position, List.of(replacement)));

        replacement.setPos(new BlockPos(2, 0, 0));

        assertThat(machine.modifierReplacementsAt(position)).singleElement()
                .isNotSameAs(replacement)
                .extracting(SingleBlockModifierReplacement::getPos)
                .isEqualTo(position);
    }

    @Test
    void replacingDynamicMachinesRebuildsMergedCompiledCache() {
        var staticMachine = new DynamicMachine(Identifier.parse("mmcr:static"), "Static", new BlockArray(Map.of()));
        var dynamicMachine = new DynamicMachine(Identifier.parse("mmcr:dynamic"), "Dynamic", new BlockArray(Map.of()));
        MachineRegistry.register(staticMachine);

        MachineRegistry.replaceDynamic(Map.of(dynamicMachine.registryName(), dynamicMachine));

        assertThat(MachineRegistry.getCompiled(staticMachine.registryName())).isNotNull();
        assertThat(MachineRegistry.getCompiled(dynamicMachine.registryName())).isNotNull();

        MachineRegistry.replaceDynamic(Map.of());

        assertThat(MachineRegistry.getMachine(staticMachine.registryName())).isSameAs(staticMachine);
        assertThat(MachineRegistry.getMachine(dynamicMachine.registryName())).isNull();
        assertThat(MachineRegistry.getCompiled(dynamicMachine.registryName())).isNull();
    }
}
