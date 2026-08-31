package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
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
            return new FluidRequirement(requirement.io(), requirement.fluid(), amount, requirement.stack(), chance,
                    requirement.tags());
        }
        FluidStack stack = requirement.stack().copy();
        stack.setAmount(IntegrationTypeHelper.asInt(
                IntegrationTypeHelper.applyFluidOutput(modifiers, stack.getAmount())));
        float chance = IntegrationTypeHelper.applyFluidOutputChance(modifiers, requirement.chance());
        return new FluidRequirement(requirement.io(), requirement.fluid(), requirement.amount(), stack, chance,
                requirement.tags());
    }

    @Override
    public FluidRequirement applyLevelModifiers(FluidRequirement requirement, double energyMultiplier,
                                                double outputMultiplier) {
        if (requirement.io() != RecipeModifier.IOType.OUTPUT) return requirement;
        FluidStack stack = requirement.stack().copy();
        stack.setAmount(levelOutputAmount(stack.getAmount(), outputMultiplier));
        return new FluidRequirement(requirement.io(), requirement.fluid(), requirement.amount(), stack,
                requirement.chance(), requirement.tags());
    }

    @Override
    public MachineIngredient legacyInput(FluidRequirement requirement) {
        return requirement.io() == RecipeModifier.IOType.INPUT
                ? new MachineIngredient.FluidIngredient(requirement.fluid(), requirement.amount()) : null;
    }

    @Override
    public FluidStack legacyFluidOutput(FluidRequirement requirement) {
        return requirement.io() == RecipeModifier.IOType.OUTPUT ? requirement.stack().copy() : null;
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
        if (requirement.io() == RecipeModifier.IOType.OUTPUT
                && !RequirementHandlerSupport.shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        }
        boolean allowPartialOutput = requirement.io() == RecipeModifier.IOType.OUTPUT
                && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = fluidMaximum(requirement, capabilities, parallelism, allowPartialOutput);
        if (maximum <= 0) {
            return requirement.io() == RecipeModifier.IOType.OUTPUT
                    ? RequirementHandlerSupport.blockedOutputPlan(requirement, context, "no_output_capacity",
                    requestedAmount(requirement, context.requestedParallelism()))
                    : RequirementHandlerSupport.blockedPlan(requirement, context, "insufficient_resource");
        }
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && requirement.stack().isEmpty()) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        return RequirementHandlerSupport.deferredPlan(context, maximum,
                (finalParallelism, reservations) -> planOperations(requirement, capabilities, finalParallelism,
                        reservations, allowPartialOutput, true),
                RequirementHandlerSupport.reservationFactory((finalParallelism, reservations) -> planOperations(
                        requirement, capabilities, finalParallelism, reservations,
                        allowPartialOutput, false)));
    }

    @Override
    public List<ResourceWakeup> resourceWakeups(FluidRequirement requirement) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            Predicate<Object> matcher = fluidMatcher(requirement);
            return matcher == null ? List.of() : List.of(new ResourceWakeup(
                    Set.of("insufficient_resource", "per_tick"), WakeupReason.INPUT_AVAILABLE, matcher));
        }
        Predicate<Object> matcher = outputFluidMatcher(requirement);
        return matcher == null ? List.of() : List.of(new ResourceWakeup(
                Set.of("insufficient_resource", "no_output_capacity", "finish"),
                WakeupReason.OUTPUT_CAPACITY, matcher));
    }

    private static long fluidMaximum(FluidRequirement requirement, List<MachineCapability> capabilities,
                                     long requested, boolean allowPartialOutputs) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            if (requirement.fluid() == null || requirement.amount() <= 0) return 0;
            long available = 0L;
            for (MachineCapability capability : capabilities) {
                ResourceStorage<?> storage = RequirementHandlerSupport.resourceStorage(capability, FluidResource.class);
                if (storage == null) continue;
                for (int slot = 0; slot < storage.size(); slot++) {
                    if (!(storage.resource(slot) instanceof FluidResource resource) || storage.amount(slot) <= 0L) continue;
                    if (requirement.fluid().test(resource.toStack((int) Math.min(storage.amount(slot), Integer.MAX_VALUE)))) {
                        available = RequirementHandlerSupport.saturatingAdd(available, storage.amount(slot));
                    }
                }
            }
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
            for (int slot = 0; slot < storage.size(); slot++) {
                Object current = storage.resource(slot);
                if (current instanceof FluidResource existing && !existing.isEmpty() && !existing.equals(resource)) continue;
                if (!storage.isValidResource(slot, resource)) continue;
                capacity = RequirementHandlerSupport.saturatingAdd(capacity,
                        Math.max(0L, storage.capacityResource(slot, resource) - storage.amount(slot)));
            }
        }
        if (allowPartialOutputs) return capacity > 0L ? requested : 0;
        long maximum = Math.min(requested, capacity / stack.getAmount());
        return maximum > 0 || capacity <= 0L ? maximum : 1;
    }

    private static RequirementPlan.OperationPlan planOperations(FluidRequirement requirement,
                                                                List<MachineCapability> capabilities,
                                                                long parallelism,
                                                                PlanningReservations reservations,
                                                                boolean allowPartialOutputs,
                                                                boolean materialize) {
        long amount = requirement.io() == RecipeModifier.IOType.INPUT
                ? RequirementHandlerSupport.scaled(requirement.amount(), parallelism)
                : RequirementHandlerSupport.scaled(requirement.stack().getAmount(), parallelism);
        if (amount <= 0L) return new RequirementPlan.OperationPlan(List.of(), null);
        long requestedAmount = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? requestedAmount(requirement, parallelism) : 0L;
        FluidResource requestedResource = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? FluidResource.of(requirement.stack()) : null;
        Map<MachineCapability, List<CapabilityRequests.ResourceAction<FluidResource>>> actionMap = new LinkedHashMap<>();
        long remaining = amount;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<?> storage = RequirementHandlerSupport.resourceStorage(capability, FluidResource.class);
            if (storage == null) continue;
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

    private static long requestedAmount(FluidRequirement requirement, long parallelism) {
        return RequirementHandlerSupport.scaled(requirement.stack().getAmount(), parallelism);
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
