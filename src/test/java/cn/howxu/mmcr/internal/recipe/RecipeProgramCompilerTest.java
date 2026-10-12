package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.ExecutionIdentity;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.RestartRule;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.Stage;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.VersionStamp;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies collection ownership and compatibility boundaries of compiled recipe rules.
 * Native mutable-component capture is covered by RunningRecipeGameTest with the live registry.
 *
 * @author howxu <dev@howxu.cn>
 */
class RecipeProgramCompilerTest {
    private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("mmcr_test", "compiled_energy");
    private static final VersionStamp VERSIONS = new VersionStamp(1L, 2L, 3L, 4L, 5L, 6L);

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void stage_detaches_lists_and_compiler_preserves_original_rules() {
        var descriptions = new ArrayList<>(List.of(energy(2)));
        var fallback = new ArrayList<>(List.of(0));
        var policies = new ArrayList<>(List.of(OutputPolicy.REQUIRE_FULL, OutputPolicy.REQUIRE_FULL,
                OutputPolicy.ALLOW_PARTIAL));
        Stage stage = new Stage(descriptions, fallback, policies);
        var program = new RecipeProgramCompiler().compile(RECIPE, 20, 3L, VERSIONS,
                Stage.empty(), stage, Stage.empty(), false);
        descriptions.clear();
        fallback.clear();
        policies.clear();

        assertThat(program.tick().requirements()).containsExactly(energy(2));
        assertThat(program.tick().fallbackIndices()).containsExactly(0);
        assertThat(program.tick().outputPolicies()).containsExactly(OutputPolicy.REQUIRE_FULL,
                OutputPolicy.REQUIRE_FULL, OutputPolicy.ALLOW_PARTIAL);
        assertThat(program.requiresMainThreadPreparation()).isTrue();
        assertThatThrownBy(() -> program.tick().requirements().clear()).isInstanceOf(UnsupportedOperationException.class);
        var restart = new RestartRule(RECIPE, VERSIONS, 9L, program.start(), program.tick(), program.finish());
        assertThat(restart.tick()).isEqualTo(stage);
        assertThat(program.versions()).isEqualTo(restart.versions());
        assertThat(new ExecutionIdentity(1L, 2L, "base", 3L))
                .isNotEqualTo(new ExecutionIdentity(1L, 2L, "extra", 3L))
                .isNotEqualTo(new ExecutionIdentity(1L, 3L, "base", 3L));
    }

    @Test
    void rejects_overlapping_fallback_and_description_indices() {
        assertThatThrownBy(() -> new Stage(List.of(energy(2)), List.of(2), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Stage(List.of(energy(2), energy(2)), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Stage(List.of(), List.of(1, 1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Stage(List.of(), List.of(-1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_invalid_execution_size_and_keeps_explicit_preparation_flag() {
        RecipeProgramCompiler compiler = new RecipeProgramCompiler();
        assertThatThrownBy(() -> compiler.compile(RECIPE, 0, 1L, VERSIONS,
                Stage.empty(), Stage.empty(), Stage.empty(), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compiler.compile(RECIPE, 1, 0L, VERSIONS,
                Stage.empty(), Stage.empty(), Stage.empty(), false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(compiler.compile(RECIPE, 1, 1L, VERSIONS,
                Stage.empty(), Stage.empty(), Stage.empty(), false).requiresMainThreadPreparation()).isFalse();
        assertThat(compiler.compile(RECIPE, 1, 1L, VERSIONS,
                Stage.empty(), Stage.empty(), Stage.empty(), true).requiresMainThreadPreparation()).isTrue();
    }

    @Test
    void probability_output_is_fallback_and_energy_rule_retains_no_capability_snapshot() {
        Stage stage = CraftingContext.prepareCompiledStage(List.of(
                new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, FluidStack.EMPTY, 0.5F, List.of()),
                new EnergyRequirement(7L)), 3L, List.of(), Map.of(0, OutputPolicy.ALLOW_PARTIAL));

        assertThat(stage.requiresCompatibility()).isTrue();
        assertThat(stage.fallbackIndices()).containsExactly(0);
        assertThat(stage.requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement.index()).isEqualTo(1);
            assertThat(requirement.amount()).isEqualTo(21L);
            assertThat(requirement.requests()).containsExactly(
                    new AsyncCapabilityRequest.Scalar(EnergyRequirement.TYPE.id(), 3L, 21L, false));
        });
        assertThat(stage.outputPolicies()).containsExactly(OutputPolicy.ALLOW_PARTIAL, OutputPolicy.REQUIRE_FULL);
    }

    private static AsyncRequirementPlanner.Requirement energy(int index) {
        return new AsyncRequirementPlanner.Requirement(index, 21L, IOType.INPUT,
                List.of(new AsyncCapabilityRequest.Scalar(EnergyRequirement.TYPE.id(), 3L, 21L, false)));
    }
}
