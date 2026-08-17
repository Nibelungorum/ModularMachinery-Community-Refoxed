package cn.howxu.mmcr.api.machine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortRequirementSpecTest {

    @Test
    void none_has_no_failure_for_empty_counts() {
        assertThat(PortRequirementSpec.none().validate(PortRequirementSpec.PortCounts.empty())).isEmpty();
    }

    @Test
    void min_requirement_reports_missing_port() {
        PortRequirementSpec spec = PortRequirementSpec.builder()
                .min("energy_input_hatch", 1)
                .build();

        var failure = spec.validate(PortRequirementSpec.PortCounts.empty());

        assertThat(failure).hasValueSatisfying(value -> {
            assertThat(value.portId()).isEqualTo("energy_input_hatch");
            assertThat(value.reason()).isEqualTo(PortRequirementSpec.FailureReason.MISSING);
            assertThat(value.actual()).isZero();
            assertThat(value.requiredMin()).isEqualTo(1);
            assertThat(value.requiredMax()).isEmpty();
        });
    }

    @Test
    void min_requirement_passes_when_actual_count_is_enough() {
        PortRequirementSpec spec = PortRequirementSpec.builder()
                .min("energy_input_hatch", 1)
                .build();

        assertThat(spec.validate(PortRequirementSpec.PortCounts.of(Map.of("energy_input_hatch", 1)))).isEmpty();
    }

    @Test
    void max_requirement_reports_too_many_ports() {
        PortRequirementSpec spec = PortRequirementSpec.builder()
                .range("item_input_bus", 0, 1)
                .build();

        var failure = spec.validate(PortRequirementSpec.PortCounts.of(Map.of("item_input_bus", 2)));

        assertThat(failure).hasValueSatisfying(value -> {
            assertThat(value.portId()).isEqualTo("item_input_bus");
            assertThat(value.reason()).isEqualTo(PortRequirementSpec.FailureReason.TOO_MANY);
            assertThat(value.actual()).isEqualTo(2);
            assertThat(value.requiredMin()).isZero();
            assertThat(value.requiredMax()).hasValue(1);
        });
    }

    @Test
    void invalid_ranges_are_rejected() {
        assertThatThrownBy(() -> PortRequirementSpec.builder().min("item_input_bus", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("min");
        assertThatThrownBy(() -> PortRequirementSpec.builder().range("item_input_bus", 2, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max");
    }
}
