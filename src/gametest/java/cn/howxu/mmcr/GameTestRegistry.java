package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.nibelungorum.DefaultRecipes;
import org.nibelungorum.TestMachines;

import java.util.function.Consumer;

public final class GameTestRegistry {
    private GameTestRegistry() {
    }

    public static void registerAll(RegisterGameTestsEvent event) {
        register(event, "block_array_match", 100, helper -> new BlockArrayMatchGameTest().structureForms3x3Casing(helper));
        register(event, "controller_tick", 100, helper -> new ControllerTickGameTest().structureForms3x3Casing(helper));
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

    public static void registerMachineDefinitions() {
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(MMCR.id("test_cube")).displayNameKey("machine.mmcr_test.test_cube").build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(MMCR.id("controller_tick")).displayNameKey("machine.mmcr_test.controller_tick").build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(MMCR.id("iron_compressor")).displayNameKey("machine.mmcr_test.iron_compressor").build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(MMCR.id("distillation_tower_test"))
                .displayNameKey("machine.mmcr_test.distillation_tower_test").expandableStructure().build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(MMCR.id("expandable_structure_stages"))
                .displayNameKey("machine.mmcr_test.expandable_structure_stages").expandableStructure().build());
        MachineDefinitions.addBuiltinSupplier(() -> {
            Identifier machineId = MMCR.id("expandable_structure_vertical_roll");
            MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(machineId);
            return MachineRegistration.builder(machineId)
                    .displayNameKey("machine.mmcr_test.expandable_structure_vertical_roll")
                    .controllerSpec(new MachineControllerSpec(defaults.id(), defaults.frontTexture(), defaults.sideTexture(),
                            defaults.topTexture(), defaults.bottomTexture(), true, false))
                    .expandableStructure()
                    .build();
        });
    }

    public static void registerMachineStructures(DynamicContentReloadService.Candidate candidate) {
        candidate.registerStructure(new MachineStructureDefinition(MMCR.id("test_cube"), TestMachines.casingCubePattern(),
                PortRequirementSpec.none(), java.util.List.of(), MachineStructureRequirements.EMPTY));
        candidate.registerStructure(new MachineStructureDefinition(MMCR.id("controller_tick"), TestMachines.casingCubePattern(),
                PortRequirementSpec.none(), java.util.List.of(), MachineStructureRequirements.EMPTY));
        candidate.registerStructure(new MachineStructureDefinition(MMCR.id("iron_compressor"), TestMachines.ironCompressorPattern(),
                PortRequirementSpec.none(), java.util.List.of(), MachineStructureRequirements.EMPTY));
        candidate.registerStructure(new MachineStructureDefinition(MMCR.id("distillation_tower_test"), TestMachines.distillationTowerDeclarations()));
        candidate.registerStructure(new MachineStructureDefinition(MMCR.id("expandable_structure_stages"), TestMachines.expandableStageDeclarations()));
        candidate.registerStructure(new MachineStructureDefinition(MMCR.id("expandable_structure_vertical_roll"), TestMachines.expandableStageDeclarations()));
    }

    public static void registerRecipes() {
        DefaultRecipes.registerStatic(DefaultRecipes.gameTestRecipes());
        Identifier id = Identifier.parse("mmcr_test:datapack_static_override");
        if (RecipeRegistry.getRecipe(id) == null) {
            RecipeRegistry.register(new MachineRecipe(id, MMCR.id("iron_compressor"), 20,
                    java.util.List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1)),
                    java.util.List.of(new ItemStack(Items.CHARCOAL))));
        }
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
