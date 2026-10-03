package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputCapability;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputBlockEntity;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPointHandler;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPlugHandler;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkOutputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.data.FluxDeviceConfigComponent;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Real CraftingRuntime settlement over native Point inventories.
 * @author howxu <dev@howxu.cn>
 */
class FluxNetworksRuntimeLifecycleTest {
    private static final HolderLookup.Provider EMPTY_LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrap() throws Exception {
        FluxNetworksTestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void synchronousRuntimeConsumesLocalInventoryThroughFinishAndCancellation() {
        Point point = point("sync", 50L);
        Rig rig = rig(point.capability());
        assertThat(rig.runtime().start(recipe("sync_finish", 5, 10L), 1L).isCrafting()).isTrue();
        assertBalances(point, 50L, 50L);
        rig.runtime().tick();
        assertBalances(point, 40L, 40L);
        assertThat(allocation(rig.runtime(), 0)).isEqualTo(40L);
        for (int tick = 1; tick < 5; tick++) rig.runtime().tick();
        assertThat(rig.runtime().finishPending()).isTrue();
        assertBalances(point, 0L, 0L);
        rig.runtime().finish();
        assertThat(rig.runtime().active()).isFalse();

        Point cancelled = point("cancel", 50L);
        Rig cancelRig = rig(cancelled.capability());
        cancelRig.runtime().start(recipe("sync_cancel", 5, 10L), 1L);
        cancelRig.runtime().tick();
        cancelRig.runtime().invalidate();
        cancelRig.runtime().invalidate();
        assertBalances(cancelled, 40L, 0L);
        assertThat(cancelled.handler().availableForPrefetch()).isEqualTo(40L);
    }

    @Test
    void asynchronousEmptyPlanAndMainThreadFallbackUseTheSameLocalSettlement() {
        for (boolean fallback : List.of(false, true)) {
            Point point = point("async_" + fallback, 6L);
            Rig rig = rig(point.capability());
            rig.runtime().start(recipe("async_" + fallback, 3, 2L), 1L);
            for (int tick = 0; tick < 3; tick++) {
                asyncTick(rig, fallback);
                assertBalances(point, 6L - (tick + 1) * 2L, 6L - (tick + 1) * 2L);
            }
            assertThat(rig.runtime().finishPending()).isTrue();
            rig.runtime().finish();
            assertThat(rig.runtime().active()).isFalse();
        }
    }

    @Test
    void appliedFluxRuntimeAlsoSettlesItsExistingLocalReservationExactlyOnce() {
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        long[] extracted = {0L};
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer, "appflux",
                requested -> Optional.of(() -> {
                    extracted[0] += requested;
                    return CapabilityResult.successful();
                }));
        Rig rig = rig(capability);
        rig.runtime().start(recipe("appflux", 3, 2L), 1L);
        assertThat(extracted[0]).isEqualTo(6L);
        assertThat(buffer.amount()).isEqualTo(6L);
        rig.runtime().tick();
        asyncTick(rig, false);
        assertThat(buffer.amount()).isEqualTo(2L);
        assertThat(buffer.reserved()).isEqualTo(2L);
        assertThat(extracted[0]).isEqualTo(6L);
        rig.runtime().invalidate();
        assertThat(buffer.amount()).isEqualTo(2L);
        assertThat(buffer.reserved()).isZero();
    }

    @Test
    void actualSearchWithdrawsAllAssociatedPointHintsWhenTheSmallerRecipeActivates() {
        Point first = point("search_first", 100L);
        Point unused = point("search_unused", 0L);
        Rig rig = rig(first.capability(), unused.capability());
        MachineRecipe large = recipe("search_large", 2, 100L, 0);
        MachineRecipe small = recipe("search_small", 2, 50L, 1);
        var state = rig.controller().currentRuntimeSnapshot();
        var found = new RecipeSearchTask(state, MMCR.id("test_cube"), MMCR.id("test_cube"),
                state.structure().version(), 1L, List.of(large, small),
                rig.controller().componentRuntime().capabilities()).compute();
        assertThat(found.success()).isTrue();
        assertThat(found.recipe()).isSameAs(small);
        first.handler().onCycleStart();
        unused.handler().onCycleStart();
        assertThat(first.handler().getRequest()).isPositive();
        assertThat(unused.handler().getRequest()).isPositive();

        assertThat(rig.runtime().start(found.recipe(), 1L).isCrafting()).isTrue();
        first.handler().onCycleStart();
        unused.handler().onCycleStart();
        assertThat(first.handler().getRequest()).isZero();
        assertThat(unused.handler().getRequest()).isZero();
        assertBalances(first, 100L, 100L);
        assertBalances(unused, 0L, 0L);

        rig.runtime().invalidate();
        first.handler().requestWarmup(1000L);
        first.handler().onCycleStart();
        // Activation must not reset the 100 FE already received during this same tick.
        assertThat(first.handler().getRequest()).isEqualTo(900L);
    }

