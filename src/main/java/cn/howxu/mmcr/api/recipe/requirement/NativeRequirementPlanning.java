package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.util.IOType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Native handler planning used by the built-in item, fluid, and energy requirements. */
final class NativeRequirementPlanning {
    private NativeRequirementPlanning() {
    }

    static RequirementPlan item(ItemRequirement requirement, List<MachineCapability> capabilities,
                                PlanningContext context) {
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        if (insert && !RequirementHandlerSupport.shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
        ItemStack requested = insert ? requirement.stack(null) : ItemStack.EMPTY;
        long perBatch = insert ? requested.getCount() : requirement.count();
        if (perBatch <= 0L || insert && requested.isEmpty()) {
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
        long requestedAmount = RequirementHandlerSupport.scaled(perBatch, context.requestedParallelism());
        List<MachineCapability> ordered = insert
                ? RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities) : capabilities;
        long available = 0L;
        for (MachineCapability capability : ordered) {
            ItemHandlerFacet facet = capability.facet(ItemHandlerFacet.class).orElse(null);
            if (facet == null || facet.itemHandler() == null) continue;
            available = RequirementHandlerSupport.saturatingAdd(available,
                    itemAvailable(facet.itemHandler(), requirement, requested, insert, facet.supportsLargeStacks()));
        }
        boolean partial = insert && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = maximum(context.requestedParallelism(), perBatch, available, insert, partial);
        if (maximum <= 0L) return blocked(requirement, context, insert, requestedAmount, available);
        RequirementHandlerSupport.ConsumeProfile consumed = insert ? null
                : RequirementHandlerSupport.consumeProfile(requirement.consumeChance(), context.requestedParallelism());
        return RequirementHandlerSupport.deferredPlan(context, maximum <= 0L ? context.requestedParallelism() : maximum,
                (parallelism, reservations) -> itemOperations(requirement, ordered, context, parallelism, requested,
                        insert, partial, consumed, reservations, true),
                RequirementHandlerSupport.reservationFactory((parallelism, reservations) -> itemOperations(
                        requirement, ordered, context, parallelism, requested, insert, partial, consumed, reservations,
                        false)));
    }

    static RequirementPlan fluid(FluidRequirement requirement, List<MachineCapability> capabilities,
                                 PlanningContext context) {
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        if (insert && !RequirementHandlerSupport.shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
        FluidStack requested = insert ? requirement.stack() : FluidStack.EMPTY;
        long perBatch = insert ? requested.getAmount() : requirement.amount();
        if (perBatch <= 0L || insert && requested.isEmpty()) {
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
        long requestedAmount = RequirementHandlerSupport.scaled(perBatch, context.requestedParallelism());
        List<MachineCapability> ordered = insert
                ? RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities) : capabilities;
        long available = 0L;
        for (MachineCapability capability : ordered) {
            FluidHandlerFacet facet = capability.facet(FluidHandlerFacet.class).orElse(null);
            if (facet == null || facet.fluidHandler() == null) continue;
            available = RequirementHandlerSupport.saturatingAdd(available,
                    fluidAvailable(facet.fluidHandler(), requirement, requested, insert));
        }
        boolean partial = insert && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = maximum(context.requestedParallelism(), perBatch, available, insert, partial);
        if (maximum <= 0L) return blocked(requirement, context, insert, requestedAmount, available);
        RequirementHandlerSupport.ConsumeProfile consumed = insert ? null
                : RequirementHandlerSupport.consumeProfile(requirement.consumeChance(), context.requestedParallelism());
        return RequirementHandlerSupport.deferredPlan(context, maximum <= 0L ? context.requestedParallelism() : maximum,
                (parallelism, reservations) -> fluidOperations(requirement, ordered, context, parallelism, requested,
                        insert, partial, consumed, reservations, true),
                RequirementHandlerSupport.reservationFactory((parallelism, reservations) -> fluidOperations(
                        requirement, ordered, context, parallelism, requested, insert, partial, consumed, reservations,
                        false)));
    }

    static RequirementPlan energy(EnergyRequirement requirement, List<MachineCapability> capabilities,
                                  PlanningContext context) {
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        long perBatch = requirement.fePerTick();
        if (perBatch <= 0L) return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        List<MachineCapability> ordered = insert
                ? RequirementHandlerSupport.prioritizedOutputCapabilities(capabilities) : capabilities;
        long available = 0L;
        for (MachineCapability capability : ordered) {
            EnergyStorageFacet facet = capability.facet(EnergyStorageFacet.class).orElse(null);
            if (facet != null && facet.energyStorage() != null) {
                available = RequirementHandlerSupport.saturatingAdd(available,
                        energyAvailable(facet.energyStorage(), context.reservations(), insert));
            }
        }
        boolean partial = insert && context.outputPolicy() == OutputPolicy.ALLOW_PARTIAL;
        long maximum = maximum(context.requestedParallelism(), perBatch, available, insert, partial);
        if (maximum <= 0L) return blocked(requirement, context, insert,
                RequirementHandlerSupport.scaled(perBatch, context.requestedParallelism()), available);
        return RequirementHandlerSupport.deferredPlan(context, maximum <= 0L ? context.requestedParallelism() : maximum,
                (parallelism, reservations) -> energyOperations(requirement, ordered, context, parallelism, insert,
                        partial, reservations, true),
                RequirementHandlerSupport.reservationFactory((parallelism, reservations) -> energyOperations(
                        requirement, ordered, context, parallelism, insert, partial, reservations, false)));
    }

    private static RequirementPlan.OperationPlan itemOperations(ItemRequirement requirement, List<MachineCapability> capabilities,
                                                                   PlanningContext context, long parallelism, ItemStack output,
                                                                    boolean insert, boolean partial,
                                                                    RequirementHandlerSupport.ConsumeProfile consumed,
                                                                    PlanningReservations reservations,
                                                                    boolean materialize) {
        long batches = insert ? parallelism : consumed.consumedBatches(parallelism);
        long remaining = RequirementHandlerSupport.scaled(insert ? output.getCount() : requirement.count(), batches);
        List<cn.howxu.mmcr.api.capability.plan.CapabilityOperation> operations = new ArrayList<>();
        for (MachineCapability capability : capabilities) {
            ItemHandlerFacet facet = capability.facet(ItemHandlerFacet.class).orElse(null);
            if (facet == null || facet.itemHandler() == null || remaining <= 0L) continue;
            List<CapabilityRequests.ItemAction> actions = new ArrayList<>();
            IItemHandler handler = facet.itemHandler();
            for (int slot = 0; slot < handler.getSlots() && remaining > 0L; slot++) {
                ItemStack current = reservations.item(handler, slot);
                if (!insert && (current.isEmpty() || requirement.item() == null || !requirement.item().test(current)
                        || !requirement.components().matches(current))) continue;
                if (insert && !handler.isItemValid(slot, output)
                        || insert && !current.isEmpty() && !ItemStack.isSameItemSameComponents(current, output)) continue;
                long capacity = itemCapacity(handler, slot);
                if (insert && !facet.supportsLargeStacks()) capacity = Math.min(capacity, output.getMaxStackSize());
                long moved = Math.min(remaining, insert ? capacity - reservations.itemAmount(handler, slot)
                        : reservations.itemAmount(handler, slot));
                if (moved <= 0L) continue;
                if (insert ? !reservations.reserveItemInsert(handler, slot, output, moved, capacity)
                        : !reservations.reserveItemExtract(handler, slot, current, moved)) continue;
                actions.add(new CapabilityRequests.ItemAction(slot, insert ? output : current, moved, insert));
                remaining -= moved;
            }
            if (materialize && !actions.isEmpty()) operations.add(capability.prepare(new CapabilityRequests.ItemRequest(
                    capability.type(), IOType.valueOf(requirement.io().name()), parallelism, actions)));
        }
        return operationResult(requirement, context, operations, remaining, insert, partial,
                insert ? RequirementHandlerSupport.scaled(output.getCount(), parallelism) : 0L);
    }

    private static RequirementPlan.OperationPlan fluidOperations(FluidRequirement requirement, List<MachineCapability> capabilities,
                                                                   PlanningContext context, long parallelism, FluidStack output,
                                                                    boolean insert, boolean partial,
                                                                    RequirementHandlerSupport.ConsumeProfile consumed,
                                                                    PlanningReservations reservations,
                                                                    boolean materialize) {
        long batches = insert ? parallelism : consumed.consumedBatches(parallelism);
        long remaining = RequirementHandlerSupport.scaled(insert ? output.getAmount() : requirement.amount(), batches);
        List<cn.howxu.mmcr.api.capability.plan.CapabilityOperation> operations = new ArrayList<>();
        for (MachineCapability capability : capabilities) {
            FluidHandlerFacet facet = capability.facet(FluidHandlerFacet.class).orElse(null);
            if (facet == null || facet.fluidHandler() == null || remaining <= 0L) continue;
            IFluidHandler handler = facet.fluidHandler();
            List<CapabilityRequests.FluidAction> actions = new ArrayList<>();
            for (int tank = 0; tank < handler.getTanks() && remaining > 0L; tank++) {
                FluidStack current = reservations.fluid(handler, tank);
                if (!insert && (current.isEmpty() || requirement.fluid() == null || !requirement.fluid().test(current))) continue;
                if (insert && !handler.isFluidValid(tank, output)
                        || insert && !current.isEmpty() && !FluidStack.isSameFluidSameComponents(current, output)) continue;
                long moved = Math.min(remaining, insert ? fluidCapacity(handler, tank) - reservations.fluidAmount(handler, tank)
                        : reservations.fluidAmount(handler, tank));
                if (moved <= 0L) continue;
                if (insert ? !reservations.reserveFluidInsert(handler, tank, output, moved, fluidCapacity(handler, tank))
                        : !reservations.reserveFluidExtract(handler, tank, current, moved)) continue;
                actions.add(new CapabilityRequests.FluidAction(tank, insert ? output : current, moved, insert));
                remaining -= moved;
            }
            if (materialize && !actions.isEmpty()) operations.add(capability.prepare(new CapabilityRequests.FluidRequest(
                    capability.type(), IOType.valueOf(requirement.io().name()), parallelism, actions)));
        }
        return operationResult(requirement, context, operations, remaining, insert, partial,
                insert ? RequirementHandlerSupport.scaled(output.getAmount(), parallelism) : 0L);
    }

    private static RequirementPlan.OperationPlan energyOperations(EnergyRequirement requirement, List<MachineCapability> capabilities,
                                                                     PlanningContext context, long parallelism,
                                                                      boolean insert, boolean partial,
                                                                      PlanningReservations reservations,
                                                                      boolean materialize) {
        long remaining = RequirementHandlerSupport.scaled(requirement.fePerTick(), parallelism);
        List<cn.howxu.mmcr.api.capability.plan.CapabilityOperation> operations = new ArrayList<>();
        for (MachineCapability capability : capabilities) {
            EnergyStorageFacet facet = capability.facet(EnergyStorageFacet.class).orElse(null);
            if (facet == null || facet.energyStorage() == null || remaining <= 0L) continue;
            long moved = Math.min(remaining, Math.min(
                    energyAvailable(facet.energyStorage(), reservations, insert),
                    RequirementHandlerSupport.scaled(energyTransferLimit(facet.energyStorage()), parallelism)));
            if (moved <= 0L) continue;
            if (facet.energyStorage() instanceof LongEnergyHandler storage
                    && !reservations.reserveValueTotal(storage, moved, insert)) continue;
            if (materialize) {
                operations.add(capability.prepare(new CapabilityRequests.ValueRequest(capability.type(),
                        IOType.valueOf(requirement.io().name()), parallelism, moved, insert)));
            }
            remaining -= moved;
        }
        return operationResult(requirement, context, operations, remaining, insert, partial,
                insert ? RequirementHandlerSupport.scaled(requirement.fePerTick(), parallelism) : 0L);
    }

    private static RequirementPlan.OperationPlan operationResult(MachineRequirement requirement, PlanningContext context,
                                                                   List<cn.howxu.mmcr.api.capability.plan.CapabilityOperation> operations,
                                                                   long remaining, boolean insert, boolean partial, long outputRequested) {
        long accepted = Math.max(0L, outputRequested - remaining);
        if (remaining > 0L && (!insert || !partial || accepted == 0L)) return new RequirementPlan.OperationPlan(List.of(),
                RequirementHandlerSupport.blocked(requirement, context, insert ? BuiltinFailureReasons.MISSING_OUTPUT
                        : requirement instanceof EnergyRequirement ? BuiltinFailureReasons.MISSING_ENERGY
                        : BuiltinFailureReasons.MISSING_INPUT, Map.of("shortfall", Long.toString(remaining))),
                RequirementHandlerSupport.outputSimulation(outputRequested, accepted));
        return new RequirementPlan.OperationPlan(operations, null,
                RequirementHandlerSupport.outputSimulation(outputRequested, accepted));
    }

    private static RequirementPlan blocked(MachineRequirement requirement, PlanningContext context, boolean insert,
                                           long requested, long available) {
        Map<String, String> details = Map.of("required", Long.toString(requested),
                "available", Long.toString(available));
        if (insert) {
            return RequirementPlan.withOutputSimulation(context.requirementIndex(), 0L, List.of(),
                    RequirementHandlerSupport.blocked(requirement, context, BuiltinFailureReasons.MISSING_OUTPUT,
                            details), RequirementHandlerSupport.outputSimulation(requested,
                            Math.min(requested, Math.max(0L, available))));
        }
        return RequirementHandlerSupport.blockedPlan(requirement, context,
                requirement instanceof EnergyRequirement ? BuiltinFailureReasons.MISSING_ENERGY
                        : BuiltinFailureReasons.MISSING_INPUT, details);
    }

    private static long itemAvailable(IItemHandler handler, ItemRequirement requirement, ItemStack output, boolean insert,
                                     boolean supportsLargeStacks) {
        long result = 0L;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (insert) {
                if (handler.isItemValid(slot, output) && (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, output))) {
                    long capacity = itemCapacity(handler, slot);
                    if (!supportsLargeStacks) capacity = Math.min(capacity, output.getMaxStackSize());
                    result = RequirementHandlerSupport.saturatingAdd(result,
                            Math.max(0L, capacity - itemAmount(handler, slot)));
                }
            } else if (!stack.isEmpty() && requirement.item() != null && requirement.item().test(stack)
                    && requirement.components().matches(stack)) result = RequirementHandlerSupport.saturatingAdd(result, itemAmount(handler, slot));
        }
        return result;
    }

