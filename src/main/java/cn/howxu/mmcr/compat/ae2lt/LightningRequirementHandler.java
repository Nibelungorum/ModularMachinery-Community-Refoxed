package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.compat.ae2lt.LightningViewFacet;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.util.IOType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Both tiers share the existing native slot/key reservations with item/fluid/chemical views.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningRequirementHandler implements RequirementHandler<LightningRequirement> {
    @Override public RequirementPlan plan(LightningRequirement requirement, List<MachineCapability> capabilities,
                                         PlanningContext context) {
        if (!LightningBridge.get().available()) return RequirementHandlerSupport.blockedPlan(requirement, context, LightningFailureReasons.UNAVAILABLE);
        boolean output = requirement.io() == RecipeModifier.IOType.OUTPUT;
        long requested = context.requestedParallelism();
        if (output && !RequirementHandlerSupport.shouldProduce(requirement.chance(), context)) {
            return new RequirementPlan(context.requirementIndex(), requested, List.of(), null);
        }
        List<MachineCapability> ports = output ? RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities) : capabilities;
        boolean partial = output && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long available = 0L;
        for (MachineCapability capability : ports) {
            LightningStorage storage = storage(capability);
            if (storage == null) continue;
            if (output) available = RequirementHandlerSupport.saturatingAdd(available,
                    storage.reserveOutput(requirement.tier(), RequirementHandlerSupport.scaled(requirement.amount(), requested),
                            new PlanningReservations()));
            else for (int slot = 0; slot < storage.size(); slot++) {
                if (storage.key(requirement.tier()).equals(storage.storedKey(slot))) {
                    available = RequirementHandlerSupport.saturatingAdd(available, storage.amount(slot));
                }
            }
        }
        long maximum = partial ? available > 0L ? requested : 0L : Math.min(requested, available / requirement.amount());
        if (output && maximum == 0L && available > 0L) maximum = 1L;
        if (maximum == 0L) return output
                ? RequirementHandlerSupport.blockedOutputPlan(requirement, context, LightningFailureReasons.OUTPUT_BLOCKED,
                        RequirementHandlerSupport.scaled(requirement.amount(), requested))
                : RequirementHandlerSupport.blockedPlan(requirement, context, LightningFailureReasons.INPUT_MISSING);
        return RequirementHandlerSupport.deferredPlan(context, maximum,
                (parallelism, reservations) -> operations(requirement, ports, context, parallelism, reservations, partial, true),
                RequirementHandlerSupport.reservationFactory((parallelism, reservations) ->
                        operations(requirement, ports, context, parallelism, reservations, partial, false)));
    }

    private RequirementPlan.OperationPlan operations(LightningRequirement requirement, List<MachineCapability> ports,
            PlanningContext context, long parallelism, PlanningReservations reservations, boolean partial, boolean materialize) {
        boolean output = requirement.io() == RecipeModifier.IOType.OUTPUT;
        long requested = RequirementHandlerSupport.scaled(requirement.amount(), parallelism);
        long remaining = requested;
        List<CapabilityOperation> operations = new ArrayList<>();
        for (MachineCapability capability : ports) {
            LightningStorage storage = storage(capability);
            if (storage == null) continue;
            List<CapabilityRequests.ResourceAction<LightningTier>> actions = new ArrayList<>();
            if (output) {
                long moved = storage.reserveOutput(requirement.tier(), remaining, reservations);
                if (moved > 0L) actions.add(new CapabilityRequests.ResourceAction<>(-1, requirement.tier(), moved, true));
                remaining -= moved;
            } else for (int slot = 0; slot < storage.size() && remaining > 0L; slot++) {
                Object key = storage.key(requirement.tier());
                Object current = reservations.nativeKey(storage.identity(), storage.address(slot), storage.storedKey(slot));
                if (!key.equals(current)) continue;
                long amount = reservations.nativeAmount(storage.identity(), storage.address(slot), storage.amount(slot));
                long moved = Math.min(remaining, amount);
                if (moved > 0L && reservations.reserveNativeExtract(storage.identity(), storage.address(slot), key,
                        storage.storedKey(slot), storage.amount(slot), moved)) {
                    actions.add(new CapabilityRequests.ResourceAction<>(slot, requirement.tier(), moved, false));
                    remaining -= moved;
                }
            }
            if (materialize && !actions.isEmpty()) {
                operations.add(capability.prepare(new CapabilityRequests.ResourceRequest<>(capability.type(),
                        output ? IOType.OUTPUT : IOType.INPUT, parallelism, actions)));
            }
            if (remaining == 0L) break;
        }
        var simulation = output ? RequirementHandlerSupport.outputSimulation(requested, requested - remaining) : null;
        if (remaining > 0L && (!partial || remaining == requested)) {
            return new RequirementPlan.OperationPlan(List.of(), RequirementHandlerSupport.blocked(requirement, context,
                    output ? LightningFailureReasons.OUTPUT_BLOCKED : LightningFailureReasons.INPUT_MISSING,
                    Map.of("required", Long.toString(requested), "available", Long.toString(requested - remaining),
                            "shortfall", Long.toString(remaining))), simulation);
        }
        return new RequirementPlan.OperationPlan(List.copyOf(operations), null, simulation);
    }

    private static LightningStorage storage(MachineCapability capability) {
        return capability.facet(LightningViewFacet.class).map(LightningViewFacet::storage).orElse(null);
    }

    @Override public LightningRequirement applyModifiers(LightningRequirement requirement, List<RecipeModifier> modifiers) {
        double adjusted = RecipeModifier.applyModifiers(modifiers, "", requirement.io(), (double) requirement.amount(), false);
        long amount = Double.isNaN(adjusted) || adjusted <= 1D ? 1L
                : adjusted >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (long) Math.floor(adjusted);
        float chance = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? MachineOutput.clampChance(RecipeModifier.applyModifiers(modifiers, "", requirement.io(), requirement.chance(), true)) : 1F;
        return new LightningRequirement(requirement.tier(), requirement.io(), amount, chance, requirement.tags());
    }

    @Override public LightningRequirement applyLevelModifiers(LightningRequirement requirement, double energyMultiplier, double outputMultiplier) {
        if (requirement.io() != RecipeModifier.IOType.OUTPUT) return requirement;
        if (outputMultiplier <= 0D) return new LightningRequirement(requirement.tier(), requirement.io(), requirement.amount(), 0F, requirement.tags());
        long amount = Math.max(1L, Math.min(Integer.MAX_VALUE, (long) Math.floor(requirement.amount() * outputMultiplier)));
        return new LightningRequirement(requirement.tier(), requirement.io(), amount, requirement.chance(), requirement.tags());
    }

    @Override public boolean overlaps(LightningRequirement requirement, MachineRequirement other) {
        return requirement.io() == RecipeModifier.IOType.INPUT && other instanceof LightningRequirement lightning
                && lightning.io() == RecipeModifier.IOType.INPUT && requirement.tier() == lightning.tier();
    }

    @Override public List<ResourceWakeup> resourceWakeups(LightningRequirement requirement) {
        boolean output = requirement.io() == RecipeModifier.IOType.OUTPUT;
        return List.of(new ResourceWakeup(output
                ? Set.of(BuiltinFailureReasons.MISSING_OUTPUT.id(), BuiltinFailureReasons.FINISH.id(), LightningFailureReasons.OUTPUT_BLOCKED.id())
                : Set.of(BuiltinFailureReasons.MISSING_INPUT.id(), BuiltinFailureReasons.PER_TICK.id(), LightningFailureReasons.INPUT_MISSING.id()),
                output ? WakeupReason.OUTPUT_CAPACITY : WakeupReason.INPUT_AVAILABLE, resource -> resource == requirement.tier()));
    }
}
