package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.TickFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.machine.definition.MachineBehavior;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.internal.recipe.MachineRecipeThread;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.util.IOType;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Final crafting runtime behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingRuntimeTest {
    private static final HolderLookup.Provider EMPTY_LOOKUP = HolderLookup.Provider.create(Stream.empty());
    private static final FailureReason REGISTERED_RUNTIME_REASON = new FailureReason(
            MMCR.id("runtime_registered_restore"), "gui.mmcr.failure.runtime_registered_restore", 50);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        if (FailureReasonRegistry.isFrozen()) FailureReasonRegistry.clearForTesting();
        if (FailureReasonRegistry.find(BuiltinFailureReasons.UNKNOWN.id()) == null) {
            BuiltinFailureReasons.register();
        }
        if (FailureReasonRegistry.find(REGISTERED_RUNTIME_REASON.id()) == null) {
            FailureReasonRegistry.register(REGISTERED_RUNTIME_REASON);
        }
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void startTickAndFinishCommitInputsAndOutputs() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 2));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_complete", 1, List.of(
                input(Items.IRON_INGOT, 2), output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.itemHandler().amount(0)).isZero();
        assertThat(runtime.tick().isCrafting()).isTrue();
        assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
        ItemStack result = item(output.itemHandler(), 0);
        assertThat(result.getItem()).isEqualTo(Items.IRON_NUGGET);
        assertThat(result.getCount()).isEqualTo(1);
    }

    @Test
    void direct_start_rejects_a_recipe_from_a_different_machine_pool() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_foreign_pool"),
                MMCR.id("foreign_recipe_pool"), 20, List.of(), List.of());

        assertThat(runtime.start(recipe, 1).isCrafting()).isFalse();
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNotNull();
        assertThat(runtime.failure().details()).containsEntry("reason", "recipe_pool");
    }

    @Test
    void versions_current_reads_live_module_connection_without_republishing() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        ComponentRuntime components = controller.componentRuntime();
        ResourceLocation hostId = MMCR.id("runtime_validation_host");
        components.replaceModuleConnectionState(ModuleConnectionStatus.connected(hostId), 1);
        CraftingRuntime runtime = new CraftingRuntime(controller, components);
        MachineRecipe recipe = recipe("runtime_live_module_validation", 20, List.of());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        ControllerRuntimeSnapshot published = controller.currentRuntimeSnapshot();
        long capabilityVersion = components.capabilityVersion();
        long structureVersion = controller.currentStructureSnapshot().version();
        assertThat(runtime.versionsCurrent()).isTrue();

        components.replaceModuleConnectionState(ModuleConnectionStatus.disconnected(), 1);

        assertThat(components.capabilityVersion()).isEqualTo(capabilityVersion);
        assertThat(controller.currentStructureSnapshot().version()).isEqualTo(structureVersion);
        assertThat(published.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())).isTrue();
        assertThat(runtime.versionsCurrent()).isFalse();
        components.replaceModuleConnectionState(ModuleConnectionStatus.connected(hostId), 1);
        assertThat(runtime.versionsCurrent()).isTrue();
    }

    @Test
    void versions_current_detects_live_capability_and_structure_replacement() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_live_version_validation", 20, List.of());
        assertThat(runtime.versionsCurrent()).isTrue();
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(runtime.versionsCurrent()).isTrue();

        controller.componentRuntime().replaceComponents(List.of());

        assertThat(runtime.versionsCurrent()).isFalse();
        runtime.tick();
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.VERSION_INVALIDATED);
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(runtime.versionsCurrent()).isTrue();

        controller.setMachine(new DynamicMachine(MMCR.id("test_cube"), "replacement", new BlockArray(Map.of())));

        assertThat(runtime.versionsCurrent()).isFalse();
        runtime.tick();
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.VERSION_INVALIDATED);
    }

    @Test
    void versions_current_detects_pool_selection_and_same_id_registration_removal() {
        ResourceLocation machineId = MMCR.id("runtime_live_pool_validation");
        ResourceLocation firstPool = MMCR.id("runtime_live_pool_first");
        ResourceLocation secondPool = MMCR.id("runtime_live_pool_second");
        RuntimeTestFixtures.registerRecipePool(machineId);
        MachineDefinitions.replace(MachineRegistration.builder(machineId)
                .recipePoolIds(List.of(firstPool, secondPool)).build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setMachine(new DynamicMachine(machineId, "pool validation", new BlockArray(Map.of())));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe first = RecipeTestSupport.create(MMCR.id("runtime_live_first_recipe"), firstPool,
                20, List.of(), List.of());
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("runtime_live_second_recipe"), secondPool,
                20, List.of(), List.of());
        assertThat(runtime.start(first, 1).isCrafting()).isTrue();
        assertThat(runtime.versionsCurrent()).isTrue();

        assertThat(controller.selectRecipePool(secondPool)).isTrue();
        assertThat(runtime.versionsCurrent()).isFalse();
        runtime.tick();
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.start(second, 1).isCrafting()).isTrue();
        MachineDefinitions.replace(MachineRegistration.builder(machineId)
                .recipePoolIds(List.of(firstPool, secondPool)).build());
        assertThat(runtime.versionsCurrent()).isTrue();

        MachineDefinitions.replace(MachineRegistration.builder(machineId).recipePoolIds(List.of(firstPool)).build());

        assertThat(runtime.versionsCurrent()).isFalse();
        assertThat(controller.currentRecipePoolId()).isEqualTo(firstPool);
        runtime.tick();
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.VERSION_INVALIDATED);
    }

    @Test
    void snapshots_reuse_unchanged_payload_and_keep_status_copies_isolated() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        CraftingStateSnapshot idle = runtime.snapshot();
        int builds = runtime.snapshotBuildCountForTesting();
        assertThat(runtime.snapshot()).isSameAs(idle);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(builds);
        CraftingRuntime other = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(other.snapshot()).isNotSameAs(idle);

        MachineRecipe recipe = recipe("runtime_snapshot_status", 20, List.of());
        CraftingStatus liveStatus = runtime.start(recipe, 1);
        assertThat(liveStatus.isCrafting()).isTrue();
        CraftingStateSnapshot started = runtime.snapshot();
        assertThat(started).isNotSameAs(idle);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        String originalMessage = started.status().getUnlocMessage();
        started.status().overrideStatusMessage("test.changed.returned.copy");
        assertThat(started.status().getUnlocMessage()).isEqualTo(originalMessage);
        assertThat(runtime.snapshot()).isSameAs(started);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(builds);
        liveStatus.overrideStatusMessage(originalMessage);
        assertThat(runtime.snapshot()).isSameAs(started);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(builds);

        liveStatus.overrideStatusMessage("test.changed.live.status");
        CraftingStateSnapshot changedMessage = runtime.snapshot();
        assertThat(changedMessage).isNotSameAs(started);
        assertThat(changedMessage.status().getUnlocMessage()).isEqualTo("test.changed.live.status");
        assertThat(started.status().getUnlocMessage()).isEqualTo(originalMessage);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        assertThat(runtime.snapshot()).isSameAs(changedMessage);

        runtime.pause();
        runtime.start(recipe, 1).overrideStatusMessage(changedMessage.status().getUnlocMessage());
        CraftingStateSnapshot paused = runtime.snapshot();
        assertThat(paused.status().isPaused()).isTrue();
        assertThat(paused.status().getUnlocMessage()).isEqualTo(changedMessage.status().getUnlocMessage());
        assertThat(changedMessage.status().isPaused()).isFalse();
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        runtime.resume();
        CraftingStateSnapshot resumed = runtime.snapshot();
        assertThat(resumed).isNotSameAs(started);
        assertThat(resumed.status().getUnlocMessage()).isEqualTo(originalMessage);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        assertThat(runtime.snapshot()).isSameAs(resumed);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(builds);
    }

    @Test
    void snapshots_track_each_live_progress_and_parallelism_field() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_snapshot_progress", 20, List.of());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        CraftingStateSnapshot started = runtime.snapshot();
        int builds = runtime.snapshotBuildCountForTesting();

        runtime.activeRecipe().setTick(1);
        CraftingStateSnapshot progressed = runtime.snapshot();
        assertThat(progressed.tick()).isEqualTo(1);
        assertThat(started.tick()).isZero();
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        runtime.activeRecipe().setTotalTick(30);
        CraftingStateSnapshot durationChanged = runtime.snapshot();
        assertThat(durationChanged.totalTick()).isEqualTo(30);
        assertThat(progressed.totalTick()).isEqualTo(20);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        runtime.activeRecipe().setMaxParallelism(4);
        CraftingStateSnapshot limitChanged = runtime.snapshot();
        assertThat(limitChanged.maxParallelism()).isEqualTo(4L);
        assertThat(limitChanged.parallelism()).isEqualTo(durationChanged.parallelism());
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        runtime.activeRecipe().setParallelism(2);
        CraftingStateSnapshot parallelismChanged = runtime.snapshot();
        assertThat(parallelismChanged.parallelism()).isEqualTo(2L);
        assertThat(limitChanged.parallelism()).isEqualTo(1L);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        assertThat(runtime.snapshot()).isSameAs(parallelismChanged);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(builds);
    }

    @Test
    void snapshots_track_recipe_identity_and_finish_without_changing_previous_payloads() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe first = recipe("runtime_snapshot_first", 1, List.of());
        MachineRecipe second = recipe("runtime_snapshot_second", 1, List.of());
        assertThat(runtime.start(first, 1).isCrafting()).isTrue();
        CraftingStateSnapshot started = runtime.snapshot();
        int builds = runtime.snapshotBuildCountForTesting();

        runtime.invalidate();
        assertThat(runtime.start(second, 1).isCrafting()).isTrue();
        CraftingStateSnapshot replaced = runtime.snapshot();
        assertThat(replaced.recipeId()).isEqualTo(second.id());
        assertThat(started.recipeId()).isEqualTo(first.id());
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        runtime.tick();
        assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
        CraftingStateSnapshot finished = runtime.snapshot();
        assertThat(finished.recipeId()).isNull();
        assertThat(finished.status().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
        assertThat(replaced.status().isCrafting()).isTrue();
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        assertThat(runtime.snapshot()).isSameAs(finished);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(builds);
    }

    @Test
    void snapshots_track_each_restored_version_and_reuse_normalized_unset_versions() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe("runtime_snapshot_versions", 20, List.of()), 1).isCrafting()).isTrue();
        ActiveMachineRecipe active = runtime.activeRecipe();
        runtime.restore(active, null, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
        CraftingStateSnapshot unset = runtime.snapshot();
        int builds = runtime.snapshotBuildCountForTesting();

        runtime.restore(active, null, 0L, 0L, 0L, Long.MIN_VALUE);
        assertThat(runtime.snapshot()).isSameAs(unset);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(builds);
        runtime.restore(active, null, 1L, 0L, 0L, Long.MIN_VALUE);
        CraftingStateSnapshot structureChanged = runtime.snapshot();
        assertThat(structureChanged.structureVersion()).isEqualTo(1L);
        assertThat(unset.structureVersion()).isZero();
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        runtime.restore(active, null, 1L, 2L, 0L, Long.MIN_VALUE);
        CraftingStateSnapshot capabilityChanged = runtime.snapshot();
        assertThat(capabilityChanged.capabilityVersion()).isEqualTo(2L);
        assertThat(structureChanged.capabilityVersion()).isZero();
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        runtime.restore(active, null, 1L, 2L, 3L, Long.MIN_VALUE);
        CraftingStateSnapshot modifierChanged = runtime.snapshot();
        assertThat(modifierChanged.modifierVersion()).isEqualTo(3L);
        assertThat(capabilityChanged.modifierVersion()).isZero();
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        assertThat(runtime.snapshot()).isSameAs(modifierChanged);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(builds);
    }

    @Test
    void snapshots_track_failure_details_even_when_the_status_message_is_unchanged() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        runtime.recordSearchFailure(null);
        CraftingStateSnapshot first = runtime.snapshot();
        int builds = runtime.snapshotBuildCountForTesting();
        ExecutionStatus changedFailure = ExecutionStatus.blocked(MMCR.id("changed_search_failure"), MMCR.id("test"),
                FailureOccurrence.at(BuiltinFailureReasons.RECIPE_SEARCH, MMCR.id("test"), FailurePhase.RECIPE_SEARCH,
                        null, null, Map.of("reason", "changed")));

        runtime.recordSearchFailure(changedFailure);

        CraftingStateSnapshot changed = runtime.snapshot();
        assertThat(changed.status().getUnlocMessage()).isEqualTo(first.status().getUnlocMessage());
        assertThat(changed.failure()).isSameAs(changedFailure);
        assertThat(first.failure()).isNotEqualTo(changedFailure);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
        assertThat(runtime.snapshot()).isSameAs(changed);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(builds);
        runtime.invalidate();
        CraftingStateSnapshot cleared = runtime.snapshot();
        assertThat(cleared.failure()).isNull();
        assertThat(first.failure()).isNotNull();
        assertThat(changed.failure()).isSameAs(changedFailure);
        assertThat(runtime.snapshotBuildCountForTesting()).isEqualTo(++builds);
    }

    @Test
    void lightweight_observation_tracks_live_progress_pause_failure_and_finish_without_mutating_published_status() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("observation_live_state", 2, List.of());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        CraftingStateSnapshot oldSnapshot = runtime.snapshot();
        var started = runtime.observation();
        String recipeText = runtime.recipeIdText();
        assertThat(recipeText).isEqualTo(recipe.id().toString());

        runtime.tick();
        var progressed = runtime.observation();
        assertThat(progressed).isNotEqualTo(started);
        assertThat(progressed.tick()).isGreaterThan(started.tick());
        assertThat(runtime.recipeIdText()).isSameAs(recipeText);
        runtime.pause();
        var paused = runtime.observation();
        assertThat(paused.status()).isEqualTo(CraftingStatus.Status.PAUSED);
        runtime.resume();
        assertThat(runtime.observation().status()).isEqualTo(CraftingStatus.Status.CRAFTING);
        runtime.recordSearchFailure(null);
        assertThat(runtime.observation().failure()).isNotNull();
        assertThat(started.failure()).isNull();
        runtime.tick();
        assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
        assertThat(runtime.observation().recipeId()).isNull();
        assertThat(runtime.recipeIdText()).isEmpty();
        assertThat(oldSnapshot.tick()).isZero();
        assertThat(oldSnapshot.status().isPaused()).isFalse();
        oldSnapshot.status().overrideStatusMessage("test.changed.returned.copy");
        assertThat(oldSnapshot.status().getUnlocMessage()).isEqualTo(started.statusMessage());
    }

    @Test
    void recipe_tick_callback_is_preserved_when_capability_phases_are_enabled() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        AtomicInteger callbacks = new AtomicInteger();
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .recipeTick(context -> callbacks.incrementAndGet()).build()));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("runtime_recipe_tick_callback", 2, List.of()), 1).isCrafting()).isTrue();
        runtime.tick();

        assertThat(callbacks).hasValue(1);
    }

    @Test
    void capability_tick_failure_is_observable_and_a_later_success_clears_it() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        ExecutionStatus failure = ExecutionStatus.blocked(MMCR.id("tick_failure"), MMCR.id("test"),
                FailureOccurrence.at(BuiltinFailureReasons.PER_TICK, MMCR.id("test"), FailurePhase.PER_TICK,
                        null, null, Map.of()));

        runtime.handleCapabilityTickResult(new CapabilityTickResult(List.of(), failure, false));
        assertThat(runtime.failure()).isSameAs(failure);
        runtime.handleCapabilityTickResult(CapabilityTickResult.empty());

        assertThat(runtime.failure()).isNull();
    }

    @Test
    void start_plans_inputs_without_requiring_output_capacity() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_output_capacity_is_deferred", 20, List.of(
                input(Items.IRON_INGOT, 1), output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.itemHandler().amount(0)).isZero();
    }

    @Test
    void active_recipe_copies_and_exposes_the_execution_snapshot() {
        MachineRecipe recipe = recipe("active_effective_snapshot", 20, List.of());
        ItemStack outputStack = stack(Items.GOLD_NUGGET, 1);
        RecipeStartContext.ExecutionSnapshot execution = new RecipeStartContext.ExecutionSnapshot(7,
                MachineRequirement.copyList(
                        List.of(input(Items.IRON_INGOT, 2), output(Items.GOLD_NUGGET, 1))),
                List.of(new MachineOutput.ItemOutput(outputStack, 1F)));

        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 2, execution);
        outputStack.setCount(64);
        ((MachineOutput.ItemOutput) active.effectiveOutputs().getFirst()).stack().setCount(64);

        assertThat(active.getTotalTick()).isEqualTo(7);
        assertThat(((ItemRequirement) active.effectiveRequirements().getFirst()).count()).isEqualTo(2);
        assertThat(((MachineOutput.ItemOutput) active.effectiveOutputs().getFirst()).stack().getCount()).isEqualTo(1);
        assertThat(active.executionSnapshot().duration()).isEqualTo(7);
    }

    @Test
    void cancelled_start_stays_idle_without_failure_or_input_consumption() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> context.cancel()).build()));
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("runtime_cancelled_start", 20,
                List.of(input(Items.IRON_INGOT, 1))), 1).getStatus())
                .isEqualTo(CraftingStatus.Status.IDLE);
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNull();
        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
    }

    @Test
    void finish_veto_uses_the_retry_gate() {
        AtomicInteger callbacks = new AtomicInteger();
        assertFinishRetryGate(RecipeBehavior.builder().beforeFinish(context -> {
            callbacks.incrementAndGet();
            context.cancel();
        }).build(), callbacks);
    }

    @Test
    void finish_callback_exception_uses_the_retry_gate() {
        AtomicInteger callbacks = new AtomicInteger();
        assertFinishRetryGate(RecipeBehavior.builder().beforeFinish(context -> {
            callbacks.incrementAndGet();
            throw new IllegalStateException("expected test callback failure");
        }).build(), callbacks);
    }

    @Test
    void discard_outputs_removes_completion_outputs_without_undoing_per_tick_energy() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(-1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        EnergyInputHatchBlockEntity inputEnergy = RuntimeTestFixtures.energyInput(new BlockPos(2, 0, 0));
        EnergyOutputHatchBlockEntity outputEnergy = RuntimeTestFixtures.energyOutput(new BlockPos(3, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"),
                input, output, inputEnergy, outputEnergy);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        inputEnergy.energyStorage().setAmount(2);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_discard_outputs"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1),
                        output(Items.IRON_NUGGET, 1), new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4)));
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeFinish(context -> context.discardOutputs()).build()));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.itemHandler().amount(0)).isZero();
        assertThat(inputEnergy.energyStorage().getAmountAsLong()).isEqualTo(1L);
        runtime.tick();
        assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);

        assertThat(runtime.active()).isFalse();
        assertThat(input.itemHandler().amount(0)).isZero();
        assertThat(inputEnergy.energyStorage().getAmountAsLong()).isZero();
        assertThat(output.itemHandler().amount(0)).isZero();
        assertThat(outputEnergy.energyStorage().getAmountAsLong()).isEqualTo(4L);
    }

    @Test
    void finish_capability_operation_exception_uses_the_retry_gate() {
        ThrowingItemOutputBus output = new ThrowingItemOutputBus(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("runtime_finish_operation_exception", 1,
                List.of(output(Items.IRON_NUGGET, 1))), 1).isCrafting()).isTrue();
        runtime.tick();

        assertThatCode(runtime::finish).doesNotThrowAnyException();
        assertThat(runtime.active()).isTrue();
        assertThat(runtime.finishPending()).isTrue();
        assertThat(runtime.failure()).isNotNull();
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.FINISH);
        assertThat(runtime.shouldRetryFinish()).isFalse();

        LevelStub.setGameTime(controller.getLevel(), 10);
        assertThat(runtime.shouldRetryFinish()).isTrue();
        assertThatCode(runtime::finish).doesNotThrowAnyException();
        assertThat(runtime.shouldRetryFinish()).isFalse();
    }

    @Test
    void finish_output_replacement_keeps_non_physical_outputs() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        EnergyOutputHatchBlockEntity energy = RuntimeTestFixtures.energyOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output, energy);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeFinish(context -> context.setOutputs(List.of(
                        new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)))).build()));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_replace_physical_output"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(output(Items.IRON_NUGGET, 1), new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);

        assertThat(item(output.itemHandler(), 0).is(Items.GOLD_NUGGET)).isTrue();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(4L);
    }

    @Test
    void invalid_finish_output_uses_the_retry_gate() {
        AtomicInteger callbacks = new AtomicInteger();
        assertFinishRetryGate(RecipeBehavior.builder().beforeFinish(context -> {
            callbacks.incrementAndGet();
            context.setOutputs(List.of(new MachineOutput.ItemOutput(ItemStack.EMPTY, 1F)));
        }).build(), callbacks);
    }

    @Test
    void failed_start_reports_structured_missing_resource_without_consuming_input() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_missing_input", 20, List.of(
                input(Items.IRON_INGOT, 2)));

        runtime.start(recipe, 1);

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNotNull();
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
    }

    @Test
    void registeredFailureReasonUsesItsTranslationKey() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        FailureReason reason = new FailureReason(MMCR.id("runtime_registered_failure"),
                "gui.mmcr.failure.runtime_registered_failure");
        Field failure = CraftingRuntime.class.getDeclaredField("failure");
        failure.setAccessible(true);
        failure.set(runtime, ExecutionStatus.blocked(MMCR.id("test"), MMCR.id("test"),
                FailureOccurrence.at(reason, MMCR.id("test"), FailurePhase.RUNTIME, null, null, Map.of())));

        assertThat(runtime.failureUnloc()).isEqualTo(reason.translationKey());
    }

    @Test
    void duplicateInputRequirementsRemainAtomicWhenCombinedStorageIsInsufficient() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_duplicate_input", 20, List.of(
                input(Items.IRON_INGOT, 1), input(Items.IRON_INGOT, 1)));

        runtime.start(recipe, 1);

        assertThat(runtime.active()).isFalse();
        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
    }

    @Test
    void duplicateOutputRequirementsCommit_each_output_to_the_real_storage() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_duplicate_output", 1, List.of(
                output(Items.IRON_NUGGET, 1), output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
        assertThat(output.itemHandler().amount(0)).isEqualTo(2L);
    }

    @Test
    void redstonePauseAndResumeKeepTheActiveRuntime() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_pause", 20, List.of());

        runtime.start(recipe, 1);
        runtime.pause();
        assertThat(runtime.snapshot().status().isPaused()).isTrue();
        assertThat(runtime.active()).isTrue();

        runtime.resume();
        assertThat(runtime.snapshot().status().isCrafting()).isTrue();
    }

    @Test
    void capabilityVersionInvalidationCancelsTheActiveRuntime() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_invalidation", 20, List.of(input(Items.IRON_INGOT, 1)));

        runtime.start(recipe, 1);
        controller.componentRuntime().replaceComponents(List.of());
        controller.setMachine(controller.runtimeSnapshot().structure().configuredMachine());

        runtime.tick();

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.VERSION_INVALIDATED);
    }

    @Test
    void perTickEnergyIsCommittedThroughTheRealEnergyCapability() {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.energyStorage().setAmount(10);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_energy"), MMCR.id("test_cube"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(new EnergyRequirement(2)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(8);
        runtime.tick();

        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(6);
        assertThat(runtime.active()).isTrue();
    }

    @Test
    void fluxPrefetchesTheWholeRecipeBeforeActivation() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(energyRecipe("runtime_flux_prefetch", 3, 2), 1).isCrafting()).isTrue();

        assertThat(network.extracted()).isEqualTo(6L);
        assertThat(network.reserved()).isEqualTo(6L);
        assertThat(runtime.active()).isTrue();
    }

    @Test
    void fluxPrefetchClampsRequestedParallelismToTheMachineLimit() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(12L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_flux_parallel"), MMCR.id("test_cube"), 3,
                List.of(new EnergyRequirement(2)), List.of(), List.of(), 0, 4, false,
                true, List.of(), false, Set.of());
        assertThat(runtime.start(recipe, 2).isCrafting()).isTrue();

        assertThat(runtime.parallelism()).isEqualTo(1L);
        assertThat(network.extracted()).isEqualTo(6L);
    }

    @Test
    void incompleteFluxPrefetchDoesNotStartOrPartiallyExtract() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(5L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        runtime.start(energyRecipe("runtime_flux_shortage", 3, 2), 1);

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
        assertThat(network.extracted()).isZero();
        assertThat(network.reserved()).isZero();
    }

    @Test
    void recipeSearchRejectsIncompleteFluxPrefetchBeforeStartingALane() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(5L);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(network)));

        var result = context.planStartResult(energyRecipe("runtime_flux_search_shortage", 3, 2), 1);

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT);
        assertThat(network.planned).isZero();
        assertThat(network.extracted()).isZero();
        assertThat(network.reserved()).isZero();
    }

    @Test
    void prefetchedEnergyIsConsumedLocallyOnEachTick() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(energyRecipe("runtime_flux_local_tick", 3, 2), 1).isCrafting()).isTrue();
        network.resetExtractionCounter();

        runtime.tick();

        assertThat(network.extracted()).isZero();
        runtime.invalidate();
        assertThat(network.released()).isEqualTo(4L);
    }

    @Test
    void prefetchedEnergyIsReleasedOnceWhenTheRuntimeIsInvalidated() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(energyRecipe("runtime_flux_release", 3, 2), 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.invalidate();
        runtime.invalidate();

        assertThat(network.released()).isEqualTo(4L);
    }

    @Test
    void restoreReinstatesTheRemainingPrefetchReservation() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        MachineRecipe recipe = energyRecipe("runtime_flux_restore", 3, 2);
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        CompoundTag output = new CompoundTag();
        runtime.save(output, EMPTY_LOOKUP);
        network.clearReservations();

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isTrue();
        assertThat(network.reserved()).isEqualTo(4L);
        restored.invalidate();
        assertThat(network.released()).isEqualTo(4L);
    }

    @Test
    void restoreExcludesTheFinishPendingTickWhoseInputWasAlreadyCommitted() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        MachineRecipe recipe = energyRecipe("runtime_flux_restore_finish", 3, 2);
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.tick();
        runtime.tick();
        assertThat(runtime.finishPending()).isTrue();
        CompoundTag output = new CompoundTag();
        runtime.save(output, EMPTY_LOOKUP);
        network.clearReservations();

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isTrue();
        assertThat(network.reserved()).isZero();
        restored.invalidate();
        assertThat(network.released()).isZero();
    }

    @Test
    void restorePreservesEachFacetAllocationByReservationKey() {
        PrefetchNetworkCapability first = new PrefetchNetworkCapability("test:first", 2L);
        PrefetchNetworkCapability second = new PrefetchNetworkCapability("test:second", 4L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetworks(first, second);
        MachineRecipe recipe = energyRecipe("runtime_flux_restore_first_facet", 3, 2);
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        CompoundTag output = new CompoundTag();
        runtime.save(output, EMPTY_LOOKUP);
        first.clearReservations();
        second.clearReservations();

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(first.reserved()).isEqualTo(2L);
        assertThat(second.reserved()).isEqualTo(4L);
        restored.invalidate();
        restored.invalidate();
        assertThat(first.released()).isEqualTo(2L);
        assertThat(second.released()).isEqualTo(4L);
    }

    @Test
    void restoreCombinesMultipleEnergyRequirementsIntoOneFacetAllocation() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_flux_restore_combined"),
                MMCR.id("test_cube"), 3, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new EnergyRequirement(1), new EnergyRequirement(1)));
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        CompoundTag output = new CompoundTag();
        runtime.save(output, EMPTY_LOOKUP);
        ListTag allocations = output.getCompound("recipe").getCompound("data")
                .getList("prefetched_energy_allocations", Tag.TAG_COMPOUND);
        network.clearReservations();

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(allocations).hasSize(1);
        assertThat(allocations.getCompound(0).getString("key")).isEqualTo("test:prefetch");
        assertThat(allocations.getCompound(0).getLong("amount")).isEqualTo(4L);
        assertThat(restored.active()).isTrue();
        assertThat(network.reserved()).isEqualTo(4L);
    }

    @Test
    void releaseFailureDoesNotReexecuteAnActivePrefetchAllocation() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(energyRecipe("runtime_flux_release_failure", 3, 2), 1).isCrafting()).isTrue();
        network.failRelease();
        runtime.invalidate();
        runtime.invalidate();

        assertThat(network.releaseCalls()).isEqualTo(1);
    }

    @Test
    void restoreRejectsMalformedPrefetchAllocations() {
        assertPrefetchRestoreFails("runtime_flux_restore_missing", data -> data
                .getList("prefetched_energy_allocations", Tag.TAG_COMPOUND)
                .getCompound(0).putString("key", "test:missing"));
        assertPrefetchRestoreFails("runtime_flux_restore_duplicate", data -> {
            ListTag allocations = data.getList("prefetched_energy_allocations", Tag.TAG_COMPOUND);
            allocations.add(allocations.getCompound(0).copy());
        });
        assertPrefetchRestoreFails("runtime_flux_restore_negative", data -> data
                .getList("prefetched_energy_allocations", Tag.TAG_COMPOUND).getCompound(0).putLong("amount", -1L));
        assertPrefetchRestoreFails("runtime_flux_restore_type", data -> data
                .getList("prefetched_energy_allocations", Tag.TAG_COMPOUND).getCompound(0).putString("amount", "4"));
        assertPrefetchRestoreFails("runtime_flux_restore_total", data -> data
                .getList("prefetched_energy_allocations", Tag.TAG_COMPOUND).getCompound(0).putLong("amount", 5L));
    }

    @Test
    void restoreRollsBackFacetReservationsWhenAReservationRestoreThrows() {
        PrefetchNetworkCapability first = new PrefetchNetworkCapability("test:first", 2L);
        PrefetchNetworkCapability second = new PrefetchNetworkCapability("test:second", 4L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetworks(first, second);
        MachineRecipe recipe = energyRecipe("runtime_flux_restore_exception", 3, 2);
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        CompoundTag output = new CompoundTag();
        runtime.save(output, EMPTY_LOOKUP);
        first.clearReservations();
        second.clearReservations();
        second.failRestore();

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
        assertThat(first.released()).isZero();
        assertThat(second.reserved()).isZero();
        assertThat(second.released()).isEqualTo(4L);
    }

    @Test
    void pattern_start_commits_prefetches_and_releases_prepared_reservations() {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = energyRecipe("runtime_flux_pattern", 3, 2);

        CraftingRuntime.PreparedStart prepared = runtime.preparePatternStart(recipe, 1, List.of());
        assertThat(prepared).isNotNull();
        assertThat(runtime.preparePatternStart(recipe, 1, List.of())).isNull();
        CraftingRuntime.PreparedStart stale = new CraftingRuntime.PreparedStart(prepared.recipe(), prepared.runtime(),
                prepared.effective(), prepared.plan(), prepared.prefetches());
        assertThat(runtime.reservePatternStart()).isTrue();
        assertThat(runtime.commitPatternStart(stale)).isFalse();
        runtime.releasePatternStart();
        assertThat(network.reserved()).isZero();

        prepared = runtime.preparePatternStart(recipe, 1, List.of());
        assertThat(runtime.reservePatternStart()).isTrue();
        assertThat(runtime.commitPatternStart(prepared)).isTrue();
        assertThat(network.extracted()).isEqualTo(6L);
    }

    @Test
    void pattern_start_rejects_a_plan_captured_before_modifier_change() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_stale_pattern_modifier", 20, List.of());
        CraftingRuntime.PreparedStart prepared = runtime.preparePatternStart(recipe, 1, List.of());
        assertThat(prepared).isNotNull();
        assertThat(runtime.reservePatternStart()).isTrue();

        controller.componentRuntime().replaceModifiers(Map.of("changed", List.of(
                MachineModifier.numeric("duration", "input", 2D, "multiply", false))));
        RuntimeTestFixtures.republish(controller);

        assertThat(runtime.commitPatternStart(prepared)).isFalse();
        assertThat(runtime.active()).isFalse();
    }

    @Test
    void prefetchCommitFailureRollsBackEarlierInputOperationsAndPreservesItsStatus() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L, true);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        List<ProcessingComponent> components = new ArrayList<>(controller.componentRuntime().components());
        components.add(new ProcessingComponent(null, new TickCapabilityHost(network), BlockPos.ZERO, BlockPos.ZERO,
                (String) null));
        controller.componentRuntime().replaceComponents(components);
        RuntimeTestFixtures.republish(controller);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_flux_commit_failure"), MMCR.id("test_cube"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.<MachineRequirement>of(input(Items.IRON_INGOT, 1), new EnergyRequirement(2)));

        runtime.start(recipe, 1);

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
        assertThat(network.reserved()).isZero();
    }

    @Test
    void energyOutputIsCommittedOnEveryRecipeTick() {
        EnergyOutputHatchBlockEntity energy = RuntimeTestFixtures.energyOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_energy_output_per_tick"),
                MMCR.id("test_cube"), 3, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(4L);
        runtime.tick();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(8L);
        runtime.tick();

        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(12L);
        assertThat(runtime.finishPending()).isTrue();
        runtime.finish();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(12L);
    }

    @Test
    void heatOutputIsCommittedOnEveryRecipeTick() {
        try (var requirementScope = RequirementHandlerRegistry.openTestScope();
             var outputScope = OutputRegistry.openTestScope()) {
            MekanismBridge bridge = MekanismBridgeBootstrap.selectForTesting(true);
            MekanismBridgeBootstrap.installForTesting(bridge);
            bridge.registerRecipeTypes(MekanismRecipeTypes.CHEMICAL, MekanismRecipeTypes.HEAT_TEMPERATURE,
                    MekanismRecipeTypes.HEAT);

            HeatOutputProbePort heat = new HeatOutputProbePort(new BlockPos(1, 0, 0));
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), heat);
            CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
            MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_heat_output_per_tick"),
                    MMCR.id("test_cube"), 3, List.of(LoadedHeatRequirement.outputHeat(5D)),
                    List.of(new LoadedHeatOutput(5D)), List.of(), 0, 1, false, false, false, Set.of());
            double initialHeat = heat.heatCapacitor().getHeat();

            assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
            runtime.tick();
            heat.heatCapacitor().update();
            assertThat(heat.heatCapacitor().getHeat()).isEqualTo(initialHeat + 5D);
            runtime.tick();
            heat.heatCapacitor().update();
            assertThat(heat.heatCapacitor().getHeat()).isEqualTo(initialHeat + 10D);
            runtime.tick();
            heat.heatCapacitor().update();

            assertThat(heat.heatCapacitor().getHeat()).isEqualTo(initialHeat + 15D);
            assertThat(runtime.finishPending()).isTrue();
            runtime.finish();
            assertThat(heat.heatCapacitor().getHeat()).isEqualTo(initialHeat + 15D);
        } finally {
            MekanismBridgeBootstrap.resetForTesting();
        }
    }

    @Test
    void afterInputsFailureDoesNotRepeatACommittedPerTickOutput() {
        EnergyOutputHatchBlockEntity energy = RuntimeTestFixtures.energyOutput(new BlockPos(1, 0, 0));
        FailOnceTickCapability capability = new FailOnceTickCapability(CapabilityTickPhase.AFTER_INPUTS, false);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, new TickCapabilityHost(capability), BlockPos.ZERO, BlockPos.ZERO,
                        (String) null),
                new ProcessingComponent(new MachineComponent(energy.kind(), energy.ioType()), energy,
                        energy.getBlockPos(), energy.getBlockPos(), (String) null)));
        RuntimeTestFixtures.republish(controller);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_after_inputs_failure"),
                MMCR.id("test_cube"), 2, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(4L);
        assertThat(runtime.tickCount()).isEqualTo(1);
        runtime.tick();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(8L);
        assertThat(runtime.finishPending()).isTrue();
    }

    @Test
    void finalAfterRecipeExceptionMovesToFinishWithoutRepeatingPerTickOutput() {
        EnergyOutputHatchBlockEntity energy = RuntimeTestFixtures.energyOutput(new BlockPos(1, 0, 0));
        FailOnceTickCapability capability = new FailOnceTickCapability(CapabilityTickPhase.AFTER_RECIPE, true);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, new TickCapabilityHost(capability), BlockPos.ZERO, BlockPos.ZERO,
                        (String) null),
                new ProcessingComponent(new MachineComponent(energy.kind(), energy.ioType()), energy,
                        energy.getBlockPos(), energy.getBlockPos(), (String) null)));
        RuntimeTestFixtures.republish(controller);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_final_after_recipe_failure"),
                MMCR.id("test_cube"), 1, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 4)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(4L);
        assertThat(runtime.finishPending()).isTrue();
        runtime.finish();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(4L);
    }

    @Test
    void missingPerTickEnergyReportsMissingEnergy() {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.energyStorage().setAmount(2);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_missing_energy"), MMCR.id("test_cube"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(new EnergyRequirement(2)));

        runtime.start(recipe, 1);
        runtime.tick();

        assertThat(runtime.failureUnloc()).isEqualTo("gui.mmcr.controller.failure.missing_energy");
    }

    @Test
    void modifier_changes_do_not_replace_an_active_effective_snapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_version_changes", 20, List.of());

        runtime.start(recipe, 1);
        controller.componentRuntime().replaceModifiers(Map.of("changed", List.of()));
        RuntimeTestFixtures.republish(controller);
        runtime.tick();
        assertThat(runtime.active()).isTrue();
        assertThat(runtime.failure()).isNull();
    }

    @Test
    void rebinding_versions_discards_an_active_recipe_when_the_machine_pool_changes() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = controllerRuntime(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_pool_rebind"), MMCR.id("test_cube"),
                20, List.of(), List.of());

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        ResourceLocation foreignMachineId = MMCR.id("runtime_foreign_pool_machine");
        RuntimeTestFixtures.registerRecipePool(foreignMachineId);
        controller.setMachine(new DynamicMachine(foreignMachineId, "foreign pool machine", new BlockArray(Map.of())));

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNull();
        assertThat(runtime.snapshot().status().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
        ControllerRuntimeSnapshot rebound = controller.currentRuntimeSnapshot();
        assertThat(rebound.crafting().recipeId()).isNull();
        assertThat(rebound.crafting().failure()).isNull();
        assertThat(rebound.recipePresentation()).isEqualTo(ControllerRecipePresentation.empty());
        assertThat(controller.currentRecipePoolId()).isEqualTo(foreignMachineId);

        runtime.rebindCurrentVersions();

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNull();
    }

    @Test
    void smart_interface_change_keeps_an_active_runtime_on_its_effective_snapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_smart_interface_change", 20, List.of());

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();

        runtime.invalidateForSmartInterfaceChange();

        assertThat(runtime.active()).isTrue();
        assertThat(runtime.failure()).isNull();
    }

    @Test
    void recipe_thread_keeps_active_runtime_when_smart_interface_changes() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.runtime().start(recipe("thread_smart_interface_change", 20, List.of()), 1)
                .isCrafting()).isTrue();

        thread.invalidateForSmartInterfaceChange();

        assertThat(thread.runtime().active()).isTrue();
        assertThat(thread.runtime().failure()).isNull();
    }

    @Test
    void smart_interface_output_change_during_finish_is_applied_after_the_commit() {
        SmartInterfaceBlockEntity smartInterface = (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get()
                .create(new BlockPos(1, 0, 0), ModBlocks.SMART_INTERFACE.get().defaultBlockState());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        smartInterface.setLevel(controller.getLevel());
        assertThat(smartInterface.claimController(controller.getBlockPos(), MMCR.id("test_cube"), Map.of(
                "mode", new SmartInterfaceType("mode", 1F, 0)), false)).isTrue();
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                null, smartInterface, smartInterface.getBlockPos(), smartInterface.getBlockPos(), (String) null)));
        CraftingRuntime runtime = controllerRuntime(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_smart_interface_output"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(SmartInterfaceRequirement.output("mode", 9F)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();

        assertThat(runtime.finish()).isEqualTo(CraftingStatus.IDLE);
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNull();
        assertThat(smartInterface.value("mode")).contains(9F);
    }

    @Test
    void zero_consume_chance_retains_the_input_across_start_and_tick() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_retain_input"), MMCR.id("test_cube"), 2,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY, 1F, List.of(),
                        DataComponentPredicateSet.EMPTY, 0F)));

        runtime.start(recipe, 1);
        runtime.tick();

        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
    }

    @Test
    void zeroChanceOutputDoesNotMutateStorageAtFinish() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_zero_chance", 1, List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        stack(Items.IRON_NUGGET, 2), 0F, List.of())));

        runtime.start(recipe, 1);
        runtime.tick();
        runtime.finish();

        assertThat(output.itemHandler().amount(0)).isZero();
    }

    @Test
    void positive_output_and_consume_chance_commit_through_real_item_storage() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        ItemRequirement consumed = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 1F);
        ItemRequirement produced = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                stack(Items.IRON_NUGGET, 1), 1F, List.of(), DataComponentPredicateSet.EMPTY, 1F);
        MachineRecipe recipe = recipe("runtime_positive_chance", 1, List.of(consumed, produced));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.itemHandler().amount(0)).isZero();
        runtime.tick();
        runtime.finish();

        assertThat(item(output.itemHandler(), 0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(output.itemHandler().amount(0)).isEqualTo(1L);
    }

    @Test
    void partialOutputCommitsAvailableStorageWithoutLeakingTheRemainder() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        for (int slot = 1; slot < output.itemHandler().size(); slot++) {
            setItem(output.itemHandler(), slot, stack(Items.COBBLESTONE, 64));
        }
        setItem(output.itemHandler(), 0, stack(Items.IRON_INGOT, 44));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_partial_output"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(output(Items.IRON_INGOT, 64)), false, List.of(), true);

        runtime.start(recipe, 1);
        runtime.tick();
        runtime.finish();

        assertThat(output.itemHandler().amount(0)).isEqualTo(64L);
    }

    @Test
    void blocked_finish_stays_pending_until_the_output_retry_window_opens() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        var level = LevelStub.createWithBlockEntities(List.of(controller, output));
        controller.setLevel(level);
        output.setLevel(level);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_finish_retry", 1, List.of(output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        for (int slot = 0; slot < output.itemHandler().size(); slot++) {
            setItem(output.itemHandler(), slot, stack(Items.COBBLESTONE, 64));
        }
        runtime.tick();

        assertThat(runtime.finishPending()).isTrue();
        assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.NO_RECIPE);
        assertThat(runtime.active()).isTrue();
        assertThat(runtime.shouldRetryFinish()).isFalse();

        setItem(output.itemHandler(), 0, ItemStack.EMPTY);
        LevelStub.setGameTime(level, 10);

        assertThat(runtime.shouldRetryFinish()).isTrue();
        assertThat(runtime.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
        assertThat(item(output.itemHandler(), 0).is(Items.IRON_NUGGET)).isTrue();
    }

    @Test
    void active_runtime_persists_finish_state_and_input_plan() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_persisted", 1, List.of());
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        runtime.start(recipe, 1);
        runtime.tick();
        CompoundTag output = new CompoundTag();
        runtime.save(output, EMPTY_LOOKUP);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.active()).isTrue();
        assertThat(restored.finishPending()).isTrue();
        assertThat(restored.recipe()).isEqualTo(recipe);
        assertThat(restored.activeRecipe().inputConsumptionPlan().consumedBatches(0)).isZero();
    }

    @Test
    void active_runtime_persists_finish_failure_reason() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        for (int slot = 0; slot < output.itemHandler().size(); slot++) {
            setItem(output.itemHandler(), slot, stack(Items.COBBLESTONE, 64));
        }
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        MachineRecipe recipe = recipe("runtime_persisted_finish_failure", 1,
                List.of(output(Items.IRON_NUGGET, 1)));
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(runtime.failureUnloc()).isEqualTo("gui.mmcr.controller.failure.missing_output");
        CompoundTag outputTag = new CompoundTag();
        runtime.save(outputTag, EMPTY_LOOKUP);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(outputTag, null, EMPTY_LOOKUP);

        assertThat(restored.failureUnloc()).isEqualTo("gui.mmcr.controller.failure.missing_output");
    }

    @Test
    void active_runtime_restores_a_registered_custom_failure_translation_key() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        ExecutionStatus failure = ExecutionStatus.blocked(MMCR.id("runtime_custom_failure"),
                MMCR.id("runtime_custom_source"), FailureOccurrence.at(REGISTERED_RUNTIME_REASON,
                        MMCR.id("runtime_custom_source"), FailurePhase.RUNTIME, null, null, Map.of()));

        saved.recordSearchFailure(failure);
        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.failure().reason()).isEqualTo(REGISTERED_RUNTIME_REASON);
        assertThat(restored.failureUnloc()).isEqualTo(REGISTERED_RUNTIME_REASON.translationKey());
    }

    @Test
    void active_runtime_migrates_legacy_failure_reason_when_typed_failure_is_absent() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        for (int slot = 0; slot < output.itemHandler().size(); slot++) {
            setItem(output.itemHandler(), slot, stack(Items.COBBLESTONE, 64));
        }
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        MachineRecipe recipe = recipe("runtime_legacy_finish_failure", 1,
                List.of(output(Items.IRON_NUGGET, 1)));
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();
        saved.tick();
        saved.finish();

        CompoundTag outputTag = new CompoundTag();
        saved.save(outputTag, EMPTY_LOOKUP);
        CompoundTag legacy = outputTag;
        legacy.remove("failure");
        legacy.putBoolean("has_failure", true);
        legacy.putString("failure_reason", "no_output_capacity");

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(legacy, null, EMPTY_LOOKUP);

        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        assertThat(restored.failure().failure().trace().frames().getFirst().phase()).isEqualTo(FailurePhase.FINISH);
        assertThat(restored.failure().details()).doesNotContainKey("raw_reason_id");
    }

    @Test
    void inactive_runtime_migrates_unknown_legacy_failure_with_raw_reason_id() {
        CompoundTag output = new CompoundTag();
        output.putBoolean("active", false);
        output.putBoolean("has_failure", true);
        output.putString("failure_reason", "legacy:removed_reason");

        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.UNKNOWN);
        assertThat(restored.failure().details()).containsEntry("raw_reason_id", "legacy:removed_reason");
    }

    @Test
    void inactive_runtime_clears_malformed_legacy_failure_reason() {
        CompoundTag output = new CompoundTag();
        output.putBoolean("active", false);
        output.putBoolean("has_failure", true);
        output.putString("failure_reason", "not a valid identifier");

        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.failure()).isNull();
    }

    @Test
    void active_runtime_persists_the_start_effective_snapshot_and_consumption_plan() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        AtomicInteger starts = new AtomicInteger();
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> {
                    starts.incrementAndGet();
                    context.setDuration(2);
                    context.setRequirements(MachineRequirement
                            .copyList(List.of(input(Items.IRON_INGOT, 1), output(Items.GOLD_NUGGET, 2))));
                }).build()));
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        MachineRecipe recipe = recipe("runtime_persisted_effective_snapshot", 20, List.of(
                input(Items.IRON_INGOT, 1), output(Items.IRON_NUGGET, 1), output(Items.DIAMOND, 1)));
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();
        CompoundTag outputTag = new CompoundTag();
        saved.save(outputTag, EMPTY_LOOKUP);
        CompoundTag savedRecipeTag = outputTag.getCompound("recipe");
        assertThat(savedRecipeTag.getBoolean("has_effective_definition")).isTrue();
        savedRecipeTag.putInt("totalTick", 99);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(outputTag, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isTrue();
        assertThat(starts).hasValue(1);
        assertThat(restored.totalTick()).isEqualTo(2);
        assertThat(restored.activeRecipe().inputConsumptionPlan().consumedBatches(0)).isEqualTo(1);
        assertThat(restored.activeRecipe().inputConsumptionPlan().consumedBatches(1)).isZero();
        restored.tick();
        restored.tick();
        restored.finish();

        assertThat(item(output.itemHandler(), 0).is(Items.GOLD_NUGGET)).isTrue();
        assertThat(output.itemHandler().amount(0)).isEqualTo(2L);
    }

    @Test
    void active_runtime_rejects_the_legacy_effective_execution_snapshot_alias() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        AtomicInteger starts = new AtomicInteger();
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> {
                    starts.incrementAndGet();
                    context.setDuration(2);
                    context.setRequirements(MachineRequirement
                            .copyList(List.of(input(Items.IRON_INGOT, 2))));
                }).build()));
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 2));
        MachineRecipe recipe = recipe("runtime_legacy_effective_snapshot", 20,
                List.of(input(Items.IRON_INGOT, 1)));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();
        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        CompoundTag root = output;
        CompoundTag recipeTag = root.getCompound("recipe");
        recipeTag.remove("has_effective_definition");
        recipeTag.remove("effective_definition_version");
        recipeTag.putBoolean("has_effective_execution_snapshot", true);
        recipeTag.putInt("effective_execution_snapshot_version", 1);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(root, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
    }

    @Test
    void active_runtime_ignores_legacy_effective_snapshot_aliases() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_conflicting_effective_snapshot", 20, List.of());
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();
        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        CompoundTag root = output;
        CompoundTag recipeTag = root.getCompound("recipe");
        recipeTag.putBoolean("has_effective_execution_snapshot", true);
        recipeTag.putInt("effective_execution_snapshot_version", 2);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(root, null, EMPTY_LOOKUP);

        assertThat(restored.active()).isTrue();
    }

    @Test
    void active_runtime_rejects_effective_snapshot_payload_without_a_marker() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_unmarked_effective_snapshot", 20, List.of());
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();
        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        CompoundTag root = output;
        CompoundTag recipeTag = root.getCompound("recipe");
        recipeTag.remove("has_effective_definition");
        recipeTag.remove("effective_definition_version");

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(root, null, EMPTY_LOOKUP);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
    }

    @Test
    void active_runtime_rejects_malformed_activity_state_before_restore() {
        assertMalformedActivityState("runtime_invalid_total_tick",
                recipeTag -> recipeTag.putInt("totalTick", 0));
        assertMalformedActivityState("runtime_invalid_tick",
                recipeTag -> recipeTag.putInt("tick", 21));
        assertMalformedActivityState("runtime_invalid_max_parallelism",
                recipeTag -> recipeTag.putInt("maxParallelism", 0));
        assertMalformedActivityState("runtime_invalid_parallelism_zero",
                recipeTag -> recipeTag.putInt("parallelism", 0));
        assertMalformedActivityState("runtime_invalid_parallelism",
                recipeTag -> recipeTag.putInt("parallelism", 2));
        assertMalformedActivityState("runtime_invalid_finish_pending",
                recipeTag -> recipeTag.putBoolean("finishPending", true));
    }

    @Test
    void active_runtime_rejects_effective_snapshot_without_consumption_plan() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        MachineRecipe recipe = recipe("runtime_missing_effective_plan", 20, List.of(
                input(Items.IRON_INGOT, 1)));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();
        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        var savedRecipeTag = output.getCompound("recipe");
        savedRecipeTag.remove("has_input_consumption_plan");
        savedRecipeTag.remove("inputConsumptionPlan");

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
    }

    @Test
    void active_runtime_rejects_an_invalid_effective_requirement_before_restore() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        MachineRecipe recipe = recipe("runtime_invalid_effective_requirement", 20, List.of(
                input(Items.IRON_INGOT, 1)));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        CompoundTag recipeTag = output.getCompound("recipe");
        ListTag requirements = recipeTag.getList("effective_requirements", Tag.TAG_COMPOUND);
        requirements.getCompound(0).putInt("count", -1);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
    }

    @Test
    void active_runtime_finishes_with_its_original_recipe_after_registry_replacement() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        MachineRecipe oldRecipe = recipe("runtime_reload_active", 1, List.of(
                input(Items.IRON_INGOT, 1), output(Items.IRON_NUGGET, 1)));
        MachineRecipe replacement = recipe("runtime_reload_active", 1, List.of(
                input(Items.GOLD_INGOT, 1), output(Items.DIAMOND, 1)));
        RecipeRegistry.replaceDynamic(Map.of(oldRecipe.id(), oldRecipe));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(oldRecipe, 1).isCrafting()).isTrue();
        RecipeRegistry.replaceDynamic(Map.of(replacement.id(), replacement));

        runtime.tick();
        runtime.finish();

        assertThat(runtime.active()).isFalse();
        assertThat(item(output.itemHandler(), 0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(item(output.itemHandler(), 0).is(Items.DIAMOND)).isFalse();
    }

    @Test
    void active_runtime_rejects_an_embedded_definition_replaced_in_the_current_catalog() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        MachineRecipe oldRecipe = recipe("runtime_reload_persisted", 1, List.of(
                input(Items.IRON_INGOT, 1), output(Items.IRON_NUGGET, 1)));
        MachineRecipe replacement = recipe("runtime_reload_persisted", 1, List.of(
                input(Items.IRON_INGOT, 1), output(Items.DIAMOND, 1)));
        RecipeRegistry.replaceDynamic(Map.of(oldRecipe.id(), oldRecipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(oldRecipe, 1).isCrafting()).isTrue();

        CompoundTag outputTag = new CompoundTag();
        saved.save(outputTag, EMPTY_LOOKUP);
        var savedRecipeTag = outputTag.getCompound("recipe");
        assertThat(savedRecipeTag.getBoolean("has_recipe_definition")).isTrue();
        assertThat(savedRecipeTag.getCompound("recipe_definition").isEmpty()).isFalse();
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        RecipeRegistry.replaceDynamic(Map.of(replacement.id(), replacement));

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(outputTag, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
        assertThat(item(output.itemHandler(), 0).isEmpty()).isTrue();
    }

    @Test
    void active_runtime_loads_old_nbt_without_an_embedded_definition_from_the_registry() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_old_nbt", 20, List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        var savedRecipeTag = output.getCompound("recipe");
        savedRecipeTag.remove("has_recipe_definition");
        savedRecipeTag.remove("recipe_definition");
        savedRecipeTag.remove("recipe_definition_version");
        savedRecipeTag.remove("recipe_definition_fingerprint");
        savedRecipeTag.remove("has_effective_definition");
        savedRecipeTag.remove("effective_definition_version");
        savedRecipeTag.remove("effective_duration");
        savedRecipeTag.remove("effective_requirements");
        savedRecipeTag.remove("effective_outputs");

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.active()).isTrue();
        assertThat(restored.recipe()).isEqualTo(recipe);
        assertThat(restored.activeRecipe().hasEffectiveExecutionSnapshot()).isTrue();
    }

    @Test
    void active_runtime_round_trips_a_registered_custom_requirement_handler() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(CustomRequirement.TYPE);
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
            MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("runtime_custom_requirement"), MMCR.id("test_cube"), 2,
                    List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                    List.of(new CustomRequirement(RecipeModifier.IOType.INPUT, 7)));
            RecipeRegistry.registerStatic(recipe);
            CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());

            assertThat(saved.start(recipe, 1).isCrafting()).isTrue();
            CompoundTag output = new CompoundTag();
            saved.save(output, EMPTY_LOOKUP);

            CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
            restored.load(output, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

            assertThat(restored.active()).isTrue();
            assertThat(restored.activeRecipe().effectiveRequirements()).singleElement()
                    .isEqualTo(new CustomRequirement(RecipeModifier.IOType.INPUT, 7));
            restored.tick();
            restored.tick();
            assertThat(restored.finish().getStatus()).isEqualTo(CraftingStatus.Status.IDLE);
        }
    }

    @Test
    void legacy_restore_promotes_runtime_modifier_values_to_the_public_effective_snapshot() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        AtomicReference<String> callbackFailure = new AtomicReference<>();
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .recipeTick(context -> {
                    if (context.totalTick() != 2
                            || ((cn.howxu.mmcr.api.recipe.requirement.ItemRequirement)
                            context.requirements().getFirst()).count() != 1
                            || ((MachineOutput.ItemOutput) context.outputs().getFirst()).stack().getCount() != 2) {
                        callbackFailure.compareAndSet(null, "legacy restore did not expose the effective snapshot");
                    }
                }).build()));
        controller.componentRuntime().replaceModifiers(Map.of("runtime", List.of(
                MachineModifier.numeric("duration", "input", 2D, "multiply", false),
                MachineModifier.numeric("output", "output", 2D, "multiply", false))));
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        MachineRecipe recipe = recipe("runtime_legacy_modifier_restore", 1,
                List.of(input(Items.IRON_INGOT, 1), output(Items.GOLD_NUGGET, 1)));
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();
        assertThat(ControllerRecipePresentation.from(saved)).satisfies(presentation -> {
            assertThat(presentation.durationTicks()).isEqualTo(2);
            assertThat(presentation.parallelism()).isEqualTo(1L);
        });

        CompoundTag outputTag = new CompoundTag();
        saved.save(outputTag, EMPTY_LOOKUP);
        CompoundTag root = outputTag;
        CompoundTag recipeTag = root.getCompound("recipe");
        recipeTag.remove("has_effective_definition");
        recipeTag.remove("effective_definition_version");
        recipeTag.remove("effective_duration");
        recipeTag.remove("effective_requirements");
        recipeTag.remove("effective_outputs");
        recipeTag.putInt("totalTick", 1);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(root, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isTrue();
        assertThat(restored.activeRecipe().hasEffectiveExecutionSnapshot()).isTrue();
        assertThat(restored.totalTick()).isEqualTo(2);
        assertThat(((ItemRequirement) restored.activeRecipe().effectiveRequirements().getFirst()).count()).isEqualTo(1);
        assertThat(((MachineOutput.ItemOutput) restored.activeRecipe().effectiveOutputs().getFirst()).stack().getCount())
                .isEqualTo(2);
        restored.tick();
        restored.tick();
        restored.finish();

        assertThat(callbackFailure).hasValue(null);
        assertThat(item(output.itemHandler(), 0).is(Items.GOLD_NUGGET)).isTrue();
        assertThat(output.itemHandler().amount(0)).isEqualTo(2L);
    }

    @Test
    void legacy_restore_rejects_progress_beyond_modifier_effective_duration() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.componentRuntime().replaceModifiers(Map.of("runtime", List.of(
                MachineModifier.numeric("duration", "input", 0.5D, "multiply", false))));
        MachineRecipe recipe = recipe("runtime_legacy_invalid_progress", 2, List.of());
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();
        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        CompoundTag root = output;
        CompoundTag recipeTag = root.getCompound("recipe");
        recipeTag.remove("has_effective_definition");
        recipeTag.remove("effective_definition_version");
        recipeTag.remove("effective_duration");
        recipeTag.remove("effective_requirements");
        recipeTag.remove("effective_outputs");
        recipeTag.putInt("totalTick", 2);
        recipeTag.putInt("tick", 2);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(root, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
    }

    @Test
    void invalid_embedded_definition_loads_as_a_failed_idle_runtime_without_resource_operations() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_invalid_embedded", 20, List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        var savedRecipeTag = output.getCompound("recipe");
        savedRecipeTag.remove("recipe_definition");

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
    }

    @Test
    void corrupted_embedded_definition_fingerprint_loads_as_a_failed_idle_runtime() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_corrupt_fingerprint", 20, List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        var savedRecipeTag = output.getCompound("recipe");
        savedRecipeTag.putString("recipe_definition_fingerprint", "0".repeat(64));

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
    }

    @Test
    void malformed_input_consumption_plan_fails_before_restore_and_resource_operations() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));
        MachineRecipe recipe = recipe("runtime_malformed_plan", 20, List.of(
                input(Items.IRON_INGOT, 1), output(Items.IRON_NUGGET, 1)));
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        CompoundTag outputTag = new CompoundTag();
        saved.save(outputTag, EMPTY_LOOKUP);
        var savedRecipeTag = outputTag.getCompound("recipe");
        CompoundTag malformedPlan = new CompoundTag();
        malformedPlan.putIntArray("consumedInputBatches", new int[]{1});
        savedRecipeTag.put("inputConsumptionPlan", malformedPlan);
        setItem(input.itemHandler(), 0, stack(Items.IRON_INGOT, 1));

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(outputTag, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
        assertThat(input.itemHandler().amount(0)).isEqualTo(1L);
    }

    @Test
    void modified_embedded_recipe_definition_is_rejected_by_its_fingerprint() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_modified_definition", 20, List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        var recipeTag = output.getCompound("recipe");
        assertThat(recipeTag.getString("recipe_definition_fingerprint"))
                .matches("[0-9a-f]{64}");
        recipeTag.getCompound("recipe_definition").putInt("tick_time", 99);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
    }

    private static void assertMalformedActivityState(String recipePath,
                                                      Consumer<CompoundTag> mutate) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe(recipePath, 20, List.of()), 1).isCrafting()).isTrue();

        CompoundTag output = new CompoundTag();
        saved.save(output, EMPTY_LOOKUP);
        CompoundTag recipeTag = output.getCompound("recipe");
        mutate.accept(recipeTag);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, EMPTY_LOOKUP);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
    }

    private static MachineRecipe recipe(String path, int duration, List<ItemRequirement> requirements) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"), duration,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), new ArrayList<>(requirements));
    }

    private static void assertFinishRetryGate(MachineBehavior behavior, AtomicInteger callbacks) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setMachine(machine(controller.machineId(), behavior));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("runtime_finish_callback_retry", 1, List.of()), 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.finish();
        assertThat(callbacks).hasValue(1);
        assertThat(runtime.shouldRetryFinish()).isFalse();

        runtime.finish();
        assertThat(callbacks).hasValue(1);

        LevelStub.setGameTime(controller.getLevel(), 10);
        runtime.finish();
        assertThat(callbacks).hasValue(2);
        assertThat(runtime.shouldRetryFinish()).isFalse();
    }

    private static Machine machine(ResourceLocation id, MachineBehavior behavior) {
        return new DynamicMachine(id, id.toString(), new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(id), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1, List.of(), MachineRole.NORMAL,
                Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), behavior);
    }

    private static ItemRequirement input(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY);
    }

    private static ItemRequirement output(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack(item, count));
    }

    private static ItemStack stack(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static void setItem(LongItemStorage storage, int slot, ItemStack stack) {
        storage.setContents(slot, stack, stack.getCount());
    }

    private static void assertPrefetchRestoreFails(String recipePath, Consumer<CompoundTag> mutation) {
        PrefetchNetworkCapability network = new PrefetchNetworkCapability(6L);
        MachineControllerBlockEntity controller = controllerWithPrefetchNetwork(network);
        MachineRecipe recipe = energyRecipe(recipePath, 3, 2);
        RecipeRegistry.registerStatic(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        CompoundTag output = new CompoundTag();
        runtime.save(output, EMPTY_LOOKUP);
        CompoundTag data = output.getCompound("recipe").getCompound("data");
        mutation.accept(data);
        network.clearReservations();

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(output, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
        assertThat(network.reserved()).isZero();
        assertThat(network.released()).isZero();
    }

    private static ItemStack item(LongItemStorage storage, int slot) {
        return storage.getStackInSlot(slot);
    }

    private static CraftingRuntime controllerRuntime(MachineControllerBlockEntity controller) {
        try {
            Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return ((MachineControllerRuntime) field.get(controller)).craftingRuntime();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access controller runtime", exception);
        }
    }

    private static MachineControllerBlockEntity controllerWithPrefetchNetwork(PrefetchNetworkCapability network) {
        return controllerWithPrefetchNetworks(network);
    }

    private static MachineControllerBlockEntity controllerWithPrefetchNetworks(PrefetchNetworkCapability... networks) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        List<ProcessingComponent> components = new ArrayList<>();
        for (int index = 0; index < networks.length; index++) {
            components.add(new ProcessingComponent(null, new TickCapabilityHost(networks[index]),
                    new BlockPos(index, 0, 0), new BlockPos(index, 0, 0), (String) null));
        }
        controller.componentRuntime().replaceComponents(components);
        RuntimeTestFixtures.republish(controller);
        return controller;
    }

    private static MachineRecipe energyRecipe(String path, int duration, long energyPerTick) {
        return energyRecipe(path, duration, energyPerTick, 1);
    }

    private static MachineRecipe energyRecipe(String path, int duration, long energyPerTick, int maxParallelism) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"), duration,
                List.of(), List.of(), List.of(), 0, maxParallelism, false, List.of(),
                List.of(new EnergyRequirement(energyPerTick)));
    }

    private static final class HeatOutputProbePort extends HeatPortBlockEntity {
        private final BasicHeatCapacitor testHeatCapacitor;

        private HeatOutputProbePort(BlockPos pos) {
            super(ModBlockEntities.BES.get("item_output_bus").get(), pos,
                    ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState(), PortKinds.ITEM_OUTPUT);
            testHeatCapacitor = BasicHeatCapacitor.create(300D, () -> 0D, () -> { });
        }

        @Override
        public IOType ioType() {
            return IOType.OUTPUT;
        }

        @Override
        public IOPortKind kind() {
            return PortKinds.ITEM_OUTPUT;
        }

        @Override
        public BasicHeatCapacitor heatCapacitor() {
            return testHeatCapacitor;
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of(new HeatPortCapability(heatCapacitor(), IOType.OUTPUT)));
        }
    }

    private static final class FailOnceTickCapability implements MachineCapability, TickFacet {
        private final CapabilityTickPhase failurePhase;
        private final boolean throwException;
        private final AtomicBoolean failNext = new AtomicBoolean(true);
        private final CapabilityType type = new CapabilityType(MMCR.id("runtime_phase_failure"));
        private final CapabilityView view = new CapabilityView() {
            @Override
            public CapabilityType type() {
                return FailOnceTickCapability.this.type;
            }

            @Override
            public CapabilityDirections directions() {
                return CapabilityDirections.input();
            }

            @Override
            public Set<Class<? extends CapabilityFacet>> facets() {
                return Set.of(TickFacet.class);
            }
        };

        private FailOnceTickCapability(CapabilityTickPhase failurePhase, boolean throwException) {
            this.failurePhase = failurePhase;
            this.throwException = throwException;
        }

        @Override
        public CapabilityType type() {
            return type;
        }

        @Override
        public CapabilityView view() {
            return view;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return CapabilityResult::successful;
        }

        @Override
        public CapabilityTickResult plan(CapabilityTickContext context) {
            if (context.phase() == failurePhase && failNext.compareAndSet(true, false)) {
                if (throwException) throw new IllegalStateException("expected tick phase exception");
                return new CapabilityTickResult(List.of(), ExecutionStatus.blocked(MMCR.id("runtime_phase_failure"), type.id(),
                        FailureOccurrence.at(BuiltinFailureReasons.PER_TICK, type.id(), FailurePhase.PER_TICK,
                                null, null, Map.of())), false);
            }
            return CapabilityTickResult.empty();
        }
    }

    private static final class TickCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;

        private TickCapabilityHost(MachineCapability capability) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            snapshot = new CapabilitySnapshot(List.of(capability));
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return snapshot;
        }
    }

    private static final class PrefetchNetworkCapability implements MachineCapability, RecipeEnergyPrefetchFacet {
        private final LongValueStorage storage;
        private final String reservationKey;
        private final CapabilityType type = new CapabilityType(MMCR.id("test_prefetch_network"));
        private final CapabilityView view = new CapabilityView() {
            @Override
            public CapabilityType type() {
                return PrefetchNetworkCapability.this.type;
            }

            @Override
            public CapabilityDirections directions() {
                return CapabilityDirections.input();
            }

            @Override
            public Set<Class<? extends CapabilityFacet>> facets() {
                return Set.of(RecipeEnergyPrefetchFacet.class);
            }
        };
        private long planned;
        private long reserved;
        private long released;
        private long releaseCalls;
        private long extractionBaseline;
        private final boolean failCommit;
        private boolean failRestore;
        private boolean failRelease;

        private PrefetchNetworkCapability(long available) {
            this("test:prefetch", available, false);
        }

        private PrefetchNetworkCapability(long available, boolean failCommit) {
            this("test:prefetch", available, failCommit);
        }

        private PrefetchNetworkCapability(String reservationKey, long available) {
            this(reservationKey, available, false);
        }

        private PrefetchNetworkCapability(String reservationKey, long available, boolean failCommit) {
            storage = new LongValueStorage(available, available, null);
            storage.setAmount(available);
            this.reservationKey = reservationKey;
            extractionBaseline = available;
            this.failCommit = failCommit;
        }

        @Override
        public String reservationKey() {
            return reservationKey;
        }

        @Override
        public Optional<PrefetchPlan> planPrefetch(long requestedAmount) {
            long accepted = Math.min(Math.max(0L, requestedAmount), Math.max(0L, storage.amount() - planned));
            if (accepted <= 0L) return Optional.empty();
            planned += accepted;
            return Optional.of(new PrefetchPlan(accepted, () -> {
                long extracted = storage.extract(accepted, false);
                if (extracted != accepted) return CapabilityResult.failure(ExecutionStatus.blocked(type.id(), type.id(),
                        FailureOccurrence.at(BuiltinFailureReasons.MISSING_INPUT, type.id(),
                                FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
                if (failCommit) return CapabilityResult.failure(ExecutionStatus.blocked(type.id(), type.id(),
                        FailureOccurrence.at(BuiltinFailureReasons.MISSING_ENERGY, type.id(),
                                FailurePhase.CAPABILITY_COMMIT, null, null, Map.of())));
                planned -= accepted;
                reserved += accepted;
                return CapabilityResult.successful();
            }));
        }

        @Override
        public void restoreReservation(long amount) {
            long restored = Math.max(0L, amount);
            if (failRestore) {
                reserved += restored;
                throw new IllegalStateException("expected restore failure");
            }
            if (planned >= restored) {
                planned -= restored;
            } else {
                reserved += restored;
            }
        }

        @Override
        public long releaseReservation(long amount) {
            releaseCalls++;
            if (failRelease) throw new IllegalStateException("expected release failure");
            long result = Math.min(Math.max(0L, amount), reserved);
            reserved -= result;
            released += result;
            return result;
        }

        @Override
        public CapabilityType type() {
            return type;
        }

        @Override
        public CapabilityView view() {
            return view;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            throw new UnsupportedOperationException("prefetch network has no generic operations");
        }

        private long extracted() {
            return extractionBaseline - storage.amount();
        }

        private long reserved() {
            return reserved;
        }

        private long released() {
            return released;
        }

        private long releaseCalls() {
            return releaseCalls;
        }

        private void resetExtractionCounter() {
            extractionBaseline = storage.amount();
        }

        private void clearReservations() {
            planned = 0L;
            reserved = 0L;
            released = 0L;
        }

        private void failRestore() {
            failRestore = true;
        }

        private void failRelease() {
            failRelease = true;
        }
    }

    private record CustomRequirement(RecipeModifier.IOType io, int value) implements MachineRequirement {
        private static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(
                "mmcr_test", "runtime_custom_requirement");
        private static final MapCodec<CustomRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(value -> TYPE_ID.toString()),
                RecipeModifier.IO_TYPE_CODEC.fieldOf("io").forGetter(CustomRequirement::io),
                Codec.INT.fieldOf("value").forGetter(CustomRequirement::value)
        ).apply(instance, (ignored, io, value) -> new CustomRequirement(io, value)));
        private static final RequirementHandler<CustomRequirement> HANDLER =
                (requirement, capabilities, context) -> new RequirementPlan(
                        context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        private static final RequirementType<CustomRequirement> TYPE = new RequirementType.Definition<>(
                TYPE_ID, CODEC, HANDLER);

        @Override
        public RequirementType<CustomRequirement> type() {
            return TYPE;
        }
    }

    private static final class ThrowingItemOutputBus extends ItemOutputBusBlockEntity {
        private final ThrowingItemStorage storage = new ThrowingItemStorage();

        private ThrowingItemOutputBus(BlockPos pos) {
            super(pos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        }

        @Override
        public ThrowingItemStorage itemHandler() {
            return storage;
        }

        @Override
        public ThrowingItemStorage nativeItemHandler() {
            return storage;
        }
    }

    private static final class ThrowingItemStorage extends LongItemStorage {
        private ThrowingItemStorage() {
            super(1, 100L, () -> {});
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            throw new IllegalStateException("expected finish capability operation failure");
        }
    }
}
