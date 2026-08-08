package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineBuilderJSTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        MachineDefinitions.beginRegistryPhase();
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void beginRegistryPhase() {
        MachineDefinitions.beginRegistryPhase();
    }

    @AfterEach
    void resetDefinitions() {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.beginRegistryPhase();
    }

    @Test
    void controller_textures_sets_front_and_all_other_faces() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures(MMCR.id("block/arc_front"), MMCR.id("block/arc_side"))
                .createObject();

        assertThat(machine.controllerSpec()).isEqualTo(new MachineControllerSpec(
                MMCR.id("arc_furnace_controller"),
                MMCR.id("block/arc_front"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side"),
                false));
    }

    @Test
    void individual_texture_setters_override_only_that_face() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures("mmcr:block/arc_front", "mmcr:block/arc_side")
                .controllerTopTexture(MMCR.id("block/arc_top"))
                .controllerBottomTexture(MMCR.id("block/arc_bottom"))
                .createObject();

        assertThat(machine.controllerSpec().frontTexture()).isEqualTo(MMCR.id("block/arc_front"));
        assertThat(machine.controllerSpec().sideTexture()).isEqualTo(MMCR.id("block/arc_side"));
        assertThat(machine.controllerSpec().topTexture()).isEqualTo(MMCR.id("block/arc_top"));
        assertThat(machine.controllerSpec().bottomTexture()).isEqualTo(MMCR.id("block/arc_bottom"));
    }

    @Test
    void allow_vertical_facing_sets_controller_spec_flag() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .allowVerticalFacing()
                .createObject();

        assertThat(machine.controllerSpec().allowVerticalFacing()).isTrue();
    }

    @Test
    void full_rotational_symmetry_sets_controller_spec_flag() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .fullyRotationallySymmetric()
                .createObject();

        assertThat(machine.controllerSpec().fullyRotationallySymmetric()).isTrue();
    }

    @Test
    void require_vertical_facing_sets_controller_spec_flags() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .requireVerticalFacing()
                .createObject();

        assertThat(machine.controllerSpec().allowVerticalFacing()).isTrue();
        assertThat(machine.controllerSpec().requireVerticalFacing()).isTrue();
    }

    @Test
    void startup_builder_creates_machine_registration_without_structure() {
        var registration = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .localizedName("Arc Furnace")
                .allowVerticalFacing()
                .allowModifiers()
                .createObject();

        assertThat(registration.id()).isEqualTo(MMCR.id("arc_furnace"));
        assertThat(registration.localizedName()).isEqualTo("Arc Furnace");
        assertThat(registration.controllerSpec().allowVerticalFacing()).isTrue();
        assertThat(registration.allowModifiers()).isTrue();
    }

    @Test
    void builder_sets_machine_basic_block_for_all_base_textures() {
        var registration = new MachineBuilderJS("mmcr:electric_press")
                .localizedName("Electric Press")
                .appearance("kubejs:steel_casing")
                .createObject();

        assertThat(registration.appearance()).isEqualTo(new MachineAppearanceSpec(
                Identifier.parse("kubejs:steel_casing"),
                Identifier.parse("kubejs:block/steel_casing"),
                Identifier.parse("kubejs:block/steel_casing")));
    }

    @Test
    void builder_allows_explicit_controller_and_port_base_overrides() {
        var registration = new MachineBuilderJS("mmcr:electric_press")
                .machineBasicBlock("kubejs:steel_casing")
                .controllerBaseTexture("mmcr:block/basic_casing")
                .formedPortBaseTexture("kubejs:block/clean_steel_casing")
                .createObject();

        assertThat(registration.appearance().machineBasicBlock()).isEqualTo(Identifier.parse("kubejs:steel_casing"));
        assertThat(registration.appearance().controllerBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
        assertThat(registration.appearance().formedPortBaseTexture()).isEqualTo(Identifier.parse("kubejs:block/clean_steel_casing"));
    }

    @Test
    void startup_builder_registers_only_during_registry_phase() {
        var builder = new MachineBuilderJS(MMCR.id("startup_press"));
        builder.registerObject();

        assertThat(MachineDefinitions.getRegistration(MMCR.id("startup_press"))).isNotNull();

        MachineDefinitions.freezeRegistryPhase();
        assertThatThrownBy(builder::registerObject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("registry phase");
    }
}
