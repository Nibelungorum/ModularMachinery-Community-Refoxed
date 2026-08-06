package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nibelungorum.DefaultMachines;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void bind_default_machine_uses_owning_machine_id() {
        DefaultMachines.ensureRegistered();
        var be = controllerBlockEntityWithoutRunningMinecraftConstructor();

        be.bindDefaultMachine(MMCR.id("blast_furnace"));

        assertThat(be.getMachine()).isSameAs(MachineRegistry.getMachine(MMCR.id("blast_furnace")));
    }

    @Test
    void noHatchesReturnsZeroEnergyAndEmptyFluid() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        assertThat(controller.totalStoredEnergy()).isZero();
        assertThat(controller.totalCapacityEnergy()).isZero();
        assertThat(controller.primaryFluid()).isEqualTo(FluidStack.EMPTY);
        assertThat(controller.primaryOutputFluid()).isEqualTo(FluidStack.EMPTY);
    }

    @Test
    void twoEnergyHatchesSumStoredAndCapacity() throws Exception {
        EnergyInputHatchBlockEntity first = energyHatch(new BlockPos(1, 0, 0));
        EnergyInputHatchBlockEntity second = energyHatch(new BlockPos(2, 0, 0));
        first.getMutableEnergyStorage(null).receiveEnergy(200, false);
        second.getMutableEnergyStorage(null).receiveEnergy(300, false);
        MachineControllerBlockEntity controller = controllerWithEnergyHatches(first, second);

        assertThat(controller.totalStoredEnergy()).isEqualTo(500);
        assertThat(controller.totalCapacityEnergy()).isEqualTo(first.getMutableEnergyStorage(null).getMaxEnergyStored() * 2L);
    }

    @Test
    void primaryFluidReturnsFirstNonEmptyInputHatch() throws Exception {
        Fluids.WATER.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
        FluidInputHatchBlockEntity input = fluidInputHatch(new BlockPos(1, 0, 0));
        input.getFluidTank(null).setFluid(new FluidStack(Fluids.WATER, 500));
        MachineControllerBlockEntity controller = controllerWithFluidHatch(input);

        assertThat(controller.primaryFluid().getFluid()).isEqualTo(Fluids.WATER);
        assertThat(controller.primaryFluid().getAmount()).isEqualTo(500);
    }

    @Test
    void primaryOutputFluidReturnsFirstNonEmptyOutputHatch() throws Exception {
        Fluids.LAVA.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
        FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(1, 0, 0));
        output.getFluidTank(null).setFluid(new FluidStack(Fluids.LAVA, 250));
        MachineControllerBlockEntity controller = controllerWithFluidHatch(output);

        assertThat(controller.primaryOutputFluid().getFluid()).isEqualTo(Fluids.LAVA);
        assertThat(controller.primaryOutputFluid().getAmount()).isEqualTo(250);
    }

    @Test
    void matching_structure_without_requirements_forms() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("no_requirement_machine"), "No Requirement", pattern);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(controller.getFoundMachine()).isSameAs(machine);
        assertThat(controller.getLastFormationFailure()).isNull();
        assertThat(controller.isFormed()).isTrue();
    }

    @Test
    void matching_structure_caches_compiled_pattern_and_uses_candidate_component_positions() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = anyItemOrEnergyInputPattern();
        DynamicMachine machine = new DynamicMachine(MMCR.id("compiled_controller_machine"), "Compiled Controller", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(compiledPattern(controller)).isSameAs(MachineRegistry.getCompiled(machine.registryName()));
        assertThat(controller.getComponents()).hasSize(1);
        assertThat(controller.getComponents().getFirst().getRelativePos()).isEqualTo(new BlockPos(1, 0, 0));
    }

    @Test
    void structure_version_changes_when_structure_forms_and_resets() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = anyItemOrEnergyInputPattern();
        DynamicMachine machine = new DynamicMachine(MMCR.id("versioned_controller_machine"), "Versioned Controller", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));
        long initial = controller.getStructureVersion();

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);
        invokeResetMachine(controller);

        assertThat(formed).isTrue();
        assertThat(controller.getStructureVersion()).isEqualTo(initial + 2);
    }

    @Test
    void stale_recipe_context_is_refreshed_after_structure_reforms() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = anyItemOrEnergyInputPattern();
        DynamicMachine machine = new DynamicMachine(MMCR.id("restored_active_machine"), "Restored Active", pattern);
        MachineRegistry.register(machine);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("restored_active_recipe"), machine.registryName(), 100, List.of(), List.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);
        setField(MachineControllerBlockEntity.class, controller, "active", active);
        setField(MachineControllerBlockEntity.class, controller, "context", new RecipeCraftingContext(controller));

        controller.serverTick();

        assertThat(controller.isFormed()).isTrue();
        assertThat(controller.getActive()).isSameAs(active);
        assertThat(controller.getTickCounter()).isEqualTo(1);
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

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isFalse();
        assertThat(controller.getFoundMachine()).isNull();
        assertThat(controller.isFormed()).isFalse();
        assertThat(controller.getComponents()).isEmpty();
        assertThat(controller.getLastFormationFailure()).isNotNull();
        assertThat(controller.getLastFormationFailure().portId()).isEqualTo(PortKinds.ENERGY_INPUT.id());
    }

    @Test
    void structure_mismatch_diagnostic_includes_expected_and_actual_block_details() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("diagnostic_machine"), "Diagnostic", pattern);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, energyHatch(controllerPos.offset(1, 0, 0)));

        String diagnostic = MachineControllerBlockEntity.structureMismatchDiagnostic(
                machine,
                Direction.SOUTH,
                BlockArrayCache.get(machine.pattern(), Direction.SOUTH),
                levelOf(controller),
                controllerPos);

        assertThat(diagnostic)
                .contains("machine=mmcr:diagnostic_machine")
                .contains("facing=SOUTH")
                .contains("controllerPos=BlockPos{x=10, y=4, z=10}")
                .contains("relativePos=BlockPos{x=1, y=0, z=0}")
                .contains("worldPos=BlockPos{x=11, y=4, z=10}")
                .contains("expected=OfBlock")
                .contains("actualState=Block")
                .contains("actualBlockEntity=EnergyInputHatchBlockEntity");
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

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(controller.getFoundMachine()).isSameAs(machine);
        assertThat(controller.getLastFormationFailure()).isNull();
        assertThat(controller.isFormed()).isTrue();
    }

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

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

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

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(controller.getLastFormationFailure()).isNull();
    }

    @Test
    void server_tick_keeps_formation_failure_observable_after_rejection() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("server_tick_requires_energy_machine"),
                "Requires Energy",
                pattern,
                cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("server_tick_requires_energy_machine")),
                PortRequirementSpec.builder().min(PortKinds.ENERGY_INPUT.id(), 1).build());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);

        controller.serverTick();

        assertThat(controller.isFormed()).isFalse();
        assertThat(controller.getLastFormationFailure()).isNotNull();
        assertThat(controller.getLastFormationFailure().portId()).isEqualTo(PortKinds.ENERGY_INPUT.id());
    }

    @Test
    void cached_formed_structure_revalidates_required_ports() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        BlockArray pattern = anyItemOrEnergyInputPattern();
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("cached_requires_energy_machine"),
                "Requires Energy",
                pattern,
                cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("cached_requires_energy_machine")),
                PortRequirementSpec.builder().min(PortKinds.ENERGY_INPUT.id(), 1).build());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, energyHatch(portPos));
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        for (int i = 0; i < Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS; i++) {
            controller.serverTick();
        }
        assertThat(controller.isFormed()).isTrue();

        Level level = levelOf(controller);
        ItemInputBusBlockEntity replacement = itemInputBus(portPos);
        setField(BlockEntity.class, replacement, "level", level);
        level.setBlock(portPos, blockForPort(replacement).defaultBlockState(), 3);
        LevelStub.putBlockEntity(level, replacement);

        controller.serverTick();

        assertThat(controller.isFormed()).isFalse();
        assertThat(controller.getLastFormationFailure()).isNotNull();
        assertThat(controller.getLastFormationFailure().portId()).isEqualTo(PortKinds.ENERGY_INPUT.id());
    }

    @Test
    void cached_formed_dynamic_replacement_structure_stays_formed_after_recheck() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos replacementPos = controllerPos.offset(1, 0, 0);
        BlockPos relativeReplacementPos = new BlockPos(1, 0, 0);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        var replacement = new SingleBlockModifierReplacement(
                "cached_replacement",
                relativeReplacementPos,
                new BlockPredicate.OfBlock(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("energy_input_hatch").get()),
                List.of(),
                "",
                net.minecraft.world.item.ItemStack.EMPTY);
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("cached_replacement_machine"),
                "Cached Replacement",
                pattern,
                cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("cached_replacement_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(relativeReplacementPos, List.of(replacement)));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, energyHatch(replacementPos));
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        controller.serverTick();

        assertThat(controller.isFormed()).isTrue();
        assertThat(controller.getFoundMachine()).isSameAs(machine);
    }

    @Test
    void formed_controller_exposes_only_matching_position_modifiers() throws Exception {
        var replacement = replacementAt(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK, "speed", 2F);
        var machine = machineWithReplacements(replacement);
        MachineDefinitions.register(machine);

        MachineControllerBlockEntity controller = controllerFor(machine);
        placeControllerAndReplacement(controller, machine, Blocks.GOLD_BLOCK);
        tickUntilFormed(controller, machine);

        assertThat(controller.getFoundModifiers()).containsKey("speed");
        assertThat(controller.foundModifierList()).extracting(RecipeModifier::getModifier)
                .containsExactly(2F);
    }

    @Test
    void duplicate_modifier_name_is_applied_once_and_reset_clears_it() throws Exception {
        var first = replacementAt(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK, "speed", 2F);
        var second = replacementAt(new BlockPos(2, 0, 0), Blocks.DIAMOND_BLOCK, "speed", 4F);
        var machine = machineWithReplacements(first, second);
        MachineDefinitions.register(machine);

        MachineControllerBlockEntity controller = controllerFor(machine);
        placeControllerAndReplacement(controller, machine, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK);
        tickUntilFormed(controller, machine);

        assertThat(controller.getFoundModifiers()).containsKey("speed");
        assertThat(controller.getFoundModifiers().get("speed"))
                .extracting(RecipeModifier::getModifier)
                .containsExactly(2F);
        breakStructureBlock(controller);

        assertThat(controller.getFoundModifiers()).isEmpty();
    }

    @Test
    void cached_formed_recheck_refreshes_matching_replacement_modifiers() throws Exception {
        var first = replacementAt(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK, "speed", 2F);
        var second = replacementAt(new BlockPos(1, 0, 0), Blocks.DIAMOND_BLOCK, "speed", 4F);
        var machine = machineWithReplacements(first, second);
        MachineDefinitions.register(machine);

        MachineControllerBlockEntity controller = controllerFor(machine);
        Level level = levelOf(controller);
        BlockPos replacementPos = controller.getBlockPos().offset(1, 0, 0);
        placeControllerAndReplacement(controller, machine, Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK);
        level.setBlock(replacementPos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        tickUntilFormed(controller, machine);
        level.setBlock(replacementPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);

        invokeCheckStructure(controller);

        assertThat(controller.isFormed()).isTrue();
        assertThat(controller.getFoundModifiers().get("speed"))
                .extracting(RecipeModifier::getModifier)
                .containsExactly(4F);
    }

    @Test
    void set_machine_clears_matched_modifier_snapshot() throws Exception {
        var replacement = replacementAt(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK, "speed", 2F);
        var machine = machineWithReplacements(replacement);
        var other = new DynamicMachine(MMCR.id("replacement_target_machine"), "Replacement Target", onePortPattern(Blocks.IRON_BLOCK));
        MachineDefinitions.register(machine);

        MachineControllerBlockEntity controller = controllerFor(machine);
        placeControllerAndReplacement(controller, machine, Blocks.GOLD_BLOCK);
        tickUntilFormed(controller, machine);
        assertThat(controller.getFoundModifiers()).containsKey("speed");

        controller.setMachine(other);

        assertThat(controller.getFoundModifiers()).isEmpty();
    }

    @Test
    void vertical_non_symmetric_machine_uses_placed_roll_facing_only() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        var defaults = cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("vertical_non_symmetric_machine"));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("vertical_non_symmetric_machine"),
                "Vertical Non Symmetric",
                pattern,
                new cn.howxu.mmcr.api.machine.MachineControllerSpec(
                        defaults.id(),
                        defaults.frontTexture(),
                        defaults.sideTexture(),
                        defaults.topTexture(),
                        defaults.bottomTexture(),
                        true,
                        false));
        MachineControllerBlockEntity controller = controllerForFormation(
                machine,
                controllerPos,
                Direction.UP,
                Direction.SOUTH,
                itemInputBus(controllerPos.offset(1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.UP);

        assertThat(formed).isTrue();
        assertThat(controller.getFoundPattern().pattern()).containsKey(new BlockPos(1, 0, 0));
    }

    @Test
    void vertical_non_symmetric_machine_rejects_other_rolls() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        var defaults = cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("vertical_non_symmetric_reject_machine"));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("vertical_non_symmetric_reject_machine"),
                "Vertical Non Symmetric Reject",
                pattern,
                new cn.howxu.mmcr.api.machine.MachineControllerSpec(
                        defaults.id(),
                        defaults.frontTexture(),
                        defaults.sideTexture(),
                        defaults.topTexture(),
                        defaults.bottomTexture(),
                        true,
                        false));
        MachineControllerBlockEntity controller = controllerForFormation(
                machine,
                controllerPos,
                Direction.UP,
                Direction.NORTH,
                itemInputBus(controllerPos.offset(1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.UP);

        assertThat(formed).isFalse();
        assertThat(controller.getFoundMachine()).isNull();
    }

    @Test
    void vertical_symmetric_machine_tries_all_rolls_and_caches_matching_pattern() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        var defaults = cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("vertical_symmetric_machine"));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("vertical_symmetric_machine"),
                "Vertical Symmetric",
                pattern,
                new cn.howxu.mmcr.api.machine.MachineControllerSpec(
                        defaults.id(),
                        defaults.frontTexture(),
                        defaults.sideTexture(),
                        defaults.topTexture(),
                        defaults.bottomTexture(),
                        true,
                        true));
        MachineControllerBlockEntity controller = controllerForFormation(
                machine,
                controllerPos,
                Direction.UP,
                Direction.NORTH,
                itemInputBus(controllerPos.offset(0, 0, 1)));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.UP);

        assertThat(formed).isTrue();
        assertThat(controller.getFoundPattern().pattern()).containsKey(new BlockPos(0, 0, 1));
    }

    @Test
    void require_vertical_machine_rejects_matching_horizontal_structure() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        var defaults = cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("requires_vertical_machine"));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("requires_vertical_machine"),
                "Requires Vertical",
                pattern,
                new cn.howxu.mmcr.api.machine.MachineControllerSpec(
                        defaults.id(),
                        defaults.frontTexture(),
                        defaults.sideTexture(),
                        defaults.topTexture(),
                        defaults.bottomTexture(),
                        true,
                        false,
                        true));
        MachineControllerBlockEntity controller = controllerForFormation(
                machine,
                controllerPos,
                Direction.SOUTH,
                Direction.NORTH,
                itemInputBus(controllerPos.offset(1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isFalse();
        assertThat(controller.getFoundMachine()).isNull();
    }

    @Test
    void state_bound_machine_does_not_scan_other_registered_machines_after_mismatch() throws Exception {
        var defaults = cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("bound_machine"));
        DynamicMachine boundMachine = new DynamicMachine(
                MMCR.id("bound_machine"),
                "Bound Machine",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()),
                new cn.howxu.mmcr.api.machine.MachineControllerSpec(
                        MMCR.id("bound_machine_controller"),
                        defaults.frontTexture(),
                        defaults.sideTexture(),
                        defaults.topTexture(),
                        defaults.bottomTexture(),
                        defaults.allowVerticalFacing(),
                        defaults.fullyRotationallySymmetric()),
                PortRequirementSpec.none());
        DynamicMachine otherMachine = new DynamicMachine(
                MMCR.id("other_machine"),
                "Other Machine",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("energy_input_hatch").get()));
        MachineRegistry.register(boundMachine);
        MachineRegistry.register(otherMachine);
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        MachineControllerBlockEntity controller = controllerForFormation(boundMachine, controllerPos, energyHatch(controllerPos.offset(1, 0, 0)));

        controller.serverTick();

        assertThat(controller.isFormed()).isFalse();
        assertThat(controller.getFoundMachine()).isNull();
    }

    @Test
    void failedRecipeSearchUsesRetryDelayBeforeScanningAgain() throws Exception {
        var machine = new DynamicMachine(MMCR.id("retry_machine"), "Retry Machine", onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()));
        MachineRegistry.register(machine);
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("retry_recipe"),
                machine.registryName(),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT), 1, net.minecraft.world.item.ItemStack.EMPTY)));
        RecipeRegistry.register(recipe);
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        controller.serverTick();
        assertThat(controller.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        controller.setLastFailureUnloc(null);

        controller.serverTick();

        assertThat(controller.getLastFailureUnloc()).isNull();
    }

    @Test
    void recipeSearchExceptionDoesNotBreakControllerTick() throws Exception {
        net.minecraft.world.item.Items.IRON_INGOT.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
        var machine = new DynamicMachine(MMCR.id("exception_machine"), "Exception Machine", anyItemOutputPattern());
        MachineRegistry.register(machine);
        RecipeRegistry.register(new MachineRecipe(
                MMCR.id("exception_recipe"),
                machine.registryName(),
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, net.minecraft.world.item.Items.IRON_INGOT.getDefaultInstance()))));
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemOutputBusBlockEntity outputBus = itemOutputBus(controllerPos.offset(1, 0, 0));
        setField(ItemBusBlockEntity.class, outputBus, "handler", null);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, outputBus);
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);

        controller.serverTick();

        assertThat(controller.isFormed()).isTrue();
        assertThat(controller.getActive()).isNull();
        assertThat(controller.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_SEARCH_EXCEPTION);
    }

    @Test
    void block_change_inside_compiled_bounds_marks_formed_structure_dirty() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("dirty_bounds_machine"), "Dirty Bounds", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(portPos));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isFalse();

        controller.onStructureBlockChanged(portPos);

        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isTrue();
    }

    @Test
    void static_block_change_marker_marks_matching_formed_controller_dirty() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("static_dirty_machine"), "Static Dirty", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(portPos));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isFalse();

        MachineControllerBlockEntity.markStructureDirty(levelOf(controller), portPos);

        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isTrue();
    }

    @Test
    void chunk_unload_inside_compiled_bounds_marks_dirty_and_pauses_active_recipe() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("chunk_dirty_machine"), "Chunk Dirty", pattern);
        MachineRegistry.register(machine);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("chunk_dirty_recipe"), machine.registryName(), 100, List.of(), List.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);
        setField(MachineControllerBlockEntity.class, controller, "active", active);
        setField(MachineControllerBlockEntity.class, controller, "context", new RecipeCraftingContext(controller));

        MachineControllerBlockEntity.markStructureChunkDirty(levelOf(controller), new net.minecraft.world.level.ChunkPos(controllerPos.getX() >> 4, controllerPos.getZ() >> 4));

        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isTrue();
        assertThat(controller.getActive()).isNull();
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "pausedActive")).isSameAs(active);
    }

    @Test
    void block_change_outside_compiled_bounds_does_not_mark_formed_structure_dirty() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("clean_bounds_machine"), "Clean Bounds", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        controller.onStructureBlockChanged(controllerPos.offset(8, 0, 0));

        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isFalse();
    }

    private static MachineControllerBlockEntity controllerBlockEntityWithoutRunningMinecraftConstructor() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
            initializeComponents(controller);
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate MachineControllerBlockEntity for binding test", e);
        }
    }

    private static void initializeComponents(MachineControllerBlockEntity controller) throws ReflectiveOperationException {
        Field componentsField = MachineControllerBlockEntity.class.getDeclaredField("components");
        componentsField.setAccessible(true);
        componentsField.set(controller, new ArrayList<>());
        Field foundModifiersField = MachineControllerBlockEntity.class.getDeclaredField("foundModifiers");
        foundModifiersField.setAccessible(true);
        foundModifiersField.set(controller, new LinkedHashMap<>());
    }

    private static EnergyInputHatchBlockEntity energyHatch(BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            EnergyInputHatchBlockEntity hatch = (EnergyInputHatchBlockEntity) unsafe.allocateInstance(EnergyInputHatchBlockEntity.class);
            setField(BlockEntity.class, hatch, "type", null);
            setField(BlockEntity.class, hatch, "worldPosition", pos);
            setField(BlockEntity.class, hatch, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            setField(EnergyHatchBlockEntity.class, hatch, "storage", new EnergyStorage(1000, 1000, 1000));
            return hatch;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate energy hatch", e);
        }
    }

    private static ItemInputBusBlockEntity itemInputBus(BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            ItemInputBusBlockEntity bus = (ItemInputBusBlockEntity) unsafe.allocateInstance(ItemInputBusBlockEntity.class);
            setField(BlockEntity.class, bus, "type", null);
            setField(BlockEntity.class, bus, "worldPosition", pos);
            setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
            return bus;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate item input bus", e);
        }
    }

    private static ItemOutputBusBlockEntity itemOutputBus(BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            ItemOutputBusBlockEntity bus = (ItemOutputBusBlockEntity) unsafe.allocateInstance(ItemOutputBusBlockEntity.class);
            setField(BlockEntity.class, bus, "type", null);
            setField(BlockEntity.class, bus, "worldPosition", pos);
            setField(BlockEntity.class, bus, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            return bus;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate item output bus", e);
        }
    }

    private static FluidInputHatchBlockEntity fluidInputHatch(BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            FluidInputHatchBlockEntity hatch = (FluidInputHatchBlockEntity) unsafe.allocateInstance(FluidInputHatchBlockEntity.class);
            setField(BlockEntity.class, hatch, "type", null);
            setField(BlockEntity.class, hatch, "worldPosition", pos);
            setField(BlockEntity.class, hatch, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            setField(FluidHatchBlockEntity.class, hatch, "tank", new FluidTank(8000) {
                @Override protected void onContentsChanged() { }
            });
            return hatch;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate fluid input hatch", e);
        }
    }

    private static FluidOutputHatchBlockEntity fluidOutputHatch(BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            FluidOutputHatchBlockEntity hatch = (FluidOutputHatchBlockEntity) unsafe.allocateInstance(FluidOutputHatchBlockEntity.class);
            setField(BlockEntity.class, hatch, "type", null);
            setField(BlockEntity.class, hatch, "worldPosition", pos);
            setField(BlockEntity.class, hatch, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            setField(FluidHatchBlockEntity.class, hatch, "tank", new FluidTank(8000) {
                @Override protected void onContentsChanged() { }
            });
            return hatch;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate fluid output hatch", e);
        }
    }

    private static MachineControllerBlockEntity controllerWithEnergyHatches(EnergyInputHatchBlockEntity... hatches) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        setField(BlockEntity.class, controller, "level", LevelStub.createWithBlockEntities(List.of(hatches)));
        for (EnergyInputHatchBlockEntity hatch : hatches) {
            setField(BlockEntity.class, hatch, "level", LevelStub.createWithBlockEntities(List.of(hatches)));
        }
        Field componentsField = MachineControllerBlockEntity.class.getDeclaredField("components");
        componentsField.setAccessible(true);
        List<ProcessingComponent> list = (List<ProcessingComponent>) componentsField.get(controller);
        list.clear();
        for (EnergyInputHatchBlockEntity hatch : hatches) {
            MachineComponent port = new MachineComponent(PortKinds.ENERGY_INPUT, cn.howxu.mmcr.util.IOType.INPUT);
            list.add(new ProcessingComponent(port, hatch, hatch.getBlockPos(), BlockPos.ZERO, (String) null));
        }
        return controller;
    }

    private static MachineControllerBlockEntity controllerWithFluidHatch(net.minecraft.world.level.block.entity.BlockEntity hatch) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        setField(BlockEntity.class, controller, "level", LevelStub.createWithBlockEntities(List.of(hatch)));
        setField(BlockEntity.class, hatch, "level", LevelStub.createWithBlockEntities(List.of(hatch)));
        Field componentsField = MachineControllerBlockEntity.class.getDeclaredField("components");
        componentsField.setAccessible(true);
        List<ProcessingComponent> list = (List<ProcessingComponent>) componentsField.get(controller);
        list.clear();
        MachineComponent port;
        if (hatch instanceof FluidInputHatchBlockEntity) {
            port = new MachineComponent(PortKinds.FLUID_INPUT, cn.howxu.mmcr.util.IOType.INPUT);
        } else {
            port = new MachineComponent(PortKinds.FLUID_OUTPUT, cn.howxu.mmcr.util.IOType.OUTPUT);
        }
        list.add(new ProcessingComponent(port, hatch, hatch.getBlockPos(), BlockPos.ZERO, (String) null));
        return controller;
    }

    private static BlockArray onePortPattern(Block portBlock) {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(portBlock));
        return new BlockArray(blocks);
    }

    private static BlockArray anyItemOrEnergyInputPattern() {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPredicate.OfBlock(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("energy_input_hatch").get()))));
        return new BlockArray(blocks);
    }

    private static BlockArray anyItemOutputPattern() {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_output_bus").get()))));
        return new BlockArray(blocks);
    }

    private static SingleBlockModifierReplacement replacementAt(
            BlockPos pos, Block block, String name, float value) {
        return new SingleBlockModifierReplacement(
                name, pos, new BlockPredicate.OfBlock(block),
                List.of(new RecipeModifier("item", RecipeModifier.IOType.INPUT,
                        value, RecipeModifier.Operation.ADD, false)),
                "", ItemStack.EMPTY);
    }

    private static DynamicMachine machineWithReplacements(
            SingleBlockModifierReplacement... replacements) {
        Identifier id = MMCR.id("position_modifier_test");
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        pattern.put(BlockPos.ZERO, new BlockPredicate.Any());
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierMap = new LinkedHashMap<>();
        for (SingleBlockModifierReplacement replacement : replacements) {
            pattern.put(replacement.getPos(), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
            modifierMap.computeIfAbsent(replacement.getPos(), ignored -> new ArrayList<>()).add(replacement);
        }
        return new DynamicMachine(id, "Position Modifier Test", new BlockArray(pattern),
                MachineControllerSpec.defaultsFor(id), PortRequirementSpec.none(), List.of(), modifierMap);
    }

    private static MachineControllerBlockEntity controllerFor(DynamicMachine machine) throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        return controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(8, 0, 0)));
    }

    private static void placeControllerAndReplacement(
            MachineControllerBlockEntity controller,
            DynamicMachine machine,
            Block... replacementBlocks) throws Exception {
        Level level = levelOf(controller);
        BlockPos controllerPos = controller.getBlockPos();
        Map<BlockPos, Block> blocks = new LinkedHashMap<>();
        level.setBlock(controllerPos, controller.getBlockState(), 3);
        for (var entry : machine.pattern().pattern().entrySet()) {
            if (entry.getKey().equals(BlockPos.ZERO)) continue;
            if (entry.getValue() instanceof BlockPredicate.OfBlock of) {
                blocks.put(controllerPos.offset(entry.getKey()), of.block());
            }
        }
        int index = 0;
        for (List<SingleBlockModifierReplacement> replacements : machine.modifierReplacements().values()) {
            for (SingleBlockModifierReplacement replacement : replacements) {
                blocks.put(controllerPos.offset(replacement.getPos()), replacementBlockFor(replacement, replacementBlocks[index++]));
            }
        }
        for (var entry : blocks.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue().defaultBlockState(), 3);
        }
        LevelStub.putBlockEntity(level, controller);
    }

    private static Block replacementBlockFor(SingleBlockModifierReplacement replacement, Block fallback) {
        if (replacement.getReplacement().matches(fallback.defaultBlockState())) return fallback;
        if (replacement.getReplacement() instanceof BlockPredicate.OfBlock of) return of.block();
        return fallback;
    }

    private static void tickUntilFormed(
            MachineControllerBlockEntity controller,
            DynamicMachine machine) throws Exception {
        for (int i = 0; i < 4 && !controller.isFormed(); i++) {
            invokeTryFormMachine(controller, machine, Direction.SOUTH);
        }
        assertThat(controller.isFormed()).isTrue();
    }

    private static void breakStructureBlock(MachineControllerBlockEntity controller) throws Exception {
        Level level = levelOf(controller);
        level.setBlock(controller.getBlockPos().offset(1, 0, 0), Blocks.AIR.defaultBlockState(), 3);
        controller.onStructureBlockChanged(controller.getBlockPos().offset(1, 0, 0));
        invokeResetMachine(controller);
    }

    private static MachineControllerBlockEntity controllerForFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            IOPortBlockEntity port) throws Exception {
        return controllerForFormation(machine, controllerPos, Direction.SOUTH, Direction.NORTH, port);
    }

    private static MachineControllerBlockEntity controllerForFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            Direction facing,
            Direction rollFacing,
            IOPortBlockEntity port) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        var controllerState = controllerBlock.defaultBlockState()
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FORMED, false)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING, facing)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.ROLL_FACING, rollFacing);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(port.getBlockPos(), blockForPort(port));
        Level level = LevelStub.create(blocks, List.of(controller, port));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, port, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity controllerForDefaultBlastFurnace(
            DynamicMachine machine,
            BlockPos controllerPos,
            IOPortBlockEntity first,
            IOPortBlockEntity second,
            IOPortBlockEntity third) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        var controllerState = controllerBlock.defaultBlockState()
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FORMED, false)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.ROLL_FACING, Direction.NORTH);
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

        Level level = LevelStub.create(blocks, List.of(controller, first, second, third));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, first, "level", level);
        setField(BlockEntity.class, second, "level", level);
        setField(BlockEntity.class, third, "level", level);
        return controller;
    }

    private static Block blockForPort(IOPortBlockEntity port) {
        return cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get(port.kind().id()).get();
    }

    private static cn.howxu.mmcr.internal.block.MachineControllerBlock testControllerBlock(DynamicMachine machine) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var block = (cn.howxu.mmcr.internal.block.MachineControllerBlock) unsafe.allocateInstance(cn.howxu.mmcr.internal.block.MachineControllerBlock.class);
        setField(cn.howxu.mmcr.internal.block.MachineControllerBlock.class, block, "machineId", machine.registryName());
        setField(
                net.minecraft.world.level.block.state.BlockBehaviour.class,
                block,
                "properties",
                net.minecraft.world.level.block.Blocks.IRON_BLOCK.properties());
        var builder = new net.minecraft.world.level.block.state.StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState>(block);
        builder.add(
                cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING,
                cn.howxu.mmcr.internal.block.MachineControllerBlock.ROLL_FACING,
                cn.howxu.mmcr.internal.block.MachineControllerBlock.FORMED,
                cn.howxu.mmcr.internal.block.MachineControllerBlock.ACTIVE);
        var stateDefinition = builder.create(Block::defaultBlockState, net.minecraft.world.level.block.state.BlockState::new);
        setField(Block.class, block, "stateDefinition", stateDefinition);
        setField(Block.class, block, "defaultBlockState", stateDefinition.any()
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FACING, Direction.NORTH)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.ROLL_FACING, Direction.NORTH)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.FORMED, false)
                .setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.ACTIVE, false));
        return block;
    }

    private static boolean invokeTryFormMachine(MachineControllerBlockEntity controller, DynamicMachine machine, Direction facing) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("tryFormMachine", cn.howxu.mmcr.api.machine.Machine.class, Direction.class);
        method.setAccessible(true);
        return (boolean) method.invoke(controller, machine, facing);
    }

    private static void invokeResetMachine(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("resetMachine");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static void invokeCheckStructure(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("checkStructure");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static CompiledMachinePattern compiledPattern(MachineControllerBlockEntity controller) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("foundCompiledPattern");
        field.setAccessible(true);
        return (CompiledMachinePattern) field.get(controller);
    }

    private static Level levelOf(BlockEntity blockEntity) throws Exception {
        Field field = BlockEntity.class.getDeclaredField("level");
        field.setAccessible(true);
        return (Level) field.get(blockEntity);
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object fieldValue(Class<?> declaringClass, Object target, String name) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
