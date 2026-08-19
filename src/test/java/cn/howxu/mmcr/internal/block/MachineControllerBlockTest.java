package cn.howxu.mmcr.internal.block;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerBlockTest {

    @Test
    void vertical_allowed_controller_uses_clicked_face_for_placement() {
        assertThat(MachineControllerBlock.facingForPlacement(Direction.UP, 1.5d, 1.0d, Direction.NORTH, true)).isEqualTo(Direction.NORTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.UP, 1.5d, 3.0d, Direction.NORTH, true)).isEqualTo(Direction.UP);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.DOWN, 2.5d, 1.0d, Direction.NORTH, true)).isEqualTo(Direction.DOWN);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.UP, 3.5d, 3.0d, Direction.NORTH, true)).isEqualTo(Direction.NORTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.EAST, 3.5d, 2.0d, Direction.NORTH, true)).isEqualTo(Direction.DOWN);
    }

    @Test
    void vertical_placement_roll_facing_anchors_toward_player() {
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.UP, 10.5d, 10.5d, 8.0d, 10.0d, Direction.NORTH)).isEqualTo(Direction.WEST);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.DOWN, 10.5d, 10.5d, 13.0d, 10.0d, Direction.NORTH)).isEqualTo(Direction.EAST);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.UP, 10.5d, 10.5d, 10.0d, 8.0d, Direction.NORTH)).isEqualTo(Direction.NORTH);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.DOWN, 10.5d, 10.5d, 10.0d, 13.0d, Direction.NORTH)).isEqualTo(Direction.SOUTH);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.UP, 10.5d, 10.5d, 9.5d, 9.5d, Direction.EAST)).isEqualTo(Direction.EAST);
        assertThat(MachineControllerBlock.rollFacingForPlacement(Direction.NORTH, 10.5d, 10.5d, 8.0d, 10.0d, Direction.SOUTH)).isEqualTo(Direction.SOUTH);
    }

    @Test
    void horizontal_only_controller_falls_back_when_clicked_face_is_vertical() {
        assertThat(MachineControllerBlock.facingForPlacement(Direction.UP, Direction.NORTH, false)).isEqualTo(Direction.NORTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.DOWN, Direction.SOUTH, false)).isEqualTo(Direction.SOUTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.EAST, Direction.NORTH, false)).isEqualTo(Direction.EAST);
    }

    @Test
    void required_vertical_controller_never_uses_horizontal_facing() {
        assertThat(MachineControllerBlock.facingForPlacement(Direction.EAST, 2.0d, 2.0d,
                Direction.NORTH, true, true).getAxis().isVertical()).isTrue();
        assertThat(MachineControllerBlock.facingForPlacement(Direction.WEST, 4.0d, 2.0d,
                Direction.NORTH, true, true).getAxis().isVertical()).isTrue();
    }
}
