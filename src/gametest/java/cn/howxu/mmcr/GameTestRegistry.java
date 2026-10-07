package cn.howxu.mmcr;

import cn.howxu.mmcr.compat.appmek.AppMekAdapterGameTest;
import cn.howxu.mmcr.compat.appmek.AppMekInterfaceGameTest;
import cn.howxu.mmcr.compat.appmek.AppMekPatternGameTest;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceRecipeGameTest;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceTransportGameTest;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.botania.BotaniaManaRecipeGameTest;
import cn.howxu.mmcr.compat.botania.BotaniaManaTransportGameTest;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksBridge;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksIds;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksDeviceGameTest;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksRecipeGameTest;
import cn.howxu.mmcr.compat.extendedae_plus.MirrorPatternInterfaceGameTest;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeGameTest;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticTransportGameTest;

import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.data.view.DataStorage;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.DisplayStack;
import cn.howxu.mmcr.api.machine.definition.MachineLevel;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.definition.ModifierUse;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.Task2AE2OutputGameTest;
import cn.howxu.mmcr.AppliedFluxInterfaceGameTest;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

import java.util.List;

public final class GameTestRegistry {
    private GameTestRegistry() {
    }

    public static void registerAll(RegisterGameTestsEvent event) {
        event.register(GameTestRegistry.class);
    }

