package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.internal.recipe.OutputResourceStorage;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Plans built-in fluid requirements and owns their resource wakeups.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluidRequirementHandler implements RequirementHandler<FluidRequirement> {
    @Override
    public FluidRequirement applyModifiers(FluidRequirement requirement, List<RecipeModifier> modifiers) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            int amount = IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidInput(modifiers, requirement.amount()));
            float chance = IntegrationTypeHelper.applyFluidInputChance(modifiers, requirement.chance());
            float consumeChance = IntegrationTypeHelper.applyFluidInputChance(modifiers, requirement.consumeChance());
            return new FluidRequirement(requirement.io(), requirement.fluid(), amount, requirement.stack(), chance,
                    requirement.tags(), consumeChance);
        }
        FluidStack stack = requirement.stack().copy();
        stack.setAmount(IntegrationTypeHelper.asInt(
                IntegrationTypeHelper.applyFluidOutput(modifiers, stack.getAmount())));
        float chance = IntegrationTypeHelper.applyFluidOutputChance(modifiers, requirement.chance());
        return new FluidRequirement(requirement.io(), requirement.fluid(), requirement.amount(), stack, chance,
                requirement.tags(), requirement.consumeChance());
    }

    @Override
    public FluidRequirement applyLevelModifiers(FluidRequirement requirement, double energyMultiplier,
                                                double outputMultiplier) {
        if (requirement.io() != RecipeModifier.IOType.OUTPUT) return requirement;
        FluidStack stack = requirement.stack().copy();
        stack.setAmount(levelOutputAmount(stack.getAmount(), outputMultiplier));
        return new FluidRequirement(requirement.io(), requirement.fluid(), requirement.amount(), stack,
                requirement.chance(), requirement.tags(), requirement.consumeChance());
    }

    private static int levelOutputAmount(int original, double multiplier) {
        if (multiplier <= 0D) return 0;
        if (multiplier >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        int result = (int) Math.floor(original * multiplier);
        return original > 0 ? Math.max(1, result) : result;
    }

    @Override
    public RequirementPlan plan(FluidRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        long parallelism = context.requestedParallelism();
        List<MachineCapability> plannedCapabilities = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities) : capabilities;
        if (requirement.io() == RecipeModifier.IOType.OUTPUT
                && !RequirementHandlerSupport.shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        }
        boolean allowPartialOutput = requirement.io() == RecipeModifier.IOType.OUTPUT
                && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        if (capabilities.stream().anyMatch(capability -> capability.facet(FluidHandlerFacet.class).isPresent())) {
            return NativeRequirementPlanning.fluid(requirement, capabilities, context);
        }
        IOType direction = IOType.valueOf(requirement.io().name());
        long maximum = fluidMaximum(requirement, plannedCapabilities, parallelism, allowPartialOutput);
        if (maximum <= 0) {
            return requirement.io() == RecipeModifier.IOType.OUTPUT
                    ? RequirementHandlerSupport.blockedOutputPlan(requirement, context,
                    BuiltinFailureReasons.MISSING_OUTPUT,
                    requestedAmount(requirement, context.requestedParallelism()),
                    Map.of("required", Long.toString(requestedAmount(requirement, context.requestedParallelism())),
                            "available", "0", "shortfall",
                            Long.toString(requestedAmount(requirement, context.requestedParallelism()))))
                    : RequirementHandlerSupport.blockedPlan(requirement, context, BuiltinFailureReasons.MISSING_INPUT,
                    Map.of("required", Long.toString(RequirementHandlerSupport.scaled(
                                    requirement.amount(), context.requestedParallelism())),
                            "available", Long.toString(matchingFluidAmount(requirement, capabilities))));
        }
        if (requirement.io() == RecipeModifier.IOType.INPUT && requirement.consumeChance() <= 0F) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && requirement.stack().isEmpty()) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        RequirementHandlerSupport.ConsumeProfile consumed = requirement.io() == RecipeModifier.IOType.INPUT
                ? RequirementHandlerSupport.consumeProfile(requirement.consumeChance(), parallelism) : null;
        return RequirementHandlerSupport.deferredPlan(context, maximum,
                (finalParallelism, reservations) -> planOperations(requirement, plannedCapabilities, finalParallelism,
                        context, consumed, reservations, direction, allowPartialOutput, true),
                RequirementHandlerSupport.reservationFactory((finalParallelism, reservations) -> planOperations(
                        requirement, plannedCapabilities, finalParallelism, context, consumed, reservations,
                        direction, allowPartialOutput, false)));
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(FluidRequirement requirement) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            Predicate<Object> matcher = fluidMatcher(requirement);
            return matcher == null ? List.of() : List.of(new ResourceWakeup(
                    Set.of(BuiltinFailureReasons.MISSING_INPUT.id(), BuiltinFailureReasons.PER_TICK.id()),
                    WakeupReason.INPUT_AVAILABLE, matcher));
        }
        Predicate<Object> matcher = outputFluidMatcher(requirement);
        return matcher == null ? List.of() : List.of(new ResourceWakeup(
                Set.of(BuiltinFailureReasons.MISSING_OUTPUT.id(), BuiltinFailureReasons.FINISH.id()),
                WakeupReason.OUTPUT_CAPACITY, matcher));
    }

    private static long fluidMaximum(FluidRequirement requirement, List<MachineCapability> capabilities,
                                     long requested, boolean allowPartialOutputs) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            if (requirement.fluid() == null || requirement.amount() <= 0) return 0;
            long available = matchingFluidAmount(requirement, capabilities);
            return Math.min(requested, available / requirement.amount());
        }
        FluidStack stack = requirement.stack();
        if (allowPartialOutputs && stack.isEmpty()) return 0;
        if (stack.isEmpty() || stack.getAmount() <= 0) return requested;
        FluidResource resource = FluidResource.of(stack);
        long capacity = 0L;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<?> storage = RequirementHandlerSupport.resourceStorage(capability, FluidResource.class);
            if (storage == null) continue;
            if (storage instanceof OutputResourceStorage<?> outputStorage) {
                capacity = RequirementHandlerSupport.saturatingAdd(capacity,
                        outputCapacity(outputStorage, resource));
            } else {
                for (int slot = 0; slot < storage.size(); slot++) {
                    Object current = storage.resource(slot);
                    if (current instanceof FluidResource existing && !existing.isEmpty() && !existing.equals(resource)) continue;
                    if (!storage.isValidResource(slot, resource)) continue;
                    capacity = RequirementHandlerSupport.saturatingAdd(capacity,
                            Math.max(0L, storage.capacityResource(slot, resource) - storage.amount(slot)));
                }
            }
        }
        if (allowPartialOutputs) return capacity > 0L ? requested : 0;
        long maximum = Math.min(requested, capacity / stack.getAmount());
        return maximum > 0 || capacity <= 0L ? maximum : 1;
    }

    private static RequirementPlan.OperationPlan planOperations(FluidRequirement requirement,
                                                                 List<MachineCapability> capabilities,
                                                                 long parallelism,
                                                                 PlanningContext context,
                                                                 RequirementHandlerSupport.ConsumeProfile consumed,
                                                                 PlanningReservations reservations,
                                                                 IOType direction,
                                                                 boolean allowPartialOutputs,
                                                                 boolean materialize) {
        long batches = requirement.io() == RecipeModifier.IOType.INPUT
                ? consumed.consumedBatches(parallelism) : parallelism;
        long amount = requirement.io() == RecipeModifier.IOType.INPUT
                ? RequirementHandlerSupport.scaled(requirement.amount(), batches)
                : RequirementHandlerSupport.scaled(requirement.stack().getAmount(), parallelism);
        if (amount <= 0L) return new RequirementPlan.OperationPlan(List.of(), null);
        long requestedAmount = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? requestedAmount(requirement, parallelism) : 0L;
        FluidResource requestedResource = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? FluidResource.of(requirement.stack()) : null;
        Map<MachineCapability, List<CapabilityRequests.ResourceAction<FluidResource>>> actionMap = new LinkedHashMap<>();
        List<CapabilityOperation> dynamicOperations = new ArrayList<>();
        long remaining = amount;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<?> storage = RequirementHandlerSupport.resourceStorage(capability, FluidResource.class);
            if (storage == null) continue;
            if (storage instanceof OutputResourceStorage<?> outputStorage
                    && requirement.io() == RecipeModifier.IOType.OUTPUT) {
                OutputResourceStorage.OutputPlan planned = planDynamicOutput(outputStorage, requestedResource,
                        remaining, reservations, materialize);
                long taken = planned.accepted();
                remaining -= taken;
                if (materialize && planned.operation() != null) {
                    dynamicOperations.add(planned.operation());
                }
                if (remaining == 0L) break;
                continue;
            }
            List<CapabilityRequests.ResourceAction<FluidResource>> actions = new ArrayList<>();
            for (int slot = 0; slot < storage.size() && remaining > 0L; slot++) {
                Object current = reservations.resource(storage, slot);
                long currentAmount = reservations.amount(storage, slot);
                if (requirement.io() == RecipeModifier.IOType.INPUT) {
                    if (currentAmount <= 0L || !(current instanceof FluidResource resource)
                            || requirement.fluid() == null
                            || !requirement.fluid().test(resource.toStack((int) Math.min(currentAmount, Integer.MAX_VALUE)))) continue;
                    long moved = Math.min(remaining, currentAmount);
                    if (reservations.reserveExtract(storage, slot, resource, moved)) {
                        actions.add(new CapabilityRequests.ResourceAction<>(slot, resource, moved, false));
                        remaining -= moved;
                    }
                } else {
                    if (current instanceof FluidResource resource && !resource.isEmpty()
                            && !resource.equals(requestedResource)) continue;
                    if (!storage.isValidResource(slot, requestedResource)) continue;
                    long moved = Math.min(remaining,
                            Math.max(0L, storage.capacityResource(slot, requestedResource) - currentAmount));
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
            boolean output = requirement.io() == RecipeModifier.IOType.OUTPUT;
            return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(requirement, context,
                    output ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT,
                    Map.of("required", Long.toString(amount), "available", Long.toString(amount - remaining),
                            "shortfall", Long.toString(remaining))), RequirementHandlerSupport.outputSimulation(
                    requestedAmount, amount - remaining));
        }
        if (amount - remaining == 0L) {
            return new RequirementPlan.OperationPlan(List.of(),
                    RequirementHandlerSupport.blocked(requirement, context, BuiltinFailureReasons.MISSING_OUTPUT,
                            Map.of("required", Long.toString(amount), "available", "0", "shortfall",
                                    Long.toString(amount))),
                    RequirementHandlerSupport.outputSimulation(requestedAmount, 0L));
        }
        return RequirementHandlerSupport.resourceOperations(actionMap, dynamicOperations, direction, parallelism,
                materialize, RequirementHandlerSupport.outputSimulation(requestedAmount, amount - remaining));
    }

    /**
     * Casts a wildcard {@link OutputResourceStorage} to its raw form so the
     * caller can invoke {@code planOutput} with the runtime resource type.
     *
     * @author howxu <dev@howxu.cn>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static OutputResourceStorage.OutputPlan planDynamicOutput(OutputResourceStorage<?> storage,
                                                                      Object resource, long amount,
                                                                      PlanningReservations reservations,
                                                                      boolean materialize) {
        return ((OutputResourceStorage) storage).planOutput(resource, amount, reservations, materialize);
    }

    /**
     * Casts a wildcard {@link OutputResourceStorage} to its raw form so the
     * caller can invoke {@code outputCapacity} with the runtime resource type.
     *
     * @author howxu <dev@howxu.cn>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static long outputCapacity(OutputResourceStorage<?> storage, Object resource) {
        return ((OutputResourceStorage) storage).outputCapacity(resource);
    }

    private static long requestedAmount(FluidRequirement requirement, long parallelism) {
        return RequirementHandlerSupport.scaled(requirement.stack().getAmount(), parallelism);
    }

    private static long matchingFluidAmount(FluidRequirement requirement, List<MachineCapability> capabilities) {
        if (requirement.fluid() == null) return 0L;
        long available = 0L;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<?> storage = RequirementHandlerSupport.resourceStorage(capability, FluidResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                if (!(storage.resource(slot) instanceof FluidResource resource) || storage.amount(slot) <= 0L) continue;
                if (requirement.fluid().test(resource.toStack(
                        (int) Math.min(storage.amount(slot), Integer.MAX_VALUE)))) {
                    available = RequirementHandlerSupport.saturatingAdd(available, storage.amount(slot));
                }
            }
        }
        return available;
    }

    private static Predicate<Object> fluidMatcher(FluidRequirement requirement) {
        if (requirement.fluid() == null) return null;
        return resource -> resource instanceof FluidResource fluid
                && requirement.fluid().test(fluid.toStack(1));
    }

    private static Predicate<Object> outputFluidMatcher(FluidRequirement requirement) {
        if (requirement.stack().isEmpty()) return null;
        return FluidResource.of(requirement.stack())::equals;
    }
}
