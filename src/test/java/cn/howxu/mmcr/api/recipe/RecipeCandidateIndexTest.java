package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCandidateIndexTest {

    private static final Identifier MACHINE = Identifier.fromNamespaceAndPath("test", "machine");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindComponents(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND);
    }

    @Test
    void candidatesIncludeMatchingExactItemsAndFallbackRecipesInOriginalOrder() {
        MachineRecipe iron = itemRecipe("iron", Ingredient.of(Items.IRON_INGOT));
        MachineRecipe gold = itemRecipe("gold", Ingredient.of(Items.GOLD_INGOT));
        MachineRecipe noItemInput = new MachineRecipe(id("no_item"), MACHINE, 20,
                List.of(), List.of(), List.of(), 0, 1);

        RecipeCandidateIndex index = RecipeCandidateIndex.build(List.of(iron, gold, noItemInput));

        assertThat(index.candidates(List.of(Items.IRON_INGOT))).containsExactly(iron, noItemInput);
        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(noItemInput);
    }

    @Test
    void multiItemIngredientFallsBackRatherThanExcludingAValidRecipe() {
        MachineRecipe alternatives = itemRecipe("alternatives", Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT));

        RecipeCandidateIndex index = RecipeCandidateIndex.build(List.of(alternatives));

        assertThat(index.candidates(List.of(Items.DIAMOND))).containsExactly(alternatives);
    }

    private static MachineRecipe itemRecipe(String path, Ingredient ingredient) {
        return new MachineRecipe(id(path), MACHINE, 20, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, ingredient, 1, ItemStack.EMPTY)), false);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }

    private static void bindComponents(Item... items) {
        for (Item item : items) item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }
}
