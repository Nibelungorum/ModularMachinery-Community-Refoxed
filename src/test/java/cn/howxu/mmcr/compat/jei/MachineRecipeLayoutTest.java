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

class MachineRecipeLayoutTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void layoutPlacesInputsLeftOutputsRightAndInfoBelow() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_layout"),
                MMCR.id("blast_furnace"),
                100,
                List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1),
                        new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250),
                        new MachineIngredient.EnergyIngredient(20)
                ),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                List.of(),
                0,
                1,
                true,
                List.of(new FluidStack(Fluids.LAVA.builtInRegistryHolder(), 125))
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe));

        assertThat(layout.width()).isEqualTo(150);
        assertThat(layout.height()).isEqualTo(78);
        assertThat(layout.itemInputs()).extracting(MachineRecipeLayout.SlotPlan::x).containsExactly(8);
        assertThat(layout.itemOutputs()).extracting(MachineRecipeLayout.SlotPlan::x).containsExactly(124);
        assertThat(layout.fluidInputs()).extracting(MachineRecipeLayout.SlotPlan::x).containsExactly(82);
        assertThat(layout.fluidOutputs()).extracting(MachineRecipeLayout.SlotPlan::x).containsExactly(102);
        assertThat(layout.energyInputs()).extracting(MachineRecipeLayout.SlotPlan::x).containsExactly(56);
        assertThat(layout.durationTextX()).isEqualTo(8);
        assertThat(layout.durationTextY()).isEqualTo(60);
    }

    @Test
    void itemInputsWrapHorizontallyWhenExceedingRowCapacity() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_layout_wrap"),
                MMCR.id("large_machine"),
                200,
                java.util.List.of(
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.GOLD_INGOT), 1),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.COPPER_INGOT), 1),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.DIAMOND), 1),
                        new MachineIngredient.ItemIngredient(Ingredient.of(Items.EMERALD), 1)
                ),
                java.util.List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)),
                java.util.List.of(),
                0,
                1,
                true,
                java.util.List.of()
        );

        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(MachineRecipeDisplay.from(recipe));

        assertThat(layout.itemInputs())
                .extracting(MachineRecipeLayout.SlotPlan::x, MachineRecipeLayout.SlotPlan::y)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(8, 18),
                        org.assertj.core.groups.Tuple.tuple(26, 18),
                        org.assertj.core.groups.Tuple.tuple(44, 18),
                        org.assertj.core.groups.Tuple.tuple(62, 18),
                        org.assertj.core.groups.Tuple.tuple(8, 36));
    }
}
