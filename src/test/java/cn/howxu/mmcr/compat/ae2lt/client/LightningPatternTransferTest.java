package cn.howxu.mmcr.compat.ae2lt.client;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.test.TestBootstrap;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.jei.PatternTransfer;
import cn.howxu.mmcr.compat.ae2lt.LightningBridgeBootstrap;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import org.junit.jupiter.api.AfterAll;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class LightningPatternTransferTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        LightningRecipeTypes.register();
        LightningBridgeBootstrap.installForTesting(new LoadedLightningBridge());
    }

    @AfterAll
    static void restoreBridge() { LightningBridgeBootstrap.resetForTesting(); }

    @Test
    void encodesBothTiersAndDirectionsWithoutLosingNativeIngredients() {
        var iron = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4L);
        var copper = new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 4L);
        var water = new GenericStack(AEFluidKey.of(Fluids.WATER), 750L);
        var gold = new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2L);
        var display = display(List.of(
                LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 1000L),
                LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 100L),
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 200L, 1F),
                LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 20L, 0.5F)));

        var transfer = PatternTransfer.prepare(display, List.of(List.of(iron, copper), List.of(water)),
                List.of(gold));

        assertThat(transfer.inputs()).containsExactly(List.of(iron, copper), List.of(water),
                List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1000L)),
                List.of(new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 100L)));
        assertThat(transfer.outputs()).containsExactly(gold,
                new GenericStack(LightningKey.HIGH_VOLTAGE, 200L),
                new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 20L));
    }

    @Test
    void replacesUnitLightningConversionsInsteadOfDuplicatingThem() {
        var unit = new GenericStack(LightningKey.HIGH_VOLTAGE, 1L);
        var display = display(List.of(LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 1000L),
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 200L, 1F)));

        var transfer = PatternTransfer.prepare(display, List.of(List.of(unit)), List.of(unit));

        assertThat(transfer.inputs()).containsExactly(List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1000L)));
        assertThat(transfer.outputs()).containsExactly(new GenericStack(LightningKey.HIGH_VOLTAGE, 200L));
    }

    @Test
    void mergesRepeatedDeclarationsWithinEachDirection() {
        var display = display(List.of(LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 30L),
                LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 70L),
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 4L, 1F),
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 6L, 1F)));

        var transfer = PatternTransfer.prepare(display, List.of(), List.of());

        assertThat(transfer.inputs()).containsExactly(List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 100L)));
        assertThat(transfer.outputs()).containsExactly(new GenericStack(LightningKey.HIGH_VOLTAGE, 10L));
    }

    @Test
    void preservesMaximumDeclaredAmountsWithoutStackSizeClamping() {
        var display = display(List.of(LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 2_147_483_647L),
                LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 2_147_483_647L, 1F)));

        var transfer = PatternTransfer.prepare(display, List.of(), List.of());

        assertThat(transfer.inputs()).containsExactly(
                List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 2_147_483_647L)));
        assertThat(transfer.outputs()).containsExactly(
                new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 2_147_483_647L));
    }

    @Test
    void outputOnlyLightningDoesNotBecomeAnInput() {
        var display = display(List.of(LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 15L, 1F)));

        var transfer = PatternTransfer.prepare(display, List.of(), List.of());

        assertThat(transfer.inputs()).isEmpty();
        assertThat(transfer.outputs()).containsExactly(new GenericStack(LightningKey.HIGH_VOLTAGE, 15L));
    }

    @Test
    void recipesWithoutLightningKeepNativeTransferUntouched() {
        List<List<GenericStack>> inputs = List.of(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4L)));
        List<GenericStack> outputs = List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2L));

        var transfer = PatternTransfer.prepare(display(List.of()), inputs, outputs);

        assertThat(transfer.inputs()).isSameAs(inputs);
        assertThat(transfer.outputs()).isSameAs(outputs);
    }

    @Test
    void refusesInputOverflowInsteadOfDroppingAppendedLightning() {
        var inputs = List.of(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L)));
        var transfer = PatternTransfer.prepare(display(List.of(
                LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 1000L))), inputs, List.of());

        assertThat(transfer.fits(1, 1)).isFalse();
        assertThat(transfer.fits(2, 1)).isTrue();
    }

    @Test
    void refusesOutputOverflowInsteadOfDroppingAppendedLightning() {
        var outputs = List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L));
        var transfer = PatternTransfer.prepare(display(List.of(
                LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 20L, 1F))), List.of(), outputs);

        assertThat(transfer.fits(1, 1)).isFalse();
        assertThat(transfer.fits(1, 2)).isTrue();
    }

    private static MachineRecipeDisplay display(List<MachineRequirement> requirements) {
        return MachineRecipeDisplay.from(MachineRecipe.fromCanonical(MMCR.id("lightning_transfer"),
                MMCR.id("lightning_pool"), 20, requirements, List.of(), List.of(), 0, 1,
                false, false, false, Set.of()));
    }
}
