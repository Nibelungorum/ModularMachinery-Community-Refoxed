package cn.howxu.mmcr.client.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiblockDetectorScreenTest {
    @Test
    void parse_coordinate_accepts_signed_int_and_rejects_intermediate_or_overflow_values() {
        assertThat(MultiblockDetectorScreen.parseCoordinate("-12")).hasValue(-12);
        assertThat(MultiblockDetectorScreen.parseCoordinate("0")).hasValue(0);
        assertThat(MultiblockDetectorScreen.parseCoordinate("")).isEmpty();
        assertThat(MultiblockDetectorScreen.parseCoordinate("-")).isEmpty();
        assertThat(MultiblockDetectorScreen.parseCoordinate("2147483648")).isEmpty();
    }

    @Test
    void changing_one_axis_preserves_the_other_axes() {
        BlockPos original = new BlockPos(10, 20, 30);
        assertThat(MultiblockDetectorScreen.withAxis(original, Axis.X, -4))
                .isEqualTo(new BlockPos(-4, 20, 30));
        assertThat(MultiblockDetectorScreen.withAxis(original, Axis.Y, -4))
                .isEqualTo(new BlockPos(10, -4, 30));
        assertThat(MultiblockDetectorScreen.withAxis(original, Axis.Z, -4))
                .isEqualTo(new BlockPos(10, 20, -4));
    }
}
