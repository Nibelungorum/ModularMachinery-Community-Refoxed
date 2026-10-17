package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Scalar worker descriptors must not turn probabilistic resources into guaranteed transfers.
 * @author howxu <dev@howxu.cn>
 */
class ResourceChanceAsyncPlanningTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void probabilistic_inputs_and_outputs_use_the_sampling_aware_main_thread_handler() {
        List<MachineRequirement> requirements = List.of(SourceRequirement.input(100L, 0F),
                SourceRequirement.output(100L, 0.25F), ManaRequirement.input(200L, 0.25F),
                ManaRequirement.output(200L, 0F));
        var prepared = CraftingContext.prepareAsyncPlan(requirements, 4L, List.of());
        var compiled = CraftingContext.prepareCompiledStage(requirements, 4L, List.of(), Map.of());

        assertThat(prepared.requirements()).isEmpty();
        assertThat(prepared.initialMainThreadRequirements()).containsExactly(0, 1, 2, 3);
        assertThat(compiled.requirements()).isEmpty();
        assertThat(compiled.fallbackIndices()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void default_probability_retains_existing_scalar_worker_planning() {
        List<MachineRequirement> requirements = List.of(SourceRequirement.input(100L),
                SourceRequirement.output(100L), ManaRequirement.input(200L), ManaRequirement.output(200L));
        var prepared = CraftingContext.prepareAsyncPlan(requirements, 4L, List.of());

        assertThat(prepared.initialMainThreadRequirements()).isEmpty();
        assertThat(prepared.requirements()).extracting(AsyncRequirementPlanner.Requirement::amount)
                .containsExactly(400L, 400L, 800L, 800L);
    }
}
