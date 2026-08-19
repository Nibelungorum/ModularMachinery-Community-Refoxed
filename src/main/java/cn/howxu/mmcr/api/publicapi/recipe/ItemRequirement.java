package cn.howxu.mmcr.api.publicapi.recipe;

import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Objects;

/** Immutable public item recipe requirement.
 * @author howxu <dev@howxu.cn>
 */
public record ItemRequirement(RecipeIo io, Ingredient ingredient, int count, ItemStack stack, float chance,
                              DataComponentPredicateSet components, float consumeChance) implements RecipeRequirement {
    public ItemRequirement {
        Objects.requireNonNull(io, "io");
        components = components == null ? DataComponentPredicateSet.EMPTY : components;
        if (io == RecipeIo.INPUT) {
            Objects.requireNonNull(ingredient, "ingredient");
            if (count < 1) throw new IllegalArgumentException("Item input count must be positive");
            validateChance(consumeChance, "consumeChance");
            stack = ItemStack.EMPTY;
            chance = 1F;
        } else {
            Objects.requireNonNull(stack, "stack");
            stack = stack.copy();
            if (stack.isEmpty() || stack.getCount() < 1) throw new IllegalArgumentException("Item output must not be empty");
            ingredient = null;
            count = 0;
            validateChance(chance, "chance");
            consumeChance = 1F;
        }
    }

    public static ItemRequirement input(ItemInput input) {
        return new ItemRequirement(RecipeIo.INPUT, input.ingredient(), input.count(), ItemStack.EMPTY, 1F,
                input.components(), input.consumeChance());
    }

    public static ItemRequirement output(ItemOutput output) {
        return new ItemRequirement(RecipeIo.OUTPUT, null, 0, output.stack(), output.chance(),
                DataComponentPredicateSet.EMPTY, 1F);
    }

    @Override public ItemStack stack() { return stack.copy(); }

    private static void validateChance(float chance, String name) {
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
