package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void bind_default_machine_uses_owning_machine_id() {
        TestBootstrap.registerRuntimeBuiltins();
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
    void max_parallelism_uses_parallel_controller_only_for_parallelizable_machines() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        var parallelMachine = new DynamicMachine(
                MMCR.id("parallel_test_machine"),
                "Parallel Test",
                onePortPattern(Blocks.IRON_BLOCK),
                MachineControllerSpec.defaultsFor(MMCR.id("parallel_test_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                64,
                true,
                false,
                1);
        var nonParallelMachine = new DynamicMachine(
                MMCR.id("non_parallel_test_machine"),
                "Non Parallel Test",
                onePortPattern(Blocks.IRON_BLOCK));

        assertThat(controller.getMaxParallelism()).isEqualTo(1);

        setField(MachineControllerBlockEntity.class, controller, "machine", parallelMachine);
        addParallelComponent(controller, ParallelTier.X16);
        assertThat(controller.getMaxParallelism()).isEqualTo(16);
        assertThat(controller.parallelControllerCount()).isEqualTo(1);
        assertThat(controller.currentParallelism()).isZero();

        ParallelControllerBlockEntity parallel = (ParallelControllerBlockEntity) controller.getComponents().getFirst().getContainer();
        parallel.setCurrentParallelism(7);
        assertThat(parallel.currentParallelism()).isEqualTo(7);
        assertThat(controller.getMaxParallelism()).isEqualTo(7);
        parallel.setCurrentParallelism(99);
        assertThat(parallel.currentParallelism()).isEqualTo(16);

        setField(MachineControllerBlockEntity.class, controller, "machine", nonParallelMachine);
        assertThat(controller.getMaxParallelism()).isEqualTo(1);
    }

    @Test
    void max_parallelism_sums_all_parallel_controllers_up_to_machine_limit() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        var parallelMachine = new DynamicMachine(
                MMCR.id("summed_parallel_test_machine"),
                "Summed Parallel Test",
                onePortPattern(Blocks.IRON_BLOCK),
                MachineControllerSpec.defaultsFor(MMCR.id("summed_parallel_test_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                64,
                true,
                false,
                1);

        setField(MachineControllerBlockEntity.class, controller, "machine", parallelMachine);
        addParallelComponent(controller, ParallelTier.X4);
        addParallelComponent(controller, ParallelTier.X4);

        assertThat(controller.parallelControllerCount()).isEqualTo(2);
        assertThat(controller.getMaxParallelism()).isEqualTo(8);
    }

    @Test
    void effective_threads_require_multithreading_and_parallelism_requires_parallel_flag() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        addFactorySchedulerComponent(controller, factoryController(new BlockPos(1, 0, 0), 4));
        addParallelComponent(controller, ParallelTier.X16);

        var threadsOnly = new DynamicMachine(
                MMCR.id("threads_only_runtime_machine"),
                "Threads Only Runtime",
                onePortPattern(Blocks.IRON_BLOCK),
                MachineControllerSpec.defaultsFor(MMCR.id("threads_only_runtime_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                1);
        var parallelOnly = new DynamicMachine(
                MMCR.id("parallel_only_runtime_machine"),
                "Parallel Only Runtime",
                onePortPattern(Blocks.IRON_BLOCK),
                MachineControllerSpec.defaultsFor(MMCR.id("parallel_only_runtime_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                8,
                true,
                false,
                1);

        setField(MachineControllerBlockEntity.class, controller, "machine", threadsOnly);
        assertThat(controller.effectiveFactoryThreadLimit()).isEqualTo(5);
        assertThat(controller.getMaxParallelism()).isEqualTo(1);

        setField(MachineControllerBlockEntity.class, controller, "machine", parallelOnly);
        assertThat(controller.effectiveFactoryThreadLimit()).isEqualTo(1);
        assertThat(controller.getMaxParallelism()).isEqualTo(8);
    }

    @Test
    void formed_parallel_controller_is_discovered_from_structure_snapshot() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var machine = new DynamicMachine(
                MMCR.id("formed_parallel_test_machine"),
                "Formed Parallel Test",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get(ParallelTier.X16.idSuffix()).get()),
                MachineControllerSpec.defaultsFor(MMCR.id("formed_parallel_test_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                64,
                true,
                false,
                1);
        MachineControllerBlockEntity controller = controllerForParallelFormation(
                machine,
                controllerPos,
                parallelController(ParallelTier.X16, controllerPos.offset(1, 0, 0)));

        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        assertThat(controller.getComponents()).hasSize(1);
        assertThat(controller.getComponents().getFirst().getContainer()).isInstanceOf(ParallelControllerBlockEntity.class);
        assertThat(controller.getMaxParallelism()).isEqualTo(16);
    }

    @Test
    void formed_factory_controller_is_discovered_only_for_factory_machines() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var factoryMachine = new DynamicMachine(
                MMCR.id("formed_factory_test_machine"),
                "Formed Factory Test",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("factory_controller").get()),
                MachineControllerSpec.defaultsFor(MMCR.id("formed_factory_test_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                4);
        FactorySchedulerBlockEntity factory = factoryController(controllerPos.offset(1, 0, 0));
        MachineControllerBlockEntity controller = controllerForFactoryFormation(factoryMachine, controllerPos, factory);

        assertThat(invokeTryFormMachine(controller, factoryMachine, Direction.SOUTH)).isTrue();

        assertThat(controller.getFactoryController()).isSameAs(factory);

        var nonFactoryMachine = new DynamicMachine(
                MMCR.id("formed_non_factory_test_machine"),
                "Formed Non Factory Test",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("factory_controller").get()));
        setField(MachineControllerBlockEntity.class, controller, "machine", nonFactoryMachine);
        assertThat(controller.getFactoryController()).isNull();
    }

    @Test
    void formed_factory_controller_uses_own_thread_disperser_count() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var factoryMachine = new DynamicMachine(
                MMCR.id("formed_factory_limit_test_machine"),
                "Formed Factory Limit Test",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("factory_controller").get()),
                MachineControllerSpec.defaultsFor(MMCR.id("formed_factory_limit_test_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                3);
        FactorySchedulerBlockEntity factory = factoryController(controllerPos.offset(1, 0, 0), 2);
        MachineControllerBlockEntity controller = controllerForFactoryFormation(factoryMachine, controllerPos, factory);

        assertThat(invokeTryFormMachine(controller, factoryMachine, Direction.SOUTH)).isTrue();

        assertThat(controller.getFactoryController()).isSameAs(factory);
        assertThat(controller.factorySchedulerThreadCount()).isEqualTo(3);
        assertThat(factory.threadLimit()).isEqualTo(3);
        addFactoryLane(factory);
        addFactoryLane(factory);
        addFactoryLane(factory);
        assertThat(startFactoryLane(factory)).isFalse();
        assertThat(factory.activeLaneCount()).isEqualTo(3);
    }

    @Test
    void factory_controller_threads_are_summed_and_saturated() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);

        assertThat(controller.factorySchedulerThreadCount()).isZero();

        addFactorySchedulerComponent(controller, factoryController(new BlockPos(1, 0, 0), 64));
        addFactorySchedulerComponent(controller, factoryController(new BlockPos(2, 0, 0), 3));

        assertThat(controller.factorySchedulerThreadCount()).isEqualTo(69);

        addFactorySchedulerComponent(controller, factoryController(new BlockPos(3, 0, 0), Integer.MAX_VALUE));

        assertThat(controller.factorySchedulerThreadCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void factory_controller_starts_multiple_recipe_lanes_on_server_tick() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(MMCR.id("factory_lane_smelt_machine"), 3, 3);
        registerItemRecipe("factory_lane_smelt", fixture.machine().registryName(), 20, 0);

        fixture.controller().serverTick();

        assertThat(fixture.factory().threadLimit()).isEqualTo(3);
        assertThat(fixture.factory().activeLaneCount()).isEqualTo(3);
        assertThat(fixture.controller().getActive()).isNull();
        assertThat(fixture.controller().isRuntimeActive()).isTrue();
        assertThat(fixture.controller().activeFactoryThreadCount()).isEqualTo(3);
        assertThat(fixture.controller().currentParallelism()).isEqualTo(1);
    }

    @Test
    void factory_lanes_do_not_overconsume_shared_inputs() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(MMCR.id("factory_lane_limited_machine"), 3, 2);
        registerItemRecipe("factory_lane_limited", fixture.machine().registryName(), 20, 0);

        fixture.controller().serverTick();

        assertThat(fixture.factory().activeLaneCount()).isEqualTo(2);
        assertThat(countItem(fixture.inputBus(), Items.IRON_INGOT)).isZero();
    }

    @Test
    void non_factory_machine_still_uses_single_active_recipe_slot() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity input = itemInputBus(controllerPos.offset(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        var machine = new DynamicMachine(
                MMCR.id("single_slot_stays_single_machine"),
                "Single Slot Stays Single",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()));
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, input);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        addItemInputComponent(controller, input);
        registerItemInputRecipe("single_slot_stays_single", machine.registryName(), 20);

        controller.serverTick();

        assertThat(controller.getActive()).isNotNull();
        assertThat(controller.getFactoryController()).isNull();
    }

    @Test
    void reset_and_removed_stop_factory_controller_lanes() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var factoryMachine = new DynamicMachine(
                MMCR.id("factory_stop_test_machine"),
                "Factory Stop Test",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("factory_controller").get()),
                MachineControllerSpec.defaultsFor(MMCR.id("factory_stop_test_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                4);
        FactorySchedulerBlockEntity factory = factoryController(controllerPos.offset(1, 0, 0));
        MachineControllerBlockEntity controller = controllerForFactoryFormation(factoryMachine, controllerPos, factory);
        assertThat(invokeTryFormMachine(controller, factoryMachine, Direction.SOUTH)).isTrue();
        addFactoryLane(factory);

        invokeResetMachine(controller);

        assertThat(factory.activeLaneCount()).isZero();

        assertThat(invokeTryFormMachine(controller, factoryMachine, Direction.SOUTH)).isTrue();
        addFactoryLane(factory);
        controller.setRemoved();

        assertThat(factory.activeLaneCount()).isZero();
    }

    @Test
    void reset_machine_stops_real_factory_lanes_and_returns_contexts_once() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(MMCR.id("factory_lane_reset_machine"), 2, 2);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        setField(MachineControllerBlockEntity.class, fixture.controller(), "contextPool", pool);
        MachineRecipe recipe = registerItemRecipe("factory_lane_reset", fixture.machine().registryName(), 20, 0);
        fixture.controller().serverTick();
        assertThat(fixture.factory().activeLaneCount()).isEqualTo(2);

        invokeResetMachine(fixture.controller());

        assertThat(fixture.factory().activeLaneCount()).isZero();
        assertReturnedContexts(pool, fixture.controller(), recipe, 2);
    }

    @Test
    void set_removed_stops_real_factory_lanes_and_returns_contexts_once() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(MMCR.id("factory_lane_removed_machine"), 2, 2);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        setField(MachineControllerBlockEntity.class, fixture.controller(), "contextPool", pool);
        MachineRecipe recipe = registerItemRecipe("factory_lane_removed", fixture.machine().registryName(), 20, 0);
        fixture.controller().serverTick();
        assertThat(fixture.factory().activeLaneCount()).isEqualTo(2);

        fixture.controller().setRemoved();

        assertThat(fixture.factory().activeLaneCount()).isZero();
        assertReturnedContexts(pool, fixture.controller(), recipe, 2);
    }

    @Test
    void chunk_unload_stops_real_factory_lanes_and_returns_contexts_once() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(MMCR.id("factory_lane_unload_machine"), 2, 2);
        RecipeCraftingContextPool pool = new RecipeCraftingContextPool();
        setField(MachineControllerBlockEntity.class, fixture.controller(), "contextPool", pool);
        MachineRecipe recipe = registerItemRecipe("factory_lane_unload", fixture.machine().registryName(), 20, 0);
        fixture.controller().serverTick();
        assertThat(fixture.factory().activeLaneCount()).isEqualTo(2);

        MachineControllerBlockEntity.markStructureChunkDirty(levelOf(fixture.controller()), new net.minecraft.world.level.ChunkPos(fixture.controller().getBlockPos().getX() >> 4, fixture.controller().getBlockPos().getZ() >> 4));

        assertThat(fixture.factory().activeLaneCount()).isZero();
        assertReturnedContexts(pool, fixture.controller(), recipe, 2);
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
    void forming_structure_updates_port_appearance_base_texture() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        Identifier formedTexture = Identifier.parse("kubejs:block/formed_casing");
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("port_appearance_machine"),
                "Port Appearance",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("port_appearance_machine")),
                new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), formedTexture),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(port.appearanceBaseTexture()).isEqualTo(formedTexture);
    }

    @Test
    void invalidating_structure_resets_linked_port_appearance_base_texture() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        Identifier formedTexture = Identifier.parse("kubejs:block/formed_casing");
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("port_reset_machine"),
                "Port Reset",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("port_reset_machine")),
                new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), formedTexture),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        breakStructureBlock(controller);

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void removing_controller_block_resets_linked_port_appearance_base_texture() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        Identifier formedTexture = Identifier.parse("kubejs:block/formed_casing");
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("controller_removed_port_reset_machine"),
                "Controller Removed Port Reset",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("controller_removed_port_reset_machine")),
                new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), formedTexture),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        invokeBlockOnRemove(controller.getBlockState().getBlock(), controller.getBlockState(), levelOf(controller), controllerPos,
                Blocks.AIR.defaultBlockState(), false);

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void replacing_controller_block_resets_linked_port_appearance_base_texture() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        DynamicMachine machine = portAppearanceMachine(
                "controller_replaced_port_reset_machine",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()),
                Identifier.parse("kubejs:block/formed_casing"));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        invokeBlockOnRemove(controller.getBlockState().getBlock(), controller.getBlockState(), levelOf(controller), controllerPos,
                Blocks.DIAMOND_BLOCK.defaultBlockState(), false);

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void port_tick_resets_appearance_when_linked_controller_is_replaced() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        DynamicMachine machine = portAppearanceMachine(
                "port_tick_controller_replaced_machine",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()),
                Identifier.parse("kubejs:block/formed_casing"));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        levelOf(controller).setBlock(controllerPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        for (int i = 0; i < 40; i++) {
            port.serverTick();
        }

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void port_tick_resets_appearance_when_linked_controller_is_unformed() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        DynamicMachine machine = portAppearanceMachine(
                "port_tick_controller_unformed_machine",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()),
                Identifier.parse("kubejs:block/formed_casing"));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        controller.setFormed(false);
        for (int i = 0; i < 40; i++) {
            port.serverTick();
        }

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void changing_controller_state_does_not_reset_linked_port_appearance_base_texture() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        Identifier formedTexture = Identifier.parse("kubejs:block/formed_casing");
        DynamicMachine machine = portAppearanceMachine(
                "controller_state_change_keeps_port_machine",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()),
                formedTexture);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        invokeBlockOnRemove(controller.getBlockState().getBlock(), controller.getBlockState(), levelOf(controller), controllerPos,
                controller.getBlockState().setValue(cn.howxu.mmcr.internal.block.MachineControllerBlock.ACTIVE, true), false);

        assertThat(port.appearanceBaseTexture()).isEqualTo(formedTexture);
    }

    @Test
    void moving_controller_block_does_not_reset_linked_port_appearance_base_texture() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        Identifier formedTexture = Identifier.parse("kubejs:block/formed_casing");
        DynamicMachine machine = portAppearanceMachine(
                "controller_moved_keeps_port_machine",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()),
                formedTexture);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        invokeBlockOnRemove(controller.getBlockState().getBlock(), controller.getBlockState(), levelOf(controller), controllerPos,
                Blocks.AIR.defaultBlockState(), true);

        assertThat(port.appearanceBaseTexture()).isEqualTo(formedTexture);
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
    void matching_structure_rejects_port_below_required_tier() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = anyEnergyInputPattern();
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("requires_ludicrous_energy_machine"),
                "Requires Ludicrous Energy",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("requires_ludicrous_energy_machine")),
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
                MachineControllerSpec.defaultsFor(MMCR.id("accepts_ludicrous_energy_machine")),
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
                MachineControllerSpec.defaultsFor(MMCR.id("cached_requires_ludicrous_energy_machine")),
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

    @Test
    void built_in_blast_furnace_rejects_three_arbitrary_ports() throws Exception {
        TestBootstrap.registerRuntimeBuiltins();
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
        TestBootstrap.registerRuntimeBuiltins();
        DynamicMachine machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("blast_furnace"));
        BlockPos controllerPos = new BlockPos(20, 4, 20);
        MachineControllerBlockEntity controller = controllerForDefaultBlastFurnace(
                machine,
                controllerPos,
                itemInputBus(controllerPos.offset(0, 0, -2)),
                itemOutputBus(controllerPos.offset(-1, 0, -1)),
                energyHatch(controllerPos.offset(1, 0, -1), "energy_input_hatch_ludicrous"));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(controller.getLastFormationFailure()).isNull();
    }

    @Test
    void built_in_blast_furnace_forms_when_top_factory_slot_is_casing() throws Exception {
        TestBootstrap.registerRuntimeBuiltins();
        DynamicMachine machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("blast_furnace"));
        BlockPos controllerPos = new BlockPos(20, 4, 20);
        MachineControllerBlockEntity controller = controllerForDefaultBlastFurnace(
                machine,
                controllerPos,
                itemInputBus(controllerPos.offset(0, 0, -2)),
                itemOutputBus(controllerPos.offset(-1, 0, -1)),
                energyHatch(controllerPos.offset(1, 0, -1), "energy_input_hatch_ludicrous"));
        Level level = levelOf(controller);
        level.setBlock(controllerPos.offset(0, 1, -1), cn.howxu.mmcr.registry.ModBlocks.CASING.get().defaultBlockState(), 3);

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
        MachineRegistry.register(machine);

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
        MachineRegistry.register(machine);

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
        MachineRegistry.register(machine);

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
    void modifier_only_snapshot_refresh_keeps_active_recipe_context() throws Exception {
        var first = replacementAt(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK, "speed", 2F);
        var second = replacementAt(new BlockPos(1, 0, 0), Blocks.DIAMOND_BLOCK, "speed", 4F);
        var machine = machineWithReplacements(first, second);
        MachineRegistry.register(machine);

        MachineControllerBlockEntity controller = controllerFor(machine);
        Level level = levelOf(controller);
        BlockPos replacementPos = controller.getBlockPos().offset(1, 0, 0);
        placeControllerAndReplacement(controller, machine, Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK);
        level.setBlock(replacementPos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        tickUntilFormed(controller, machine);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("modifier_refresh_active_recipe"), machine.registryName(), 100, List.of(), List.of());
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);
        RecipeCraftingContext activeContext = new RecipeCraftingContext(controller);
        activeContext.setStructureModifiers(controller.foundModifierList());
        setField(MachineControllerBlockEntity.class, controller, "active", active);
        setField(MachineControllerBlockEntity.class, controller, "context", activeContext);

        level.setBlock(replacementPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        invokeCheckStructure(controller);
        invokeTickActiveRecipe(controller);

        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "context")).isSameAs(activeContext);
        assertThat(activeContext.isStructureVersionCurrent()).isTrue();
        assertThat(controller.getTickCounter()).isEqualTo(1);
    }

    @Test
    void set_machine_clears_matched_modifier_snapshot() throws Exception {
        var replacement = replacementAt(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK, "speed", 2F);
        var machine = machineWithReplacements(replacement);
        var other = new DynamicMachine(MMCR.id("replacement_target_machine"), "Replacement Target", onePortPattern(Blocks.IRON_BLOCK));
        MachineRegistry.register(machine);

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
    void vertical_symmetric_machine_uses_controller_roll_for_position_modifiers() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos rawPos = new BlockPos(1, 0, 0);
        var defaults = cn.howxu.mmcr.api.machine.MachineControllerSpec.defaultsFor(MMCR.id("vertical_symmetric_modifier_roll"));
        var spec = new cn.howxu.mmcr.api.machine.MachineControllerSpec(
                defaults.id(), defaults.frontTexture(), defaults.sideTexture(), defaults.topTexture(), defaults.bottomTexture(), true, true);
        var replacement = new SingleBlockModifierReplacement(
                "roll_modifier", rawPos, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT,
                        3F, RecipeModifier.Operation.ADD, false)), "", ItemStack.EMPTY);
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("vertical_symmetric_modifier_roll"), "Vertical Symmetric Modifier Roll",
                new BlockArray(Map.of(rawPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                spec, PortRequirementSpec.none(), List.of(), Map.of(rawPos, List.of(replacement)));

        for (Direction rollFacing : Direction.Plane.HORIZONTAL) {
            BlockPos expected = BlockRotator.rotateSouthTo(rawPos, Direction.UP, rollFacing);
            MachineControllerBlockEntity controller = controllerForFormation(
                    machine,
                    controllerPos,
                    Direction.UP,
                    rollFacing,
                    itemInputBus(controllerPos.offset(expected)));
            Level level = levelOf(controller);
            level.setBlock(controllerPos.offset(expected), Blocks.GOLD_BLOCK.defaultBlockState(), 3);

            boolean formed = invokeTryFormMachine(controller, machine, Direction.UP);

            assertThat(formed).isTrue();
            assertThat(controller.getFoundPattern().pattern()).containsKey(expected);
            assertThat(controller.getFoundModifiers()).containsKey("roll_modifier");
            assertThat(controller.getFoundModifiers().get("roll_modifier"))
                    .extracting(RecipeModifier::getModifier)
                    .containsExactly(3F);
        }
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
    void reset_restores_formed_port_texture_even_when_linked_positions_were_lost() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        BlockArray pattern = onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("appearance_reset_machine"),
                "Appearance Reset",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("appearance_reset_machine")),
                cn.howxu.mmcr.api.machine.MachineAppearanceSpec.fromBasicBlock(net.minecraft.resources.Identifier.withDefaultNamespace("blue_ice")),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of());
        MachineRegistry.register(machine);
        IOPortBlockEntity port = itemInputBus(portPos);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        assertThat(port.appearanceBaseTexture()).isEqualTo(net.minecraft.resources.Identifier.withDefaultNamespace("block/blue_ice"));
        setField(MachineControllerBlockEntity.class, controller, "linkedPortPositions", new java.util.HashSet<>());

        invokeResetMachine(controller);

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
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
    void chunk_unload_stops_factory_lanes_when_single_active_slot_is_empty() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var factoryMachine = new DynamicMachine(
                MMCR.id("chunk_unload_factory_stop_machine"),
                "Chunk Unload Factory Stop",
                onePortPattern(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("factory_controller").get()),
                MachineControllerSpec.defaultsFor(MMCR.id("chunk_unload_factory_stop_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                4);
        MachineRegistry.register(factoryMachine);
        FactorySchedulerBlockEntity factory = factoryController(controllerPos.offset(1, 0, 0));
        MachineControllerBlockEntity controller = controllerForFactoryFormation(factoryMachine, controllerPos, factory);
        assertThat(invokeTryFormMachine(controller, factoryMachine, Direction.SOUTH)).isTrue();
        addFactoryLane(factory);
        assertThat(controller.getActive()).isNull();

        MachineControllerBlockEntity.markStructureChunkDirty(levelOf(controller), new net.minecraft.world.level.ChunkPos(controllerPos.getX() >> 4, controllerPos.getZ() >> 4));

        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isTrue();
        assertThat(factory.activeLaneCount()).isZero();
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

    @Test
    void modifier_only_refresh_updates_active_total_tick() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = machineWithReplacements(new SingleBlockModifierReplacement(
                "duration_half",
                new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT,
                        0.5F, RecipeModifier.Operation.MULTIPLY, false)),
                "", ItemStack.EMPTY));
        MachineRegistry.register(machine);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("duration_refresh_recipe"), machine.registryName(), 100, List.of(), List.of());
        MachineControllerBlockEntity controller = controllerFor(machine);
        Level level = levelOf(controller);
        placeControllerAndReplacement(controller, machine, Blocks.IRON_BLOCK);
        level.setBlock(controllerPos.offset(1, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        tickUntilFormed(controller, machine);
        assertThat(controller.foundModifierList()).isEmpty();
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        context.setStructureModifiers(controller.foundModifierList());
        setField(MachineControllerBlockEntity.class, controller, "active", active);
        setField(MachineControllerBlockEntity.class, controller, "context", context);
        assertThat(active.getTotalTick()).isEqualTo(100);

        level.setBlock(controllerPos.offset(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        invokeCollectFoundModifiers(controller, machine.modifierReplacements());
        invokeTickActiveRecipe(controller);

        assertThat(active.getTotalTick()).isEqualTo(50);
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
        return energyHatch(pos, PortKinds.ENERGY_INPUT.id());
    }

    private static EnergyInputHatchBlockEntity energyHatch(BlockPos pos, String id) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            EnergyInputHatchBlockEntity hatch = (EnergyInputHatchBlockEntity) unsafe.allocateInstance(EnergyInputHatchBlockEntity.class);
            setField(BlockEntity.class, hatch, "type", null);
            setField(BlockEntity.class, hatch, "worldPosition", pos);
            setField(BlockEntity.class, hatch, "blockState", net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            setField(EnergyInputHatchBlockEntity.class, hatch, "kind", PortKinds.all().stream()
                    .filter(kind -> kind.id().equals(id))
                    .findFirst()
                    .orElseThrow());
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
            setField(ItemInputBusBlockEntity.class, bus, "kind", PortKinds.ITEM_INPUT);
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
            setField(ItemOutputBusBlockEntity.class, bus, "kind", PortKinds.ITEM_OUTPUT);
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
            setField(FluidInputHatchBlockEntity.class, hatch, "kind", PortKinds.FLUID_INPUT);
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
            setField(FluidOutputHatchBlockEntity.class, hatch, "kind", PortKinds.FLUID_OUTPUT);
            setField(FluidHatchBlockEntity.class, hatch, "tank", new FluidTank(8000) {
                @Override protected void onContentsChanged() { }
            });
            return hatch;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate fluid output hatch", e);
        }
    }

    private static void addParallelComponent(MachineControllerBlockEntity controller, ParallelTier tier) throws Exception {
        ParallelControllerBlockEntity parallel = parallelController(tier, new BlockPos(1, 0, 0));
        Field componentsField = MachineControllerBlockEntity.class.getDeclaredField("components");
        componentsField.setAccessible(true);
        List<ProcessingComponent> list = (List<ProcessingComponent>) componentsField.get(controller);
        list.add(new ProcessingComponent(null, parallel, parallel.getBlockPos(), BlockPos.ZERO, List.of(), null));
    }

    private static ParallelControllerBlockEntity parallelController(ParallelTier tier, BlockPos pos) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            ParallelControllerBlockEntity entity = (ParallelControllerBlockEntity) unsafe.allocateInstance(ParallelControllerBlockEntity.class);
            setField(BlockEntity.class, entity, "type", null);
            setField(BlockEntity.class, entity, "worldPosition", pos);
            setField(BlockEntity.class, entity, "blockState", Blocks.IRON_BLOCK.defaultBlockState());
            setField(ParallelControllerBlockEntity.class, entity, "tier", tier);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate parallel controller", e);
        }
    }

    private static FactorySchedulerBlockEntity factoryController(BlockPos pos) {
        return factoryController(pos, 0);
    }

    private static FactorySchedulerBlockEntity factoryController(BlockPos pos, int dispersers) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            FactorySchedulerBlockEntity entity = (FactorySchedulerBlockEntity) unsafe.allocateInstance(FactorySchedulerBlockEntity.class);
            setField(BlockEntity.class, entity, "type", null);
            setField(BlockEntity.class, entity, "worldPosition", pos);
            setField(BlockEntity.class, entity, "blockState", Blocks.IRON_BLOCK.defaultBlockState());
            setField(FactorySchedulerBlockEntity.class, entity, "threadLimit", 4);
            setField(FactorySchedulerBlockEntity.class, entity, "scheduler", new cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler(4));
            setField(FactorySchedulerBlockEntity.class, entity, "handler", threadDisperserHandler(dispersers));
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate factory controller", e);
        }
    }

    private static ItemStackHandler threadDisperserHandler(int dispersers) {
        ItemStackHandler handler = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(cn.howxu.mmcr.registry.ModItems.THREAD_DISPERSER.get());
            }
        };
        handler.setStackInSlot(0, new ItemStack(cn.howxu.mmcr.registry.ModItems.THREAD_DISPERSER.get(), dispersers));
        return handler;
    }

    private static void addFactoryLane(FactorySchedulerBlockEntity factory) throws Exception {
        assertThat(startFactoryLane(factory)).isTrue();
    }

    private static boolean startFactoryLane(FactorySchedulerBlockEntity factory) throws Exception {
        Field schedulerField = FactorySchedulerBlockEntity.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler scheduler =
                (cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler) schedulerField.get(factory);
        return scheduler.startLane(new cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler.Lane() {
            @Override
            public boolean tick() {
                return false;
            }

            @Override
            public void stop() { }
        });
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

    private static FactoryRuntimeFixture formedFactoryRuntimeFixture(Identifier machineId,
                                                                     int threadLimit,
                                                                     int inputCount) throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos inputPos = controllerPos.offset(1, 0, 0);
        BlockPos outputPos = controllerPos.offset(2, 0, 0);
        BlockPos factoryPos = controllerPos.offset(3, 0, 0);
        ItemInputBusBlockEntity input = itemInputBus(inputPos);
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, inputCount));
        ItemOutputBusBlockEntity output = itemOutputBus(outputPos);
        setField(ItemBusBlockEntity.class, output, "handler", new ItemStackHandler(6));
        FactorySchedulerBlockEntity factory = factoryController(factoryPos, Math.max(0, threadLimit - 1));
        var machine = new DynamicMachine(
                machineId,
                "Factory Runtime",
                factoryItemPattern(),
                MachineControllerSpec.defaultsFor(machineId),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                threadLimit);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFactoryRuntimeFormation(machine, controllerPos, input, output, factory);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        addItemInputComponent(controller, input);
        addItemOutputComponent(controller, output);
        return new FactoryRuntimeFixture(controller, factory, input, output, machine);
    }

    private static void addItemInputComponent(MachineControllerBlockEntity controller, ItemInputBusBlockEntity input) throws Exception {
        addComponent(controller, new MachineComponent(PortKinds.ITEM_INPUT, cn.howxu.mmcr.util.IOType.INPUT), input);
    }

    private static void addItemOutputComponent(MachineControllerBlockEntity controller, ItemOutputBusBlockEntity output) throws Exception {
        addComponent(controller, new MachineComponent(PortKinds.ITEM_OUTPUT, cn.howxu.mmcr.util.IOType.OUTPUT), output);
    }

    private static void addFactoryComponent(MachineControllerBlockEntity controller, FactorySchedulerBlockEntity factory) throws Exception {
        addComponent(controller, null, factory);
    }

    private static void addFactorySchedulerComponent(MachineControllerBlockEntity controller, FactorySchedulerBlockEntity scheduler) throws Exception {
        addComponent(controller, null, scheduler);
    }

    private static void addComponent(MachineControllerBlockEntity controller,
                                     MachineComponent component,
                                     BlockEntity container) throws Exception {
        Field componentsField = MachineControllerBlockEntity.class.getDeclaredField("components");
        componentsField.setAccessible(true);
        List<ProcessingComponent> list = (List<ProcessingComponent>) componentsField.get(controller);
        list.add(new ProcessingComponent(component, container, container.getBlockPos(), container.getBlockPos().subtract(controller.getBlockPos()), (String) null));
    }

    private static BlockArray factoryItemPattern() {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get()));
        blocks.put(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_output_bus").get()));
        blocks.put(new BlockPos(3, 0, 0), new BlockPredicate.OfBlock(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("factory_controller").get()));
        return new BlockArray(blocks);
    }

    private static MachineRecipe registerItemRecipe(String path, Identifier machineId, int ticks) {
        return registerItemRecipe(path, machineId, ticks, 1);
    }

    private static MachineRecipe registerItemRecipe(String path, Identifier machineId, int ticks, int maxThreads) {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id(path),
                machineId,
                ticks,
                List.of(),
                List.of(),
                List.of(),
                0,
                maxThreads,
                false,
                List.of(),
                List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                        new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET))));
        RecipeRegistry.register(recipe);
        return recipe;
    }

    private static void registerItemInputRecipe(String path, Identifier machineId, int ticks) {
        RecipeRegistry.register(new MachineRecipe(
                MMCR.id(path),
                machineId,
                ticks,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY))));
    }

    private static int countItem(ItemInputBusBlockEntity input, Item item) {
        int count = 0;
        for (int slot = 0; slot < input.getItemStackHandler(null).getSlots(); slot++) {
            ItemStack stack = input.getItemStackHandler(null).getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void assertReturnedContexts(RecipeCraftingContextPool pool,
                                               MachineControllerBlockEntity controller,
                                               MachineRecipe recipe,
                                               int expected) {
        List<RecipeCraftingContext> contexts = new ArrayList<>();
        for (int i = 0; i < expected; i++) {
            contexts.add(pool.borrow(new ActiveMachineRecipe(recipe, 1), controller));
        }
        assertThat(contexts).doesNotHaveDuplicates();
        for (RecipeCraftingContext context : contexts) {
            pool.returnContext(context);
        }
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
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

    private static BlockArray anyEnergyInputPattern() {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.AnyOf(PortKinds.all().stream()
                .filter(kind -> kind.ioType() == IOType.INPUT && kind.energyHatchSize().isPresent())
                .<BlockPredicate>map(kind -> new BlockPredicate.OfBlock(cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get(kind.id()).get()))
                .toList()));
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

    private static DynamicMachine portAppearanceMachine(String path, BlockArray pattern, Identifier formedTexture) {
        Identifier id = MMCR.id(path);
        return new DynamicMachine(
                id,
                path,
                pattern,
                MachineControllerSpec.defaultsFor(id),
                new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), formedTexture),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of());
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

    private static MachineControllerBlockEntity controllerForParallelFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            ParallelControllerBlockEntity parallel) throws Exception {
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
        blocks.put(controllerPos, controllerBlock);
        blocks.put(parallel.getBlockPos(), cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get(ParallelTier.X16.idSuffix()).get());
        Level level = LevelStub.create(blocks, List.of(controller, parallel));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, parallel, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity controllerForFactoryFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            FactorySchedulerBlockEntity factory) throws Exception {
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
        blocks.put(controllerPos, controllerBlock);
        blocks.put(factory.getBlockPos(), cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("factory_controller").get());
        Level level = LevelStub.create(blocks, List.of(controller, factory));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, factory, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity controllerForFactoryRuntimeFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            ItemInputBusBlockEntity input,
            ItemOutputBusBlockEntity output,
            FactorySchedulerBlockEntity factory) throws Exception {
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
        blocks.put(controllerPos, controllerBlock);
        blocks.put(input.getBlockPos(), cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_input_bus").get());
        blocks.put(output.getBlockPos(), cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("item_output_bus").get());
        blocks.put(factory.getBlockPos(), cn.howxu.mmcr.registry.ModBlocks.BLOCKS.get("factory_controller").get());
        Level level = LevelStub.create(blocks, List.of(controller, input, output, factory));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        setField(BlockEntity.class, output, "level", level);
        setField(BlockEntity.class, factory, "level", level);
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
                case BlockPredicate.AnyOf anyOf -> firstBlock(anyOf);
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

    private static Block firstBlock(BlockPredicate.AnyOf predicate) {
        for (BlockPredicate child : predicate.children()) {
            if (child instanceof BlockPredicate.OfBlock of) return of.block();
        }
        return cn.howxu.mmcr.registry.ModBlocks.CASING.get();
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

    private static void invokeTickActiveRecipe(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("tickActiveRecipe");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static void invokeCollectFoundModifiers(
            MachineControllerBlockEntity controller,
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("collectFoundModifiers", Map.class);
        method.setAccessible(true);
        method.invoke(controller, replacements);
    }

    private static void invokeBlockOnRemove(Block block, BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) throws Exception {
        Method method = onRemoveMethod(block.getClass());
        method.setAccessible(true);
        method.invoke(block, state, level, pos, newState, moving);
    }

    private static Method onRemoveMethod(Class<?> type) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod("onRemove", BlockState.class, Level.class, BlockPos.class, BlockState.class, boolean.class);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException("onRemove");
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

    private record FactoryRuntimeFixture(MachineControllerBlockEntity controller,
                                         FactorySchedulerBlockEntity factory,
                                         ItemInputBusBlockEntity inputBus,
                                         ItemOutputBusBlockEntity outputBus,
                                         DynamicMachine machine) { }
}
