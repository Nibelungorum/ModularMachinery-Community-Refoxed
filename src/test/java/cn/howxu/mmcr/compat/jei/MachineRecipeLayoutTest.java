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

import java.util.Set;

import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class MachineRecipeLayoutTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.GOLD_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.COPPER_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
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

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.inputs().slots())
                .extracting(slot -> slot.entry().kind(), slot -> slot.entry().index(),
                        MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(
                        tuple(MachineRecipeLayout.Kind.FLUID, 0, 12, 8), tuple(MachineRecipeLayout.Kind.FLUID, 1, 30, 8),
                        tuple(MachineRecipeLayout.Kind.ITEM, 0, 48, 8), tuple(MachineRecipeLayout.Kind.ITEM, 1, 12, 26),
                        tuple(MachineRecipeLayout.Kind.ITEM, 2, 30, 26));
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
                IntStream.range(0, 22)
                        .<MachineIngredient>mapToObj(index -> new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1))
                        .toList(),
                java.util.List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                java.util.List.of(),
                0,
                1,
                true,
                java.util.List.of()
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.inputs().slots()).hasSize(14);
        assertThat(layout.inputs().slots()).allSatisfy(slot -> assertThat(slot.entry()).isNotNull());
        assertThat(layout.inputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(48, 80));
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
                IntStream.range(0, 25)
                        .<MachineIngredient>mapToObj(index -> new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1))
                        .toList(),
                java.util.List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                java.util.List.of(),
                0,
                1,
                true,
                java.util.List.of()
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.inputs().slots()).hasSize(14);
        assertThat(layout.inputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(48, 80));
        assertThat(layout.inputs().hiddenEntries()).hasSize(11);
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
                IntStream.range(0, 22)
                        .mapToObj(index -> new FluidStack(Fluids.WATER.builtInRegistryHolder(), 125))
                        .toList()
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.inputs().slots())
                .extracting(MachineRecipeLayout.SlotPlan::entry, MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(tuple(new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.ITEM, 0), 12, 8));
        assertThat(layout.hasInputOverflow()).isFalse();
        assertThat(layout.outputs().slots()).hasSize(14);
        assertThat(layout.outputs().overflowSlot()).isEqualTo(new MachineRecipeLayout.OverflowSlotPlan(138, 80));
        assertThat(layout.outputs().hiddenEntries())
                .contains(new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.FLUID, 14));
        assertThat(layout.hasOutputOverflow()).isTrue();
        assertThat(layout.outputs().slots().subList(12, 14))
                .extracting(MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(tuple(102, 80), tuple(120, 80));
        assertThat(layout.durationTextY()).isEqualTo(102);
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

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.outputs().slots())
                .extracting(MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(tuple(102, 8), tuple(120, 8), tuple(138, 8), tuple(138, 26));
    }

    @Test
    void recipeArrowStartsTwoPixelsAfterTheThirdInputSlotAndMovesWithSlots() {
        assertThat(MachineRecipeCategory.RECIPE_ARROW_X).isEqualTo(72);
        assertThat(MachineRecipeCategory.RECIPE_ARROW_Y).isEqualTo(8);
    }

    @Test
    void emptyInputAndOutputRegionsReserveTextSpaceWithoutSlots() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_layout_empty"), MMCR.id("blast_furnace"), 100,
                List.of(), List.of(), List.of(), 0, 1, true, List.of());

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.inputs().slots()).isEmpty();
        assertThat(layout.outputs().slots()).isEmpty();
        assertThat(layout.durationTextY()).isEqualTo(30);
    }

    @Test
    void levelRequirementRowsFollowTheEnergyRows() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_level_layout"), MMCR.id("blast_furnace"), 100,
                List.of(new MachineIngredient.EnergyIngredient(40)), List.of(), List.of(), 0, 1,
                false, List.of());
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.levelRequirementY(MachineRecipeDisplay.from(recipe), 0))
                .isEqualTo(layout.durationTextY() + 20);
    }

    @Test
    void metadataRowsReserveHostRequirementBeforeDurationAndStayInsideRecipeHeight() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_host_layout"), MMCR.id("hosted_module"), 100,
                IntStream.range(0, 22)
                        .<MachineIngredient>mapToObj(index -> new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1))
                        .toList(),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                List.of(), 0, 1, false, List.of(), List.of(), false, List.of(), Set.of(MMCR.id("host_a")));

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe), 4);

        assertThat(layout.hostRequirementTextY()).isEqualTo(102);
        assertThat(layout.durationTextY()).isEqualTo(112);
        assertThat(layout.lastMetadataTextY(MachineRecipeDisplay.from(recipe))).isLessThan(layout.height());
    }

}
