package cn.howxu.mmcr.internal.async;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.ConfigTestSupport;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.config.IConfigSpec;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies factory lane scheduling through the asynchronous execution chain.
 *
 * @author howxu <dev@howxu.cn>
 */
class AsyncFactoryExecutionTest {
    private ServerLevel level;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @AfterEach
    void cleanup() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        RecipeRegistry.clearForTesting();
        if (level == null) return;
        MachineAsyncCoordinator.discard(level);
        SharedIoCoordinator.discard(level);
        StructureClaimRegistry.discard(level);
    }

    @Test
    void async_factory_advances_each_active_lane_once_after_same_tick_shared_io_grants() {
        MachineControllerBlockEntity controller = factoryController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_factory_lanes"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 2);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isZero();

        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(1);

        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.laneId())
                .containsExactlyInAnyOrder("base", "factory-0");
        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.tick())
                .containsExactly(1, 0);
    }

    @Test
    void async_factory_keeps_base_and_factory_lane_order_fair_across_ticks() {
        MachineControllerBlockEntity controller = factoryController(3);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_factory_lane_order"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 3);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.laneId())
                .containsExactly("base");

        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.laneId())
                .containsExactly("base", "factory-0", "factory-1");
        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.parallelism())
                .containsExactly(1L, 1L, 1L);

        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes())
                .filteredOn(lane -> lane.active())
                .extracting(lane -> lane.tick())
                .containsExactly(2, 1, 1);
    }

    @Test
    void async_factory_retries_a_cancelled_search_after_the_pause_clears() {
        MachineControllerBlockEntity controller = factoryController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("cancelled_async_factory_search"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 2);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();
        RuntimeTestFixtures.setDirectSignal(level, controller.getBlockPos(), 15);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isZero();

        RuntimeTestFixtures.setDirectSignal(level, controller.getBlockPos(), 0);
        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(1);

        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(2);
    }

    @Test
    void async_factory_does_not_create_speculative_lanes_when_no_recipe_can_start() {
        MachineControllerBlockEntity controller = factoryController(8);
        RecipeRegistry.registerStatic(RecipeTestSupport.create(MMCR.id("idle_factory_missing_input"),
                MMCR.id("test_cube"), 20, List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                        new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                                ItemStack.EMPTY))));

        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(factoryRuntime(controller).laneCount()).isEqualTo(1);
        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isZero();
    }

    @Test
    void async_factory_finish_release_wakes_and_restarts_its_lane() {
        MachineControllerBlockEntity controller = factoryController(1);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_factory_finish_restart"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();
        completeAsyncLevelTick(level);
        long beforeFinishEpoch = controller.resourceAvailabilityEpoch();

        for (int pass = 0; pass < 3 && controller.resourceAvailabilityEpoch() == beforeFinishEpoch; pass++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.serverTick();
            completeAsyncLevelTick(level);
        }

        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeFinishEpoch + 1L);
        for (int pass = 0; pass < 8
                && !controller.runtimeSnapshot().factory().presentationLanes().getFirst().active(); pass++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.serverTick();
            completeAsyncLevelTick(level);
        }
        assertThat(controller.runtimeSnapshot().factory().presentationLanes()).singleElement().satisfies(lane -> {
            assertThat(lane.active()).isTrue();
            assertThat(lane.tick()).isZero();
        });
    }

    @Test
    void async_factory_does_not_delay_a_fallback_when_the_more_specific_output_is_blocked() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = factoryController(input);
        MachineRecipe specific = RecipeTestSupport.create(MMCR.id("async_factory_blocked_specific"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.GOLD_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET, 1))));
        MachineRecipe fallback = RecipeTestSupport.create(MMCR.id("async_factory_output_blocked_fallback"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
        RecipeRegistry.replaceDynamic(Map.of(specific.id(), specific, fallback.id(), fallback));
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        setItem(input.itemHandler(), new ItemStack(Items.IRON_INGOT, 1));

        for (int pass = 0; pass < 8 && controller.runtimeSnapshot().factory().activeLaneCount() == 0; pass++) {
            controller.serverTick();
            completeAsyncLevelTick(level);
            RuntimeTestFixtures.advanceGameTime(level);
        }

        assertThat(controller.runtimeSnapshot().factory().presentationLanes()).singleElement().satisfies(lane -> {
            assertThat(lane.active()).isTrue();
            assertThat(lane.recipeId()).isEqualTo(fallback.id().toString());
        });
    }

    @Test
    void async_factory_matches_sync_delay_for_a_level_blocked_specific_recipe_with_pending_input() {
        assertThat(fallbackStartIsDelayedBySpecificPendingInput(MachineWorkMode.SYNC)).isTrue();
        assertThat(fallbackStartIsDelayedBySpecificPendingInput(MachineWorkMode.ASYNC)).isTrue();
    }

    @Test
    void shrinking_factory_invalidates_queued_lane_searches_before_their_continuations_commit() {
        MachineControllerBlockEntity controller = factoryController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("shrunk_async_factory_search"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 2);
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();
        factoryRuntime(controller).setLaneLimit(1);
        completeAsyncLevelTick(level);

        assertThat(controller.runtimeSnapshot().factory().presentationLanes()).hasSize(1);
        assertThat(controller.runtimeSnapshot().factory().activeLaneCount()).isEqualTo(1);
    }

    private MachineControllerBlockEntity factoryController() {
        return factoryController(2);
    }

    private MachineControllerBlockEntity factoryController(int laneLimit) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "async factory", pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("test_cube")), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, laneLimit);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        List<ProcessingComponent> components = new ArrayList<>();
        components.add(new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null));
        controller.componentRuntime().replaceComponents(components);
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        return controller;
    }

    private MachineControllerBlockEntity factoryController(ItemInputBusBlockEntity input) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "async factory", pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("test_cube")), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, input);
        List<ProcessingComponent> components = new ArrayList<>();
        components.add(new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null));
        components.add(new ProcessingComponent(new MachineComponent(input.kind(), input.ioType()), input,
                input.getBlockPos(), input.getBlockPos(), (String) null));
        controller.componentRuntime().replaceComponents(components);
        controller.setFormed(true);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        RuntimeTestFixtures.republish(controller);
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        return controller;
    }

    private boolean fallbackStartIsDelayedBySpecificPendingInput(MachineWorkMode mode) {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = factoryController(input, output);
        ResourceLocation levelType = MMCR.id("factory_pending_input_level_type");
        ResourceLocation requiredLevel = MMCR.id("factory_pending_input_level");
        TestBootstrap.registerType(new LevelType(levelType, Component.literal("Factory Test Level")));
        TestBootstrap.registerLevel(new MachineLevel(requiredLevel, levelType, 1,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
        MachineRecipe specific = RecipeTestSupport.create(MMCR.id("level_blocked_specific"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.GOLD_INGOT), 1, ItemStack.EMPTY),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_NUGGET, 1))), false,
                List.of(LevelRequirement.input(levelType, requiredLevel)));
        MachineRecipe fallback = RecipeTestSupport.create(MMCR.id("level_blocked_fallback"), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
        RecipeRegistry.replaceDynamic(Map.of(specific.id(), specific, fallback.id(), fallback));
        ConfigTestSupport.setMachineWorkMode(mode);
        setItem(input.itemHandler(), new ItemStack(Items.IRON_INGOT, 1));

        for (int pass = 0; pass < 8; pass++) {
            controller.serverTick();
            completeAsyncLevelTick(level);
            RuntimeTestFixtures.advanceGameTime(level);
        }

        return controller.runtimeSnapshot().factory().activeLaneCount() == 0;
    }

    private MachineControllerBlockEntity factoryController(ItemInputBusBlockEntity input, ItemOutputBusBlockEntity output) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "async factory", pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("test_cube")), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler, input, output);
        List<ProcessingComponent> components = new ArrayList<>();
        components.add(new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null));
        components.add(new ProcessingComponent(new MachineComponent(input.kind(), input.ioType()), input,
                input.getBlockPos(), input.getBlockPos(), (String) null));
        components.add(new ProcessingComponent(new MachineComponent(output.kind(), output.ioType()), output,
                output.getBlockPos(), output.getBlockPos(), (String) null));
        controller.componentRuntime().replaceComponents(components);
        controller.setFormed(true);
        input.linkControllerAppearance(controller.getBlockPos(), null);
        output.linkControllerAppearance(controller.getBlockPos(), null);
        RuntimeTestFixtures.republish(controller);
        level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        return controller;
    }

    private static void setItem(LongItemStorage storage, ItemStack stack) {
        storage.setContents(0, stack, stack.getCount());
    }

    private static void completeAsyncLevelTick(ServerLevel level) {
        SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        long gameTime = level.getGameTime();
        sharedIo.beginLevelTick(gameTime);
        async.beginLevelTick(gameTime);
        sharedIo.resolve(level);
        async.completeUntilIdleForTesting(() -> sharedIo.resolve(level));
        MachineControllerBlockEntity.flushQueuedAsyncRuntimeState(level);
    }


    private static FactoryRuntime factoryRuntime(MachineControllerBlockEntity controller) {
        try {
            var field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return ((MachineControllerRuntime) field.get(controller)).factoryRuntime();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access controller factory runtime", exception);
        }
    }
}
