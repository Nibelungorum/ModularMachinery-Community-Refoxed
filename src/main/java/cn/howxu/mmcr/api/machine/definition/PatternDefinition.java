package cn.howxu.mmcr.api.machine.definition;

import cn.howxu.mmcr.internal.structure.PatternRows;

import java.util.LinkedHashMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable layered structure pattern.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PatternDefinition(List<List<String>> layers, Map<Character, BlockPredicate> predicates,
                                 char controllerSymbol, int width, int height, int depth) {
    public PatternDefinition {
        Objects.requireNonNull(layers, "layers");
        Objects.requireNonNull(predicates, "predicates");
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("At least one pattern layer is required");
        }
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Pattern dimensions must be positive");
        }
        if (layers.size() != depth) {
            throw new IllegalArgumentException("Pattern depth must match layer count");
        }
        if (controllerSymbol == ' ') {
            throw new IllegalArgumentException("Controller symbol must not be empty space");
        }
        PatternRows rows = PatternRows.copyOf(layers);
        if (rows.height() != height || rows.width() != width) {
            throw new IllegalArgumentException("Pattern dimensions must match layer rows");
        }
        predicates = Map.copyOf(predicates);
        rows.validateBindings(predicates, controllerSymbol);
        layers = rows;
    }

    PatternDefinition bindController(BlockPredicate controller) {
        Map<Character, BlockPredicate> boundPredicates = new LinkedHashMap<>(predicates);
        if (predicates.get(controllerSymbol).isAutomaticController()) {
            boundPredicates.put(controllerSymbol, controller);
        }
        return new PatternDefinition(layers, boundPredicates, controllerSymbol, width, height, depth);
    }
}
