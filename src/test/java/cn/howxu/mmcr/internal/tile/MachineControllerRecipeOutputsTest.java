package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatOutput;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link MachineControllerBlockEntity#recipeOutputs()} falls back to the
 * controller's {@code CraftingRuntime.activeOutputs()} when no factory lane is active and
 * aggregates per-lane outputs when factory lanes are present.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerRecipeOutputsTest {

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void emptyControllerReturnsEmptyOutputs() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        assertThat(controller.recipeOutputs()).isEmpty();
    }

    @Test
    void factoryAggregationMergesIdenticalItems() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TestBootstrap.bindFactoryWithOutputs(controller, List.of(
                new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 4), 1F),
                new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 6), 1F)));
        List<MachineOutputAmount> merged = controller.recipeOutputs();
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).amount()).isEqualTo(10L);
        assertThat(((MachineOutput.ItemOutput) merged.get(0).output()).stack().getCount()).isEqualTo(4);
    }

    @Test
    void ordinaryMachineParallelismKeepsLongOutputAmount() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TestBootstrap.bindCraftingWithOutputs(controller,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.STONE, Integer.MAX_VALUE), 1F)), 2L);

        assertThat(controller.recipeOutputs()).singleElement()
                .satisfies(output -> assertThat(output.amount()).isEqualTo((long) Integer.MAX_VALUE * 2L));
    }

    @Test
    void ordinaryMachineWithoutParallelismKeepsTheStackAmount() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TestBootstrap.bindCraftingWithOutputs(controller,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 4), 1F)), 1L);

        assertThat(controller.recipeOutputs()).singleElement()
                .satisfies(output -> assertThat(output.amount()).isEqualTo(4L));
    }

    @Test
    void factoryThreadsAggregateLongOutputAmounts() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        MachineOutput output = new MachineOutput.ItemOutput(new ItemStack(Items.STONE, Integer.MAX_VALUE), 1F);
        TestBootstrap.bindFactoryWithOutputs(controller, List.of(output), 2L);
        TestBootstrap.bindFactoryWithOutputs(controller, List.of(output), 2L);

        assertThat(controller.recipeOutputs()).singleElement()
                .satisfies(value -> assertThat(value.amount()).isEqualTo((long) Integer.MAX_VALUE * 4L));
    }

    @Test
    void outputAmountSaturatesAtLongMaximumAfterParallelScaling() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TestBootstrap.bindCraftingWithOutputs(controller,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.STONE, Integer.MAX_VALUE), 1F)),
                Long.MAX_VALUE);

        assertThat(controller.recipeOutputs()).singleElement()
                .satisfies(output -> assertThat(output.amount()).isEqualTo(Long.MAX_VALUE));
    }

    @Test
    void runtimePresentationScalesOutputsEnergyAndHeatByParallelism() {
        CraftingRuntime runtime = TestBootstrap.newCraftingRuntime();
        TestBootstrap.configureCraftingWithPresentation(runtime,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 3), 1F),
                        new LoadedHeatOutput(2.5D)),
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 100L),
                        new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 50L)), 4L);

        ControllerRecipePresentation presentation = ControllerRecipePresentation.from(runtime);

        assertThat(presentation.outputs()).extracting(MachineOutputAmount::amount).containsExactly(12L);
        assertThat(presentation.energyInputPerTick()).isEqualTo(400L);
        assertThat(presentation.energyOutputPerTick()).isEqualTo(200L);
        assertThat(presentation.heatOutputPerTick()).isEqualTo(10D);
        assertThat(presentation.parallelism()).isEqualTo(4L);
    }

    @Test
    void factoryThreadPresentationContainsOnlyItsOwnOutputs() {
        ControllerRecipePresentation first = new ControllerRecipePresentation(List.of(
                new MachineOutputAmount(new MachineOutput.ItemOutput(new ItemStack(Items.IRON_INGOT, 1), 1F), 3L)),
                0L, 0L, 0D);
        ControllerRecipePresentation second = new ControllerRecipePresentation(List.of(
                new MachineOutputAmount(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 1), 1F), 5L)),
                0L, 0L, 0D);

        FactoryRuntime.ThreadSnapshot firstThread = new FactoryRuntime.ThreadSnapshot(0, "factory-0", true, false,
                true, "mmcr:first", 1, 20, 3L, null, first);
        FactoryRuntime.ThreadSnapshot secondThread = new FactoryRuntime.ThreadSnapshot(1, "factory-1", false, false,
                true, "mmcr:second", 1, 20, 5L, null, second);

        assertThat(firstThread.presentation().outputs()).containsExactly(first.outputs().getFirst());
        assertThat(firstThread.presentation().outputs()).doesNotContainAnyElementsOf(second.outputs());
        assertThat(secondThread.presentation().outputs()).containsExactly(second.outputs().getFirst());
    }
}
