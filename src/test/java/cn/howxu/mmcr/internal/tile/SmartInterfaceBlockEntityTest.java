package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;

import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SmartInterfaceBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void parameter_values_round_trip_and_reject_invalid_updates() {
        var owner = createSmartInterface();
        assertThat(owner.claimController(BlockPos.ZERO, MMCR.id("test"), Map.of(
                "mode", new SmartInterfaceType("mode", 4F, 0),
                "temperature", new SmartInterfaceType("temperature", 20F, 1)
        ), false)).isTrue();
        assertThat(owner.setValue("mode", 7F)).isTrue();

        HolderLookup.Provider lookup = HolderLookup.Provider.create(Stream.empty());
        CompoundTag output = new CompoundTag();
        owner.saveAdditional(output, lookup);
        var restored = createSmartInterface();
        restored.loadAdditional(output, lookup);

        assertThat(restored.machineId()).contains(MMCR.id("test"));
        assertThat(restored.controllerPositions()).containsExactly(BlockPos.ZERO);
        assertThat(restored.value("mode")).contains(7F);
        assertThat(restored.value("temperature")).contains(20F);
        assertThat(restored.setValue("mode", Float.NaN)).isFalse();

        MachineCapability capability = restored.capabilitySnapshot().capabilities().stream()
                .filter(candidate -> candidate.directions().supports(IOType.OUTPUT))
                .findFirst().orElseThrow();
        assertThat(capability.prepare(new CapabilityRequests.SmartValueRequest(
                capability.type(), IOType.OUTPUT, 1, "mode", 9F)).commit().success()).isTrue();
        assertThat(restored.value("mode")).contains(9F);
    }

    @Test
    void legacy_bindings_load_as_interface_owned_values() {
        var legacy = createSmartInterface();
        assertThat(legacy.bind(BlockPos.ZERO, MMCR.id("test"), "mode", 4F)).isTrue();

        HolderLookup.Provider lookup = HolderLookup.Provider.create(Stream.empty());
        CompoundTag output = new CompoundTag();
        legacy.saveAdditional(output, lookup);
        var restored = createSmartInterface();
        restored.loadAdditional(output, lookup);

        assertThat(restored.machineId()).contains(MMCR.id("test"));
        assertThat(restored.value("mode")).contains(4F);
        assertThat(restored.hasController(BlockPos.ZERO)).isTrue();
    }

    @Test
    void sync_types_uses_registered_minimum_value() {
        var owner = createSmartInterface();

        assertThat(owner.claimController(BlockPos.ZERO, MMCR.id("test"), Map.of(
                "temperature", new SmartInterfaceType("temperature", 400F, 6800F, 0,
                        SmartInterfaceType.ValueType.INTEGER)
        ), false)).isTrue();

        assertThat(owner.value("temperature")).contains(400F);
    }

    @Test
    void capability_root_commit_publishes_the_value_to_the_interface_owner() {
        var owner = createSmartInterface();
        assertThat(owner.claimController(BlockPos.ZERO, MMCR.id("test"), Map.of(
                "temperature", new SmartInterfaceType("temperature", 20F, 0)
        ), false)).isTrue();
        MachineCapability output = owner.capabilitySnapshot().capabilities().stream()
                .filter(capability -> capability.directions().supports(IOType.OUTPUT))
                .findFirst().orElseThrow();
        var request = new CapabilityRequests.SmartValueRequest(
                output.type(), IOType.OUTPUT, 1, "temperature", 80F);

        assertThat(output.prepare(request).commit().success()).isTrue();

        assertThat(owner.value("temperature")).contains(80F);
    }

    @Test
    void direct_value_change_keeps_a_bound_active_runtime_on_its_effective_snapshot() {
        SmartInterfaceBlockEntity owner = createSmartInterface();
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        owner.setLevel(controller.getLevel());
        assertThat(owner.claimController(controller.getBlockPos(), MMCR.id("test_cube"), Map.of(
                "mode", new SmartInterfaceType("mode", 1F, 0)), false)).isTrue();
        CraftingRuntime runtime = controllerRuntime(controller);

        assertThat(runtime.start(RecipeTestSupport.create(MMCR.id("smart_change_controller_runtime"),
                MMCR.id("test_cube"), 20, List.of(), List.of()), 1).isCrafting()).isTrue();

        assertThat(owner.setValue("mode", 2F)).isTrue();

        assertThat(runtime.active()).isTrue();
        assertThat(runtime.failure()).isNull();
    }

    @Test
    void bind_with_server_level_stub_does_not_post_without_kubejs_listeners() {
        var smartInterface = createSmartInterface();
        smartInterface.setLevel(LevelStub.createWithBlockEntities(List.of(smartInterface)));

        assertThatCode(() -> smartInterface.bind(BlockPos.ZERO, MMCR.id("test"), "mode", 4F))
                .doesNotThrowAnyException();
    }

    private static SmartInterfaceBlockEntity createSmartInterface() {
        return (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get().create(
                BlockPos.ZERO, ModBlocks.SMART_INTERFACE.get().defaultBlockState());
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
}
