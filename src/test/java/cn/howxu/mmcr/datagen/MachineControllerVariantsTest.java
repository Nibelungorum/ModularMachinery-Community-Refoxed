package cn.howxu.mmcr.datagen;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerVariantsTest {

    @Test
    void vertical_controller_model_rotation_depends_on_roll_facing() {
        assertThat(MachineControllerVariants.rotationFor(Direction.UP, Direction.SOUTH))
                .isNotEqualTo(MachineControllerVariants.rotationFor(Direction.UP, Direction.NORTH));
        assertThat(MachineControllerVariants.rotationFor(Direction.DOWN, Direction.SOUTH))
                .isNotEqualTo(MachineControllerVariants.rotationFor(Direction.DOWN, Direction.NORTH));
    }

    @Test
    void full_dispatch_includes_roll_facing_property() {
        assertThat(MachineControllerVariants.propertyCount()).isEqualTo(4);
    }
}
