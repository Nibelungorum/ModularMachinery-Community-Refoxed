package cn.howxu.mmcr.api.recipe.modifier;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Immutable item identity key that excludes stack count.
 * @author howxu <dev@howxu.cn>
 */
public record ModifierItemKey(Item item, DataComponentMap components) {
    public ModifierItemKey {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(components, "components");
    }

    public static ModifierItemKey of(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        DataComponentMap components = stack.getComponents();
        if (components instanceof PatchedDataComponentMap patched) {
            components = patched.toImmutableMap();
        }
        return new ModifierItemKey(stack.getItem(), components);
    }
}
