package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class MachineRecipeLayoutTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void layoutPlansFluidThenItemInputsAcrossThreeColumns() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_layout"),
                MMCR.id("blast_furnace"),
                100,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.GOLD_INGOT), 1),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.LAVA), 250),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.COPPER_INGOT), 1)
                ),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                List.of(),
                0,
                1,
                true,
                List.of(new FluidStack(Fluids.LAVA.builtInRegistryHolder(), 125))
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe));

        assertThat(layout.inputs().slots())
                .extracting(slot -> slot.entry().kind(), slot -> slot.entry().index(),
                        MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(
                        tuple(MachineRecipeLayout.Kind.FLUID, 0, 8, 8), tuple(MachineRecipeLayout.Kind.FLUID, 1, 26, 8),
                        tuple(MachineRecipeLayout.Kind.ITEM, 0, 44, 8), tuple(MachineRecipeLayout.Kind.ITEM, 1, 8, 26),
                        tuple(MachineRecipeLayout.Kind.ITEM, 2, 26, 26));
        assertThat(layout.width()).isEqualTo(150);
        assertThat(layout.height()).isEqualTo(150);
        assertThat(layout.durationTextX()).isEqualTo(8);
        assertThat(layout.durationTextY()).isEqualTo(48);
        assertThat(layout.outputs().slots()).allSatisfy(slot -> assertThat(slot.x()).isGreaterThan(90));
    }

    @Test
    void inputOverflowUsesLastSlotAsEllipsis() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_layout_wrap"),
                MMCR.id("large_machine"),
                200,
                java.util.stream.IntStream.range(0, 22)
                        .<MachineIngredient>mapToObj(index -> new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1))
                        .toList(),
                java.util.List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                java.util.List.of(),
                0,
                1,
                true,
                java.util.List.of()
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe));

        assertThat(layout.inputs().slots()).hasSize(17);
        assertThat(layout.inputs().slots()).allSatisfy(slot -> assertThat(slot.entry()).isNotNull());
        assertThat(layout.inputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(44, 98));
        assertThat(layout.inputs().hiddenEntries())
                .contains(new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.ITEM, 17));
        assertThat(layout.hasInputOverflow()).isTrue();
    }

    @Test
    void inputOverflowSlotIsOnlyVisualSoJeiFallsBackToCategoryTooltip() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_layout_input_overflow_tooltip"),
                MMCR.id("large_machine"),
                200,
                java.util.stream.IntStream.range(0, 25)
                        .<MachineIngredient>mapToObj(index -> new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1))
                        .toList(),
                java.util.List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                java.util.List.of(),
                0,
                1,
                true,
                java.util.List.of()
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe));

        assertThat(layout.inputs().slots()).hasSize(17);
        assertThat(layout.inputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(44, 98));
        assertThat(layout.inputs().hiddenEntries()).hasSize(8);
    }

    @Test
    void outputOverflowDoesNotChangeInputPlan() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_layout_output_overflow"),
                MMCR.id("large_machine"),
                200,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                java.util.List.of(),
                java.util.List.of(),
                0,
                1,
                true,
                java.util.stream.IntStream.range(0, 22)
                        .mapToObj(index -> new FluidStack(Fluids.WATER.builtInRegistryHolder(), 125))
                        .toList()
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe));

        assertThat(layout.inputs().slots())
                .extracting(MachineRecipeLayout.SlotPlan::entry, MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(tuple(new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.ITEM, 0), 8, 8));
        assertThat(layout.hasInputOverflow()).isFalse();
        assertThat(layout.outputs().slots()).hasSize(17);
        assertThat(layout.outputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(127, 98));
        assertThat(layout.outputs().hiddenEntries())
                .contains(new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.FLUID, 17));
        assertThat(layout.hasOutputOverflow()).isTrue();
        assertThat(layout.outputs().slots().subList(15, 17))
                .extracting(MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(tuple(91, 98), tuple(109, 98));
        assertThat(layout.durationTextY()).isEqualTo(120);
    }

    @Test
    void outputsAreRightAlignedWithinEachRow() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_layout_right_aligned_outputs"),
                MMCR.id("blast_furnace"),
                100,
                List.of(),
                List.of(
                        new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1),
                        new ItemStack(Holder.direct(Items.GOLD_NUGGET, DataComponentMap.EMPTY), 1),
                        new ItemStack(Holder.direct(Items.COPPER_NUGGET, DataComponentMap.EMPTY), 1),
                        new ItemStack(Holder.direct(Items.REDSTONE, DataComponentMap.EMPTY), 1)
                ),
                List.of(),
                0,
                1,
                true,
                List.of()
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe));

        assertThat(layout.outputs().slots())
                .extracting(MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(tuple(91, 8), tuple(109, 8), tuple(127, 8), tuple(127, 26));
    }

    @Test
    void recipeArrowStartsTwoPixelsAfterTheThirdInputSlotAndMovesWithSlots() {
        assertThat(MachineRecipeCategory.RECIPE_ARROW_X).isEqualTo(64);
        assertThat(MachineRecipeCategory.RECIPE_ARROW_Y).isEqualTo(8);
    }
}
