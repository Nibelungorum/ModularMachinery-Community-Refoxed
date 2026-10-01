package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SmartInterfaceModifierTest {

    @Test
    void maps_interface_values_to_numeric_machine_modifiers() {
        SmartInterfaceModifier mapping = SmartInterfaceModifier.numeric(
                "speed", "duration", "input", 0F, 10F, 1F, 0.5F, "multiply", false);

        assertThat(mapping.toModifier(5F)).isEqualTo(
                MachineModifier.numeric("duration", "input", 0.75D, "multiply", false));
    }

    @Test
    void output_resource_constructor_normalizes_target_before_mapping() {
        SmartInterfaceModifier mapping = new SmartInterfaceModifier("yield", "item",
                RecipeModifier.IOType.OUTPUT, true, 0F, 10F, 1F, 2F, RecipeModifier.Operation.MULTIPLY);

        assertThat(mapping.target()).isEqualTo("output");
        assertThat(mapping.io()).isSameAs(RecipeModifier.IOType.OUTPUT);
        assertThat(mapping.toModifier(5F)).isEqualTo(
                MachineModifier.numeric("output", "output", 1.5D, "multiply", true));
    }

    @Test
    void declaration_constructor_rejects_unsupported_target_at_construction() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SmartInterfaceModifier("mode", "parallelized",
                "recipe", false, 0F, 1F, 0F, 1F, RecipeModifier.Operation.MULTIPLY));
    }

    @Test
    void rejects_boolean_parallelized_target() {
        assertThatIllegalArgumentException().isThrownBy(() -> SmartInterfaceModifier.numeric(
                "mode", "parallelized", "recipe", 0F, 1F, 0F, 1F, "multiply", false));
    }

    @Test
    void finite_extreme_endpoints_stay_finite_during_interpolation() {
        SmartInterfaceModifier mapping = SmartInterfaceModifier.numeric("speed", "duration", "input",
                -1F, 1F, Float.MAX_VALUE, -Float.MAX_VALUE, "multiply", false);

        assertThat(mapping.mappedValue(0F)).isFinite();
        assertThat(mapping.toModifier(0F).value()).isFinite();
    }
}
