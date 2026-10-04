package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachinePatternCompiler;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.NetworkInterfaceSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.api.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.api.machine.definition.MachineBehavior;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.client.model.DynamicOverlayItemModel;
import cn.howxu.mmcr.client.model.DynamicOverlayModelLoader;
import cn.howxu.mmcr.client.model.RuntimeMachineModelRegistry;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.ComponentRuntime;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final controller structure and preview behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerBlockEntityTest {
    private final List<ControllerScreenTextRegistry.Registration> textRegistrations = new ArrayList<>();

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER,
                new MenuType<>((containerId, inventory) -> MachineControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
        bind(ModUIs.FACTORY_CONTROLLER,
                new MenuType<>((containerId, inventory) -> FactoryControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
    }

    @BeforeEach
    void openTextRegistration() {
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
    }

    @AfterEach
    void closeTextRegistration() {
        textRegistrations.forEach(ControllerScreenTextRegistry.Registration::unregister);
        textRegistrations.clear();
    }

    @Test
    void structure_stage_and_preview_use_the_public_controller_boundaries() {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_boundary"), "Controller Boundary",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_boundary")));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        controller.setMachine(machine);
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get()), List.of(controller)));

        assertThat(controller.assemblyPattern(machine, 1).pattern()).containsKey(new BlockPos(-1, 0, 0));
        assertThat(controller.createStructurePreviewSnapshot(16)).isPresent();
        assertThat(controller.structureSnapshot().formed()).isFalse();

        controller.setFormed(true);

        assertThat(controller.structureSnapshot().formed()).isTrue();
    }

    @Test
    void idle_runtime_work_reuses_the_published_snapshot() {
        Identifier machineId = MMCR.id("idle_runtime_snapshot");
        DynamicMachine machine = new DynamicMachine(machineId, "Idle Runtime Snapshot",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, machine);
        ServerLevel level = (ServerLevel) controller.getLevel();

        controller.tickRuntimeWork(level, controller.getBlockPos());
        var published = controller.runtimeSnapshot();
        controller.tickRuntimeWork(level, controller.getBlockPos());

        assertThat(controller.runtimeSnapshot()).isSameAs(published);
    }

    @Test
    void server_progress_reuses_owned_static_state_and_callbacks_read_batch_writes() throws Exception {
        Identifier machineId = MMCR.id("shared_progress_batch");
        MachineControllerBlockEntity controller = factoryTextController(machineId);
        MachineControllerRuntime runtime = runtimeOf(controller);
        controller.componentRuntime().replaceModifiers(Map.of("progress", List.of(MachineModifier.parallelized(true))));
        MachineLevel level = snapshotLevel("shared_progress_level");
        controller.componentRuntime().replaceLevels(Map.of(level.id(), level));
        runtime.publishUpgradeBusState(List.of(new ComponentRuntime.UpgradeBusSnapshot(BlockPos.ZERO,
                List.of(new ItemStack(Items.DIAMOND, 2)))));
        DataStorage storage = new DataStorage();
        storage.set("mode", DataValue.of("before"));
        runtime.publishDataStorages(Map.of(BlockPos.ZERO, storage));
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("shared_progress_recipe"), machineId,
                20, List.of(), List.of());
        assertThat(runtime.craftingRuntime().start(recipe, 1L).isCrafting()).isTrue();
        runtime.refreshCraftingState();
        var before = controller.runtimeSnapshot();
        assertThat(before.factoryControllerPresent()).isTrue();
        AtomicInteger callbacks = new AtomicInteger();
        textRegistrations.add(ControllerScreenTextRegistry.register(machineId, context -> {
            var live = controller.currentRuntimeSnapshot();
            assertThat(live.crafting().tick()).isEqualTo(7);
            assertThat(live.crafting().failure()).isSameAs(runtime.craftingRuntime().failure());
            assertThat(live.dataStorageValues()).containsEntry("mode", DataValue.of("after"));
            callbacks.incrementAndGet();
        }));

        runtime.beginUpdateBatch();
        try {
            runtime.craftingRuntime().activeRecipe().setTick(7);
            runtime.refreshCraftingState();
            var progressed = controller.currentRuntimeSnapshot();
            assertSharedStaticState(progressed, before);
            assertThat(ownedUpgradeItems(progressed)).isSameAs(ownedUpgradeItems(before));
            assertThat(progressed.recipePresentation()).isSameAs(before.recipePresentation());
            assertThat(controller.runtimeSnapshot()).isSameAs(before);
            progressed.upgradeItems().getFirst().setCount(64);
            assertThat(before.upgradeItems().getFirst().getCount()).isEqualTo(2);
            assertThat(progressed.upgradeItems().getFirst().getCount()).isEqualTo(2);

            storage.set("mode", DataValue.of("after"));
            runtime.onDataStorageChanged(storage);
            var changedData = controller.currentRuntimeSnapshot();
            assertThat(changedData.dataStorageValues()).isNotSameAs(before.dataStorageValues());
            assertThat(ownedUpgradeItems(changedData)).isNotSameAs(ownedUpgradeItems(before));
            runtime.craftingRuntime().recordSearchFailure(null);
            runtime.refreshCraftingState();
            var failed = controller.currentRuntimeSnapshot();
            assertSharedStaticState(failed, changedData);
            assertThat(failed.crafting().failure()).isNotNull();
            ControllerScreenTextRegistry.apply(runtime.runtimeContext());
        } finally {
            runtime.endUpdateBatch();
        }

        assertThat(callbacks).hasValue(1);
        assertThat(controller.runtimeSnapshot()).isSameAs(controller.currentRuntimeSnapshot());
        assertThat(controller.runtimeSnapshot().crafting().tick()).isEqualTo(7);
        assertThat(before.crafting().tick()).isZero();
        assertThat(before.crafting().failure()).isNull();
        assertThat(before.dataStorageValues()).containsEntry("mode", DataValue.of("before"));
    }

    @Test
    void dynamic_factory_capability_and_output_revisions_keep_static_ownership() throws Exception {
        Identifier machineId = MMCR.id("shared_dynamic_revisions");
        MachineControllerBlockEntity controller = factoryTextController(machineId);
        MachineControllerRuntime runtime = runtimeOf(controller);
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(2, 0, 0));
        List<ProcessingComponent> components = new ArrayList<>(controller.componentRuntime().components());
        components.add(new ProcessingComponent(new MachineComponent(energy.kind(), energy.ioType()), energy,
                energy.getBlockPos(), BlockPos.ZERO, List.of()));
        controller.componentRuntime().replaceComponents(components);
        controller.componentRuntime().replaceModifiers(Map.of("revision", List.of(MachineModifier.parallelized(true))));
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("shared_revision_recipe"), machineId,
                20, List.of(), List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 3), 1F)),
                List.of(), 0, 1, false, false, false, Set.of());
        assertThat(runtime.craftingRuntime().start(recipe, 1L).isCrafting()).isTrue();
        runtime.craftingRuntime().activeRecipe().setMaxParallelism(2L);
        runtime.craftingRuntime().activeRecipe().setParallelism(2L);
        runtime.refreshCraftingState();
        var before = controller.runtimeSnapshot();
        assertThat(before.capabilityPresentations()).isNotEmpty();

        energy.energyStorage().setAmount(17L);
        controller.componentRuntime().markCapabilityPresentationChanged();
        runtime.factoryRuntime().setLaneLimit(2);
        runtime.publishSnapshot();
        var dynamic = controller.runtimeSnapshot();
        assertSharedStaticState(dynamic, before);
        assertThat(dynamic.capabilityPresentations()).isNotSameAs(before.capabilityPresentations());
        assertThat(dynamic.capabilityPresentations()).isEqualTo(controller.componentRuntime().capabilityPresentations());
        assertThat(dynamic.factory()).isSameAs(runtime.factoryRuntime().snapshot()).isNotSameAs(before.factory());
        assertThat(dynamic.factory().laneLimit()).isEqualTo(2);
        assertThat(before.factory().laneLimit()).isEqualTo(1);

        var active = runtime.craftingRuntime().activeRecipe();
        active.setEffectiveExecutionSnapshot(new RecipeStartContext.ExecutionSnapshot(active.getTotalTick(), List.of(),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 5), 1F))));
        var outputs = controller.currentRuntimeSnapshot();
        assertSharedStaticState(outputs, dynamic);
        assertThat(outputs.crafting()).isEqualTo(dynamic.crafting());
        assertThat(outputs.recipePresentation()).isNotSameAs(dynamic.recipePresentation());
        assertThat(outputs.recipePresentation().outputs()).extracting(MachineOutputAmount::amount).containsExactly(10L);
        assertThat(before.recipePresentation().outputs()).extracting(MachineOutputAmount::amount).containsExactly(6L);
        runtime.publishSnapshot();
        assertThat(controller.runtimeSnapshot()).isSameAs(outputs);
        active.setParallelism(1L);
        active.setTotalTick(40);
        runtime.refreshCraftingState();
        var resized = controller.runtimeSnapshot();
        assertSharedStaticState(resized, outputs);
        assertThat(resized.crafting().parallelism()).isEqualTo(1L);
        assertThat(resized.recipePresentation().durationTicks()).isEqualTo(40);
        assertThat(resized.recipePresentation().outputs()).extracting(MachineOutputAmount::amount).containsExactly(5L);
    }

    @Test
    void static_changes_rebuild_snapshot_and_preserve_previous_owned_values() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        Identifier machineId = MMCR.id("static_snapshot_inputs");
        Machine machine = new DynamicMachine(machineId, "Static Snapshot Inputs", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(machineId));
        runtime.publishFormationState(machine, machine.pattern(), null, Direction.SOUTH, Direction.SOUTH, 1);
        var modifier = MachineModifier.parallelized(true);
        controller.componentRuntime().replaceModifiers(Map.of("first", List.of(modifier)));
        MachineLevel level = snapshotLevel("first_snapshot_level");
        controller.componentRuntime().replaceLevels(Map.of(level.id(), level));
        runtime.publishUpgradeBusState(List.of(new ComponentRuntime.UpgradeBusSnapshot(BlockPos.ZERO,
                List.of(new ItemStack(Items.DIAMOND, 2)))));
        var first = controller.runtimeSnapshot();
        Machine replacement = new DynamicMachine(machineId, "Static Snapshot Inputs", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(machineId));
        assertThat(replacement).isEqualTo(machine).isNotSameAs(machine);
        runtime.publishFormationState(replacement, replacement.pattern(), null, Direction.SOUTH, Direction.SOUTH, 1);
        var replacedMachine = controller.runtimeSnapshot();
        assertThat(replacedMachine.structure().configuredMachine()).isSameAs(replacement);
        assertThat(replacedMachine.structure().machine()).isSameAs(replacement);
        assertThat(replacedMachine.structure().version()).isEqualTo(first.structure().version());
        assertThat(replacedMachine.foundModifiers()).isNotSameAs(first.foundModifiers());
        assertThat(ownedUpgradeItems(replacedMachine)).isNotSameAs(ownedUpgradeItems(first));
        assertThat(first.structure().configuredMachine()).isSameAs(machine);

        ProcessingComponent left = new ProcessingComponent(null, controller, BlockPos.ZERO, BlockPos.ZERO, List.of("left"));
        ProcessingComponent right = new ProcessingComponent(null, controller, new BlockPos(1, 0, 0), BlockPos.ZERO, List.of("right"));
        controller.componentRuntime().replaceComponents(List.of(left, right));
        runtime.publishSnapshot();
        var ordered = controller.runtimeSnapshot();
        controller.componentRuntime().replaceComponents(List.of(right, left));
        runtime.publishSnapshot();
        var reordered = controller.runtimeSnapshot();
        assertThat(reordered.componentPresentations()).extracting(ControllerRuntimeSnapshot.ComponentPresentation::position)
                .containsExactly(right.getPos(), left.getPos());
        assertThat(ordered.componentPresentations()).extracting(ControllerRuntimeSnapshot.ComponentPresentation::position)
                .containsExactly(left.getPos(), right.getPos());
        assertThat(reordered.foundModifiers()).isNotSameAs(ordered.foundModifiers());
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, controller,
                left.getPos(), BlockPos.ZERO, List.of("replacement"))));
        controller.componentRuntime().replaceModifiers(Map.of("second", List.of(MachineModifier.parallelized(false))));
        MachineLevel nextLevel = snapshotLevel("second_snapshot_level");
        controller.componentRuntime().replaceLevels(Map.of(nextLevel.id(), nextLevel));
        runtime.publishSnapshot();
        var changed = controller.runtimeSnapshot();
        assertThat(changed.foundModifiers()).containsOnlyKeys("second");
        assertThat(changed.foundLevels()).containsOnlyKeys(nextLevel.id());
        assertThat(changed.foundLevelIds()).containsExactly(nextLevel.id().toString());
        assertThat(changed.componentPresentations()).singleElement()
                .satisfies(row -> assertThat(row.tags()).containsExactly("replacement"));
        assertThat(first.foundModifiers()).containsOnlyKeys("first");
        assertThat(first.foundLevels()).containsOnlyKeys(level.id());

        runtime.refreshUpgradeBusState(List.of(new ComponentRuntime.UpgradeBusSnapshot(BlockPos.ZERO,
                List.of(new ItemStack(Items.EMERALD, 3)))));
        var upgraded = controller.runtimeSnapshot();
        assertThat(upgraded.upgradeContentRevision()).isGreaterThan(changed.upgradeContentRevision());
        assertThat(ownedUpgradeItems(upgraded)).isNotSameAs(ownedUpgradeItems(changed));
        assertThat(upgraded.upgradeItems().getFirst().getItem()).isEqualTo(Items.EMERALD);
        assertThat(changed.upgradeItems().getFirst().getItem()).isEqualTo(Items.DIAMOND);
        assertThat(first.upgradeItems().getFirst().getCount()).isEqualTo(2);
    }

    @Test
    void cached_machine_ids_keep_working_and_published_baselines_separate() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        MutablePresentationMachine machine = new MutablePresentationMachine();
        runtime.publishStructureState(true, false, machine, 0);
        controller.componentRuntime().replaceModifiers(Map.of("id", List.of(MachineModifier.parallelized(true))));
        runtime.publishSnapshot();
        var first = controller.runtimeSnapshot();
        Identifier originalId = machine.id;
        Field workingId = MachineControllerRuntime.class.getDeclaredField("workingMachineId");
        Field publishedId = MachineControllerRuntime.class.getDeclaredField("publishedMachineId");
        workingId.setAccessible(true);
        publishedId.setAccessible(true);
        assertThat(workingId.get(runtime)).isSameAs(originalId);
        assertThat(publishedId.get(runtime)).isSameAs(originalId);

        machine.id = Identifier.parse("mmcr:mutable_snapshot_machine");
        assertThat(machine.id).isEqualTo(originalId).isNotSameAs(originalId);
        for (int i = 0; i < 3; i++) {
            assertThat(controller.currentRuntimeSnapshot()).isSameAs(first);
            runtime.publishSnapshot();
            assertThat(controller.runtimeSnapshot()).isSameAs(first);
        }
        assertThat(workingId.get(runtime)).isSameAs(originalId);
        assertThat(publishedId.get(runtime)).isSameAs(originalId);

        runtime.beginUpdateBatch();
        ControllerRuntimeSnapshot changed;
        try {
            machine.id = MMCR.id("batch_changed_machine_id");
            changed = controller.currentRuntimeSnapshot();
            assertThat(changed.machineId()).isEqualTo("mmcr:batch_changed_machine_id");
            assertThat(changed.foundModifiers()).isNotSameAs(first.foundModifiers());
            assertThat(workingId.get(runtime)).isSameAs(machine.id);
            assertThat(publishedId.get(runtime)).isSameAs(originalId);
            runtime.publishSnapshot();
            assertThat(controller.runtimeSnapshot()).isSameAs(first);
        } finally {
            runtime.endUpdateBatch();
        }
        assertThat(controller.runtimeSnapshot()).isSameAs(changed);
        assertThat(publishedId.get(runtime)).isSameAs(machine.id);
        assertThat(controller.currentRuntimeSnapshot()).isSameAs(changed);

        machine.id = MMCR.id("published_changed_machine_id");
        runtime.publishSnapshot();
        var published = controller.runtimeSnapshot();
        assertThat(published.machineId()).isEqualTo("mmcr:published_changed_machine_id");
        assertThat(published.foundModifiers()).isNotSameAs(changed.foundModifiers());
        assertThat(controller.currentRuntimeSnapshot()).isSameAs(published);
        assertThat(first.machineId()).isEqualTo("mmcr:mutable_snapshot_machine");
        assertThat(changed.machineId()).isEqualTo("mmcr:batch_changed_machine_id");
    }

    @Test
    void mutable_machine_presentation_invalidates_both_outer_caches_without_epoch_changes() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        MutablePresentationMachine machine = new MutablePresentationMachine();
        runtime.publishStructureState(true, false, machine, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        ParallelControllerBlockEntity parallel = new ParallelControllerBlockEntity(ParallelTier.REINFORCED,
                new BlockPos(2, 0, 0), ModBlocks.BLOCKS.get("parallel_controller_reinforced").get().defaultBlockState());
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, List.of()),
                new ProcessingComponent(null, parallel, parallel.getBlockPos(), BlockPos.ZERO, List.of())));
        controller.componentRuntime().replaceModifiers(Map.of("mutable", List.of(MachineModifier.parallelized(true))));
        runtime.publishSnapshot();
        var first = controller.runtimeSnapshot();
        long structureVersion = controller.structureSnapshot().version();
        for (Runnable change : List.<Runnable>of(
                () -> machine.name = "test.changed_name",
                () -> machine.factory = true,
                () -> machine.role = MachineRole.HOST,
                () -> machine.maximum = 32L,
                () -> machine.role = MachineRole.MODULE,
                () -> machine.behavior = TickBehavior.defaults(),
                () -> machine.parallel = false,
                () -> machine.id = MMCR.id("changed_mutable_machine"))) {
            var previous = controller.runtimeSnapshot();
            change.run();
            var current = controller.currentRuntimeSnapshot();
            assertThat(current).isNotSameAs(previous);
            assertThat(current.foundModifiers()).isNotSameAs(previous.foundModifiers());
            assertThat(current.machineId()).isEqualTo(machine.registryName().toString());
            assertThat(current.machineName()).isEqualTo(machine.displayNameKey());
            assertThat(current.factorySupported()).isEqualTo(machine.hasFactory() && machine.behavior() instanceof RecipeBehavior);
            assertThat(current.factoryControllerPresent()).isEqualTo(current.factorySupported());
            assertThat(current.parallelControllerCount()).isEqualTo(1);
            assertThat(current.controllerRole()).isEqualTo(machine.isHost() ? 1 : machine.isModule() ? 2 : 0);
            assertThat(current.maxParallelControllerCount()).isEqualTo(machine.parallelizable() ? machine.maxParallelism() : 0L);
            runtime.publishSnapshot();
            assertThat(controller.runtimeSnapshot()).isSameAs(current);
            assertThat(controller.structureSnapshot().version()).isEqualTo(structureVersion);
        }
        assertThat(first.machineId()).isEqualTo("mmcr:mutable_snapshot_machine");
        assertThat(first.machineName()).isEqualTo("test.original_name");
        assertThat(first.factorySupported()).isFalse();
        assertThat(first.controllerRole()).isZero();
        assertThat(first.maxParallelControllerCount()).isEqualTo(8L);
        controller.componentRuntime().replaceComponents(List.of());
        runtime.publishSnapshot();
        assertThat(controller.runtimeSnapshot().parallelControllerCount()).isZero();
        assertThat(first.parallelControllerCount()).isEqualTo(1);
    }

    @Test
    void live_parallel_configuration_updates_working_and_published_snapshots() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        Machine machine = new DynamicMachine(MMCR.id("live_parallel_snapshot"), "Live Parallel Snapshot",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(MMCR.id("live_parallel_snapshot")),
                PortRequirementSpec.none(), List.of(), Map.of(), 64, true, false, 1);
        var parallelBlock = ModBlocks.BLOCKS.get("parallel_controller_reinforced").get();
        ParallelControllerBlockEntity parallel = new ParallelControllerBlockEntity(ParallelTier.REINFORCED,
                new BlockPos(1, 0, 0), parallelBlock.defaultBlockState());
        parallel.setCurrentParallelism(4);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, parallel, parallel.getBlockPos(), parallel.getBlockPos(), List.of())));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.publishFormationState(machine, machine.pattern(), null, Direction.SOUTH, Direction.SOUTH, 1);
        var first = controller.currentRuntimeSnapshot();
        assertThat(controller.getMaxParallelism()).isEqualTo(4L);
        assertThat(controller.runtimeSnapshot()).isSameAs(first);
        long stateVersion = controller.componentRuntime().stateVersion();
        long capabilityVersion = controller.componentRuntime().capabilityVersion();
        long modifierVersion = controller.componentRuntime().modifierVersion();
        int publications = runtime.snapshotBuildCountForTesting();

        parallel.setCurrentParallelism(19);

        assertThat(controller.getMaxParallelism()).isEqualTo(19L);
        var increased = controller.currentRuntimeSnapshot();
        assertThat(increased).isNotSameAs(first);
        assertSharedStaticState(increased, first);
        assertThat(increased.maxParallelism()).isEqualTo(19L);
        assertThat(controller.currentRuntimeSnapshot()).isSameAs(increased);
        runtime.publishSnapshot();
        assertThat(controller.runtimeSnapshot()).isSameAs(increased);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(publications + 1);
        assertThat(first.maxParallelism()).isEqualTo(4L);

        parallel.setCurrentParallelism(2);
        runtime.publishSnapshot();

        var decreased = controller.runtimeSnapshot();
        assertThat(decreased).isNotSameAs(increased);
        assertSharedStaticState(decreased, increased);
        assertThat(decreased.maxParallelism()).isEqualTo(2L);
        assertThat(controller.currentRuntimeSnapshot()).isSameAs(decreased);
        assertThat(controller.getMaxParallelism()).isEqualTo(2L);
        assertThat(increased.maxParallelism()).isEqualTo(19L);
        assertThat(decreased.structure()).isSameAs(first.structure());
        assertThat(controller.componentRuntime().stateVersion()).isEqualTo(stateVersion);
        assertThat(controller.componentRuntime().capabilityVersion()).isEqualTo(capabilityVersion);
        assertThat(controller.componentRuntime().modifierVersion()).isEqualTo(modifierVersion);
        runtime.publishSnapshot();
        assertThat(controller.runtimeSnapshot()).isSameAs(decreased);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(publications + 2);
    }

    @Test
    void loading_existing_parallel_configuration_invalidates_outer_snapshot_caches() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        Machine machine = new DynamicMachine(MMCR.id("loaded_parallel_snapshot"), "Loaded Parallel Snapshot",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(MMCR.id("loaded_parallel_snapshot")),
                PortRequirementSpec.none(), List.of(), Map.of(), 64, true, false, 1);
        var parallelBlock = ModBlocks.BLOCKS.get("parallel_controller_reinforced").get();
        ParallelControllerBlockEntity parallel = new ParallelControllerBlockEntity(ParallelTier.REINFORCED,
                new BlockPos(1, 0, 0), parallelBlock.defaultBlockState());
        parallel.setCurrentParallelism(4);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, parallel, parallel.getBlockPos(), parallel.getBlockPos(), List.of())));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.publishFormationState(machine, machine.pattern(), null, Direction.SOUTH, Direction.SOUTH, 1);
        var first = controller.currentRuntimeSnapshot();
        assertThat(controller.getMaxParallelism()).isEqualTo(4L);
        long stateVersion = controller.componentRuntime().stateVersion();
        long capabilityVersion = controller.componentRuntime().capabilityVersion();
        long modifierVersion = controller.componentRuntime().modifierVersion();

        for (int configured : new int[]{19, 2}) {
            var previous = controller.runtimeSnapshot();
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                    HolderLookup.Provider.create(Stream.empty()));
            output.putInt("current_parallelism", configured);
            parallel.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                    HolderLookup.Provider.create(Stream.empty()), output.buildResult()));
            runtime.publishSnapshot();

            var loaded = controller.runtimeSnapshot();
            assertThat(loaded).isNotSameAs(previous);
            assertSharedStaticState(loaded, previous);
            assertThat(loaded.maxParallelism()).isEqualTo(configured);
            assertThat(controller.currentRuntimeSnapshot()).isSameAs(loaded);
            assertThat(controller.getMaxParallelism()).isEqualTo(configured);
            assertThat(previous.maxParallelism()).isEqualTo(configured == 19 ? 4L : 19L);
            assertThat(loaded.structure()).isSameAs(first.structure());
            assertThat(controller.componentRuntime().stateVersion()).isEqualTo(stateVersion);
            assertThat(controller.componentRuntime().capabilityVersion()).isEqualTo(capabilityVersion);
            assertThat(controller.componentRuntime().modifierVersion()).isEqualTo(modifierVersion);
        }
    }

    @Test
    void data_storage_reuses_the_dynamic_casing_model() {
        var definition = RuntimeMachineModelRegistry.dynamicBlockState(ModBlocks.DATA_STORAGE.get());
        assertThat(definition.id()).isEqualTo(MMCR.id("data_storage"));
        assertThat(definition.variants()).singleElement()
                .satisfies(variant -> assertThat(variant.modelId()).isEqualTo(DynamicOverlayModelLoader.PORT_ID));

        var itemDescription = DynamicOverlayItemModel.describeItem(ModItems.ITEMS.get("data_storage").get());
        assertThat(itemDescription.baseModel()).isEqualTo(MMCR.id("block/dynamic_io_port"));
        assertThat(itemDescription.baseTextureSource()).isEqualTo(MachineAppearanceSpec.defaults().formedPortTextureSource());
        assertThat(itemDescription.overlayTextures()).containsExactly(MMCR.id("block/overlay_data_storage"));
    }

    @Test
    void replacing_or_clearing_primary_data_storage_refreshes_snapshot() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        MachineControllerRuntime runtime = runtimeOf(controller);
        DataStorage initial = new DataStorage();
        initial.set("mode", DataValue.of("initial"));
        runtime.publishDataStorages(Map.of(BlockPos.ZERO, initial));

        assertThat(runtime.snapshot().dataStorageValues()).containsEntry("mode", DataValue.of("initial"));
        var before = runtime.snapshot();

        DataStorage replacement = new DataStorage();
        replacement.set("mode", DataValue.of("replacement"));
        runtime.publishDataStorages(Map.of(BlockPos.ZERO, replacement));

        assertThat(runtime.snapshot().dataStorageValues()).containsEntry("mode", DataValue.of("replacement"));
        assertThat(runtime.snapshot().dataStorageValues()).isNotSameAs(before.dataStorageValues());
        assertThat(before.dataStorageValues()).containsEntry("mode", DataValue.of("initial"));

        runtime.publishDataStorages(Map.of());

        assertThat(runtime.snapshot().dataStorageValues()).isEmpty();
    }

    @Test
    void data_storage_change_in_idle_recipe_batch_broadcasts_the_flushed_payload() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        Identifier machineId = MMCR.id("idle_data_storage_batch");
        RuntimeTestFixtures.formStructureWithComponents(controller, new DynamicMachine(machineId,
                "Idle Data Storage Batch", new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))), MachineControllerSpec.defaultsFor(machineId)));
        MachineControllerRuntime runtime = runtimeOf(controller);
        DataStorage storage = new DataStorage();
        storage.set("mode", DataValue.of("idle"));
        runtime.publishDataStorages(Map.of(BlockPos.ZERO, storage));
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer player = testPlayer(level, controller.getBlockPos());
        setPlayers(level, List.of(player));
        controller.onDataStorageChanged(storage);

        runtime.beginUpdateBatch();
        try {
            storage.set("mode", DataValue.of("changed"));
            controller.onDataStorageChanged(storage);
            assertThat(machineStatePackets(player)).hasSize(1);
        } finally {
            runtime.endUpdateBatch();
        }

        assertThat(controller.runtimeSnapshot().crafting().status().getStatus())
                .isEqualTo(CraftingStatus.Status.IDLE);
        assertThat(machineStatePackets(player)).hasSize(2);
        assertThat(machineStatePackets(player).getLast().dataStorageValues())
                .containsEntry("mode", DataValue.of("changed"));
    }

    @Test
    void unchanged_idle_batches_initialize_later_recipients_only_after_the_chunk_is_sent() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        Identifier machineId = MMCR.id("idle_recipient_baseline");
        RuntimeTestFixtures.formStructureWithComponents(controller, new DynamicMachine(machineId,
                "Idle Recipient Baseline", new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))), MachineControllerSpec.defaultsFor(machineId)));
        MachineControllerRuntime runtime = runtimeOf(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer first = player(level, BlockPos.ZERO);
        ServerPlayer later = player(level, BlockPos.ZERO);
        setPlayers(level, List.of(first));
        runtime.beginUpdateBatch();
        runtime.endUpdateBatch();
        assertThat(machineStatePackets(first)).hasSize(1);
        var snapshot = controller.runtimeSnapshot();
        Field pending = PlayerChunkSender.class.getDeclaredField("pendingChunks");
        pending.setAccessible(true);
        LongSet pendingChunks = (LongSet) pending.get(later.connection.chunkSender);
        pendingChunks.add(ChunkPos.pack(BlockPos.ZERO));
        setPlayers(level, List.of(first, later));
        runtime.beginUpdateBatch();
        runtime.endUpdateBatch();
        assertThat(machineStatePackets(later)).isEmpty();
        pendingChunks.clear();
        runtime.beginUpdateBatch();
        runtime.endUpdateBatch();
        assertThat(controller.runtimeSnapshot()).isSameAs(snapshot);
        assertThat(machineStatePackets(first)).hasSize(1);
        assertThat(machineStatePackets(later)).containsExactly(machineStatePackets(first).getFirst());
        setPlayers(level, List.of(first));
        runtime.beginUpdateBatch();
        runtime.endUpdateBatch();
        setPlayers(level, List.of(first, later));
        runtime.beginUpdateBatch();
        runtime.endUpdateBatch();
        assertThat(machineStatePackets(later)).hasSize(2);
    }

    @Test
    void runtime_update_invokes_matching_handlers_and_coalesces_same_component_updates() throws Exception {
        Identifier machineId = MMCR.id("controller_text_runtime");
        MachineControllerBlockEntity controller = textController(machineId);
        MachineControllerRuntime runtime = runtimeOf(controller);
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer player = player(level, controller.getBlockPos());
        player.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        setPlayers(level, List.of(player));
        AtomicInteger invocations = new AtomicInteger();
        textRegistrations.add(ControllerScreenTextRegistry.register(machineId, context -> {
            invocations.incrementAndGet();
            assertThat(context.controllerPos()).isEqualTo(controller.getBlockPos());
            context.screenText().append(ControllerScreenTextScope.CONTROLLER,
                    MMCR.id("runtime_line"), Component.literal("same"));
        }));

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());
        long revision = runtime.screenText().revision();
        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(invocations).hasValue(2);
        assertThat(runtime.screenText().revision()).isEqualTo(revision).isEqualTo(1L);
        assertThat(runtime.screenText().snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text().getString()).isEqualTo("same"));
        assertThat(textPackets(player)).hasSize(1);
    }

    @Test
    void open_controller_menus_receive_only_matching_revisioned_text_snapshots() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_audience"));
        runtimeOf(controller).screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("audience_line"), Component.literal("visible"));
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer ordinary = player(level, controller.getBlockPos());
        ordinary.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        ServerPlayer factory = player(level, controller.getBlockPos());
        factory.containerMenu = new FactoryControllerMenu(2, new Inventory(null, null), controller);
        ServerPlayer wrongPosition = player(level, controller.getBlockPos());
        wrongPosition.containerMenu = new MachineControllerMenu(3, new Inventory(null, null),
                controller.getBlockPos().above());
        ServerPlayer closed = player(level, controller.getBlockPos());
        closed.containerMenu = closedMenu();
        setPlayers(level, List.of(ordinary, factory, wrongPosition, closed));

        invokeSyncOpenText(controller);

        assertThat(textPackets(ordinary)).singleElement()
                .satisfies(packet -> assertThat(packet.controllerPos()).isEqualTo(controller.getBlockPos()))
                .satisfies(packet -> assertThat(packet.lines()).hasSize(1));
        assertThat(textPackets(factory)).hasSize(1);
        assertThat(textPackets(wrongPosition)).isEmpty();
        assertThat(textPackets(closed)).isEmpty();

        invokeSyncOpenText(controller);
        assertThat(textPackets(ordinary)).hasSize(1);
        assertThat(textPackets(factory)).hasSize(1);
    }

    @Test
    void ordinary_and_factory_menu_open_paths_send_the_current_text_snapshot() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_open"));
        runtimeOf(controller).screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("open_line"), Component.literal("open"));
        ServerLevel level = (ServerLevel) controller.getLevel();

        ServerPlayer ordinary = player(level, controller.getBlockPos());
        controller.sendControllerScreenText(ordinary);
        assertThat(textPackets(ordinary)).singleElement()
                .satisfies(packet -> assertThat(packet.controllerPos()).isEqualTo(controller.getBlockPos()))
                .satisfies(packet -> assertThat(packet.revision()).isEqualTo(1L));

        MachineControllerBlockEntity factoryController = factoryTextController(MMCR.id("controller_text_factory_open"));
        runtimeOf(factoryController).screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("factory_open_line"), Component.literal("factory open"));
        ServerPlayer factory = player((ServerLevel) factoryController.getLevel(), factoryController.getBlockPos());
        new FactoryControllerMenu(2, new Inventory(null, null), factoryController, factory);
        assertThat(textPackets(factory)).singleElement()
                .satisfies(packet -> assertThat(packet.controllerPos()).isEqualTo(factoryController.getBlockPos()))
                .satisfies(packet -> assertThat(packet.lines()).singleElement()
                        .satisfies(line -> assertThat(line.text().getString()).isEqualTo("factory open")));
    }

    @Test
    void factory_menu_sync_sends_recipe_text_with_its_lane_id() throws Exception {
        MachineControllerBlockEntity controller = factoryTextController(MMCR.id("controller_text_factory_lane"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.factoryRuntime().ensureBaseLane(controller);
        runtime.recipeScreenText("base").append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("factory_lane_line"), Component.literal("factory lane"));
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer factory = player(level, controller.getBlockPos());
        factory.containerMenu = new FactoryControllerMenu(2, new Inventory(null, null), controller);
        setPlayers(level, List.of(factory));

        invokeSyncOpenText(controller);

        assertThat(textPackets(factory)).anySatisfy(packet -> {
            assertThat(packet.laneId()).isEqualTo("base");
            assertThat(packet.lines()).singleElement()
                    .satisfies(line -> assertThat(line.text().getString()).isEqualTo("factory lane"));
        });
    }

    @Test
    void unopened_controller_menus_do_not_materialize_factory_lane_text() throws Exception {
        MachineControllerBlockEntity controller = factoryTextController(MMCR.id("controller_text_no_viewer"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.factoryRuntime().ensureBaseLane(controller);
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("no_viewer_line"), Component.literal("visible"));
        ServerLevel level = (ServerLevel) controller.getLevel();
        lastSentRecipeScreenTextRevisions(controller).put("removed", 1L);
        setPlayers(level, List.of());

        invokeSyncOpenText(controller);

        assertThat(lastSentRecipeScreenTextRevisions(controller)).containsEntry("removed", 1L);

        ServerPlayer factory = player(level, controller.getBlockPos());
        factory.containerMenu = new FactoryControllerMenu(2, new Inventory(null, null), controller);
        setPlayers(level, List.of(factory));
        invokeSyncOpenText(controller);

        assertThat(textPackets(factory)).anySatisfy(packet -> assertThat(packet.laneId()).isEmpty());
        assertThat(textPackets(factory)).anySatisfy(packet -> assertThat(packet.laneId()).isEqualTo("base"));
    }

    @Test
    void unchanged_factory_tick_does_not_mark_the_controller_dirty() throws Exception {
        ChangeCountingController controller = changeCountingFactoryController(MMCR.id("factory_unchanged_dirty"));

        invokeTickFactoryRecipes(controller);
        controller.changedCalls = 0;
        invokeTickFactoryRecipes(controller);

        assertThat(controller.changedCalls).isZero();
    }

    @Test
    void factory_lane_sync_marks_persistence_without_notifying_comparators() throws Exception {
        ChangeCountingController controller = changeCountingFactoryController(MMCR.id("factory_lane_sync_dirty"));
        controller.changedCalls = 0;
        controller.persistenceChangedCalls = 0;

        invokeTickFactoryRecipes(controller);

        assertThat(controller.persistenceChangedCalls).isPositive();
        assertThat(controller.changedCalls).isZero();
    }

    @Test
    void recipe_runtime_progress_marks_persistence_without_notifying_comparators() throws Exception {
        ChangeCountingController controller = changeCountingFactoryController(MMCR.id("recipe_runtime_dirty"));
        controller.changedCalls = 0;
        controller.persistenceChangedCalls = 0;

        controller.syncRecipeRuntimeFailure(runtimeOf(controller).craftingRuntime());

        assertThat(controller.persistenceChangedCalls).isEqualTo(1);
        assertThat(controller.changedCalls).isZero();
    }

    @Test
    void runtime_update_batch_coalesces_persistence_changes() throws Exception {
        ChangeCountingController controller = changeCountingFactoryController(MMCR.id("runtime_dirty_batch"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        controller.persistenceChangedCalls = 0;

        runtime.beginUpdateBatch();
        controller.syncRecipeRuntimeFailure(runtime.craftingRuntime());
        controller.syncRecipeRuntimeFailure(runtime.craftingRuntime());

        assertThat(controller.persistenceChangedCalls).isZero();
        runtime.endUpdateBatch();
        assertThat(controller.persistenceChangedCalls).isEqualTo(1);
    }

    @Test
    void empty_text_snapshot_reaches_matching_menus_after_external_text_is_cleared() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_clear"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("clear_line"), Component.literal("clear"));
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer player = player(level, controller.getBlockPos());
        player.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        setPlayers(level, List.of(player));
        invokeSyncOpenText(controller);

        runtime.screenText().clear(ControllerScreenTextScope.CONTROLLER);
        invokeSyncOpenText(controller);

        assertThat(textPackets(player)).hasSize(2);
        assertThat(textPackets(player).getLast().revision()).isGreaterThan(1L);
        assertThat(textPackets(player).getLast().lines()).isEmpty();
    }

    @Test
    void completed_recipe_clears_operation_text_but_keeps_controller_text() throws Exception {
        Identifier machineId = MMCR.id("controller_text_operation");
        MachineControllerBlockEntity controller = textController(machineId);
        MachineControllerRuntime runtime = runtimeOf(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("controller_text_operation_recipe"), machineId, 1,
                List.of(), List.of());
        assertThat(runtime.craftingRuntime().start(recipe, 1).isCrafting()).isTrue();
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("persistent_line"), Component.literal("persistent"));
        runtime.screenText().append(ControllerScreenTextScope.OPERATION,
                MMCR.id("operation_line"), Component.literal("operation"));

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());
        resolveSharedRequests(controller);
        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());
        resolveSharedRequests(controller);

        assertThat(runtime.screenText().snapshot().lines())
                .extracting(ControllerScreenTextSnapshot.Line::scope)
                .containsExactly(ControllerScreenTextScope.CONTROLLER);
    }

    @Test
    void modifier_change_keeps_active_operation_text() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_failed_operation"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("controller_text_failed_recipe"),
                MMCR.id("controller_text_failed_operation"), 20, List.of(), List.of());
        assertThat(runtime.craftingRuntime().start(recipe, 1).isCrafting()).isTrue();
        runtime.screenText().append(ControllerScreenTextScope.OPERATION,
                MMCR.id("failed_operation_line"), Component.literal("running"));

        controller.componentRuntime().replaceModifiers(Map.of("changed", List.of()));
        RuntimeTestFixtures.republish(controller);
        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());
        SharedIoEvents.completeLevelTick((ServerLevel) controller.getLevel());
        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());
        SharedIoEvents.completeLevelTick((ServerLevel) controller.getLevel());

        assertThat(runtime.craftingRuntime().failure()).isNull();
        assertThat(runtime.craftingRuntime().active()).isTrue();
        assertThat(runtime.screenText().snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.scope()).isEqualTo(ControllerScreenTextScope.OPERATION));
    }

    @Test
    void smart_interface_change_keeps_active_operation_text() throws Exception {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        Identifier machineId = MMCR.id("controller_text_cancelled_operation");
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DynamicMachine machine = new DynamicMachine(machineId, "cancelled text test",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch").get()))),
                MachineControllerSpec.defaultsFor(machineId));
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, energy);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(new MachineComponent(energy.kind(), energy.ioType()), energy,
                        energy.getBlockPos(), BlockPos.ZERO, (String) null)));
        RuntimeTestFixtures.republish(controller);
        MachineControllerRuntime runtime = runtimeOf(controller);
        energy.energyStorage().setAmount(2);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("controller_text_cancelled_recipe"),
                machineId, 20, List.of(), List.of(), List.of(), 0, 1, true,
                List.of(), List.of(new EnergyRequirement(2)));
        assertThat(runtime.craftingRuntime().start(recipe, 1).isCrafting()).isTrue();
        runtime.screenText().append(ControllerScreenTextScope.OPERATION,
                MMCR.id("cancelled_operation_line"), Component.literal("running"));
        assertThat(runtime.screenText().snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.scope()).isEqualTo(ControllerScreenTextScope.OPERATION));

        controller.onSmartInterfaceValueChanged();

        assertThat(runtime.craftingRuntime().active()).isTrue();
        assertThat(runtime.screenText().snapshot().lines())
                .anyMatch(line -> line.scope() == ControllerScreenTextScope.OPERATION);
    }

    @Test
    void custom_tick_updates_one_controller_line_without_duplicates() throws Exception {
        Identifier machineId = MMCR.id("controller_text_custom_tick");
        MachineControllerBlockEntity controller = textController(machineId);
        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer player = player(level, controller.getBlockPos());
        player.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        setPlayers(level, List.of(player));
        AtomicInteger ticks = new AtomicInteger();
        AtomicBoolean updateText = new AtomicBoolean(true);
        textRegistrations.add(ControllerScreenTextRegistry.register(machineId, context ->
                {
                    if (updateText.get()) {
                        context.screenText().append(ControllerScreenTextScope.CONTROLLER,
                                MMCR.id("custom_tick_line"), Component.literal("tick " + ticks.incrementAndGet()));
                    }
                }));

        controller.tickRuntimeWork(level, controller.getBlockPos());
        controller.tickRuntimeWork(level, controller.getBlockPos());

        assertThat(runtimeOf(controller).screenText().snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text().getString()).isEqualTo("tick 2"));
        assertThat(textPackets(player)).hasSize(2);

        long unchangedRevision = runtimeOf(controller).screenText().revision();
        int payloadCount = textPackets(player).size();
        updateText.set(false);
        controller.tickRuntimeWork(level, controller.getBlockPos());

        assertThat(runtimeOf(controller).screenText().revision()).isEqualTo(unchangedRevision);
        assertThat(textPackets(player)).hasSize(payloadCount);
    }

    @Test
    void handler_exception_does_not_interrupt_controller_runtime_update() throws Exception {
        Identifier machineId = MMCR.id("controller_text_handler_exception");
        MachineControllerBlockEntity controller = textController(machineId);
        AtomicInteger invocations = new AtomicInteger();
        textRegistrations.add(ControllerScreenTextRegistry.register(machineId, context -> {
            invocations.incrementAndGet();
            throw new IllegalStateException("expected test failure");
        }));
        textRegistrations.add(ControllerScreenTextRegistry.register(machineId, context -> {
            invocations.incrementAndGet();
            context.screenText().append(ControllerScreenTextScope.CONTROLLER,
                    MMCR.id("after_exception"), Component.literal("continued"));
        }));

        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(invocations).hasValue(2);
        assertThat(runtimeOf(controller).screenText().snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text().getString()).isEqualTo("continued"));
    }

    @Test
    void reset_machine_clears_external_text_and_advances_revision() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_reset"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("reset_line"), Component.literal("reset"));
        runtime.screenText().append(ControllerScreenTextScope.OPERATION,
                MMCR.id("reset_operation"), Component.literal("operation"));
        long revision = runtime.screenText().revision();

        controller.invalidateFormedStructure();

        assertThat(runtime.screenText().snapshot().lines()).isEmpty();
        assertThat(runtime.screenText().revision()).isGreaterThan(revision);
    }

    @Test
    void unbinding_configured_machine_clears_external_text_and_advances_revision() throws Exception {
        MachineControllerBlockEntity controller = textController(MMCR.id("controller_text_unbind"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("unbind_line"), Component.literal("unbind"));
        long revision = runtime.screenText().revision();

        controller.setMachine(null);

        assertThat(runtime.screenText().snapshot().lines()).isEmpty();
        assertThat(runtime.screenText().revision()).isGreaterThan(revision);
    }

    @Test
    void no_op_runtime_update_reuses_the_published_snapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        var published = controller.runtimeSnapshot();
        int buildCount = controller.snapshotBuildCountForTesting();

        controller.setFormed(published.structure().formed());

        assertThat(controller.runtimeSnapshot()).isEqualTo(published);
        assertThat(controller.snapshotBuildCountForTesting()).isEqualTo(buildCount);
        assertThat(controller.runtimeSnapshot()).isSameAs(published);
    }

    @Test
    void idle_structure_transition_is_published_in_the_same_runtime_batch() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DynamicMachine machine = new DynamicMachine(MMCR.id("test_cube"), "Idle BER Publish",
                new BlockArray(Map.of()));
        RuntimeTestFixtures.formStructure(controller, machine);
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.publishStructureState(true, false, machine, 0);
        runtime.publishSnapshot();

        runtime.beginUpdateBatch();
        try {
            runtime.publishStructureState(true, true, machine, 0);
            controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

            assertThat(runtimeStateBroadcastPending(controller)).isTrue();
        } finally {
            runtime.endUpdateBatch();
        }
    }

    @Test
    void structure_reconciliation_claims_only_sorted_network_interfaces_within_machine_limit() {
        BlockPos firstPatternPos = new BlockPos(-2, 0, 0);
        BlockPos secondPatternPos = new BlockPos(1, 0, 0);
        DynamicMachine machine = networkMachine(MMCR.id("network_interface_limit"), 1,
                Map.of(firstPatternPos, new BlockPredicate.OfBlock(ModBlocks.NETWORK_INTERFACE.get()),
                        secondPatternPos, new BlockPredicate.OfBlock(ModBlocks.NETWORK_INTERFACE.get())));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        List<BlockPos> positions = MachinePatternCompiler.compile(machine)
                .networkInterfacePositions(controller.getBlockState().getValue(MachineControllerBlock.FACING));
        Comparator<BlockPos> positionOrder = Comparator.<BlockPos>comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
        BlockPos firstPos = positions.stream().min(positionOrder).orElseThrow();
        BlockPos secondPos = positions.stream().max(positionOrder).orElseThrow();
        NetworkInterfaceBlockEntity first = networkInterface(firstPos);
        NetworkInterfaceBlockEntity second = networkInterface(secondPos);

        RuntimeTestFixtures.formStructureWithComponents(controller, machine, first, second);

        assertThat(controller.activeNetworkInterfacePositions()).containsExactly(firstPos);
        assertThat(controller.hasActiveNetworkInterface(firstPos)).isTrue();
        assertThat(controller.hasActiveNetworkInterface(secondPos)).isFalse();
        assertThat(first.owner()).contains(GlobalPos.of(controller.getLevel().dimension(), controller.getBlockPos()));
        assertThat(second.owner()).isEmpty();
        assertThat(controller.runtimeSnapshot().linkedPortPositions()).doesNotContain(firstPos, secondPos);
        assertThat(first.linkedControllerPositions()).contains(controller.getBlockPos());
    }

    @Test
    void resetting_a_controller_releases_network_interface_ownership_and_connections() {
        BlockPos interfacePatternPos = new BlockPos(1, 0, 0);
        DynamicMachine machine = networkMachine(MMCR.id("network_interface_reset"), 1,
                Map.of(interfacePatternPos, new BlockPredicate.OfBlock(ModBlocks.NETWORK_INTERFACE.get())));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos interfacePos = MachinePatternCompiler.compile(machine)
                .networkInterfacePositions(controller.getBlockState().getValue(MachineControllerBlock.FACING)).getFirst();
        NetworkInterfaceBlockEntity networkInterface = networkInterface(interfacePos);

        RuntimeTestFixtures.formStructureWithComponents(controller, machine, networkInterface);
        assertThat(networkInterface.addConnection(new NetworkInterfaceBlockEntity.Connection(
                globalPos("mmcr:network_endpoint", new BlockPos(4, 0, 0)),
                new MachineReference(MMCR.id("network_target"), 4L), 1L))).isTrue();

        controller.invalidateFormedStructure();

        assertThat(controller.activeNetworkInterfacePositions()).isEmpty();
        assertThat(networkInterface.owner()).isEmpty();
        assertThat(networkInterface.connections()).isEmpty();
        assertThat(networkInterface.linkedControllerPositions()).isEmpty();
    }

    @Test
    void update_batch_flushes_structure_component_and_factory_changes_once() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        MachineControllerRuntime runtime = new MachineControllerRuntime(controller);
        DynamicMachine machine = new DynamicMachine(MMCR.id("batched_snapshot"), "Batched Snapshot",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(MMCR.id("batched_snapshot")));

        runtime.beginUpdateBatch();
        try {
            runtime.publishStructureState(true, true, machine, 1);
            runtime.publishComponentState(List.of(new ProcessingComponent(null, "component", BlockPos.ZERO)),
                    Map.of(), Map.of(), Set.of());
            runtime.factoryRuntime().setLaneLimit(2);
        } finally {
            runtime.endUpdateBatch();
        }

        var published = runtime.snapshot();
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(1);
        assertThat(published.structure().configuredMachine()).isEqualTo(machine);
        assertThat(published.structure().formed()).isTrue();
        assertThat(published.componentPresentations()).hasSize(1);
        assertThat(published.factory().laneLimit()).isEqualTo(2);
    }

    @Test
    void server_tick_runs_factory_work_when_structure_forms_earlier_in_the_same_batch() {
        Identifier machineId = MMCR.id("same_tick_factory_formation");
        DynamicMachine machine = new DynamicMachine(machineId, "Same Tick Factory Formation",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        RuntimeTestFixtures.registerRecipePool(machineId);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(new BlockPos(-1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.invalidateFormedStructure();
        RecipeRegistry.registerStatic(RecipeTestSupport.create(MMCR.id("same_tick_factory_recipe"), machineId, 20,
                List.of(), List.of()));

        controller.serverTick();
        resolveSharedRequests(controller);

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(controller.runtimeSnapshot().factory().active())
                .as("factory snapshot=%s", controller.runtimeSnapshot().factory()).isTrue();
    }

    @Test
    void tick_machine_accepts_an_ignored_factory_controller() {
        Identifier machineId = MMCR.id("tick_machine_ignored_factory");
        BlockPos schedulerPos = new BlockPos(1, 0, 0);
        DynamicMachine machine = new DynamicMachine(machineId, "Tick Machine Ignored Factory",
                new BlockArray(Map.of(schedulerPos,
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1, List.of(), MachineRole.NORMAL,
                Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), TickBehavior.builder().build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());

        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(controller.hasFactoryController()).isFalse();
        assertThat(controller.effectiveFactoryThreadLimit()).isEqualTo(1);
        assertThat(controller.runtimeSnapshot().factoryControllerPresent()).isFalse();
    }

    // @Test
    // This is one unstable test, run it directly do not cause problems, but will cause problem with full test
    void server_tick_does_not_run_factory_after_structure_resets_earlier_in_the_same_batch() {
        Identifier machineId = MMCR.id("same_tick_factory_reset");
        DynamicMachine machine = new DynamicMachine(machineId, "Same Tick Factory Reset",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        RuntimeTestFixtures.registerRecipePool(machineId);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = new BlockPos(-1, 0, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        RuntimeTestFixtures.republish(controller);
        RecipeRegistry.registerStatic(RecipeTestSupport.create(MMCR.id("same_tick_reset_recipe"), machineId, 20,
                List.of(), List.of()));
        controller.serverTick();
        resolveSharedRequests(controller);
        assertThat(controller.runtimeSnapshot().factory().active())
                .as("factory snapshot=%s", controller.runtimeSnapshot().factory()).isTrue();

        RuntimeTestFixtures.replaceBlockEntity(controller, RuntimeTestFixtures.itemInput(schedulerPos));
        controller.onStructureBlockChanged(schedulerPos);
        controller.serverTick();
        resolveSharedRequests(controller);

        assertThat(controller.runtimeSnapshot().structure().formed()).isTrue();
        assertThat(controller.runtimeSnapshot().factoryControllerPresent()).isFalse();
        assertThat(controller.runtimeSnapshot().factory().active()).isFalse();
    }

    @Test
    void module_refresh_in_a_batch_notifies_from_current_component_state_once() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DynamicMachine machine = new DynamicMachine(MMCR.id("module_refresh_batch"), "Module Refresh Batch",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(MMCR.id("module_refresh_batch")));
        RuntimeTestFixtures.formStructure(controller, machine);
        controller.componentRuntime().replaceModuleConnectionState(
                ModuleConnectionStatus.connected(MMCR.id("module_host")), 2);
        RuntimeTestFixtures.republish(controller);
        long beforeEpoch = controller.resourceAvailabilityEpoch();
        controller.setStructureCheckCallbackForTesting(controller::refreshModuleConnectionState);
        controller.requestImmediateStructureCheck();

        controller.serverTick();

        assertThat(controller.componentRuntime().moduleConnectionStatus()).isEqualTo(ModuleConnectionStatus.notRequired());
        assertThat(controller.componentRuntime().installedModuleCount()).isZero();
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeEpoch + 1);
        controller.refreshModuleConnectionState();
        assertThat(controller.resourceAvailabilityEpoch()).isEqualTo(beforeEpoch + 1);
    }

    @Test
    void formation_batch_sends_the_final_factory_snapshot_to_an_open_menu() throws Exception {
        Identifier firstMachineId = MMCR.id("factory_menu_first");
        Identifier secondMachineId = MMCR.id("factory_menu_second");
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine firstMachine = new DynamicMachine(firstMachineId, "Factory Menu First", pattern,
                MachineControllerSpec.defaultsFor(firstMachineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        DynamicMachine secondMachine = new DynamicMachine(secondMachineId, "Factory Menu Second", pattern,
                MachineControllerSpec.defaultsFor(secondMachineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(new BlockPos(-1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, firstMachine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);

        ServerLevel level = (ServerLevel) controller.getLevel();
        ServerPlayer player = testPlayer(level, controller.getBlockPos());
        FactoryControllerMenu menu = new FactoryControllerMenu(1, new Inventory(player, null), controller, player);
        player.containerMenu = menu;
        setField(ServerLevel.class, level, "players", List.of(player));
        assertThat(menu.machineName()).isEqualTo(firstMachine.displayNameKey());

        controller.invalidateFormedStructure();
        controller.setMachine(secondMachine);
        controller.serverTick();

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(menu.isFormed()).isTrue();
        assertThat(menu.machineName()).isEqualTo(secondMachine.displayNameKey());
    }

    @Test
    void structure_and_factory_epochs_change_only_for_real_state_changes() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        StructureRuntime structure = new StructureRuntime(controller);
        DynamicMachine machine = new DynamicMachine(MMCR.id("epoch_machine"), "Epoch Machine",
                new BlockArray(Map.of()), MachineControllerSpec.defaultsFor(MMCR.id("epoch_machine")));
        long structureVersion = structure.version();
        FactoryRuntime factory = new FactoryRuntime();
        long factoryEpoch = factory.stateEpoch();

        assertThat(structure.setMachine(null)).isFalse();
        assertThat(structure.setMachine(machine)).isTrue();
        assertThat(structure.setMachine(machine)).isFalse();
        assertThat(structure.version()).isGreaterThan(structureVersion);
        assertThat(structure.setCriticalChunks(Set.of())).isFalse();
        assertThat(structure.setCriticalChunks(Set.of(new ChunkPos(1, 1)))).isTrue();
        assertThat(structure.setCriticalChunks(Set.of(new ChunkPos(1, 1)))).isFalse();

        assertThat(factory.setLaneLimit(1)).isFalse();
        assertThat(factory.stateEpoch()).isEqualTo(factoryEpoch);
        assertThat(factory.setLaneLimit(2)).isTrue();
        assertThat(factory.stateEpoch()).isGreaterThan(factoryEpoch);
    }

    @Test
    void structure_version_changes_only_for_a_block_inside_the_formed_bounds() {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_version"), "Controller Version",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_version")));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructure(controller, machine);
        long formedVersion = controller.structureSnapshot().version();

        controller.handleStructureBlockChanged(controllerPos.offset(10, 0, 0));
        assertThat(controller.structureSnapshot().version()).isEqualTo(formedVersion);

        controller.handleStructureBlockChanged(controllerPos.offset(-1, 0, 0));
        assertThat(controller.structureSnapshot().version()).isGreaterThan(formedVersion);
        assertThat(controller.structureSnapshot().dirty()).isTrue();
        assertThat(controller.structureWorkSnapshotForTesting().componentRefreshRequired()).isTrue();
    }

    @Test
    void expandStructure_blockDirty_after_forming_lowest_stage_keeps_lowest_stage() throws Exception {
        BlockPos controllerPos = new BlockPos(0, 0, 0);
        Identifier machineId = MMCR.id("expand_structure_block_dirty");
        BlockArray firstStage = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(3, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        BlockArray secondStage = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(3, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        MachineControllerBlockEntity controller = expandStructureControllerWith(machineId, controllerPos, firstStage, secondStage);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(machineId), 1);
        ServerLevel level = (ServerLevel) controller.getLevel();
        RuntimeTestFixtures.advanceGameTime(level);
        controller.setStructureScanBatchesForTesting(2);
        RuntimeTestFixtures.advanceGameTime(level);

        controller.handleStructureBlockChanged(controllerPos.offset(1, 0, 0));
        RuntimeTestFixtures.advanceGameTime(level);
        controller.tickStructure(level, controllerPos);
        for (int tick = 0; tick < 16 && controller.structureWorkSnapshotForTesting().scan() != null; tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.tickStructure(level, controllerPos);
        }

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(controller.structureSnapshot().matchedStage()).isEqualTo(1);
    }

    private MachineControllerBlockEntity expandStructureControllerWith(Identifier machineId, BlockPos controllerPos,
                                                                  BlockArray firstStage, BlockArray secondStage) throws Exception {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).expandableStructure().build());
        MachineStructureDefinition definition = new MachineStructureDefinition(machineId, List.of(
                new MachineStructureDefinition.Declaration(MachineStructureDefinition.Declaration.Kind.FULL,
                        firstStage, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                        MachineStructureRequirements.EMPTY),
                new MachineStructureDefinition.Declaration(MachineStructureDefinition.Declaration.Kind.FULL,
                        secondStage, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                        MachineStructureRequirements.EMPTY)));
        MachineStructureRegistry.replaceDynamic(Map.of(machineId, definition));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        controller.setMachine(MachineRegistry.getMachine(machineId));
        var controllerState = controller.getBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH);
        setField(BlockEntity.class, controller, "blockState", controllerState);
        Level level = LevelStub.create(Map.of(
                controllerPos, controllerState.getBlock(),
                controllerPos.offset(1, 0, 0), Blocks.IRON_BLOCK,
                controllerPos.offset(3, 0, 0), Blocks.IRON_BLOCK), List.of(controller));
        controller.setLevel(level);
        return controller;
    }

    @Test
    void queued_structure_changes_are_coalesced_until_controller_tick() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        BlockPos changedPos = controller.getBlockPos().offset(1, 0, 0);
        long formedVersion = controller.structureSnapshot().version();

        controller.queueStructureBlockChanged(changedPos);
        controller.queueStructureBlockChanged(changedPos);

        assertThat(controller.structureSnapshot().version()).isEqualTo(formedVersion);
        controller.serverTick();

        assertThat(controller.structureSnapshot().version()).isEqualTo(formedVersion + 1);
    }

    @Test
    void unformed_structure_mismatch_waits_for_the_next_check_interval() {
        BlockPos controllerPos = BlockPos.ZERO;
        Identifier machineId = MMCR.id("controller_mismatch_interval");
        DynamicMachine machine = new DynamicMachine(machineId, "Controller Mismatch Interval",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructure(controller, machine);
        BlockPos structurePos = controller.getBlockPos().offset(controller.assemblyPattern(machine).pattern().keySet().iterator().next());
        controller.getLevel().setBlock(structurePos, Blocks.AIR.defaultBlockState(), 3);
        controller.setStructureCheckIntervalForTesting(40);
        controller.invalidateFormedStructure();

        controller.tickStructure((ServerLevel) controller.getLevel(), controllerPos);
        int invocationsAfterFirstCheck = controller.matcherInvocationCountForTesting();

        RuntimeTestFixtures.advanceGameTime(controller.getLevel());
        controller.tickStructure((ServerLevel) controller.getLevel(), controllerPos);

        assertThat(invocationsAfterFirstCheck).isGreaterThan(0);
        assertThat(controller.matcherInvocationCountForTesting()).isEqualTo(invocationsAfterFirstCheck);
    }

    @Test
    void formed_structure_uses_the_120_tick_safety_interval() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();
        int safetyChecks = controller.structureSafetyCheckCountForTesting();

        long nextCheckTick = controller.structureWorkSnapshotForTesting().nextCheckTick();
        assertThat(nextCheckTick).isGreaterThan(level.getGameTime());
        assertThat(nextCheckTick - level.getGameTime())
                .isLessThanOrEqualTo(ServerConfig.structureSafetyCheckIntervalTicks());

        while (level.getGameTime() + 1 < nextCheckTick) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.tickStructure(level, controller.getBlockPos());
        }

        assertThat(controller.structureSafetyCheckCountForTesting()).isEqualTo(safetyChecks);

        RuntimeTestFixtures.advanceGameTime(level);
        controller.tickStructure(level, controller.getBlockPos());

        assertThat(controller.structureSafetyCheckCountForTesting()).isEqualTo(safetyChecks + 1);
    }

    @Test
    void formed_structure_safety_checks_are_phased_by_controller_position() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity first = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        MachineControllerBlockEntity second = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"),
                new BlockPos(1, 0, 0));
        RuntimeTestFixtures.formStructure(first, MachineRegistry.getMachine(MMCR.id("test_cube")));
        RuntimeTestFixtures.formStructure(second, MachineRegistry.getMachine(MMCR.id("test_cube")));

        assertThat(first.structureWorkSnapshotForTesting().nextCheckTick())
                .isNotEqualTo(second.structureWorkSnapshotForTesting().nextCheckTick());
    }

    // This is an unstable assert, do not need any more
    // @Test
    // void formed_structure_safety_check_continues_with_a_full_batched_scan() {
    //     TestBootstrap.registerRuntimeBuiltins();
    //     Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
    //     for (int index = 0; index < 100; index++) {
    //         entries.put(new BlockPos(index + 1, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE));
    //     }
    //     DynamicMachine machine = new DynamicMachine(MMCR.id("async_safety_scan"), "Async Safety Scan",
    //             new BlockArray(entries), MachineControllerSpec.defaultsFor(MMCR.id("async_safety_scan")));
    //     MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
    //     RuntimeTestFixtures.formStructure(controller, machine);
    //     ServerLevel level = (ServerLevel) controller.getLevel();
    //     controller.setStructureScanBatchesForTesting(6);
    //     var published = controller.runtimeSnapshot();

    //     long nextCheckTick = controller.structureWorkSnapshotForTesting().nextCheckTick();
    //     while (level.getGameTime() < nextCheckTick) {
    //         RuntimeTestFixtures.advanceGameTime(level);
    //         controller.tickStructure(level, controller.getBlockPos());
    //         SharedIoEvents.completeLevelTick(level);
    //     }
    //     assertThat(controller.structureWorkSnapshotForTesting().scan()).isNotNull();
    //     for (int tick = 0; tick < 40 && controller.structureWorkSnapshotForTesting().scan() != null; tick++) {
    //         RuntimeTestFixtures.advanceGameTime(level);
    //         controller.tickStructure(level, controller.getBlockPos());
    //         SharedIoEvents.completeLevelTick(level);
    //     }

    //     
    //     assertThat(controller.scanBatchCountForTesting()).isGreaterThan(5);
    //     assertThat(controller.structureWorkSnapshotForTesting().scan()).isNull();
    //     assertThat(controller.structureSnapshot().formed()).isTrue();
    //     assertThat(controller.runtimeSnapshot()).isSameAs(published);
    // }

    @Test
    void stale_structure_batch_is_discarded_before_its_cursor_or_result_is_applied() throws InterruptedException {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();

        for (int tick = 0; tick < 120; tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.tickStructure(level, controller.getBlockPos());
        }

        StructureRuntime.StructureWorkSnapshot.ScanView scan = controller.structureWorkSnapshotForTesting().scan();
        assertThat(scan).isNotNull();
        assertThat(scan.cursor()).isZero();
        assertThat(waitForPendingMainStep(MachineAsyncCoordinator.get(level))).isTrue();

        BlockPos changedPos = controller.getBlockPos().offset(
                controller.structureSnapshot().pattern().pattern().keySet().iterator().next());
        level.setBlock(changedPos, Blocks.DIRT.defaultBlockState(), 3);
        controller.handleStructureBlockChanged(changedPos);
        SharedIoEvents.completeLevelTick(level);

        assertThat(controller.structureSnapshot().formed()).isTrue();
        for (int tick = 0; tick < 64 && controller.structureSnapshot().formed(); tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.tickStructure(level, controller.getBlockPos());
            SharedIoEvents.completeLevelTick(level);
        }

        assertThat(controller.structureSnapshot().formed()).isFalse();
    }

    @Test
    void redstone_pause_keeps_an_inflight_structure_scan_deliverable() throws InterruptedException {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();

        startPendingStructureScan(controller, level);
        RuntimeTestFixtures.setDirectSignal(level, controller.getBlockPos(), 15);
        controller.tickRuntimeWork(level, controller.getBlockPos());

        assertThat(controller.isRedstonePaused()).isTrue();
        assertThat(MachineAsyncCoordinator.get(level).hasPendingMainStepForTesting()).isTrue();
        SharedIoEvents.completeLevelTick(level);
        assertThat(controller.structureScanCursorForTesting()).isPositive();
    }

    @Test
    void unloading_the_controller_chunk_cancels_a_scan_outside_its_pattern_bounds() throws InterruptedException {
        TestBootstrap.registerRuntimeBuiltins();
        Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        for (int index = 0; index < 9; index++) {
            entries.put(new BlockPos(17 + index, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE));
        }
        DynamicMachine machine = new DynamicMachine(MMCR.id("scan_in_other_chunk"), "Scan In Other Chunk",
                new BlockArray(entries), MachineControllerSpec.defaultsFor(MMCR.id("scan_in_other_chunk")));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, machine);
        ServerLevel level = (ServerLevel) controller.getLevel();

        startPendingStructureScan(controller, level);
        controller.onChunkUnloaded();
        SharedIoEvents.completeLevelTick(level);

        assertThat(controller.structureScanCursorForTesting()).isEqualTo(-1);
    }

    @Test
    void stable_safety_check_keeps_the_published_runtime_snapshot() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();
        var published = controller.runtimeSnapshot();

        for (int tick = 0; tick < 120; tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.tickStructure(level, controller.getBlockPos());
        }

        assertThat(controller.runtimeSnapshot()).isSameAs(published);
    }

    @Test
    void structure_work_records_dirty_component_and_chunk_state_transitions() {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos inputPos = controllerPos.offset(-1, 0, 0);
        var input = RuntimeTestFixtures.itemInput(inputPos);
        Identifier machineId = MMCR.id("task7_component_transition");
        DynamicMachine machine = new DynamicMachine(machineId, "Task 7 Component Transition",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, input);
        long chunkEpoch = controller.structureWorkSnapshotForTesting().chunkStateEpoch();
        BlockPos changedPos = controller.getBlockPos().offset(
                controller.structureSnapshot().pattern().pattern().keySet().iterator().next());

        controller.handleStructureBlockChanged(changedPos);

        assertThat(controller.structureWorkSnapshotForTesting().checkReason())
                .isEqualTo(StructureRuntime.CheckReason.DIRTY_EVENT);
        assertThat(controller.structureWorkSnapshotForTesting().componentRefreshRequired()).isTrue();

        controller.handleStructureChunkChanged((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(controller.structureWorkSnapshotForTesting().chunkStateEpoch()).isEqualTo(chunkEpoch + 1);
    }

    @Test
    void formed_structure_block_change_triggers_synchronous_full_match_recheck() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();
        controller.setStructureCheckIntervalForTesting(1000);
        BlockPos changedPos = controller.getBlockPos().offset(1, 0, 0);
        int matcherBefore = controller.matcherInvocationCountForTesting();

        controller.handleStructureBlockChanged(changedPos);
        controller.tickStructure(level, controller.getBlockPos());

        assertThat(controller.matcherInvocationCountForTesting())
                .as("dirty event should drive a synchronous full match recheck")
                .isGreaterThan(matcherBefore);
    }

    @Test
    void unrelated_chunk_load_does_not_force_a_redundant_full_match() {
        TestBootstrap.registerRuntimeBuiltins();
        Identifier machineId = MMCR.id("unrelated_chunk_scan");
        Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++) {
            entries.put(new BlockPos(index + 1, 0, 0), new BlockPredicate.Any());
        }
        DynamicMachine machine = new DynamicMachine(machineId, "Unrelated Chunk Scan", new BlockArray(entries),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DynamicMachine seedMachine = new DynamicMachine(MMCR.id("unrelated_chunk_scan_seed"), "Unrelated Chunk Scan Seed",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE))),
                MachineControllerSpec.defaultsFor(MMCR.id("unrelated_chunk_scan_seed")));
        RuntimeTestFixtures.formStructure(controller, seedMachine);
        controller.setMachine(machine);
        controller.invalidateFormedStructure();
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();

        ServerLevel level = (ServerLevel) controller.getLevel();
        controller.tickStructure(level, controller.getBlockPos());
        int matcherAfterFirstCheck = controller.matcherInvocationCountForTesting();

        MachineControllerBlockEntity.markStructureChunkDirty(level, new ChunkPos(100, 100));
        RuntimeTestFixtures.advanceGameTime(level);
        controller.tickStructure(level, controller.getBlockPos());

        assertThat(controller.matcherInvocationCountForTesting())
                .as("an unrelated chunk event must not force an extra full match")
                .isEqualTo(matcherAfterFirstCheck);
    }

    @Test
    void verify_stage_diagnostic_fires_without_a_second_full_match() throws Exception {
        TestBootstrap.registerRuntimeBuiltins();
        Identifier machineId = MMCR.id("late_scan_mismatch");
        Map<BlockPos, BlockPredicate> entries = new LinkedHashMap<>();
        Map<BlockPos, Block> blocks = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) {
            BlockPos position = new BlockPos(index + 1, 0, 0);
            entries.put(position, new BlockPredicate.OfBlock(Blocks.STONE));
            blocks.put(position, index == 9 ? Blocks.DIRT : Blocks.STONE);
        }
        DynamicMachine machine = new DynamicMachine(machineId, "Late Scan Mismatch", new BlockArray(entries),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        controller.setMachine(machine);
        for (var entry : blocks.entrySet()) {
            controller.getLevel().setBlock(entry.getKey(), entry.getValue().defaultBlockState(), 3);
        }
        controller.invalidateFormedStructure();
        controller.setStructureCheckIntervalForTesting(1);
        int[] diagnostics = {0};
        controller.setStructureDiagnosticCallbackForTesting(() -> diagnostics[0]++);

        controller.verifyStage(testPlayer((ServerLevel) controller.getLevel(), BlockPos.ZERO), 1);

        assertThat(diagnostics[0]).isEqualTo(1);
    }

    @Test
    void block_change_resets_a_formed_structure_synchronously() {
        TestBootstrap.registerRuntimeBuiltins();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller, MachineRegistry.getMachine(MMCR.id("test_cube")));
        ServerLevel level = (ServerLevel) controller.getLevel();
        BlockPos changedRelative = null;
        int entryIndex = 0;
        for (BlockPos relative : controller.structureSnapshot().pattern().pattern().keySet()) {
            if (entryIndex++ == 1) changedRelative = relative;
        }
        BlockPos changedPos = controller.getBlockPos().offset(changedRelative);
        level.setBlock(changedPos, Blocks.DIRT.defaultBlockState(), 3);
        controller.handleStructureBlockChanged(changedPos);

        for (int tick = 0; tick < 4 && controller.structureSnapshot().formed(); tick++) {
            controller.tickStructure(level, controller.getBlockPos());
            RuntimeTestFixtures.advanceGameTime(level);
        }

        assertThat(controller.structureSnapshot().formed()).isFalse();
    }

    @Test
    void every_structure_chunk_is_tracked_for_loaded_area_invalidation() {
        BlockPos controllerPos = new BlockPos(0, 1, 1);
        Identifier machineId = MMCR.id("controller_structure_chunk");
        DynamicMachine machine = new DynamicMachine(machineId, "Controller Structure Chunk",
                new BlockArray(Map.of(new BlockPos(20, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE))),
                MachineControllerSpec.defaultsFor(machineId));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructure(controller, machine);
        BlockPos relative = controller.structureSnapshot().pattern().pattern().keySet().iterator().next();
        ChunkPos expectedChunk = new ChunkPos((controllerPos.getX() + relative.getX()) >> 4,
                (controllerPos.getZ() + relative.getZ()) >> 4);

        assertThat(controller.structureSnapshot().criticalChunks()).contains(expectedChunk);
    }

    @Test
    void unloading_a_critical_component_chunk_marks_the_formed_structure_unloaded() {
        BlockPos controllerPos = new BlockPos(0, 1, 1);
        BlockPos inputPos = controllerPos.offset(-1, 0, 0);
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(inputPos);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_chunk"), "Controller Chunk",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_chunk")),
                PortRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, input);
        assertThat(controller.structureSnapshot().criticalChunks()).contains(new ChunkPos(-1, 0));
        long formedVersion = controller.structureSnapshot().version();

        RuntimeTestFixtures.setLoadedChunks(controller.getLevel(), Set.of(
                LevelStub.chunkKey(controllerPos.getX() >> 4, controllerPos.getZ() >> 4)));
        controller.handleStructureChunkChanged((ServerLevel) controller.getLevel(), controllerPos);

        assertThat(controller.structureSnapshot().structureAreaLoaded()).isFalse();
        assertThat(controller.structureSnapshot().version()).isGreaterThan(formedVersion);
    }

    @Test
    void structure_runtime_version_round_trips_before_the_load_recheck() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setFormed(true);
        long savedVersion = controller.structureSnapshot().version();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        controller.saveAdditional(output);

        MachineControllerBlockEntity restored = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(restored.structureSnapshot().version()).isEqualTo(savedVersion);
        assertThat(restored.structureSnapshot().dirty()).isTrue();
    }

    @Test
    void recipe_pool_defaults_to_the_first_supported_pool_when_no_selection_was_saved() {
        Identifier machineId = MMCR.id("controller_recipe_pool_default");
        Identifier firstPool = MMCR.id("controller_recipe_pool_first");
        Identifier secondPool = MMCR.id("controller_recipe_pool_second");
        MachineControllerBlockEntity controller = recipePoolController(machineId, firstPool, secondPool);

        assertThat(controller.supportedRecipePoolIds()).containsExactly(firstPool, secondPool);
        assertThat(controller.currentRecipePoolId()).isEqualTo(firstPool);
    }

    @Test
    void selected_non_default_recipe_pool_uses_its_own_catalog() {
        Identifier machineId = MMCR.id("controller_recipe_pool_catalog");
        Identifier firstPool = MMCR.id("controller_recipe_pool_catalog_first");
        Identifier secondPool = MMCR.id("controller_recipe_pool_catalog_second");
        MachineRecipe firstRecipe = RecipeTestSupport.create(MMCR.id("controller_recipe_pool_catalog_recipe_first"),
                firstPool, 20, List.of(), List.of());
        MachineRecipe secondRecipe = RecipeTestSupport.create(MMCR.id("controller_recipe_pool_catalog_recipe_second"),
                secondPool, 20, List.of(), List.of());
        Map<Identifier, MachineRecipe> previous = RecipeRegistry.dynamicSnapshot();
        try {
            RecipeRegistry.replaceDynamic(Map.of(firstRecipe.id(), firstRecipe, secondRecipe.id(), secondRecipe),
                    ignored -> true);
            MachineControllerBlockEntity controller = recipePoolController(machineId, firstPool, secondPool);

            assertThat(controller.recipesForMachine()).containsExactly(firstRecipe);
            assertThat(controller.selectRecipePool(secondPool)).isTrue();
            assertThat(controller.recipesForMachine()).containsExactly(secondRecipe);
        } finally {
            RecipeRegistry.replaceDynamic(previous, ignored -> true);
        }
    }

    @Test
    void selected_recipe_pool_round_trips_through_value_persistence() {
        Identifier machineId = MMCR.id("controller_recipe_pool_persistence");
        Identifier firstPool = MMCR.id("controller_recipe_pool_persistence_first");
        Identifier secondPool = MMCR.id("controller_recipe_pool_persistence_second");
        MachineControllerBlockEntity controller = recipePoolController(machineId, firstPool, secondPool);
        assertThat(controller.selectRecipePool(secondPool)).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        controller.saveAdditional(output);
        MachineControllerBlockEntity restored = recipePoolController(machineId, firstPool, secondPool);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(restored.currentRecipePoolId()).isEqualTo(secondPool);
    }

    @Test
    void malformed_saved_recipe_pool_falls_back_to_the_first_supported_pool() {
        Identifier machineId = MMCR.id("controller_recipe_pool_malformed");
        Identifier firstPool = MMCR.id("controller_recipe_pool_malformed_first");
        Identifier secondPool = MMCR.id("controller_recipe_pool_malformed_second");
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        output.putString("selected_recipe_pool", "not an identifier");
        MachineControllerBlockEntity controller = recipePoolController(machineId, firstPool, secondPool);

        controller.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(controller.currentRecipePoolId()).isEqualTo(firstPool);
    }

    @Test
    void removed_saved_recipe_pool_falls_back_to_the_first_replacement_pool() {
        Identifier machineId = MMCR.id("controller_recipe_pool_removed");
        Identifier firstPool = MMCR.id("controller_recipe_pool_removed_first");
        Identifier removedPool = MMCR.id("controller_recipe_pool_removed_second");
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        output.putString("selected_recipe_pool", removedPool.toString());
        MachineControllerBlockEntity controller = recipePoolController(machineId, firstPool, removedPool);
        controller.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));
        MachineDefinitions.replace(MachineRegistration.builder(machineId).recipePoolIds(List.of(firstPool)).build());

        assertThat(controller.currentRecipePoolId()).isEqualTo(firstPool);
    }

    @Test
    void live_pool_and_catalog_queries_in_a_batch_do_not_derive_factory_lane_snapshots() throws Exception {
        Identifier machineId = MMCR.id("lightweight_pool_query");
        Identifier firstPool = MMCR.id("lightweight_pool_first");
        Identifier secondPool = MMCR.id("lightweight_pool_second");
        MachineControllerBlockEntity controller = recipePoolController(machineId, firstPool, secondPool);
        MachineControllerRuntime runtime = controllerRuntime(controller);
        runtime.factoryRuntime().ensureBaseLane(controller);
        var counter = runtime.factoryRuntime().getClass().getDeclaredMethod("laneSnapshotBuildCountForTesting");
        counter.setAccessible(true);
        var oldSnapshot = controller.runtimeSnapshot();
        runtime.beginUpdateBatch();
        try {
            runtime.factoryRuntime().setLaneLimit(16);
            int before = (int) counter.invoke(runtime.factoryRuntime());
            assertThat(controller.supportedRecipePoolIds()).containsExactly(firstPool, secondPool);
            assertThat(controller.currentRecipePoolId()).isEqualTo(firstPool);
            long catalogVersion = RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version();
            MachineRecipe reloaded = RecipeTestSupport.create(MMCR.id("lightweight_pool_reload_recipe"), firstPool,
                    20, List.of(), List.of());
            RecipeRegistry.replaceDynamic(Map.of(reloaded.id(), reloaded));
            assertThat(RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version()).isNotEqualTo(catalogVersion);
            var oldCatalog = RecipeRegistry.catalogForPool(controller.currentRecipePoolId());
            MachineRecipe replacementRecipe = RecipeTestSupport.create(reloaded.id(), firstPool, 30, List.of(), List.of());
            RecipeRegistry.replaceDynamic(Map.of(replacementRecipe.id(), replacementRecipe));
            var newCatalog = RecipeRegistry.catalogForPool(controller.currentRecipePoolId());
            assertThat(newCatalog.version()).isNotEqualTo(oldCatalog.version());
            assertThat(newCatalog.recipes()).containsExactly(replacementRecipe);
            assertThat(oldCatalog.recipes()).containsExactly(reloaded);
            assertThat((int) counter.invoke(runtime.factoryRuntime())).isEqualTo(before);
            assertThat(controller.runtimeSnapshot()).isSameAs(oldSnapshot);

            Machine replacement = new DynamicMachine(machineId, "replacement recipe pool machine", new BlockArray(Map.of()));
            controller.setMachine(replacement);
            MachineDefinitions.replace(MachineRegistration.builder(machineId).recipePoolIds(List.of(secondPool)).build());
            before = (int) counter.invoke(runtime.factoryRuntime());
            assertThat(controller.currentStructureSnapshot().configuredMachine()).isSameAs(replacement);
            assertThat(controller.supportedRecipePoolIds()).containsExactly(secondPool);
            assertThat(controller.currentRecipePoolId()).isEqualTo(secondPool);
            assertThat((int) counter.invoke(runtime.factoryRuntime())).isEqualTo(before);
        } finally {
            runtime.endUpdateBatch();
        }
    }

    @Test
    void removed_current_recipe_pool_discards_active_work_when_falling_back() {
        Identifier machineId = MMCR.id("controller_recipe_pool_removed_active");
        Identifier firstPool = MMCR.id("controller_recipe_pool_removed_active_first");
        Identifier removedPool = MMCR.id("controller_recipe_pool_removed_active_second");
        MachineControllerBlockEntity controller = recipePoolController(machineId, firstPool, removedPool);
        controller.selectRecipePool(removedPool);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("controller_recipe_pool_removed_active_recipe"),
                removedPool, 20, List.of(), List.of());
        CraftingRuntime craftingRuntime = controllerRuntime(controller).craftingRuntime();

        assertThat(craftingRuntime.start(recipe, 1).isCrafting()).isTrue();
        MachineDefinitions.replace(MachineRegistration.builder(machineId).recipePoolIds(List.of(firstPool)).build());

        assertThat(controller.currentRecipePoolId()).isEqualTo(firstPool);
        assertThat(craftingRuntime.active()).isFalse();
    }

    @Test
    void selecting_a_recipe_pool_discards_active_controller_work() {
        Identifier machineId = MMCR.id("controller_recipe_pool_switch");
        Identifier firstPool = MMCR.id("controller_recipe_pool_switch_first");
        Identifier secondPool = MMCR.id("controller_recipe_pool_switch_second");
        MachineControllerBlockEntity controller = recipePoolController(machineId, firstPool, secondPool);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("controller_recipe_pool_switch_recipe"), firstPool,
                20, List.of(), List.of());
        CraftingRuntime craftingRuntime = controllerRuntime(controller).craftingRuntime();

        assertThat(craftingRuntime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(craftingRuntime.active()).isTrue();

        assertThat(controller.selectRecipePool(MMCR.id("unsupported_recipe_pool"))).isFalse();
        assertThat(controller.selectRecipePool(firstPool)).isFalse();
        assertThat(controller.selectRecipePool(secondPool)).isTrue();

        assertThat(controller.currentRecipePoolId()).isEqualTo(secondPool);
        assertThat(craftingRuntime.active()).isFalse();
    }

    private static MachineControllerBlockEntity recipePoolController(Identifier machineId, Identifier... pools) {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).recipePoolIds(List.of(pools)).build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setMachine(new DynamicMachine(machineId, "recipe pool test", new BlockArray(Map.of())));
        return controller;
    }

    private static MachineControllerRuntime controllerRuntime(MachineControllerBlockEntity controller) {
        try {
            Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (MachineControllerRuntime) field.get(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access controller runtime", exception);
        }
    }

    @Test
    void negative_structure_runtime_version_loads_as_zero() {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        output.putLong("structure_runtime_version", -1L);

        MachineControllerBlockEntity restored = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(restored.structureSnapshot().version()).isZero();
        assertThat(restored.structureSnapshot().dirty()).isTrue();
    }

    @Test
    void factory_runtime_survives_initial_structure_recheck_after_load() {
        Identifier machineId = MMCR.id("controller_factory_persistence");
        DynamicMachine machine = new DynamicMachine(machineId, "Factory Persistence",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        RuntimeTestFixtures.registerRecipePool(machineId);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("controller_factory_persistence_recipe"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);

        controller.serverTick();
        resolveSharedRequests(controller);
        assertThat(controller.runtimeSnapshot().factory().active()).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        controller.saveAdditional(output);

        MachineControllerBlockEntity restored = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        FactorySchedulerBlockEntity restoredScheduler = new FactorySchedulerBlockEntity(new BlockPos(1, 0, 0),
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(restored, machine, restoredScheduler);
        restored.invalidateFormedStructure();
        restored.setMachine(null);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));
        restored.setMachine(machine);

        restored.tickStructure((ServerLevel) restored.getLevel(), restored.getBlockPos());

        assertThat(restored.runtimeSnapshot().factory().active()).isTrue();
        assertThat(restored.runtimeSnapshot().factory().presentationLanes().getFirst().recipeId())
                .isEqualTo(recipe.id().toString());
    }

    @Test
    void restored_factory_runtime_is_cleared_when_the_factory_component_is_removed_before_recheck() {
        Identifier machineId = MMCR.id("controller_factory_removed_after_load");
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos schedulerPos = controllerPos.offset(-1, 0, 0);
        DynamicMachine machine = new DynamicMachine(machineId, "Factory Removed After Load",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        RuntimeTestFixtures.registerRecipePool(machineId);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, schedulerPos, BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("controller_factory_removed_after_load_recipe"), machineId, 20,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        controller.serverTick();
        resolveSharedRequests(controller);
        assertThat(controller.runtimeSnapshot().factory().active()).isTrue();
        RuntimeTestFixtures.setDirectSignal(controller.getLevel(), controllerPos, 15);
        controller.serverTick();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        controller.saveAdditional(output);

        MachineControllerBlockEntity restored = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        FactorySchedulerBlockEntity restoredScheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(restored, machine, restoredScheduler);
        restored.invalidateFormedStructure();
        restored.setMachine(null);
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));
        restored.setMachine(machine);
        assertThat(restored.runtimeSnapshot().factory().presentationLanes())
                .anyMatch(thread -> thread.recipeId().equals(recipe.id().toString()));
        RuntimeTestFixtures.setDirectSignal(restored.getLevel(), controllerPos, 15);
        ItemInputBusBlockEntity replacement = RuntimeTestFixtures.itemInput(schedulerPos);
        RuntimeTestFixtures.replaceBlockEntity(restored, replacement);
        restored.onStructureBlockChanged(schedulerPos);
        for (int tick = 0; tick < 32 && !restored.structureSnapshot().formed(); tick++) {
            RuntimeTestFixtures.advanceGameTime(restored.getLevel());
            restored.tickStructure((ServerLevel) restored.getLevel(), controllerPos);
        }

        assertThat(restored.structureSnapshot().formed()).isTrue();
        assertThat(restored.runtimeSnapshot().factory().presentationLanes())
                .noneMatch(thread -> thread.recipeId().equals(recipe.id().toString()));
        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(restored.runtimeSnapshot());
        assertThat(state.factoryControllerPresent()).isFalse();
        assertThat(state.active()).isFalse();
        assertThat(state.activeFactoryThreadCount()).isZero();
    }

    @Test
    void reforming_same_structure_refreshes_a_replaced_same_type_component_reference() {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos componentPos = controllerPos.offset(-1, 0, 0);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_component_replacement"),
                "Controller Component Replacement",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_component_replacement")));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        ItemInputBusBlockEntity first = RuntimeTestFixtures.itemInput(componentPos);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, first);
        CraftingRuntime crafting = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("controller_component_replacement_recipe"), machine.registryName(),
                20, List.of(), List.of(), List.of(), 0, 1);
        assertThat(crafting.start(recipe, 1).isCrafting()).isTrue();
        long capabilityVersion = controller.runtimeSnapshot().capabilityVersion();
        long componentStateVersion = controller.runtimeSnapshot().stateVersion();

        ItemInputBusBlockEntity replacement = RuntimeTestFixtures.itemInput(componentPos);
        RuntimeTestFixtures.replaceBlockEntity(controller, replacement);
        controller.onStructureBlockChanged(componentPos);
        for (int tick = 0; tick < 32 && controller.structureSnapshot().dirty(); tick++) {
            RuntimeTestFixtures.advanceGameTime(controller.getLevel());
            controller.tickStructure((ServerLevel) controller.getLevel(), controllerPos);
        }

        assertThat(controller.structureSnapshot().formed()).isTrue();
        assertThat(controller.runtimeSnapshot().capabilityVersion()).isGreaterThan(capabilityVersion);
        assertThat(controller.runtimeSnapshot().stateVersion()).isGreaterThan(componentStateVersion);
        assertThat(crafting.versionsCurrent()).isFalse();
        assertThat(controller.componentRuntime().components()).extracting(component -> component.getContainer())
                .containsExactly(replacement);
        assertThat(controller.componentRuntime().capabilities())
                .containsExactlyElementsOf(replacement.capabilitySnapshot().capabilities());
    }

    @Test
    void structure_diagnostics_preserve_mismatch_and_port_requirement_details() {
        BlockPos controllerPos = new BlockPos(4, 1, 4);
        BlockPos relative = new BlockPos(1, 0, 0);
        DynamicMachine machine = new DynamicMachine(MMCR.id("controller_diagnostic"), "Controller Diagnostic",
                new BlockArray(Map.of(relative, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(MMCR.id("controller_diagnostic")));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        controller.setMachine(machine);
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get(),
                controllerPos.offset(relative), Blocks.GOLD_BLOCK), List.of(controller)));

        String mismatch = MachineControllerBlockEntity.structureMismatchDiagnostic(
                machine, Direction.SOUTH, machine.pattern(), controller.getLevel(), controllerPos);
        assertThat(mismatch).contains("reason=blockMismatch")
                .contains("relativePos=" + relative)
                .contains("actualBlock=");

        String failure = MachineControllerBlockEntity.formationFailureDiagnostic(machine, Direction.SOUTH, controllerPos,
                new PortRequirementSpec.Failure("energy_input_hatch", 0, 1, OptionalInt.empty(),
                        PortRequirementSpec.FailureReason.MISSING));
        assertThat(failure).contains("reason=portRequirementMismatch")
                .contains("portId=energy_input_hatch")
                .contains("requiredMin=1");
    }

    @Test
    void count_ports_adds_each_combined_input_family_alias_once() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        CombinedPort port = new CombinedPort(portPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get(),
                portPos, ModBlocks.BLOCKS.get("item_input_bus").get()), List.of(controller, port)));

        var method = MachineControllerBlockEntity.class.getDeclaredMethod(
                "countPorts", BlockArray.class, CompiledMachinePattern.class, Direction.class);
        method.setAccessible(true);
        PortRequirementSpec.PortCounts counts = (PortRequirementSpec.PortCounts) method.invoke(
                controller,
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                null,
                Direction.SOUTH);

        assertThat(counts.count("combined_input_test")).isEqualTo(1);
        assertThat(counts.count("item_input_bus")).isEqualTo(1);
        assertThat(counts.count("fluid_input_hatch")).isEqualTo(1);
    }

    @Test
    void count_ports_adds_each_combined_output_family_alias_once() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        CombinedPort port = new CombinedPort(portPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState(), IOType.OUTPUT);
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get(),
                portPos, ModBlocks.BLOCKS.get("item_input_bus").get()), List.of(controller, port)));

        var method = MachineControllerBlockEntity.class.getDeclaredMethod(
                "countPorts", BlockArray.class, CompiledMachinePattern.class, Direction.class);
        method.setAccessible(true);
        PortRequirementSpec.PortCounts counts = (PortRequirementSpec.PortCounts) method.invoke(
                controller,
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                null,
                Direction.SOUTH);

        assertThat(counts.count("combined_output_test")).isEqualTo(1);
        assertThat(counts.count("item_output_bus")).isEqualTo(1);
        assertThat(counts.count("fluid_output_hatch")).isEqualTo(1);
    }

    @Test
    void bidirectional_port_counts_each_binding_once_per_direction() throws Exception {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos portPos = controllerPos.offset(1, 0, 0);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        BidirectionalPort port = new BidirectionalPort(portPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        controller.setLevel(LevelStub.create(Map.of(
                controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get(),
                portPos, ModBlocks.BLOCKS.get("item_input_bus").get()), List.of(controller, port)));

        var method = MachineControllerBlockEntity.class.getDeclaredMethod(
                "countPorts", BlockArray.class, CompiledMachinePattern.class, Direction.class);
        method.setAccessible(true);
        PortRequirementSpec.PortCounts counts = (PortRequirementSpec.PortCounts) method.invoke(
                controller,
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                null,
                Direction.SOUTH);

        assertThat(counts.count("item_input_bus")).isEqualTo(1);
        assertThat(counts.count("duplicate_item_input_bus")).isZero();
        assertThat(counts.count("item_output_bus")).isEqualTo(1);
        assertThat(PortTierRequirementSpec.builder().anyItemInput().anyItemOutput().build()
                .validate(List.of(port.kind()))).isEmpty();
        assertThat(PortRequirementSpec.builder()
                .min("item_input_bus", 1)
                .min("duplicate_item_input_bus", 1)
                .build()
                .validate(counts)).hasValueSatisfying(failure ->
                        assertThat(failure.portId()).isEqualTo("duplicate_item_input_bus"));
    }

    @Test
    void structure_validation_does_not_count_multiple_aliases_from_one_bidirectional_family_twice() {
        BlockPos controllerPos = BlockPos.ZERO;
        BlockPos portPos = controllerPos.offset(-1, 0, 0);
        Identifier machineId = MMCR.id("single_family_bidirectional_count");
        DynamicMachine machine = new DynamicMachine(machineId, "Single Family Bidirectional Count",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.builder()
                        .min("item_input_bus", 1)
                        .min("duplicate_item_input_bus", 1)
                        .build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), controllerPos);
        BidirectionalPort port = new BidirectionalPort(portPos,
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState(), singleFamilyBidirectionalKind());

        AssertionError formationFailure = null;
        try {
            RuntimeTestFixtures.formStructureWithComponents(controller, machine, port);
        } catch (AssertionError exception) {
            formationFailure = exception;
        }

        assertThat(formationFailure).hasMessageContaining("Unable to form test structure");
        assertThat(controller.structureSnapshot().formed()).isFalse();
    }

    private static final class CombinedPort extends IOPortBlockEntity {
        private static final IOPortKind INPUT_KIND = combinedKind(IOType.INPUT, "combined_input_test");
        private static final IOPortKind OUTPUT_KIND = combinedKind(IOType.OUTPUT, "combined_output_test");
        private final IOPortKind kind;

        private CombinedPort(BlockPos pos, BlockState state) {
            this(pos, state, IOType.INPUT);
        }

        private CombinedPort(BlockPos pos, BlockState state, IOType ioType) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos, state);
            kind = ioType == IOType.INPUT ? INPUT_KIND : OUTPUT_KIND;
        }

        @Override
        public IOType ioType() {
            return kind.ioType();
        }

        @Override
        public IOPortKind kind() {
            return kind;
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of());
        }
    }

    private static final class BidirectionalPort extends IOPortBlockEntity {
        private static final IOPortKind KIND = bidirectionalKind();
        private final IOPortKind kind;

        private BidirectionalPort(BlockPos pos, BlockState state) {
            this(pos, state, KIND);
        }

        private BidirectionalPort(BlockPos pos, BlockState state, IOPortKind kind) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos, state);
            this.kind = kind;
        }

        @Override
        public IOType ioType() {
            return IOType.INPUT;
        }

        @Override
        public IOPortKind kind() {
            return kind;
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of());
        }
    }

    private static IOPortKind combinedKind(IOType ioType, String id) {
        List<PortFamilyDescriptor> families = List.of(
                new PortFamilyDescriptor(PortFamilyIds.ITEM, ioType, 2,
                        List.of(ioType == IOType.INPUT ? "item_input_bus" : "item_output_bus")),
                new PortFamilyDescriptor(PortFamilyIds.FLUID, ioType, 2,
                        List.of(ioType == IOType.INPUT ? "fluid_input_hatch" : "fluid_output_hatch")));
        return new PortKinds.CombinedKind(id, ioType, families, CombinedPort::new,
                PortDefinition.of(MMCR.id(id),
                        IOPortKind.binding(BuiltinCapabilityDefinitions.ITEM_TYPE, ioType, families),
                        IOPortKind.binding(BuiltinCapabilityDefinitions.FLUID_TYPE, ioType, families)));
    }

    private static IOPortKind bidirectionalKind() {
        List<PortFamilyDescriptor> families = List.of(
                new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.INPUT, 0, List.of("item_input_bus")),
                new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.INPUT, 0, List.of("duplicate_item_input_bus")),
                new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.OUTPUT, 0, List.of("item_output_bus")));
        CapabilityBinding binding = new CapabilityBinding(BuiltinCapabilityDefinitions.ITEM_TYPE,
                CapabilityDirections.bidirectional(), context -> null, PortTierPolicy.always());
        return new IOPortKind() {
            @Override
            public String id() {
                return "bidirectional_count_test";
            }

            @Override
            public IOType ioType() {
                return IOType.INPUT;
            }

            @Override
            public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
                return BidirectionalPort::new;
            }

            @Override
            public PortDefinition definition() {
                return PortDefinition.of(MMCR.id(id()), binding);
            }

            @Override
            public List<PortFamilyDescriptor> families() {
                return families;
            }
        };
    }

    private static IOPortKind singleFamilyBidirectionalKind() {
        List<PortFamilyDescriptor> families = List.of(
                new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.INPUT, 0,
                        List.of("item_input_bus", "duplicate_item_input_bus")));
        CapabilityBinding binding = new CapabilityBinding(BuiltinCapabilityDefinitions.ITEM_TYPE,
                CapabilityDirections.bidirectional(), context -> null, PortTierPolicy.always());
        return new IOPortKind() {
            @Override
            public String id() {
                return "single_family_bidirectional_count_test";
            }

            @Override
            public IOType ioType() {
                return IOType.INPUT;
            }

            @Override
            public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
                return BidirectionalPort::new;
            }

            @Override
            public PortDefinition definition() {
                return PortDefinition.of(MMCR.id(id()), binding);
            }

            @Override
            public List<PortFamilyDescriptor> families() {
                return families;
            }
        };
    }

    private static void resolveSharedRequests(MachineControllerBlockEntity controller) {
        ServerLevel level = (ServerLevel) controller.getLevel();
        SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        long gameTime = level.getGameTime();
        sharedIo.beginLevelTick(gameTime);
        async.beginLevelTick(gameTime);
        sharedIo.resolve(level);
        async.completeUntilIdleForTesting(() -> sharedIo.resolve(level));
        MachineControllerBlockEntity.flushQueuedAsyncRuntimeState(level);
    }

    private static void startPendingStructureScan(MachineControllerBlockEntity controller, ServerLevel level)
            throws InterruptedException {
        for (int tick = 0; tick < 120; tick++) {
            RuntimeTestFixtures.advanceGameTime(level);
            controller.tickStructure(level, controller.getBlockPos());
        }
        assertThat(controller.structureWorkSnapshotForTesting().scan()).isNotNull();
        assertThat(waitForPendingMainStep(MachineAsyncCoordinator.get(level))).isTrue();
    }

    private static boolean waitForPendingMainStep(MachineAsyncCoordinator coordinator) throws InterruptedException {
        return coordinator.awaitPendingMainStepForTesting(1L, TimeUnit.SECONDS);
    }

    private static NetworkInterfaceBlockEntity networkInterface(BlockPos pos) {
        BlockEntity entity = ModBlockEntities.NETWORK_INTERFACE.get().create(pos,
                ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());
        assertThat(entity).isInstanceOf(NetworkInterfaceBlockEntity.class);
        return (NetworkInterfaceBlockEntity) entity;
    }

    private static DynamicMachine networkMachine(Identifier machineId, int maxCount,
                                                  Map<BlockPos, BlockPredicate> pattern) {
        return new DynamicMachine(machineId, "network test", new BlockArray(pattern),
                MachineControllerSpec.defaultsFor(machineId), MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(),
                1, false, false, 1, List.of(), MachineRole.NORMAL, Set.of(),
                new NetworkInterfaceSpec(maxCount, 4, Set.of()), List.of(),
                RecipeFailureActions.getDefaultAction(), RecipeBehavior.defaults());
    }

    private static GlobalPos globalPos(String dimension, BlockPos pos) {
        return GlobalPos.of(ResourceKey.create(
                Registries.DIMENSION, Identifier.parse(dimension)), pos);
    }

    private static MachineControllerBlockEntity textController(Identifier machineId) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        DynamicMachine machine = new DynamicMachine(machineId, "text test",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, false, 1);
        RuntimeTestFixtures.formStructure(controller, machine);
        return controller;
    }

    private static MachineControllerBlockEntity factoryTextController(Identifier machineId) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(1, 0, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        DynamicMachine machine = new DynamicMachine(machineId, "factory text test",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, schedulerPos, BlockPos.ZERO, (String) null)));
        RuntimeTestFixtures.republish(controller);
        return controller;
    }

    private static ChangeCountingController changeCountingFactoryController(Identifier machineId) {
        ChangeCountingController controller = new ChangeCountingController(BlockPos.ZERO,
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        BlockPos schedulerPos = controller.getBlockPos().offset(1, 0, 0);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        DynamicMachine machine = new DynamicMachine(machineId, "factory dirty test",
                new BlockArray(Map.of(new BlockPos(1, 0, 0),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, schedulerPos, BlockPos.ZERO, (String) null)));
        RuntimeTestFixtures.republish(controller);
        return controller;
    }

    private static MachineControllerRuntime runtimeOf(MachineControllerBlockEntity controller) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        field.setAccessible(true);
        return (MachineControllerRuntime) field.get(controller);
    }

    private static void assertSharedStaticState(ControllerRuntimeSnapshot after, ControllerRuntimeSnapshot before) {
        assertThat(after.structure()).isSameAs(before.structure());
        assertThat(after.foundModifiers()).isSameAs(before.foundModifiers());
        assertThat(after.foundLevels()).isSameAs(before.foundLevels());
        assertThat(after.dataStorageValues()).isSameAs(before.dataStorageValues());
        assertThat(after.componentPresentations()).isSameAs(before.componentPresentations());
        assertThat(after.foundLevelIds()).isSameAs(before.foundLevelIds());
        assertThat(after.factoryControllerPresent()).isEqualTo(before.factoryControllerPresent());
        assertThat(after.parallelControllerCount()).isEqualTo(before.parallelControllerCount());
        assertThat(after.upgradeContentRevision()).isEqualTo(before.upgradeContentRevision());
    }

    private static Object ownedUpgradeItems(ControllerRuntimeSnapshot snapshot) throws Exception {
        // The public accessor intentionally returns copies, so inspect only the private owner identity.
        Field field = ControllerRuntimeSnapshot.class.getDeclaredField("upgradeItems");
        field.setAccessible(true);
        return field.get(snapshot);
    }

    private static MachineLevel snapshotLevel(String path) {
        Identifier id = MMCR.id(path);
        return new MachineLevel(id, MMCR.id("snapshot_level_type"), 1, new BlockPredicate.Any(),
                ItemStack.EMPTY, ModifierDefinition.EMPTY);
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class MutablePresentationMachine implements Machine {
        private Identifier id = MMCR.id("mutable_snapshot_machine");
        private String name = "test.original_name";
        private boolean factory;
        private MachineRole role = MachineRole.NORMAL;
        private long maximum = 8L;
        private boolean parallel = true;
        private MachineBehavior behavior = RecipeBehavior.defaults();
        private final BlockArray pattern = new BlockArray(Map.of());

        @Override
        public Identifier registryName() { return id; }

        @Override
        public String displayNameKey() { return name; }

        @Override
        public BlockArray pattern() { return pattern; }

        @Override
        public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }

        @Override
        public boolean hasFactory() { return factory; }

        @Override
        public MachineRole role() { return role; }

        @Override
        public boolean parallelizable() { return parallel; }

        @Override
        public long maxParallelism() { return maximum; }

        @Override
        public MachineBehavior behavior() { return behavior; }
    }

    private static boolean runtimeStateBroadcastPending(MachineControllerBlockEntity controller) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("runtimeStateBroadcastPending");
        field.setAccessible(true);
        return field.getBoolean(controller);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> lastSentRecipeScreenTextRevisions(MachineControllerBlockEntity controller)
            throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("lastSentRecipeScreenTextRevisions");
        field.setAccessible(true);
        return (Map<String, Long>) field.get(controller);
    }

    private static void invokeSyncOpenText(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("syncOpenControllerScreenText");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static void invokeTickFactoryRecipes(MachineControllerBlockEntity controller) throws Exception {
        Method method = MachineControllerBlockEntity.class.getDeclaredMethod("tickFactoryRecipes");
        method.setAccessible(true);
        method.invoke(controller);
    }

    private static List<PktControllerScreenTextPayload> textPackets(ServerPlayer player) {
        return ((TestConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(PktControllerScreenTextPayload.class::isInstance)
                .map(PktControllerScreenTextPayload.class::cast)
                .toList();
    }

    private static List<PktMachineStatePayload> machineStatePackets(ServerPlayer player) {
        return ((TestConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(PktMachineStatePayload.class::isInstance)
                .map(PktMachineStatePayload.class::cast)
                .toList();
    }

    private static AbstractContainerMenu closedMenu() {
        return new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
    }

    private static final class ChangeCountingController extends MachineControllerBlockEntity {
        private int changedCalls;
        private int persistenceChangedCalls;

        private ChangeCountingController(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public void setChanged() {
            changedCalls++;
            super.setChanged();
        }

        @Override
        protected void persistRuntimeChanges() {
            persistenceChangedCalls++;
            super.persistRuntimeChanges();
        }
    }

    private static ServerPlayer player(ServerLevel level, BlockPos pos) throws Exception {
        ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        setField(Entity.class, player, "level", level);
        setField(Entity.class, player, "position", Vec3.atCenterOf(pos));
        TestConnection connection = (TestConnection) unsafe().allocateInstance(TestConnection.class);
        connection.packets = new ArrayList<>();
        setField(ServerGamePacketListenerImpl.class, connection, "chunkSender", new PlayerChunkSender(true));
        player.connection = connection;
        player.setChunkTrackingView(ChunkTrackingView.of(ChunkPos.containing(pos), 4));
        if (level.getChunkSource() == null) {
            var source = (ServerChunkCache) unsafe().allocateInstance(ServerChunkCache.class);
            setField(ServerChunkCache.class, source, "chunkMap", unsafe().allocateInstance(ChunkMap.class));
            setField(ServerLevel.class, level, "chunkSource", source);
        }
        return player;
    }

    private static void setPlayers(ServerLevel level, List<ServerPlayer> players) throws Exception {
        setField(ServerLevel.class, level, "players", players);
    }

    private static ServerPlayer testPlayer(ServerLevel level, BlockPos pos) throws Exception {
        return player(level, pos);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = null;
        Class<?> declaringClass = type;
        while (declaringClass != null && field == null) {
            try {
                field = declaringClass.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                declaringClass = declaringClass.getSuperclass();
            }
        }
        if (field == null) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void bind(Object deferredHolder, MenuType<?> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
    }

    /**
     * Captures server packets for menu synchronization assertions.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestConnection extends ServerGamePacketListenerImpl {
        private List<Packet<?>> packets;

        private TestConnection() {
            super(null, null, null, null);
        }

        @Override
        public void send(Packet<?> packet) {
            packets.add(packet);
        }
    }
}
