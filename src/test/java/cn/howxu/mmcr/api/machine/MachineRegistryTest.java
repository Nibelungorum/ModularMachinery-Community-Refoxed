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
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPos(1, 0, 0), new BlockPredicate.Any(),
                List.of(), "", ItemStack.EMPTY);
        var machine = new DynamicMachine(
                Identifier.fromNamespaceAndPath("mmcr", "replacement_machine"),
                "Replacement Machine", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(Identifier.fromNamespaceAndPath("mmcr", "replacement_machine")),
                PortRequirementSpec.none(), List.of(),
                Map.of(new BlockPos(1, 0, 0), List.of(replacement)));

        assertThat(machine.modifierReplacementsAt(new BlockPos(1, 0, 0))).containsExactly(replacement);
        assertThatThrownBy(() -> machine.modifierReplacements().put(BlockPos.ZERO, List.of(replacement)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
