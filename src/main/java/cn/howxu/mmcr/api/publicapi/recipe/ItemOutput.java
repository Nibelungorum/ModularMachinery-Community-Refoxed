package cn.howxu.mmcr.api.publicapi.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Immutable public item output value.
 * @author howxu <dev@howxu.cn>
 */
public record ItemOutput(ItemStack stack, float chance) {
    public ItemOutput {
        Objects.requireNonNull(stack, "stack");
        stack = stack.copy();
        if (stack.isEmpty() || stack.getCount() < 1) throw new IllegalArgumentException("Item output must not be empty");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) throw new IllegalArgumentException("chance must be in [0, 1]");
    }

    public ItemOutput(Item item, int count) {
        this(new ItemStack(Objects.requireNonNull(item, "item"), count), 1F);
    }

    public ItemOutput(ItemStack stack) {
        this(stack, 1F);
    }

    @Override public ItemStack stack() { return stack.copy(); }
}
