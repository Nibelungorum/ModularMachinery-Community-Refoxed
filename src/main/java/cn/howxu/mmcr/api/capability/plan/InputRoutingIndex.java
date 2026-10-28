package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.recipe.InputScope;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Compiles fixed input scopes against full capability occurrence indexes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InputRoutingIndex {
    private final Map<BlockPos, List<Integer>> sources;

    private InputRoutingIndex(Map<BlockPos, List<Integer>> sources) {
        this.sources = sources;
    }

    public static InputRoutingIndex capture(InputRoutingFacet routing) {
        Map<BlockPos, List<Integer>> sources = new HashMap<>();
        for (InputRoutingFacet.Source source : routing.sources()) {
            sources.computeIfAbsent(source.position(), ignored -> new ArrayList<>()).add(source.capabilityIndex());
        }
        sources.replaceAll((position, indexes) -> List.copyOf(indexes));
        return new InputRoutingIndex(Map.copyOf(sources));
    }

    public IntPredicate allowed(InputScope scope) {
        if (scope.kind() == InputScope.Kind.LEGACY) return ignored -> true;
        BitSet allowed = new BitSet();
        for (BlockPos position : scope.sources()) {
            for (int index : sources.getOrDefault(position, List.of())) allowed.set(index);
        }
        return index -> index >= 0 && allowed.get(index);
    }
}
