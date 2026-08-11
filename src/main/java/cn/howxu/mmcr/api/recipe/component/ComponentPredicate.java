package cn.howxu.mmcr.api.recipe.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author howxu <dev@howxu.cn>
 */
public sealed interface ComponentPredicate permits ComponentPredicate.Exact, ComponentPredicate.MapValue,
        ComponentPredicate.ListValue, ComponentPredicate.Range, ComponentPredicate.TextValue {

    Codec<ComponentPredicate> CODEC = Codec.of(ComponentPredicate::encode, ComponentPredicate::decode);

    boolean matches(Dynamic<?> candidate);

    static <T> ComponentPredicate exact(Dynamic<T> value) {
        return new Exact(value);
    }

    static ComponentPredicate map(Map<String, ComponentPredicate> values) {
        return new MapValue(Map.copyOf(values));
    }

    static ComponentPredicate list(List<ComponentPredicate> values) {
        return new ListValue(List.copyOf(values));
    }

    static ComponentPredicate range(double min, double max) {
        return new Range(min, max);
    }

    static ComponentPredicate text(String value, TextMode mode) {
        return new TextValue(Component.literal(value), mode);
    }

    static ComponentPredicate text(Component value, TextMode mode) {
        return new TextValue(value, mode);
    }

    private static <T> DataResult<T> encode(ComponentPredicate predicate, DynamicOps<T> ops, T prefix) {
        if (predicate instanceof Exact exact) {
            return ops.mapBuilder()
                    .add("type", ops.createString("exact"))
                    .add("value", exact.value.convert(ops).getValue())
                    .build(prefix);
        }
        if (predicate instanceof MapValue map) {
            Map<T, T> values = new LinkedHashMap<>();
            for (var entry : map.values.entrySet()) {
                var encoded = CODEC.encodeStart(ops, entry.getValue()).result();
                if (encoded.isEmpty()) return DataResult.error(() -> "Could not encode component predicate " + entry.getKey());
                values.put(ops.createString(entry.getKey()), encoded.get());
            }
            return ops.mapBuilder().add("type", ops.createString("map")).add("values", ops.createMap(values)).build(prefix);
        }
        if (predicate instanceof ListValue list) {
            List<T> values = new ArrayList<>();
            for (ComponentPredicate value : list.values) {
                var encoded = CODEC.encodeStart(ops, value).result();
                if (encoded.isEmpty()) return DataResult.error(() -> "Could not encode component list predicate");
                values.add(encoded.get());
            }
            return ops.mapBuilder().add("type", ops.createString("list")).add("values", ops.createList(values.stream())).build(prefix);
        }
        if (predicate instanceof Range range) {
            return ops.mapBuilder()
                    .add("type", ops.createString("range"))
                    .add("min", ops.createDouble(range.min))
                    .add("max", ops.createDouble(range.max))
                    .build(prefix);
        }
        TextValue text = (TextValue) predicate;
        return ops.mapBuilder()
                .add("type", ops.createString("text"))
                .add("value", ComponentSerialization.CODEC.encodeStart(ops, text.value))
                .add("mode", ops.createString(text.mode.serializedName))
                .build(prefix);
    }

    private static <T> DataResult<Pair<ComponentPredicate, T>> decode(DynamicOps<T> ops, T input) {
        Dynamic<T> dynamic = new Dynamic<>(ops, input);
        return dynamic.get("type").asString().flatMap(type -> switch (type) {
            case "exact" -> dynamic.get("value").result().map(ComponentPredicate::exact).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Component exact predicate is missing a value"));
            case "map" -> dynamic.get("values").asMapOpt().flatMap(values -> {
                Map<String, ComponentPredicate> decoded = new LinkedHashMap<>();
                for (var entry : values.toList()) {
                    var key = entry.getFirst().asString().result();
                    var value = CODEC.parse(entry.getSecond()).result();
                    if (key.isEmpty() || value.isEmpty()) return DataResult.error(() -> "Invalid component map predicate");
                    decoded.put(key.get(), value.get());
                }
                return DataResult.success(ComponentPredicate.map(decoded));
            });
            case "list" -> dynamic.get("values").asStreamOpt().flatMap(values -> {
                List<ComponentPredicate> decoded = new ArrayList<>();
                for (Dynamic<T> value : values.toList()) {
                    var parsed = CODEC.parse(value).result();
                    if (parsed.isEmpty()) return DataResult.error(() -> "Invalid component list predicate");
                    decoded.add(parsed.get());
                }
                return DataResult.success(ComponentPredicate.list(decoded));
            });
            case "range" -> dynamic.get("min").asNumber().flatMap(min -> dynamic.get("max").asNumber()
                    .flatMap(max -> min.doubleValue() <= max.doubleValue()
                            ? DataResult.success(ComponentPredicate.range(min.doubleValue(), max.doubleValue()))
                            : DataResult.error(() -> "Component predicate range minimum exceeds maximum")));
            case "text" -> dynamic.get("value").result()
                    .map(value -> ComponentSerialization.CODEC.parse(value).flatMap(text -> dynamic.get("mode").asString()
                            .flatMap(mode -> TextMode.byName(mode)
                                    .<DataResult<ComponentPredicate>>map(textMode -> DataResult.success(ComponentPredicate.text(text, textMode)))
                                    .orElseGet(() -> DataResult.error(() -> "Unknown component text mode " + mode)))))
                    .orElseGet(() -> DataResult.error(() -> "Component text predicate is missing a value"));
            default -> DataResult.error(() -> "Unknown component predicate type " + type);
        }).map(value -> Pair.of(value, input));
    }

    record Exact(Dynamic<?> value) implements ComponentPredicate {
        @Override
        public boolean matches(Dynamic<?> candidate) {
            return candidate.convert(JsonOps.INSTANCE).getValue().equals(value.convert(JsonOps.INSTANCE).getValue());
        }
    }

    record MapValue(Map<String, ComponentPredicate> values) implements ComponentPredicate {
        public MapValue {
            values = Map.copyOf(values);
        }

        @Override
        public boolean matches(Dynamic<?> candidate) {
            return values.entrySet().stream().allMatch(entry -> candidate.get(entry.getKey()).result()
                    .map(entry.getValue()::matches).orElse(false));
        }
    }

    record ListValue(List<ComponentPredicate> values) implements ComponentPredicate {
        public ListValue {
            values = List.copyOf(values);
        }

        @Override
        public boolean matches(Dynamic<?> candidate) {
            var stream = candidate.asStreamOpt().result();
            if (stream.isEmpty()) return false;
            return matches(values, new ArrayList<>(stream.get().toList()), 0);
        }

        private static boolean matches(List<ComponentPredicate> required, List<Dynamic<?>> candidates, int index) {
            if (index == required.size()) return true;
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                if (!required.get(index).matches(candidates.get(candidateIndex))) continue;
                Dynamic<?> candidate = candidates.remove(candidateIndex);
                if (matches(required, candidates, index + 1)) return true;
                candidates.add(candidateIndex, candidate);
            }
            return false;
        }
    }

    record Range(double min, double max) implements ComponentPredicate {
        @Override
        public boolean matches(Dynamic<?> candidate) {
            return candidate.asNumber().result().map(Number::doubleValue)
                    .map(value -> value >= min && value <= max).orElse(false);
        }
    }

    record TextValue(Component value, TextMode mode) implements ComponentPredicate {
        @Override
        public boolean matches(Dynamic<?> candidate) {
            var component = ComponentSerialization.CODEC.parse(candidate).result();
            if (component.isEmpty()) return false;
            if (mode == TextMode.PLAIN) return value.getString().equals(component.get().getString());
            return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, value).result()
                    .equals(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component.get()).result());
        }
    }

    enum TextMode {
        PLAIN("plain"),
        FULL("full");

        private final String serializedName;

        TextMode(String serializedName) {
            this.serializedName = serializedName;
        }

        private static java.util.Optional<TextMode> byName(String name) {
            for (TextMode value : values()) {
                if (value.serializedName.equals(name)) return java.util.Optional.of(value);
            }
            return java.util.Optional.empty();
        }
    }
}
