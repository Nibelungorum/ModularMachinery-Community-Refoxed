package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** @author howxu <dev@howxu.cn> */
class LightningRecipeContractTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }
    @Test void codecKeepsTiersAndRejectsProbabilisticInput() {
        for (LightningTier tier : LightningTier.values()) {
            var codec = LightningRecipeTypes.requirementType(tier).codec().codec();
            var output = new LightningRequirement(tier, RecipeModifier.IOType.OUTPUT, 23L, 0.5F, List.of("lane"));
            var encoded = codec.encodeStart(JsonOps.INSTANCE, output).getOrThrow();
            assertThat(codec.parse(JsonOps.INSTANCE, encoded).getOrThrow()).isEqualTo(output);
            var invalid = JsonParser.parseString("{\"type\":\"" + tier.requirementId()
                    + "\",\"io\":\"input\",\"amount\":3,\"chance\":0.5}");
            assertThat(codec.parse(JsonOps.INSTANCE, invalid).error()).isPresent();
            var negative = JsonParser.parseString("{\"type\":\"" + tier.requirementId() + "\",\"amount\":-1}");
            assertThat(codec.parse(JsonOps.INSTANCE, negative).error()).isPresent();
        }
    }
    @Test void combinedAmountIsCheckedPerTierAndDirection() {
        var high = LightningRequirement.input(LightningTier.HIGH_VOLTAGE, Integer.MAX_VALUE);
        var extreme = LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, Integer.MAX_VALUE);
        LightningRecipeTypes.validateRecipeAmounts(List.of(high, extreme,
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, Integer.MAX_VALUE, 1F)));
        assertThatThrownBy(() -> LightningRecipeTypes.validateRecipeAmounts(List.of(high,
                LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 1L)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void paidLightningInputsCanBeRestoredButOutputsCannotBeMarkedConsumed() {
        List<MachineRequirement> requirements = List.of(LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 2L),
                LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 3L),
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 5L, 1F));
        assertThat(new ActiveMachineRecipe.InputConsumptionPlan(List.of(1, 1, 0)).isValidFor(requirements)).isTrue();
        assertThat(new ActiveMachineRecipe.InputConsumptionPlan(List.of(1, 1, 1)).isValidFor(requirements)).isFalse();
    }
}
