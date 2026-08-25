package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;
import static org.assertj.core.api.Assertions.assertThat;

class SmartInterfaceBindingCoordinatorTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void clearMachineRegistry() {
        MachineRegistry.clearForTesting();
    }

    @Test
    void reconcile_claims_all_machine_parameters_on_one_interface() throws Exception {
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));

        coordinator(false, type("low", 1F, 1), type("high", 2F, 10))
                .reconcile(controller(), List.of(smartInterface));

        assertThat(smartInterface.parameterTypes()).containsExactly("low", "high");
        assertThat(smartInterface.value("low")).contains(1F);
        assertThat(smartInterface.value("high")).contains(2F);
        assertThat(smartInterface.hasController(BlockPos.ZERO)).isTrue();
    }

    @Test
    void default_exclusive_interfaces_reject_second_controller() throws Exception {
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));
        var first = controller(BlockPos.ZERO, MMCR.id("binding_test"), MMCR.id("block/basic_casing"));
        var second = controller(new BlockPos(9, 0, 0), MMCR.id("binding_test"), MMCR.id("block/basic_casing"));

        coordinator(false, type("mode", 1F, 0)).reconcile(first, List.of(smartInterface));
        coordinator(false, type("mode", 1F, 0)).reconcile(second, List.of(smartInterface));

        assertThat(smartInterface.controllerPositions()).containsExactly(BlockPos.ZERO);
    }

    @Test
    void shared_interfaces_allow_multiple_controllers_and_one_value_set() throws Exception {
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));
        var first = controller(BlockPos.ZERO, MMCR.id("binding_test"), MMCR.id("block/basic_casing"));
        var second = controller(new BlockPos(9, 0, 0), MMCR.id("binding_test"), MMCR.id("block/basic_casing"));

        coordinator(true, type("mode", 1F, 0)).reconcile(first, List.of(smartInterface));
        assertThat(smartInterface.setValue("mode", 3F)).isTrue();
        coordinator(true, type("mode", 1F, 0)).reconcile(second, List.of(smartInterface));

        assertThat(smartInterface.controllerPositions()).containsExactly(BlockPos.ZERO, new BlockPos(9, 0, 0));
        assertThat(smartInterface.value("mode")).contains(3F);
    }

    @Test
    void unbindAll_removes_only_the_controller_bindings() throws Exception {
        var controller = controller();
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));
        assertThat(smartInterface.bind(BlockPos.ZERO, MMCR.id("binding_test"), "low", 1F)).isTrue();
        assertThat(smartInterface.bind(new BlockPos(9, 0, 0), MMCR.id("binding_test"), "high", 2F)).isTrue();

        new SmartInterfaceBindingCoordinator(Map.of()).unbindAll(controller, List.of(smartInterface));

        assertThat(smartInterface.hasController(BlockPos.ZERO)).isFalse();
        assertThat(smartInterface.hasController(new BlockPos(9, 0, 0))).isTrue();
    }

    @Test
    void reconcile_links_unique_controller_appearance_to_smart_interface() throws Exception {
        var texture = MMCR.id("block/smart_interface_casing");
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));

        new SmartInterfaceBindingCoordinator(types(type("low", 1F, 1)))
                .reconcile(controller(texture), List.of(smartInterface));

        assertThat(smartInterface.appearanceBaseTexture()).isEqualTo(texture);
    }

    @Test
    void unbindAll_unlinks_controller_appearance_from_smart_interface() throws Exception {
        var controller = controller(MMCR.id("block/smart_interface_casing"));
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));
        new SmartInterfaceBindingCoordinator(types(type("low", 1F, 1))).reconcile(controller, List.of(smartInterface));

        new SmartInterfaceBindingCoordinator(Map.of()).unbindAll(controller, List.of(smartInterface));

        assertThat(smartInterface.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void multiple_controller_bindings_fall_back_to_basic_casing_for_smart_interface() throws Exception {
        var firstTexture = MMCR.id("block/smart_interface_casing");
        var secondTexture = MMCR.id("block/other_smart_interface_casing");
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));
        var firstController = controller(BlockPos.ZERO, MMCR.id("binding_test"), firstTexture);
        var secondController = controller(new BlockPos(9, 0, 0), MMCR.id("binding_test"), secondTexture);

        new SmartInterfaceBindingCoordinator(types(type("low", 1F, 1)))
                .reconcile(firstController, List.of(smartInterface));
        new SmartInterfaceBindingCoordinator(types(type("high", 2F, 1)), true)
                .reconcile(secondController, List.of(smartInterface));

        assertThat(smartInterface.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    private static Map<String, SmartInterfaceType> types(SmartInterfaceType... types) {
        Map<String, SmartInterfaceType> result = new LinkedHashMap<>();
        for (SmartInterfaceType type : types) result.put(type.type(), type);
        return result;
    }

    private static SmartInterfaceBindingCoordinator coordinator(boolean shared, SmartInterfaceType... types) {
        return new SmartInterfaceBindingCoordinator(types(types), shared);
    }

    private static SmartInterfaceType type(String type, float defaultValue, int priority) {
        return new SmartInterfaceType(type, defaultValue, priority);
    }

    private static SmartInterfaceBlockEntity smartInterface(BlockPos pos) {
        return (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get().create(
                pos, ModBlocks.SMART_INTERFACE.get().defaultBlockState());
    }

    private static MachineControllerBlockEntity controller() throws Exception {
        return controller(MMCR.id("block/basic_casing"));
    }

    private static MachineControllerBlockEntity controller(Identifier texture) throws Exception {
        return controller(BlockPos.ZERO, MMCR.id("binding_test"), texture);
    }

    private static MachineControllerBlockEntity controller(BlockPos pos, Identifier machineId,
                                                           Identifier texture) {
        Machine machine = new cn.howxu.mmcr.api.machine.DynamicMachine(machineId, "Binding Test",
                new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                MachineControllerSpec.defaultsFor(machineId),
                new MachineAppearanceSpec(MMCR.id("block/basic_casing"), MMCR.id("block/basic_casing"), texture),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of());
        if (MachineRegistry.getMachine(machineId) == null) MachineRegistry.register(machine);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), pos);
        RuntimeTestFixtures.formStructure(controller, machine);
        return controller;
    }
}
