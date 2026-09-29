package cn.howxu.mmcr.api.capability.async;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * An immutable capability state captured on the main thread for asynchronous planning.
 *
 * @author howxu <dev@howxu.cn>
 */
public sealed interface AsyncCapabilitySnapshot permits AsyncCapabilitySnapshot.Resource, AsyncCapabilitySnapshot.Scalar,
        AsyncCapabilitySnapshot.Heat {
    /**
     * A resource capability snapshot.
     *
     * @param capabilityId capability type identifier
      * @param slots immutable slot contents in planning order
     */
    record Resource(ResourceLocation capabilityId, List<ResourceSlot> slots) implements AsyncCapabilitySnapshot {
        public Resource {
            Objects.requireNonNull(capabilityId, "capabilityId");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        }
    }

    /**
     * A scalar capability snapshot, such as energy.
     *
     * @param capabilityId capability type identifier
     * @param amount stored scalar amount
     * @param capacity scalar capacity
     * @param transferLimit maximum scalar amount transferable in one storage operation
     */
    record Scalar(ResourceLocation capabilityId, long amount, long capacity, long transferLimit)
            implements AsyncCapabilitySnapshot {
        public Scalar {
            Objects.requireNonNull(capabilityId, "capabilityId");
            if (amount < 0L || capacity < 0L || amount > capacity || transferLimit < 0L) {
                throw new IllegalArgumentException("scalar amount must be within capacity and transfer limit non-negative");
            }
        }
    }

    /** Immutable Mekanism heat values used for temperature checks and heat output planning. */
    record Heat(ResourceLocation capabilityId, double heat, double temperature, double capacity)
            implements AsyncCapabilitySnapshot {
        public Heat {
            Objects.requireNonNull(capabilityId, "capabilityId");
            if (!Double.isFinite(heat) || !Double.isFinite(temperature) || !Double.isFinite(capacity)
                    || heat < 0D || capacity <= 0D) {
                throw new IllegalArgumentException("heat snapshot values must be finite and non-negative");
            }
        }
    }

    /**
     * Immutable contents and capacity of a resource storage slot.
     *
     * @param resource resource value
     * @param amount stored amount
     * @param capacity slot capacity
     */
    record ResourceSlot(Optional<AsyncResourceValue> resource, long amount, long capacity) {
        public ResourceSlot {
            Objects.requireNonNull(resource, "resource");
            if (amount < 0L || capacity < 0L || amount > capacity) {
                throw new IllegalArgumentException("slot amounts must be within capacity");
            }
            if (resource.isEmpty() && amount != 0L) {
                throw new IllegalArgumentException("empty resource slots cannot contain an amount");
            }
        }
    }
}
