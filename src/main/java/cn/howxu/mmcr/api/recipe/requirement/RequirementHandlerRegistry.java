package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.OutputFit;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.OutputSimulation;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of requirement handlers used by recipe planning.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RequirementHandlerRegistry {
    private static final Map<RequirementType<?>, RequirementHandler<?>> HANDLERS = new ConcurrentHashMap<>();

    static {
        registerBuiltIns();
    }

    private RequirementHandlerRegistry() {
    }

    public static <R extends MachineRequirement> void register(RequirementHandler<R> handler) {
        if (handler == null) throw new IllegalArgumentException("handler must not be null");
        RequirementType<R> type = handler.type();
        if (type == null) throw new IllegalArgumentException("handler type must not be null");
        if (HANDLERS.putIfAbsent(type, handler) != null) {
            throw new IllegalArgumentException("Duplicate requirement handler type: " + type.id());
        }
    }

    public static RequirementHandler<?> handlerFor(RequirementType<?> type) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        return HANDLERS.get(type);
    }

    public static void registerBuiltIns() {
        registerBuiltIn(new ItemHandler());
        registerBuiltIn(new FluidHandler());
        registerBuiltIn(new EnergyHandler());
        registerBuiltIn(new SmartInterfaceHandler());
    }

    private static void registerBuiltIn(RequirementHandler<?> handler) {
        HANDLERS.putIfAbsent(handler.type(), handler);
    }

    private static ExecutionStatus blocked(MachineRequirement requirement, String reason) {
        return new ExecutionStatus(requirement.type().id(), StatusSeverity.BLOCKED,
                requirement.type().id(), Map.of("reason", reason));
    }

    private static RequirementPlan blockedPlan(MachineRequirement requirement, PlanningContext context, String reason) {
        return new RequirementPlan(context.requirementIndex(), 0, List.of(), blocked(requirement, reason));
    }

    private static RequirementPlan blockedOutputPlan(MachineRequirement requirement, PlanningContext context,
                                                     String reason, long requested) {
        return RequirementPlan.withOutputSimulation(context.requirementIndex(), 0, List.of(),
                blocked(requirement, reason), new OutputSimulation(requested, 0L, OutputFit.NONE));
    }

    private static RequirementPlan deferredPlan(PlanningContext context,
                                                long maxParallelism, RequirementPlan.OperationFactory factory) {
        return new RequirementPlan(context.requirementIndex(), maxParallelism, List.of(), null, factory);
    }

    private static RequirementPlan deferredPlan(PlanningContext context,
                                                long maxParallelism, RequirementPlan.OperationFactory factory,
                                                RequirementPlan.ReservationFactory reservationFactory) {
        return new RequirementPlan(context.requirementIndex(), maxParallelism, List.of(), null,
                factory, reservationFactory);
    }

    private static ResourceStorage<?> resourceStorage(MachineCapability capability, Class<?> resourceType) {
        return capability.storage() instanceof ResourceStorage<?> storage
                && storage.resourceType().equals(resourceType) ? storage : null;
    }

    private static long scaled(long amount, long parallelism) {
        try {
            return Math.multiplyExact(amount, parallelism);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long requestedAmount(ItemRequirement requirement, long parallelism) {
        return scaled(requirement.stack(null).getCount(), parallelism);
    }

    private static long requestedAmount(FluidRequirement requirement, long parallelism) {
        return scaled(requirement.stack().getAmount(), parallelism);
    }

    private static long requestedAmount(EnergyRequirement requirement, long parallelism) {
        return scaled(requirement.fePerTick(), parallelism);
    }

    private static OutputSimulation outputSimulation(long requested, long accepted) {
        if (requested <= 0L) return null;
        OutputFit fit = accepted == 0L ? OutputFit.NONE
                : accepted == requested ? OutputFit.FULL : OutputFit.PARTIAL;
        return new OutputSimulation(requested, accepted, fit);
    }

    private static RequirementPlan.ReservationFactory reservationFactory(
            RequirementPlan.OperationFactory operationFactory) {
        return new RequirementPlan.ReservationFactory() {
            @Override
            public ExecutionStatus reserve(long parallelism, PlanningReservations reservations) {
                return operationFactory.create(parallelism, reservations).failure();
            }

            @Override
            public RequirementPlan.ReservationResult reserveResult(long parallelism,
                                                                    PlanningReservations reservations) {
                RequirementPlan.OperationPlan operationPlan = operationFactory.create(parallelism, reservations);
                return new RequirementPlan.ReservationResult(operationPlan.failure(),
                        operationPlan.outputSimulation());
            }
        };
    }

    private record EnergyAction(MachineCapability capability, long amount) {
    }

    private static RequirementPlan planEnergy(EnergyRequirement requirement,
                                               List<MachineCapability> capabilities,
                                               PlanningContext context) {
        if (requirement.fePerTick() <= 0) {
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        boolean allowPartialOutput = insert && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = energyMaximum(requirement.fePerTick(), insert, capabilities,
                context.requestedParallelism(), allowPartialOutput);
        if (maximum <= 0) {
            return insert
                    ? blockedOutputPlan(requirement, context, "no_output_capacity",
                    requestedAmount(requirement, context.requestedParallelism()))
                    : blockedPlan(requirement, context, "insufficient_energy");
        }
        return deferredPlan(context, maximum,
                (parallelism, reservations) -> {
                    return planEnergyOperations(requirement, capabilities, parallelism, reservations, insert,
                            allowPartialOutput, true);
                },
                reservationFactory((parallelism, reservations) -> planEnergyOperations(
                        requirement, capabilities, parallelism, reservations, insert,
                        allowPartialOutput, false)));
    }

    private static RequirementPlan planItem(ItemRequirement requirement,
                                             List<MachineCapability> capabilities,
                                             PlanningContext context) {
        long parallelism = context.requestedParallelism();
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && !shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        }
        if (requirement.io() == RecipeModifier.IOType.INPUT && requirement.item() == null) {
            return blockedPlan(requirement, context, "missing_item_ingredient");
        }
        boolean allowPartialOutput = requirement.io() == RecipeModifier.IOType.OUTPUT
                && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = itemMaximum(requirement, capabilities, parallelism, allowPartialOutput);
        if (maximum <= 0) {
            return requirement.io() == RecipeModifier.IOType.OUTPUT
                    ? blockedOutputPlan(requirement, context, "no_output_capacity",
                    requestedAmount(requirement, context.requestedParallelism()))
                    : blockedPlan(requirement, context, "insufficient_resource");
        }
        boolean[] consumed = requirement.io() == RecipeModifier.IOType.INPUT
                ? consumeDecisions(requirement.consumeChance(), parallelism) : new boolean[parallelism];
        if (requirement.io() == RecipeModifier.IOType.INPUT && requirement.consumeChance() <= 0F) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && requirement.stack(null).isEmpty()) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        return deferredPlan(context, maximum,
                (finalParallelism, reservations) -> planItemOperations(
                        requirement, capabilities, finalParallelism, consumed, reservations,
                        allowPartialOutput, true),
                reservationFactory((finalParallelism, reservations) -> planItemOperations(
                        requirement, capabilities, finalParallelism, consumed, reservations,
                        allowPartialOutput, false)));
    }

    private static RequirementPlan planFluid(FluidRequirement requirement,
                                              List<MachineCapability> capabilities,
                                              PlanningContext context) {
        long parallelism = context.requestedParallelism();
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && !shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        }
        boolean allowPartialOutput = requirement.io() == RecipeModifier.IOType.OUTPUT
                && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = fluidMaximum(requirement, capabilities, parallelism, allowPartialOutput);
        if (maximum <= 0) {
            return requirement.io() == RecipeModifier.IOType.OUTPUT
                    ? blockedOutputPlan(requirement, context, "no_output_capacity",
                    requestedAmount(requirement, context.requestedParallelism()))
                    : blockedPlan(requirement, context, "insufficient_resource");
        }
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && requirement.stack().isEmpty()) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        return deferredPlan(context, maximum,
                (finalParallelism, reservations) -> planFluidOperations(
                        requirement, capabilities, finalParallelism, reservations,
                        allowPartialOutput, true),
                reservationFactory((finalParallelism, reservations) -> planFluidOperations(
                        requirement, capabilities, finalParallelism, reservations,
                        allowPartialOutput, false)));
    }

    private static RequirementPlan planSmartInterface(SmartInterfaceRequirement requirement,
                                                        List<MachineCapability> capabilities,
                                                        PlanningContext context) {
        for (MachineCapability capability : capabilities) {
            if (!(capability.storage() instanceof FloatValueStorage storage)) continue;
            if (requirement.io() == RecipeModifier.IOType.INPUT) {
                if (storage.value(requirement.interfaceType())
                        .filter(value -> value >= requirement.minValue() && value <= requirement.maxValue()).isPresent()) {
                    return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
                }
                continue;
            }
            if (storage.value(requirement.interfaceType()).isPresent()) {
                return deferredPlan(context, context.requestedParallelism(),
                        (parallelism, reservations) -> new RequirementPlan.OperationPlan(List.of(
                                capability.prepare(new CapabilityRequests.SmartValueRequest(
                                        capability.view().type(), capability.view().ioType(), parallelism,
                                        requirement.interfaceType(), requirement.minValue()))), null));
            }
        }
        return blockedPlan(requirement, context, "missing_smart_interface");
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
            ResourceStorage<?> storage = resourceStorage(capability, ItemResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                Object current = storage.resource(slot);
                if (current instanceof ItemResource existing && !existing.isEmpty() && !existing.equals(resource)) continue;
                if (!storage.isValidResource(slot, resource)) continue;
                long slotCapacity = Math.min(storage.capacityResource(slot, resource), stack.getMaxStackSize());
                long room = Math.max(0L, slotCapacity - storage.amount(slot));
                capacity = saturatingAdd(capacity, room);
            }
        }
        if (allowPartialOutputs) return capacity > 0L ? requested : 0;
        long maximum = Math.min(requested, capacity / stack.getCount());
        return maximum > 0 || capacity <= 0L ? maximum : 1;
    }

    private static long fluidMaximum(FluidRequirement requirement, List<MachineCapability> capabilities,
                                     long requested, boolean allowPartialOutputs) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            if (requirement.fluid() == null || requirement.amount() <= 0) return 0;
            long available = 0L;
            for (MachineCapability capability : capabilities) {
                ResourceStorage<?> storage = resourceStorage(capability, FluidResource.class);
                if (storage == null) continue;
                for (int slot = 0; slot < storage.size(); slot++) {
                    if (!(storage.resource(slot) instanceof FluidResource resource) || storage.amount(slot) <= 0L) continue;
                    if (requirement.fluid().test(resource.toStack((int) Math.min(storage.amount(slot), Integer.MAX_VALUE)))) {
                        available = saturatingAdd(available, storage.amount(slot));
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
            ResourceStorage<?> storage = resourceStorage(capability, FluidResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                Object current = storage.resource(slot);
                if (current instanceof FluidResource existing && !existing.isEmpty() && !existing.equals(resource)) continue;
                if (!storage.isValidResource(slot, resource)) continue;
                capacity = saturatingAdd(capacity,
                        Math.max(0L, storage.capacityResource(slot, resource) - storage.amount(slot)));
            }
        }
        if (allowPartialOutputs) return capacity > 0L ? requested : 0;
        long maximum = Math.min(requested, capacity / stack.getAmount());
        return maximum > 0 || capacity <= 0L ? maximum : 1;
    }

    private static RequirementPlan.OperationPlan planItemOperations(ItemRequirement requirement,
                                                                       List<MachineCapability> capabilities,
                                                                       long parallelism, boolean[] consumed,
                                                                       PlanningReservations reservations,
                                                                       boolean allowPartialOutputs,
                                                                       boolean materialize) {
        long batches = requirement.io() == RecipeModifier.IOType.INPUT
                ? consumedPrefix(consumed, parallelism) : parallelism;
        long amount = requirement.io() == RecipeModifier.IOType.INPUT
                ? scaled(requirement.count(), batches)
                : scaled(requirement.stack(null).getCount(), parallelism);
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
            ResourceStorage<?> storage = resourceStorage(capability, ItemResource.class);
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
                    long capacity = Math.min(storage.capacityResource(slot, requestedResource), stackLimit);
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
            return new RequirementPlan.OperationPlan(List.of(), blocked(requirement, "insufficient_resource"),
                    outputSimulation(requestedAmount, amount - remaining));
        }
        if (actionMap.isEmpty()) return new RequirementPlan.OperationPlan(List.of(),
                blocked(requirement, "no_output_capacity"), outputSimulation(requestedAmount, 0L));
        return resourceOperations(actionMap, parallelism, materialize,
                outputSimulation(requestedAmount, amount - remaining));
    }

    private static RequirementPlan.OperationPlan planFluidOperations(FluidRequirement requirement,
                                                                       List<MachineCapability> capabilities,
                                                                       long parallelism,
                                                                       PlanningReservations reservations,
                                                                       boolean allowPartialOutputs,
                                                                       boolean materialize) {
        long amount = requirement.io() == RecipeModifier.IOType.INPUT
                ? scaled(requirement.amount(), parallelism)
                : scaled(requirement.stack().getAmount(), parallelism);
        if (amount <= 0L) return new RequirementPlan.OperationPlan(List.of(), null);
        long requestedAmount = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? requestedAmount(requirement, parallelism) : 0L;
        FluidResource requestedResource = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? FluidResource.of(requirement.stack()) : null;
        Map<MachineCapability, List<CapabilityRequests.ResourceAction<FluidResource>>> actionMap = new LinkedHashMap<>();
        long remaining = amount;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<?> storage = resourceStorage(capability, FluidResource.class);
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
            return new RequirementPlan.OperationPlan(List.of(), blocked(requirement, "insufficient_resource"),
                    outputSimulation(requestedAmount, amount - remaining));
        }
        if (actionMap.isEmpty()) return new RequirementPlan.OperationPlan(List.of(),
                blocked(requirement, "no_output_capacity"), outputSimulation(requestedAmount, 0L));
        return resourceOperations(actionMap, parallelism, materialize,
                outputSimulation(requestedAmount, amount - remaining));
    }

    private static <R> RequirementPlan.OperationPlan resourceOperations(
            Map<MachineCapability, List<CapabilityRequests.ResourceAction<R>>> actionMap,
            long parallelism, boolean materialize, OutputSimulation outputSimulation) {
        List<CapabilityOperation> operations = materialize
                ? actionMap.entrySet().stream()
                .map(entry -> entry.getKey().prepare(new CapabilityRequests.ResourceRequest<>(
                        entry.getKey().view().type(), entry.getKey().view().ioType(), parallelism, entry.getValue())))
                .toList()
                : List.of();
        return new RequirementPlan.OperationPlan(operations, null, outputSimulation);
    }

    private static RequirementPlan.OperationPlan planEnergyOperations(EnergyRequirement requirement,
                                                                       List<MachineCapability> capabilities,
                                                                       long parallelism,
                                                                       PlanningReservations reservations,
                                                                       boolean insert,
                                                                       boolean allowPartialOutput,
                                                                       boolean materialize) {
        List<CapabilityOperation> operations = new ArrayList<>();
        long requested = insert ? requestedAmount(requirement, parallelism) : 0L;
        long accepted = 0L;
        for (int batch = 0; batch < parallelism; batch++) {
            List<EnergyAction> actions = reserveEnergyBatch(requirement.fePerTick(), insert,
                    capabilities, reservations);
            long batchAccepted = energyAmount(actions);
            accepted = saturatingAdd(accepted, batchAccepted);
            if (batchAccepted < requirement.fePerTick() && (!allowPartialOutput || !insert)) {
                return new RequirementPlan.OperationPlan(List.of(), blocked(requirement,
                        batchAccepted == 0L && insert ? "no_output_capacity" :
                                insert ? "insufficient_resource" : "insufficient_energy"),
                        outputSimulation(requested, accepted));
            }
            if (!materialize) continue;
            for (EnergyAction action : actions) {
                operations.add(action.capability().prepare(new CapabilityRequests.ValueRequest(
                        action.capability().view().type(), action.capability().view().ioType(),
                        parallelism, action.amount(), insert)));
            }
        }
        if (insert && accepted == 0L) {
            return new RequirementPlan.OperationPlan(List.of(), blocked(requirement, "no_output_capacity"),
                    outputSimulation(requested, accepted));
        }
        return new RequirementPlan.OperationPlan(operations, null, outputSimulation(requested, accepted));
    }

    private static long energyMaximum(long perBatch, boolean insert, List<MachineCapability> capabilities,
                                      long requested, boolean allowPartialOutput) {
        if (insert && allowPartialOutput) return hasEnergyCapacity(capabilities) ? requested : 0;
        PlanningReservations reservations = new PlanningReservations();
        long maximum = 0;
        for (long batch = 0; batch < requested; batch++) {
            if (energyAmount(reserveEnergyBatch(perBatch, insert, capabilities, reservations)) < perBatch) break;
            maximum++;
        }
        if (insert && maximum == 0 && hasEnergyCapacity(capabilities)) return 1;
        return maximum;
    }

    private static boolean hasEnergyCapacity(List<MachineCapability> capabilities) {
        PlanningReservations reservations = new PlanningReservations();
        for (MachineCapability capability : capabilities) {
            if (capability.storage() instanceof LongValueStorage storage
                    && storage.transferLimit() > 0L
                    && reservations.valueAvailable(storage, true) > 0L) return true;
        }
        return false;
    }

    private static List<EnergyAction> reserveEnergyBatch(long amount, boolean insert,
                                                          List<MachineCapability> capabilities,
                                                          PlanningReservations reservations) {
        long remaining = amount;
        List<EnergyAction> actions = new ArrayList<>();
        for (MachineCapability capability : capabilities) {
            if (!(capability.storage() instanceof LongValueStorage storage)) continue;
            long available = reservations.valueAvailable(storage, insert);
            long moved = Math.min(remaining, Math.min(available, storage.transferLimit()));
            if (moved <= 0L || !reservations.reserveValue(storage, moved, insert)) continue;
            actions.add(new EnergyAction(capability, moved));
            remaining -= moved;
            if (remaining == 0L) break;
        }
        return actions;
    }

    private static long energyAmount(List<EnergyAction> actions) {
        long amount = 0L;
        for (EnergyAction action : actions) amount = saturatingAdd(amount, action.amount());
        return amount;
    }

    private static long consumedPrefix(boolean[] consumed, long parallelism) {
        long batches = 0L;
        for (int index = 0; index < parallelism; index++) if (consumed[index]) batches++;
        return batches;
    }

    private static boolean[] consumeDecisions(float chance, long parallelism) {
        boolean[] decisions = new boolean[Math.toIntExact(parallelism)];
        for (int index = 0; index < parallelism; index++) {
            decisions[index] = chance >= 1F || chance > 0F && Math.random() < chance;
        }
        return decisions;
    }

    private static boolean matchesItem(ItemRequirement requirement, ItemResource resource) {
        ItemStack stack = resource.toStack((int) Math.min(resource.getMaxStackSize(), Integer.MAX_VALUE));
        return requirement.item().test(stack) && requirement.components().matches(stack);
    }

    private static long matchingItemAmount(ItemRequirement requirement, List<MachineCapability> capabilities) {
        long amount = 0;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<?> storage = resourceStorage(capability, ItemResource.class);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                if (storage.resource(slot) instanceof ItemResource resource
                        && !resource.isEmpty() && matchesItem(requirement, resource)) {
                    amount = saturatingAdd(amount, storage.amount(slot));
                }
            }
        }
        return amount;
    }

    private static long saturatingAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return first + second;
    }

    private static boolean shouldProduce(float chance) {
        return chance >= 1F || chance > 0F && Math.random() < chance;
    }

    private static final class ItemHandler implements RequirementHandler<ItemRequirement> {
        @Override
        public RequirementType<ItemRequirement> type() {
            return ItemRequirement.TYPE;
        }

        @Override
        public RequirementPlan plan(ItemRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return planItem(requirement, capabilities, context);
        }

    }

    private static final class FluidHandler implements RequirementHandler<FluidRequirement> {
        @Override
        public RequirementType<FluidRequirement> type() {
            return FluidRequirement.TYPE;
        }

        @Override
        public RequirementPlan plan(FluidRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return planFluid(requirement, capabilities, context);
        }

    }

    private static final class EnergyHandler implements RequirementHandler<EnergyRequirement> {
        @Override
        public RequirementType<EnergyRequirement> type() {
            return EnergyRequirement.TYPE;
        }

        @Override
        public RequirementPlan plan(EnergyRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return planEnergy(requirement, capabilities, context);
        }

    }

    private static final class SmartInterfaceHandler implements RequirementHandler<SmartInterfaceRequirement> {
        @Override
        public RequirementType<SmartInterfaceRequirement> type() {
            return SmartInterfaceRequirement.TYPE;
        }

        @Override
        public RequirementPlan plan(SmartInterfaceRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return planSmartInterface(requirement, capabilities, context);
        }

    }
}
