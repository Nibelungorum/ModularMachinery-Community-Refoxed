package cn.howxu.mmcr;

import cn.howxu.mmcr.internal.recipe.RecipeSearchGameTest;

import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUiProtocolsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.data.view.DataStorage;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.definition.ModifierUse;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.Task2AE2OutputGameTest;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.InterfaceMenuGameTest;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.StructureFluidGameTest;
import cn.howxu.mmcr.compat.patchouli.PatchouliGuideGameTest;
import net.neoforged.fml.ModList;
import cn.howxu.mmcr.AppliedFluxInterfaceGameTest;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.function.Consumer;

import java.util.List;

@EventBusSubscriber(modid = MMCR.MODID)
public final class GameTestRegistry {
    private GameTestRegistry() {
    }

    public static void registerAll(RegisterGameTestsEvent event) {
        register(event, "block_map_color", 20, BlockMapColorGameTest::registeredBlocksAreVisibleMapSurfaces);
        register(event, "structure_material_fluid_counts", 20, StructureMaterialGameTest::sourcesAndFlowsShareBuckets);
        register(event, "structure_material_flowing_only", 20, StructureMaterialGameTest::flowingOnlyBucketsAreDisplayOnly);
        register(event, "structure_material_bucket_demand", 20, StructureMaterialGameTest::bucketDemandIsNotStackClamped);
        register(event, "controller_placement_horizontal", 20, ControllerPlacementGameTest::horizontalPlacementFollowsEnrichmentChamber);
        register(event, "controller_placement_optional_vertical", 20, ControllerPlacementGameTest::optionalVerticalPlacementFollowsMeDrive);
        register(event, "controller_placement_required_vertical", 20, ControllerPlacementGameTest::requiredVerticalPlacementFollowsPitchSign);
        register(event, "controller_placement_without_player", 20, ControllerPlacementGameTest::placementWithoutPlayerUsesReferenceFallbacks);
        register(event, "async_search_components_and_stack_limits", 20, RecipeSearchGameTest::componentPredicatesAndOutputStackLimits);
        if (ModList.get().isLoaded("mekanism")) {
            register(event, "async_search_chemical_and_heat", 20, RecipeSearchGameTest::chemicalAndHeatMatchNativeHandlers);
        }
        register(event, "input_isolation_item_sources", 20, InputIsolationGameTest::itemSourcesCannotPoolAcrossIsolation);
        register(event, "input_isolation_fluid_sources", 20, InputIsolationGameTest::fluidSourcesCannotPoolAcrossIsolation);
        register(event, "input_isolation_combined_atomic", 20, InputIsolationGameTest::combinedInputSharesEnergyAndOutputAtomically);
        register(event, "input_isolation_full_local_replacement", 20, InputIsolationGameTest::fullIsolationPreservesLocalAndIncludesReplacement);
        register(event, "input_isolation_menu_two_viewers", 20, InputIsolationGameTest::menusPersistAndSynchronizeTwoViewers);
        register(event, "input_isolation_paid_ordinary_reload", 20, InputIsolationGameTest::paidOrdinaryScopeSurvivesReload);
        register(event, "input_isolation_paid_isolated_reload", 20, InputIsolationGameTest::paidIsolatedScopeSurvivesReload);
        if (ModList.get().isLoaded("ae2")) {
            register(event, "pattern_batch_definition_roundtrip", 20,
                    PatternBatchCodecGameTest::batchDefinitionRoundTripsDistinctMultipliers);
            register(event, "pattern_batch_invalid_definition", 20,
                    PatternBatchCodecGameTest::invalidBatchDefinitionCannotBecomeAnUnscaledTask);
            register(event, "pattern_batch_native_job_reload", 20,
                    PatternBatchCodecGameTest::nativeExecutingJobReloadKeepsFullAndRemainder);
            register(event, "pattern_batch_int_max_native_dispatch", 600,
                    helper -> new PatternBatchLargeAmountGameTest().nativeCpuDispatchesIntMaxParallelismAsOneBatch(helper));
            register(event, "pattern_batch_large_item_settlement", 600,
                    helper -> new PatternBatchLargeAmountGameTest().hugeBatchSettlesOutputsAboveIntRangeWithoutTruncation(helper));
            register(event, "pattern_batch_large_fluid_settlement", 600,
                    helper -> new PatternBatchLargeAmountGameTest().hugeFluidBatchSettlesOutputsAboveIntRangeWithoutTruncation(helper));
            register(event, "pattern_batch_large_item_atomic_refusal", 600,
                    helper -> new PatternBatchLargeAmountGameTest().hugeBatchFailsAtomicallyWhenOneUnitOrAePowerIsMissing(helper));
            register(event, "pattern_batch_large_fluid_atomic_refusal", 600,
                    helper -> new PatternBatchLargeAmountGameTest().hugeFluidBatchFailsAtomicallyWhenOneUnitOrAePowerIsMissing(helper));
            register(event, "pattern_batch_large_cpu_cancel", 600,
                    helper -> new PatternBatchLargeAmountGameTest().cancelUndispatchedHugeBatchReturnsEveryCpuHeldUnit(helper));
            register(event, "pattern_batch_cpu_full_remainder_reload", 3000,
                    helper -> new PatternBatchLargeAmountGameTest().nativeCpuReloadKeepsFullAndRemainderThenSettles(helper));
            register(event, "pattern_batch_full_remainder_totals", 40,
                    PatternBatchPlanningGameTest::fullRemainderAndSingleHugeBatchPreserveTotals);
            register(event, "pattern_batch_reuse_cycle_safety", 40,
                    PatternBatchPlanningGameTest::reusedMoldAndCyclicDependencyRemainExecutable);
            register(event, "pattern_batch_dag_totals", 40,
                    PatternBatchPlanningGameTest::acyclicUpstreamAndByproductsKeepUsedItems);
            register(event, "pattern_batch_unknown_requirement_safety", 40,
                    PatternBatchPlanningGameTest::unknownRequirementsPoisonOnlyEnumerableComponents);
            register(event, "pattern_batch_snapshot_isolation", 40,
                    PatternBatchPlanningGameTest::planningDoesNotTouchLiveWorldOrInvokeHooks);
            register(event, "pattern_batch_native_provider_isolation", 40,
                    PatternBatchPlanningGameTest::mixedProvidersNeverDispatchBatchToVanilla);
            if (ModList.get().isLoaded("extendedae")) {
                register(event, "pattern_batch_two_host_publication", 100,
                        PatternBatchPlanningGameTest::onlyTheTwoMmcrHostsPublishBatchIdentity);
            }
            register(event, "pattern_batch_paid_processing_reload", 1000, MMCR.id("pattern_batch_cross_chunk"),
                    helper -> new PatternBatchPersistenceGameTest().reloadCpuMachineAndInterfaceDuringPaidProcessing(helper));
            register(event, "pattern_batch_paid_final_tick_reload", 1000, MMCR.id("pattern_batch_cross_chunk"),
                    helper -> new PatternBatchPersistenceGameTest().reloadCpuMachineAndInterfaceAfterFinalTickPaid(helper));
            register(event, "pattern_batch_paid_output_reload", 1000, MMCR.id("pattern_batch_cross_chunk"),
                    helper -> new PatternBatchPersistenceGameTest().reloadCpuMachineAndInterfaceWithOutputWaitingForNetwork(helper));
            register(event, "pattern_batch_machine_energy_refusal", 600,
                    helper -> new PatternBatchPersistenceGameTest().hugeBatchRejectsMissingMachineFeWithoutTakingCpuInputs(helper));
            register(event, "pattern_batch_full_output_refusal", 600,
                    helper -> new PatternBatchPersistenceGameTest().hugeBatchRejectsFullOutputWithoutTakingCpuInputs(helper));
            register(event, "pattern_batch_overflow_before_payment", 600,
                    helper -> new PatternBatchPersistenceGameTest().overflowingBatchDefinitionRejectsBeforeAnyCpuPayment(helper));
            register(event, "pattern_batch_exact_normal_runtime", 40,
                    PatternBatchRuntimeGameTest::exactNormalBatchRejectsPrefixAndFinishesOnce);
            register(event, "pattern_batch_exact_factory_runtime", 40,
                    PatternBatchRuntimeGameTest::exactFactoryBatchRejectsPrefixAndFinishesOnce);
            register(event, "pattern_batch_paid_normal_snapshot", 40,
                    PatternBatchRuntimeGameTest::paidNormalPatternReloadKeepsSnapshot);
            register(event, "pattern_batch_paid_factory_snapshot", 40,
                    PatternBatchRuntimeGameTest::paidFactoryPatternReloadKeepsSnapshot);
            register(event, "pattern_batch_exact_long_resources", 40,
                    PatternBatchRuntimeGameTest::exactLongItemAndFluidBatchFinishesOnce);
            register(event, "pattern_batch_conditions_callbacks_runtime", 40,
                    PatternBatchRuntimeGameTest::conditionalBatchRunsAllNativeCallbacksAtRequestedParallelism);
            register(event, "pattern_batch_conditions_callbacks_publication", 100,
                    PatternBatchPlanningGameTest::runtimeConditionsAndHooksPublishAndCompressBulkInputs);
            register(event, "pattern_batch_probability_publication", 100,
                    PatternBatchPlanningGameTest::onlyIntermediateInputProbabilityKeepsNativeUnitTasks);
                // On Github CI, this will dropped sometime
            // register(event, "pattern_batch_probability_admission", 40,
            //         PatternBatchRuntimeGameTest::probabilisticOutputsAdmitBatchesAndRetainedInputsReturnUnconsumed);
            register(event, "pattern_batch_inspect_unloaded_chunk", 40,
                    PatternBatchRuntimeGameTest::inspectionDoesNotLoadLinkedControllerChunk);
            register(event, "input_isolation_native_ae_input", 20, InputIsolationGameTest::nativeAeInputUsesOnePhysicalSource);
            register(event, "input_isolation_ordinary_pattern_request", 20, InputIsolationGameTest::ordinaryPatternRequestIsAtomic);
            register(event, "input_isolation_isolated_pattern_request", 20, InputIsolationGameTest::isolatedPatternRequestIsAtomic);
            register(event, "input_isolation_ordinary_pattern_topup", 20, InputIsolationGameTest::ordinaryPatternRequestCanTopUpAndRollback);
        }
        if (ModList.get().isLoaded("mekanism")) {
            register(event, "structure_material_mekanism_bucket", 20, StructureMaterialGameTest::mekanismFluidUsesItsRegisteredBucket);
            register(event, "startup_mekanism_registry_references", 20,
                    StartupRegistryReferenceGameTest::mekanismExampleResolvesRegisteredContent);
            register(event, "input_isolation_chemical_shared_heat", 20, InputIsolationGameTest::chemicalIsolationStillSharesHeatAndEnergy);
        }
        if (ModList.get().isLoaded("kubejs")) {
            register(event, "startup_kubejs_registry_references", 20,
                    StartupRegistryReferenceGameTest::kubejsReferencesResolveAfterRegistryCallbacks);
        }
        register(event, "running_energy_admission", 20, RunningRecipeGameTest::ordinaryEnergyChargesOnlyRecipeTicks);
        register(event, "running_installed_fluid_probability", 20, RunningRecipeGameTest::installedFluidProbabilityAttempts);
        register(event, "running_mixed_input_rollback", 20, RunningRecipeGameTest::mixedInputCommitRollsBackAfterEnergyChanges);
        register(event, "running_retained_input_removed", 20, RunningRecipeGameTest::removedRetainedInputStopsContinuousPayment);
        register(event, "running_final_paid_output_reload", 20, RunningRecipeGameTest::finalPaidTickSurvivesBlockedOutputReload);
        register(event, "running_resource_paid_save_reentrance", 20, RunningRecipeGameTest::resourcePaidSaveReentranceRestoresPausedLedger);
        register(event, "appflux_prefetch_port_nbt_restore", 20,
                helper -> new AppliedFluxInterfaceGameTest().prefetchReservationRestoresWithActualPortNbt(helper));
        register(event, "running_same_time_qualification", 20, RunningRecipeGameTest::sameGameTimeRoundsRequireFreshBlockEntityEligibility);
        register(event, "controller_ui_committed_progress_baseline", 20, ControllerUiGameTest::reopenedSessionUsesCommittedProgressAndJadeRequest);
        register(event, "mekanism_prepared_temperature_live_check", 20,
                helper -> new MekanismPortGameTest().preparedTemperatureRechecksLivePortAndRollsBackEnergy(helper));
        register(event, "patchouli_guide_acquisition", 20,
                PatchouliGuideGameTest::acquisitionMatchesOptionalInstallation);
        register(event, "controller_ui_menu_lifecycle", 20,
                helper -> new ControllerUiGameTest().openMetadataAndLifecycle(helper));
        register(event, "spectator_controller_ui_metadata", 20, ControllerUiGameTest::spectatorControllerMetadata);
        register(event, "controller_ui_terminal_menu_lifecycle", 20, ControllerUiGameTest::terminalMenuLifecycle);
        register(event, "controller_ui_manual_structure_check", 20, ControllerUiGameTest::manualStructureCheck);
        register(event, "controller_ui_request_storage", 100, ControllerUiGameTest::requestStorage);
        register(event, "controller_ui_reopen_stale_request", 100, ControllerUiGameTest::reopenStaleRequest);
        register(event, "controller_ui_lane_removed", 100, ControllerUiGameTest::laneRemoved);
        register(event, "controller_ui_slot_visibility", 20, ControllerUiGameTest::slotVisibility);
        register(event, "controller_ui_snapshot_ownership", 20, ControllerUiGameTest::snapshotOwnership);
        register(event, "registry_reflection_helper", 20, helper -> {
            boolean registered = helper.getLevel().registryAccess()
                    .lookupOrThrow(Registries.TEST_INSTANCE)
                    .get(MMCR.id("registry_reflection_helper"))
                    .isPresent();
            helper.assertTrue(registered, "GameTestRegistry reflection helper registers the canonical test ID");
            helper.succeed();
        });
        register(event, "recipe_amount_codec_limits", 20,
                helper -> new RecipeAmountCodecGameTest().long_recipe_output_amounts_are_capped_at_native_stack_limits(helper));
        register(event, "runtime_content_final_datapack_sync", 20,
                RuntimeContentSyncGameTest::finalDatapackSyncIncludesLateRecipes);
        register(event, "item_output_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().outputResolvesPlainJsonEnchantments(helper));
        register(event, "pattern_output_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().patternOutputsMatchResolvedComponentsStrictly(helper));
        register(event, "recipe_serializer_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().recipeSerializerPreservesEnchantmentComponents(helper));
        register(event, "jade_output_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().jadeOutputPreservesEnchantmentComponents(helper));
        register(event, "async_item_output_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().asyncOutputPreservesEnchantmentComponents(helper));
        register(event, "cached_item_output_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().cachedOutputSurvivesFinishReplacement(helper));
        register(event, "block_array_match", 100, helper -> new BlockArrayMatchGameTest().structureForms3x3Casing(helper));
        register(event, "controller_tick", 100, helper -> new ControllerTickGameTest().structureForms3x3Casing(helper));
        register(event, "upgrade_bus_invalidation", 100,
                helper -> new UpgradeBusGameTest().optionalBusesPreserveActiveRecipe(helper));
        register(event, "controller_tick_recipe_hooks", 100,
                helper -> new ControllerTickGameTest().recipeMachineHooksPublishTextAndRespectRedstone(helper));
        register(event, "controller_tick_partial_io", 100,
                helper -> new ControllerTickGameTest().formedTickCommitsPartialOutputAndDataAtomically(helper));
        register(event, "data_storage_pure_tick", 100,
                helper -> new DataStorageGameTest().pureTickWritesBoundStorage(helper));
        register(event, "data_storage_recipe_snapshot", 100,
                helper -> new DataStorageGameTest().recipeSnapshotLoadsWithoutStartCallbackRerun(helper));
        register(event, "multi_factory_controller", 160,
                helper -> new MultiFactoryControllerGameTest().formsWithTwoFactoryControllersAndReformsAfterRelease(helper));
        register(event, "e2e_recipe_run", 200, helper -> new E2ERecipeRunGameTest().ironCompressorRuns(helper));
        register(event, "e2e_distillation_tower_partial_outputs", 160, helper -> new E2ERecipeRunGameTest().distillationTowerUnlocksPartialFluidOutputsByStage(helper));
        register(event, "energy_hatch_capability", 100, helper -> new EnergyHatchCapabilityGameTest().energyHatchStoresFE(helper));
        register(event, "fluid_hatch_capability", 100, helper -> new FluidHatchCapabilityGameTest().fluidHatchStoresWater(helper));
        register(event, "fluid_hatch_menu_storage", 100, helper -> new FluidHatchCapabilityGameTest().positionOnlyFluidHatchMenuResolvesStoredFluid(helper));
        register(event, "fluid_hatch_bucket_interaction", 100, helper -> new FluidHatchCapabilityGameTest().bucketInteractionRespectsHatchDirection(helper));
        register(event, "port_container_bucket_direction", 20,
                helper -> new PortContainerTransferGameTest().bucketsFollowPortDirection(helper));
        register(event, "port_container_bucket_rollback", 20,
                helper -> new PortContainerTransferGameTest().partialBucketsRollbackBothSides(helper));
        register(event, "port_container_combined_second_tank", 20,
                helper -> new PortContainerTransferGameTest().combinedSecondTankIsIndependent(helper));
        register(event, "port_container_stacked_bucket_inventory", 20,
                helper -> new PortContainerTransferGameTest().stackedBucketsRespectInventoryCapacity(helper));
        register(event, "port_container_invalid_requests", 20,
                helper -> new PortContainerTransferGameTest().invalidRequestsCannotMutateStorage(helper));
        register(event, "port_container_world_buckets", 20,
                helper -> new PortContainerTransferGameTest().worldBucketsUseExtractionFirstAndOneByOne(helper));
        register(event, "port_container_world_combined", 20,
                helper -> new PortContainerTransferGameTest().worldCombinedTransfersSelectCompatibleResources(helper));
        register(event, "item_bus_capability", 100, ItemBusCapabilityGameTest::itemBusAcceptsItems);
        register(event, "item_bus_non_stackable_limit", 100,
                ItemBusCapabilityGameTest::itemBusDoesNotStackNonStackableItems);
        register(event, "item_bus_capability_cache_auto_io_side", 100, ItemBusCapabilityGameTest::itemBusCapabilityCacheFollowsAutoIOSideConfig);
        register(event, "item_bus_drops_contents", 100, ItemBusCapabilityGameTest::itemBusDropsStoredItemsWhenRemoved);
        // No need any more
        // register(event, "wrench_standing_preserves_block", 100,
        //         helper -> new WrenchDismantleGameTest().wrenchWhileStandingDoesNotDismantle(helper));
        // register(event, "wrench_dismantles_item_bus", 100,
        //         helper -> new WrenchDismantleGameTest().wrenchDismantlesItemBusAndDropsItsContents(helper));
        register(event, "dynamic_controller_drops_self", 100, ItemBusCapabilityGameTest::dynamicControllerDropsItself);
        // Disabled: CPU scheduling delays can cause this otherwise-correct test to fail intermittently.
        register(event, "auto_io_adjacent_picked_icons", 20,
                helper -> new AutoIOGameTest().adjacentIconsUsePickedItems(helper));
        register(event, "auto_io_fluid_output", 120, helper -> new AutoIOGameTest().fluidOutputAutoExports(helper));
        register(event, "auto_io_energy_output", 120, helper -> new AutoIOGameTest().energyOutputAutoExports(helper));
        register(event, "auto_io_combined_input", 120,
                helper -> new AutoIOGameTest().combinedInputAutoImportsCapabilitiesIndependently(helper));
        register(event, "auto_io_combined_output", 160,
                helper -> new AutoIOGameTest().combinedOutputAutoExportsCapabilitiesIndependently(helper));
        register(event, "combined_input_capability_ejection", 100,
                helper -> new AutoIOGameTest().combinedInputEjectionIsCapabilitySpecific(helper));
        register(event, "combined_port_appearance", 100,
                helper -> new CombinedPortGameTest().combinedPortPublishesFormedAppearance(helper));
        register(event, "combined_port_fluid_container", 100,
                helper -> new CombinedPortGameTest().combinedPortSupportsFluidContainerInteraction(helper));
        register(event, "extended_combined_long_transfer", 100,
                helper -> new ExtendedPortGameTest().extendedCombinedPortTransfersBeyondIntegerRange(helper));
        register(event, "port_storage_shared_viewer_baseline", 20,
                PortStorageSyncGameTest::openingRegistersFreshBaselineAndViewersShareFinalPayload);
        register(event, "item_port_removal_drops_contents", 100,
                helper -> new ExtendedPortGameTest().itemPortsDropStoredItemsWhenRemoved(helper));
        register(event, "large_item_port_bounded_drops", 100,
                helper -> new ExtendedPortGameTest().largeItemPortDropsUseLegalBoundedStacks(helper));
        register(event, "extended_standalone_capabilities", 100,
                helper -> new ExtendedPortGameTest().standaloneExtendedPortsExposeItemFluidAndEnergyCapabilities(helper));
        register(event, "extended_combined_port_appearance", 100,
                helper -> new ExtendedPortGameTest().extendedCombinedPortPublishesFormedAppearance(helper));
        register(event, "item_input_ejection_stops_after_first_target", 100, helper -> new AutoIOGameTest().itemInputEjectionStopsAfterFirstTarget(helper));
        register(event, "item_input_ejection_continues_after_partial_target", 100, helper -> new AutoIOGameTest().itemInputEjectionContinuesAfterPartialTarget(helper));
        register(event, "item_input_ejection_preserves_remainder", 100, helper -> new AutoIOGameTest().itemInputEjectionPreservesRemainder(helper));
        register(event, "fluid_input_ejection_stops_after_first_target", 100, helper -> new AutoIOGameTest().fluidInputEjectionStopsAfterFirstTarget(helper));
        register(event, "fluid_input_ejection_continues_after_partial_target", 100, helper -> new AutoIOGameTest().fluidInputEjectionContinuesAfterPartialTarget(helper));
        register(event, "fluid_input_ejection_preserves_remainder", 100, helper -> new AutoIOGameTest().fluidInputEjectionPreservesRemainder(helper));
        register(event, "energy_input_ejection_stops_after_first_target", 100, helper -> new AutoIOGameTest().energyInputEjectionStopsAfterFirstTarget(helper));
        register(event, "energy_input_ejection_continues_after_partial_target", 100, helper -> new AutoIOGameTest().energyInputEjectionContinuesAfterPartialTarget(helper));
        register(event, "energy_input_ejection_preserves_remainder", 100, helper -> new AutoIOGameTest().energyInputEjectionPreservesRemainder(helper));
        register(event, "port_menu_direction", 100, helper -> new PortMenuDirectionGameTest().itemBusMenuAllowsContainerSlotTransfers(helper));
        register(event, "spectator_menu_opening", 20, SpectatorMenuGameTest::allBlockMenusCarryOpeningData);
        register(event, "shared_multiblock_teardown", 100, helper -> new SharedMultiblockIoGameTest().sharedEnergyPortFormsBothControllersAndSurvivesOneTeardown(helper));
        register(event, "shared_multiblock_input", 100, helper -> new SharedMultiblockIoGameTest().sharedInputPartiallyStartsBothControllers(helper));
        register(event, "shared_multiblock_energy", 100, helper -> new SharedMultiblockIoGameTest().finiteSharedEnergyRotatesTickGrantsBetweenLanes(helper));
        register(event, "shared_multiblock_exclusive_replacement", 100,
                helper -> new SharedMultiblockIoGameTest().exclusiveReplacementInvalidatesOneSharedController(helper));
        register(event, "smart_interface", 100, helper -> new SmartInterfaceGameTest().bindsDefaultValueAndWritesRecipeOutput(helper));
        register(event, "network_interface_same_block_state_replacement", 100,
                helper -> new NetworkInterfaceGameTest().sameBlockStateReplacementPreservesConnections(helper));
        register(event, "network_interface_block_replacement", 100,
                helper -> new NetworkInterfaceGameTest().blockReplacementClearsPeerConnections(helper));
        register(event, "tag_component_ingredient", 100, helper -> new TagComponentIngredientGameTest().tagIngredientMatchesComponentPredicate(helper));
        register(event, "terminal_build", 100, helper -> new TerminalAssemblyGameTest().buildSkipsOccupiedPositionsAndPlacesOnlyMissingBlocks(helper));
        register(event, "terminal_build_flowing_replacement", 100,
                helper -> new TerminalAssemblyGameTest().buildReplacesFlowingLiquidsButProtectsOccupiedBlocks(helper));
        register(event, "terminal_build_creative_fluid", 100,
                helper -> new TerminalAssemblyGameTest().creativeBuildPlacesOnlyFluidSources(helper));
        register(event, "ae2_terminal_fluid_build", 100,
                helper -> new AE2TerminalGameTest().boundAccessPointBuildsFluidSources(helper));
        register(event, "ae2_terminal_fluid_failure_safety", 100,
                helper -> new StructureFluidGameTest().failedPlacementsPreserveFluidStorage(helper));
        register(event, "terminal_service_rejects_non_terminal", 100,
                helper -> new TerminalAssemblyGameTest().terminalServiceRejectsActionsWithoutHeldTerminal(helper));
        register(event, "terminal_service_rejects_level_without_controller", 100,
                helper -> new TerminalAssemblyGameTest().terminalServiceRejectsLevelSelectionWithoutController(helper));
        register(event, "terminal_service_normalizes_stale_stage", 100,
                helper -> new TerminalAssemblyGameTest().terminalServiceNormalizesStaleStageBeforeBuildAndDemolish(helper));
        register(event, "terminal_demolish", 100, helper -> new TerminalAssemblyGameTest().demolishSkipsAirAndNonMatchingBlocks(helper));
        register(event, "terminal_demolish_rejected_drop", 100,
                helper -> new TerminalAssemblyGameTest().demolishStopsBeforeRejectedDropAndPreservesLaterBlocks(helper));
        register(event, "terminal_storage_resolver", 100,
                helper -> new TerminalAssemblyGameTest().storageResolverRejectsUnavailableContainerTargets(helper));
        register(event, "terminal_block_storage", 100,
                helper -> new TerminalAssemblyGameTest().blockStorageAcceptsCompleteStacksAndRollsBackPartialInsertions(helper));
        register(event, "ae2_terminal_native_linking", 20,
                helper -> new AE2TerminalGameTest().nativeMenuLinksAndRelinksWithoutChangingSettings(helper));
        register(event, "ae2_terminal_access_point", 100,
                helper -> new AE2TerminalGameTest().boundAccessPointTransfersBuildAndDemolishMaterials(helper));
        register(event, "terminal_expandable_demolish_stage_two", 100, helper -> new TerminalAssemblyGameTest().demolishExpandableFormedStageTwoRemovesCompleteSnapshot(helper));
        register(event, "terminal_expandable_missing_materials_stage_one", 100, helper -> new TerminalAssemblyGameTest().defaultBuildMissingMaterialsExcludeStageTwo(helper));
        register(event, "terminal_build_missing_stage_one_partial", 100, helper -> new TerminalAssemblyGameTest().survivalBuildRejectsWhenStageOneMaterialsAreMissing(helper));
        register(event, "terminal_build_already_formed_multi_stage", 100, helper -> new TerminalAssemblyGameTest().buildAlreadyFormedMultiStageReportsSpecificStage(helper));
        register(event, "terminal_build_completion_only_selected_stage", 100, helper -> new TerminalAssemblyGameTest().buildCompletionVerifiesOnlySelectedStageForMultiStageMachine(helper));
        register(event, "terminal_build_completion_no_stage_message_single_stage", 100, helper -> new TerminalAssemblyGameTest().buildCompletionOmitsStageFormedMessageForSingleStageMachine(helper));
        register(event, "terminal_verify_stage_no_message_single_stage", 100, helper -> new TerminalAssemblyGameTest().verifyStageSuppressesFormMessageForSingleStageMachine(helper));
        register(event, "terminal_build_across_ticks_duplicate", 200,
                helper -> new TerminalAssemblyGameTest().buildCompletesAcrossTicksAndRejectsDuplicateSubmission(helper));
        register(event, "terminal_build_structure_diagnostic", 100,
                helper -> new TerminalAssemblyGameTest().completedBuildRequestsStructureDiagnostic(helper));
        register(event, "terminal_incremental_invalidation", 200,
                 helper -> new TerminalAssemblyGameTest().incrementalScanRestartsAfterPendingInvalidation(helper));
        register(event, "terminal_falling_block_invalidation", 100,
                helper -> new TerminalAssemblyGameTest().fallingBuildBlockInvalidatesFormedStructure(helper));
        register(event, "terminal_small_structure_diagnostic", 100,
                helper -> new TerminalAssemblyGameTest().smallStructureDiagnosticIsDeliveredAfterNextScan(helper));
        register(event, "terminal_build_disconnect_refund", 100,
                helper -> new TerminalAssemblyGameTest().disconnectedBuilderDropsReservedMaterials(helper));
        register(event, "module_connection_host_empty", 100, helper -> new ModuleConnectionGameTest().hostFormsWithNoInstalledModules(helper));
        register(event, "module_connection_module_disconnected", 100, helper -> new ModuleConnectionGameTest().moduleFormsIndependentlyButCannotRunWithoutHost(helper));
        register(event, "module_connection_connected", 100, helper -> new ModuleConnectionGameTest().sharedCouplerConnectsModuleAndEnablesHostGatedRecipes(helper));
        register(event, "module_connection_interface_conflict", 100, helper -> new ModuleConnectionGameTest().sharedInterfaceInvalidatesHost(helper));
        register(event, "mekanism_chemical_structure_constraints", 20,
                helper -> new MekanismPortGameTest().chemicalStructureChecksCountsAndMinimumTiers(helper));
        register(event, "port_container_mekanism_chemical_direction", 20,
                helper -> new MekanismContainerTransferGameTest().chemicalTankFollowsPortDirection(helper));
        register(event, "port_container_mekanism_fluid_partial", 20,
                helper -> new MekanismContainerTransferGameTest().fluidTankTransfersOnlyAvailableCapacity(helper));
        register(event, "port_container_mekanism_chemical_partial_mismatch", 20,
                helper -> new MekanismContainerTransferGameTest().chemicalTankRetainsRemainderAndRejectsMismatch(helper));
        register(event, "port_container_world_mekanism_chemical", 20,
                helper -> new MekanismContainerTransferGameTest().worldChemicalTankUsesExtractionFirst(helper));
        register(event, "port_container_world_mekanism_fluid", 20,
                helper -> new MekanismContainerTransferGameTest().worldFluidTankUsesExtractionFirst(helper));
        register(event, "mekanism_single_tier_structure_constraints", 20,
                helper -> new MekanismPortGameTest().singleTierStructureChecksFamilyAndDirection(helper));
        register(event, "mekanism_single_tier_structure_counts", 20,
                helper -> new MekanismPortGameTest().singleTierStructureChecksCounts(helper));
        register(event, "mekanism_normal_chemical_radioactive_rejection", 100,
                helper -> new MekanismPortGameTest().normalChemicalPortRejectsRadioactiveAndAcceptsNonRadioactive(helper));
        register(event, "mekanism_radioactive_chemical_only_accepts_radioactive", 100,
                helper -> new MekanismPortGameTest().radioactiveChemicalPortRejectsNonRadioactiveAndAcceptsRadioactive(helper));
        register(event, "mekanism_radioactive_chemical_wrench_protection", 100,
                helper -> new MekanismPortGameTest().wrenchPreservesNonEmptyRadioactiveChemicalPort(helper));
        register(event, "mekanism_normal_chemical_capacities_match_tiers", 100,
                helper -> new MekanismPortGameTest().normalChemicalCapacitiesMatchDeclaredTiers(helper));
        register(event, "mekanism_radioactive_chemical_capacity_512k", 100,
                helper -> new MekanismPortGameTest().radioactiveChemicalCapacityIsFixedTier(helper));
        register(event, "mekanism_heat_temperature_requirement_reads_only", 100,
                helper -> new MekanismPortGameTest().heatTemperatureRequirementReadsWithoutConsumingHeat(helper));
        register(event, "mekanism_heat_output_handle_heat_increases_storage", 100,
                helper -> new MekanismPortGameTest().heatOutputHandleHeatIncreasesStoredHeat(helper));
        register(event, "mekanism_heat_output_rejects_external_input", 100,
                helper -> new MekanismPortGameTest().heatOutputCapabilityRejectsExternalHeatInput(helper));
        register(event, "mekanism_heat_port_loses_heat_to_environment", 100,
                helper -> new MekanismPortGameTest().heatPortLosesHeatToItsEnvironment(helper));
        register(event, "mekanism_heat_output_port_loses_heat_to_environment", 100,
                helper -> new MekanismPortGameTest().heatOutputPortLosesHeatToItsEnvironment(helper));
        register(event, "mekanism_heat_output_port_does_not_absorb_ambient", 100,
                helper -> new MekanismPortGameTest().heatOutputPortDoesNotAbsorbAmbientHeat(helper));
        register(event, "mekanism_heat_input_port_does_not_absorb_ambient", 100,
                helper -> new MekanismPortGameTest().heatInputPortDoesNotAbsorbAmbientHeat(helper));
        register(event, "mekanism_heat_port_uses_standard_adjacent_exchange", 100,
                helper -> new MekanismPortGameTest().heatPortUsesStandardAdjacentExchange(helper));
        register(event, "mekanism_ambient_heat_port_does_not_emit_baseline", 100,
                helper -> new MekanismPortGameTest().ambientHeatPortDoesNotEmitBaselineHeat(helper));
        register(event, "mekanism_heat_input_port_does_not_transfer_to_output", 100,
                helper -> new MekanismPortGameTest().heatInputPortDoesNotTransferHeatToAdjacentOutput(helper));
        register(event, "mekanism_heat_output_port_does_not_transfer_to_output", 100,
                helper -> new MekanismPortGameTest().heatOutputPortDoesNotTransferHeatToAdjacentOutput(helper));
        register(event, "mekanism_heat_output_port_transfers_to_thermodynamic_conductor", 100,
                helper -> new MekanismPortGameTest().heatOutputPortTransfersHeatToThermodynamicConductor(helper));
        register(event, "mekanism_thermodynamic_conductor_does_not_heat_output_port", 100,
                helper -> new MekanismPortGameTest().thermodynamicConductorDoesNotHeatOutputPort(helper));
        register(event, "mekanism_chemical_input_auto_imports", 160,
                helper -> new MekanismPortGameTest().chemicalInputAutoImportsFromAdjacentOutput(helper));
        register(event, "mekanism_chemical_input_ejects_to_first_target", 100,
                helper -> new MekanismPortGameTest().chemicalInputEjectionSpreadsAcrossDirectionsAndEmptiesSource(helper));
        register(event, "mekanism_chemical_input_ejection_preserves_remainder", 100,
                helper -> new MekanismPortGameTest().chemicalInputEjectionPreservesRemainderAgainstPartialTarget(helper));
        register(event, "mekanism_chemical_and_heat_persist_across_reload", 100,
                helper -> new MekanismPortGameTest().chemicalAndHeatContentsPersistAcrossReload(helper));
        register(event, "mekanism_unavailable_bridge_still_registers_io", 100,
                helper -> new MekanismPortGameTest().unavailableBridgeLeavesBuilderWorkingThroughCustomRecipeIo(helper));
        register(event, "mekanism_heat_input_accepts_only_external_input", 100,
                helper -> new MekanismPortGameTest().heatInputCapabilityAcceptsOnlyExternalHeatInput(helper));
        register(event, "mekanism_formed_port_reflects_texture_change", 100,
                helper -> new MekanismPortGameTest().formedMultiblockPortReflectsBaseTextureChange(helper));
        if (ModList.get().isLoaded("ae2")) {
            if (ModList.get().isLoaded("ae2lt")) {
                register(event, "ae2lt_stocking_rebind_watcher_transaction", 160,
                        helper -> new LightningInterfaceGameTest().stockingRebindWatcherAndTransaction(helper));
                register(event, "ae2lt_output_budget_async_lifecycle", 160,
                        helper -> new LightningInterfaceGameTest().outputBudgetAndAsyncLifecycle(helper));
                register(event, "ae2lt_local_interface_native_persistence", 40,
                        helper -> new LightningInterfaceGameTest().localInterfacesPersistNativeKeys(helper));
                register(event, "ae2lt_native_pattern_admission_persistence", 160,
                        helper -> new LightningPatternGameTest().nativePatternAdmissionAndPersistence(helper));
                register(event, "ae2lt_mixed_lane_long_return", 40,
                        helper -> new LightningPatternGameTest().mixedLaneRollbackLongPersistenceAndNativeReturn(helper));
                register(event, "ae2lt_pattern_batch_native_planning_admission", 160,
                        PatternBatchPlanningGameTest::lightningBatchesPreserveNativePlanningAndAdmission);
                register(event, "pattern_batch_probability_lightning", 160,
                        PatternBatchPlanningGameTest::probabilisticLightningOutputsPreserveNativePlanningAndAdmission);
            }
            register(event, "ae2_pattern_return_long_balances", 40,
                    helper -> new PatternReturnInventoryGameTest().nativeSlotsAndExactLongBalances(helper));
            register(event, "ae2_pattern_return_components_persistence", 40,
                    helper -> new PatternReturnInventoryGameTest().componentKeysPersistenceAndTypeLimit(helper));
            register(event, "ae2_pattern_return_transactions", 40,
                    helper -> new PatternReturnInventoryGameTest().transactionalReturnsAndOutputPlanning(helper));
            register(event, "ae2_pattern_return_old_save_menu_drops", 40,
                    helper -> new PatternReturnInventoryGameTest().oldSaveMenuAndBoundedDrops(helper));
            register(event, "ae2_pattern_return_large_cpu_execution", 160,
                    helper -> new AE2PatternOutputComponentGameTest().craftingCpuSettlesLargeInputsOutputsAndLeftovers(helper));
            register(event, "ae2_pattern_batch_exact_excess_return", 160,
                    helper -> new AE2PatternOutputComponentGameTest().requestExcessReturnsExactlyOnce(helper));
            register(event, "ae2_pattern_return_native_transfer_menu", 40,
                    helper -> new PatternReturnInventoryGameTest().nativeTransferAndMenuInsertionConserveResources(helper));
            register(event, "ae2_pattern_atomic_admission", 20, RunningRecipeGameTest::patternAdmissionRollsBackRequestAndOrdinaryInput);
            register(event, "ae2_menu_host_routes", 40,
                    helper -> new InterfaceMenuGameTest().ae2HostsOpenAndReturnOwnTypes(helper));
            register(event, "ae2_menu_mirror_sync", 40,
                    helper -> new InterfaceMenuGameTest().mirrorSyncPreservesWrappedAmounts(helper));
            register(event, "ae2_menu_small_stocking_mirror", 200,
                    helper -> new AE2StockingInterfaceGameTest().smallStockingMirrorCannotMaterializeNetworkItems(helper));
            register(event, "ae2_menu_output_local_policy", 40,
                    helper -> new InterfaceMenuGameTest().outputCacheUsesLocalSlotPolicy(helper));
            register(event, "ae2_menu_async_read_only", 40,
                    helper -> new InterfaceMenuGameTest().asyncDisplayCannotMaterializeQueuedOutputs(helper));
            register(event, "ae2_menu_native_isolation", 40,
                    helper -> new InterfaceMenuGameTest().nativeAe2InterfaceRemainsEditable(helper));
            register(event, "ae2_menu_submenu_return", 40,
                    helper -> new AE2InterfaceGameTest().amountAndPriorityReturnToOwnMenu(helper));
            register(event, "ae2_menu_fuzzy_restock", 200,
                    helper -> new AE2InterfaceGameTest().fuzzyCardRestocksDamagedItem(helper));
            register(event, "ae2_menu_crafting_restock_cancel", 400,
                    helper -> new AE2InterfaceGameTest().craftingCardActuallyRestocksAndCancels(helper));
            register(event, "ae2_pattern_menu_terminal", 100,
                    helper -> new AE2PatternInterfaceGameTest().patternMenuStateAndTerminalVisibility(helper));
            if (ModList.get().isLoaded("extendedae")) {
                register(event, "eae_pattern_return_long_balances", 40,
                        helper -> new PatternReturnInventoryGameTest().extendedInterfaceUsesTheSameOverflow(helper));
                register(event, "eae_menu_host_routes", 40,
                        helper -> new InterfaceMenuGameTest().extendedHostsOpenAndReturnOwnTypes(helper));
                register(event, "eae_menu_page_semantics", 40,
                        helper -> new InterfaceMenuGameTest().extendedPagesDoNotMutateSemanticLists(helper));
                register(event, "eae_menu_pick_all_page", 40,
                        helper -> new InterfaceMenuGameTest().extendedPickAllOnlyCollectsCurrentPage(helper));
                register(event, "eae_menu_quick_craft_page", 40,
                        helper -> new InterfaceMenuGameTest().extendedQuickCraftResetsOnEffectivePageChange(helper));
                register(event, "eae_menu_player_shift_page", 40,
                        helper -> new InterfaceMenuGameTest().extendedPlayerShiftUsesOnlyCurrentPage(helper));
                register(event, "eae_menu_small_stocking_mirror", 200,
                        helper -> new AE2StockingInterfaceGameTest().extendedSmallStockingMirrorCannotMaterializeNetworkItems(helper));
                register(event, "eae_menu_output_conservation", 40,
                        helper -> new InterfaceMenuGameTest().extendedOutputConservesItemsAndFluids(helper));
                register(event, "eae_menu_native_isolation", 40,
                        helper -> new InterfaceMenuGameTest().nativeExtendedInterfaceRemainsEditable(helper));
                register(event, "eae_menu_amount_page_return", 40,
                        helper -> new AE2InterfaceGameTest().extendedAmountReturnRetainsTransientPage(helper));
                register(event, "eae_menu_fuzzy_restock", 200,
                        helper -> new AE2InterfaceGameTest().extendedFuzzyCardRestocksDamagedItem(helper));
                register(event, "eae_menu_crafting_restock_cancel", 400,
                        helper -> new AE2InterfaceGameTest().extendedCraftingCardActuallyRestocksAndCancels(helper));
                register(event, "eae_menu_upgrade_toolbox_profiles", 40,
                        helper -> new AE2InterfaceGameTest().inputUpgradeToolboxAndOversizeAmount(helper));
                register(event, "eae_pattern_menu_terminal", 100,
                        helper -> new AE2PatternInterfaceGameTest().extendedPatternMenuStateAndTerminalVisibility(helper));
                register(event, "eae_me_extended_input_provenance", 100,
                        helper -> new AE2InterfaceGameTest().extendedInputReturnsOnlyAeOwnedResourcesAfterCommittedExtraction(helper));
                register(event, "eae_me_extended_output_menu", 100,
                        helper -> new AE2OutputInterfaceGameTest().extendedOutputMenuAllowsExtractionButBlocksInsertionAndFilters(helper));
                register(event, "eae_me_extended_pattern_slot_35", 100,
                        helper -> new AE2PatternInterfaceGameTest().extendedPatternSlot35IsAdvertisedAndReturnsThroughCraftingMachine(helper));
            }
            register(event, "ae2_me_input_interface", 200,
                    helper -> new AE2InterfaceGameTest().interfaceFeedsMmcrInputs(helper));
            register(event, "ae2_me_input_manual_cache", 100,
                    helper -> new AE2InterfaceGameTest().inputInterfaceDoesNotReturnManualCacheItems(helper));
            register(event, "ae2_me_input_memory_card", 100,
                    helper -> new AE2InterfaceGameTest().inputInterfaceMemoryCardRoundTrip(helper));
            register(event, "ae2_me_stocking_input_interface", 100,
                    helper -> new AE2StockingInterfaceGameTest().stockingInterfaceReadsAndWatchesNetworkStorage(helper));
            // register(event, "eae_me_oversize_stocking_watcher", 100,
            //         helper -> new AE2StockingInterfaceGameTest().oversizeStockingWatcherTracksLargeConfiguredKey(helper));
            register(event, "task2_ae2_output_lifecycle", 100,
                    helper -> new Task2AE2OutputGameTest().outputWakeUpAndActiveNodeLifecycle(helper));
            register(event, "ae2_me_output_interface", 200,
                    helper -> new AE2OutputInterfaceGameTest().outputInterfaceDrainsToNetworkAndLocksConfig(helper));
            register(event, "ae2_me_async_output_interface", 200,
                    helper -> new AE2AsyncOutputInterfaceGameTest().asyncOutputInterfaceDrainsServiceAndSurvivesDisconnect(helper));
            register(event, "ae2_me_pattern_memory_card", 100,
                    helper -> new AE2PatternInterfaceGameTest().patternInterfaceMemoryCardRoundTrip(helper));
            register(event, "ae2_me_pattern_interface", 160,
                    helper -> new AE2PatternInterfaceGameTest().patternInterfaceRestoresPatternsAndWakesNativeWork(helper));
            register(event, "ae2_me_pattern_native_return_notification", 100,
                    helper -> new AE2PatternInterfaceGameTest().nativeReturnDrainNotifiesWithoutBlockEntityPolling(helper));
            register(event, "ae2_me_pattern_interface_request", 100,
                    helper -> new AE2PatternInterfaceGameTest().patternRequestStartsControllerWithRemainingOrdinaryInput(helper));
            register(event, "ae2_pending_pattern_save", 40, PendingPatternLifecycleGameTest::pendingSaveAndOldSave);
            register(event, "ae2_standard_cpu_pattern_accounting", 240,
                    helper -> new AE2PatternInterfaceGameTest().craftingCpuDispatchesStandardRequestsAndAccountsForOutputs(helper));
            register(event, "ae2_standard_cpu_pattern_cancellation", 240,
                    helper -> new AE2PatternInterfaceGameTest().craftingCpuCancellationKeepsAcceptedRequests(helper));
            register(event, "ae2_pending_queue_long_returns", 40,
                    helper -> new PatternReturnInventoryGameTest().pendingQueueLongBalancesRollbackAndPersistence(helper));
            register(event, "ae2_pending_queue_rollback", 20,
                    helper -> new PendingPatternQueueGameTest().rollbackRestoresGroupAndCounts(helper));
            register(event, "ae2_pending_queue_large", 20,
                    helper -> new PendingPatternQueueGameTest().largeCompressedGroupRefundsAtomically(helper));
            register(event, "ae2_pending_queue_callback", 20,
                    helper -> new PendingPatternQueueGameTest().rootCallbackFailurePreservesCommittedOwnership(helper));
            register(event, "ae2_pending_queue_legacy", 20,
                    helper -> new PendingPatternQueueGameTest().oldSaveHasEmptyPendingQueue(helper));
            register(event, "ae2_pending_queue_persistence", 20,
                    helper -> new PendingPatternQueueGameTest().pendingQueueRoundTripPreservesComponentsAndLongCounts(helper));
            register(event, "ae2_pending_queue_damaged", 20,
                    helper -> new PendingPatternQueueGameTest().damagedOrFutureDataIsPreservedAndBlocked(helper));
            register(event, "ae2_legacy_pattern_refund_only", 40,
                    PendingPatternLifecycleGameTest::legacyAcceptedInputsAreRefundedNotExecuted);
            register(event, "ae2_legacy_pattern_one_group_per_tick", 40,
                    PendingPatternLifecycleGameTest::legacyTickAttemptsOnlyOneGroup);
            register(event, "ae2_legacy_pattern_old_carrier_refund", 40,
                    PendingPatternLifecycleGameTest::oldCarrierOnlyRefunds);
            register(event, "ae2_legacy_pattern_unknown_carrier", 40,
                    PendingPatternLifecycleGameTest::unknownCarrierPreservesBlockedOwnership);
            register(event, "ae2_legacy_pattern_full_return_reload", 40,
                    helper -> new PatternReturnInventoryGameTest().fullReturnsKeepLegacyGroupAcrossReload(helper));
            register(event, "ae2_pending_pattern_carrier", 40, PendingPatternLifecycleGameTest::ordinaryCarrierRoundTrip);
            if (ModList.get().isLoaded("extendedae")) {
                register(event, "eae_pending_pattern_carrier", 40, PendingPatternLifecycleGameTest::extendedCarrierRoundTrip);
            }
            register(event, "ae2_me_pattern_cpu_output_components", 100,
                    helper -> new AE2PatternOutputComponentGameTest().craftingCpuPushesResolvedEnchantmentOutput(helper));
            // No need for this gameTest
            // register(event, "ae2_cpu_factory_pattern_batch", 180,
            //         helper -> new AE2PatternInterfaceGameTest().craftingCpuBatchesFactoryPatternAcrossLanesAndAccountsForOutputs(helper));
        }
        register(event, "appflux_me_flux_input_interface", 100,
                helper -> new AppliedFluxInterfaceGameTest().portBlocksResolveToFluxBlockEntities(helper));
        register(event, "appflux_me_flux_input_grid_node", 100,
                helper -> new AppliedFluxInterfaceGameTest().gridNodeHostsAreExposedAndExternalFeIsSuppressed(helper));
        register(event, "appflux_me_flux_input_capability_family", 100,
                helper -> new AppliedFluxInterfaceGameTest().fluxCapabilitiesBelongToEnergyFamilyWithoutTransferFacet(helper));
        register(event, "appflux_me_flux_output_interface", 100,
                helper -> new AppliedFluxInterfaceGameTest().controllerFormsWithFluxPortsAndResolvesEnergyFamily(helper));
    }

    public static void registerMachineDefinitions(RegisterMachineDefinitionsEvent event) {
        registerMachineDefinitions(RegistrationAdapters.core(event));
    }

    public static void registerMachineDefinitions(MachineDefinitionRegistration event) {
        event.registerMachine(MachineBuilder.machine(ControllerPlacementGameTest.REQUIRED_VERTICAL_MACHINE)
                .displayNameKey("machine.mmcr_test.controller_placement_required_vertical")
                .controller(controller -> controller.requireVerticalFacing()).build());
        event.registerMachine(MachineBuilder.machine(ControllerUiGameTest.MACHINE_ID)
                .displayNameKey("gui.mmcr.ui.test_machine")
                .allowMultithreading()
                .factory(factory -> factory.hasFactory(true).threadLimit(4)).build());
        for (String name : List.of("test_cube", "controller_tick", "task7_tick_io", "task7_recipe_snapshot", "data_storage_tick", "upgrade_bus_test", "smart_interface_test", "iron_compressor",
                "distillation_tower_test", "expandable_structure_stages", "expandable_structure_vertical_roll", "falling_block_structure", "fluid_assembly")) {
            Identifier id = MMCR.id(name);
            MachineBuilder builder = MachineBuilder.machine(id);
            builder.displayNameKey("machine.mmcr_test." + name);
            if (name.equals("data_storage_tick")) {
                builder.tickBehavior(tick -> tick.serverTick(context -> {
                    if (!context.isDue(20)) return;
                    DataStorage storage = context.dataStorage();
                    if (storage == null) return;
                    long ticks = storage.get("ticks").map(DataValue::longValue).orElse(0L);
                    var plan = context.ioPlan();
                    if (!plan.simulate().inputsSatisfied()) return;
                    if (!plan.commitData(transaction -> storage.set("ticks", DataValue.of(ticks + 1L), transaction))
                            .successful()) return;
                    context.screenText().append(ControllerScreenTextScope.OPERATION,
                            MMCR.id("data_storage_tick_status"), Component.literal("ticks=" + (ticks + 1L)));
                }));
            }
            if (name.equals("expandable_structure_vertical_roll")) {
                builder.controller(controller -> controller.allowVerticalFacing());
            }
            if (name.equals("upgrade_bus_test")) {
                builder.allowModifiers();
            }
            if (name.equals("smart_interface_test")) {
                builder.smartInterface(new SmartInterfaceType("temperature", 12F, 20F, 0));
            }
            MachineDefinition definition = builder.build();
            if (name.equals("distillation_tower_test") || name.equals("expandable_structure_stages")
                    || name.equals("expandable_structure_vertical_roll")) {
                definition = new MachineDefinition(definition.id(), definition.recipePoolId(), definition.displayNameKey(), definition.controller(),
                        definition.appearance(), definition.factory(), definition.role(), definition.acceptedModuleIds(),
                        definition.maxParallelism(), definition.parallelizable(), definition.failureAction(),
                        definition.allowModifiers(), definition.allowMultithreading(), definition.maxParallelAmount(), true,
                        definition.smartInterfaceTypes(), definition.shareSmartInterfaces(), definition.smartInterfaceModifiers(),
                        definition.runningSoundId(), definition.finishSoundId(), definition.pattern());
            }
            event.registerMachine(definition);
        }
    }

    public static void registerMachineStructures(RegisterMachineStructuresEvent event) {
        if (ModList.get().isLoaded("mekanism")) {
            StartupRegistryReferenceGameTest.registerPublicReferences(event);
        }
        registerMachineStructures(RegistrationAdapters.core(event));
    }

    public static void registerMachineStructures(StructureRegistration event) {
        event.registerStructure(ControllerPlacementGameTest.REQUIRED_VERTICAL_MACHINE,
                structure -> structure.fullStructure(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.deferredBlock(
                                () -> ModBlocks.controllerFor(ControllerPlacementGameTest.REQUIRED_VERTICAL_MACHINE).get()))
                        .controller('C'))));
        event.registerStructure(ControllerUiGameTest.MACHINE_ID, structure -> structure.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("SCF")
                        .where('S', BlockPredicate.deferredBlock(() -> ModBlocks.DATA_STORAGE.get()))
                        .where('C', BlockPredicate.deferredBlock(() -> ModBlocks.controllerFor(ControllerUiGameTest.MACHINE_ID).get()))
                        .where('F', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("factory_controller").get()))
                        .controller('C'))));
        event.registerStructure(MMCR.id("fluid_assembly"), structure -> structure.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("W W", " C ", " F ")
                        .where('C', BlockPredicate.deferredBlock(() -> ModBlocks.controllerFor(MMCR.id("fluid_assembly")).get()))
                        .where('W', BlockPredicate.block(Blocks.WATER))
                        .where('F', BlockPredicate.blockState(Blocks.WATER.defaultBlockState()
                                .setValue(LiquidBlock.LEVEL, 1)))
                        .controller('C'))));
        Identifier upgradeBusBlockModifierId = MMCR.id("upgrade_bus_test_block_modifier");
        event.registerModifier(upgradeBusBlockModifierId,
                ModifierDefinition.of("duration", "input", 1.0F, "add", false));
        Identifier upgradeBusModifierId = MMCR.id("upgrade_bus_test_modifier");
        event.registerModifier(upgradeBusModifierId,
                ModifierDefinition.of("duration", "input", 1.0F, "add", false));
        event.registerModifierItem(new ItemStack(Items.NETHER_STAR), upgradeBusModifierId);
        for (String name : List.of("test_cube", "controller_tick", "task7_tick_io", "task7_recipe_snapshot", "data_storage_tick", "upgrade_bus_test", "iron_compressor",
                "distillation_tower_test", "expandable_structure_stages", "expandable_structure_vertical_roll", "falling_block_structure")) {
            Identifier id = MMCR.id(name);
            event.registerStructure(id, structure -> {
                BlockPredicate casing = BlockPredicate.deferredBlock(() -> ModBlocks.CASING.get());
                BlockPredicate controller = BlockPredicate.deferredBlock(() -> ModBlocks.controllerFor(id).get());
                if (name.equals("upgrade_bus_test")) {
                    structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("BICOB")
                            .where('B', casing)
                            .where('I', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("item_input_bus").get()))
                            .where('C', controller)
                            .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("item_output_bus").get()))
                            .controller('C'))
                            .modifier('B', ModifierUse.of(upgradeBusBlockModifierId,
                                    BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("upgrade_bus_normal").get()))));
                    return structure;
                }
                if (name.equals("task7_tick_io")) {
                    structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("IICOO")
                            .layer("  E  ")
                            .layer("  S  ")
                            .where('I', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("item_input_bus").get()))
                            .where('C', controller)
                            .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("item_output_bus").get()))
                            .where('E', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("energy_input_hatch").get()))
                            .where('S', BlockPredicate.deferredBlock(() -> ModBlocks.DATA_STORAGE.get()))
                            .controller('C')));
                    return structure;
                }
                if (name.equals("task7_recipe_snapshot")) {
                    structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("ICO")
                            .where('I', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("item_input_bus").get()))
                            .where('C', controller)
                            .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("item_output_bus").get()))
                            .controller('C')));
                    return structure;
                }
                if (name.equals("data_storage_tick")) {
                    structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("CX")
                            .where('C', controller)
                            .where('X', BlockPredicate.deferredBlock(() -> ModBlocks.DATA_STORAGE.get()))
                            .controller('C')));
                    return structure;
                }
                if (name.equals("falling_block_structure")) {
                    structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("CS")
                            .where('C', controller)
                            .where('S', BlockPredicate.block(Blocks.SAND))
                            .controller('C')));
                    return structure;
                }
                if (name.contains("expandable")) {
                    structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("CX")
                            .where('C', controller)
                            .where('X', casing)
                            .controller('C')));
                    structure.extension(stage -> stage.pattern(pattern -> pattern
                            .layer("C ", " X")
                            .where('C', controller)
                            .where('X', casing)
                            .controller('C')));
                    structure.extension(stage -> stage.pattern(pattern -> pattern
                            .layer("C X")
                            .where('C', controller)
                            .where('X', casing)
                            .controller('C')));
                    return structure;
                }
                if (name.contains("distillation")) {
                    structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("I ")
                            .layer("CE")
                            .layer("O ")
                            .where('I', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("item_input_bus").get()))
                            .where('C', controller)
                            .where('E', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("energy_input_hatch").get()))
                            .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("fluid_output_hatch").get()))
                            .controller('C')));
                    structure.extension(stage -> stage.pattern(pattern -> pattern
                            .layer("OC")
                            .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("fluid_output_hatch").get()))
                            .where('C', controller)
                            .controller('C')));
                    structure.extension(stage -> stage.pattern(pattern -> pattern
                            .layer("C", "O")
                            .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("fluid_output_hatch").get()))
                            .where('C', controller)
                            .controller('C')));
                    return structure;
                }
                if (name.equals("iron_compressor")) {
                    structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("XXX", " I ")
                            .layer("XCX", "  E")
                            .layer("XXX", " O ")
                            .where('X', casing)
                            .where('C', controller)
                            .where('I', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("item_input_bus").get()))
                            .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("item_output_bus").get()))
                            .where('E', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("energy_input_hatch").get()))
                            .controller('C')));
                    return structure;
                }
                structure.fullStructure(stage -> stage.pattern(pattern -> pattern
                        .layer("XXX").layer("XCX").layer("XXX")
                        .where('X', casing)
                        .where('C', controller)
                        .controller('C')));
                if (name.contains("expandable") || name.contains("distillation")) {
                    structure.extension(stage -> stage.pattern(pattern -> pattern
                            .layer("XXX").layer("XCX").layer("XXX")
                            .where('X', casing)
                            .where('C', controller)
                            .controller('C')));
                    structure.extension(stage -> stage.pattern(pattern -> pattern
                            .layer("XXX").layer("XCX").layer("XXX")
                            .where('X', casing)
                            .where('C', controller)
                            .controller('C')));
                }
                return structure;
            });
        }
    }

    public static void registerRecipes(RegisterMachineRecipesEvent event) {
        registerRecipes(RegistrationAdapters.core(event));
    }

    @SubscribeEvent
    public static void registerControllerUiProtocols(RegisterControllerUiProtocolsEvent event) {
        ControllerUiGameTest.registerProtocols(event);
    }

    public static void registerRecipes(MachineRecipeRegistration event) {
        event.registerRecipe(MachineRecipeBuilder.recipe(MMCR.id("distillation_test_recipe"))
                        .recipePool(MMCR.id("distillation_tower_test"))
                .duration(20).inputItem(Items.COAL, 1).outputFluid(Fluids.WATER, 1).build());
    }

    private static void register(RegisterGameTestsEvent event, String name, int maxTicks, Consumer<GameTestHelper> test) {
        register(event, name, maxTicks, Identifier.fromNamespaceAndPath("minecraft", "empty"), test);
    }

    private static void register(RegisterGameTestsEvent event, String name, int maxTicks, Identifier structure,
                                 Consumer<GameTestHelper> test) {
        Holder<TestEnvironmentDefinition<?>> environment = Holder.direct(new TestEnvironmentDefinition.AllOf());
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment,
                structure,
                maxTicks,
                0,
                true,
                Rotation.NONE,
                false,
                1,
                1,
                false,
                0);
        registerTest(event, MMCR.id(name), new SimpleGameTest(data, name, test));
    }

    private static void registerTest(RegisterGameTestsEvent event, Identifier id, GameTestInstance instance) {
        try {
            event.getClass().getMethod("registerTest", Identifier.class, GameTestInstance.class).invoke(event, id, instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to register GameTest " + id, e);
        }
    }

    private static final class SimpleGameTest extends GameTestInstance {
        private final String name;
        private final Consumer<GameTestHelper> test;

        private SimpleGameTest(TestData<Holder<TestEnvironmentDefinition<?>>> data, String name, Consumer<GameTestHelper> test) {
            super(data);
            this.name = name;
            this.test = test;
        }

        @Override
        public void run(GameTestHelper helper) {
            test.accept(helper);
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public MapCodec<? extends GameTestInstance> codec() {
            return MapCodec.unit(this);
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("MMCR " + name);
        }
    }
}