    @Test
    void settingsPasteThenImmediateSaveRestoresOwnershipAndContinuesConsumption() {
        Point original = point("paste", 50L);
        Rig rig = rig(original.capability());
        MachineRecipe recipe = recipe("paste_restore", 5, 10L);
        RecipeRegistry.registerStatic(recipe);
        rig.runtime().start(recipe, 1L);
        original.handler().applyConfiguration(FluxDeviceConfigComponent.EMPTY, original.handler().getBuffer());
        assertThat(rig.runtime().active()).isTrue();
        assertBalances(original, 50L, 50L);
        CompoundTag runtimeTag = save(rig.runtime());
        Point restored = restorePoint("paste", original);
        assertBalances(restored, 50L, 50L);
        Rig restoredRig = rig(restored.capability());
        load(restoredRig.runtime(), runtimeTag);
        assertThat(restoredRig.runtime().active()).isTrue();
        restored.handler().reconcileRecipeOwners();
        assertBalances(restored, 50L, 50L);
        restoredRig.runtime().tick();
        assertBalances(restored, 40L, 40L);
        restoredRig.runtime().invalidate();
        assertBalances(restored, 40L, 0L);
    }

    @Test
    void inventoryReplacementCancelsItsActualRuntimeOwnerBeforeReplacingTheBuffer() {
        Point point = point("replace", 50L);
        Rig rig = rig(point.capability());
        rig.runtime().start(recipe("replace", 5, 10L), 1L);
        point.handler().applyConfiguration(FluxDeviceConfigComponent.EMPTY, 30L);
        assertThat(rig.runtime().active()).isFalse();
        assertBalances(point, 30L, 0L);
        point.handler().applyConfiguration(FluxDeviceConfigComponent.EMPTY, 30L);
        assertBalances(point, 30L, 0L);
    }

