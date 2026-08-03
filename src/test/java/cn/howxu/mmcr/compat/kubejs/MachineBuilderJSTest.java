package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineBuilderJSTest {

    @Test
    void controller_textures_sets_front_and_all_other_faces() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures(MMCR.id("block/arc_front"), MMCR.id("block/arc_side"))
                .createObject();

        assertThat(machine.controller()).isEqualTo(new MachineControllerSpec(
                MMCR.id("arc_furnace_controller"),
                MMCR.id("block/arc_front"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side")));
    }

    @Test
    void individual_texture_setters_override_only_that_face() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures("mmcr:block/arc_front", "mmcr:block/arc_side")
                .controllerTopTexture(MMCR.id("block/arc_top"))
                .controllerBottomTexture(MMCR.id("block/arc_bottom"))
                .createObject();

        assertThat(machine.controller().frontTexture()).isEqualTo(MMCR.id("block/arc_front"));
        assertThat(machine.controller().sideTexture()).isEqualTo(MMCR.id("block/arc_side"));
        assertThat(machine.controller().topTexture()).isEqualTo(MMCR.id("block/arc_top"));
        assertThat(machine.controller().bottomTexture()).isEqualTo(MMCR.id("block/arc_bottom"));
    }
}
