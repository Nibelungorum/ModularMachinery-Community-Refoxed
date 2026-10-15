package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.recipe.InputScope;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable mapping from full capability occurrences to material input ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public record InputRoutingFacet(long revision, List<Source> sources, List<Port> inputPorts)
        implements CapabilityFacet {
    public InputRoutingFacet {
        sources = List.copyOf(sources);
        inputPorts = inputPorts.stream().sorted(Comparator.comparingInt((Port port) -> port.position().getX())
                .thenComparingInt(port -> port.position().getY())
                .thenComparingInt(port -> port.position().getZ())).toList();
    }

    /** @author howxu <dev@howxu.cn> */
    public record Source(int capabilityIndex, BlockPos position) {
        public Source {
            if (capabilityIndex < 0) throw new IllegalArgumentException("capability index must be non-negative");
            position = position.immutable();
        }
    }

    /** @author howxu <dev@howxu.cn> */
    public record Port(BlockPos position, boolean isolated) {
        public Port {
            position = position.immutable();
        }
    }

    public InputScope ordinaryScope() {
        return new InputScope(InputScope.Kind.ORDINARY, inputPorts.stream().filter(port -> !port.isolated())
                .map(Port::position).toList());
    }

    public List<InputScope> scopes(boolean materialInputs) {
        if (!materialInputs) return List.of(ordinaryScope());
        List<InputScope> scopes = new ArrayList<>();
        for (Port port : inputPorts) {
            if (port.isolated()) scopes.add(new InputScope(InputScope.Kind.ISOLATED, List.of(port.position())));
        }
        scopes.add(ordinaryScope());
        return List.copyOf(scopes);
    }

    public boolean allows(InputScope scope, int capabilityIndex) {
        if (scope.kind() == InputScope.Kind.LEGACY) return true;
        return sources.stream().anyMatch(source -> source.capabilityIndex() == capabilityIndex
                && scope.sources().contains(source.position()));
    }
}
