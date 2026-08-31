package cn.howxu.mmcr.api.recipe.modifier;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Immutable item identity key that excludes stack count.
 * @author howxu <dev@howxu.cn>
 */
public record ModifierItemKey(Item item, DataComponentPatch patch) {
    public ModifierItemKey {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(patch, "patch");
    }

    public static ModifierItemKey of(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return new ModifierItemKey(stack.getItem(), stack.getComponentsPatch());
    }

    public DataComponentMap components() {
        return patch.split().added();
    }
}
