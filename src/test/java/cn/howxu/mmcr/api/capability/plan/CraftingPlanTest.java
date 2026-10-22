package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CraftingPlanTest {
    private static final ExecutionStatus FAILURE = new ExecutionStatus(
            ResourceLocation.fromNamespaceAndPath("mmcr_test", "plan_failure"),
            StatusSeverity.FAILURE,
            ResourceLocation.fromNamespaceAndPath("mmcr_test", "plan"),
            FailureOccurrence.at(BuiltinFailureReasons.UNKNOWN,
                    ResourceLocation.fromNamespaceAndPath("mmcr_test", "plan"), FailurePhase.CAPABILITY_COMMIT,
                    null, null, Map.of()));

    @Test
    void commits_input_and_output_operations_by_direction() {
        AtomicInteger inputs = new AtomicInteger();
        AtomicInteger outputs = new AtomicInteger();
        CraftingPlan plan = new CraftingPlan(List.of(
                new RequirementPlan(0, 1L, List.of(() -> {
                    inputs.incrementAndGet();
                    return CapabilityResult.successful();
                }), null),
                new RequirementPlan(1, 1L, List.of(() -> {
                    outputs.incrementAndGet();
                    return CapabilityResult.successful();
                }), null)), 1L,
                Map.of(0, RecipeModifier.IOType.INPUT, 1, RecipeModifier.IOType.OUTPUT));

        assertThat(plan.commitInputs()).isTrue();
        assertThat(inputs).hasValue(1);
        assertThat(outputs).hasValue(0);
        assertThat(plan.commitOutputs()).isTrue();
        assertThat(outputs).hasValue(1);
    }

    @Test
    void excluded_inputs_and_outputs_stay_unpaid_during_native_start_commit() {
        LongValueStorage storage = new LongValueStorage(20L, 20L, null);
        storage.setAmount(20L);
        CraftingPlan plan = new CraftingPlan(List.of(
                new RequirementPlan(2, 1, List.of(() -> {
                    storage.setAmount(storage.amount() - 7L);
                    return CapabilityResult.successful();
                }), null),
                new RequirementPlan(5, 1, List.of(() -> {
                    storage.setAmount(storage.amount() - 3L);
                    return CapabilityResult.successful();
                }), null),
                new RequirementPlan(8, 1, List.of(() -> {
                    storage.setAmount(storage.amount() + 4L);
                    return CapabilityResult.successful();
                }), null)), 1,
                Map.of(2, RecipeModifier.IOType.INPUT, 5, RecipeModifier.IOType.INPUT, 8, RecipeModifier.IOType.OUTPUT));

        assertThat(plan.commitInputsRuntime(Set.of(2), () -> { }, () -> true)).isTrue();
        assertThat(storage.amount()).isEqualTo(17L);
        assertThat(plan.wasNativeExecuted(2)).isFalse();
        assertThat(plan.wasNativeExecuted(5)).isTrue();
        assertThat(plan.wasNativeExecuted(8)).isFalse();
    }

    @Test
    void stops_at_the_first_native_failure_and_preserves_its_status() {
        AtomicInteger completed = new AtomicInteger();
        CraftingPlan plan = plan(() -> {
            completed.incrementAndGet();
            return CapabilityResult.successful();
        }, () -> CapabilityResult.failure(FAILURE), () -> {
            completed.incrementAndGet();
            return CapabilityResult.successful();
        });

        assertThat(plan.commit()).isFalse();
        assertThat(completed).hasValue(1);
        assertThat(plan.failure()).isSameAs(FAILURE);
    }

    @Test
    void reports_structured_failure_for_null_or_untyped_results() {
        CraftingPlan nullPlan = plan(() -> null);
        CraftingPlan untypedPlan = plan(() -> new CapabilityResult(false, null));

        assertThat(nullPlan.commit()).isFalse();
        assertThat(untypedPlan.commit()).isFalse();
        assertThat(nullPlan.failure().reason()).isEqualTo(BuiltinFailureReasons.OPERATION_FAILED_WITHOUT_STATUS);
        assertThat(untypedPlan.failure().reason()).isEqualTo(BuiltinFailureReasons.OPERATION_FAILED_WITHOUT_STATUS);
    }

    @Test
    void output_simulation_collection_is_immutable() {
        OutputSimulation simulation = new OutputSimulation(1L, 1L, OutputFit.FULL);
        CraftingPlan plan = new CraftingPlan(List.of(
                RequirementPlan.withOutputSimulation(0, 1L, List.of(), null, simulation)), 1L);

        assertThatThrownBy(() -> plan.outputSimulations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static CraftingPlan plan(CapabilityOperation... operations) {
        return new CraftingPlan(List.of(new RequirementPlan(0, 1L, List.of(operations), null)), 1L);
    }
}
