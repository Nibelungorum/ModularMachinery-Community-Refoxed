package cn.howxu.mmcr.api.capability.async;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Plans a logical capability operation from immutable values on a worker thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public sealed interface AsyncCapabilityPlanner permits AsyncCapabilityPlanner.Resource, AsyncCapabilityPlanner.Scalar,
        AsyncCapabilityPlanner.Heat {
    /**
     * Produces an operation supported by the captured snapshot and request.
     *
     * @param snapshot immutable capability values captured on the server thread
     * @param request immutable requested capability operation
     * @return a logical operation when the request is supported
     */
    Optional<AsyncCapabilityOperation> plan(AsyncCapabilitySnapshot snapshot, AsyncCapabilityRequest request);

    /**
     * A pure planner for slot-based resource capabilities.
     *
     * @param capabilityId capability type identifier
     */
    record Resource(ResourceLocation capabilityId) implements AsyncCapabilityPlanner {
        public Resource {
            Objects.requireNonNull(capabilityId, "capabilityId");
        }

        @Override
        public Optional<AsyncCapabilityOperation> plan(AsyncCapabilitySnapshot snapshot, AsyncCapabilityRequest request) {
            if (!(snapshot instanceof AsyncCapabilitySnapshot.Resource(
                    ResourceLocation id, List<AsyncCapabilitySnapshot.ResourceSlot> slots1
            ))
                    || !(request instanceof AsyncCapabilityRequest.Resource resourceRequest)
                    || !capabilityId.equals(id)
                    || !capabilityId.equals(resourceRequest.capabilityId())) {
                return Optional.empty();
            }

            List<AsyncCapabilitySnapshot.ResourceSlot> slots = new ArrayList<>(slots1);
            List<AsyncCapabilityOperation> operations = new ArrayList<>(resourceRequest.actions().size());
            for (AsyncResourceAction action : resourceRequest.actions()) {
                List<AsyncCapabilityOperation.Resource> actionOperations = planAction(slots, action);
                if (actionOperations == null) {
                    return Optional.empty();
                }
                operations.addAll(actionOperations);
            }
            return Optional.of(new AsyncCapabilityOperation.Group(operations));
        }

        private List<AsyncCapabilityOperation.Resource> planAction(List<AsyncCapabilitySnapshot.ResourceSlot> slots,
                                                                     AsyncResourceAction action) {
            long remaining = action.amount();
            List<AsyncCapabilityOperation.Resource> operations = new ArrayList<>();
            for (int slot = 0; slot < slots.size(); slot++) {
                AsyncCapabilitySnapshot.ResourceSlot stored = slots.get(slot);
                if (action.insert() && (stored.resource().isEmpty() || stored.resource().get().equals(action.resource()))) {
                    long amount = Math.min(remaining, stored.capacity() - stored.amount());
                    if (amount > 0L) {
                        slots.set(slot, new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(action.resource()),
                                stored.amount() + amount, stored.capacity()));
                        operations.add(new AsyncCapabilityOperation.Resource(capabilityId, slot, action.resource(), amount,
                                true));
                        remaining -= amount;
                    }
                } else if (!action.insert() && stored.resource().filter(action.resource()::equals).isPresent()) {
                    long amount = Math.min(remaining, stored.amount());
                    if (amount > 0L) {
                        long storedRemaining = stored.amount() - amount;
                        slots.set(slot, new AsyncCapabilitySnapshot.ResourceSlot(
                                storedRemaining == 0L ? Optional.empty() : stored.resource(), storedRemaining,
                                stored.capacity()));
                        operations.add(new AsyncCapabilityOperation.Resource(capabilityId, slot, action.resource(), amount,
                                false));
                        remaining -= amount;
                    }
                }
                if (remaining == 0L) return operations;
            }
            return null;
        }
    }

    /**
     * A pure planner for scalar capabilities such as energy.
     *
     * @param capabilityId capability type identifier
     */
    record Scalar(ResourceLocation capabilityId) implements AsyncCapabilityPlanner {
        public Scalar {
            Objects.requireNonNull(capabilityId, "capabilityId");
        }

        @Override
        public Optional<AsyncCapabilityOperation> plan(AsyncCapabilitySnapshot snapshot, AsyncCapabilityRequest request) {
            if (!(snapshot instanceof AsyncCapabilitySnapshot.Scalar(
                    ResourceLocation capabilityId1, long amount2, long capacity, long transferLimit
            ))
                    || !(request instanceof AsyncCapabilityRequest.Scalar(
                    ResourceLocation id, long parallelism, long amount1, boolean insert
            ))
                    || !capabilityId.equals(capabilityId1)
                    || !capabilityId.equals(id)) {
                return Optional.empty();
            }

            long available = insert
                    ? capacity - amount2
                    : amount2;
            long maximum = scaled(transferLimit, parallelism);
            long amount = Math.min(amount1, Math.min(available, maximum));
            if (amount <= 0L) return Optional.empty();

            List<AsyncCapabilityOperation> operations = new ArrayList<>();
            while (amount > 0L) {
                long operationAmount = Math.min(amount, transferLimit);
                operations.add(new AsyncCapabilityOperation.Scalar(capabilityId, operationAmount, insert));
                amount -= operationAmount;
            }
            return Optional.of(operations.size() == 1 ? operations.getFirst() : new AsyncCapabilityOperation.Group(operations));
        }

        private static long scaled(long amount, long multiplier) {
            return amount > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : amount * multiplier;
        }
    }

    /** Pure planner for Mekanism heat checks and output mutations. */
    record Heat(ResourceLocation capabilityId) implements AsyncCapabilityPlanner {
        public Heat {
            Objects.requireNonNull(capabilityId, "capabilityId");
        }

        @Override
        public Optional<AsyncCapabilityOperation> plan(AsyncCapabilitySnapshot snapshot,
                                                        AsyncCapabilityRequest request) {
            if (!(snapshot instanceof AsyncCapabilitySnapshot.Heat heatSnapshot)
                    || !(request instanceof AsyncCapabilityRequest.Heat heatRequest)
                    || !capabilityId.equals(heatSnapshot.capabilityId())
                    || !capabilityId.equals(heatRequest.capabilityId())) return Optional.empty();
            if (heatRequest.minimumTemperature() && heatSnapshot.temperature() < heatRequest.value()) {
                return Optional.empty();
            }
            return Optional.of(new AsyncCapabilityOperation.Heat(capabilityId, heatRequest.value(),
                    heatRequest.minimumTemperature(), heatRequest.accountingAmount()));
        }
    }
}
