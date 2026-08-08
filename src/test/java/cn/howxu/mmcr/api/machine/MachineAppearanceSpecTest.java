package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineAppearanceSpecTest {

    @Test
    void defaults_use_basic_casing_for_all_base_textures() {
        MachineAppearanceSpec spec = MachineAppearanceSpec.defaults();

        assertThat(spec.machineBasicBlock()).isEqualTo(MMCR.id("basic_casing"));
        assertThat(spec.controllerBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
        assertThat(spec.formedPortBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void from_basic_block_derives_block_texture_path() {
        Identifier blockId = Identifier.parse("kubejs:steel_casing");

        MachineAppearanceSpec spec = MachineAppearanceSpec.fromBasicBlock(blockId);

        assertThat(spec.machineBasicBlock()).isEqualTo(blockId);
        assertThat(spec.controllerBaseTexture()).isEqualTo(Identifier.parse("kubejs:block/steel_casing"));
        assertThat(spec.formedPortBaseTexture()).isEqualTo(Identifier.parse("kubejs:block/steel_casing"));
    }

    @Test
    void dynamic_machine_exposes_default_appearance() {
        DynamicMachine machine = new DynamicMachine(MMCR.id("lathe"), "Lathe", new BlockArray(Map.of()));

        assertThat(machine.appearance()).isEqualTo(MachineAppearanceSpec.defaults());
    }

    @Test
    void null_fields_are_rejected() {
        assertThatThrownBy(() -> new MachineAppearanceSpec(null, MMCR.id("block/basic_casing"), MMCR.id("block/basic_casing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("machineBasicBlock null");
    }
}
