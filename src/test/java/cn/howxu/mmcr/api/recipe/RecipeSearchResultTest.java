package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeSearchResultTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void successResultsRequireActiveRecipeAndContext() {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        MachineRecipe recipe = new MachineRecipe(
                Identifier.fromNamespaceAndPath("mmcr", "recipe"),
                machineId,
                20,
                List.of(),
                List.of());
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);

        assertThatThrownBy(() -> RecipeSearchResult.success(active, null, machineId, 7))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void failedResultsCarryFailureWithoutActiveContext() {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        RequirementFailure failure = new RequirementFailure(0, RequirementFailure.Kind.MISSING_INPUT, 10, 4);

        RecipeSearchResult result = RecipeSearchResult.failure(
                machineId,
                3,
                RecipeCraftingContext.FAILURE_MISSING_INPUT,
                failure,
                0.5F);

        assertThat(result.success()).isFalse();
        assertThat(result.machineId()).isEqualTo(machineId);
        assertThat(result.structureVersion()).isEqualTo(3);
        assertThat(result.failureUnloc()).isEqualTo(RecipeCraftingContext.FAILURE_MISSING_INPUT);
        assertThat(result.requirementFailure()).isEqualTo(failure);
        assertThat(result.validity()).isEqualTo(0.5F);
        assertThat(result.activeRecipe()).isNull();
        assertThat(result.context()).isNull();
    }
}
