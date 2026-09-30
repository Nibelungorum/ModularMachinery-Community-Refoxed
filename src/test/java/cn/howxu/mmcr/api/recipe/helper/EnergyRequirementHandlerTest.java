package cn.howxu.mmcr.api.recipe.helper;

import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirementHandler;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies native energy requirement planning and commit behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
class EnergyRequirementHandlerTest {
    @Test
    void single_input_hatch_with_enough_energy_consumes_exactly_required_fe() {
        LongEnergyStorage hatch = chargedStorage(1_000, 500, 300);

        RequirementPlan plan = energyPlan(RecipeModifier.IOType.INPUT, 120, 1, List.of(hatch));

        assertThat(plan.successful()).isTrue();
        assertThat(commit(plan, 1)).isTrue();
        assertThat(hatch.getAmountAsLong()).isEqualTo(180);
    }

    @Test
    void multiple_input_hatches_can_satisfy_one_tick_together() {
        LongEnergyStorage first = chargedStorage(1_000, 500, 80);
        LongEnergyStorage second = chargedStorage(1_000, 500, 200);

        RequirementPlan plan = energyPlan(RecipeModifier.IOType.INPUT, 150, 1, List.of(first, second));

        assertThat(plan.successful()).isTrue();
        assertThat(commit(plan, 1)).isTrue();
        assertThat(first.getAmountAsLong()).isZero();
        assertThat(second.getAmountAsLong()).isEqualTo(130);
    }

    @Test
    void insufficient_combined_energy_fails_without_mutation() {
        LongEnergyStorage first = chargedStorage(1_000, 500, 60);
        LongEnergyStorage second = chargedStorage(1_000, 500, 70);

        RequirementPlan plan = energyPlan(RecipeModifier.IOType.INPUT, 150, 1, List.of(first, second));

        assertThat(plan.successful()).isFalse();
        assertThat(first.getAmountAsLong()).isEqualTo(60);
        assertThat(second.getAmountAsLong()).isEqualTo(70);
    }

    @Test
    void insufficient_transfer_limit_fails_without_partial_mutation() {
        LongEnergyStorage first = chargedStorage(1_000, 40, 500);
        LongEnergyStorage second = chargedStorage(1_000, 40, 500);

        RequirementPlan plan = energyPlan(RecipeModifier.IOType.INPUT, 100, 1, List.of(first, second));

        assertThat(plan.successful()).isTrue();
        assertThat(commit(plan, 1)).isFalse();
        assertThat(first.getAmountAsLong()).isEqualTo(500);
        assertThat(second.getAmountAsLong()).isEqualTo(500);
    }

    @Test
    void output_hatch_with_capacity_receives_required_fe() {
        LongEnergyStorage hatch = emptyStorage(1_000, 500);

        RequirementPlan plan = energyPlan(RecipeModifier.IOType.OUTPUT, 200, 1, List.of(hatch));

        assertThat(plan.successful()).isTrue();
        assertThat(commit(plan, 1)).isTrue();
        assertThat(hatch.getAmountAsLong()).isEqualTo(200);
    }

    @Test
    void multiple_output_hatches_can_satisfy_one_tick_together() {
        LongEnergyStorage first = emptyStorage(150, 150);
        LongEnergyStorage second = emptyStorage(150, 150);

        RequirementPlan plan = energyPlan(RecipeModifier.IOType.OUTPUT, 200, 1, List.of(first, second));

        assertThat(plan.successful()).isTrue();
        assertThat(commit(plan, 1)).isTrue();
        assertThat(first.getAmountAsLong()).isEqualTo(150);
        assertThat(second.getAmountAsLong()).isEqualTo(50);
    }

    @Test
    void insufficient_output_capacity_fails_without_mutation() {
        LongEnergyStorage first = emptyStorage(50, 50);
        LongEnergyStorage second = emptyStorage(50, 50);

        RequirementPlan plan = energyPlan(RecipeModifier.IOType.OUTPUT, 200, 1, List.of(first, second));

        assertThat(plan.successful()).isFalse();
        assertThat(first.getAmountAsLong()).isZero();
        assertThat(second.getAmountAsLong()).isZero();
    }

    @Test
    void output_plan_does_not_mutate_storage_before_commit() {
        LongEnergyStorage hatch = emptyStorage(1_000, 500);

        RequirementPlan plan = energyPlan(RecipeModifier.IOType.OUTPUT, 200, 1, List.of(hatch));

        assertThat(plan.successful()).isTrue();
        assertThat(hatch.getAmountAsLong()).isZero();
    }

    @Test
    void output_parallelism_scales_required_energy() {
        LongEnergyStorage first = emptyStorage(500, 500);
        LongEnergyStorage second = emptyStorage(500, 500);

        RequirementPlan plan = energyPlan(RecipeModifier.IOType.OUTPUT, 200, 3, List.of(first, second));

        assertThat(plan.successful()).isTrue();
        assertThat(commit(plan, 3)).isTrue();
        assertThat(first.getAmountAsLong() + second.getAmountAsLong()).isEqualTo(600);
    }

    private static RequirementPlan energyPlan(RecipeModifier.IOType io, long fePerTick, long parallelism,
                                               List<LongEnergyStorage> storages) {
        IOType direction = io == RecipeModifier.IOType.OUTPUT ? IOType.OUTPUT : IOType.INPUT;
        List<MachineCapability> capabilities = storages.stream()
                .<MachineCapability>map(storage -> new EnergyHatchCapability(storage, direction)).toList();
        return new EnergyRequirementHandler().plan(new EnergyRequirement(io, fePerTick),
                capabilities,
                new PlanningContext(parallelism, 0));
    }

    private static boolean commit(RequirementPlan plan, long parallelism) {
        RequirementPlan materialized = plan.materialize(parallelism, new PlanningReservations(), null);
        return materialized.successful()
                && materialized.operations().stream().allMatch(operation -> operation.commit().success());
    }

    private static LongEnergyStorage chargedStorage(int capacity, int transferLimit, int inserted) {
        LongEnergyStorage storage = new LongEnergyStorage(capacity, transferLimit, () -> {});
        storage.forceInsert(inserted, false);
        assertThat(storage.getAmountAsLong()).isEqualTo(inserted);
        return storage;
    }

    private static LongEnergyStorage emptyStorage(int capacity, int transferLimit) {
        return new LongEnergyStorage(capacity, transferLimit, () -> {});
    }
}
