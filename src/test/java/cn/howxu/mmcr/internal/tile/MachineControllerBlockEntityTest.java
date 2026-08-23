package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.FactoryThreadSpec;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
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
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
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
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.TreeMap;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
    void structure_candidates_are_highest_first_and_stage_state_resets() throws Exception {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        BlockArray stage1 = onePortPattern(Blocks.IRON_BLOCK);
        BlockArray stage2 = onePortPattern(Blocks.GOLD_BLOCK);
        BlockArray stage3 = onePortPattern(Blocks.DIAMOND_BLOCK);
        DynamicMachine machine = stagedMachine(MMCR.id("candidate_stage_order_machine"), stage1, stage2, stage3);
        MachineRegistry.register(machine);

        assertThat(controller.candidateStageNumbers(machine, Direction.SOUTH)).containsExactly(3, 2, 1);
        assertThat(controller.getMatchedStructureStage()).isZero();

        BlockPos controllerPos = new BlockPos(10, 4, 10);
        controller = controllerForFormation(machine, controllerPos, Blocks.GOLD_BLOCK);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        assertThat(controller.getMatchedStructureStage()).isEqualTo(2);
        assertThat(compiledPattern(controller).stageNumber()).isEqualTo(2);
        assertThat(controller.assemblyPattern(machine)).isSameAs(controller.getFoundPattern());

        setField(MachineControllerBlockEntity.class, controller, "structureDirty", true);
        AtomicInteger checks = new AtomicInteger();
        controller.setStructureCheckCallbackForTesting(checks::incrementAndGet);
        BlockPos stagePos = controllerPos.offset(1, 0, 0);
        levelOf(controller).setBlock(stagePos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        controller.onStructureBlockChanged(stagePos);
        controller.serverTick();
        assertThat(checks).hasValue(1);
        assertThat(controller.getMatchedStructureStage()).isEqualTo(3);

        checks.set(0);
        levelOf(controller).setBlock(stagePos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        controller.onStructureBlockChanged(controllerPos.offset(1, 0, 0));
        controller.serverTick();
        assertThat(checks).hasValue(1);
        assertThat(controller.getMatchedStructureStage()).isEqualTo(2);
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "lastStructureMismatchDiagnostic")).isNull();

        controller.invalidateFormedStructure();

        assertThat(controller.getMatchedStructureStage()).isZero();
        assertThat(controller.assemblyPattern(machine)).isSameAs(MachineRegistry.getCompiledStages(machine.registryName()).getFirst().rotatedPattern(Direction.SOUTH));
    }

    @Test
    void preview_uses_the_highest_effective_complete_stage() throws Exception {
        TestBootstrap.registerRuntimeBuiltins();
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = stagedMachine(MMCR.id("preview_complete_stage_machine"),
                onePortPattern(Blocks.IRON_BLOCK), onePortPattern(Blocks.GOLD_BLOCK));
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.AIR);

        MultiblockPreviewSnapshot snapshot = controller.createStructurePreviewSnapshot(16).orElseThrow();

        assertThat(snapshot.entries()).extracting(MultiblockPreviewSnapshot.Entry::state)
                .containsExactly(Blocks.GOLD_BLOCK.defaultBlockState());
    }

    @Test
    void preview_falls_back_when_highest_stage_has_no_preview_state() throws Exception {
        TestBootstrap.registerRuntimeBuiltins();
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray invalidHighStage = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.Any()));
        DynamicMachine machine = stagedMachine(MMCR.id("preview_invalid_high_stage_machine"),
                onePortPattern(Blocks.IRON_BLOCK), invalidHighStage);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.AIR);

        MultiblockPreviewSnapshot snapshot = controller.createStructurePreviewSnapshot(16).orElseThrow();

        assertThat(snapshot.entries()).extracting(MultiblockPreviewSnapshot.Entry::state)
                .containsExactly(Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Test
    void preview_falls_back_when_highest_stage_cannot_form_at_the_current_positions() throws Exception {
        TestBootstrap.registerRuntimeBuiltins();
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray lowerStage = new BlockArray(Map.of(
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        DynamicMachine machine = stagedMachine(MMCR.id("preview_unformable_high_stage_machine"),
                lowerStage, onePortPattern(Blocks.GOLD_BLOCK));
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.STONE);

        MultiblockPreviewSnapshot snapshot = controller.createStructurePreviewSnapshot(16).orElseThrow();

        assertThat(snapshot.entries()).extracting(MultiblockPreviewSnapshot.Entry::state)
                .containsExactly(Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Test
    void preview_selects_a_valid_highest_stage_over_a_lower_stage() throws Exception {
        TestBootstrap.registerRuntimeBuiltins();
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = stagedMachine(MMCR.id("preview_valid_high_stage_machine"),
                onePortPattern(Blocks.IRON_BLOCK), onePortPattern(Blocks.DIAMOND_BLOCK));
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.AIR);

        MultiblockPreviewSnapshot snapshot = controller.createStructurePreviewSnapshot(16).orElseThrow();

        assertThat(snapshot.entries()).extracting(MultiblockPreviewSnapshot.Entry::state)
                .containsExactly(Blocks.DIAMOND_BLOCK.defaultBlockState());
    }

    @Test
    void matching_structure_uses_rotated_directional_block_state_predicate() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos rawPosition = new BlockPos(1, 0, 0);
        BlockState southState = Blocks.OAK_LOG.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X);
        BlockArray pattern = new BlockArray(Map.of(rawPosition, new BlockPredicate.OfBlockState(southState)));
        DynamicMachine machine = stagedMachine(MMCR.id("rotated_directional_match_machine"), pattern);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.AIR);
        Level level = levelOf(controller);
        BlockPos rotatedPosition = controllerPos.offset(BlockRotator.rotateSouthTo(rawPosition, Direction.EAST));
        BlockState rotatedState = southState.rotate(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
        level.setBlock(controllerPos.offset(rawPosition), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(rotatedPosition, rotatedState, 3);
        setField(BlockEntity.class, controller, "blockState",
                testControllerState(testControllerBlock(machine)).setValue(MachineControllerBlock.FACING, Direction.EAST));

        assertThat(invokeTryFormMachine(controller, machine, Direction.EAST)).isTrue();

        setField(BlockEntity.class, controller, "blockState",
                testControllerState(testControllerBlock(machine)).setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isFalse();
    }

    @Test
    void matched_stage_is_persisted_and_invalid_saved_stage_is_dirty() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        setField(MachineControllerBlockEntity.class, controller, "matchedStructureStage", 2);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(controller, output);
        assertThat(output.buildResult().toString()).contains("matched_structure_stage");

        MachineControllerBlockEntity loaded = controllerBlockEntityWithoutRunningMinecraftConstructor();
        TagValueOutput invalid = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invalid.putInt("matched_structure_stage", -4);
        invokeLoadAdditional(loaded, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), invalid.buildResult()));
        assertThat(loaded.getMatchedStructureStage()).isZero();
        assertThat(fieldValue(MachineControllerBlockEntity.class, loaded, "structureDirty")).isEqualTo(true);
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
    void preview_receiver_is_consumed_only_inside_preview_window() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        controller.rememberPreviewReceiverForTesting(first, 100L, 160);
        controller.rememberPreviewReceiverForTesting(second, 100L, 160);

        assertThat(controller.consumeActivePreviewReceiverIdsForTesting(259L)).containsExactlyInAnyOrder(first, second);
        assertThat(controller.consumeActivePreviewReceiverIdsForTesting(259L)).isEmpty();

        controller.rememberPreviewReceiverForTesting(first, 100L, 160);
        assertThat(controller.consumeActivePreviewReceiverIdsForTesting(261L)).isEmpty();
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
        addParallelComponent(controller, ParallelTier.PLUS);
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
        addParallelComponent(controller, ParallelTier.NORMAL);
        addParallelComponent(controller, ParallelTier.NORMAL);

        assertThat(controller.parallelControllerCount()).isEqualTo(2);
        assertThat(controller.getMaxParallelism()).isEqualTo(8);
    }

    @Test
    void effective_threads_require_multithreading_and_parallelism_requires_parallel_flag() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        addFactorySchedulerComponent(controller, factoryController(new BlockPos(1, 0, 0), 4));
        addParallelComponent(controller, ParallelTier.PLUS);

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
    void controller_scheduler_aggregates_factory_capacity_and_updates_immediately() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        FactorySchedulerBlockEntity first = factoryController(new BlockPos(1, 0, 0), 2);
        FactorySchedulerBlockEntity second = factoryController(new BlockPos(2, 0, 0), 4);
        addFactorySchedulerComponent(controller, first);
        addFactorySchedulerComponent(controller, second);
        first.bindOwner(controller);
        second.bindOwner(controller);
        var machine = new DynamicMachine(
                MMCR.id("controller_scheduler_owner_machine"),
                "Controller Scheduler Owner",
                onePortPattern(Blocks.IRON_BLOCK),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_scheduler_owner_machine")),
                PortRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 1);
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);

        int initialCapacity = first.threadCount() + second.threadCount();
        assertThat(controller.effectiveFactoryThreadLimit()).isEqualTo(initialCapacity);
        assertThat(controller.factoryScheduler().threadLimit()).isEqualTo(controller.effectiveFactoryThreadLimit());

        first.getItemStackHandler(null).setStackInSlot(0,
                new ItemStack(ModItems.THREAD_DISPERSER.get(), 10));

        assertThat(controller.effectiveFactoryThreadLimit()).isEqualTo(first.threadCount() + second.threadCount());
        assertThat(controller.factoryScheduler().threadLimit()).isEqualTo(controller.effectiveFactoryThreadLimit());
    }

    @Test
    void max_parallelism_clamps_long_sum_at_integer_maximum() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var machine = new DynamicMachine(
                MMCR.id("long_parallelism_machine"), "Long Parallelism", onePortPattern(Blocks.IRON_BLOCK),
                MachineControllerSpec.defaultsFor(MMCR.id("long_parallelism_machine")), PortRequirementSpec.none(),
                List.of(), Map.of(), Integer.MAX_VALUE, true, false, 1);
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        addParallelComponent(controller, ParallelTier.ULTIMATE);
        ParallelControllerBlockEntity first = (ParallelControllerBlockEntity) controller.getComponents().getFirst().getContainer();
        first.setCurrentParallelism(Integer.MAX_VALUE);

        assertThat(controller.getMaxParallelism()).isEqualTo(Integer.MAX_VALUE);

        addParallelComponent(controller, ParallelTier.ULTIMATE);
        ParallelControllerBlockEntity second = (ParallelControllerBlockEntity) controller.getComponents().get(1).getContainer();
        second.setCurrentParallelism(Integer.MAX_VALUE);

        assertThat(controller.getMaxParallelism()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void structure_transition_reuses_controller_scheduler_and_stops_invalid_contexts() throws Exception {
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(
                MMCR.id("factory_structure_transition_machine"), 2, 2);
        registerItemRecipe("factory_structure_transition_recipe", fixture.machine().registryName(), 20, 0);
        fixture.controller().serverTick();
        FactoryRecipeScheduler scheduler = fixture.controller().factoryScheduler();
        assertThat(scheduler.activeLaneCount()).isPositive();

        invokeResetMachine(fixture.controller());

        assertThat(scheduler.activeLaneCount()).isZero();
        assertThat(fixture.controller().factoryScheduler()).isSameAs(scheduler);
        assertThat(fieldValue(FactorySchedulerBlockEntity.class, fixture.factory(), "owner")).isNull();

        assertThat(invokeTryFormMachine(fixture.controller(), fixture.machine(), Direction.SOUTH)).isTrue();
        assertThat(fieldValue(FactorySchedulerBlockEntity.class, fixture.factory(), "owner"))
                .isSameAs(fixture.controller());
        assertThat(fixture.controller().factoryScheduler()).isSameAs(scheduler);
    }

    @Test
    void controller_scheduler_round_trips_active_recipe_and_lock_state() throws Exception {
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(
                MMCR.id("factory_controller_scheduler_persistence"), 2, 2);
        MachineRecipe recipe = registerItemRecipe("factory_controller_scheduler_persistence_recipe",
                fixture.machine().registryName(), 20, 0);
        fixture.controller().serverTick();
        FactoryRecipeScheduler scheduler = fixture.controller().factoryScheduler();
        FactoryRecipeThread activeThread = scheduler.allThreads().stream()
                .filter(thread -> thread.getActiveRecipe() != null)
                .findFirst().orElseThrow();
        activeThread.setLockedRecipeId(recipe.id());

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        fixture.controller().saveAdditional(output);

        MachineControllerBlockEntity restored = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(restored);
        setField(BlockEntity.class, restored, "worldPosition", BlockPos.ZERO);
        setField(MachineControllerBlockEntity.class, restored, "machine", fixture.machine());
        FactorySchedulerBlockEntity restoredFactory = factoryController(new BlockPos(3, 0, 0));
        addFactorySchedulerComponent(restored, restoredFactory);
        restoredFactory.bindOwner(restored);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        List<FactoryRecipeThread> restoredThreads = restored.factoryScheduler().allThreads();
        assertThat(restoredThreads).anyMatch(thread -> thread.getActiveRecipe() != null
                && thread.getActiveRecipe().getRecipe().id().equals(recipe.id()));
        assertThat(restoredThreads).anyMatch(thread -> recipe.id().equals(thread.lockedRecipeId()));
    }

    @Test
    void controller_scheduler_loads_old_data_without_factory_scheduler_as_base_thread() throws Exception {
        MachineControllerBlockEntity restored = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(restored);
        setField(BlockEntity.class, restored, "worldPosition", BlockPos.ZERO);
        Machine machine = new DynamicMachine(
                MMCR.id("factory_controller_scheduler_old_data"), "Factory Old Data",
                factoryItemPattern(), MachineControllerSpec.defaultsFor(MMCR.id("factory_controller_scheduler_old_data")),
                PortRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 1);
        setField(MachineControllerBlockEntity.class, restored, "machine", machine);
        FactorySchedulerBlockEntity factory = factoryController(new BlockPos(3, 0, 0));
        addFactorySchedulerComponent(restored, factory);
        factory.bindOwner(restored);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(restored.factoryScheduler().allThreads()).hasSize(1);
        assertThat(restored.factoryScheduler().allThreads().getFirst().isBaseThread()).isTrue();
    }

    @Test
    void controller_snapshot_and_lock_access_use_the_controller_scheduler() throws Exception {
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(
                MMCR.id("factory_controller_snapshot_machine"), 1, 0);
        Identifier recipeId = MMCR.id("factory_controller_snapshot_lock");
        FactoryRecipeScheduler scheduler = fixture.controller().factoryScheduler();
        scheduler.allThreads().getFirst().setLockedRecipeId(recipeId);

        assertThat(fixture.controller().factoryControllerSnapshot().threads().getFirst().lockedRecipeId())
                .isEqualTo(recipeId.toString());
        assertThat(fixture.controller().toggleFactoryRecipeLock(0)).isTrue();
        assertThat(fixture.controller().factoryControllerSnapshot().threads().getFirst().locked())
                .isFalse();
    }

    @Test
    void non_factory_accessors_do_not_create_a_factory_scheduler() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        setField(MachineControllerBlockEntity.class, controller, "machine",
                new DynamicMachine(MMCR.id("non_factory_scheduler_guard"), "Non Factory Guard",
                        onePortPattern(Blocks.IRON_BLOCK)));

        assertThat(controller.activeFactoryThreadCount()).isZero();
        assertThat(controller.factoryThreadSnapshots())
                .containsExactly(FactoryRecipeScheduler.ThreadSnapshot.idleBase());
        assertThat(controller.factoryControllerSnapshot().threads())
                .containsExactly(FactoryRecipeScheduler.ThreadSnapshot.idleBase());
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "factoryScheduler")).isNull();
    }

    @Test
    void formed_parallel_controller_is_discovered_from_structure_snapshot() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        Identifier texture = MMCR.id("block/parallel_structure_casing");
        var machine = new DynamicMachine(
                MMCR.id("formed_parallel_test_machine"),
                "Formed Parallel Test",
                onePortPattern(ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get()),
                MachineControllerSpec.defaultsFor(MMCR.id("formed_parallel_test_machine")),
                new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), texture),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of(),
                64,
                true,
                false,
                1);
        ParallelControllerBlockEntity parallel = parallelController(ParallelTier.PLUS, controllerPos.offset(1, 0, 0));
        MachineControllerBlockEntity controller = controllerForParallelFormation(machine, controllerPos, parallel);

        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        assertThat(controller.getComponents()).hasSize(1);
        assertThat(controller.getComponents().getFirst().getContainer()).isInstanceOf(ParallelControllerBlockEntity.class);
        assertThat(controller.getMaxParallelism()).isEqualTo(16);
        assertThat(((ParallelControllerBlockEntity) controller.getComponents().getFirst().getContainer()).appearanceBaseTexture())
                .isEqualTo(texture);

        invokeResetMachine(controller);

        assertThat(parallel.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void formed_structure_binds_smart_interfaces_before_rebuilding_components_and_unbinds_when_invalid() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        Identifier machineId = MMCR.id("smart_interface_binding_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId)
                .localizedName("Smart Interface Binding")
                .smartInterfaceType(new SmartInterfaceType("mode", 3F, 1))
                .build());
        var machine = new DynamicMachine(machineId, "Smart Interface Binding",
                onePortPattern(ModBlocks.SMART_INTERFACE.get()));
        var smartInterface = (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get().create(
                controllerPos.offset(1, 0, 0), ModBlocks.SMART_INTERFACE.get().defaultBlockState());
        MachineControllerBlockEntity controller = controllerForSmartInterfaceFormation(machine, controllerPos, smartInterface);

        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        assertThat(smartInterface.bindingFor(controllerPos)).contains(new SmartInterfaceBlockEntity.Binding(
                controllerPos, machineId, "mode", 3F));
        assertThat(controller.getComponents()).extracting(ProcessingComponent::getContainer).contains(smartInterface);

        invokeResetMachine(controller);

        assertThat(smartInterface.bindingFor(controllerPos)).isEmpty();
    }

    @Test
    void formed_factory_controller_is_discovered_only_for_factory_machines() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var factoryMachine = new DynamicMachine(
                MMCR.id("formed_factory_test_machine"),
                "Formed Factory Test",
                onePortPattern(ModBlocks.BLOCKS.get("factory_controller").get()),
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
                onePortPattern(ModBlocks.BLOCKS.get("factory_controller").get()));
        setField(MachineControllerBlockEntity.class, controller, "machine", nonFactoryMachine);
        assertThat(controller.getFactoryController()).isNull();
    }

    @Test
    void formed_factory_controller_exposes_formed_base_texture_to_dynamic_model_data() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        Identifier formedTexture = Identifier.parse("kubejs:block/formed_factory_casing");
        var factoryMachine = new DynamicMachine(
                MMCR.id("formed_factory_appearance_machine"),
                "Formed Factory Appearance",
                onePortPattern(ModBlocks.BLOCKS.get("factory_controller").get()),
                MachineControllerSpec.defaultsFor(MMCR.id("formed_factory_appearance_machine")),
                new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), formedTexture),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                4);
        FactorySchedulerBlockEntity factory = factoryController(controllerPos.offset(1, 0, 0));
        MachineControllerBlockEntity controller = controllerForFactoryFormation(factoryMachine, controllerPos, factory);

        assertThat(invokeTryFormMachine(controller, factoryMachine, Direction.SOUTH)).isTrue();

        assertThat(factory.getModelData().get(MachineModelDataKeys.PORT_BASE_TEXTURE)).isEqualTo(formedTexture);
    }

    @Test
    void formed_factory_controller_uses_own_thread_disperser_count() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var factoryMachine = new DynamicMachine(
                MMCR.id("formed_factory_limit_test_machine"),
                "Formed Factory Limit Test",
                onePortPattern(ModBlocks.BLOCKS.get("factory_controller").get()),
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
        assertThat(controller.factoryScheduler().threadLimit()).isEqualTo(controller.effectiveFactoryThreadLimit());
        addFactoryLane(controller);
        addFactoryLane(controller);
        addFactoryLane(controller);
        assertThat(startFactoryLane(controller)).isFalse();
        assertThat(controller.factoryScheduler().activeLaneCount()).isEqualTo(3);
    }

    @Test
    void formed_factory_controller_syncs_declared_core_threads() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        Identifier machineId = MMCR.id("formed_factory_core_machine");
        MachineRecipe coreRecipe = new MachineRecipe(MMCR.id("formed_factory_core_recipe"), machineId,
                20, List.of(), List.of(), List.of(), 0, 0);
        FactoryThreadSpec coreSpec = new FactoryThreadSpec("core", List.of(coreRecipe.id()));
        DynamicMachine factoryMachine = new DynamicMachine(
                machineId,
                "Formed Factory Core",
                onePortPattern(ModBlocks.BLOCKS.get("factory_controller").get()),
                MachineControllerSpec.defaultsFor(machineId),
                MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                2,
                List.of(coreSpec));
        MachineRegistry.register(factoryMachine);
        RecipeRegistry.register(coreRecipe);
        FactorySchedulerBlockEntity factory = factoryController(controllerPos.offset(1, 0, 0), 1);
        MachineControllerBlockEntity controller = controllerForFactoryFormation(factoryMachine, controllerPos, factory);
        assertThat(invokeTryFormMachine(controller, factoryMachine, Direction.SOUTH)).isTrue();

        invokeTickFactoryRecipes(controller);

        assertThat(controller.factoryThreadSnapshots()).filteredOn(snapshot -> snapshot.coreThread())
                .singleElement()
                .satisfies(snapshot -> assertThat(snapshot.recipeId()).isEqualTo(coreRecipe.id().toString()));
    }

    @Test
    void factory_thread_count_aggregates_all_factory_controllers() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);

        assertThat(controller.factorySchedulerThreadCount()).isZero();

        FactorySchedulerBlockEntity first = factoryController(new BlockPos(1, 0, 0), 64);
        FactorySchedulerBlockEntity second = factoryController(new BlockPos(2, 0, 0), 3);
        addFactorySchedulerComponent(controller, first);
        addFactorySchedulerComponent(controller, second);

        assertThat(controller.factorySchedulerThreadCount()).isEqualTo(first.threadCount() + second.threadCount());

        addFactorySchedulerComponent(controller, factoryController(new BlockPos(3, 0, 0), Integer.MAX_VALUE));

        assertThat(controller.factorySchedulerThreadCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void structure_with_multiple_factory_controllers_forms_and_aggregates_capacity() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var machine = new DynamicMachine(
                MMCR.id("duplicate_factory_controller_machine"),
                "Duplicate Factory Controller",
                twoFactoryControllersPattern(),
                MachineControllerSpec.defaultsFor(MMCR.id("duplicate_factory_controller_machine")),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                4);
        FactorySchedulerBlockEntity first = factoryController(controllerPos.offset(1, 0, 0));
        FactorySchedulerBlockEntity second = factoryController(controllerPos.offset(2, 0, 0));
        MachineControllerBlockEntity controller = controllerForFactoriesFormation(machine, controllerPos, first, second);

        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        assertThat(controller.isFormed()).isTrue();
        assertThat(controller.getFactoryController()).isSameAs(first);
        assertThat(controller.factoryComponents()).containsExactly(first, second);
        assertThat(controller.factorySchedulerThreadCount()).isEqualTo(2);
    }

    @Test
    void factory_controller_starts_multiple_recipe_lanes_on_server_tick() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(MMCR.id("factory_lane_smelt_machine"), 3, 3);
        registerItemRecipe("factory_lane_smelt", fixture.machine().registryName(), 20, 0);

        fixture.controller().serverTick();

        assertThat(fixture.controller().factoryScheduler().threadLimit()).isEqualTo(fixture.controller().effectiveFactoryThreadLimit());
        assertThat(fixture.controller().factoryScheduler().activeLaneCount()).isEqualTo(3);
        assertThat(fixture.controller().getActive()).isNull();
        assertThat(fixture.controller().isRuntimeActive()).isTrue();
        assertThat(fixture.controller().activeFactoryThreadCount()).isEqualTo(3);
        assertThat(fixture.controller().currentParallelism()).isZero();
    }

    @Test
    void runtime_activity_switches_only_on_ordinary_start_and_finish() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        RuntimeSyncFixture fixture = serverRuntimeFixture(MMCR.id("runtime_sync_single_machine"), 2, 1);
        registerItemRecipe("runtime_sync_single", fixture.machine().registryName(), 2);

        fixture.controller().serverTick();
        fixture.controller().serverTick();

        assertThat(fixture.controller().isRuntimeActive()).isTrue();
        assertThat(fixture.controller().getBlockState().getValue(MachineControllerBlock.ACTIVE)).isTrue();

        fixture.controller().serverTick();

        assertThat(fixture.controller().isRuntimeActive()).isFalse();
        assertThat(fixture.controller().getBlockState().getValue(MachineControllerBlock.ACTIVE)).isFalse();
    }

    @Test
    void runtime_activity_stays_true_while_active_recipe_waits_for_outputs() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        RuntimeSyncFixture fixture = serverRuntimeFixture(MMCR.id("runtime_waiting_output_machine"), 1, 1);
        registerItemRecipe("runtime_waiting_output", fixture.machine().registryName(), 1);

        fixture.controller().serverTick();
        fillOutputBus(fixture.outputBus(), Items.COBBLESTONE);
        fixture.controller().serverTick();

        assertThat(fixture.controller().isRuntimeActive()).isTrue();
        assertThat(fixture.controller().getBlockState().getValue(MachineControllerBlock.ACTIVE)).isTrue();
    }

    @Test
    void client_runtime_activity_uses_synced_runtime_state() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        DynamicMachine machine = factoryOnlyMachine(MMCR.id("client_runtime_sync_machine"), 1);
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        setField(BlockEntity.class, controller, "blockState", testControllerState(testControllerBlock(machine)));
        Level level = LevelStub.create(Map.of(BlockPos.ZERO, testControllerBlock(machine)), List.of(controller));
        setField(Level.class, level, "isClientSide", true);
        setField(BlockEntity.class, controller, "level", level);

        controller.applyClientState("", true, true, List.of());

        assertThat(controller.hasClientActiveRecipe()).isTrue();
        assertThat(controller.isRuntimeActive()).isTrue();
    }

    @Test
    void client_runtime_activity_recovers_from_synced_active_block_state() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        DynamicMachine machine = factoryOnlyMachine(MMCR.id("client_runtime_block_state_machine"), 1);
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        setField(BlockEntity.class, controller, "blockState", testControllerState(testControllerBlock(machine))
                .setValue(MachineControllerBlock.FORMED, true)
                .setValue(MachineControllerBlock.ACTIVE, true));
        Level level = LevelStub.create(Map.of(BlockPos.ZERO, testControllerBlock(machine)), List.of(controller));
        setField(Level.class, level, "isClientSide", true);
        setField(BlockEntity.class, controller, "level", level);

        assertThat(controller.hasClientActiveRecipe()).isFalse();
        assertThat(controller.isRuntimeActive()).isTrue();
    }

    @Test
    void runtime_activity_pauses_for_redstone_and_resumes_from_cached_recipe() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        RuntimeSyncFixture fixture = serverRuntimeFixture(MMCR.id("runtime_redstone_machine"), 20, 1);
        registerItemRecipe("runtime_redstone", fixture.machine().registryName(), 20);
        fixture.controller().serverTick();
        assertThat(fixture.controller().isRuntimeActive()).isTrue();

        LevelStub.setDirectSignal(fixture.level(), fixture.controller().getBlockPos(), 15);
        fixture.controller().serverTick();

        assertThat(fixture.controller().isRuntimeActive()).isFalse();
        assertThat(fixture.controller().getBlockState().getValue(MachineControllerBlock.ACTIVE)).isFalse();

        LevelStub.setDirectSignal(fixture.level(), fixture.controller().getBlockPos(), 0);
        fixture.controller().serverTick();

        assertThat(fixture.controller().isRuntimeActive()).isTrue();
        assertThat(fixture.controller().getBlockState().getValue(MachineControllerBlock.ACTIVE)).isTrue();
    }

    @Test
    void redstone_paused_single_recipe_lock_uses_paused_recipe_without_resuming() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity input = itemInputBus(controllerPos.offset(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        ItemOutputBusBlockEntity output = itemOutputBus(controllerPos.offset(2, 0, 0));
        setField(ItemBusBlockEntity.class, output, "handler", new ItemStackHandler(6));
        FactorySchedulerBlockEntity factory = factoryController(controllerPos.offset(3, 0, 0));
        DynamicMachine machine = new DynamicMachine(MMCR.id("redstone_paused_lock_machine"), "Redstone Paused Lock",
                factoryItemPattern(), MachineControllerSpec.defaultsFor(MMCR.id("redstone_paused_lock_machine")),
                PortRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 1);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFactoryRuntimeFormation(machine, controllerPos, input, output, factory);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        addItemInputComponent(controller, input);
        addItemOutputComponent(controller, output);
        MachineRecipe recipe = registerItemRecipe("redstone_paused_lock_recipe", machine.registryName(), 20);
        controller.serverTick();
        invokeSyncRuntimeStateIfChanged(controller);
        assertThat(controller.factoryThreadSnapshots().getFirst().recipeId()).isEqualTo(recipe.id().toString());

        LevelStub.setDirectSignal(levelOf(controller), controllerPos, 15);
        controller.serverTick();
        invokeSyncRuntimeStateIfChanged(controller);
        assertThat(controller.isRedstonePaused()).isTrue();
        assertThat(controller.isRuntimeActive()).isFalse();

        assertThat(controller.toggleFactoryRecipeLock(0)).isTrue();

        assertThat(controller.isRedstonePaused()).isTrue();
        assertThat(controller.getActive()).isNull();
        assertThat(controller.isRuntimeActive()).isFalse();
        assertThat(controller.factoryThreadSnapshots().getFirst().lockedRecipeId()).isEqualTo(recipe.id().toString());
    }

    @Test
    void redstone_paused_ordinary_recipe_can_be_locked_without_resuming_controller() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("paused_ordinary_lock_recipe"), MMCR.id("paused_ordinary_lock_machine"),
                20, List.of(), List.of());
        RecipeRegistry.register(recipe);
        setField(MachineControllerBlockEntity.class, controller, "redstonePaused", true);
        setField(MachineControllerBlockEntity.class, controller, "pausedActive", new ActiveMachineRecipe(recipe));
        setField(MachineControllerBlockEntity.class, controller, "pausedContext", new RecipeCraftingContext(controller));

        assertThat(controller.toggleFactoryRecipeLock(0)).isTrue();

        assertThat(controller.isRedstonePaused()).isTrue();
        assertThat(controller.getActive()).isNull();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(controller, output);
        assertThat(output.buildResult().toString()).contains("locked_recipe");
        assertThat(output.buildResult().toString()).contains(recipe.id().toString());

        MachineControllerBlockEntity loaded = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(loaded);
        invokeLoadAdditional(loaded, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));
        TagValueOutput loadedOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(loaded, loadedOutput);

        assertThat(loadedOutput.buildResult().toString()).contains(recipe.id().toString());
    }

    @Test
    void ordinary_locked_recipe_is_cleared_when_missing_on_load() throws Exception {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        output.putString("locked_recipe", "mmcr:removed_ordinary_lock_recipe");
        MachineControllerBlockEntity loaded = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(loaded);

        invokeLoadAdditional(loaded, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        TagValueOutput loadedOutput = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(loaded, loadedOutput);
        assertThat(loadedOutput.buildResult().toString()).doesNotContain("locked_recipe");
    }

    @Test
    void runtime_activity_is_false_after_structure_chunk_unload_pauses_recipe() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = new DynamicMachine(MMCR.id("runtime_chunk_pause_machine"), "Runtime Chunk Pause",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()));
        MachineRegistry.register(machine);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_chunk_pause_recipe"), machine.registryName(), 100, List.of(), List.of());
        MachineControllerBlockEntity controller = controllerForServerFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        setField(MachineControllerBlockEntity.class, controller, "active", new ActiveMachineRecipe(recipe, 1));
        setField(MachineControllerBlockEntity.class, controller, "context", new RecipeCraftingContext(controller));
        invokeSyncRuntimeStateIfChanged(controller);
        assertThat(controller.isRuntimeActive()).isTrue();

        MachineControllerBlockEntity.markStructureChunkDirty(levelOf(controller),
                new ChunkPos(controllerPos.getX() >> 4, controllerPos.getZ() >> 4));

        assertThat(controller.isRuntimeActive()).isFalse();
    }

    @Test
    void runtime_activity_switches_false_when_last_factory_lane_finishes() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = factoryOnlyMachine(MMCR.id("runtime_factory_finish_machine"), 1);
        FactorySchedulerBlockEntity factory = factoryController(controllerPos.offset(1, 0, 0));
        MachineControllerBlockEntity controller = controllerForServerFactoryFormation(machine, controllerPos, factory);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        startFiniteFactoryLane(controller, 1);
        invokeSyncRuntimeStateIfChanged(controller);
        assertThat(controller.isRuntimeActive()).isTrue();
        assertThat(blockUpdateCount(levelOf(controller))).isEqualTo(2);

        controller.factoryScheduler().tick();
        invokeSyncRuntimeStateIfChanged(controller);

        assertThat(controller.isRuntimeActive()).isFalse();
        assertThat(blockUpdateCount(levelOf(controller))).isEqualTo(3);
    }

    @Test
    void factory_lane_progress_does_not_repeat_runtime_block_updates() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = factoryOnlyMachine(MMCR.id("runtime_factory_progress_machine"), 1);
        FactorySchedulerBlockEntity factory = factoryController(controllerPos.offset(1, 0, 0));
        MachineControllerBlockEntity controller = controllerForServerFactoryFormation(machine, controllerPos, factory);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        startFiniteFactoryLane(controller, 3);
        invokeSyncRuntimeStateIfChanged(controller);
        int afterStart = blockUpdateCount(levelOf(controller));

        controller.factoryScheduler().tick();
        invokeSyncRuntimeStateIfChanged(controller);
        controller.factoryScheduler().tick();
        invokeSyncRuntimeStateIfChanged(controller);

        assertThat(controller.isRuntimeActive()).isTrue();
        assertThat(blockUpdateCount(levelOf(controller))).isEqualTo(afterStart);
    }

    @Test
    void factory_thread_limit_is_updated_from_controller_capacity() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        FactorySchedulerBlockEntity factory = factoryController(new BlockPos(1, 0, 0), 1);
        addFactorySchedulerComponent(controller, factory);
        setField(MachineControllerBlockEntity.class, controller, "machine",
                factoryOnlyMachine(MMCR.id("factory_capacity_update_machine"), 1));

        assertThat(controller.factoryScheduler().threadLimit()).isEqualTo(controller.effectiveFactoryThreadLimit());
        factory.getItemStackHandler(null).setStackInSlot(0, new ItemStack(ModItems.THREAD_DISPERSER.get(), 3));
        assertThat(controller.factoryScheduler().threadLimit()).isEqualTo(controller.effectiveFactoryThreadLimit());
    }

    @Test
    void synced_runtime_activity_is_not_saved() throws Exception {
        RuntimeSyncFixture fixture = serverRuntimeFixture(MMCR.id("runtime_not_saved_machine"), 20, 1);
        setField(MachineControllerBlockEntity.class, fixture.controller(), "active",
                new ActiveMachineRecipe(new MachineRecipe(MMCR.id("runtime_not_saved_recipe"), fixture.machine().registryName(), 20, List.of(), List.of())));
        setField(MachineControllerBlockEntity.class, fixture.controller(), "context", new RecipeCraftingContext(fixture.controller()));
        invokeSyncRuntimeStateIfChanged(fixture.controller());

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(fixture.controller(), output);

        assertThat(output.buildResult().toString()).doesNotContain("syncedRuntimeActive");
    }

    @Test
    void factory_lanes_do_not_overconsume_shared_inputs() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        FactoryRuntimeFixture fixture = formedFactoryRuntimeFixture(MMCR.id("factory_lane_limited_machine"), 3, 2);
        registerItemRecipe("factory_lane_limited", fixture.machine().registryName(), 20, 0);

        fixture.controller().serverTick();

        assertThat(fixture.controller().factoryScheduler().activeLaneCount()).isEqualTo(2);
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
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()));
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
    void ordinary_locked_recipe_search_does_not_start_other_recipe() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.GOLD_INGOT);
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity input = itemInputBus(controllerPos.offset(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));
        DynamicMachine machine = new DynamicMachine(MMCR.id("ordinary_locked_search_machine"), "Ordinary Locked Search",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()));
        MachineRegistry.register(machine);
        MachineRecipe locked = itemInputRecipe("ordinary_locked_recipe", machine.registryName(), Items.IRON_INGOT);
        MachineRecipe fallback = itemInputRecipe("ordinary_fallback_recipe", machine.registryName(), Items.GOLD_INGOT);
        RecipeRegistry.register(locked);
        RecipeRegistry.register(fallback);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, input);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        addItemInputComponent(controller, input);
        setField(MachineControllerBlockEntity.class, controller, "lockedRecipeId", locked.id());

        controller.serverTick();

        assertThat(controller.getActive()).isNull();
        assertThat(controller.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "lockedRecipeId")).isEqualTo(locked.id());
        assertThat(countItem(input, Items.GOLD_INGOT)).isEqualTo(1);
    }

    @Test
    void ordinary_locked_recipe_restart_does_not_bypass_with_mismatched_last_recipe() throws Exception {
        bindItemComponents(Items.GOLD_INGOT);
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity input = itemInputBus(controllerPos.offset(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));
        DynamicMachine machine = new DynamicMachine(MMCR.id("ordinary_locked_restart_machine"), "Ordinary Locked Restart",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()));
        MachineRegistry.register(machine);
        MachineRecipe locked = itemInputRecipe("ordinary_restart_locked_recipe", machine.registryName(), Items.IRON_INGOT);
        MachineRecipe cached = itemInputRecipe("ordinary_restart_cached_recipe", machine.registryName(), Items.GOLD_INGOT);
        RecipeRegistry.register(locked);
        RecipeRegistry.register(cached);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, input);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        addItemInputComponent(controller, input);
        setField(MachineControllerBlockEntity.class, controller, "lockedRecipeId", locked.id());
        setField(MachineControllerBlockEntity.class, controller, "lastRecipe", cached);
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeStructureVersion", controller.getStructureVersion());
        setField(MachineControllerBlockEntity.class, controller, "lastRecipeModifierSnapshotVersion", controller.getModifierSnapshotVersion());
        setField(MachineControllerBlockEntity.class, controller, "recipeDirty", false);

        controller.serverTick();

        assertThat(controller.getActive()).isNull();
        assertThat(controller.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        assertThat(countItem(input, Items.GOLD_INGOT)).isEqualTo(1);
    }

    @Test
    void ordinary_locked_recipe_survives_save_load_and_limits_search() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.GOLD_INGOT);
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity input = itemInputBus(controllerPos.offset(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 1));
        DynamicMachine machine = new DynamicMachine(MMCR.id("ordinary_locked_persist_machine"), "Ordinary Locked Persist",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()));
        MachineRegistry.register(machine);
        MachineRecipe locked = itemInputRecipe("ordinary_persist_locked_recipe", machine.registryName(), Items.IRON_INGOT);
        MachineRecipe fallback = itemInputRecipe("ordinary_persist_fallback_recipe", machine.registryName(), Items.GOLD_INGOT);
        RecipeRegistry.register(locked);
        RecipeRegistry.register(fallback);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, input);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        setField(MachineControllerBlockEntity.class, controller, "lockedRecipeId", locked.id());
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(controller, saved);

        MachineControllerBlockEntity loaded = controllerForFormation(machine, controllerPos, input);
        assertThat(invokeTryFormMachine(loaded, machine, Direction.SOUTH)).isTrue();
        addItemInputComponent(loaded, input);
        invokeLoadAdditional(loaded, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), saved.buildResult()));

        loaded.serverTick();

        assertThat(loaded.getActive()).isNull();
        assertThat(loaded.getLastFailureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        assertThat(fieldValue(MachineControllerBlockEntity.class, loaded, "lockedRecipeId")).isEqualTo(locked.id());
        assertThat(countItem(input, Items.GOLD_INGOT)).isEqualTo(1);
    }

    @Test
    void redstone_paused_single_recipe_round_trips_and_resumes_from_its_saved_tick() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity input = itemInputBus(controllerPos.offset(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        DynamicMachine machine = new DynamicMachine(MMCR.id("paused_single_machine"), "Paused Single",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()));
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, input);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        addItemInputComponent(controller, input);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("paused_single_recipe"), machine.registryName(), 20,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
        RecipeRegistry.register(recipe);

        controller.serverTick();
        controller.serverTick();
        ActiveMachineRecipe active = controller.getActive();
        assertThat(active).isNotNull();
        assertThat(active.getTick()).isPositive();
        RecipeCraftingContext activeContext = (RecipeCraftingContext) fieldValue(MachineControllerBlockEntity.class, controller, "context");
        activeContext.setRequirementFailure("test.pause.failure", null);

        Level level = levelOf(controller);
        LevelStub.setDirectSignal(level, controllerPos, 15);
        controller.serverTick();
        ActiveMachineRecipe paused = (ActiveMachineRecipe) fieldValue(MachineControllerBlockEntity.class, controller, "pausedActive");
        RecipeCraftingContext pausedContext = (RecipeCraftingContext) fieldValue(MachineControllerBlockEntity.class, controller, "pausedContext");
        int pausedTick = paused.getTick();
        controller.serverTick();
        controller.serverTick();
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "pausedActive")).isSameAs(paused);
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "pausedContext")).isSameAs(pausedContext);
        assertThat(paused.getTick()).isEqualTo(pausedTick);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(controller, output);
        MachineControllerBlockEntity loaded = controllerForFormation(machine, controllerPos, input);
        invokeLoadAdditional(loaded, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(fieldValue(MachineControllerBlockEntity.class, loaded, "pausedActive"))
                .isInstanceOf(ActiveMachineRecipe.class)
                .extracting(value -> ((ActiveMachineRecipe) value).getRecipe().id())
                .isEqualTo(recipe.id());
        assertThat(((ActiveMachineRecipe) fieldValue(MachineControllerBlockEntity.class, loaded, "pausedActive")).getTick())
                .isEqualTo(pausedTick);
        assertThat(((RecipeCraftingContext) fieldValue(MachineControllerBlockEntity.class, loaded, "pausedContext"))
                .getLastFailureUnloc()).isEqualTo("test.pause.failure");

        LevelStub.setDirectSignal(levelOf(loaded), controllerPos, 0);
        loaded.serverTick();

        assertThat(loaded.getActive()).isSameAs(fieldValue(MachineControllerBlockEntity.class, loaded, "active"));
        assertThat(loaded.getActive().getTick()).isGreaterThan(pausedTick);
    }

    @Test
    void structure_resume_recomputes_parallelism_for_paused_recipe() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        Identifier machineId = MMCR.id("paused_parallel_machine");
        DynamicMachine machine = new DynamicMachine(machineId, "Paused Parallel", onePortPattern(Blocks.IRON_BLOCK),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                64, true, false, 1);
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        addParallelComponent(controller, ParallelTier.PLUS);

        MachineRecipe recipe = new MachineRecipe(MMCR.id("paused_parallel_recipe"), machineId, 20,
                List.of(), List.of());
        ActiveMachineRecipe paused = new ActiveMachineRecipe(recipe, 1);
        RecipeCraftingContext pausedContext = new RecipeCraftingContext(controller);
        setField(MachineControllerBlockEntity.class, controller, "pausedActive", paused);
        setField(MachineControllerBlockEntity.class, controller, "pausedContext", pausedContext);

        invokeResumePausedRecipeAfterStructureCheck(controller);

        assertThat(controller.getActive()).isSameAs(paused);
        assertThat(paused.getParallelism()).isEqualTo(16);
    }

    @Test
    void paused_recipe_save_load_keeps_consumed_input_route_and_commits_output() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.IRON_NUGGET);
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity input = itemInputBus(controllerPos.offset(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        ItemOutputBusBlockEntity outputBus = itemOutputBus(controllerPos.offset(2, 0, 0));
        setField(ItemBusBlockEntity.class, outputBus, "handler", new ItemStackHandler(6));
        DynamicMachine machine = new DynamicMachine(MMCR.id("paused_route_machine"), "Paused Route",
                itemInputOutputPattern());
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForItemRuntimeFormation(machine, controllerPos, input, outputBus);
        MachineRecipe recipe = registerItemRecipe("paused_route_recipe", machine.registryName(), 2);

        controller.serverTick();
        assertThat(countItem(input, Items.IRON_INGOT)).isZero();
        LevelStub.setDirectSignal(levelOf(controller), controllerPos, 15);
        controller.serverTick();

        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(controller, saved);
        MachineControllerBlockEntity loaded = controllerForItemRuntimeFormation(machine, controllerPos, input, outputBus);
        assertThat(loaded.getComponents()).isEmpty();
        invokeLoadAdditional(loaded, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), saved.buildResult()));

        LevelStub.setDirectSignal(levelOf(loaded), controllerPos, 0);
        loaded.serverTick();
        loaded.serverTick();
        loaded.serverTick();

        assertThat(loaded.getActive()).isNull();
        assertThat(countItem(outputBus, Items.IRON_NUGGET)).isEqualTo(1);
        assertThat(recipe.id()).isEqualTo(MMCR.id("paused_route_recipe"));
    }

    @Test
    void save_and_load_discard_recipe_slots_without_a_complete_recipe_context_pair() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("orphaned_recipe"), MMCR.id("orphaned_machine"), 20,
                List.of(), List.of());
        RecipeRegistry.register(recipe);
        setField(MachineControllerBlockEntity.class, controller, "active", new ActiveMachineRecipe(recipe));

        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(controller, saved);
        assertThat(saved.buildResult().toString()).doesNotContain("recipe_state");

        MachineControllerBlockEntity loaded = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(loaded);
        invokeLoadAdditional(loaded, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), saved.buildResult()));

        assertThat(loaded.getActive()).isNull();
        assertThat(fieldValue(MachineControllerBlockEntity.class, loaded, "context")).isNull();
        assertThat(fieldValue(MachineControllerBlockEntity.class, loaded, "pausedActive")).isNull();
        assertThat(fieldValue(MachineControllerBlockEntity.class, loaded, "pausedContext")).isNull();
    }

    @Test
    void save_and_load_discards_last_failure_reason() throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        controller.setLastFailureUnloc("gui.mmcr.controller.failure.level_insufficient");

        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        invokeSaveAdditional(controller, saved);

        assertThat(saved.buildResult().toString()).doesNotContain("last_failure_unloc");

        MachineControllerBlockEntity loaded = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(loaded);
        invokeLoadAdditional(loaded, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), saved.buildResult()));

        assertThat(loaded.getLastFailureUnloc()).isNull();
    }

    @Test
    void load_discards_serialized_recipe_without_its_context_pair() throws Exception {
        MachineRecipe recipe = new MachineRecipe(MMCR.id("orphaned_load_recipe"), MMCR.id("orphaned_load_machine"), 20,
                List.of(), List.of());
        RecipeRegistry.register(recipe);
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        saved.putString("recipe_state", "active");
        new ActiveMachineRecipe(recipe).serialize(saved.child("active_recipe"));

        MachineControllerBlockEntity loaded = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(loaded);
        invokeLoadAdditional(loaded, TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), saved.buildResult()));

        assertThat(loaded.getActive()).isNull();
        assertThat(fieldValue(MachineControllerBlockEntity.class, loaded, "context")).isNull();
        assertThat(fieldValue(MachineControllerBlockEntity.class, loaded, "pausedActive")).isNull();
        assertThat(fieldValue(MachineControllerBlockEntity.class, loaded, "pausedContext")).isNull();
    }

    @Test
    void reset_and_removed_stop_factory_controller_lanes() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var factoryMachine = new DynamicMachine(
                MMCR.id("factory_stop_test_machine"),
                "Factory Stop Test",
                onePortPattern(ModBlocks.BLOCKS.get("factory_controller").get()),
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
        addFactoryLane(controller);

        invokeResetMachine(controller);

        assertThat(controller.factoryScheduler().activeLaneCount()).isZero();

        assertThat(invokeTryFormMachine(controller, factoryMachine, Direction.SOUTH)).isTrue();
        addFactoryLane(controller);
        controller.setRemoved();

        assertThat(controller.factoryScheduler().activeLaneCount()).isZero();
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
        assertThat(fixture.controller().factoryScheduler().activeLaneCount()).isEqualTo(2);

        invokeResetMachine(fixture.controller());

        assertThat(fixture.controller().factoryScheduler().activeLaneCount()).isZero();
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
        assertThat(fixture.controller().factoryScheduler().activeLaneCount()).isEqualTo(2);

        fixture.controller().setRemoved();

        assertThat(fixture.controller().factoryScheduler().activeLaneCount()).isZero();
        assertReturnedContexts(pool, fixture.controller(), recipe, 2);
    }

    @Test
    void set_removed_does_not_rewrite_formed_controller_state() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("removed_formed_controller_machine"),
                "Removed Formed Controller",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        BlockState formedState = controller.getBlockState();

        controller.setRemoved();

        assertThat(levelOf(controller).getBlockState(controllerPos)).isSameAs(formedState);
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
        assertThat(fixture.controller().factoryScheduler().activeLaneCount()).isEqualTo(2);

        MachineControllerBlockEntity.markStructureChunkDirty(levelOf(fixture.controller()), new ChunkPos(fixture.controller().getBlockPos().getX() >> 4, fixture.controller().getBlockPos().getZ() >> 4));

        assertThat(fixture.controller().factoryScheduler().activeLaneCount()).isZero();
        assertReturnedContexts(pool, fixture.controller(), recipe, 2);
    }

    @Test
    void twoEnergyHatchesSumStoredAndCapacity() throws Exception {
        EnergyInputHatchBlockEntity first = energyHatch(new BlockPos(1, 0, 0));
        EnergyInputHatchBlockEntity second = energyHatch(new BlockPos(2, 0, 0));
        first.getMutableEnergyStorage().forceInsert(200, false);
        second.getMutableEnergyStorage().forceInsert(300, false);
        MachineControllerBlockEntity controller = controllerWithEnergyHatches(first, second);

        assertThat(controller.totalStoredEnergy()).isEqualTo(500);
        assertThat(controller.totalCapacityEnergy()).isEqualTo(first.getMutableEnergyStorage().getCapacityAsLong() * 2L);
    }

    @Test
    void primaryFluidReturnsFirstNonEmptyInputHatch() throws Exception {
        Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        FluidInputHatchBlockEntity input = fluidInputHatch(new BlockPos(1, 0, 0));
        input.getMutableFluidStorage().setFluid(new FluidStack(Fluids.WATER, 500));
        MachineControllerBlockEntity controller = controllerWithFluidHatch(input);

        assertThat(controller.primaryFluid().getFluid()).isEqualTo(Fluids.WATER);
        assertThat(controller.primaryFluid().getAmount()).isEqualTo(500);
    }

    @Test
    void primaryOutputFluidReturnsFirstNonEmptyOutputHatch() throws Exception {
        Fluids.LAVA.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        FluidOutputHatchBlockEntity output = fluidOutputHatch(new BlockPos(1, 0, 0));
        output.getMutableFluidStorage().setFluid(new FluidStack(Fluids.LAVA, 250));
        MachineControllerBlockEntity controller = controllerWithFluidHatch(output);

        assertThat(controller.primaryOutputFluid().getFluid()).isEqualTo(Fluids.LAVA);
        assertThat(controller.primaryOutputFluid().getAmount()).isEqualTo(250);
    }

    @Test
    void matching_structure_without_requirements_forms() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
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
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
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
    void exclusive_component_claim_prevents_second_controller_from_forming() throws Exception {
        BlockPos firstControllerPos = new BlockPos(0, 64, 0);
        BlockPos secondControllerPos = new BlockPos(2, 64, 0);
        BlockPos componentPos = new BlockPos(1, 64, 0);
        ParallelControllerBlockEntity component = parallelController(ParallelTier.PLUS, componentPos);
        DynamicMachine firstMachine = new DynamicMachine(
                MMCR.id("exclusive_first_machine"), "Exclusive First",
                onePortPattern(ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get()));
        DynamicMachine secondMachine = new DynamicMachine(
                MMCR.id("exclusive_second_machine"), "Exclusive Second",
                new BlockArray(Map.of(new BlockPos(-1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get()))));
        ControllerPairFixture fixture = controllerPair(firstMachine, firstControllerPos, secondMachine, secondControllerPos, component);

        assertThat(invokeTryFormMachine(fixture.first(), firstMachine, Direction.SOUTH)).isTrue();
        assertThat(invokeTryFormMachine(fixture.second(), secondMachine, Direction.SOUTH)).isFalse();

        assertThat(fixture.second().getLastFormationFailure().portId()).contains("component_claim_conflict");
        assertThat(fixture.second().isFormed()).isFalse();
        assertThat(fixture.second().getComponents()).isEmpty();
        assertThat(StructureClaimRegistry.get(fixture.level()).ownersOf(componentPos)).containsExactly(firstControllerPos);
    }

    @Test
    void shared_port_remains_linked_when_one_of_its_controllers_resets() throws Exception {
        BlockPos sharedPortPos = new BlockPos(1, 64, 0);
        ItemInputBusBlockEntity shared = itemInputBus(sharedPortPos);
        DynamicMachine firstMachine = portAppearanceMachine(
                "first_shared_port_machine",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()),
                Identifier.parse("kubejs:block/first_formed_casing"));
        BlockPos firstControllerPos = new BlockPos(0, 64, 0);
        BlockPos secondControllerPos = new BlockPos(4, 64, 0);
        DynamicMachine secondMachine = portAppearanceMachine(
                "second_shared_port_machine",
                new BlockArray(Map.of(new BlockPos(-3, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))),
                Identifier.parse("kubejs:block/second_formed_casing"));
        ControllerPairFixture fixture = controllerPair(firstMachine, firstControllerPos, secondMachine, secondControllerPos, shared);

        assertThat(invokeTryFormMachine(fixture.first(), firstMachine, Direction.SOUTH)).isTrue();
        assertThat(invokeTryFormMachine(fixture.second(), secondMachine, Direction.SOUTH)).isTrue();
        assertThat(fixture.first().resourceDomain().controllers())
                .containsExactlyInAnyOrder(firstControllerPos, secondControllerPos);
        assertThat(fixture.second().resourceDomain().controllers())
                .containsExactlyInAnyOrder(firstControllerPos, secondControllerPos);
        assertThat(StructureClaimRegistry.get(fixture.level()).domainFor(firstControllerPos))
                .isEqualTo(StructureClaimRegistry.get(fixture.level()).domainFor(secondControllerPos));
        assertThat(StructureClaimRegistry.get(fixture.level()).ownersOf(sharedPortPos))
                .containsExactlyInAnyOrder(firstControllerPos, secondControllerPos);

        invokeResetMachine(fixture.first());

        assertThat(shared.linkedControllerPositions()).containsExactly(secondControllerPos);
        assertThat(fixture.second().isFormed()).isTrue();
        assertThat(fixture.second().getComponents()).extracting(ProcessingComponent::getContainer).containsExactly(shared);
        assertThat(fixture.second().hasLinkedPort(shared.getBlockPos())).isTrue();
        assertThat(StructureClaimRegistry.get(fixture.level()).ownersOf(sharedPortPos)).containsExactly(secondControllerPos);
        assertThat(fixture.second().resourceDomain().controllers()).containsExactly(secondControllerPos);
        assertThat(StructureClaimRegistry.get(fixture.level()).domainFor(secondControllerPos).controllers()).containsExactly(secondControllerPos);

        fixture.second().setRemoved();

        assertThat(StructureClaimRegistry.get(fixture.level()).ownersOf(sharedPortPos)).isEmpty();
        assertThat(fixture.second().resourceDomain()).isNull();
    }

    @Test
    void invalidating_structure_resets_linked_port_appearance_base_texture() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
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
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
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
    void removing_formed_controller_stops_active_recipe_without_restoring_its_block_state() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("controller_removal_stops_recipe_machine"),
                "Controller Removal Stops Recipe",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos,
                itemInputBus(controllerPos.offset(1, 0, 0)));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        MachineRecipe recipe = new MachineRecipe(MMCR.id("controller_removal_stops_recipe"), machine.registryName(), 100,
                List.of(), List.of());
        setField(MachineControllerBlockEntity.class, controller, "active", new ActiveMachineRecipe(recipe));
        setField(MachineControllerBlockEntity.class, controller, "context", new RecipeCraftingContext(controller));

        invokeBlockOnRemove(controller.getBlockState().getBlock(), controller.getBlockState(), levelOf(controller), controllerPos,
                Blocks.AIR.defaultBlockState(), false);

        assertThat(controller.getActive()).isNull();
        assertThat(controller.getFoundMachine()).isNull();

        controller.setRemoved();
        controller.serverTick();

        assertThat(controller.getActive()).isNull();
        assertThat(controller.getFoundMachine()).isNull();
    }

    @Test
    void replacing_controller_block_resets_linked_port_appearance_base_texture() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        DynamicMachine machine = portAppearanceMachine(
                "controller_replaced_port_reset_machine",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()),
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
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()),
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
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()),
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
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()),
                formedTexture);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        invokeBlockOnRemove(controller.getBlockState().getBlock(), controller.getBlockState(), levelOf(controller), controllerPos,
                controller.getBlockState().setValue(MachineControllerBlock.ACTIVE, true), false);

        assertThat(port.appearanceBaseTexture()).isEqualTo(formedTexture);
    }

    @Test
    void moving_controller_block_does_not_reset_linked_port_appearance_base_texture() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity port = itemInputBus(controllerPos.offset(1, 0, 0));
        Identifier formedTexture = Identifier.parse("kubejs:block/formed_casing");
        DynamicMachine machine = portAppearanceMachine(
                "controller_moved_keeps_port_machine",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()),
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
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("requires_energy_machine"),
                "Requires Energy",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("requires_energy_machine")),
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
    void port_requirement_failure_diagnostic_has_distinct_reason() {
        DynamicMachine machine = new DynamicMachine(MMCR.id("port_failure_diagnostic_machine"), "Port Failure", new BlockArray(Map.of()));
        var failure = new PortRequirementSpec.Failure(
                PortKinds.ENERGY_INPUT.id(), 0, 1, OptionalInt.empty(), PortRequirementSpec.FailureReason.MISSING);

        String diagnostic = MachineControllerBlockEntity.formationFailureDiagnostic(
                machine, Direction.SOUTH, new BlockPos(10, 4, 10), failure);

        assertThat(diagnostic)
                .contains("machine=mmcr:port_failure_diagnostic_machine")
                .contains("reason=portRequirementMismatch")
                .contains("portId=" + PortKinds.ENERGY_INPUT.id())
                .contains("actual=0")
                .contains("requiredMin=1")
                .contains("requiredMax=unbounded")
                .contains("failureReason=MISSING");
    }

    @Test
    void structure_mismatch_diagnostic_includes_expected_and_actual_block_details() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
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
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("energy_input_hatch").get());
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("requires_energy_machine"),
                "Requires Energy",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("requires_energy_machine")),
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
    void base_port_count_accepts_tiered_port_before_tier_validation() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = anyEnergyInputPattern();
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("requires_ludicrous_energy_with_count_machine"),
                "Requires Ludicrous Energy With Count",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("requires_ludicrous_energy_with_count_machine")),
                PortRequirementSpec.builder().min(PortKinds.ENERGY_INPUT.id(), 1).build(),
                PortTierRequirementSpec.builder().minEnergyInput(EnergyHatchSize.LUDICROUS).build(),
                List.of(),
                Map.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos,
                energyHatch(controllerPos.offset(1, 0, 0), "energy_input_hatch_tiny"));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isFalse();
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
        tickController(controller, Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS);
        assertThat(controller.isFormed()).isTrue();

        Level level = levelOf(controller);
        EnergyInputHatchBlockEntity replacement = energyHatch(portPos);
        setField(BlockEntity.class, replacement, "level", level);
        level.setBlock(portPos, blockForPort(replacement).defaultBlockState(), 3);
        LevelStub.putBlockEntity(level, replacement);
        controller.requestImmediateStructureCheck();

        controller.serverTick();

        assertThat(controller.isFormed()).isFalse();
        assertThat(controller.getLastFormationFailure()).isNotNull();
        assertThat(controller.getLastFormationFailure().portId()).isEqualTo("energy_input_hatch>=ludicrous");
    }

    @Test
    void test_cube_forms_with_required_ports() throws Exception {
        DynamicMachine machine = requiredPortTestCube();
        MachineRegistry.register(machine);
        BlockPos controllerPos = new BlockPos(20, 4, 20);
        MachineControllerBlockEntity controller = controllerForRequiredPortTestCube(
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
    void test_cube_forms_when_top_factory_slot_is_casing() throws Exception {
        DynamicMachine machine = requiredPortTestCube();
        MachineRegistry.register(machine);
        BlockPos controllerPos = new BlockPos(20, 4, 20);
        MachineControllerBlockEntity controller = controllerForRequiredPortTestCube(
                machine,
                controllerPos,
                itemInputBus(controllerPos.offset(0, 0, -2)),
                itemOutputBus(controllerPos.offset(-1, 0, -1)),
                energyHatch(controllerPos.offset(1, 0, -1), "energy_input_hatch_ludicrous"));
        Level level = levelOf(controller);
        level.setBlock(controllerPos.offset(0, 1, -1), ModBlocks.CASING.get().defaultBlockState(), 3);

        boolean formed = invokeTryFormMachine(controller, machine, Direction.SOUTH);

        assertThat(formed).isTrue();
        assertThat(controller.getLastFormationFailure()).isNull();
    }

    @Test
    void server_tick_keeps_formation_failure_observable_after_rejection() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("server_tick_requires_energy_machine"),
                "Requires Energy",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("server_tick_requires_energy_machine")),
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
                MachineControllerSpec.defaultsFor(MMCR.id("cached_requires_energy_machine")),
                PortRequirementSpec.builder().min(PortKinds.ENERGY_INPUT.id(), 1).build());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, energyHatch(portPos));
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        tickController(controller, Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS);
        assertThat(controller.isFormed()).isTrue();

        Level level = levelOf(controller);
        ItemInputBusBlockEntity replacement = itemInputBus(portPos);
        setField(BlockEntity.class, replacement, "level", level);
        level.setBlock(portPos, blockForPort(replacement).defaultBlockState(), 3);
        LevelStub.putBlockEntity(level, replacement);
        controller.requestImmediateStructureCheck();

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
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        var replacement = new SingleBlockModifierReplacement(
                "cached_replacement",
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch").get()),
                List.of(),
                ItemStack.EMPTY);
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("cached_replacement_machine"),
                "Cached Replacement",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("cached_replacement_machine")),
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
    void base_block_forms_without_activating_replacement_modifier() throws Exception {
        var replacement = replacementAt(new BlockPos(1, 0, 0), Blocks.DIAMOND_BLOCK, "speed", 2F);
        var machine = machineWithReplacements(replacement);
        MachineRegistry.register(machine);

        MachineControllerBlockEntity controller = controllerFor(machine);
        placeControllerAndReplacement(controller, machine, Blocks.IRON_BLOCK);
        levelOf(controller).setBlock(controller.getBlockPos().offset(1, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        tickUntilFormed(controller, machine);

        assertThat(controller.isFormed()).isTrue();
        assertThat(controller.getFoundModifiers()).isEmpty();
        assertThat(controller.foundModifierList()).isEmpty();
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
    void privateControllerFinalOutputRetryDoesNotRepeatItsLastTickIo() throws Exception {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.COBBLESTONE);
        ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = itemOutputBus(new BlockPos(2, 0, 0));
        setField(ItemBusBlockEntity.class, output, "handler", new ItemStackHandler(6));
        setField(ItemBusBlockEntity.class, input, "handler", new ItemStackHandler(6) {
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack extracted = super.extractItem(slot, amount, simulate);
                if (!simulate && !extracted.isEmpty()) {
                    for (int outputSlot = 0; outputSlot < output.getItemStackHandler(null).getSlots(); outputSlot++) {
                        output.getItemStackHandler(null).setStackInSlot(outputSlot, new ItemStack(Items.COBBLESTONE, 64));
                    }
                }
                return extracted;
            }

            @Override protected void onContentsChanged(int slot) { }
        });
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        EnergyInputHatchBlockEntity energy = energyHatch(new BlockPos(3, 0, 0));
        energy.getMutableEnergyStorage().forceInsert(20, false);
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        DynamicMachine stateMachine = new DynamicMachine(MMCR.id("private_controller_state"), "Private Controller State", new BlockArray(Map.of()));
        setField(BlockEntity.class, controller, "blockState", testControllerBlock(stateMachine).defaultBlockState());
        Level level = LevelStub.createWithBlockEntities(List.of(input, output, energy));
        setField(Level.class, level, "isClientSide", true);
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        setField(BlockEntity.class, output, "level", level);
        setField(BlockEntity.class, energy, "level", level);
        addItemInputComponent(controller, input);
        addItemOutputComponent(controller, output);
        addComponent(controller, new MachineComponent(PortKinds.ENERGY_INPUT, IOType.INPUT), energy);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("private_controller_finish_retry"), MMCR.id("private_controller"),
                1, List.of(), List.of(), List.of(), 0, 0, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY),
                new EnergyRequirement(10),
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_INGOT))
        ));
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        setField(MachineControllerBlockEntity.class, controller, "active", active);
        setField(MachineControllerBlockEntity.class, controller, "context", new RecipeCraftingContext(controller));

        invokeTickActiveRecipe(controller);

        assertThat(active.isFinishPending()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);

        invokeTickActiveRecipe(controller);
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);

        output.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        setField(ActiveMachineRecipe.class, active, "nextFinishRetryTick", 0);
        invokeTickActiveRecipe(controller);

        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "active")).isNull();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(energy.getMutableEnergyStorage().getAmountAsLong()).isEqualTo(10);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getItem()).isEqualTo(Items.IRON_INGOT);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void private_controller_finish_invokes_sound_dispatch_once() throws Exception {
        TestSoundController controller = controllerWithFinalTickResult(ActiveMachineRecipe.TickStatus.FINISHED);

        invokeTickActiveRecipe(controller);

        assertThat(controller.finishSounds).isEqualTo(1);
    }

    @Test
    void private_controller_cancelled_finish_does_not_invoke_sound_dispatch() throws Exception {
        TestSoundController controller = controllerWithFinalTickResult(ActiveMachineRecipe.TickStatus.CANCELLED);

        invokeTickActiveRecipe(controller);

        assertThat(controller.finishSounds).isZero();
    }

    @Test
    void private_controller_waiting_finish_does_not_invoke_sound_dispatch() throws Exception {
        TestSoundController controller = controllerWithFinalTickResult(ActiveMachineRecipe.TickStatus.WAITING);

        invokeTickActiveRecipe(controller);

        assertThat(controller.finishSounds).isZero();
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
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        var defaults = MachineControllerSpec.defaultsFor(MMCR.id("vertical_non_symmetric_machine"));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("vertical_non_symmetric_machine"),
                "Vertical Non Symmetric",
                pattern,
                new MachineControllerSpec(
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
                itemInputBus(controllerPos.offset(-1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.UP);

        assertThat(formed).isTrue();
        assertThat(controller.getFoundPattern().pattern()).containsKey(new BlockPos(-1, 0, 0));
    }

    @Test
    void vertical_non_symmetric_machine_rejects_other_rolls() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        var defaults = MachineControllerSpec.defaultsFor(MMCR.id("vertical_non_symmetric_reject_machine"));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("vertical_non_symmetric_reject_machine"),
                "Vertical Non Symmetric Reject",
                pattern,
                new MachineControllerSpec(
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
                itemInputBus(controllerPos.offset(-1, 0, 0)));

        boolean formed = invokeTryFormMachine(controller, machine, Direction.UP);

        assertThat(formed).isFalse();
        assertThat(controller.getFoundMachine()).isNull();
    }

    @Test
    void vertical_symmetric_machine_tries_all_rolls_and_caches_matching_pattern() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        var defaults = MachineControllerSpec.defaultsFor(MMCR.id("vertical_symmetric_machine"));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("vertical_symmetric_machine"),
                "Vertical Symmetric",
                pattern,
                new MachineControllerSpec(
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
        var defaults = MachineControllerSpec.defaultsFor(MMCR.id("vertical_symmetric_modifier_roll"));
        var spec = new MachineControllerSpec(
                defaults.id(), defaults.frontTexture(), defaults.sideTexture(), defaults.topTexture(), defaults.bottomTexture(), true, true);
        var replacement = new SingleBlockModifierReplacement(
                "roll_modifier", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT,
                        3F, RecipeModifier.Operation.ADD, false)), ItemStack.EMPTY);
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
    void vertical_symmetric_machine_uses_matched_roll_for_position_modifiers() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos rawPos = new BlockPos(1, 0, 0);
        Direction matchedRoll = Direction.NORTH;
        BlockPos expected = BlockRotator.rotateSouthTo(rawPos, Direction.UP, matchedRoll);
        var defaults = MachineControllerSpec.defaultsFor(MMCR.id("vertical_symmetric_matched_modifier_roll"));
        var spec = new MachineControllerSpec(
                defaults.id(), defaults.frontTexture(), defaults.sideTexture(), defaults.topTexture(), defaults.bottomTexture(), true, true);
        var replacement = new SingleBlockModifierReplacement(
                "matched_roll_modifier", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT,
                        4F, RecipeModifier.Operation.ADD, false)), ItemStack.EMPTY);
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("vertical_symmetric_matched_modifier_roll"), "Vertical Symmetric Matched Modifier Roll",
                new BlockArray(Map.of(rawPos, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                spec, PortRequirementSpec.none(), List.of(), Map.of(rawPos, List.of(replacement)));
        MachineControllerBlockEntity controller = controllerForFormation(
                machine,
                controllerPos,
                Direction.UP,
                Direction.SOUTH,
                itemInputBus(controllerPos.offset(expected)));
        Level level = levelOf(controller);
        level.setBlock(controllerPos.offset(expected), Blocks.GOLD_BLOCK.defaultBlockState(), 3);

        boolean formed = invokeTryFormMachine(controller, machine, Direction.UP);

        assertThat(formed).isTrue();
        assertThat(controller.getFoundPattern().pattern()).containsKey(expected);
        assertThat(controller.assemblyPattern(machine)).isSameAs(controller.getFoundPattern());
        assertThat(controller.getFoundModifiers()).containsKey("matched_roll_modifier");
        assertThat(controller.getFoundModifiers().get("matched_roll_modifier"))
                .extracting(RecipeModifier::getModifier)
                .containsExactly(4F);
    }

    @Test
    void vertical_stage_match_keeps_selected_compiled_pattern_with_non_default_roll() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos rawPos = new BlockPos(1, 0, 0);
        Direction rollFacing = Direction.WEST;
        BlockPos expected = BlockRotator.rotateSouthTo(rawPos, Direction.UP, rollFacing);
        Identifier machineId = MMCR.id("vertical_staged_roll_machine");
        var defaults = MachineControllerSpec.defaultsFor(machineId);
        var spec = new MachineControllerSpec(defaults.id(), defaults.frontTexture(), defaults.sideTexture(),
                defaults.topTexture(), defaults.bottomTexture(), true, false);
        DynamicMachine machine = stagedMachineWithController(machineId, spec,
                onePortPattern(Blocks.IRON_BLOCK),
                new BlockArray(Map.of(rawPos, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK))),
                new BlockArray(Map.of(rawPos, new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK))));
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(
                machine,
                controllerPos,
                Direction.UP,
                rollFacing,
                itemInputBus(controllerPos.offset(expected)));
        levelOf(controller).setBlock(controllerPos.offset(expected), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);

        boolean formed = invokeTryFormMachine(controller, machine, Direction.UP);

        assertThat(formed).isTrue();
        assertThat(controller.getMatchedStructureStage()).isEqualTo(3);
        assertThat(compiledPattern(controller)).isSameAs(MachineRegistry.getCompiledStages(machine.registryName()).get(2));
        assertThat(compiledPattern(controller).stageNumber()).isEqualTo(3);
        assertThat(controller.getFoundPattern().pattern()).containsKey(expected);
    }

    @Test
    void vertical_stage_match_uses_selected_stage_modifier_replacements() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos rawPos = new BlockPos(1, 0, 0);
        Direction rollFacing = Direction.WEST;
        BlockPos expected = BlockRotator.rotateSouthTo(rawPos, Direction.UP, rollFacing);
        Identifier machineId = MMCR.id("vertical_staged_modifier_machine");
        var defaults = MachineControllerSpec.defaultsFor(machineId);
        var spec = new MachineControllerSpec(defaults.id(), defaults.frontTexture(), defaults.sideTexture(),
                defaults.topTexture(), defaults.bottomTexture(), true, false);
        DynamicMachine machine = stagedMachineWithController(machineId, spec, List.of(
                stageWithReplacement(1, Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, "stage_1_modifier", 1F),
                stageWithReplacement(2, Blocks.GOLD_BLOCK, Blocks.EMERALD_BLOCK, "stage_2_modifier", 2F),
                stageWithReplacement(3, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, "stage_3_modifier", 3F)));
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(
                machine,
                controllerPos,
                Direction.UP,
                rollFacing,
                itemInputBus(controllerPos.offset(expected)));
        levelOf(controller).setBlock(controllerPos.offset(expected), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);

        boolean formed = invokeTryFormMachine(controller, machine, Direction.UP);

        assertThat(formed).isTrue();
        assertThat(controller.getMatchedStructureStage()).isEqualTo(3);
        assertThat(controller.getFoundModifiers()).containsOnlyKeys("stage_3_modifier");
        assertThat(controller.getFoundModifiers().get("stage_3_modifier"))
                .extracting(RecipeModifier::getModifier)
                .containsExactly(3F);
    }

    @Test
    void require_vertical_machine_rejects_matching_horizontal_structure() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        var defaults = MachineControllerSpec.defaultsFor(MMCR.id("requires_vertical_machine"));
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("requires_vertical_machine"),
                "Requires Vertical",
                pattern,
                new MachineControllerSpec(
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
        var defaults = MachineControllerSpec.defaultsFor(MMCR.id("bound_machine"));
        DynamicMachine boundMachine = new DynamicMachine(
                MMCR.id("bound_machine"),
                "Bound Machine",
                onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()),
                new MachineControllerSpec(
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
                onePortPattern(ModBlocks.BLOCKS.get("energy_input_hatch").get()));
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
        var machine = new DynamicMachine(MMCR.id("retry_machine"), "Retry Machine", onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get()));
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
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
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
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
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
                List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, Items.IRON_INGOT.getDefaultInstance()))));
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
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("dirty_bounds_machine"), "Dirty Bounds", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(portPos));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isFalse();

        controller.onStructureBlockChanged(portPos);

        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isTrue();
    }

    @Test
    void ordinary_block_change_during_scan_sets_pending_without_invalidating_cursor() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("pending_scan_machine"), "Pending Scan", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(portPos));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, false, 0));
        setField(MachineControllerBlockEntity.class, controller, "structureScan", scan);

        controller.onStructureBlockChanged(portPos);

        assertThat(scan.invalidated()).isNull();
        assertThat(scan.cursor()).isZero();
        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller,
                "pendingStructureInvalidation")).isTrue();
    }

    @Test
    void ordinary_block_change_during_unformed_scan_sets_pending() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("pending_unformed_scan_machine"),
                "Pending Unformed Scan", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(portPos));
        StructureMatcher.ScanState scan = StructureMatcher.beginScan(pattern, Map.of(), true,
                StructureMatcher.ScanOptions.of(5, false, 0));
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        setField(MachineControllerBlockEntity.class, controller, "structureScan", scan);
        setField(BlockEntity.class, controller, "blockState",
                controller.getBlockState().setValue(MachineControllerBlock.FORMED, false));

        controller.onStructureBlockChanged(portPos);

        assertThat(scan.invalidated()).isNull();
        assertThat(scan.cursor()).isZero();
        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller,
                "pendingStructureInvalidation")).isTrue();
    }

    @Test
    void reset_restores_formed_port_texture_even_when_linked_positions_were_lost() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("appearance_reset_machine"),
                "Appearance Reset",
                pattern,
                MachineControllerSpec.defaultsFor(MMCR.id("appearance_reset_machine")),
                MachineAppearanceSpec.fromBasicBlock(Identifier.withDefaultNamespace("blue_ice")),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                Map.of());
        MachineRegistry.register(machine);
        IOPortBlockEntity port = itemInputBus(portPos);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, port);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        assertThat(port.appearanceBaseTexture()).isEqualTo(Identifier.withDefaultNamespace("block/blue_ice"));
        setField(MachineControllerBlockEntity.class, controller, "linkedPortPositions", new HashSet<>());

        invokeResetMachine(controller);

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void static_block_change_marker_marks_matching_formed_controller_dirty() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
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
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("chunk_dirty_machine"), "Chunk Dirty", pattern);
        MachineRegistry.register(machine);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("chunk_dirty_recipe"), machine.registryName(), 100, List.of(), List.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);
        setField(MachineControllerBlockEntity.class, controller, "active", active);
        setField(MachineControllerBlockEntity.class, controller, "context", new RecipeCraftingContext(controller));

        MachineControllerBlockEntity.markStructureChunkDirty(levelOf(controller), new ChunkPos(controllerPos.getX() >> 4, controllerPos.getZ() >> 4));

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
                onePortPattern(ModBlocks.BLOCKS.get("factory_controller").get()),
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
        addFactoryLane(controller);
        assertThat(controller.getActive()).isNull();

        MachineControllerBlockEntity.markStructureChunkDirty(levelOf(controller), new ChunkPos(controllerPos.getX() >> 4, controllerPos.getZ() >> 4));

        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isTrue();
        assertThat(controller.factoryScheduler().activeLaneCount()).isZero();
    }

    @Test
    void block_change_outside_compiled_bounds_does_not_mark_formed_structure_dirty() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("clean_bounds_machine"), "Clean Bounds", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, itemInputBus(controllerPos.offset(1, 0, 0)));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();

        controller.onStructureBlockChanged(controllerPos.offset(8, 0, 0));

        assertThat((boolean) fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isFalse();
    }

    @Test
    void clean_formed_controller_waits_for_structure_check_interval() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = new DynamicMachine(MMCR.id("schedule_clean_machine"), "Schedule Clean",
                onePortPattern(Blocks.IRON_BLOCK));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.IRON_BLOCK);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        AtomicInteger checks = new AtomicInteger();
        controller.setStructureCheckCallbackForTesting(checks::incrementAndGet);
        setField(MachineControllerBlockEntity.class, controller, "structureDirty", false);
        setField(MachineControllerBlockEntity.class, controller, "structureCheckCounter", 0);
        setField(MachineControllerBlockEntity.class, controller, "nextStructureCheckTick", 40L);
        controller.serverTick();
        assertThat(checks).hasValue(0);
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "structureCheckCounter")).isEqualTo(1);
    }

    @Test
    void formed_controller_uses_configured_forty_tick_interval() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = new DynamicMachine(MMCR.id("schedule_forty_tick_machine"), "Schedule Forty Tick",
                onePortPattern(Blocks.IRON_BLOCK));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.IRON_BLOCK);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        AtomicInteger checks = new AtomicInteger();
        controller.setStructureCheckCallbackForTesting(checks::incrementAndGet);
        setField(MachineControllerBlockEntity.class, controller, "structureDirty", false);
        setField(MachineControllerBlockEntity.class, controller, "nextStructureCheckTick", 40L);

        tickController(controller, 39);
        assertThat(checks).hasValue(0);
        tickController(controller, 1);
        assertThat(checks).hasValue(1);
    }

    @Test
    void unformed_failure_does_not_reschedule_every_skipped_tick() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = new DynamicMachine(MMCR.id("schedule_unformed_machine"), "Schedule Unformed",
                onePortPattern(Blocks.IRON_BLOCK));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.AIR);
        AtomicInteger checks = new AtomicInteger();
        controller.setStructureCheckCallbackForTesting(checks::incrementAndGet);
        setField(MachineControllerBlockEntity.class, controller, "structureDirty", true);

        tickController(controller, 1);
        assertThat(checks).hasValue(1);
        tickController(controller, 39);
        assertThat(checks).hasValue(1);
        tickController(controller, 1);
        assertThat(checks).hasValue(2);
    }

    @Test
    void formed_mismatch_waits_full_interval_after_becoming_unformed() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = new DynamicMachine(MMCR.id("schedule_mismatch_machine"), "Schedule Mismatch",
                onePortPattern(Blocks.IRON_BLOCK));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.IRON_BLOCK);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        AtomicInteger checks = new AtomicInteger();
        controller.setStructureCheckCallbackForTesting(checks::incrementAndGet);

        BlockPos structurePos = controllerPos.offset(1, 0, 0);
        levelOf(controller).setBlock(structurePos, Blocks.AIR.defaultBlockState(), 3);
        controller.onStructureBlockChanged(structurePos);
        tickController(controller, 1);

        assertThat(checks).hasValue(1);
        assertThat(controller.isFormed()).isFalse();
        tickController(controller, Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS - 1);
        assertThat(checks).hasValue(1);
        tickController(controller, 1);
        assertThat(checks).hasValue(2);
    }

    @Test
    void configured_interval_above_default_is_not_capped() throws Exception {
        int configuredInterval = Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS + 40;
        setConfigIntervalForTesting(configuredInterval);
        try {
            BlockPos controllerPos = new BlockPos(10, 4, 10);
            DynamicMachine machine = new DynamicMachine(MMCR.id("schedule_configured_interval_machine"),
                    "Schedule Configured Interval", onePortPattern(Blocks.IRON_BLOCK));
            MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.IRON_BLOCK);
            assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
            AtomicInteger checks = new AtomicInteger();
            controller.setStructureCheckCallbackForTesting(checks::incrementAndGet);
            setField(MachineControllerBlockEntity.class, controller, "structureDirty", false);
            setField(MachineControllerBlockEntity.class, controller, "nextStructureCheckTick", (long) configuredInterval);

            tickController(controller, configuredInterval - 1);
            assertThat(checks).hasValue(0);
            tickController(controller, 1);
            assertThat(checks).hasValue(1);
        } finally {
            setConfigIntervalForTesting(Config.DEFAULT_MACHINE_CHECK_INTERVAL_TICKS);
        }
    }

    @Test
    void explicit_request_runs_on_next_tick_without_synchronous_full_scan() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = new DynamicMachine(MMCR.id("schedule_request_machine"), "Schedule Request",
                onePortPattern(Blocks.IRON_BLOCK));
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Blocks.AIR);
        AtomicInteger checks = new AtomicInteger();
        controller.setStructureCheckCallbackForTesting(checks::incrementAndGet);
        setField(MachineControllerBlockEntity.class, controller, "structureDirty", false);
        setField(MachineControllerBlockEntity.class, controller, "nextStructureCheckTick", 40L);

        tickController(controller, 10);
        controller.requestImmediateStructureCheck();
        assertThat(checks).hasValue(0);
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "structureScan")).isNull();
        tickController(controller, 1);
        assertThat(checks).hasValue(1);
    }

    @Test
    void shift_right_click_requests_scan_without_running_it_in_the_interaction_callback() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = new DynamicMachine(MMCR.id("interaction_request_machine"),
                "Interaction Request", onePortPattern(Blocks.IRON_BLOCK));
        MachineControllerBlock block = testControllerBlock(machine);
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", block.defaultBlockState());
        ServerLevel level = serverLevel(Map.of(controllerPos, block), List.of(controller));
        setField(BlockEntity.class, controller, "level", level);
        setField(MachineControllerBlockEntity.class, controller, "structureDirty", false);
        setField(MachineControllerBlockEntity.class, controller, "nextStructureCheckTick", 40L);
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        ServerPlayer player = (ServerPlayer) unsafe.allocateInstance(InteractionServerPlayer.class);

        Method interaction = MachineControllerBlock.class.getDeclaredMethod("useWithoutItem", BlockState.class,
                Level.class, BlockPos.class, net.minecraft.world.entity.player.Player.class, BlockHitResult.class);
        interaction.setAccessible(true);
        interaction.invoke(block, block.defaultBlockState(), level, controllerPos, player,
                new BlockHitResult(Vec3.ZERO, Direction.UP, controllerPos, false));

        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isEqualTo(true);
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "structureScan")).isNull();
    }

    @Test
    void multiple_dirty_notifications_are_consumed_by_one_check_start() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        BlockArray pattern = onePortPattern(ModBlocks.BLOCKS.get("item_input_bus").get());
        DynamicMachine machine = new DynamicMachine(MMCR.id("schedule_dirty_machine"), "Schedule Dirty", pattern);
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos,
                itemInputBus(controllerPos.offset(1, 0, 0)));
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        controller.onStructureBlockChanged(controllerPos.offset(1, 0, 0));
        controller.onStructureBlockChanged(controllerPos.offset(1, 0, 0));
        AtomicInteger checks = new AtomicInteger();
        controller.setStructureCheckCallbackForTesting(checks::incrementAndGet);

        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isEqualTo(true);
        controller.serverTick();
        assertThat(checks).hasValue(1);
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "structureCheckCounter")).isEqualTo(0);
        assertThat(fieldValue(MachineControllerBlockEntity.class, controller, "structureDirty")).isEqualTo(false);
        controller.serverTick();
        assertThat(checks).hasValue(1);
    }

    @Test
    void controller_rotation_invalidates_the_cached_structure_path() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        var defaults = MachineControllerSpec.defaultsFor(MMCR.id("schedule_rotation_machine"));
        var spec = new MachineControllerSpec(defaults.id(), defaults.frontTexture(), defaults.sideTexture(),
                defaults.topTexture(), defaults.bottomTexture(), true, false);
        DynamicMachine machine = new DynamicMachine(MMCR.id("schedule_rotation_machine"), "Schedule Rotation",
                onePortPattern(Blocks.IRON_BLOCK), spec, PortRequirementSpec.none(), List.of(), Map.of());
        MachineControllerBlockEntity controller = controllerForFormation(machine, controllerPos, Direction.UP,
                Direction.WEST, itemInputBus(controllerPos.offset(0, 0, -1)));
        BlockPos formedPos = controllerPos.offset(BlockRotator.rotateSouthTo(new BlockPos(1, 0, 0), Direction.UP, Direction.WEST));
        levelOf(controller).setBlock(formedPos, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        assertThat(invokeTryFormMachine(controller, machine, Direction.UP)).isTrue();
        AtomicInteger checks = new AtomicInteger();
        controller.setStructureCheckCallbackForTesting(checks::incrementAndGet);
        setField(BlockEntity.class, controller, "blockState", controller.getBlockState()
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.EAST));

        controller.serverTick();

        assertThat(checks).hasValue(1);
    }

    @Test
    void modifier_only_refresh_updates_active_total_tick() throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        DynamicMachine machine = machineWithReplacements(replacementAt(
                new BlockPos(1, 0, 0),
                "duration_half",
                new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT,
                        0.5F, RecipeModifier.Operation.MULTIPLY, false)),
                ItemStack.EMPTY));
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
            setField(BlockEntity.class, hatch, "blockState", Blocks.CHEST.defaultBlockState());
            setField(EnergyInputHatchBlockEntity.class, hatch, "kind", PortKinds.all().stream()
                    .filter(kind -> kind.id().equals(id))
                    .findFirst()
                    .orElseThrow());
            setField(EnergyHatchBlockEntity.class, hatch, "storage", new LongEnergyStorage(1000, 1000, () -> {}));
            initializePortAppearance(hatch);
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
            setField(BlockEntity.class, bus, "blockState", Blocks.CHEST.defaultBlockState());
            setField(ItemInputBusBlockEntity.class, bus, "kind", PortKinds.ITEM_INPUT);
            setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(6));
            initializePortAppearance(bus);
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
            setField(BlockEntity.class, bus, "blockState", Blocks.CHEST.defaultBlockState());
            setField(ItemOutputBusBlockEntity.class, bus, "kind", PortKinds.ITEM_OUTPUT);
            initializePortAppearance(bus);
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
            setField(BlockEntity.class, hatch, "blockState", Blocks.CHEST.defaultBlockState());
            setField(FluidInputHatchBlockEntity.class, hatch, "kind", PortKinds.FLUID_INPUT);
            setField(FluidHatchBlockEntity.class, hatch, "storage", new LongFluidStorage(8000, () -> {}));
            initializePortAppearance(hatch);
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
            setField(BlockEntity.class, hatch, "blockState", Blocks.CHEST.defaultBlockState());
            setField(FluidOutputHatchBlockEntity.class, hatch, "kind", PortKinds.FLUID_OUTPUT);
            setField(FluidHatchBlockEntity.class, hatch, "storage", new LongFluidStorage(8000, () -> {}));
            initializePortAppearance(hatch);
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

    private static void initializePortAppearance(IOPortBlockEntity port) throws ReflectiveOperationException {
        initializeLinkedAppearance(port);
    }

    private static void initializeLinkedAppearance(LinkedAppearanceBlockEntity component) throws ReflectiveOperationException {
        setField(LinkedAppearanceBlockEntity.class, component, "appearanceBaseTexture", MMCR.id("block/basic_casing"));
        setField(LinkedAppearanceBlockEntity.class, component, "linkedControllers", new TreeMap<>(BlockPos::compareTo));
        setField(LinkedAppearanceBlockEntity.class, component, "controllerLinkCheckCounter", 0);
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
            initializeLinkedAppearance(entity);
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
            setField(FactorySchedulerBlockEntity.class, entity, "handler", threadDisperserHandler(dispersers));
            initializeLinkedAppearance(entity);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate factory controller", e);
        }
    }

    private static ItemStackHandler threadDisperserHandler(int dispersers) {
        ItemStackHandler handler = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(ModItems.THREAD_DISPERSER.get());
            }
        };
        handler.setStackInSlot(0, new ItemStack(ModItems.THREAD_DISPERSER.get(), dispersers));
        return handler;
    }

    private static void addFactoryLane(MachineControllerBlockEntity controller) throws Exception {
        assertThat(startFactoryLane(controller)).isTrue();
    }

    private static boolean startFactoryLane(MachineControllerBlockEntity controller) {
        FactoryRecipeScheduler scheduler = controller.factoryScheduler();
        return scheduler.startLane(new FactoryRecipeScheduler.Lane() {
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
            MachineComponent port = new MachineComponent(PortKinds.ENERGY_INPUT, IOType.INPUT);
            list.add(new ProcessingComponent(port, hatch, hatch.getBlockPos(), BlockPos.ZERO, (String) null));
        }
        return controller;
    }

    private static MachineControllerBlockEntity controllerWithFluidHatch(BlockEntity hatch) throws Exception {
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
            port = new MachineComponent(PortKinds.FLUID_INPUT, IOType.INPUT);
        } else {
            port = new MachineComponent(PortKinds.FLUID_OUTPUT, IOType.OUTPUT);
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
        addComponent(controller, new MachineComponent(PortKinds.ITEM_INPUT, IOType.INPUT), input);
    }

    private static void addItemOutputComponent(MachineControllerBlockEntity controller, ItemOutputBusBlockEntity output) throws Exception {
        addComponent(controller, new MachineComponent(PortKinds.ITEM_OUTPUT, IOType.OUTPUT), output);
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
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()));
        blocks.put(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()));
        blocks.put(new BlockPos(3, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()));
        return new BlockArray(blocks);
    }

    private static BlockArray twoFactoryControllersPattern() {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()));
        blocks.put(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()));
        return new BlockArray(blocks);
    }

    private static BlockArray itemInputOutputPattern() {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()));
        blocks.put(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()));
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

    private static MachineRecipe itemInputRecipe(String path, Identifier machineId, Item item) {
        return new MachineRecipe(
                MMCR.id(path),
                machineId,
                20,
                List.of(),
                List.of(),
                List.of(),
                0,
                1,
                false,
                List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), 1, ItemStack.EMPTY)));
    }

    private static int countItem(ItemBusBlockEntity input, Item item) {
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

    private static DynamicMachine stagedMachine(Identifier id, BlockArray... stages) {
        return stagedMachineWithController(id, MachineControllerSpec.defaultsFor(id), stages);
    }

    private static DynamicMachine stagedMachineWithController(Identifier id, MachineControllerSpec controllerSpec, BlockArray... stages) {
        List<MachineStructureStage> structureStages = new ArrayList<>();
        for (int i = 0; i < stages.length; i++) {
            structureStages.add(new MachineStructureStage(
                    i + 1, stages[i], PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                    MachineStructureRequirements.EMPTY));
        }
        return stagedMachineWithController(id, controllerSpec, structureStages);
    }

    private static DynamicMachine stagedMachineWithController(
            Identifier id, MachineControllerSpec controllerSpec,
            List<MachineStructureStage> structureStages) {
        return new DynamicMachine(id, "Staged Machine", structureStages.getFirst().pattern(), controllerSpec,
                MachineAppearanceSpec.defaults(), PortRequirementSpec.none(), PortTierRequirementSpec.none(),
                List.of(), Map.of(), 1, false, false, 1, List.of(), structureStages);
    }

    private static MachineStructureStage stageWithReplacement(
            int number, Block patternBlock, Block replacementBlock, String modifierName, float value) {
        BlockPos rawPos = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement(
                modifierName, new BlockPredicate.OfBlock(replacementBlock),
                List.of(new RecipeModifier(IntegrationTypeHelper.TARGET_ITEM, RecipeModifier.IOType.INPUT,
                        value, RecipeModifier.Operation.ADD, false)), ItemStack.EMPTY);
        return new MachineStructureStage(
                number,
                new BlockArray(Map.of(rawPos, new BlockPredicate.OfBlock(patternBlock)), Map.of(), Map.of(rawPos, 'M')),
                PortRequirementSpec.none(),
                PortTierRequirementSpec.none(),
                List.of(),
                MachineStructureRequirements.builder()
                        .modifier('M', replacement)
                        .build(new BlockArray(Map.of(rawPos, new BlockPredicate.OfBlock(patternBlock)), Map.of(), Map.of(rawPos, 'M'))));
    }

    private static BlockArray anyItemOrEnergyInputPattern() {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch").get()))));
        return new BlockArray(blocks);
    }

    private static BlockArray anyEnergyInputPattern() {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.AnyOf(PortKinds.all().stream()
                .filter(kind -> kind.ioType() == IOType.INPUT && kind.energyHatchSize().isPresent())
                .<BlockPredicate>map(kind -> new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(kind.id()).get()))
                .toList()));
        return new BlockArray(blocks);
    }

    private static BlockArray anyItemOutputPattern() {
        Map<BlockPos, BlockPredicate> blocks = new HashMap<>();
        blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()))));
        return new BlockArray(blocks);
    }

    private static PositionedReplacement replacementAt(
            BlockPos pos, Block block, String name, float value) {
        return replacementAt(pos, name, new BlockPredicate.OfBlock(block),
                List.of(new RecipeModifier("item", RecipeModifier.IOType.INPUT,
                        value, RecipeModifier.Operation.ADD, false)), ItemStack.EMPTY);
    }

    private static PositionedReplacement replacementAt(
            BlockPos pos, String name, BlockPredicate predicate, List<RecipeModifier> modifiers, ItemStack stack) {
        return new PositionedReplacement(pos, new SingleBlockModifierReplacement(name, predicate, modifiers, stack));
    }

    private static DynamicMachine machineWithReplacements(
            PositionedReplacement... replacements) {
        Identifier id = MMCR.id("position_modifier_test");
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        pattern.put(BlockPos.ZERO, new BlockPredicate.Any());
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierMap = new LinkedHashMap<>();
        for (PositionedReplacement replacement : replacements) {
            pattern.put(replacement.pos(), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
            modifierMap.computeIfAbsent(replacement.pos(), ignored -> new ArrayList<>()).add(replacement.replacement());
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

    private static DynamicMachine requiredPortTestCube() {
        Identifier id = MMCR.id("test_cube");
        BlockArray pattern = new BlockArray(Map.of(
                BlockPos.ZERO, new BlockPredicate.OfBlock(ModBlocks.controllerFor(id).get()),
                new BlockPos(0, 0, -2), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPos(-1, 0, -1), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_output_bus").get()),
                new BlockPos(1, 0, -1), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch_ludicrous").get()),
                new BlockPos(0, 1, -1), new BlockPredicate.OfBlock(ModBlocks.CASING.get())));
        return new DynamicMachine(id, "Required Port Test Cube", pattern, MachineControllerSpec.defaultsFor(id),
                MachineAppearanceSpec.defaults(), PortRequirementSpec.none(), PortTierRequirementSpec.builder()
                        .minEnergyInput(EnergyHatchSize.LUDICROUS).minItemInput(ItemBusSize.NORMAL).anyItemOutput().build(),
                List.of(), Map.of(), Integer.MAX_VALUE, true, true, 4, List.of());
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
        for (var entry : machine.modifierReplacements().entrySet()) {
            for (SingleBlockModifierReplacement replacement : entry.getValue()) {
                blocks.put(controllerPos.offset(entry.getKey()), replacementBlockFor(replacement, replacementBlocks[index++]));
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

    private record PositionedReplacement(BlockPos pos, SingleBlockModifierReplacement replacement) {
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
            Block componentBlock) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        var controllerState = testControllerState(controllerBlock);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Level level = LevelStub.create(Map.of(
                controllerPos, controllerBlock,
                controllerPos.offset(1, 0, 0), componentBlock), List.of(controller));
        setField(BlockEntity.class, controller, "level", level);
        return controller;
    }

    private static RuntimeSyncFixture serverRuntimeFixture(Identifier machineId, int ticks, int inputCount) throws Exception {
        BlockPos controllerPos = new BlockPos(10, 4, 10);
        ItemInputBusBlockEntity input = itemInputBus(controllerPos.offset(1, 0, 0));
        input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, inputCount));
        ItemOutputBusBlockEntity output = itemOutputBus(controllerPos.offset(2, 0, 0));
        setField(ItemBusBlockEntity.class, output, "handler", new ItemStackHandler(6));
        DynamicMachine machine = new DynamicMachine(machineId, "Runtime Sync", itemInputOutputPattern());
        MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = controllerForItemRuntimeFormation(machine, controllerPos, input, output);
        assertThat(invokeTryFormMachine(controller, machine, Direction.SOUTH)).isTrue();
        addItemInputComponent(controller, input);
        addItemOutputComponent(controller, output);
        return new RuntimeSyncFixture(controller, levelOf(controller), input, output, machine, ticks);
    }

    private static MachineControllerBlockEntity controllerForServerFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            IOPortBlockEntity port) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        var controllerState = testControllerState(controllerBlock);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(port.getBlockPos(), blockForPort(port));
        CountingServerLevel level = countingServerLevel(blocks, List.of(controller, port));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, port, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity controllerForServerItemRuntimeFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            ItemInputBusBlockEntity input,
            ItemOutputBusBlockEntity output) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        var controllerState = testControllerState(controllerBlock);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(input.getBlockPos(), ModBlocks.BLOCKS.get("item_input_bus").get());
        blocks.put(output.getBlockPos(), ModBlocks.BLOCKS.get("item_output_bus").get());
        CountingServerLevel level = countingServerLevel(blocks, List.of(controller, input, output));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        setField(BlockEntity.class, output, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity controllerForServerFactoryFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            FactorySchedulerBlockEntity factory) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        var controllerState = testControllerState(controllerBlock);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(factory.getBlockPos(), ModBlocks.BLOCKS.get("factory_controller").get());
        CountingServerLevel level = countingServerLevel(blocks, List.of(controller, factory));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, factory, "level", level);
        return controller;
    }

    private static DynamicMachine factoryOnlyMachine(Identifier machineId, int threadLimit) {
        DynamicMachine machine = new DynamicMachine(
                machineId,
                "Factory Runtime Sync",
                onePortPattern(ModBlocks.BLOCKS.get("factory_controller").get()),
                MachineControllerSpec.defaultsFor(machineId),
                PortRequirementSpec.none(),
                List.of(),
                Map.of(),
                1,
                false,
                true,
                threadLimit);
        MachineRegistry.register(machine);
        return machine;
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
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.FACING, facing)
                .setValue(MachineControllerBlock.ROLL_FACING, rollFacing);
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

    private static ControllerPairFixture controllerPair(
            DynamicMachine firstMachine,
            BlockPos firstControllerPos,
            DynamicMachine secondMachine,
            BlockPos secondControllerPos,
            BlockEntity component) throws Exception {
        MachineControllerBlockEntity first = controllerBlockEntityWithoutRunningMinecraftConstructor();
        MachineControllerBlockEntity second = controllerBlockEntityWithoutRunningMinecraftConstructor();
        var firstBlock = testControllerBlock(firstMachine);
        var secondBlock = testControllerBlock(secondMachine);
        BlockState firstState = testControllerState(firstBlock);
        BlockState secondState = testControllerState(secondBlock);
        setField(BlockEntity.class, first, "worldPosition", firstControllerPos);
        setField(BlockEntity.class, first, "blockState", firstState);
        setField(BlockEntity.class, second, "worldPosition", secondControllerPos);
        setField(BlockEntity.class, second, "blockState", secondState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(firstControllerPos, firstBlock);
        blocks.put(secondControllerPos, secondBlock);
        blocks.put(component.getBlockPos(), component instanceof IOPortBlockEntity port
                ? blockForPort(port)
                : ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get());
        ServerLevel level = serverLevel(blocks, List.of(first, second, component));
        setField(BlockEntity.class, first, "level", level);
        setField(BlockEntity.class, second, "level", level);
        setField(BlockEntity.class, component, "level", level);
        return new ControllerPairFixture(first, second, level);
    }

    private static BlockState testControllerState(MachineControllerBlock block) {
        return block.defaultBlockState()
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
    }

    private static ServerLevel serverLevel(Map<BlockPos, Block> blocks, List<BlockEntity> blockEntities) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        TestServerLevel level = (TestServerLevel) unsafe.allocateInstance(TestServerLevel.class);
        setField(TestServerLevel.class, level, "blocks", new HashMap<>(blocks.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().defaultBlockState()))));
        setField(TestServerLevel.class, level, "blockEntities", blockEntities.stream()
                .collect(Collectors.toMap(BlockEntity::getBlockPos, entity -> entity)));
        return level;
    }

    private static CountingServerLevel countingServerLevel(Map<BlockPos, Block> blocks, List<BlockEntity> blockEntities) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        CountingServerLevel level = (CountingServerLevel) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(CountingServerLevel.class);
        setField(TestServerLevel.class, level, "blocks", new HashMap<>(blocks.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().defaultBlockState()))));
        setField(TestServerLevel.class, level, "blockEntities", blockEntities.stream()
                .collect(Collectors.toMap(BlockEntity::getBlockPos, entity -> entity)));
        setField(CountingServerLevel.class, level, "directSignals", new HashMap<>());
        return level;
    }

    private static void setDirectSignal(ServerLevel level, BlockPos pos, int signal) throws Exception {
        Map<BlockPos, Integer> directSignals = (Map<BlockPos, Integer>) fieldValue(CountingServerLevel.class, level, "directSignals");
        directSignals.put(pos, signal);
    }

    private static int blockUpdateCount(Level level) {
        return ((CountingServerLevel) level).blockUpdates;
    }

    private static void fillOutputBus(ItemOutputBusBlockEntity outputBus, Item item) {
        for (int slot = 0; slot < outputBus.getItemStackHandler(null).getSlots(); slot++) {
            outputBus.getItemStackHandler(null).setStackInSlot(slot, new ItemStack(item, 64));
        }
    }

    private static void startFiniteFactoryLane(MachineControllerBlockEntity controller, int ticks) {
        FactoryRecipeScheduler scheduler = controller.factoryScheduler();
        assertThat(scheduler.startLane(new FactoryRecipeScheduler.Lane() {
            private int remaining = ticks;

            @Override
            public boolean tick() {
                return --remaining <= 0;
            }

            @Override
            public boolean tick(long gameTime) {
                return tick();
            }

            @Override
            public void stop() { }
        })).isTrue();
    }

    private static MachineControllerBlockEntity controllerForParallelFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            ParallelControllerBlockEntity parallel) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        var controllerState = controllerBlock.defaultBlockState()
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(parallel.getBlockPos(), ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get());
        Level level = LevelStub.create(blocks, List.of(controller, parallel));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, parallel, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity controllerForSmartInterfaceFormation(
            DynamicMachine machine, BlockPos controllerPos, SmartInterfaceBlockEntity smartInterface) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", testControllerState(controllerBlock));
        Level level = LevelStub.create(Map.of(
                controllerPos, controllerBlock,
                smartInterface.getBlockPos(), ModBlocks.SMART_INTERFACE.get()), List.of(controller, smartInterface));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, smartInterface, "level", level);
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
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(factory.getBlockPos(), ModBlocks.BLOCKS.get("factory_controller").get());
        Level level = LevelStub.create(blocks, List.of(controller, factory));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, factory, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity controllerForFactoriesFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            FactorySchedulerBlockEntity first,
            FactorySchedulerBlockEntity second) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        var controllerState = controllerBlock.defaultBlockState()
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(first.getBlockPos(), ModBlocks.BLOCKS.get("factory_controller").get());
        blocks.put(second.getBlockPos(), ModBlocks.BLOCKS.get("factory_controller").get());
        Level level = LevelStub.create(blocks, List.of(controller, first, second));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, first, "level", level);
        setField(BlockEntity.class, second, "level", level);
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
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(input.getBlockPos(), ModBlocks.BLOCKS.get("item_input_bus").get());
        blocks.put(output.getBlockPos(), ModBlocks.BLOCKS.get("item_output_bus").get());
        blocks.put(factory.getBlockPos(), ModBlocks.BLOCKS.get("factory_controller").get());
        Level level = LevelStub.create(blocks, List.of(controller, input, output, factory));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        setField(BlockEntity.class, output, "level", level);
        setField(BlockEntity.class, factory, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity controllerForItemRuntimeFormation(
            DynamicMachine machine,
            BlockPos controllerPos,
            ItemInputBusBlockEntity input,
            ItemOutputBusBlockEntity output) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = testControllerBlock(machine);
        var controllerState = controllerBlock.defaultBlockState()
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Map<BlockPos, Block> blocks = new HashMap<>();
        blocks.put(controllerPos, controllerBlock);
        blocks.put(input.getBlockPos(), ModBlocks.BLOCKS.get("item_input_bus").get());
        blocks.put(output.getBlockPos(), ModBlocks.BLOCKS.get("item_output_bus").get());
        Level level = LevelStub.create(blocks, List.of(controller, input, output));
        setField(BlockEntity.class, controller, "level", level);
        setField(BlockEntity.class, input, "level", level);
        setField(BlockEntity.class, output, "level", level);
        return controller;
    }

    private static MachineControllerBlockEntity controllerForPattern(
            DynamicMachine machine,
            BlockPos controllerPos,
            IOPortBlockEntity first,
            IOPortBlockEntity second,
            IOPortBlockEntity third) throws Exception {
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        initializeComponents(controller);
        var controllerBlock = ModBlocks.hasControllerFor(machine.registryName())
                ? (MachineControllerBlock) ModBlocks.controllerFor(machine.registryName()).get()
                : testControllerBlock(machine);
        var controllerState = controllerBlock.defaultBlockState()
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "worldPosition", controllerPos);
        setField(BlockEntity.class, controller, "blockState", controllerState);

        Map<BlockPos, Block> blocks = new HashMap<>();
        for (var entry : machine.pattern().pattern().entrySet()) {
            blocks.put(controllerPos.offset(entry.getKey()), switch (entry.getValue()) {
                case BlockPredicate.OfBlock of -> of.block();
                case BlockPredicate.AnyOf anyOf -> firstBlock(anyOf);
                default -> ModBlocks.CASING.get();
            });
        }
        blocks.put(controllerPos, controllerBlock);
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

    private static MachineControllerBlockEntity controllerForRequiredPortTestCube(
            DynamicMachine machine,
            BlockPos controllerPos,
            IOPortBlockEntity first,
            IOPortBlockEntity second,
            IOPortBlockEntity third) throws Exception {
        MachineControllerBlockEntity controller = controllerForPattern(
                machine, controllerPos, first, second, third);
        var controllerBlock = ModBlocks.controllerFor(MMCR.id("test_cube")).get();
        BlockState controllerState = controllerBlock.defaultBlockState()
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        levelOf(controller).setBlock(controllerPos, controllerState, 3);
        return controller;
    }

    private static Block firstBlock(BlockPredicate.AnyOf predicate) {
        for (BlockPredicate child : predicate.children()) {
            if (child instanceof BlockPredicate.OfBlock of) return of.block();
        }
        return ModBlocks.CASING.get();
    }

    private static Block blockForPort(IOPortBlockEntity port) {
        return ModBlocks.BLOCKS.get(port.kind().id()).get();
    }

    private static MachineControllerBlock testControllerBlock(DynamicMachine machine) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var block = (MachineControllerBlock) unsafe.allocateInstance(MachineControllerBlock.class);
        setField(MachineControllerBlock.class, block, "machineId", machine.registryName());
        setField(
                BlockBehaviour.class,
                block,
                "properties",
                Blocks.IRON_BLOCK.properties());
        var builder = new StateDefinition.Builder<Block, BlockState>(block);
        builder.add(
                MachineControllerBlock.FACING,
                MachineControllerBlock.ROLL_FACING,
                MachineControllerBlock.FORMED,
                MachineControllerBlock.ACTIVE);
        var stateDefinition = builder.create(Block::defaultBlockState, BlockState::new);
        setField(Block.class, block, "stateDefinition", stateDefinition);
        setField(Block.class, block, "defaultBlockState", stateDefinition.any()
                .setValue(MachineControllerBlock.FACING, Direction.NORTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH)
                .setValue(MachineControllerBlock.FORMED, false)
                .setValue(MachineControllerBlock.ACTIVE, false));
        return block;
    }

    private static boolean invokeTryFormMachine(MachineControllerBlockEntity controller, DynamicMachine machine, Direction facing) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("tryFormMachine", Machine.class, Direction.class);
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

    private static void invokeResumePausedRecipeAfterStructureCheck(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("resumePausedRecipeAfterStructureCheck");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static void invokeSaveAdditional(MachineControllerBlockEntity controller, TagValueOutput output) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("saveAdditional", ValueOutput.class);
        method.setAccessible(true);
        method.invoke(controller, output);
    }

    private static void invokeLoadAdditional(MachineControllerBlockEntity controller, ValueInput input) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("loadAdditional", ValueInput.class);
        method.setAccessible(true);
        method.invoke(controller, input);
    }

    private static void invokeTickFactoryRecipes(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("tickFactoryRecipes");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static void invokeSyncRuntimeStateIfChanged(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("syncRuntimeStateIfChanged");
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

    private static void tickController(MachineControllerBlockEntity controller, int ticks) throws Exception {
        Level level = levelOf(controller);
        for (int i = 0; i < ticks; i++) {
            LevelStub.setGameTime(level, level.getGameTime() + 1);
            controller.serverTick();
        }
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setConfigIntervalForTesting(int interval) throws ReflectiveOperationException {
        Field field = Config.MACHINE_CHECK_INTERVAL_TICKS.getClass().getSuperclass().getDeclaredField("cachedValue");
        field.setAccessible(true);
        field.set(Config.MACHINE_CHECK_INTERVAL_TICKS, interval);
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

    private record RuntimeSyncFixture(MachineControllerBlockEntity controller,
                                      Level level,
                                      ItemInputBusBlockEntity inputBus,
                                      ItemOutputBusBlockEntity outputBus,
                                      DynamicMachine machine,
                                      int ticks) { }

    private record ControllerPairFixture(MachineControllerBlockEntity first,
                                         MachineControllerBlockEntity second,
                                         ServerLevel level) { }

    private static class TestServerLevel extends ServerLevel {
        private Map<BlockPos, BlockState> blocks;
        private Map<BlockPos, BlockEntity> blockEntities;

        private TestServerLevel() {
            super(null, null, null, null, null, null, false, 0L, List.of(), false);
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override public List<ServerPlayer> players() { return List.of(); }

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return blockEntities.get(pos);
        }

        @Override public void blockEntityChanged(BlockPos pos) { }

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            blocks.put(pos, state);
            BlockEntity blockEntity = blockEntities.get(pos);
            if (blockEntity != null) {
                try {
                    setField(BlockEntity.class, blockEntity, "blockState", state);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Unable to update block entity state", e);
                }
            }
            return true;
        }

        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }

        @Override public boolean hasChunk(int chunkX, int chunkZ) { return true; }

        @Override public void invalidateCapabilities(BlockPos pos) { }
    }

    private static final class CountingServerLevel extends TestServerLevel {
        private Map<BlockPos, Integer> directSignals;
        private int blockUpdates;

        private CountingServerLevel() { }

        @Override public int getDirectSignalTo(BlockPos pos) {
            return directSignals.getOrDefault(pos, 0);
        }

        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
            blockUpdates++;
        }
    }

    private static TestSoundController controllerWithFinalTickResult(ActiveMachineRecipe.TickStatus status) throws Exception {
        TestSoundController controller = testSoundControllerWithoutRunningMinecraftConstructor();
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        DynamicMachine machine = new DynamicMachine(MMCR.id("finish_sound_" + status.name().toLowerCase()),
                "Finish Sound", new BlockArray(Map.of()));
        MachineRecipe recipe = new MachineRecipe(MMCR.id("finish_sound_recipe_" + status.name().toLowerCase()),
                machine.registryName(), 1, List.of(), List.of(), List.of(), 0, 0,
                status == ActiveMachineRecipe.TickStatus.CANCELLED, List.of(), finishSoundRequirements(status));
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        setField(MachineControllerBlockEntity.class, controller, "foundMachine", machine);
        setField(MachineControllerBlockEntity.class, controller, "active", active);
        setField(MachineControllerBlockEntity.class, controller, "context", context);
        return controller;
    }

    private static List<MachineRequirement> finishSoundRequirements(
            ActiveMachineRecipe.TickStatus status) {
        if (status == ActiveMachineRecipe.TickStatus.CANCELLED) {
            return List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY));
        }
        if (status == ActiveMachineRecipe.TickStatus.WAITING) {
            return List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_INGOT)));
        }
        return List.of();
    }

    private static TestSoundController testSoundControllerWithoutRunningMinecraftConstructor() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (TestSoundController) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(TestSoundController.class);
    }

    private static final class InteractionServerPlayer extends ServerPlayer {
        private InteractionServerPlayer() {
            super(null, null, new GameProfile(UUID.randomUUID(), "interaction-test"),
                    ClientInformation.createDefault());
        }

        @Override
        public boolean isShiftKeyDown() {
            return true;
        }

        @Override
        public ItemStack getMainHandItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack getOffhandItem() {
            return ItemStack.EMPTY;
        }
    }

    /** Test seam for counting finish sound dispatches. */
    private static final class TestSoundController extends MachineControllerBlockEntity {
        private int finishSounds;

        private TestSoundController() {
            super(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
        }

        @Override
        void playFinishSound() {
            finishSounds++;
        }
    }
}
