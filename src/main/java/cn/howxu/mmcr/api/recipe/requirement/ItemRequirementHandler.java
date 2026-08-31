package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Plans built-in item requirements and owns their resource wakeups.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ItemRequirementHandler implements RequirementHandler<ItemRequirement> {
    @Override
    public RequirementPlan plan(ItemRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        long parallelism = context.requestedParallelism();
        if (requirement.io() == RecipeModifier.IOType.OUTPUT
                && !RequirementHandlerSupport.shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        }
        if (requirement.io() == RecipeModifier.IOType.INPUT && requirement.item() == null) {
            return RequirementHandlerSupport.blockedPlan(requirement, context, "missing_item_ingredient");
        }
        boolean allowPartialOutput = requirement.io() == RecipeModifier.IOType.OUTPUT
                && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = itemMaximum(requirement, capabilities, parallelism, allowPartialOutput);
        if (maximum <= 0) {
            return requirement.io() == RecipeModifier.IOType.OUTPUT
                    ? RequirementHandlerSupport.blockedOutputPlan(requirement, context, "no_output_capacity",
                    requestedAmount(requirement, context.requestedParallelism()))
                    : RequirementHandlerSupport.blockedPlan(requirement, context, "insufficient_resource");
        }
        if (requirement.io() == RecipeModifier.IOType.INPUT && requirement.consumeChance() <= 0F) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && requirement.stack(null).isEmpty()) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        RequirementHandlerSupport.ConsumeProfile consumed = requirement.io() == RecipeModifier.IOType.INPUT
                ? RequirementHandlerSupport.consumeProfile(requirement.consumeChance(), parallelism) : null;
        return RequirementHandlerSupport.deferredPlan(context, maximum,
                (finalParallelism, reservations) -> planOperations(requirement, capabilities, finalParallelism,
                        consumed, reservations, allowPartialOutput, true),
                RequirementHandlerSupport.reservationFactory((finalParallelism, reservations) -> planOperations(
                        requirement, capabilities, finalParallelism, consumed, reservations,
                        allowPartialOutput, false)));
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(ItemRequirement requirement) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            return List.of(new ResourceWakeup(Set.of("insufficient_resource", "per_tick"),
                    WakeupReason.INPUT_AVAILABLE, itemMatcher(requirement)));
        }
        Predicate<Object> matcher = outputItemMatcher(requirement);
        return matcher == null ? List.of() : List.of(new ResourceWakeup(
                Set.of("insufficient_resource", "no_output_capacity", "finish"),
                WakeupReason.OUTPUT_CAPACITY, matcher));
    }

    private static long itemMaximum(ItemRequirement requirement, List<MachineCapability> capabilities,
                                    long requested, boolean allowPartialOutputs) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            long available = matchingItemAmount(requirement, capabilities);
            if (requirement.count() <= 0) return requested;
            return Math.min(requested, available / requirement.count());
        }
        ItemStack stack = requirement.stack(null);
        if (allowPartialOutputs && stack.isEmpty()) return 0;
        if (stack.isEmpty() || stack.getCount() <= 0) return requested;
        long capacity = 0L;
        ItemResource resource = ItemResource.of(stack);
        for (MachineCapability capability : capabilities) {
            ResourceStorage<?> storage = RequirementHandlerSupport.resourceStorage(capability, ItemResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                Object current = storage.resource(slot);
                if (current instanceof ItemResource existing && !existing.isEmpty() && !existing.equals(resource)) continue;
                if (!storage.isValidResource(slot, resource)) continue;
                long slotCapacity = storage.capacityResource(slot, resource);
                if (!(capability instanceof ItemBusCapability itemBus) || !itemBus.supportsLargeStacks()) {
                    slotCapacity = Math.min(slotCapacity, stack.getMaxStackSize());
                }
                capacity = RequirementHandlerSupport.saturatingAdd(capacity,
                        Math.max(0L, slotCapacity - storage.amount(slot)));
            }
        }
        if (allowPartialOutputs) return capacity > 0L ? requested : 0;
        long maximum = Math.min(requested, capacity / stack.getCount());
        return maximum > 0 || capacity <= 0L ? maximum : 1;
    }

    private static RequirementPlan.OperationPlan planOperations(ItemRequirement requirement,
                                                                List<MachineCapability> capabilities,
                                                                long parallelism,
                                                                RequirementHandlerSupport.ConsumeProfile consumed,
                                                                PlanningReservations reservations,
                                                                boolean allowPartialOutputs,
                                                                boolean materialize) {
        long batches = requirement.io() == RecipeModifier.IOType.INPUT
                ? consumed.consumedBatches(parallelism) : parallelism;
        long amount = requirement.io() == RecipeModifier.IOType.INPUT
                ? RequirementHandlerSupport.scaled(requirement.count(), batches)
                : RequirementHandlerSupport.scaled(requirement.stack(null).getCount(), parallelism);
        if (amount <= 0L) return new RequirementPlan.OperationPlan(List.of(), null);
        long requestedAmount = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? requestedAmount(requirement, parallelism) : 0L;
        ItemResource requestedResource = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? ItemResource.of(requirement.stack(null)) : null;
        int stackLimit = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? requirement.stack(null).getMaxStackSize() : 0;
        Map<MachineCapability, List<CapabilityRequests.ResourceAction<ItemResource>>> actionMap = new LinkedHashMap<>();
        long remaining = amount;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<?> storage = RequirementHandlerSupport.resourceStorage(capability, ItemResource.class);
            if (storage == null) continue;
            List<CapabilityRequests.ResourceAction<ItemResource>> actions = new ArrayList<>();
            for (int slot = 0; slot < storage.size() && remaining > 0L; slot++) {
                Object current = reservations.resource(storage, slot);
                long currentAmount = reservations.amount(storage, slot);
                if (requirement.io() == RecipeModifier.IOType.INPUT) {
                    if (currentAmount <= 0L || !(current instanceof ItemResource resource)
                            || !matchesItem(requirement, resource)) continue;
                    long moved = Math.min(remaining, currentAmount);
                    if (reservations.reserveExtract(storage, slot, resource, moved)) {
                        actions.add(new CapabilityRequests.ResourceAction<>(slot, resource, moved, false));
                        remaining -= moved;
                    }
                } else {
                    if (current instanceof ItemResource resource && !resource.isEmpty()
                            && !resource.equals(requestedResource)) continue;
                    if (!storage.isValidResource(slot, requestedResource)) continue;
                    long capacity = storage.capacityResource(slot, requestedResource);
                    if (!(capability instanceof ItemBusCapability itemBus) || !itemBus.supportsLargeStacks()) {
                        capacity = Math.min(capacity, stackLimit);
                    }
                    long moved = Math.min(remaining, Math.max(0L, capacity - currentAmount));
                    if (moved > 0L && reservations.reserveInsert(storage, slot, requestedResource, moved)) {
                        actions.add(new CapabilityRequests.ResourceAction<>(slot, requestedResource, moved, true));
                        remaining -= moved;
                    }
                }
            }
            if (!actions.isEmpty()) actionMap.put(capability, actions);
            if (remaining == 0L) break;
        }
        if (remaining > 0L && !(allowPartialOutputs && requirement.io() == RecipeModifier.IOType.OUTPUT)) {
            return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(requirement,
                    "insufficient_resource"), RequirementHandlerSupport.outputSimulation(
                    requestedAmount, amount - remaining));
        }
        if (actionMap.isEmpty()) return new RequirementPlan.OperationPlan(List.of(),
                RequirementHandlerSupport.blocked(requirement, "no_output_capacity"),
                RequirementHandlerSupport.outputSimulation(requestedAmount, 0L));
        return RequirementHandlerSupport.resourceOperations(actionMap, parallelism, materialize,
                RequirementHandlerSupport.outputSimulation(requestedAmount, amount - remaining));
    }

    private static boolean matchesItem(ItemRequirement requirement, ItemResource resource) {
        ItemStack stack = resource.toStack((int) Math.min(resource.getMaxStackSize(), Integer.MAX_VALUE));
        return requirement.item().test(stack) && requirement.components().matches(stack);
    }

    private static long matchingItemAmount(ItemRequirement requirement, List<MachineCapability> capabilities) {
        long amount = 0;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<?> storage = RequirementHandlerSupport.resourceStorage(capability, ItemResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                if (storage.resource(slot) instanceof ItemResource resource
                        && !resource.isEmpty() && matchesItem(requirement, resource)) {
                    amount = RequirementHandlerSupport.saturatingAdd(amount, storage.amount(slot));
                }
            }
        }
        return amount;
    }

    private static long requestedAmount(ItemRequirement requirement, long parallelism) {
        return RequirementHandlerSupport.scaled(requirement.stack(null).getCount(), parallelism);
    }

    private static Predicate<Object> itemMatcher(ItemRequirement requirement) {
        return resource -> resource instanceof ItemResource item
                && requirement.item() != null
                && requirement.item().test(item.toStack(Math.min(item.getMaxStackSize(), Integer.MAX_VALUE)))
                && requirement.components().matches(item.toStack(Math.min(item.getMaxStackSize(), Integer.MAX_VALUE)));
    }

    private static Predicate<Object> outputItemMatcher(ItemRequirement requirement) {
        ItemStack stack = requirement.stack(null);
        if (stack.isEmpty()) return null;
        return ItemResource.of(stack)::equals;
    }
}
