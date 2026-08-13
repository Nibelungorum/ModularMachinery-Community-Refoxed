package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerBlockTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void menu_title_uses_machine_localized_name() {
        assertThat(((net.minecraft.network.chat.contents.TranslatableContents) MachineControllerBlock.titleFor(MMCR.id("blast_furnace")).getContents()).getKey())
                .isEqualTo("machine.mmcr.blast_furnace");
    }

    @Test
    void controller_facing_property_accepts_vertical_values() {
        assertThat(MachineControllerBlock.FACING).isEqualTo(BlockStateProperties.FACING);
        assertThat(MachineControllerBlock.FACING.getPossibleValues()).contains(Direction.UP);
    }

    @Test
    void cracker_controller_definition_allows_vertical_placement() {
        assertThat(cn.howxu.mmcr.api.machine.MachineDefinitions.getRegistration(MMCR.id("cracker")).controllerSpec().allowVerticalFacing()).isTrue();
    }

    @Test
    void cracker_controller_definition_is_fully_rotationally_symmetric() {
        assertThat(cn.howxu.mmcr.api.machine.MachineDefinitions.getRegistration(MMCR.id("cracker")).controllerSpec().fullyRotationallySymmetric()).isTrue();
    }

    @Test
    void cracker_controller_definition_does_not_require_vertical_placement() {
        assertThat(cn.howxu.mmcr.api.machine.MachineDefinitions.getRegistration(MMCR.id("cracker")).controllerSpec().requireVerticalFacing()).isFalse();
    }

    @Test
    void blast_furnace_controller_definition_is_horizontal_only() {
        assertThat(cn.howxu.mmcr.api.machine.MachineDefinitions.getRegistration(MMCR.id("blast_furnace")).controllerSpec().allowVerticalFacing()).isFalse();
    }

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
    void controller_has_separate_roll_facing_property_for_vertical_placement() {
        assertThat(MachineControllerBlock.ROLL_FACING.getName()).isEqualTo("roll_facing");
        assertThat(MachineControllerBlock.ROLL_FACING.getPossibleValues()).containsExactlyInAnyOrder(
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST);
    }

    @Test
    void horizontal_only_controller_falls_back_when_clicked_face_is_vertical() {
        assertThat(MachineControllerBlock.facingForPlacement(Direction.UP, Direction.NORTH, false)).isEqualTo(Direction.NORTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.DOWN, Direction.SOUTH, false)).isEqualTo(Direction.SOUTH);
        assertThat(MachineControllerBlock.facingForPlacement(Direction.EAST, Direction.NORTH, false)).isEqualTo(Direction.EAST);
    }
}
