package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
                BlockPos.ZERO, networkInterface(),
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE)));
        Machine machine = new DynamicMachine(id, "Network Compilation", pattern);

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(networkInterface().matches(Blocks.STONE.defaultBlockState())).isFalse();
        assertThat(compiled.networkInterfacePositions(Direction.SOUTH)).containsExactly(BlockPos.ZERO);
        assertThat(compiled.componentPositions(Direction.SOUTH)).doesNotContain(BlockPos.ZERO);
        assertThat(compiled.portPositions(Direction.SOUTH)).doesNotContain(BlockPos.ZERO);
        assertThat(compiled.networkInterfacePositions(Direction.UP)).isEmpty();
    }

    @Test
    void compilerKeeps_network_any_of_positions_separate_from_component_and_port_positions() {
        Identifier id = Identifier.parse("mmcr:network_any_of_compilation");
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.AnyOf(List.of(networkInterface()))));
        Machine machine = new DynamicMachine(id, "Network Any Of Compilation", pattern);

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(compiled.networkInterfacePositions(Direction.SOUTH)).containsExactly(BlockPos.ZERO);
        assertThat(compiled.componentPositions(Direction.SOUTH)).doesNotContain(BlockPos.ZERO);
        assertThat(compiled.portPositions(Direction.SOUTH)).doesNotContain(BlockPos.ZERO);
    }

    @Test
    void compiler_keeps_data_storage_component_positions_when_they_share_an_any_of_with_network_interfaces() {
        BlockPos sharedPosition = BlockPos.ZERO;
        BlockArray pattern = new BlockArray(Map.of(sharedPosition, new BlockPredicate.AnyOf(List.of(
                networkInterface(), new BlockPredicate.OfBlock(ModBlocks.DATA_STORAGE.get())))));
        Machine machine = new DynamicMachine(Identifier.parse("mmcr:network_data_storage_compilation"),
                "Network Data Storage Compilation", pattern);

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(compiled.componentPositions(Direction.SOUTH)).contains(sharedPosition);
    }

    @Test
    void old_complete_constructor_defaults_network_positions_to_empty() {
        Identifier id = Identifier.parse("mmcr:legacy_compiled_pattern");
        BlockArray pattern = new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any()));
        Machine machine = new DynamicMachine(id, "Legacy Compiled Pattern", pattern);

        CompiledMachinePattern compiled = new CompiledMachinePattern(
                machine,
                2,
                Map.of(Direction.SOUTH, pattern),
                Map.of(Direction.SOUTH, new BoundingBox(0, 0, 0, 0, 0, 0)),
                Map.of(Direction.SOUTH, List.of(BlockPos.ZERO)),
                Map.of(Direction.SOUTH, List.of(BlockPos.ZERO)),
                Map.of(Direction.SOUTH, List.of()),
                Map.of(Direction.SOUTH, List.of()),
                List.of(),
                Map.of(),
                false);

        assertThat(compiled.stageNumber()).isEqualTo(2);
        assertThat(compiled.networkInterfacePositions(Direction.SOUTH)).isEmpty();
    }

    @Test
    void raw_fallback_positions_exclude_network_predicates_but_keep_ordinary_any_of() {
        BlockPos mixedNetwork = BlockPos.ZERO;
        BlockPos ordinary = new BlockPos(1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                mixedNetwork, new BlockPredicate.AnyOf(List.of(
                        networkInterface(), new BlockPredicate.OfBlock(Blocks.STONE))),
                ordinary, new BlockPredicate.AnyOf(List.of(new BlockPredicate.OfBlock(Blocks.STONE)))));

        assertThat(MachinePatternCompiler.positionsExcludingNetworkInterfaces(pattern))
                .containsExactly(ordinary);
    }

    @Test
    void mixed_nested_network_any_of_never_enters_module_connection_indexes() {
        var smartInterface = new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get());
        BlockPos mixedCoupler = BlockPos.ZERO;
        BlockPos nestedMixedCoupler = new BlockPos(1, 0, 0);
        BlockPos ordinaryCoupler = new BlockPos(2, 0, 0);
        BlockPos mixedInterface = new BlockPos(3, 0, 0);
        BlockPos nestedMixedInterface = new BlockPos(4, 0, 0);
        BlockPos ordinaryInterface = new BlockPos(5, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(
                mixedCoupler, new BlockPredicate.AnyOf(List.of(
                        BlockPredicate.machineCoupler(), networkInterface())),
                nestedMixedCoupler, new BlockPredicate.AnyOf(List.of(
                        new BlockPredicate.AnyOf(List.of(networkInterface(), BlockPredicate.machineCoupler())))),
                ordinaryCoupler, new BlockPredicate.AnyOf(List.of(
                        BlockPredicate.machineCoupler(), new BlockPredicate.AnyOf(List.of(BlockPredicate.machineCoupler())))),
                mixedInterface, new BlockPredicate.AnyOf(List.of(smartInterface, networkInterface())),
                nestedMixedInterface, new BlockPredicate.AnyOf(List.of(
                        new BlockPredicate.AnyOf(List.of(networkInterface(), smartInterface)))),
                ordinaryInterface, new BlockPredicate.AnyOf(List.of(
                        smartInterface, new BlockPredicate.AnyOf(List.of(smartInterface))))));
        Machine machine = new DynamicMachine(
                Identifier.parse("mmcr:mixed_network_indexes"), "Mixed Network Indexes", pattern);

        CompiledMachinePattern compiled = MachinePatternCompiler.compile(machine);

        assertThat(compiled.couplerPositions(Direction.SOUTH)).containsExactly(ordinaryCoupler);
        assertThat(compiled.interfacePositions(Direction.SOUTH)).containsExactly(ordinaryInterface);
        assertThat(compiled.networkInterfacePositions(Direction.SOUTH))
                .containsExactlyInAnyOrder(mixedCoupler, nestedMixedCoupler, mixedInterface, nestedMixedInterface);
    }

    static BlockPredicate.DeferredBlock networkInterface() {
        return new BlockPredicate.DeferredBlock(ModBlocks.NETWORK_INTERFACE::get, true);
    }
}
