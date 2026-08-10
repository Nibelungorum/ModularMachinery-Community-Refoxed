package cn.howxu.mmcr.api.recipe.component;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.component.DataComponentType;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class ComponentPredicates {

    private static final DynamicOps<com.google.gson.JsonElement> COMPONENT_OPS = JsonOps.INSTANCE;

    private ComponentPredicates() {
    }

    public static boolean matches(ItemStack stack, DataComponentPredicateSet predicates) {
        return predicates.matches(stack);
    }

    static <T> boolean matches(DataComponentType<T> type, T value, ComponentPredicate predicate) {
        return type.codec().encodeStart(COMPONENT_OPS, value)
                .map(encoded -> predicate.matches(new Dynamic<>(COMPONENT_OPS, encoded)))
                .result().orElse(false);
    }

    static <T> T exactValue(DataComponentType<T> type, ComponentPredicate predicate) {
        if (!(predicate instanceof ComponentPredicate.Exact exact)) return null;
        return type.codec().parse(COMPONENT_OPS, exact.value().convert(COMPONENT_OPS).getValue()).result().orElse(null);
    }
}
