package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

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

    public static Optional<DisplayStack> optional(ItemStack stack) {
        return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(of(stack));
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }
}
