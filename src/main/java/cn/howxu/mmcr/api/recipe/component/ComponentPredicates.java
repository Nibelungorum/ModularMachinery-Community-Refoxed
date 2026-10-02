package cn.howxu.mmcr.api.recipe.component;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class ComponentPredicates {

    private static final DynamicOps<JsonElement> COMPONENT_OPS = JsonOps.INSTANCE;

    private ComponentPredicates() {
    }

    public static boolean matches(ItemStack stack, DataComponentPredicateSet predicates) {
        return predicates.matches(stack);
    }

    static <T> boolean matches(DataComponentType<T> type, T value, ComponentPredicate predicate, DynamicOps<?> ops) {
        if (predicate instanceof ComponentPredicate.Exact exact) {
            Dynamic<?> exactValue = exact.value();
            T expected = parseExactValue(type, exactValue, ops);
            if (expected != null) return expected.equals(value);
            if (ops != exactValue.getOps()) {
                expected = parseExactValue(type, exactValue, exactValue.getOps());
                if (expected != null) return expected.equals(value);
            }
            return encodeAndMatch(type, value,
                    candidate -> candidate.convert(COMPONENT_OPS).getValue().equals(exactValue.convert(COMPONENT_OPS).getValue()),
                    exactValue.getOps());
        }
        return encodeAndMatch(type, value, candidate -> predicate.matches(candidate, type.codec()), ops);
    }

    static <T> T exactValue(DataComponentType<T> type, ComponentPredicate predicate, ItemStack targetStack) {
        return exactValue(type, predicate, targetStack, null);
    }

    static <T> T exactValue(DataComponentType<T> type, ComponentPredicate predicate, ItemStack targetStack,
            @Nullable DynamicOps<?> overrideOps) {
        if (!(predicate instanceof ComponentPredicate.Exact exact)) return null;
        Dynamic<?> value = exact.value();
        T parsed = parseExactValue(type, value, overrideOps == null ? value.getOps() : overrideOps);
        if (parsed != null) return parsed;
        return overrideOps != null && overrideOps != value.getOps() ? parseExactValue(type, value, value.getOps()) : null;
    }

    @SuppressWarnings("unchecked")
    private static <T, O> boolean encodeAndMatch(DataComponentType<T> type, T value, Predicate<Dynamic<?>> predicate, DynamicOps<?> rawOps) {
        DynamicOps<O> ops = (DynamicOps<O>) rawOps;
        return type.codec().encodeStart(ops, value)
                .map(encoded -> predicate.test(new Dynamic<>(ops, encoded)))
                .result().orElse(false);
    }

    @SuppressWarnings("unchecked")
    private static <T, O> T parseExactValue(DataComponentType<T> type, Dynamic<?> exactValue, DynamicOps<?> rawOps) {
        Dynamic<O> value = (Dynamic<O>) exactValue;
        DynamicOps<O> ops = (DynamicOps<O>) rawOps;
        try {
            return type.codec().parse(ops, value.convert(ops).getValue()).result().orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
