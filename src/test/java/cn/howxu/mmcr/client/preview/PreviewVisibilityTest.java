package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewVisibilityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void single_layer_hides_other_y_values_and_all_restores_them() {
        BlockPos lower = new BlockPos(0, 0, 0);
        BlockPos upper = new BlockPos(0, 1, 0);
        BlockState state = Blocks.IRON_BLOCK.defaultBlockState();

        assertThat(PreviewVisibility.singleLayer(1).isVisible(lower, state)).isFalse();
        assertThat(PreviewVisibility.singleLayer(1).isVisible(upper, state)).isTrue();
        assertThat(PreviewVisibility.ALL.isVisible(lower, state)).isTrue();
    }
}
