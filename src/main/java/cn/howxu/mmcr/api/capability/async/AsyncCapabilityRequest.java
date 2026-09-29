package cn.howxu.mmcr.api.capability.async;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * A worker-safe capability request for asynchronous planning.
 *
 * @author howxu <dev@howxu.cn>
 */
public sealed interface AsyncCapabilityRequest permits AsyncCapabilityRequest.Resource, AsyncCapabilityRequest.Scalar,
        AsyncCapabilityRequest.Heat {
    ResourceLocation capabilityId();

    long parallelism();

    /**
     * A resource request expressed as immutable, ordered resource actions that are planned atomically.
     *
     * @param capabilityId capability type identifier
     * @param parallelism requested parallelism
     * @param actions requested resource actions
     */
    record Resource(ResourceLocation capabilityId, long parallelism, List<AsyncResourceAction> actions)
            implements AsyncCapabilityRequest {
        public Resource {
            Objects.requireNonNull(capabilityId, "capabilityId");
            if (parallelism <= 0L) throw new IllegalArgumentException("parallelism must be positive");
            actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
            if (actions.isEmpty()) throw new IllegalArgumentException("resource actions must not be empty");
        }
    }

    /**
     * A scalar capability request, such as energy.
     *
     * @param capabilityId capability type identifier
     * @param parallelism requested parallelism
     * @param amount requested scalar amount
     * @param insert whether the scalar is inserted rather than extracted
     */
    record Scalar(ResourceLocation capabilityId, long parallelism, long amount, boolean insert) implements AsyncCapabilityRequest {
        public Scalar {
            Objects.requireNonNull(capabilityId, "capabilityId");
            if (parallelism <= 0L || amount <= 0L) {
                throw new IllegalArgumentException("parallelism and amount must be positive");
            }
        }
    }

    /** A minimum-temperature check or output-heat request. */
    record Heat(ResourceLocation capabilityId, long parallelism, double value, boolean minimumTemperature,
                long accountingAmount) implements AsyncCapabilityRequest {
        public Heat {
            Objects.requireNonNull(capabilityId, "capabilityId");
            if (parallelism <= 0L || !Double.isFinite(value) || value < 0D || accountingAmount <= 0L) {
                throw new IllegalArgumentException("heat request values must be finite and positive");
            }
        }
    }
}
