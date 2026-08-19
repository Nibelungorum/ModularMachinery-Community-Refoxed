package cn.howxu.mmcr.api.publicapi.machine;

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
        layers = layers.stream()
                .map(layer -> {
                    if (layer.size() != height) {
                        throw new IllegalArgumentException("Pattern height must match each layer row count");
                    }
                    return layer.stream()
                            .map(row -> {
                                Objects.requireNonNull(row, "row");
                                if (row.length() != width) {
                                    throw new IllegalArgumentException("Pattern width must match each row");
                                }
                                return row;
                            })
                            .toList();
                })
                .toList();
        predicates = Map.copyOf(predicates);

        int controllerCount = 0;
        for (List<String> layer : layers) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    if (symbol == ' ') continue;
                    if (!predicates.containsKey(symbol)) {
                        throw new IllegalArgumentException("Unbound pattern symbol: " + symbol);
                    }
                    if (symbol == controllerSymbol) controllerCount++;
                }
            }
        }
        if (controllerCount != 1) {
            throw new IllegalArgumentException("Pattern must contain exactly one controller symbol");
        }
    }
}
