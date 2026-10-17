package cn.howxu.mmcr.compat.ae2lt.client;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.test.TestBootstrap;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;
import static org.assertj.core.api.Assertions.assertThat;

/** Exact amounts, native alternatives and capacity checks for the 1.21.1 JEI encoder.
 * @author howxu <dev@howxu.cn> */
class LightningPatternTransferTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); LightningRecipeTypes.register(); }

    @Test void replacesUnitKeyIngredientsWithExactCombinedRecipeAmounts() {
        var recipe = MachineRecipe.fromCanonical(MMCR.id("lightning_transfer_test"), MMCR.id("test_cube"), 10,
                List.of(LightningRequirement.input(HIGH_VOLTAGE, 7L), LightningRequirement.input(HIGH_VOLTAGE, 11L),
                        LightningRequirement.input(EXTREME_HIGH_VOLTAGE, 13L), LightningRequirement.output(HIGH_VOLTAGE, 17L, 0.5F)),
                List.of(), List.of(), 0, 1, false, false, false, Set.of());
        var iron = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3L);
        var copper = new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 3L);
        var gold = new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 5L);
        var transfer = LightningPatternTransfer.prepare(MachineRecipeDisplay.from(recipe),
                List.of(List.of(iron, copper), List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1L))),
                List.of(gold, new GenericStack(LightningKey.HIGH_VOLTAGE, 1L)));
        assertThat(transfer.inputs()).containsExactly(List.of(iron, copper), List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 18L)),
                List.of(new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 13L)));
        assertThat(transfer.outputs()).containsExactly(gold, new GenericStack(LightningKey.HIGH_VOLTAGE, 17L));
        assertThat(transfer.fits(3, 2)).isTrue();
        assertThat(transfer.fits(2, 2)).isFalse();
        assertThat(transfer.fits(3, 1)).isFalse();
    }

    @Test void ordinaryRecipesKeepNativeInputsAndOutputsUntouched() {
        var recipe = MachineRecipe.fromCanonical(MMCR.id("ordinary_transfer_test"), MMCR.id("test_cube"), 10,
                List.of(), List.of(), List.of(), 0, 1, false, false, false, Set.of());
        List<List<GenericStack>> inputs = List.of(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3L)));
        List<GenericStack> outputs = List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 5L));
        var transfer = LightningPatternTransfer.prepare(MachineRecipeDisplay.from(recipe), inputs, outputs);
        assertThat(transfer.inputs()).isSameAs(inputs);
        assertThat(transfer.outputs()).isSameAs(outputs);
    }
}
