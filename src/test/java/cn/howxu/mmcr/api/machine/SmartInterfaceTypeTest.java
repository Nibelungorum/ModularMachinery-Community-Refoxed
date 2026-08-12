package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SmartInterfaceTypeTest {

    @Test
    void registration_keeps_interface_types_in_declaration_order() {
        var mode = new SmartInterfaceType("mode", 1.5F, 20, "head", "%.1f", "foot", "bad", "%s %.1f", 2);
        var speed = new SmartInterfaceType("speed", 2F, 10, "", "", "", "", "", 0);
        var registration = MachineRegistration.builder(MMCR.id("interface_test"))
                .localizedName("Interface Test").smartInterfaceType(mode).smartInterfaceType(speed).build();

        assertThat(registration.smartInterfaceTypes()).containsEntry("mode", mode).containsEntry("speed", speed);
        assertThat(registration.smartInterfaceTypes().keySet()).containsExactly("mode", "speed");
    }

    @Test
    void type_rejects_blank_name_and_non_finite_default_value() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SmartInterfaceType(" ", 0F, 0, null, null, null, null, null, -1));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SmartInterfaceType("mode", Float.NaN, 0, null, null, null, null, null, -1));
    }
}
