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
    void coordinate_candidates_allow_editing_states_but_reject_malformed_values() {
        assertThat(MultiblockDetectorScreen.acceptsCoordinateCandidate("")).isTrue();
        assertThat(MultiblockDetectorScreen.acceptsCoordinateCandidate("-")).isTrue();
        assertThat(MultiblockDetectorScreen.acceptsCoordinateCandidate("-12")).isTrue();
        assertThat(MultiblockDetectorScreen.acceptsCoordinateCandidate("12-")).isFalse();
    }

    @Test
    void coordinate_sync_requires_a_set_point_and_a_complete_signed_integer() {
        BlockPos point = new BlockPos(10, 20, 30);

        assertThat(MultiblockDetectorScreen.canSyncCoordinate(false, point, "-12")).isTrue();
        assertThat(MultiblockDetectorScreen.canSyncCoordinate(false, point, "12-")).isFalse();
        assertThat(MultiblockDetectorScreen.canSyncCoordinate(false, point, "-")).isFalse();
        assertThat(MultiblockDetectorScreen.canSyncCoordinate(false, null, "12")).isFalse();
        assertThat(MultiblockDetectorScreen.canSyncCoordinate(true, point, "12")).isFalse();
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

    @Test
    void axis_adjustments_saturate_at_integer_boundaries() {
        assertThat(MultiblockDetectorScreen.adjustedCoordinate(4, 1)).isEqualTo(5);
        assertThat(MultiblockDetectorScreen.adjustedCoordinate(4, -1)).isEqualTo(3);
        assertThat(MultiblockDetectorScreen.adjustedCoordinate(Integer.MAX_VALUE, 1)).isEqualTo(Integer.MAX_VALUE);
        assertThat(MultiblockDetectorScreen.adjustedCoordinate(Integer.MIN_VALUE, -1)).isEqualTo(Integer.MIN_VALUE);
    }
}
