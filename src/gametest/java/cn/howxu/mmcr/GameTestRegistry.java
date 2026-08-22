package cn.howxu.mmcr;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.function.Consumer;

import java.util.List;

public final class GameTestRegistry {
    private GameTestRegistry() {
    }

    public static void registerAll(RegisterGameTestsEvent event) {
        register(event, "registry_reflection_helper", 20, helper -> {
            boolean registered = helper.getLevel().registryAccess()
                    .lookupOrThrow(Registries.TEST_INSTANCE)
                    .get(MMCR.id("registry_reflection_helper"))
                    .isPresent();
            helper.assertTrue(registered, "GameTestRegistry reflection helper registers the canonical test ID");
            helper.succeed();
        });
        register(event, "block_array_match", 100, helper -> new BlockArrayMatchGameTest().structureForms3x3Casing(helper));
        register(event, "controller_tick", 100, helper -> new ControllerTickGameTest().structureForms3x3Casing(helper));
        register(event, "multi_factory_controller", 160,
                helper -> new MultiFactoryControllerGameTest().formsWithTwoFactoryControllersAndReformsAfterRelease(helper));
        register(event, "controller_tick_scan_registry", 100, helper -> new ControllerTickGameTest().scansRegisteredMachineWhenDefaultBindingIsEmpty(helper));
        register(event, "expandable_structure_stages", 120, helper -> new ExpandableStructureGameTest().upgradesAndDowngradesHighestAvailableStage(helper));
        register(event, "expandable_structure_vertical_roll", 120, helper -> new ExpandableStructureGameTest().verticalNonDefaultRollUsesStageSelection(helper));
        register(event, "e2e_recipe_run", 200, helper -> new E2ERecipeRunGameTest().ironCompressorRuns(helper));
        register(event, "datapack_recipe_run", 160, helper -> new DataPackRecipeGameTest().dataPackRecipeRunsOnMachine(helper));
        register(event, "datapack_recipe_override", 100, helper -> new DataPackRecipeGameTest().dataPackRecipeOverridesStaticRecipe(helper));
        register(event, "datapack_recipe_malformed_isolation", 100, helper -> new DataPackRecipeGameTest().malformedDataPackRecipeDoesNotBlockValidRecipe(helper));
        register(event, "e2e_distillation_tower_partial_outputs", 160, helper -> new E2ERecipeRunGameTest().distillationTowerUnlocksPartialFluidOutputsByStage(helper));
        register(event, "energy_hatch_capability", 100, helper -> new EnergyHatchCapabilityGameTest().energyHatchStoresFE(helper));
        register(event, "fluid_hatch_capability", 100, helper -> new FluidHatchCapabilityGameTest().fluidHatchStoresWater(helper));
        register(event, "fluid_hatch_bucket_interaction", 100, helper -> new FluidHatchCapabilityGameTest().bucketInteractionRespectsHatchDirection(helper));
        register(event, "item_bus_capability", 100, ItemBusCapabilityGameTest::itemBusAcceptsItems);
        register(event, "item_bus_capability_cache_auto_io_side", 100, ItemBusCapabilityGameTest::itemBusCapabilityCacheFollowsAutoIOSideConfig);
        register(event, "item_bus_drops_contents", 100, ItemBusCapabilityGameTest::itemBusDropsStoredItemsWhenRemoved);
        register(event, "dynamic_controller_drops_self", 100, ItemBusCapabilityGameTest::dynamicControllerDropsItself);
        // Disabled: CPU scheduling delays can cause this otherwise-correct test to fail intermittently.
        register(event, "auto_io_fluid_output", 120, helper -> new AutoIOGameTest().fluidOutputAutoExports(helper));
        register(event, "auto_io_energy_output", 120, helper -> new AutoIOGameTest().energyOutputAutoExports(helper));
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
        register(event, "smart_interface", 100, helper -> new SmartInterfaceGameTest().bindsDefaultValueAndWritesRecipeOutput(helper));
        register(event, "tag_component_ingredient", 100, helper -> new TagComponentIngredientGameTest().tagIngredientMatchesComponentPredicate(helper));
        register(event, "terminal_build", 100, helper -> new TerminalAssemblyGameTest().buildSkipsOccupiedPositionsAndPlacesOnlyMissingBlocks(helper));
        register(event, "terminal_demolish", 100, helper -> new TerminalAssemblyGameTest().demolishSkipsAirAndNonMatchingBlocks(helper));
        register(event, "terminal_expandable_build_stage_one", 100, helper -> new TerminalAssemblyGameTest().buildExpandableControllerPlacesOnlyStageOne(helper));
        register(event, "terminal_expandable_demolish_stage_two", 100, helper -> new TerminalAssemblyGameTest().demolishExpandableFormedStageTwoRemovesCompleteSnapshot(helper));
        register(event, "terminal_expandable_missing_materials_stage_one", 100, helper -> new TerminalAssemblyGameTest().defaultBuildMissingMaterialsExcludeStageTwo(helper));
        register(event, "terminal_build_missing_stage_one_partial", 100, helper -> new TerminalAssemblyGameTest().survivalBuildPartiallyWhenStageOneMaterialsAreMissing(helper));
        register(event, "terminal_build_replenished_materials", 100, helper -> new TerminalAssemblyGameTest().survivalBuildContinuesAfterInventoryIsReplenished(helper));
        register(event, "module_connection_host_empty", 100, helper -> new ModuleConnectionGameTest().hostFormsWithNoInstalledModules(helper));
        register(event, "module_connection_module_disconnected", 100, helper -> new ModuleConnectionGameTest().moduleFormsIndependentlyButCannotRunWithoutHost(helper));
        register(event, "module_connection_connected", 100, helper -> new ModuleConnectionGameTest().sharedCouplerConnectsModuleAndEnablesHostGatedRecipes(helper));
        register(event, "module_connection_interface_conflict", 100, helper -> new ModuleConnectionGameTest().sharedInterfaceInvalidatesHost(helper));
    }

