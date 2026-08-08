package cn.howxu.mmcr.datagen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelGenTest {

    @Test
    void io_port_overlay_texture_uses_matching_port_tier() {
        assertThat(ModelGen.overlayTextureFor("item_input_bus_big")).isEqualTo("overlay_inputbus_big");
        assertThat(ModelGen.overlayTextureFor("item_output_bus_ludicrous")).isEqualTo("overlay_outputbus_ludicrous");
        assertThat(ModelGen.overlayTextureFor("fluid_input_hatch_vacuum")).isEqualTo("overlay_fluidinputhatch_vacuum");
        assertThat(ModelGen.overlayTextureFor("fluid_output_hatch_huge")).isEqualTo("overlay_fluidoutputhatch_huge");
        assertThat(ModelGen.overlayTextureFor("energy_input_hatch_ultimate")).isEqualTo("overlay_energyinputhatch_ultimate");
        assertThat(ModelGen.overlayTextureFor("energy_output_hatch_reinforced")).isEqualTo("overlay_energyoutputhatch_reinforced");
    }

    @Test
    void normal_io_port_overlay_texture_keeps_unsuffixed_registration_id() {
        assertThat(ModelGen.overlayTextureFor("item_input_bus")).isEqualTo("overlay_inputbus_normal");
        assertThat(ModelGen.overlayTextureFor("fluid_output_hatch")).isEqualTo("overlay_fluidoutputhatch_normal");
        assertThat(ModelGen.overlayTextureFor("energy_input_hatch")).isEqualTo("overlay_energyinputhatch_normal");
    }

    @Test
    void machine_controllers_use_shared_dynamic_model_reference() {
        assertThat(ModelGen.dynamicControllerModel()).hasToString("mmcr:block/dynamic_machine_controller");
    }

    @Test
    void io_ports_use_shared_dynamic_model_reference() {
        assertThat(ModelGen.dynamicIoPortModel()).hasToString("mmcr:block/dynamic_io_port");
    }
}
