package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Immutable public display stack declaration.
 * @author howxu <dev@howxu.cn>
 */
public record DisplayStack(ItemStack stack) {
    public DisplayStack {
        stack = Objects.requireNonNull(stack, "stack").copy();
    }

    public static DisplayStack of(ItemStack stack) {
        return new DisplayStack(stack);
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }
}
