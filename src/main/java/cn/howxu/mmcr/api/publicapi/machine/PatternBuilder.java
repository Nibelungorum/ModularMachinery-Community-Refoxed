package cn.howxu.mmcr.api.publicapi.machine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Layered structure pattern builder.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBuilder {
    private final Map<Character, BlockPredicate> predicates = new LinkedHashMap<>();
    private List<List<String>> layers = List.of();
    private Character controllerSymbol;
    private int width = -1;
    private int height = -1;

    private PatternBuilder() {
    }

    public static PatternBuilder pattern() {
        return new PatternBuilder();
    }

    public PatternBuilder layer(String... rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.length == 0) {
            throw new IllegalArgumentException("layer(...) must contain at least one row");
        }
        int layerWidth = Objects.requireNonNull(rows[0], "row").length();
        if (layerWidth == 0) {
            throw new IllegalArgumentException("pattern rows must not be empty");
        }
        for (String row : rows) {
            Objects.requireNonNull(row, "row");
            if (row.length() != layerWidth) {
                throw new IllegalArgumentException("All rows in a pattern layer must have the same width");
            }
        }
        if (width != -1 && layerWidth != width) {
            throw new IllegalArgumentException("All pattern layers must have the same width");
        }
        if (height != -1 && rows.length != height) {
            throw new IllegalArgumentException("All pattern layers must have the same height");
        }

        width = layerWidth;
        height = rows.length;
        List<List<String>> copied = new ArrayList<>(layers.size() + 1);
        copied.addAll(layers);
        copied.add(List.of(rows));
        layers = List.copyOf(copied);
        return this;
    }

    public PatternBuilder pattern(String... rows) {
        return layer(rows);
    }

    public PatternBuilder where(char symbol, BlockPredicate predicate) {
        if (symbol == ' ') {
            throw new IllegalArgumentException("Space is reserved for empty pattern cells");
        }
        predicates.put(symbol, Objects.requireNonNull(predicate, "predicate"));
        return this;
    }

    public PatternBuilder controller(char symbol) {
        if (symbol == ' ') {
            throw new IllegalArgumentException("Controller symbol must not be empty space");
        }
        if (controllerSymbol != null) {
            throw new IllegalStateException("Pattern controller symbol is already set");
        }
        controllerSymbol = symbol;
        return this;
    }

    public PatternDefinition build() {
        if (layers.isEmpty()) {
            throw new IllegalStateException("At least one pattern layer is required");
        }
        if (controllerSymbol == null) {
            throw new IllegalStateException("Pattern controller symbol is required");
        }

        int controllerCount = 0;
        for (List<String> layer : layers) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    if (symbol == ' ') continue;
                    if (!predicates.containsKey(symbol)) {
                        throw new IllegalStateException("Unbound pattern symbol: " + symbol);
                    }
                    if (symbol == controllerSymbol) controllerCount++;
                }
            }
        }
        if (controllerCount != 1) {
            throw new IllegalStateException("Pattern must contain exactly one controller symbol");
        }
        return new PatternDefinition(layers, predicates, controllerSymbol, width, height, layers.size());
    }
}
