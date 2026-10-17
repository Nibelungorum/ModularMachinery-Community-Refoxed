package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static cn.howxu.mmcr.publicapi.recipe.LightningTier.EXTREME_HIGH_VOLTAGE;
import static cn.howxu.mmcr.publicapi.recipe.LightningTier.HIGH_VOLTAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public lightning declarations use the canonical recipe and output contracts.
 * @author howxu <dev@howxu.cn>
 */
class PublicLightningRecipeTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void publicDeclarationsRoundTripBothTiersAndDirectionsWithoutDuplicateOutputs() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            LightningRecipeTypes.register();
            var draft = Recipes.recipe(id("round_trip")).recipePool(id("pool"));
            assertThat(draft.lightningInput(HIGH_VOLTAGE, 123L)).isSameAs(draft);
            assertThat(draft.lightningInput(EXTREME_HIGH_VOLTAGE, 456L)).isSameAs(draft);
            assertThat(draft.lightningOutput(HIGH_VOLTAGE, 78L, 0.25F)).isSameAs(draft);
            assertThat(draft.lightningOutput(EXTREME_HIGH_VOLTAGE, 90L)).isSameAs(draft);
            var recipe = MachineRecipeConverter.toRecipe(RecipeAdapters.unwrap(draft.build()),
                    new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
            var codec = MachineRecipe.CODEC.codec();
            var decoded = codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, recipe).getOrThrow())
                    .getOrThrow();

            assertThat(decoded.runtimeRequirements()).containsExactly(
                    LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 123L),
                    LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 456L),
                    LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 78L, 0.25F),
                    LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 90L, 1F));
            assertThat(decoded.machineOutputs()).containsExactly(
                    new LightningOutput(LightningTier.HIGH_VOLTAGE, 78L, 0.25F),
                    new LightningOutput(LightningTier.EXTREME_HIGH_VOLTAGE, 90L, 1F));
        }
    }

    @Test
    void publicDeclarationsPreserveCanonicalAmountLimitsForEachTierAndDirection() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            LightningRecipeTypes.register();
            var spec = Recipes.recipe(id("limits")).recipePool(id("pool"))
                    .lightningInput(HIGH_VOLTAGE, Long.MAX_VALUE)
                    .lightningInput(EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE)
                    .lightningOutput(HIGH_VOLTAGE, Long.MAX_VALUE)
                    .lightningOutput(EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE, 0.5F)
                    .build();

            assertThat(RecipeAdapters.unwrap(spec).requirements()).containsExactly(
                    LightningRequirement.input(LightningTier.HIGH_VOLTAGE, Integer.MAX_VALUE),
                    LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, Integer.MAX_VALUE),
                    LightningRequirement.output(LightningTier.HIGH_VOLTAGE, Integer.MAX_VALUE, 1F),
                    LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, Integer.MAX_VALUE, 0.5F));
        }
    }

    @Test
    void publicDeclarationsRejectNonPositiveAmountsAndMissingTiers() {
        var draft = Recipes.recipe(id("invalid")).recipePool(id("pool"));
        for (var tier : List.of(HIGH_VOLTAGE, EXTREME_HIGH_VOLTAGE)) {
            for (long amount : new long[] {0L, -1L}) {
                assertThatThrownBy(() -> draft.lightningInput(tier, amount)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> draft.lightningOutput(tier, amount)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> draft.lightningOutput(tier, amount, 0.5F)).isInstanceOf(IllegalArgumentException.class);
            }
        }
        assertThatThrownBy(() -> draft.lightningInput(null, 1L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> draft.lightningOutput(null, 1L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> draft.lightningOutput(null, 1L, 0.5F)).isInstanceOf(NullPointerException.class);
        assertThat(draft.build().requirements()).isEmpty();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("public_lightning_test", path);
    }
}
