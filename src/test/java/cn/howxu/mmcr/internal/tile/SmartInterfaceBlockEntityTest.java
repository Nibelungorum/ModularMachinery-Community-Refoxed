package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        owner.saveAdditional(output);
        var restored = createSmartInterface();
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

        assertThat(restored.machineId()).contains(MMCR.id("test"));
        assertThat(restored.controllerPositions()).containsExactly(BlockPos.ZERO);
        assertThat(restored.value("mode")).contains(7F);
        assertThat(restored.value("temperature")).contains(20F);
        assertThat(restored.setValue("mode", Float.NaN)).isFalse();
    }

    @Test
    void legacy_bindings_load_as_interface_owned_values() {
        var legacy = createSmartInterface();
        assertThat(legacy.bind(BlockPos.ZERO, MMCR.id("test"), "mode", 4F)).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        legacy.saveAdditional(output);
        var restored = createSmartInterface();
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult()));

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
}
