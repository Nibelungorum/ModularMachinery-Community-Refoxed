package cn.howxu.mmcr.api.capability.async;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * An immutable capability operation that may be planned off the main thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public sealed interface AsyncCapabilityOperation permits AsyncCapabilityOperation.Resource, AsyncCapabilityOperation.Scalar,
        AsyncCapabilityOperation.Heat, AsyncCapabilityOperation.Group {
    ResourceLocation capabilityId();

    /**
     * A resource insertion or extraction for one storage slot.
     *
     * @param capabilityId capability type identifier
     * @param slot storage slot index
     * @param resource immutable resource value
     * @param amount resource amount
     * @param insert whether the resource is inserted rather than extracted
     */
    record Resource(ResourceLocation capabilityId, int slot, AsyncResourceValue resource, long amount, boolean insert)
            implements AsyncCapabilityOperation {
        public Resource {
            if (slot < 0 || amount <= 0L) {
                throw new IllegalArgumentException("slot must be non-negative and amount must be positive");
            }
            Objects.requireNonNull(capabilityId, "capabilityId");
            Objects.requireNonNull(resource, "resource");
        }
    }

    /**
     * An ordered, atomic group of logical operations.
     *
     * @param operations immutable operations for one capability to commit in order
     */
    record Group(List<AsyncCapabilityOperation> operations) implements AsyncCapabilityOperation {
        public Group {
            operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
            if (operations.isEmpty()) throw new IllegalArgumentException("operation group must not be empty");
            if (operations.stream().anyMatch(Group.class::isInstance)) {
                throw new IllegalArgumentException("operation groups must not be nested");
            }
            ResourceLocation groupCapabilityId = operations.getFirst().capabilityId();
            if (operations.stream().map(AsyncCapabilityOperation::capabilityId)
                    .anyMatch(capabilityId -> !capabilityId.equals(groupCapabilityId))) {
                throw new IllegalArgumentException("operation group capabilities must match");
            }
        }

        @Override
        public ResourceLocation capabilityId() {
            return operations.getFirst().capabilityId();
        }
    }

    /**
     * A scalar insertion or extraction, such as energy.
     *
     * @param capabilityId capability type identifier
     * @param amount scalar amount
     * @param insert whether the scalar is inserted rather than extracted
     */
    record Scalar(ResourceLocation capabilityId, long amount, boolean insert) implements AsyncCapabilityOperation {
        public Scalar {
            Objects.requireNonNull(capabilityId, "capabilityId");
            if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
        }
    }

    /** A validated minimum-temperature check or output-heat mutation. */
    record Heat(ResourceLocation capabilityId, double value, boolean minimumTemperature, long accountingAmount)
            implements AsyncCapabilityOperation {
        public Heat {
            Objects.requireNonNull(capabilityId, "capabilityId");
            if (!Double.isFinite(value) || value < 0D || accountingAmount <= 0L) {
                throw new IllegalArgumentException("heat operation values must be finite and positive");
            }
        }
    }
}
