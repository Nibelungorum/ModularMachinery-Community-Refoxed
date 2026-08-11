package cn.howxu.mmcr.api.recipe.component;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

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

    static <T> T exactValue(DataComponentType<T> type, ComponentPredicate predicate, ItemStack targetStack) {
        return exactValue(type, predicate, targetStack, null);
    }

    static <T> T exactValue(DataComponentType<T> type, ComponentPredicate predicate, ItemStack targetStack,
            @org.jetbrains.annotations.Nullable DynamicOps<?> overrideOps) {
        if (!(predicate instanceof ComponentPredicate.Exact exact)) return null;
        return parseExactValue(type, exact, targetStack, overrideOps);
    }

    @SuppressWarnings("unchecked")
    private static <T, O> boolean matchesExact(DataComponentType<T> type, T value, ComponentPredicate.Exact exact) {
        T expected = parseExactValue(type, exact, null, null);
        if (expected != null && expected.equals(value)) return true;
        if (type == DataComponents.ENCHANTMENTS && value instanceof ItemEnchantments enchantments
                && matchesStandardEnchantments(enchantments, exact)) return true;
        return encodeAndMatch(type, value, exact, (DynamicOps<O>) exact.value().getOps());
    }

    private static boolean matchesStandardEnchantments(ItemEnchantments enchantments, ComponentPredicate.Exact exact) {
        var expected = exact.value().convert(JsonOps.INSTANCE).getValue();
        if (!expected.isJsonObject()) return false;
        JsonObject actual = new JsonObject();
        for (var enchantment : enchantments.keySet()) {
            var key = enchantment.unwrapKey();
            if (key.isEmpty()) return false;
            actual.addProperty(key.get().identifier().toString(), enchantments.getLevel(enchantment));
        }
        return actual.equals(expected);
    }

    private static <T, O> boolean encodeAndMatch(DataComponentType<T> type, T value, ComponentPredicate predicate, DynamicOps<O> ops) {
        return type.codec().encodeStart(ops, value)
                .map(encoded -> predicate.matches(new Dynamic<>(ops, encoded)))
                .result().orElse(false);
    }

    @SuppressWarnings("unchecked")
    private static <T, O> T parseExactValue(DataComponentType<T> type, ComponentPredicate.Exact exact,
            @org.jetbrains.annotations.Nullable ItemStack targetStack,
            @org.jetbrains.annotations.Nullable DynamicOps<?> overrideOps) {
        Dynamic<O> value = (Dynamic<O>) exact.value();
        DynamicOps<O> ops = overrideOps == null ? value.getOps() : (DynamicOps<O>) overrideOps;
        O input = overrideOps == null ? value.getValue() : value.convert(ops).getValue();
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (id != null) {
            T parsedFromStack = componentFromStackCodec(id, type, ops, input, targetStack);
            if (parsedFromStack != null) return parsedFromStack;
        }
        return type.codec().parse(ops, input).result().orElse(null);
    }

    private static <T, O> T componentFromStackCodec(Identifier id, DataComponentType<T> type, DynamicOps<O> ops, O value,
            ItemStack targetStack) {
        O components = ops.mapBuilder()
                .add(id.toString(), value)
                .build(ops.emptyMap())
                .result().orElse(null);
        if (components == null) return null;
        T parsedPatch = DataComponentPatch.CODEC.parse(ops, components).result()
                .map(patch -> patch.getPatch(type))
                .filter(java.util.Objects::nonNull)
                .flatMap(optional -> optional)
                .orElse(null);
        if (parsedPatch != null) return parsedPatch;
        if (targetStack == null || targetStack.isEmpty()) return null;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(targetStack.getItem());
        if (itemId == null) return null;
        if (!targetStack.getItem().builtInRegistryHolder().areComponentsBound()) {
            targetStack.getItem().builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
        O stack = ops.mapBuilder()
                .add("id", ops.createString(itemId.toString()))
                .add("count", ops.createInt(Math.max(1, targetStack.getCount())))
                .add("components", components)
                .build(ops.emptyMap())
                .result().orElse(null);
        if (stack == null) return null;
        return ItemStack.CODEC.parse(ops, stack).result()
                .map(parsed -> parsed.get(type))
                .orElse(null);
    }
}