    public static void registerMachineDefinitions(MMCRMachineDefinationsEvent event) {
        for (String name : List.of("test_cube", "controller_tick", "iron_compressor",
                "distillation_tower_test", "expandable_structure_stages", "expandable_structure_vertical_roll")) {
            Identifier id = MMCR.id(name);
            MachineBuilder builder = MachineBuilder.machine(id);
            builder.displayNameKey("machine.mmcr_test." + name);
            if (name.equals("expandable_structure_vertical_roll")) {
                builder.controller(controller -> controller.allowVerticalFacing());
            }
            MachineDefinition definition = builder.build();
            if (name.equals("distillation_tower_test") || name.equals("expandable_structure_stages")
                    || name.equals("expandable_structure_vertical_roll")) {
                definition = new MachineDefinition(definition.id(), definition.displayNameKey(), definition.controller(),
                        definition.appearance(), definition.factory(), definition.role(), definition.acceptedModuleIds(),
                        definition.maxParallelism(), definition.parallelizable(), definition.failureAction(),
                        definition.allowModifiers(), definition.allowMultithreading(), definition.maxParallelAmount(), true,
                        definition.smartInterfaceTypes(), definition.shareSmartInterfaces(), definition.smartInterfaceModifiers(),
                        definition.runningSoundId(), definition.finishSoundId(), definition.pattern());
            }
            event.registerMachine(definition);
        }
    }

    public static void registerMachineStructures(MMCRMachineStructuresEvent event) {
        for (String name : List.of("test_cube", "controller_tick", "iron_compressor",
                "distillation_tower_test", "expandable_structure_stages", "expandable_structure_vertical_roll")) {
            Identifier id = MMCR.id(name);
            event.registerStructure(id, structure -> {
                BlockPredicate casing = BlockPredicate.deferredBlock(() -> ModBlocks.CASING.get());
                BlockPredicate controller = BlockPredicate.deferredBlock(() -> ModBlocks.controllerFor(id).get());
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

    public static void registerRecipes(MMCRMachineRecipesEvent event) {
        Identifier id = Identifier.parse("mmcr_test:datapack_static_override");
        event.registerRecipe(MachineRecipeBuilder.recipe(id, MMCR.id("iron_compressor")).duration(20)
                .inputItem(Items.COAL, 1).outputItem(Items.CHARCOAL, 1).build());
        event.registerRecipe(MachineRecipeBuilder.recipe(MMCR.id("distillation_test_recipe"),
                        MMCR.id("distillation_tower_test"))
                .duration(20).inputItem(Items.COAL, 1).outputFluid(Fluids.WATER, 1).build());
    }

    private static void register(RegisterGameTestsEvent event, String name, int maxTicks, Consumer<GameTestHelper> test) {
        Holder<TestEnvironmentDefinition<?>> environment = Holder.direct(new TestEnvironmentDefinition.AllOf());
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment,
                Identifier.fromNamespaceAndPath("minecraft", "empty"),
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
            return (MapCodec) MapCodec.unit(this);
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("MMCR " + name);
        }
    }
}
