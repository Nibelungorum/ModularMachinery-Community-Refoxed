package cn.howxu.mmcr.api.publicapi.recipe;

import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Objects;

/** Immutable public item input value.
 * @author howxu <dev@howxu.cn>
 */
public record ItemInput(Ingredient ingredient, int count, DataComponentPredicateSet components, float consumeChance) {
    public ItemInput {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count < 1) throw new IllegalArgumentException("Item input count must be positive");
        components = components == null ? DataComponentPredicateSet.EMPTY : components;
        validateChance(consumeChance, "consumeChance");
    }

    public ItemInput(Item item, int count) {
        this(Ingredient.of(Objects.requireNonNull(item, "item")), count, DataComponentPredicateSet.EMPTY, 1F);
    }

    public ItemInput(Ingredient ingredient, int count) {
        this(ingredient, count, DataComponentPredicateSet.EMPTY, 1F);
    }

    private static void validateChance(float chance, String name) {
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
