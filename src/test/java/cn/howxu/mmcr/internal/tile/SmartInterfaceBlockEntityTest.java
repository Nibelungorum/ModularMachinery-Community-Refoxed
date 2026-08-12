package cn.howxu.mmcr.internal.tile;

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

import static org.assertj.core.api.Assertions.assertThat;

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

    private static SmartInterfaceBlockEntity createSmartInterface() {
        return (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get().create(
                BlockPos.ZERO, ModBlocks.SMART_INTERFACE.get().defaultBlockState());
    }
}
