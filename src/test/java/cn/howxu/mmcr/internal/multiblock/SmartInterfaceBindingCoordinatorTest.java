package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SmartInterfaceBindingCoordinatorTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void reconcile_uses_unused_types_before_the_highest_priority_fallback() throws Exception {
        var types = types(
                type("low", 1F, 1),
                type("high", 2F, 10));
        var first = smartInterface(new BlockPos(3, 0, 0));
        var second = smartInterface(new BlockPos(1, 0, 0));
        var third = smartInterface(new BlockPos(2, 0, 0));

        new SmartInterfaceBindingCoordinator(types).reconcile(controller(), List.of(first, second, third));

        assertThat(List.of(first.binding(0).orElseThrow().type(), second.binding(0).orElseThrow().type(), third.binding(0).orElseThrow().type()))
                .containsExactlyInAnyOrder("low", "high", "high");
    }

    @Test
    void reconcile_retains_valid_bindings_and_replaces_invalid_bindings() throws Exception {
        var controller = controller();
        var retained = smartInterface(new BlockPos(1, 0, 0));
        assertThat(retained.bind(BlockPos.ZERO, MMCR.id("binding_test"), "low", 7F)).isTrue();

        new SmartInterfaceBindingCoordinator(types(type("low", 1F, 1), type("high", 2F, 10)))
                .reconcile(controller, List.of(retained));

        assertThat(retained.bindingFor(BlockPos.ZERO)).contains(new SmartInterfaceBlockEntity.Binding(
                BlockPos.ZERO, MMCR.id("binding_test"), "low", 7F));

        assertThat(retained.unbind(BlockPos.ZERO)).isTrue();
        assertThat(retained.bind(BlockPos.ZERO, MMCR.id("other"), "missing", 2F)).isTrue();
        new SmartInterfaceBindingCoordinator(types(type("low", 1F, 1), type("high", 2F, 10)))
                .reconcile(controller, List.of(retained));
        assertThat(retained.bindingFor(BlockPos.ZERO)).contains(new SmartInterfaceBlockEntity.Binding(
                BlockPos.ZERO, MMCR.id("binding_test"), "low", 1F));
    }

    @Test
    void unbindAll_removes_only_the_controller_bindings() throws Exception {
        var controller = controller();
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));
        assertThat(smartInterface.bind(BlockPos.ZERO, MMCR.id("binding_test"), "low", 1F)).isTrue();
        assertThat(smartInterface.bind(new BlockPos(9, 0, 0), MMCR.id("other"), "high", 2F)).isTrue();

        new SmartInterfaceBindingCoordinator(Map.of()).unbindAll(controller, List.of(smartInterface));

        assertThat(smartInterface.bindingFor(BlockPos.ZERO)).isEmpty();
        assertThat(smartInterface.bindingFor(new BlockPos(9, 0, 0))).isPresent();
    }

    private static Map<String, SmartInterfaceType> types(SmartInterfaceType... types) {
        Map<String, SmartInterfaceType> result = new LinkedHashMap<>();
        for (SmartInterfaceType type : types) result.put(type.type(), type);
        return result;
    }

    private static SmartInterfaceType type(String type, float defaultValue, int priority) {
        return new SmartInterfaceType(type, defaultValue, priority, "", "", "", "", "", 0);
    }

    private static SmartInterfaceBlockEntity smartInterface(BlockPos pos) {
        return (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get().create(
                pos, ModBlocks.SMART_INTERFACE.get().defaultBlockState());
    }

    private static MachineControllerBlockEntity controller() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(MachineControllerBlockEntity.class);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        setField(MachineControllerBlockEntity.class, controller, "foundMachine", new Machine() {
            @Override
            public net.minecraft.resources.Identifier registryName() {
                return MMCR.id("binding_test");
            }

            @Override
            public String localizedName() {
                return "Binding Test";
            }

            @Override
            public BlockArray pattern() {
                return new BlockArray(Map.of());
            }

            @Override
            public MachineControllerSpec controller() {
                return MachineControllerSpec.defaultsFor(registryName());
            }
        });
        return controller;
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
