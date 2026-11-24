package cn.howxu.mmcr.api.machine.definition;

import cn.howxu.mmcr.internal.structure.PatternRows;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


/**
 * Layered structure pattern builder.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBuilder {
    private final Map<Character, BlockPredicate> predicates = new LinkedHashMap<>();
    private final PatternRows.Builder layers = new PatternRows.Builder();
    private Character controllerSymbol;

    private PatternBuilder() {
    }

    public static PatternBuilder pattern() {
        return new PatternBuilder();
    }

    public PatternBuilder layer(String... rows) {
        layers.add(rows);
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
        if (!predicates.containsKey(symbol)) {
            predicates.put(symbol, BlockPredicate.automaticController());
        }
        return this;
    }

    public PatternDefinition build() {
        PatternRows rows = layers.freeze();
        if (rows.isEmpty()) throw new IllegalStateException("At least one pattern layer is required");
        if (controllerSymbol == null) throw new IllegalStateException("Pattern controller symbol is required");
        return new PatternDefinition(rows, predicates, controllerSymbol, rows.width(), rows.height(), rows.size());
    }
}
