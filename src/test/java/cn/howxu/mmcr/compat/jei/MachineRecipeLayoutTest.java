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
    void layoutPlansFluidThenItemInputsAcrossFourColumns() {
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
                        tuple(MachineRecipeLayout.Kind.FLUID, 0, 8, 18), tuple(MachineRecipeLayout.Kind.FLUID, 1, 26, 18),
                        tuple(MachineRecipeLayout.Kind.ITEM, 0, 44, 18), tuple(MachineRecipeLayout.Kind.ITEM, 1, 62, 18),
                        tuple(MachineRecipeLayout.Kind.ITEM, 2, 8, 36));
        assertThat(layout.outputs().slots()).allSatisfy(slot -> assertThat(slot.x()).isGreaterThan(100));
    }

    @Test
    void inputOverflowUsesLastSlotAsEllipsis() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_layout_wrap"),
                MMCR.id("large_machine"),
                200,
                java.util.stream.IntStream.range(0, 33)
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

        assertThat(layout.inputs().slots()).hasSize(32);
        assertThat(layout.inputs().slots().subList(0, 31)).allSatisfy(slot -> assertThat(slot.entry()).isNotNull());
        assertThat(layout.inputs().slots().getLast())
                .extracting(MachineRecipeLayout.SlotPlan::entry, MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(null, 62, 144);
        assertThat(layout.inputs().hiddenEntries())
                .contains(new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.ITEM, 31));
        assertThat(layout.hasInputOverflow()).isTrue();
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
                java.util.stream.IntStream.range(0, 33)
                        .mapToObj(index -> new FluidStack(Fluids.WATER.builtInRegistryHolder(), 125))
                        .toList()
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe));

        assertThat(layout.inputs().slots())
                .extracting(MachineRecipeLayout.SlotPlan::entry, MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(tuple(new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.ITEM, 0), 8, 18));
        assertThat(layout.hasInputOverflow()).isFalse();
        assertThat(layout.outputs().slots()).hasSize(32);
        assertThat(layout.outputs().slots().getLast().entry()).isNull();
        assertThat(layout.outputs().hiddenEntries())
                .contains(new MachineRecipeLayout.EntryPlan(MachineRecipeLayout.Kind.FLUID, 31));
        assertThat(layout.hasOutputOverflow()).isTrue();
    }
}
