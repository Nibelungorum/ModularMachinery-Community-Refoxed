package cn.howxu.mmcr.internal.machine;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import org.nibelungorum.DefaultMachines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMachinesTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
    }

    @Test
    void structures_install_default_blast_furnace_once() {
        installDefaultStructures();
        installDefaultStructures();

        var machine = MachineRegistry.getMachine(MMCR.id("blast_furnace"));

        assertThat(machine).isNotNull();
        assertThat(machine.parallelizable()).isTrue();
        assertThat(machine.maxParallelism()).isEqualTo(Integer.MAX_VALUE);
        assertThat(machine.hasFactory()).isTrue();
        assertThat(machine.localizedName()).isEqualTo("高炉");
        assertThat(machine.controller().id()).isEqualTo(MMCR.id("blast_furnace_controller"));
        assertThat(machine.portRequirements().isEmpty()).isTrue();
        assertThat(machine.pattern().pattern()).hasSize(26);
        assertThat(machine.pattern().get(BlockPos.ZERO))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.controllerFor(machine.registryName()).get()));
        assertThat(machine.pattern().get(new BlockPos(0, 0, -1))).isNull();
        assertThat(machine.pattern().get(new BlockPos(0, -1, -1)))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
        assertThat(machine.pattern().get(new BlockPos(-1, 0, -2)))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("item_input_bus_ludicrous").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("energy_input_hatch_ultimate").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("energy_output_hatch").get().defaultBlockState())).isTrue();
    }

    @Test
    void structures_install_default_alloy_furnace_once() {
        installDefaultStructures();
        installDefaultStructures();

        var machine = (cn.howxu.mmcr.api.machine.DynamicMachine) MachineRegistry.getMachine(MMCR.id("alloy_furnace"));

        assertThat(machine).isNotNull();
        assertThat(machine.localizedName()).isEqualTo("合金炉");
        assertThat(machine.controller().id()).isEqualTo(MMCR.id("alloy_furnace_controller"));
        assertThat(machine.appearance().machineBasicBlock()).isEqualTo(net.minecraft.resources.Identifier.withDefaultNamespace("bricks"));
        assertThat(machine.appearance().controllerBaseTexture()).isEqualTo(net.minecraft.resources.Identifier.withDefaultNamespace("block/bricks"));
        assertThat(machine.appearance().formedPortBaseTexture()).isEqualTo(net.minecraft.resources.Identifier.withDefaultNamespace("block/bricks"));
        assertThat(machine.portRequirements().isEmpty()).isTrue();
        assertThat(machine.pattern().pattern()).hasSize(26);
        assertThat(machine.pattern().get(BlockPos.ZERO))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.controllerFor(machine.registryName()).get()));
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -2)))
                .isEqualTo(new BlockPredicate.OfBlock(net.minecraft.world.level.block.Blocks.BRICKS));
        assertThat(machine.pattern().get(new BlockPos(0, -1, -1)))
                .isEqualTo(new BlockPredicate.OfBlock(net.minecraft.world.level.block.Blocks.BLAST_FURNACE));
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("item_input_bus_reinforced").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("energy_input_hatch_big").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 0, -2)).matches(ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState())).isFalse();

        var mPosUp = new BlockPos(0, -1, -1);
        var mPosDown = new BlockPos(0, 1, -1);
        assertThat(machine.modifierReplacementsAt(mPosUp))
                .extracting(cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement::getModifierName)
                .containsExactly("alloy_furnace_diamond_speedup", "alloy_furnace_gold_doubling");
        assertThat(machine.modifierReplacementsAt(mPosDown))
                .extracting(cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement::getModifierName)
                .containsExactly("alloy_furnace_diamond_speedup", "alloy_furnace_gold_doubling");

        var diamondUp = machine.modifierReplacementsAt(mPosUp).get(0);
        assertThat(diamondUp.getReplacement())
                .isEqualTo(new BlockPredicate.OfBlock(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK));
        assertThat(diamondUp.getModifiers()).singleElement().satisfies(mod -> {
            assertThat(mod.getTarget()).isEqualTo("duration");
            assertThat(mod.getIOTarget()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.INPUT);
            assertThat(mod.getOperation()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.Operation.MULTIPLY);
            assertThat(mod.getModifier()).isEqualTo(0.5F);
        });

        var goldDown = machine.modifierReplacementsAt(mPosDown).get(1);
        assertThat(goldDown.getReplacement())
                .isEqualTo(new BlockPredicate.OfBlock(net.minecraft.world.level.block.Blocks.GOLD_BLOCK));
        assertThat(goldDown.getModifiers()).singleElement().satisfies(mod -> {
            assertThat(mod.getTarget()).isEqualTo("item");
            assertThat(mod.getIOTarget()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT);
            assertThat(mod.getOperation()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.Operation.MULTIPLY);
            assertThat(mod.getModifier()).isEqualTo(2.0F);
        });
    }

    @Test
    void structures_install_thermal_smelting_furnace_with_replaceable_basalt_slots() {
        installDefaultStructures();

        var machine = MachineRegistry.getMachine(MMCR.id("thermal_smelting_furnace"));

        assertThat(machine).isNotNull();
        assertThat(machine.localizedName()).isEqualTo("热能冶炼炉");
        assertThat(machine.parallelizable()).isTrue();
        assertThat(machine.hasFactory()).isTrue();
        assertThat(machine.maxParallelism()).isEqualTo(Integer.MAX_VALUE);
        assertThat(machine.controller().id()).isEqualTo(MMCR.id("thermal_smelting_furnace_controller"));
        assertThat(machine.pattern().get(BlockPos.ZERO))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.controllerFor(machine.registryName()).get()));
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -1)).matches(net.minecraft.world.level.block.Blocks.SMOOTH_BASALT.defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -1)).matches(ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -1)).matches(ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -1)).matches(ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -1)).matches(ModBlocks.BLOCKS.get("parallel_controller_4").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -1)).matches(ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -1)).matches(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK.defaultBlockState())).isFalse();
        assertThat(machine.pattern().pattern().values())
                .contains(new BlockPredicate.OfBlock(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE));
        assertThat(requirementIds(machine)).contains("energy_input_hatch>=tiny", "item_input_bus>=tiny", "item_output_bus>=tiny");
    }

    @Test
    void structures_install_default_cracker_once() {
        installDefaultStructures();
        installDefaultStructures();

        var machine = MachineRegistry.getMachine(MMCR.id("cracker"));

        assertThat(machine).isNotNull();
        assertThat(machine.parallelizable()).isFalse();
        assertThat(machine.maxParallelism()).isEqualTo(1);
        assertThat(machine.hasFactory()).isFalse();
        assertThat(machine.localizedName()).isEqualTo("裂化器");
        assertThat(machine.controller().id()).isEqualTo(MMCR.id("cracker_controller"));
        assertThat(machine.controller().allowVerticalFacing()).isTrue();
        assertThat(machine.portRequirements().isEmpty()).isTrue();
        assertThat(machine.pattern().get(BlockPos.ZERO))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.controllerFor(machine.registryName()).get()));
        assertThat(machine.pattern().get(new BlockPos(0, -2, 0))).isNull();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, 0)).matches(crackerPortPredicateState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, 0)).matches(ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, 0)).matches(ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, 0)).matches(ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, 0)).matches(ModBlocks.BLOCKS.get("fluid_output_hatch_huge").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, 0)).matches(ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, 0)).matches(ModBlocks.BLOCKS.get("energy_input_hatch_reinforced").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, 0)).matches(net.minecraft.world.level.block.Blocks.WEATHERED_COPPER.defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, -3, -1)).matches(net.minecraft.world.level.block.Blocks.POLISHED_ANDESITE.defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(1, 0, 0)).matches(net.minecraft.world.level.block.Blocks.BLUE_ICE.defaultBlockState())).isTrue();
    }

    @Test
    void structures_install_default_reactor_once() {
        installDefaultStructures();
        installDefaultStructures();

        var machine = MachineRegistry.getMachine(MMCR.id("reactor"));

        assertThat(machine).isNotNull();
        assertThat(machine.localizedName()).isEqualTo("反应堆");
        assertThat(machine.controller().id()).isEqualTo(MMCR.id("reactor_controller"));
        assertThat(machine.appearance().machineBasicBlock()).isEqualTo(net.minecraft.resources.Identifier.withDefaultNamespace("blue_ice"));
        assertThat(machine.appearance().controllerBaseTexture()).isEqualTo(net.minecraft.resources.Identifier.withDefaultNamespace("block/blue_ice"));
        assertThat(machine.appearance().formedPortBaseTexture()).isEqualTo(net.minecraft.resources.Identifier.withDefaultNamespace("block/blue_ice"));
        assertThat(machine.portRequirements().isEmpty()).isTrue();
        assertThat(machine.pattern().get(BlockPos.ZERO))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.controllerFor(machine.registryName()).get()));
        assertThat(machine.pattern().get(new BlockPos(0, 0, 0)).matches(ModBlocks.controllerFor(machine.registryName()).get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, 0, 0)).matches(ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, 0, 0)).matches(ModBlocks.BLOCKS.get("fluid_input_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, 0, 0)).matches(ModBlocks.BLOCKS.get("fluid_input_hatch_big").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, 0, 0)).matches(ModBlocks.BLOCKS.get("fluid_output_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, 0, 0)).matches(ModBlocks.BLOCKS.get("fluid_output_hatch_ludicrous").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, 0, 0)).matches(ModBlocks.BLOCKS.get("energy_output_hatch").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, 0, 0)).matches(ModBlocks.BLOCKS.get("energy_output_hatch_ultimate").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, 0, 0)).matches(net.minecraft.world.level.block.Blocks.BLUE_ICE.defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, -1, -6)).matches(net.minecraft.world.level.block.Blocks.BLUE_ICE.defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, -1, 1)).matches(net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState())).isTrue();
    }

    @Test
    void default_cracker_only_matches_when_controller_faces_vertically() {
        Machine machine = DefaultMachines.cracker(
                ModBlocks.BLOCKS.get("item_input_bus").get(),
                ModBlocks.BLOCKS.get("item_output_bus").get(),
                ModBlocks.BLOCKS.get("fluid_output_hatch").get(),
                ModBlocks.BLOCKS.get("energy_input_hatch").get());
        BlockPos controller = new BlockPos(10, 4, 10);
        Map<BlockPos, Block> blocks = new HashMap<>();
        for (var entry : machine.pattern().pattern().entrySet()) {
            blocks.put(controller.offset(cn.howxu.mmcr.api.machine.BlockRotator.rotateSouthTo(entry.getKey(), Direction.UP)), switch (entry.getValue()) {
                case BlockPredicate.OfBlock of -> of.block();
                case BlockPredicate.AnyOf any -> any.children().stream()
                        .filter(BlockPredicate.OfBlock.class::isInstance)
                        .map(BlockPredicate.OfBlock.class::cast)
                        .map(BlockPredicate.OfBlock::block)
                        .findFirst()
                        .orElse(ModBlocks.BLOCKS.get("item_input_bus").get());
                default -> ModBlocks.CASING.get();
            });
        }

        assertThat(StructureMatcher.matches(machine.pattern(), LevelStub.create(blocks), controller, Direction.UP))
                .isTrue();
        assertThat(machine.controller().requireVerticalFacing()).isTrue();
    }

    @Test
    void default_blast_furnace_raw_pattern_faces_south() {
        Machine machine = DefaultMachines.blastFurnace(
                ModBlocks.CASING.get(),
                ModBlocks.BLOCKS.get("item_input_bus").get(),
                ModBlocks.BLOCKS.get("item_output_bus").get(),
                ModBlocks.BLOCKS.get("fluid_input_hatch").get(),
                ModBlocks.BLOCKS.get("fluid_output_hatch").get(),
                ModBlocks.BLOCKS.get("energy_input_hatch").get(),
                ModBlocks.BLOCKS.get("energy_output_hatch").get());
        assertThat(machine.pattern().get(BlockPos.ZERO))
                .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.controllerFor(machine.registryName()).get()));
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -2)).matches(ModBlocks.CASING.get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -2)).matches(ModBlocks.BLOCKS.get("parallel_controller_4").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 1, -1)).matches(ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState())).isTrue();
    }

    @Test
    void default_blast_furnace_accepts_parallel_controllers_and_factory_scheduler_slots() {
        Machine machine = DefaultMachines.blastFurnace(
                ModBlocks.CASING.get(),
                ModBlocks.BLOCKS.get("item_input_bus").get(),
                ModBlocks.BLOCKS.get("item_output_bus").get(),
                ModBlocks.BLOCKS.get("fluid_input_hatch").get(),
                ModBlocks.BLOCKS.get("fluid_output_hatch").get(),
                ModBlocks.BLOCKS.get("energy_input_hatch").get(),
                ModBlocks.BLOCKS.get("energy_output_hatch").get());

        assertThat(machine.pattern().get(new BlockPos(-1, -1, -2)).matches(ModBlocks.CASING.get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(-1, -1, -2)).matches(ModBlocks.BLOCKS.get("parallel_controller_max").get().defaultBlockState())).isTrue();
        assertThat(machine.pattern().get(new BlockPos(0, 1, -1)).matches(ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState())).isTrue();
        assertThat(machine.parallelizable()).isTrue();
        assertThat(machine.hasFactory()).isTrue();
    }

    @Test
    void built_in_machines_define_expected_port_tier_requirements() {
        DefaultMachines.ensureRegistered();

        assertThat(requirementIds(MachineRegistry.getMachine(MMCR.id("blast_furnace"))))
                .contains("energy_input_hatch>=ludicrous", "item_input_bus>=normal");
        assertThat(requirementIds(MachineRegistry.getMachine(MMCR.id("alloy_furnace"))))
                .isEmpty();
        assertThat(requirementIds(MachineRegistry.getMachine(MMCR.id("cracker"))))
                .contains("fluid_output_hatch>=huge", "energy_input_hatch>=reinforced", "item_input_bus>=normal");
        assertThat(requirementIds(MachineRegistry.getMachine(MMCR.id("reactor"))))
                .isEmpty();
    }

    private static List<String> requirementIds(Machine machine) {
        return machine.portTierRequirements().requirements().stream()
                .map(PortTierRequirementSpec.Requirement::id)
                .toList();
    }

    private static net.minecraft.world.level.block.state.BlockState crackerPortPredicateState() {
        return ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState();
    }

    private static void installDefaultStructures() {
        MachineStructureRegistry.replaceDynamic(DefaultMachines.structures());
    }
}
