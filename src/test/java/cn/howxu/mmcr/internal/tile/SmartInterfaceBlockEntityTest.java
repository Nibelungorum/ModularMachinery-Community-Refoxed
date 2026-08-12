package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SmartInterfaceBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void bindings_round_trip_and_reject_non_finite_updates() {
        var owner = createSmartInterface();
        assertThat(owner.bind(new BlockPos(1, 2, 3), MMCR.id("test"), "mode", 4F)).isTrue();
        assertThat(owner.bind(new BlockPos(1, 2, 3), MMCR.id("other"), "mode", 2F)).isFalse();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        owner.saveAdditional(output);
        var restored = createSmartInterface();
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()), output.buildResult()));

        assertThat(restored.binding(0)).contains(new SmartInterfaceBlockEntity.Binding(
                new BlockPos(1, 2, 3), MMCR.id("test"), "mode", 4F));
        assertThat(restored.setValue(0, Float.NaN)).isFalse();
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
