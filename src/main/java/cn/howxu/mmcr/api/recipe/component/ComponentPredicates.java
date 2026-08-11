package cn.howxu.mmcr.api.recipe.component;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
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
        if (predicate instanceof ComponentPredicate.Exact exact) {
            return matchesExact(type, value, exact);
        }
        return encodeAndMatch(type, value, predicate, COMPONENT_OPS);
    }

    static <T> T exactValue(DataComponentType<T> type, ComponentPredicate predicate) {
        if (!(predicate instanceof ComponentPredicate.Exact exact)) return null;
        return parseExactValue(type, exact);
    }

    @SuppressWarnings("unchecked")
    private static <T, O> boolean matchesExact(DataComponentType<T> type, T value, ComponentPredicate.Exact exact) {
        T expected = parseExactValue(type, exact);
        if (expected != null) return expected.equals(value);
        return encodeAndMatch(type, value, exact, (DynamicOps<O>) exact.value().getOps());
    }

    private static <T, O> boolean encodeAndMatch(DataComponentType<T> type, T value, ComponentPredicate predicate, DynamicOps<O> ops) {
        return type.codec().encodeStart(ops, value)
                .map(encoded -> predicate.matches(new Dynamic<>(ops, encoded)))
                .result().orElse(false);
    }

    @SuppressWarnings("unchecked")
    private static <T, O> T parseExactValue(DataComponentType<T> type, ComponentPredicate.Exact exact) {
        Dynamic<O> value = (Dynamic<O>) exact.value();
        T parsed = type.codec().parse(value.getOps(), value.getValue()).result().orElse(null);
        if (parsed != null) return parsed;
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (id == null) return null;
        return componentFromStackCodec(id, type, value);
    }

    private static <T, O> T componentFromStackCodec(Identifier id, DataComponentType<T> type, Dynamic<O> value) {
        O components = value.getOps().mapBuilder()
                .add(id.toString(), value.getValue())
                .build(value.getOps().emptyMap())
                .result().orElse(null);
        if (components == null) return null;
        O stack = value.getOps().mapBuilder()
                .add("id", value.getOps().createString("minecraft:diamond_sword"))
                .add("count", value.getOps().createInt(1))
                .add("components", components)
                .build(value.getOps().emptyMap())
                .result().orElse(null);
        if (stack == null) return null;
        return ItemStack.CODEC.parse(value.getOps(), stack).result()
                .map(parsed -> parsed.get(type))
                .orElse(null);
    }
}