    private static long maximum(long requested, long perBatch, long available, boolean insert, boolean partial) {
        long maximum = Math.min(requested, available / perBatch);
        if (insert && partial && available > 0L) return requested;
        return maximum;
    }

    private static long fluidAvailable(IFluidHandler handler, FluidRequirement requirement, FluidStack output, boolean insert) {
        long result = 0L;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (insert) {
                if (handler.isFluidValid(tank, output) && (stack.isEmpty() || FluidStack.isSameFluidSameComponents(stack, output))) {
                    result = RequirementHandlerSupport.saturatingAdd(result, Math.max(0L, fluidCapacity(handler, tank) - fluidAmount(handler, tank)));
                }
            } else if (!stack.isEmpty() && requirement.fluid() != null && requirement.fluid().test(stack)) {
                result = RequirementHandlerSupport.saturatingAdd(result, fluidAmount(handler, tank));
            }
        }
        return result;
    }

    private static long energyAvailable(IEnergyStorage storage, PlanningReservations reservations, boolean insert) {
        if (storage instanceof LongEnergyHandler longStorage) return reservations.valueAvailable(longStorage, insert);
        return insert ? (long) storage.getMaxEnergyStored() - storage.getEnergyStored() : storage.getEnergyStored();
    }

    private static long energyTransferLimit(IEnergyStorage storage) {
        return storage instanceof LongEnergyHandler longStorage ? longStorage.getTransferLimit() : Integer.MAX_VALUE;
    }

    private static long itemAmount(IItemHandler handler, int slot) {
        return handler instanceof LongItemStorage storage ? storage.amount(slot)
                : handler instanceof NativeStackSync.Item sync ? sync.amount(slot)
                : handler.getStackInSlot(slot).getCount();
    }

    private static long itemCapacity(IItemHandler handler, int slot) {
        return handler instanceof LongItemStorage storage ? storage.capacity(slot)
                : handler instanceof NativeStackSync.Item sync ? sync.capacity(slot) : handler.getSlotLimit(slot);
    }

    private static long fluidAmount(IFluidHandler handler, int tank) {
        return handler instanceof LongFluidStorage storage ? storage.amount(tank)
                : handler instanceof NativeStackSync.Fluid sync ? sync.amount(tank)
                : handler.getFluidInTank(tank).getAmount();
    }

    private static long fluidCapacity(IFluidHandler handler, int tank) {
        return handler instanceof LongFluidStorage storage ? storage.capacity(tank)
                : handler instanceof NativeStackSync.Fluid sync ? sync.capacity(tank) : handler.getTankCapacity(tank);
    }
}
