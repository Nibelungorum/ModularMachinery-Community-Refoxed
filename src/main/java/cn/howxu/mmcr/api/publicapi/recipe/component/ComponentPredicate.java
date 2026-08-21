package cn.howxu.mmcr.api.publicapi.recipe.component;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

/**
 * Immutable JSON-backed declaration for matching an item data component.
 *
 * @author howxu <dev@howxu.cn>
 */
public sealed interface ComponentPredicate permits ComponentPredicate.Exact, ComponentPredicate.MapValue,
        ComponentPredicate.ListValue, ComponentPredicate.Range, ComponentPredicate.TextValue {

    static ComponentPredicate exact(JsonElement value) {
        return new Exact(value);
    }

    static ComponentPredicate map(Map<String, ComponentPredicate> values) {
        return new MapValue(values);
    }

    static ComponentPredicate list(List<ComponentPredicate> values) {
        return new ListValue(values);
    }

    static ComponentPredicate range(double min, double max) {
        return new Range(min, max);
    }

    static ComponentPredicate text(String value, TextMode mode) {
        return new TextValue(value, mode);
    }

    default boolean isExact() {
        return this instanceof Exact;
    }

    record Exact(JsonElement value) implements ComponentPredicate {
        public Exact {
            value = value.deepCopy();
        }

        @Override
        public JsonElement value() {
            return value.deepCopy();
        }
    }

    record MapValue(Map<String, ComponentPredicate> values) implements ComponentPredicate {
        public MapValue {
            values = Map.copyOf(values);
        }
    }

    record ListValue(List<ComponentPredicate> values) implements ComponentPredicate {
        public ListValue {
            values = List.copyOf(values);
        }
    }

    record Range(double min, double max) implements ComponentPredicate {
    }

    record TextValue(String value, TextMode mode) implements ComponentPredicate {
    }

    enum TextMode {
        PLAIN,
        FULL
    }
}
