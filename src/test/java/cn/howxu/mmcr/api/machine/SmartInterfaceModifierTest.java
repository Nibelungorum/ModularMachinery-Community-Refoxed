package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.modifier.ModifierTarget;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmartInterfaceModifierTest {

    @Test
    void nonlinear_function_produces_a_numeric_modifier_applied_to_duration() {
        SmartInterfaceModifier mapping = SmartInterfaceModifier.numeric(
                "speed", "duration", "input", value -> 1D / (1D + value * value), "multiply", false);

        assertThat(mapping.toModifier(2F)).isEqualTo(
                MachineModifier.numeric("duration", "input", 0.2D, "multiply", false));
        assertThat(MachineModifier.apply(List.of(mapping.toModifier(2F)), ModifierTarget.DURATION, 200D, false))
                .isEqualTo(40D);
        assertThat(mapping.mappedValue(10F)).isEqualTo(1D / 101D);
    }

    @Test
    void piecewise_mapping_is_not_clamped_or_sampled_during_registration() {
        AtomicInteger calls = new AtomicInteger();
        SmartInterfaceModifier mapping = SmartInterfaceModifier.duration("speed", value -> {
            calls.incrementAndGet();
            return value < 20 ? 1D : value < 80 ? 0.75D : 0.5D;
        }, null);

        assertThat(calls).hasValue(0);
        assertThat(mapping.operation()).isEqualTo(RecipeModifier.Operation.MULTIPLY);
        assertThat(mapping.mappedValue(-100F)).isEqualTo(1D);
        assertThat(mapping.mappedValue(50F)).isEqualTo(0.75D);
        assertThat(mapping.mappedValue(200F)).isEqualTo(0.5D);
        assertThat(calls).hasValue(3);
    }

    @Test
    void output_resource_constructor_normalizes_target_before_mapping() {
        SmartInterfaceModifier mapping = new SmartInterfaceModifier("yield", "item",
                RecipeModifier.IOType.OUTPUT, true, value -> 1D + value / 10D, RecipeModifier.Operation.MULTIPLY);

        assertThat(mapping.target()).isEqualTo("output");
        assertThat(mapping.io()).isSameAs(RecipeModifier.IOType.OUTPUT);
        assertThat(mapping.toModifier(5F)).isEqualTo(
                MachineModifier.numeric("output", "output", 1.5D, "multiply", true));
    }

    @Test
    void validates_metadata_without_executing_the_function() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SmartInterfaceModifier("mode", "parallelized",
                "recipe", false, value -> { throw new AssertionError("must not run"); }, RecipeModifier.Operation.MULTIPLY));
        assertThatIllegalArgumentException().isThrownBy(() -> SmartInterfaceModifier.numeric(
                "mode", "parallelized", "recipe", value -> value, "multiply", false));
        assertThatIllegalArgumentException().isThrownBy(() -> SmartInterfaceModifier.numeric(
                "mode", "duration", "input", value -> value, "multiply", true));
        assertThatNullPointerException().isThrownBy(() -> SmartInterfaceModifier.duration("speed", null, null));
    }

    @Test
    void preserves_double_precision_and_finite_values_beyond_float_range() {
        SmartInterfaceModifier mapping = SmartInterfaceModifier.energy("power", value -> 1D / 3D, null);
        assertThat(mapping.toModifier(1F).value()).isEqualTo(1D / 3D);
        assertThat(SmartInterfaceModifier.energy("power", value -> Double.MAX_VALUE, null).toModifier(1F).value())
                .isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void rejects_non_finite_input_and_output_and_propagates_callback_failures() {
        SmartInterfaceModifier identity = SmartInterfaceModifier.energy("power", value -> value, null);
        for (float input : new float[] { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }) {
            assertThatIllegalArgumentException().isThrownBy(() -> identity.toModifier(input));
        }
        for (double output : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            SmartInterfaceModifier invalid = SmartInterfaceModifier.energy("power", value -> output, null);
            assertThatIllegalArgumentException().isThrownBy(() -> invalid.toModifier(1F));
        }
        IllegalStateException failure = new IllegalStateException("bad mapping");
        SmartInterfaceModifier throwing = SmartInterfaceModifier.duration("speed", value -> { throw failure; }, null);
        assertThatThrownBy(() -> throwing.toModifier(1F)).isSameAs(failure);
    }
}
