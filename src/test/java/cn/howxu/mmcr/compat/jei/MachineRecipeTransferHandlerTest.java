package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.test.TestBootstrap;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineRecipeTransferHandlerTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.GOLD_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void transferViewRestoresRealCountForAllItemVariants() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_transfer_count"),
                MMCR.id("blast_furnace"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 8)),
                List.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        assertThat(display.entries()).singleElement().satisfies(entry -> assertThat(entry.transferable()).isTrue());

        IRecipeSlotView placeholder = itemSlot(
                new ItemStack(Items.IRON_INGOT, 1),
                new ItemStack(Items.GOLD_INGOT, 1));
        IRecipeSlotsView source = () -> List.of(placeholder);

        List<IRecipeSlotView> transformed = MachineRecipeTransferHandler
                .withActualInputCounts(source, display);

        assertThat(transformed).singleElement().satisfies(slot ->
                assertThat(slot.getItemStacks().map(ItemStack::getCount).toList())
                        .containsExactly(8, 8));
    }

    @Test
    void transferViewSkipsFluidInputSlotsWhenRestoringItemCounts() {
        MachineRecipe recipe = new MachineRecipe(
                MMCR.id("jei_transfer_mixed_inputs"),
                MMCR.id("blast_furnace"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 8)),
                List.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        IRecipeSlotView fluidSlot = ingredientSlot(new TypedFluid(new FluidStack(Fluids.WATER, 1_000)));
        IRecipeSlotView itemSlot = itemSlot(new ItemStack(Items.IRON_INGOT, 1));
        IRecipeSlotsView source = () -> List.of(fluidSlot, itemSlot);

        List<IRecipeSlotView> transformed = MachineRecipeTransferHandler
                .withActualInputCounts(source, display);

        assertThat(transformed.get(0).getIngredients(NeoForgeTypes.FLUID_STACK)
                .map(FluidStack::getAmount).toList())
                .containsExactly(1_000);
        assertThat(transformed.get(1).getItemStacks().map(ItemStack::getCount).toList())
                .containsExactly(8);
        assertThatThrownBy(() -> transformed.get(1).getAllIngredientsList().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static IRecipeSlotView itemSlot(ItemStack... stacks) {
        List<ITypedIngredient<?>> ingredients = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) {
            ingredients.add(new TypedItem(stack));
        }
        return ingredientSlot(ingredients.toArray(ITypedIngredient<?>[]::new));
    }

    private static IRecipeSlotView ingredientSlot(ITypedIngredient<?>... values) {
        List<ITypedIngredient<?>> ingredients = new ArrayList<>(List.of(values));
        return new IRecipeSlotView() {
            @Override
            public Stream<ITypedIngredient<?>> getAllIngredients() {
                return ingredients.stream();
            }

            @Override
            public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
                return ingredients;
            }

            @Override
            public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
                return ingredients.stream().findFirst();
            }

            @Override
            public RecipeIngredientRole getRole() {
                return RecipeIngredientRole.INPUT;
            }

            @Override
            public void drawHighlight(GuiGraphicsExtractor guiGraphics, int color) {
            }

            @Override
            public Optional<String> getSlotName() {
                return Optional.empty();
            }
        };
    }

    private record TypedItem(ItemStack stack) implements ITypedIngredient<ItemStack> {
        @Override
        public IIngredientType<ItemStack> getType() {
            return VanillaTypes.ITEM_STACK;
        }

        @Override
        public ItemStack getIngredient() {
            return stack;
        }
    }

    private record TypedFluid(FluidStack stack) implements ITypedIngredient<FluidStack> {
        @Override
        public IIngredientType<FluidStack> getType() {
            return NeoForgeTypes.FLUID_STACK;
        }

        @Override
        public FluidStack getIngredient() {
            return stack;
        }
    }
}
