package cn.howxu.mmcr.api.publicapi.recipe.component;

import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Immutable component predicate declarations keyed by data component identifier.
 *
 * @author howxu <dev@howxu.cn>
 */
public record DataComponentPredicateSet(Map<Identifier, ComponentPredicate> values) {

    public static final DataComponentPredicateSet EMPTY = new DataComponentPredicateSet(Map.of());

    public DataComponentPredicateSet {
        values = Map.copyOf(values);
    }

    public boolean hasNonExactValues() {
        return values.values().stream().anyMatch(predicate -> !predicate.isExact());
    }
}
