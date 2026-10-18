package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exact external promises cannot collapse custom resource identities or overflow long totals.
 * @author howxu <dev@howxu.cn>
 */
class PatternExactOutputPromiseTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void chemical_id_and_lightning_tier_are_part_of_the_promised_resource() {
        var oxygen = new LoadedChemicalOutput(MMCR.id("oxygen"), 3L, 1F);
        var hydrogen = new LoadedChemicalOutput(MMCR.id("hydrogen"), 3L, 1F);
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(oxygen), List.of(hydrogen), 2L)).isFalse();
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(oxygen), List.of(
                new LoadedChemicalOutput(oxygen.id(), 1L, 1F), new LoadedChemicalOutput(oxygen.id(), 2L, 1F)), 2L)).isTrue();
        assertThat(CraftingRuntime.samePromisedOutputs(
                List.of(new LightningOutput(LightningTier.HIGH_VOLTAGE, 1L, 1F)),
                List.of(new LightningOutput(LightningTier.EXTREME_HIGH_VOLTAGE, 1L, 1F)), 1L)).isFalse();
    }

    @Test
    void components_are_compared_but_output_partitioning_is_not_resource_identity() {
        ItemStack named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("promised"));
        var expected = new MachineOutput.ItemOutput(named, 1F);
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(expected),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.IRON_INGOT), 1F)), 1L)).isFalse();
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(expected, expected),
                List.of(new MachineOutput.ItemOutput(named.copyWithCount(2), 1F)), 10L)).isTrue();
    }

    @Test
    void unchanged_probabilistic_outputs_allow_batches_but_preserve_independent_rolls() {
        var output = new MachineOutput.ItemOutput(new ItemStack(Items.IRON_INGOT), 0.5F);
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(output), List.of(output), 1L)).isTrue();
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(output), List.of(output), 2L)).isTrue();
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(output), List.of(output.withChance(1F)), 1L)).isFalse();
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(output, output),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.IRON_INGOT, 2), 0.5F)), 1L)).isFalse();
    }

    @Test
    void chemical_scaling_and_same_resource_addition_both_use_checked_long_arithmetic() {
        var huge = new LoadedChemicalOutput(MMCR.id("chemical"), Long.MAX_VALUE, 1F);
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(huge), List.of(huge), 2L)).isFalse();
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(huge, huge), List.of(huge, huge), 1L)).isFalse();
        assertThat(CraftingRuntime.samePromisedOutputs(List.of(huge), List.of(huge), 1L)).isTrue();
    }
}
