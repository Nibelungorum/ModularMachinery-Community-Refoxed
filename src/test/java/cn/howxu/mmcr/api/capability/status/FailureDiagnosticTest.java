package cn.howxu.mmcr.api.capability.status;

import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies immutable typed failure diagnostics and report selection.
 *
 * @author howxu <dev@howxu.cn>
 */
class FailureDiagnosticTest {
    private static final ResourceLocation TEST_ID = ResourceLocation.parse("mmcr_test:failure");
    private static final ResourceLocation TEST_RECIPE = ResourceLocation.parse("mmcr_test:recipe");

    @Test
    void report_selects_larger_reason_priority_before_validity() {
        FailureReason low = new FailureReason(TEST_ID.withPath("low"), "test.low", 10);
        FailureReason high = new FailureReason(TEST_ID.withPath("high"), "test.high", 20);
        ExecutionStatus lowStatus = status(low);
        ExecutionStatus highStatus = status(high);

        FailureReport report = FailureReport.empty()
                .plus(lowStatus, 1.0F)
                .plus(highStatus, 0.0F);

        assertThat(report.primary()).isSameAs(highStatus);
    }

    @Test
    void report_prefers_insufficient_temperature_over_missing_chemical_input() {
        ExecutionStatus missingChemical = status(MekanismFailureReasons.CHEMICAL_INPUT_MISSING);
        ExecutionStatus insufficientTemperature = status(MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT);

        FailureReport report = FailureReport.empty()
                .plus(missingChemical, 1.0F)
                .plus(insufficientTemperature, 1.0F);

        assertThat(report.primary()).isSameAs(insufficientTemperature);
    }

    @Test
    void report_uses_validity_then_insertion_order_for_equal_priority() {
        FailureReason reason = new FailureReason(TEST_ID.withPath("same"), "test.same", 10);
        ExecutionStatus first = status(reason);
        ExecutionStatus second = status(reason);

        assertThat(FailureReport.empty().plus(first, 0.5F).plus(second, 0.9F).primary())
                .isSameAs(second);
        assertThat(FailureReport.empty().plus(first, 0.5F).plus(second, 0.5F).primary())
                .isSameAs(first);
    }

    @Test
    void trace_append_preserves_the_original_source_frame() {
        FailureReason reason = new FailureReason(TEST_ID.withPath("trace"), "test.trace", 10);
        FailureOccurrence occurrence = FailureOccurrence.at(
                reason, TEST_ID.withPath("item_bus"), FailurePhase.CAPABILITY_COMMIT,
                null, 1, Map.of("available", "0"));

        FailureOccurrence traced = occurrence.append(
                TEST_ID.withPath("recipe"), FailurePhase.RECIPE_SEARCH, TEST_RECIPE, 1);

        assertThat(occurrence.trace().frames()).hasSize(1);
        assertThat(traced.trace().frames()).hasSize(2);
        assertThat(traced.trace().frames().getFirst().source()).isEqualTo(TEST_ID.withPath("item_bus"));
        assertThat(traced.trace().frames().getLast().recipeId()).isEqualTo(TEST_RECIPE);
        assertThat(traced.details()).containsEntry("available", "0");
        assertThatThrownBy(() -> traced.trace().frames().add(traced.trace().frames().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void trace_copies_input_frames_and_details_immutably() {
        FailureReason reason = new FailureReason(TEST_ID.withPath("immutable"), "test.immutable", 10);
        FailureTrace.Frame frame = new FailureTrace.Frame(
                TEST_ID.withPath("source"), FailurePhase.RUNTIME, null, null);
        List<FailureTrace.Frame> frames = new ArrayList<>(List.of(frame));
        Map<String, String> details = new HashMap<>();
        details.put("value", "before");

        FailureOccurrence occurrence = new FailureOccurrence(reason, new FailureTrace(frames), details);
        frames.clear();
        details.put("value", "after");

        assertThat(occurrence.trace().frames()).containsExactly(frame);
        assertThat(occurrence.details()).containsEntry("value", "before");
        assertThatThrownBy(() -> occurrence.details().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unknown_reason_resolution_returns_unknown_and_keeps_raw_id_in_details() {
        ResourceLocation rawReason = ResourceLocation.parse("old:removed_reason");
        ExecutionStatus restored = FailureStatusFixtures.unknownStatus(rawReason);

        assertThat(restored.reason()).isSameAs(BuiltinFailureReasons.UNKNOWN);
        assertThat(restored.details()).containsEntry("raw_reason_id", "old:removed_reason");
    }

    @Test
    void report_candidates_are_immutable() {
        ExecutionStatus status = status(new FailureReason(TEST_ID.withPath("candidate"), "test.candidate", 10));
        FailureReport report = FailureReport.empty().plus(status, 1.0F);

        assertThatThrownBy(() -> report.candidates().add(new FailureReport.Candidate(status, 0.0F)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static ExecutionStatus status(FailureReason reason) {
        FailureOccurrence occurrence = FailureOccurrence.at(reason, TEST_ID, FailurePhase.REQUIREMENT_PLAN,
                null, null, Map.of());
        return ExecutionStatus.blocked(TEST_ID, TEST_ID, occurrence);
    }

    private static final class FailureStatusFixtures {
        private static ExecutionStatus unknownStatus(ResourceLocation rawReason) {
            FailureReason reason = FailureReasonRegistry.resolve(rawReason);
            FailureOccurrence occurrence = FailureOccurrence.at(reason, TEST_ID, FailurePhase.UNKNOWN,
                    null, null, Map.of("raw_reason_id", rawReason.toString()));
            return ExecutionStatus.blocked(TEST_ID, TEST_ID, occurrence);
        }
    }
}
