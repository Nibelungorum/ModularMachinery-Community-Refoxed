package cn.howxu.mmcr;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.function.Consumer;

public final class GameTestRegistry {
    private GameTestRegistry() {
    }

    public static void registerAll(RegisterGameTestsEvent event) {
        register(event, "block_array_match", 100, helper -> new BlockArrayMatchGameTest().structureForms3x3Casing(helper));
        register(event, "controller_tick", 100, helper -> new ControllerTickGameTest().structureForms3x3Casing(helper));
        register(event, "controller_tick_scan_registry", 100, helper -> new ControllerTickGameTest().scansRegisteredMachineWhenDefaultBindingIsEmpty(helper));
        register(event, "e2e_recipe_run", 200, helper -> new E2ERecipeRunGameTest().ironCompressorRuns(helper));
        register(event, "energy_hatch_capability", 100, helper -> new EnergyHatchCapabilityGameTest().energyHatchStoresFE(helper));
        register(event, "fluid_hatch_capability", 100, helper -> new FluidHatchCapabilityGameTest().fluidHatchStoresWater(helper));
        register(event, "item_bus_capability", 100, ItemBusCapabilityGameTest::itemBusAcceptsItems);
        register(event, "item_bus_capability_cache_auto_io_side", 100, ItemBusCapabilityGameTest::itemBusCapabilityCacheFollowsAutoIOSideConfig);
        register(event, "auto_io_item_input", 120, helper -> new AutoIOGameTest().itemInputAutoImports(helper));
        register(event, "auto_io_item_input_late_neighbor", 160, helper -> new AutoIOGameTest().itemInputAutoImportsAfterLateNeighborPlacement(helper));
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
