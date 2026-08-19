package cn.howxu.mmcr.api.publicapi.machine;

import java.util.List;
import java.util.Map;

/**
 * Immutable layered structure pattern.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PatternDefinition(List<List<String>> layers, Map<Character, BlockPredicate> predicates,
                                char controllerSymbol, int width, int height, int depth) {
    public PatternDefinition {
        layers = layers.stream()
                .map(List::copyOf)
                .toList();
        predicates = Map.copyOf(predicates);
    }
}
