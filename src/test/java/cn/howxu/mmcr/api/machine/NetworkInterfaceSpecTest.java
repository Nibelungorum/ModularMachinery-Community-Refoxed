package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies immutable network interface declarations and compiled network positions.
 * @author howxu <dev@howxu.cn>
 */
class NetworkInterfaceSpecTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void disabledAndAllowlistRulesAreExplicit() {
        assertThat(NetworkInterfaceSpec.disabled().maxCount()).isZero();
        assertThat(NetworkInterfaceSpec.disabled().maxConnections()).isZero();
        assertThatThrownBy(() -> new NetworkInterfaceSpec(-1, 1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NetworkInterfaceSpec(1, -1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);

        MachineDefinition definition = MachineBuilder.machine(Identifier.parse("mmcr:source"))
                .networkInterface(2, 3)
                .allowNetworkMachine(Identifier.parse("mmcr:target"))
                .build();
        assertThat(definition.networkInterface().maxCount()).isEqualTo(2);
        assertThat(definition.networkInterface().allowedMachineIds())
                .contains(Identifier.parse("mmcr:target"));
    }

    @Test
    void allowlistIsDefensivelyCopiedInInsertionOrder() {
        Identifier first = Identifier.parse("mmcr:first");
        Identifier second = Identifier.parse("mmcr:second");
        Set<Identifier> source = new LinkedHashSet<>(List.of(first, second));
        NetworkInterfaceSpec spec = new NetworkInterfaceSpec(1, 2, source);

        source.clear();
        assertThat(spec.allowedMachineIds()).containsExactly(first, second);
        assertThatThrownBy(() -> spec.allowedMachineIds().add(Identifier.parse("mmcr:third")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void duplicateAllowedMachineIdsRemainHarmlessSetEntries() {
        Identifier target = Identifier.parse("mmcr:target");
        NetworkInterfaceSpec spec = new NetworkInterfaceSpec(2, 3,
                new LinkedHashSet<>(List.of(target, target)));
        NetworkInterfaceSpec added = spec.withAllowedMachine(target);

        assertThat(added).isNotSameAs(spec);
        assertThat(added.allowedMachineIds()).containsExactly(target);
        assertThat(spec.allowedMachineIds()).containsExactly(target);
    }

    @Test
    void compilerKeeps_network_positions_separate_from_component_and_port_positions() {
        Identifier id = Identifier.parse("mmcr:network_compilation");
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, BlockPredicate.networkInterface(),
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        Machine machine = new DynamicMachine(id, "Network Compilation", pattern);

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(BlockPredicate.networkInterface().matches(Blocks.STONE.defaultBlockState())).isFalse();
        assertThat(compiled.networkInterfacePositions(Direction.SOUTH)).containsExactly(BlockPos.ZERO);
        assertThat(compiled.componentPositions(Direction.SOUTH)).doesNotContain(BlockPos.ZERO);
        assertThat(compiled.portPositions(Direction.SOUTH)).doesNotContain(BlockPos.ZERO);
        assertThat(compiled.networkInterfacePositions(Direction.UP)).isEmpty();
    }

    @Test
    void compilerKeeps_network_any_of_positions_separate_from_component_and_port_positions() {
        Identifier id = Identifier.parse("mmcr:network_any_of_compilation");
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.AnyOf(List.of(BlockPredicate.networkInterface()))));
        Machine machine = new DynamicMachine(id, "Network Any Of Compilation", pattern);

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(compiled.networkInterfacePositions(Direction.SOUTH)).containsExactly(BlockPos.ZERO);
        assertThat(compiled.componentPositions(Direction.SOUTH)).doesNotContain(BlockPos.ZERO);
        assertThat(compiled.portPositions(Direction.SOUTH)).doesNotContain(BlockPos.ZERO);
    }
}
