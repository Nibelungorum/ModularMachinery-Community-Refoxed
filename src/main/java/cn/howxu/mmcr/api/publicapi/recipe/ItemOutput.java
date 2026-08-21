package cn.howxu.mmcr.api.publicapi.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;

import java.util.Objects;

/** Immutable public item output value.
 * @author howxu <dev@howxu.cn>
 */
public record ItemOutput(ItemStack stack, float chance, DataComponentPredicateSet components) {
    public ItemOutput {
        Objects.requireNonNull(stack, "stack");
        stack = stack.copy();
        if (stack.isEmpty() || stack.getCount() < 1) throw new IllegalArgumentException("Item output must not be empty");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) throw new IllegalArgumentException("chance must be in [0, 1]");
        components = components == null ? DataComponentPredicateSet.EMPTY : components;
        if (components.hasNonExactValues()) throw new IllegalArgumentException("Item output components must be exact");
    }

    public ItemOutput(Item item, int count) {
        this(new ItemStack(Objects.requireNonNull(item, "item"), count), 1F, DataComponentPredicateSet.EMPTY);
    }

    public ItemOutput(ItemStack stack) {
        this(stack, 1F, DataComponentPredicateSet.EMPTY);
    }

    public ItemOutput(ItemStack stack, float chance) {
        this(stack, chance, DataComponentPredicateSet.EMPTY);
    }

    public ItemOutput(ItemStack stack, DataComponentPredicateSet components) {
        this(stack, 1F, components);
    }

    @Override public ItemStack stack() { return stack.copy(); }
}