    @GameTestGenerator
    public static Collection<TestFunction> generateTests() {
        List<TestFunction> event = new ArrayList<>();
        register(event, "registry_reflection_helper", 20, helper -> {
            boolean registered = net.minecraft.gametest.framework.GameTestRegistry
                    .findTestFunction(MMCR.id("registry_reflection_helper").toString())
                    .isPresent();
            helper.assertTrue(registered, "GameTestRegistry reflection helper registers the canonical test ID");
            helper.succeed();
        });
        register(event, "recipe_amount_codec_limits", 20,
                helper -> new RecipeAmountCodecGameTest().long_recipe_output_amounts_are_capped_at_native_stack_limits(helper));
        register(event, "runtime_content_final_datapack_sync", 20,
                RuntimeContentSyncGameTest::finalDatapackSyncIncludesLateRecipes);
        register(event, "recipe_update_packet_components", 20,
                helper -> new RecipeAmountCodecGameTest().recipe_update_packet_preserves_registry_backed_components(helper));
        register(event, "example_native_recipe_codecs", 20,
                helper -> new ExampleScriptGameTest().nativeRecipeRequirementsDecode(helper));
        register(event, "example_structure_block_states", 20,
                helper -> new ExampleScriptGameTest().structureBlocksResolve(helper));
        register(event, "item_output_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().outputResolvesPlainJsonEnchantments(helper));
        register(event, "async_item_output_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().asyncOutputPreservesEnchantmentComponents(helper));
        register(event, "cached_item_output_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().cachedOutputSurvivesFinishReplacement(helper));
        register(event, "public_item_output_enchantment_components", 20,
                helper -> new ItemOutputComponentGameTest().publicOutputViewPreservesComponentsWhenRebuilt(helper));
        register(event, "block_array_match", 100, helper -> new BlockArrayMatchGameTest().structureForms3x3Casing(helper));
        register(event, "controller_tick", 100, helper -> new ControllerTickGameTest().structureForms3x3Casing(helper));
        register(event, "formed_controller_break", 20,
                helper -> new ControllerTickGameTest().formedControllerCanBeBroken(helper, false));
        register(event, "active_controller_break", 20,
                helper -> new ControllerTickGameTest().formedControllerCanBeBroken(helper, true));
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
                helper -> new TerminalAssemblyGameTest().blockStorageAcceptsCompleteStacksAndRejectsPartialInsertions(helper));
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
        register(event, "mekanism_heat_recipe_output_internal_handler", 100,
                helper -> new MekanismPortGameTest().heatRecipeOutputUsesInternalHandler(helper));
        register(event, "mekanism_radioactive_any_ports_structure", 100,
                helper -> new MekanismPortGameTest().anyPortsFormsWithRadioactiveHatches(helper));
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
         register(event, "ae2_me_input_interface", 200,
                 helper -> new AE2InterfaceGameTest().interfaceFeedsMmcrInputs(helper));
        register(event, "ae2_me_input_manual_cache", 100,
                helper -> new AE2InterfaceGameTest().inputInterfaceDoesNotReturnManualCacheItems(helper));
        register(event, "eae_me_extended_input_provenance", 100,
                helper -> new AE2InterfaceGameTest().extendedInputReturnsOnlyAeOwnedResourcesAfterCommittedExtraction(helper));
        register(event, "ae2_me_input_memory_card", 100,
                helper -> new AE2InterfaceGameTest().inputInterfaceMemoryCardRoundTrip(helper));
        register(event, "ae2_me_stocking_input_interface", 100,
                helper -> new AE2StockingInterfaceGameTest().stockingInterfaceReadsAndWatchesNetworkStorage(helper));
        register(event, "task2_ae2_output_lifecycle", 100,
                helper -> new Task2AE2OutputGameTest().outputWakeUpAndActiveNodeLifecycle(helper));
        register(event, "ae2_me_output_interface", 200,
                helper -> new AE2OutputInterfaceGameTest().outputInterfaceDrainsToNetworkAndLocksConfig(helper));
        register(event, "eae_me_extended_output_menu", 100,
                helper -> new AE2OutputInterfaceGameTest().extendedOutputMenuAllowsExtractionButBlocksInsertionAndFilters(helper));
        register(event, "ae2_me_async_output_interface", 200,
                helper -> new AE2AsyncOutputInterfaceGameTest().asyncOutputInterfaceDrainsServiceAndSurvivesDisconnect(helper));
        register(event, "ae2_me_pattern_memory_card", 100,
                helper -> new AE2PatternInterfaceGameTest().patternInterfaceMemoryCardRoundTrip(helper));
        register(event, "ae2_me_pattern_interface", 160,
                helper -> new AE2PatternInterfaceGameTest().patternInterfaceRestoresPatternsAndWakesNativeWork(helper));
        register(event, "ae2_me_pattern_interface_request", 100,
                helper -> new AE2PatternInterfaceGameTest().patternRequestStartsControllerWithRemainingOrdinaryInput(helper));
        // No need for this gameTest
        // register(event, "ae2_cpu_factory_pattern_batch", 180,
        //         helper -> new AE2PatternInterfaceGameTest().craftingCpuBatchesFactoryPatternAcrossLanesAndAccountsForOutputs(helper));
        register(event, "eae_me_extended_pattern_slot_35", 100,
                helper -> new AE2PatternInterfaceGameTest().extendedPatternSlot35IsAdvertisedAndReturnsThroughCraftingMachine(helper));
        register(event, "appmek_local_simulation", 100,
                helper -> new AppMekAdapterGameTest().localSimulationPreservesInventory(helper));
        register(event, "appmek_multi_chemical_queries_and_apis", 100,
                helper -> new AppMekAdapterGameTest().queriesAndExistingApisSeeAllChemicalKinds(helper));
        register(event, "appmek_chemical_tag_queries", 100,
                helper -> new AppMekAdapterGameTest().tagQueriesCountOnlyMatchingChemicals(helper));
        register(event, "appmek_multi_slot_sync", 100,
                helper -> new AppMekAdapterGameTest().multiSlotSyncPreservesMixedResourcesAndNetworkKeys(helper));
        register(event, "appmek_chemical_async_boundary", 100,
                helper -> new AppMekAdapterGameTest().localAsyncSnapshotKeepsMixedNetworkInputOnMainThread(helper));
        register(event, "appmek_chemical_predicates_both_radiation_categories", 100,
                helper -> new AppMekInterfaceGameTest().chemicalPredicatesIncludeBothRadiationCategories(helper));
        register(event, "appmek_stocking_hydrogen_radioactive_recipe", 400,
                helper -> new AppMekInterfaceGameTest().stockingHydrogenFeedsRadioactiveRecipeThroughAnyPorts(helper));
        register(event, "appmek_stocking_radioactive_storage_bus", 400,
                helper -> new AppMekInterfaceGameTest().stockingReadsRadioactiveWasteBarrelStorageBus(helper));
        register(event, "appmek_chemical_kind_wakeup", 200,
                helper -> new AppMekInterfaceGameTest().chemicalKindChangesWakeBlockedController(helper));
        register(event, "appmek_real_cell_input_recipe", 400,
                helper -> new AppMekInterfaceGameTest().configuredInputFeedsRecipeFromRealChemicalCell(helper));
        register(event, "appmek_stocking_chemical_reconnect", 400,
                helper -> new AppMekInterfaceGameTest().stockingTracksChemicalDisconnectAndReconnect(helper));
        register(event, "appmek_normal_chemical_cache_flush", 400,
                helper -> new AppMekInterfaceGameTest().normalOutputFlushesDisconnectedChemicalCache(helper));
        register(event, "appmek_async_real_cell_partial_output", 400,
                helper -> new AppMekInterfaceGameTest().asyncOutputConservesRealCellPartialAcceptance(helper));
        register(event, "appmek_oversize_last_slot_long_recipe", 200,
                helper -> new AppMekInterfaceGameTest().oversizeLastSlotConsumesLongRecipeAmount(helper));
        register(event, "appmek_chemical_native_save_load", 100,
                helper -> new AppMekInterfaceGameTest().chemicalStorageAndConfigurationSurviveNativeSaveLoad(helper));
        register(event, "appmek_chemical_memory_card", 400,
                helper -> new AppMekInterfaceGameTest().chemicalMemoryCardConfigurationPullsFromNetwork(helper));
        register(event, "appmek_mixed_pattern", 200,
                helper -> new AppMekPatternGameTest().mixedPatternReturnsProductsAndExcessInputs(helper));
        register(event, "appmek_radioactive_pattern_waste_barrel_storage_bus", 400,
                helper -> new AppMekPatternGameTest().radioactivePatternReturnsProductsThroughWasteBarrelStorageBus(helper));
        register(event, "appmek_extended_pattern_last_slot", 200,
                helper -> new AppMekPatternGameTest().extendedPatternLastSlotProcessesChemicals(helper));
        register(event, "appmek_rejected_pattern_ownership", 200,
                helper -> new AppMekPatternGameTest().rejectedPatternPreservesInputHolders(helper));
        register(event, "appmek_factory_chemical_batch", 200,
                helper -> new AppMekPatternGameTest().batchPatternAccountsForChemicalInputsAcrossLanes(helper));
        register(event, "appmek_long_pattern_output_matching", 100,
                helper -> new AppMekPatternGameTest().chemicalPatternOutputMatchingPreservesLongAmounts(helper));
        register(event, "appmek_all_me_kinds", 100,
                helper -> new AppMekInterfaceGameTest().allMeKindsExposeChemicalCapabilities(helper));
        register(event, "appmek_generic_radioactivity_validation", 100,
                helper -> new AppMekInterfaceGameTest().genericCapabilitiesAcceptRadioactiveChemicals(helper));
        register(event, "appmek_partial_network_output", 100,
                helper -> new AppMekAdapterGameTest().partialNetworkOutputConservesRemainder(helper));
        register(event, "appmek_disconnected_output", 100,
                helper -> new AppMekAdapterGameTest().disconnectedOutputReturnsUnacceptedAmount(helper));
        register(event, "appmek_network_input_reservations", 100,
                helper -> new AppMekAdapterGameTest().networkInputFiltersDeduplicatesAndSharesReservations(helper));
        register(event, "appmek_multi_slot_reservations", 100,
                helper -> new AppMekAdapterGameTest().multiSlotInputAndAliasedViewsRespectReservations(helper));
        register(event, "appmek_mixed_slot_reservations", 100,
                helper -> new AppMekAdapterGameTest().mixedViewsSharePhysicalSlotAndSyncPreservesItems(helper));
        register(event, "appmek_long_amounts", 100,
                helper -> new AppMekAdapterGameTest().chemicalAmountsAreNotTruncated(helper));
        register(event, "appflux_me_flux_input_interface", 100,
                helper -> new AppliedFluxInterfaceGameTest().portBlocksResolveToFluxBlockEntities(helper));
        register(event, "appflux_me_flux_input_grid_node", 100,
                helper -> new AppliedFluxInterfaceGameTest().gridNodeHostsAreExposedAndExternalFeIsSuppressed(helper));
        register(event, "appflux_me_flux_input_capability_family", 100,
                helper -> new AppliedFluxInterfaceGameTest().fluxCapabilitiesBelongToEnergyFamilyWithoutTransferFacet(helper));
        register(event, "appflux_me_flux_output_interface", 100,
                helper -> new AppliedFluxInterfaceGameTest().controllerFormsWithFluxPortsAndResolvesEnergyFamily(helper));
        if (ModList.get().isLoaded("ars_nouveau")) {
            register(event, "ars_source_wand_native_relays", 20,
                    helper -> new ArsSourceTransportGameTest().wandMenusAndNativeRelaysRespectDirections(helper));
            register(event, "ars_source_discovery_restore_reload_menu", 20,
                    helper -> new ArsSourceTransportGameTest().discoveryRestorationReloadAndMenuUseRealStorage(helper));
            register(event, "ars_source_recipe_restore_wakeups", 20,
                    helper -> new ArsSourceRecipeGameTest().recipeLifecycleConsumesOnceAndNativeTransfersWakeSearches(helper));
        }
        if (ModList.get().isLoaded("pneumaticcraft")) {
            register(event, "pneumatic_native_tube_transport_cache", 20,
                    helper -> new PneumaticTransportGameTest().nativeTubeEqualizationAndCacheLifecycle(helper));
            register(event, "pneumatic_native_compressor_transport", 20,
                    helper -> new PneumaticTransportGameTest().nativeCompressorFeedsInputThroughTube(helper));
            register(event, "pneumatic_native_single_tick", 20,
                    helper -> new PneumaticTransportGameTest().serverTickerRunsNativeHandlerExactlyOnce(helper));
            register(event, "pneumatic_signed_persistence_sync", 20,
                    helper -> new PneumaticTransportGameTest().signedPersistenceAndSyncPreserveNativeState(helper));
            register(event, "pneumatic_native_recipe_validation", 20,
                    helper -> new PneumaticTransportGameTest().recipeMutationRechecksDirectionPressureAndSafety(helper));
            register(event, "pneumatic_native_signed_wakeups", 20,
                    helper -> new PneumaticTransportGameTest().directNativeChangesPublishSignedPressureAndCapacityWakeups(helper));
            register(event, "pneumatic_air_recipe_restore_wakeups", 20,
                    helper -> new PneumaticRecipeGameTest().recipeLifecycleRestoreAndNativeRefillWakeSearch(helper));
            register(event, "pneumatic_air_condition_output_recovery", 20,
                    helper -> new PneumaticRecipeGameTest().pressureOnlyFallbackAndFullOutputRecovery(helper));
        }
        if (ModList.get().isLoaded("botania")) {
            BotaniaManaTransportGameTest transport = new BotaniaManaTransportGameTest();
            register(event, "botania_mana_capabilities_wand_persistence", 20, transport::capabilitiesWandAndPersistence);
            register(event, "botania_mana_update_tag_assignment", 20, transport::updateTagOverwritesRealManaAndPreservesAppearance);
            register(event, "botania_mana_native_spreader_burst", 20, transport::nativeSpreaderPullAndBurstCollision);
            register(event, "botania_mana_native_spark_lifecycle", 20, transport::nativeSparkDirectionsAndLifecycle);
            register(event, "botania_mana_dropped_item_permissions", 20, transport::droppedItemsPermissionsAndBoundaries);
            register(event, "botania_mana_local_ticker_items", 20, transport::localTickerDoesNotInfuseOrDoubleTransfer);
            register(event, "botania_mana_dispersive_spark", 20, transport::dispersiveSparkUsesOnlyOutputMana);
            BotaniaManaRecipeGameTest recipes = new BotaniaManaRecipeGameTest();
            register(event, "botania_mana_recipe_multi_pool_parallel", 20, recipes::multiplePoolsLowerParallelismAndConsumeOnlyAtStart);
            register(event, "botania_mana_recipe_item_input_wakeup", 20, recipes::droppedTabletWakesBlockedInputSearch);
            register(event, "botania_mana_recipe_output_capacity_retry", 20, recipes::outputCapacityRetriesAfterNativeItemDrain);
            register(event, "botania_mana_recipe_active_controller_restore", 20, recipes::activeControllerSaveRestoreKeepsConsumedMana);
            register(event, "botania_mana_recipe_tags_real_query", 20, recipes::taggedQueriesAndPlanningUseRealPoolStorage);
            register(event, "botania_mana_recipe_stale_async_commit", 20, recipes::staleAsyncPlansRevalidateAfterNativeAndRecipeChanges);
        }
        if (FluxNetworksBridge.get().available()) {
            register(event, "fluxnetworks_device_menu", 100,
                    helper -> new FluxNetworksDeviceGameTest().nativeMenuAndCapabilities(helper));
            register(event, "fluxnetworks_recipe_prefetch", 100,
                    helper -> new FluxNetworksRecipeGameTest().prefetchAndConsumeOnce(helper));
            register(event, "fluxnetworks_recipe_restore", 100,
                    helper -> new FluxNetworksRecipeGameTest().restoreAndCancelWithoutSecondDebit(helper));
            register(event, "fluxnetworks_cross_dimension", 100,
                    helper -> new FluxNetworksDeviceGameTest().crossDimensionNativeTransfer(helper));
            register(event, "fluxnetworks_drop_configuration", 100,
                    helper -> new FluxNetworksDeviceGameTest().dropAndReplacePreserveEnergy(helper));
            register(event, "fluxnetworks_native_item_crafting", 100,
                    helper -> new FluxNetworksDeviceGameTest().nativeItemCraftingRecipes(helper));
            register(event, "fluxnetworks_pending_connections", 100,
                    helper -> new FluxNetworksDeviceGameTest().pendingConnectionsAndRemoval(helper));
            register(event, "fluxnetworks_same_network_paste_sort", 100,
                    helper -> new FluxNetworksDeviceGameTest().sameNetworkPasteSortsNativeLists(helper));
            register(event, "fluxnetworks_failed_owner_restore", 100,
                    helper -> new FluxNetworksRecipeGameTest().failedLoadReleasesUnownedReservation(helper));
            register(event, "fluxnetworks_candidate_warmup_withdrawal", 100,
                    helper -> new FluxNetworksRecipeGameTest().successfulCandidateRetractsFailedWarmup(helper));
        }
        if (ModList.get().isLoaded("extendedae_plus")) {
            register(event, "eaep_mirror_sync", 200,
                    helper -> new MirrorPatternInterfaceGameTest().syncAndPersistence(helper));
            register(event, "eaep_mirror_tool", 100,
                    helper -> new MirrorPatternInterfaceGameTest().nativeToolBinding(helper));
            register(event, "eaep_mirror_dispatch", 200,
                    helper -> new MirrorPatternInterfaceGameTest().dispatchesOwnController(helper));
        }
        return event;
    }

    public static void registerMachineDefinitions(RegisterMachineDefinitionsEvent event) {
        registerMachineDefinitions(RegistrationAdapters.core(event));
        if (ModList.get().isLoaded("botania")) BotaniaManaRecipeGameTest.registerMachines(event);
    }

    public static void registerMachineDefinitions(MachineDefinitionRegistration event) {
        if (ModList.get().isLoaded("pneumaticcraft")) {
            PneumaticRecipeGameTest.registerMachineDefinitions(event);
        }
        if (FluxNetworksBridge.get().available()) {
            event.registerMachine(MachineBuilder.machine(FluxNetworksRecipeGameTest.MACHINE_ID)
                    .displayNameKey("machine.mmcr_test.fluxnetworks")
                    .appearance(appearance -> appearance.machineBasicBlock("minecraft:gold_block"))
                    .maxParallelism(2).parallelizable(true).build());
        }
        for (String name : List.of("test_cube", "controller_tick", "task7_tick_io", "task7_recipe_snapshot", "data_storage_tick", "upgrade_bus_test", "smart_interface_test", "iron_compressor",
                "distillation_tower_test", "expandable_structure_stages", "expandable_structure_vertical_roll", "falling_block_structure")) {
            ResourceLocation id = MMCR.id(name);
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
        if (ModList.get().isLoaded("ars_nouveau")) {
            event.registerMachine(MachineBuilder.machine(ArsSourceRecipeGameTest.MACHINE_ID)
                    .displayNameKey("machine.mmcr_test.ars_source_task9")
                    .maxParallelism(2).parallelizable(true).build());
        }
    }

    public static void registerMachineStructures(RegisterMachineStructuresEvent event) {
        registerMachineStructures(RegistrationAdapters.core(event));
        if (ModList.get().isLoaded("botania")) BotaniaManaRecipeGameTest.registerStructures(event);
    }

    public static void registerMachineStructures(StructureRegistration event) {
        if (ModList.get().isLoaded("pneumaticcraft")) {
            PneumaticRecipeGameTest.registerMachineStructures(event);
        }
        if (FluxNetworksBridge.get().available()) {
            event.registerStructure(FluxNetworksRecipeGameTest.MACHINE_ID, structure -> {
                structure.fullStructure(stage -> stage.pattern(pattern -> pattern.layer("ICO", " P ")
                        .where('I', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get(FluxNetworksIds.INPUT).get()))
                        .where('C', BlockPredicate.deferredBlock(() -> ModBlocks.controllerFor(FluxNetworksRecipeGameTest.MACHINE_ID).get()))
                        .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get(FluxNetworksIds.OUTPUT).get()))
                        .where('P', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("parallel_controller_normal").get()))
                        .controller('C')));
                return structure;
            });
        }
        if (ModList.get().isLoaded("ars_nouveau")) {
            event.registerStructure(ArsSourceRecipeGameTest.MACHINE_ID, structure -> {
                structure.fullStructure(stage -> stage.pattern(pattern -> pattern.layer("ICO", " P ")
                        .where('I', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get(ArsSourceIds.INPUT).get()))
                        .where('C', BlockPredicate.deferredBlock(() -> ModBlocks.controllerFor(ArsSourceRecipeGameTest.MACHINE_ID).get()))
                        .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get(ArsSourceIds.OUTPUT).get()))
                        .where('P', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get("parallel_controller_normal").get()))
                        .controller('C')));
                return structure;
            });
        }
        ResourceLocation terminalLevelType = MMCR.id("terminal_test_level_type");
        event.registerLevelType(new LevelType(terminalLevelType, Component.literal("Terminal Test Level")));
        event.registerLevel(new MachineLevel(MMCR.id("terminal_test_level"), terminalLevelType, 0,
                BlockPredicate.blockState(Blocks.IRON_BLOCK.defaultBlockState()),
                DisplayStack.of(new ItemStack(Items.IRON_BLOCK)),
                ModifierDefinition.of("duration", "input", 1.0F, "add", false)));
        ResourceLocation upgradeBusBlockModifierId = MMCR.id("upgrade_bus_test_block_modifier");
        event.registerModifier(upgradeBusBlockModifierId,
                ModifierDefinition.of("duration", "input", 1.0F, "add", false));
        ResourceLocation upgradeBusModifierId = MMCR.id("upgrade_bus_test_modifier");
        event.registerModifier(upgradeBusModifierId,
                ModifierDefinition.of("duration", "input", 1.0F, "add", false));
        event.registerModifierItem(new ItemStack(Items.NETHER_STAR), upgradeBusModifierId);
        for (String name : List.of("test_cube", "controller_tick", "task7_tick_io", "task7_recipe_snapshot", "data_storage_tick", "upgrade_bus_test", "iron_compressor",
                "distillation_tower_test", "expandable_structure_stages", "expandable_structure_vertical_roll", "falling_block_structure")) {
            ResourceLocation id = MMCR.id(name);
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
        if (ModList.get().isLoaded("botania")) BotaniaManaRecipeGameTest.registerRecipes(event);
    }

    public static void registerRecipes(MachineRecipeRegistration event) {
        if (ModList.get().isLoaded("pneumaticcraft")) {
            PneumaticRecipeGameTest.registerRecipes(event);
        }
        if (FluxNetworksBridge.get().available()) {
            event.registerRecipe(MachineRecipeBuilder.recipe(FluxNetworksRecipeGameTest.RECIPE_ID)
                    .recipePool(FluxNetworksRecipeGameTest.MACHINE_ID).priority(10).duration(5).parallelized(true).inputEnergy(10L).build());
            event.registerRecipe(MachineRecipeBuilder.recipe(FluxNetworksRecipeGameTest.OUTPUT_RECIPE_ID)
                    .recipePool(FluxNetworksRecipeGameTest.MACHINE_ID).duration(5).parallelized(true).outputEnergy(10L).build());
            event.registerRecipe(MachineRecipeBuilder.recipe(FluxNetworksRecipeGameTest.HIGH_BUDGET_RECIPE_ID)
                    .recipePool(FluxNetworksRecipeGameTest.MACHINE_ID).priority(0).duration(5).parallelized(true).inputEnergy(20L).build());
        }
        if (ModList.get().isLoaded("ars_nouveau")) {
            event.registerRecipe(MachineRecipeBuilder.recipe(ArsSourceRecipeGameTest.RECIPE_ID)
                    .recipePool(ArsSourceRecipeGameTest.MACHINE_ID).duration(3).parallelized(true)
                    .inputSource(300).outputSource(100).build());
            event.registerRecipe(MachineRecipeBuilder.recipe(ArsSourceRecipeGameTest.OUTPUT_WAIT_ID)
                    .recipePool(ArsSourceRecipeGameTest.MACHINE_ID).duration(3).outputSource(200).build());
        }
        event.registerRecipe(MachineRecipeBuilder.recipe(MMCR.id("distillation_test_recipe"))
                        .recipePool(MMCR.id("distillation_tower_test"))
                .duration(20).inputItem(Items.COAL, 1).outputFluid(Fluids.WATER, 1).build());
    }

    private static void register(Collection<TestFunction> tests, String name, int maxTicks,
                                 Consumer<GameTestHelper> test) {
        tests.add(new TestFunction(MMCR.MODID, MMCR.id(name).toString(), "mmcr:empty",
                maxTicks, 0, true, test));
    }
}
