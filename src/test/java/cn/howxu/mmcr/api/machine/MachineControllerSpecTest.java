package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerSpecTest {

    @Test
    void defaults_derive_controller_id_and_safe_textures_from_machine_id() {
        MachineControllerSpec spec = MachineControllerSpec.defaultsFor(MMCR.id("blast_furnace"));

        assertThat(spec.id()).isEqualTo(MMCR.id("blast_furnace_controller"));
        assertThat(spec.frontTexture()).isEqualTo(MMCR.id("block/basic_controller"));
        assertThat(spec.sideTexture()).isEqualTo(MMCR.id("block/basic_casing"));
        assertThat(spec.topTexture()).isEqualTo(MMCR.id("block/basic_casing"));
        assertThat(spec.bottomTexture()).isEqualTo(MMCR.id("block/basic_casing"));
        assertThat(spec.allowVerticalFacing()).isFalse();
        assertThat(spec.fullyRotationallySymmetric()).isFalse();
        assertThat(spec.requireVerticalFacing()).isFalse();
        assertThat(spec.tooltip()).isEmpty();
    }

    @Test
    void dynamic_machine_compat_constructor_uses_default_controller_spec() {
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("blast_furnace"), "高炉", new BlockArray(Map.of()));

        assertThat(machine.controller()).isEqualTo(MachineControllerSpec.defaultsFor(MMCR.id("blast_furnace")));
    }

    @Test
    void spec_rejects_null_values() {
        Identifier id = MMCR.id("blast_furnace_controller");
        Identifier texture = MMCR.id("block/basic_controller");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MachineControllerSpec(null, texture, texture, texture, texture, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MachineControllerSpec(id, null, texture, texture, texture, false));
    }

    @Test
    void spec_can_opt_into_vertical_controller_facing() {
        Identifier id = MMCR.id("blast_furnace_controller");
        Identifier texture = MMCR.id("block/basic_controller");

        MachineControllerSpec spec = new MachineControllerSpec(id, texture, texture, texture, texture, true);

        assertThat(spec.allowVerticalFacing()).isTrue();
        assertThat(spec.fullyRotationallySymmetric()).isFalse();
    }

    @Test
    void spec_can_opt_into_full_rotational_symmetry() {
        Identifier id = MMCR.id("cracker_controller");
        Identifier texture = MMCR.id("block/basic_controller");

        MachineControllerSpec spec = new MachineControllerSpec(id, texture, texture, texture, texture, true, true);

        assertThat(spec.allowVerticalFacing()).isTrue();
        assertThat(spec.fullyRotationallySymmetric()).isTrue();
        assertThat(spec.requireVerticalFacing()).isFalse();
    }

    @Test
    void spec_can_require_vertical_facing() {
        Identifier id = MMCR.id("cracker_controller");
        Identifier texture = MMCR.id("block/basic_controller");

        MachineControllerSpec spec = new MachineControllerSpec(id, texture, texture, texture, texture, true, true, true);

        assertThat(spec.requireVerticalFacing()).isTrue();
    }

    @Test
    void spec_can_store_controller_tooltip_lines() {
        Identifier id = MMCR.id("cracker_controller");
        Identifier texture = MMCR.id("block/basic_controller");

        MachineControllerSpec spec = new MachineControllerSpec(id, texture, texture, texture, texture,
                false, false, false, List.of("tooltip.mmcr.cracker.0", "tooltip.mmcr.cracker.1"));

        assertThat(spec.tooltip()).containsExactly("tooltip.mmcr.cracker.0", "tooltip.mmcr.cracker.1");
    }
}
