package cn.howxu.mmcr.api.recipe.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
<<<<<<< HEAD
import com.mojang.serialization.JsonOps;
=======
>>>>>>> feat/shared-multiblock-io
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author howxu <dev@howxu.cn>
 */
public record DataComponentPredicateSet(Map<DataComponentType<?>, ComponentPredicate> values) {

    public static final DataComponentPredicateSet EMPTY = new DataComponentPredicateSet(Map.of());
    public static final Codec<DataComponentPredicateSet> CODEC = Codec.of(DataComponentPredicateSet::encode, DataComponentPredicateSet::decode);

    public DataComponentPredicateSet {
        values = Map.copyOf(values);
    }

    public boolean matches(ItemStack stack) {
<<<<<<< HEAD
        return matches(stack, JsonOps.INSTANCE);
    }

    public boolean matches(ItemStack stack, DynamicOps<?> ops) {
        if (stack == null || stack.isEmpty()) return values.isEmpty();
        for (var entry : values.entrySet()) {
            if (!matches(stack, entry.getKey(), entry.getValue(), ops)) return false;
=======
        for (var entry : values.entrySet()) {
            if (!matches(stack, entry.getKey(), entry.getValue())) return false;
>>>>>>> feat/shared-multiblock-io
        }
        return true;
    }

    public ItemStack displayStack(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        applyTo(stack);
        return stack;
    }

<<<<<<< HEAD
    public ItemStack displayStack(Item item, int count, DynamicOps<?> ops) {
        ItemStack stack = new ItemStack(item, count);
        applyTo(stack, ops);
        return stack;
    }

=======
>>>>>>> feat/shared-multiblock-io
    /**
     * Write every exact-valued component onto {@code stack} via {@link ItemStack#set}.
     * Mirrors the vanilla anvil approach where JEI slots are built by mutating a real
     * {@code ItemStack} with the recipe's data, then handed to the renderer as-is.
     * <p>
     * Components whose predicate is not {@link ComponentPredicate.Exact}, or whose exact
     * value cannot be decoded without registry access, are silently skipped; callers can
     * detect that case through {@link #exactPatch()}.
     */
    public void applyTo(ItemStack stack) {
        for (var entry : values.entrySet()) {
            applyExactValue(stack, entry.getKey(), entry.getValue());
        }
    }

<<<<<<< HEAD
    public void applyTo(ItemStack stack, DynamicOps<?> ops) {
        for (var entry : values.entrySet()) {
            applyExactValue(stack, entry.getKey(), entry.getValue(), ops);
        }
    }

=======
>>>>>>> feat/shared-multiblock-io
    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Optional<DataComponentPatch> exactPatch() {
        DataComponentPatch.Builder patch = DataComponentPatch.builder();
        for (var entry : values.entrySet()) {
            if (!(entry.getValue() instanceof ComponentPredicate.Exact exact)) return Optional.empty();
<<<<<<< HEAD
            Object value = ComponentPredicates.exactValue(entry.getKey(), exact, ItemStack.EMPTY);
=======
            Object value = ComponentPredicates.exactValue(entry.getKey(), exact);
>>>>>>> feat/shared-multiblock-io
            if (value == null) return Optional.empty();
            setPatchValue(patch, entry.getKey(), value);
        }
        return Optional.of(patch.build());
    }

    public boolean hasNonExactValues() {
        return values.values().stream().anyMatch(predicate -> !(predicate instanceof ComponentPredicate.Exact));
    }

    private static <T> DataResult<T> encode(DataComponentPredicateSet predicates, DynamicOps<T> ops, T prefix) {
        Map<T, T> values = new LinkedHashMap<>();
        for (var entry : predicates.values.entrySet()) {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
            if (id == null) return DataResult.error(() -> "Unregistered data component type " + entry.getKey());
            var predicate = ComponentPredicate.CODEC.encodeStart(ops, entry.getValue()).result();
            if (predicate.isEmpty()) return DataResult.error(() -> "Could not encode data component predicate " + id);
            values.put(ops.createString(id.toString()), predicate.get());
        }
        return DataResult.success(ops.createMap(values));
    }

    private static <T> DataResult<Pair<DataComponentPredicateSet, T>> decode(DynamicOps<T> ops, T input) {
        return new Dynamic<>(ops, input).asMapOpt().flatMap(values -> {
            Map<DataComponentType<?>, ComponentPredicate> decoded = new LinkedHashMap<>();
            for (var entry : values.toList()) {
                var key = entry.getFirst().asString().result();
<<<<<<< HEAD
                var predicate = ComponentPredicate.CODEC.parse(entry.getSecond()).result()
                        .or(() -> Optional.of(ComponentPredicate.exact(entry.getSecond())));
=======
                var predicate = ComponentPredicate.CODEC.parse(entry.getSecond()).result();
>>>>>>> feat/shared-multiblock-io
                if (key.isEmpty() || predicate.isEmpty()) return DataResult.error(() -> "Invalid data component predicate");
                Identifier id;
                try {
                    id = Identifier.parse(key.get());
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Invalid data component type " + key.get());
                }
                DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
                if (type == null) return DataResult.error(() -> "Unknown data component type " + id);
                decoded.put(type, predicate.get());
            }
            return DataResult.success(Pair.of(decoded.isEmpty() ? EMPTY : new DataComponentPredicateSet(decoded), input));
        });
    }

<<<<<<< HEAD
    private static <T> boolean matches(ItemStack actual, DataComponentType<T> type, ComponentPredicate predicate,
            DynamicOps<?> ops) {
        T actualValue = actual.get(type);
        if (actualValue == null) return false;
        return ComponentPredicates.matches(type, actualValue, predicate, ops);
    }

    private static <T> void applyExactValue(ItemStack stack, DataComponentType<T> type, ComponentPredicate predicate) {
        applyExactValue(stack, type, predicate, null);
    }

    private static <T> void applyExactValue(ItemStack stack, DataComponentType<T> type, ComponentPredicate predicate,
            DynamicOps<?> ops) {
        T value = ComponentPredicates.exactValue(type, predicate, stack, ops);
=======
    private static <T> boolean matches(ItemStack stack, DataComponentType<T> type, ComponentPredicate predicate) {
        T value = stack.get(type);
        return value != null && ComponentPredicates.matches(type, value, predicate);
    }

    private static <T> void applyExactValue(ItemStack stack, DataComponentType<T> type, ComponentPredicate predicate) {
        T value = ComponentPredicates.exactValue(type, predicate);
>>>>>>> feat/shared-multiblock-io
        if (value == null && type == DataComponents.CUSTOM_NAME && predicate instanceof ComponentPredicate.TextValue text) {
            value = (T) text.value();
        }
        if (value != null) stack.set(type, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setPatchValue(DataComponentPatch.Builder builder, DataComponentType<T> type, Object value) {
        builder.set(type, (T) value);
    }
}
