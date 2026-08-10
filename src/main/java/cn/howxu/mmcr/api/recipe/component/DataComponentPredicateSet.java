package cn.howxu.mmcr.api.recipe.component;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * @author howxu <dev@howxu.cn>
 */
public record DataComponentPredicateSet(Map<DataComponentType<?>, ComponentPredicate> values) {

    public DataComponentPredicateSet {
        values = Map.copyOf(values);
    }

    public boolean matches(ItemStack stack) {
        for (var entry : values.entrySet()) {
            if (!matches(stack, entry.getKey(), entry.getValue())) return false;
        }
        return true;
    }

    public ItemStack displayStack(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        for (var entry : values.entrySet()) {
            applyExactValue(stack, entry.getKey(), entry.getValue());
        }
        return stack;
    }

    private static <T> boolean matches(ItemStack stack, DataComponentType<T> type, ComponentPredicate predicate) {
        T value = stack.get(type);
        return value != null && ComponentPredicates.matches(type, value, predicate);
    }

    private static <T> void applyExactValue(ItemStack stack, DataComponentType<T> type, ComponentPredicate predicate) {
        T value = ComponentPredicates.exactValue(type, predicate);
        if (value != null) stack.set(type, value);
    }
}