    @Test
    void failedRealLoadReclaimsSavedReservationsOnlyAfterRestoreWasProcessed() {
        Point original = point("load_failure", 80L);
        Rig rig = rig(original.capability());
        MachineRecipe recipe = recipe("deleted_recipe", 4, 20L);
        RecipeRegistry.registerStatic(recipe);
        rig.runtime().start(recipe, 1L);
        CompoundTag runtimeTag = save(rig.runtime());
        Point restored = restorePoint("load_failure", original);
        assertBalances(restored, 80L, 80L); // Loading Point NBT must not release a pending controller budget.
        RecipeRegistry.clearForTesting();
        Rig restoredRig = rig(restored.capability());
        load(restoredRig.runtime(), runtimeTag);
        assertThat(restoredRig.runtime().active()).isFalse();
        assertThat(restoredRig.runtime().failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
        restored.handler().reconcileRecipeOwners();
        assertBalances(restored, 80L, 0L);
        assertThat(restoredRig.runtime().start(recipe("replacement_recipe", 2, 25L), 1L).isCrafting()).isTrue();
        restored.handler().onCycleStart();
        assertThat(restored.handler().getRequest()).isZero();
        assertBalances(restored, 80L, 50L);
    }

    @Test
    void coldControllerLoadWaitsForProductionDiscoveryAndPreservesNbtWhileAChunkIsMissing() throws Exception {
        for (boolean normalPrefetch : List.of(true, false)) {
            for (boolean deleted : List.of(false, true)) {
                for (boolean pointFirst : List.of(false, true)) {
                    var machine = new DynamicMachine(MMCR.id("test_cube"), "machine.mmcr.test_cube",
                            new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(ModBlocks.controllerFor(MMCR.id("test_cube")).get()),
                                    new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))));
                    MachineRegistry.clearForTesting();
                    MachineRegistry.register(machine);
                    MachineControllerBlockEntity original = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"),
                            new BlockPos(15, 0, 0), ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                                    .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
                    try {
                        Point point = point("cold", 300L);
                        RuntimeCapabilityHost host = new RuntimeCapabilityHost(original.getBlockPos().east(),
                                new CapabilitySnapshot(List.of(point.capability())));
                        RuntimeTestFixtures.formStructureWithComponents(original, machine, host);
                        CraftingRuntime originalRuntime = controllerRuntime(original).craftingRuntime();
                        MachineRecipe recipe = recipe("cold", 5, 20L);
                        RecipeRegistry.registerStatic(recipe);
                        originalRuntime.start(recipe, 1L);
                        originalRuntime.tick();
                        if (!normalPrefetch) originalRuntime.invalidate();
                        var factory = controllerRuntime(original).factoryRuntime();
                        factory.ensureBaseLane(original);
                        factory.setLaneLimit(2);
                        for (int index = 0; index < 2; index++) {
                            MachineRecipe laneRecipe = recipe("cold_lane_" + index, 5, 20L);
                            RecipeRegistry.registerStatic(laneRecipe);
                            var lane = factory.reservePatternStart(laneRecipe, 1L, List.of());
                            assertThat(lane).isNotNull();
                            assertThat(lane.runtime().commitPatternStart(lane.preparedStart())).isTrue();
                            lane.runtime().tick();
                        }
                        long savedReserved = normalPrefetch ? 240L : 160L;
                        assertBalances(point, 240L, savedReserved);
                        CompoundTag saved = original.saveWithFullMetadata(registries());
                        Point restored = restorePoint("cold", point);
                        original.invalidateFormedStructure();
                        if (deleted) RecipeRegistry.clearForTesting();

                        MachineControllerBlockEntity cold = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"),
                                original.getBlockPos(), original.getBlockState());
                        RuntimeTestFixtures.replaceBlockEntity(original, cold);
                        cold.loadWithComponents(saved, registries());
                        RuntimeCapabilityHost coldHost = new RuntimeCapabilityHost(host.getBlockPos(),
                                new CapabilitySnapshot(List.of(restored.capability())));
                        RuntimeTestFixtures.replaceBlockEntity(original, coldHost);
                        assertThat(cold.componentRuntime().capabilities()).isEmpty();
                        FluxNetworkInputBlockEntity leaf = reconciliationLeaf(restored, cold.getBlockPos());
                        leaf.setLevel(cold.getLevel());
                        RuntimeTestFixtures.setLoadedChunks(cold.getLevel(), Set.of(LevelStub.chunkKey(0, 0)));
                        if (pointFirst) reconcileLeaf(leaf);
                        cold.tickStructure((ServerLevel) cold.getLevel(), cold.getBlockPos()); // Binds default machine before discovery.
                        reconcileLeaf(leaf);
                        assertThat(cold.currentStructureSnapshot().configuredMachine()).isNotNull();
                        assertThat(cold.runtimeRestorationComplete()).isFalse();
                        assertThat(cold.componentRuntime().capabilities()).isEmpty();
                        assertThat(controllerRuntime(cold).craftingRuntime().active()).isFalse();
                        assertThat(controllerRuntime(cold).factoryRuntime().activeRuntimes()).isEmpty();
                        assertBalances(restored, 240L, savedReserved);
                        assertThat(cold.saveWithFullMetadata(registries()).getCompound("crafting_runtime"))
                                .isEqualTo(saved.getCompound("crafting_runtime"));
                        assertThat(cold.saveWithFullMetadata(registries()).getCompound("factory_runtime"))
                                .isEqualTo(saved.getCompound("factory_runtime"));

                        RuntimeTestFixtures.setLoadedChunks(cold.getLevel(), Set.of(LevelStub.chunkKey(0, 0), LevelStub.chunkKey(1, 0)));
                        cold.requestImmediateStructureCheck();
                        // Loading the chunk and publishing a check request must still leave restoration
                        // pending until the production discovery pass actually visits the replacement BE.
                        assertThat(cold.runtimeRestorationComplete()).isFalse();
                        assertThat(cold.componentRuntime().capabilities()).isEmpty();
                        assertThat(cold.saveWithFullMetadata(registries()).getCompound("crafting_runtime"))
                                .isEqualTo(saved.getCompound("crafting_runtime"));
                        assertThat(cold.saveWithFullMetadata(registries()).getCompound("factory_runtime"))
                                .isEqualTo(saved.getCompound("factory_runtime"));
                        for (int step = 0; step < 32 && !cold.currentStructureSnapshot().formed(); step++) {
                            cold.tickStructure((ServerLevel) cold.getLevel(), cold.getBlockPos());
                            RuntimeTestFixtures.advanceGameTime(cold.getLevel());
                        }
                        assertThat(cold.runtimeRestorationComplete()).isTrue();
                        assertThat(cold.componentRuntime().capabilities()).contains(restored.capability());
                        reconcileLeaf(leaf);
                        CraftingRuntime recovered = controllerRuntime(cold).craftingRuntime();
                        assertThat(recovered.active()).isEqualTo(!deleted && normalPrefetch);
                        var lanes = controllerRuntime(cold).factoryRuntime().activeRuntimes();
                        assertThat(lanes).hasSize(deleted ? 0 : 2);
                        assertBalances(restored, 240L, deleted ? 0L : savedReserved);
                        if (deleted) {
                            if (normalPrefetch) assertThat(recovered.failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_LOAD);
                        } else {
                            if (normalPrefetch) {
                                assertThat(recovered.tickCount()).isEqualTo(1);
                                recovered.tick();
                            }
                            for (CraftingRuntime lane : lanes) {
                                assertThat(lane.tickCount()).isEqualTo(1);
                                lane.tick();
                            }
                            long spent = normalPrefetch ? 60L : 40L;
                            assertBalances(restored, 240L - spent, savedReserved - spent);
                        }
                        cold.invalidateFormedStructure();
                    } finally {
                        original.invalidateFormedStructure();
                        MachineRegistry.clearForTesting();
                        MachineRegistry.restoreStartupForTesting();
                        RecipeRegistry.clearForTesting();
                    }
                }
            }
        }
    }

    @Test
    void inputLeafReclaimsAConfirmedMissingControllerWithoutLoadingItsChunk() throws Exception {
        Point original = point("missing", 80L);
        Rig rig = rig(original.capability());
        rig.runtime().start(recipe("missing", 4, 20L), 1L);
        Point restored = restorePoint("missing", original);
        BlockPos owner = new BlockPos(32, 0, 0);
        FluxNetworkInputBlockEntity leaf = reconciliationLeaf(restored, owner);
        leaf.setLevel(LevelStub.createWithLoadedChunks(Map.of(), Set.of()));
        reconcileLeaf(leaf);
        assertBalances(restored, 80L, 80L);
        leaf.setLevel(LevelStub.createWithLoadedChunks(Map.of(), Set.of(LevelStub.chunkKey(2, 0))));
        reconcileLeaf(leaf);
        assertBalances(restored, 80L, 0L);
    }

    @Test
    void multipleAllocationsKeepSuccessfulDebitsOnFailureAndRestoreRetryWithoutDoubleCharging() {
        for (boolean throwing : List.of(false, true)) {
            for (boolean asynchronous : List.of(false, true)) {
                Point first = point("partial_first", 1L);
                Point second = point("partial_second", 5L);
                PartialPoint firstFacet = new PartialPoint(first, false);
                PartialPoint secondFacet = new PartialPoint(second, throwing);
                FluxNetworkPlugHandler plug = plug();
                Rig rig = rig(firstFacet, secondFacet, new FluxNetworkOutputCapability(plug));
                MachineRecipe recipe = recipe("partial_" + throwing + asynchronous, 3,
                        List.of(new EnergyRequirement(2L), new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 1L)));
                RecipeRegistry.registerStatic(recipe);
                rig.runtime().start(recipe, 1L);
                secondFacet.fail = true;
                for (int retry = 0; retry < 2; retry++) {
                    if (asynchronous) {
                        var prepared = rig.runtime().prepareAsyncTickPlan(rig.controller().currentRuntimeSnapshot());
                        assertThat(rig.runtime().commitAsyncTick(prepared.plan())).isFalse();
                        rig.runtime().discardAsyncTickPreparation();
                    } else assertThat(rig.runtime().tick().isCrafting()).isFalse();
                    assertThat(plug.getBuffer()).isZero();
                }
                assertThat(rig.runtime().tickCount()).isZero();
                assertBalances(first, 0L, 0L);
                assertBalances(second, 5L, 5L);
                assertThat(allocation(rig.runtime(), 0)).isZero();
                assertThat(allocation(rig.runtime(), 1)).isEqualTo(5L);
                CompoundTag saved = save(rig.runtime());

                Point restoredFirst = restorePoint("partial_first", first);
                Point restoredSecond = restorePoint("partial_second", second);
                PartialPoint restoredFailure = new PartialPoint(restoredSecond, false);
                FluxNetworkPlugHandler restoredPlug = plug();
                Rig restoredRig = rig(new PartialPoint(restoredFirst, false), restoredFailure,
                        new FluxNetworkOutputCapability(restoredPlug));
                load(restoredRig.runtime(), saved);
                assertThat(restoredRig.runtime().active()).isTrue();
                restoredFirst.handler().reconcileRecipeOwners();
                restoredSecond.handler().reconcileRecipeOwners();
                restoredFailure.fail = true;
                restoredRig.runtime().tick();
                assertThat(restoredPlug.getBuffer()).isZero();
                assertBalances(restoredSecond, 5L, 5L);
                restoredFailure.fail = false;
                asyncTick(restoredRig, true);
                assertThat(restoredPlug.getBuffer()).isEqualTo(1L);
                assertThat(restoredRig.runtime().tickCount()).isEqualTo(1);
                assertBalances(restoredFirst, 0L, 0L);
                assertBalances(restoredSecond, 4L, 4L);
                asyncTick(restoredRig, false);
                restoredRig.runtime().tick();
                restoredRig.runtime().finish();
                assertThat(restoredRig.runtime().active()).isFalse();
                assertThat(restoredPlug.getBuffer()).isEqualTo(3L);
                assertBalances(restoredSecond, 0L, 0L);
            }
        }
    }

    @Test
    void paidTickCreditSurvivesRealPlugCommitFailureAndSaveLoad() {
        for (boolean asynchronous : List.of(false, true)) {
            FluxNetworkPlugHandler plug = plug();
            boolean[] blockOutputOnDebit = {false};
            FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, () -> 1L, () -> {
                if (blockOutputOnDebit[0]) plug.setLimit(0L);
            });
            handler.applyConfiguration(FluxDeviceConfigComponent.EMPTY, 6L);
            Point point = new Point(handler, new FluxNetworkInputCapability(handler, () -> "output_blocked"));
            Rig rig = rig(point.capability(), new FluxNetworkOutputCapability(plug));
            MachineRecipe recipe = recipe("output_blocked_" + asynchronous, 3,
                    List.of(new EnergyRequirement(2L), new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 1L)));
            RecipeRegistry.registerStatic(recipe);
            rig.runtime().start(recipe, 1L);
            blockOutputOnDebit[0] = true;
            if (asynchronous) {
                var prepared = rig.runtime().prepareAsyncTickPlan(rig.controller().currentRuntimeSnapshot());
                assertThat(rig.runtime().commitAsyncTick(prepared.plan())).isFalse();
                rig.runtime().discardAsyncTickPreparation();
            } else rig.runtime().tick();
            assertThat(rig.runtime().tickCount()).isZero();
            assertThat(plug.getBuffer()).isZero();
            assertBalances(point, 4L, 4L);
            rig.runtime().tick(); // Planning now blocks too; already-paid credit must remain intact.
            assertBalances(point, 4L, 4L);

            Point restored = restorePoint("output_blocked", point);
            Rig recovered = rig(restored.capability(), new FluxNetworkOutputCapability(plug));
            load(recovered.runtime(), save(rig.runtime()));
            plug.setLimit(100L);
            asyncTick(recovered, true);
            assertBalances(restored, 4L, 4L);
            assertThat(plug.getBuffer()).isEqualTo(1L);
            asyncTick(recovered, false);
            recovered.runtime().tick();
            recovered.runtime().finish();
            assertBalances(restored, 0L, 0L);
            assertThat(plug.getBuffer()).isEqualTo(3L);
        }
    }

    @Test
    void publicScalarIoCannotConsumeAnotherOwnersReservationOrTentativeBudget() {
        Point point = point("isolated", 10L);
        Rig rig = rig(point.capability());
        rig.runtime().start(recipe("isolated", 3, 2L), 1L);
        var snapshot = new CapabilitySnapshot(List.of(point.capability()));
        assertThat(new MachineIoView(snapshot).energyInput()).isEqualTo(4L);
        var tooLarge = new MachineIoPlan(snapshot).addInput(new EnergyRequirement(5L));
        assertThat(tooLarge.simulate().energySatisfied()).isFalse();
        assertThat(tooLarge.commit().successful()).isFalse();
        assertThat(point.capability().prepare(new CapabilityRequests.ValueRequest(point.capability().type(),
                IOType.INPUT, 1L, 5L, false)).commit().success()).isFalse();
        var tentative = point.capability().planPrefetch(2L).orElseThrow();
        assertThat(new MachineIoView(snapshot).energyInput()).isEqualTo(2L);
        EnergyStorage nativeStorage = new EnergyStorage(1);
        nativeStorage.receiveEnergy(1, false);
        var mixed = new CapabilitySnapshot(List.of(new EnergyHatchCapability(nativeStorage, IOType.INPUT), point.capability()));
        var mixedTooLarge = new MachineIoPlan(mixed).addInput(new EnergyRequirement(4L));
        assertThat(mixedTooLarge.simulate().energySatisfied()).isFalse();
        var free = new MachineIoPlan(mixed).addInput(new EnergyRequirement(3L));
        assertThat(free.simulate().energySatisfied()).isTrue();
        assertThat(free.commit().successful()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isZero();
        assertBalances(point, 8L, 6L);
        point.capability().restoreReservation(tentative.amount());
        rig.runtime().tick();
        assertBalances(point, 6L, 4L);
        rig.runtime().invalidate();
        assertThat(point.capability().consumeReservation(1L).success()).isFalse();
        assertBalances(point, 6L, 0L);
    }

    @Test
    void failedMultiAllocationTickCancellationReleasesOnlyTheUnconsumedBalance() {
        Point first = point("failed_cancel_first", 1L);
        Point second = point("failed_cancel_second", 5L);
        PartialPoint failing = new PartialPoint(second, false);
        Rig rig = rig(new PartialPoint(first, false), failing);
        rig.runtime().start(recipe("failed_cancel", 3, 2L), 1L);
        failing.fail = true;
        var prepared = rig.runtime().prepareAsyncTickPlan(rig.controller().currentRuntimeSnapshot());
        assertThat(rig.runtime().commitAsyncTick(prepared.plan())).isFalse();
        assertThat(rig.runtime().tickCount()).isZero();
        rig.runtime().discardAsyncTickPreparation();
        rig.runtime().invalidate();
        assertBalances(first, 0L, 0L);
        assertBalances(second, 5L, 0L);
    }

    @Test
    void cancelOnPerTickFailureReleasesTheUpdatedMultipleAllocationLedger() {
        Point first = point("auto_cancel_first", 1L);
        Point second = point("auto_cancel_second", 5L);
        PartialPoint failing = new PartialPoint(second, false);
        Rig rig = rig(new PartialPoint(first, false), failing);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("flux_runtime_auto_cancel"), MMCR.id("test_cube"),
                3, List.of(), List.of(), List.of(), 0, 1, true, List.of(), List.of(new EnergyRequirement(2L)));
        rig.runtime().start(recipe, 1L);
        failing.fail = true;
        rig.runtime().tick();
        assertThat(rig.runtime().active()).isFalse();
        assertBalances(first, 0L, 0L);
        assertBalances(second, 5L, 0L);
    }

    @Test
    void saturatedLongBudgetRemainsConservedThroughTheRealRuntimeAndSavedRestore() {
        long perTick = Long.MAX_VALUE / 2L + 1L;
        Point original = point("saturated", Long.MAX_VALUE);
        Rig rig = rig(original.capability());
        MachineRecipe recipe = recipe("saturated", 3, perTick);
        RecipeRegistry.registerStatic(recipe);
        assertThat(rig.runtime().start(recipe, 1L).isCrafting()).isTrue();
        assertBalances(original, Long.MAX_VALUE, Long.MAX_VALUE);
        rig.runtime().tick();
        assertBalances(original, Long.MAX_VALUE - perTick, Long.MAX_VALUE - perTick);
        Point restored = restorePoint("saturated", original);
        Rig restoredRig = rig(restored.capability());
        load(restoredRig.runtime(), save(rig.runtime()));
        assertThat(restoredRig.runtime().active()).isTrue();
        asyncTick(restoredRig, true);
        assertBalances(restored, 0L, 0L);
        restoredRig.runtime().tick();
        assertThat(restoredRig.runtime().finishPending()).isTrue();
        restoredRig.runtime().finish();
        assertThat(restoredRig.runtime().active()).isFalse();
    }

    @Test
    void twoRealPointsRejectUnrepresentableAggregateBeforeStartAndPreserveTheLegalBoundaryOnSave() {
        for (boolean overflow : List.of(true, false)) {
            long firstAmount = Long.MAX_VALUE / 2L + 1L;
            long secondAmount = overflow ? firstAmount : Long.MAX_VALUE - firstAmount;
            Point first = point("aggregate_first", firstAmount);
            Point second = point("aggregate_second", secondAmount);
            Rig rig = rig(first.capability(), second.capability());
            MachineRecipe recipe = recipe("aggregate_" + overflow, 1,
                    List.of(new EnergyRequirement(firstAmount), new EnergyRequirement(secondAmount)));
            RecipeRegistry.registerStatic(recipe);
            var context = new CraftingContext(new CapabilitySnapshot(List.of(first.capability(), second.capability())));
            assertThat(context.planStartResult(recipe, 1L).successful()).isEqualTo(!overflow);
            assertBalances(first, firstAmount, 0L);
            assertBalances(second, secondAmount, 0L);
            assertThat(first.handler().availableForPrefetch()).isEqualTo(firstAmount);
            assertThat(second.handler().availableForPrefetch()).isEqualTo(secondAmount);
            assertThat(rig.runtime().start(recipe, 1L).isCrafting()).isEqualTo(!overflow);
            CompoundTag saved = save(rig.runtime());
            if (overflow) {
                assertThat(saved.getBoolean("active")).isFalse();
                assertBalances(first, firstAmount, 0L);
                assertBalances(second, secondAmount, 0L);
                assertThat(first.handler().availableForPrefetch()).isEqualTo(firstAmount);
                assertThat(second.handler().availableForPrefetch()).isEqualTo(secondAmount);
            } else {
                assertBalances(first, firstAmount, firstAmount);
                assertBalances(second, secondAmount, secondAmount);
                Point restoredFirst = restorePoint("aggregate_first", first);
                Point restoredSecond = restorePoint("aggregate_second", second);
                Rig recovered = rig(restoredFirst.capability(), restoredSecond.capability());
                load(recovered.runtime(), saved);
                assertThat(recovered.runtime().active()).isTrue();
                recovered.runtime().tick();
                assertThat(recovered.runtime().finishPending()).isTrue();
                assertBalances(restoredFirst, 0L, 0L);
                assertBalances(restoredSecond, 0L, 0L);
            }
        }
    }

    @Test
    void longRuntimeBudgetIsConservedAcrossConsumptionSaveRestoreAndCancel() {
        long perTick = (long) Integer.MAX_VALUE + 100L;
        Point point = point("long", perTick * 3L);
        Rig rig = rig(point.capability());
        MachineRecipe recipe = recipe("long_budget", 3, perTick);
        RecipeRegistry.registerStatic(recipe);
        rig.runtime().start(recipe, 1L);
        rig.runtime().tick();
        assertBalances(point, perTick * 2L, perTick * 2L);
        Point restored = restorePoint("long", point);
        Rig restoredRig = rig(restored.capability());
        load(restoredRig.runtime(), save(rig.runtime()));
        restored.handler().reconcileRecipeOwners();
        asyncTick(restoredRig, false);
        restoredRig.runtime().invalidate();
        assertBalances(restored, perTick, 0L);
    }

    private static void asyncTick(Rig rig, boolean fallback) {
        var prepared = rig.runtime().prepareAsyncTickPlan(rig.controller().currentRuntimeSnapshot());
        assertThat(prepared).isNotNull();
        var planned = fallback ? new AsyncRequirementPlanner.PlanResult(List.of(), List.of(0)) : prepared.plan();
        assertThat(rig.runtime().commitAsyncTick(planned)).isTrue();
        assertThat(rig.runtime().completeAsyncTickAfterInputs()).isTrue();
        rig.runtime().completeAsyncTickAfterRecipe();
    }

    private static Rig rig(MachineCapability... capabilities) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        List<ProcessingComponent> components = new ArrayList<>();
        for (int index = 0; index < capabilities.length; index++) {
            CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(capabilities[index]));
            BlockPos pos = new BlockPos(index + 1, 0, 0);
            RuntimeCapabilityHost host = new RuntimeCapabilityHost(pos, snapshot);
            components.add(new ProcessingComponent(null, host, pos, pos, (String) null));
        }
        controller.componentRuntime().replaceComponents(components);
        RuntimeTestFixtures.republish(controller);
        return new Rig(controller, new CraftingRuntime(controller, controller.componentRuntime()));
    }

    private static MachineRecipe recipe(String path, int duration, long perTick) {
        return recipe(path, duration, perTick, 0);
    }

    private static MachineRecipe recipe(String path, int duration, long perTick, int priority) {
        return RecipeTestSupport.create(MMCR.id("flux_runtime_" + path), MMCR.id("test_cube"), duration,
                List.of(), List.of(), List.of(), priority, 1, false, List.of(), List.of(new EnergyRequirement(perTick)));
    }

    private static MachineRecipe recipe(String path, int duration, List<MachineRequirement> requirements) {
        return RecipeTestSupport.create(MMCR.id("flux_runtime_" + path), MMCR.id("test_cube"), duration,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), requirements);
    }

    private static FluxNetworkPlugHandler plug() {
        FluxNetworkPlugHandler handler = new FluxNetworkPlugHandler(() -> true, () -> 100L, () -> {});
        handler.setLimit(100L);
        return handler;
    }

    private static HolderLookup.Provider registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static MachineControllerRuntime controllerRuntime(MachineControllerBlockEntity controller) throws Exception {
        return (MachineControllerRuntime) field(MachineControllerBlockEntity.class, "runtime").get(controller);
    }

    private static Point point(String key, long energy) {
        FluxNetworkPointHandler handler = new FluxNetworkPointHandler(() -> true, () -> 1L, () -> {});
        handler.setLimit(1000L);
        if (energy > 1000L) handler.setDisableLimit(true);
        if (energy > 0L) {
            handler.requestWarmup(energy);
            handler.onCycleStart();
            handler.addToBuffer(energy);
        }
        handler.clearDemand();
        return new Point(handler, new FluxNetworkInputCapability(handler, () -> key));
    }

    private static Point restorePoint(String key, Point original) {
        CompoundTag tag = new CompoundTag();
        original.handler().writeCustomTag(tag, FluxConstants.NBT_SAVE_ALL);
        Point restored = point(key, 0L);
        restored.handler().readCustomTag(tag, FluxConstants.NBT_SAVE_ALL);
        return restored;
    }

    private static CompoundTag save(CraftingRuntime runtime) {
        CompoundTag tag = new CompoundTag();
        runtime.save(tag, EMPTY_LOOKUP);
        return tag;
    }

    private static void load(CraftingRuntime runtime, CompoundTag tag) {
        runtime.load(tag, null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static FluxNetworkInputBlockEntity reconciliationLeaf(Point point, BlockPos owner) throws Exception {
        // The leaf's reconciliation has no dependency on native registration/network first-tick setup.
        // Allocate only that shell, then run its real ownership method against the existing LevelStub.
        Field unsafeField = field(Unsafe.class, "theUnsafe");
        var unsafe = (Unsafe) unsafeField.get(null);
        var leaf = (FluxNetworkInputBlockEntity) unsafe.allocateInstance(FluxNetworkInputBlockEntity.class);
        field(FluxNetworkInputBlockEntity.class, "handler").set(leaf, point.handler());
        field(FluxNetworkInputBlockEntity.class, "reservationControllerPos").set(leaf, owner);
        field(BlockEntity.class, "worldPosition").set(leaf, new BlockPos(1, 0, 0));
        return leaf;
    }

    private static void reconcileLeaf(FluxNetworkInputBlockEntity leaf) throws Exception {
        Method method = FluxNetworkInputBlockEntity.class.getDeclaredMethod("reconcileLoadedReservations");
        method.setAccessible(true);
        method.invoke(leaf);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static long allocation(CraftingRuntime runtime, int index) {
        return save(runtime).getCompound("recipe").getCompound("data")
                .getList("prefetched_energy_allocations", Tag.TAG_COMPOUND).getCompound(index).getLong("amount");
    }

    private static void assertBalances(Point point, long buffer, long reserved) {
        assertThat(point.handler().getBuffer()).isEqualTo(buffer);
        assertThat(point.handler().storage().amount()).isEqualTo(buffer);
        assertThat(point.handler().reserved()).isEqualTo(reserved);
    }

    private record Point(FluxNetworkPointHandler handler, FluxNetworkInputCapability capability) { }
    private record Rig(MachineControllerBlockEntity controller, CraftingRuntime runtime) { }

    /** Capability container using the existing runtime tests' registered BE fixture.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RuntimeCapabilityHost extends BlockEntity implements CapabilityHost, MachineComponentTile {
        private final CapabilitySnapshot snapshot;

        private RuntimeCapabilityHost(BlockPos pos, CapabilitySnapshot snapshot) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            this.snapshot = snapshot;
        }

        @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
        @Override public MachineComponent provideComponent() { return new MachineComponent(PortKinds.ENERGY_INPUT, IOType.INPUT); }
    }

    /** Uses real Point plans/inventory, injecting only split admission and a settlement failure.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PartialPoint implements MachineCapability, RecipeEnergyPrefetchFacet {
        private final Point point;
        private final boolean throwing;
        private boolean fail;

        private PartialPoint(Point point, boolean throwing) {
            this.point = point;
            this.throwing = throwing;
        }

        @Override public CapabilityType type() { return point.capability().type(); }
        @Override public CapabilityDirections directions() { return point.capability().directions(); }
        @Override public CapabilityView view() { return point.capability().view(); }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { return point.capability().prepare(request); }
        @Override public String reservationKey() { return point.capability().reservationKey(); }
        @Override public Optional<PrefetchPlan> planPrefetch(long requested) {
            return point.capability().planPrefetch(Math.min(requested, point.handler().availableForPrefetch()));
        }
        @Override public void restoreReservation(long amount) { point.capability().restoreReservation(amount); }
        @Override public long releaseReservation(long amount) { return point.capability().releaseReservation(amount); }
        @Override public CapabilityResult consumeReservation(long amount) {
            if (fail && throwing) throw new IllegalStateException("Injected second-allocation settlement failure");
            return point.capability().consumeReservation(fail ? Long.MAX_VALUE : amount);
        }
        @Override public void onRecipeReservationChanged(Object owner, long remaining, Runnable cancelOwner) {
            point.capability().onRecipeReservationChanged(owner, remaining, cancelOwner);
        }
    }
}
