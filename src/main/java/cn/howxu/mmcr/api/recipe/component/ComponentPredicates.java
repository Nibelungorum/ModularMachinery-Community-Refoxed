package cn.howxu.mmcr.api.recipe.component;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentType;
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

    static <T> boolean matches(DataComponentType<T> type, T value, ComponentPredicate predicate, DynamicOps<?> ops) {
        if (predicate instanceof ComponentPredicate.Exact exact) {
            T expected = parseExactValue(type, exact, ops);
            if (expected != null) return expected.equals(value);
            expected = parseExactValue(type, exact, exact.value().getOps());
            if (expected != null) return expected.equals(value);
            return encodeAndMatch(type, value, exact, exact.value().getOps());
        }
        return encodeAndMatch(type, value, predicate, ops);
    }

    static <T> T exactValue(DataComponentType<T> type, ComponentPredicate predicate, ItemStack targetStack) {
        return exactValue(type, predicate, targetStack, null);
    }

    static <T> T exactValue(DataComponentType<T> type, ComponentPredicate predicate, ItemStack targetStack,
            @org.jetbrains.annotations.Nullable DynamicOps<?> overrideOps) {
        if (!(predicate instanceof ComponentPredicate.Exact exact)) return null;
        return parseExactValue(type, exact, overrideOps == null ? exact.value().getOps() : overrideOps);
    }

    @SuppressWarnings("unchecked")
    private static <T, O> boolean encodeAndMatch(DataComponentType<T> type, T value, ComponentPredicate predicate, DynamicOps<?> rawOps) {
        DynamicOps<O> ops = (DynamicOps<O>) rawOps;
        return type.codec().encodeStart(ops, value)
                .map(encoded -> predicate.matches(new Dynamic<>(ops, encoded)))
                .result().orElse(false);
    }

    @SuppressWarnings("unchecked")
    private static <T, O> T parseExactValue(DataComponentType<T> type, ComponentPredicate.Exact exact, DynamicOps<?> rawOps) {
        Dynamic<O> value = (Dynamic<O>) exact.value();
        DynamicOps<O> ops = (DynamicOps<O>) rawOps;
        try {
            return type.codec().parse(ops, value.convert(ops).getValue()).result().orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
