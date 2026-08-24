package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
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
 * Registry of requirement handlers used by recipe planning and runtime forwarding.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RequirementHandlerRegistry {
    private static final Map<RequirementType<?>, RequirementHandler<?>> HANDLERS = new ConcurrentHashMap<>();
    private static final Map<RequirementType<?>, LegacyHandler<?>> LEGACY_HANDLERS = new ConcurrentHashMap<>();

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

    public static boolean simulate(MachineRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
        return legacyHandler(requirement).simulate(requirement, context, requirementIndex);
    }

    public static boolean commit(MachineRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
        return legacyHandler(requirement).commit(requirement, context, requirementIndex);
    }

    public static int maxInputParallelism(MachineRequirement requirement, RecipeCraftingContext context, int limit) {
        return legacyHandler(requirement).maxInputParallelism(requirement, context, limit);
    }

    public static boolean ioTick(MachineRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
        return legacyHandler(requirement).ioTick(requirement, context, requirementIndex);
    }

    private static void registerBuiltIn(LegacyHandler<?> handler) {
        if (HANDLERS.putIfAbsent(handler.type(), handler) == null) {
            LEGACY_HANDLERS.put(handler.type(), handler);
        }
    }

    @SuppressWarnings("unchecked")
    private static LegacyHandler<MachineRequirement> legacyHandler(MachineRequirement requirement) {
        LegacyHandler<?> handler = LEGACY_HANDLERS.get(requirement.type());
        if (handler == null) {
            throw new IllegalStateException("Requirement handler does not support runtime forwarding: " + requirement.type().id());
        }
        return (LegacyHandler<MachineRequirement>) handler;
    }

    private static ExecutionStatus blocked(MachineRequirement requirement, String reason) {
        return new ExecutionStatus(requirement.type().id(), StatusSeverity.BLOCKED,
                requirement.type().id(), Map.of("reason", reason));
    }

    private static RequirementPlan blockedPlan(MachineRequirement requirement, PlanningContext context, String reason) {
        return new RequirementPlan(context.requirementIndex(), 0, List.of(), blocked(requirement, reason));
    }

    private static RequirementPlan deferredPlan(MachineRequirement requirement, PlanningContext context,
                                                int maxParallelism, RequirementPlan.OperationFactory factory) {
        return new RequirementPlan(context.requirementIndex(), maxParallelism, List.of(), null, factory);
    }

    private static RequirementPlan deferredPlan(MachineRequirement requirement, PlanningContext context,
                                                int maxParallelism, RequirementPlan.OperationFactory factory,
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

    private record EnergyAction(MachineCapability capability, long amount) {
    }

    private interface LegacyHandler<R extends MachineRequirement> extends RequirementHandler<R> {
        boolean simulate(R requirement, RecipeCraftingContext context, int requirementIndex);

        boolean commit(R requirement, RecipeCraftingContext context, int requirementIndex);

        default int maxInputParallelism(R requirement, RecipeCraftingContext context, int limit) {
            return -1;
        }

        default boolean ioTick(R requirement, RecipeCraftingContext context, int requirementIndex) {
            return true;
        }

        @Override
        default RequirementPlan plan(R requirement, List<MachineCapability> capabilities, PlanningContext context) {
            return blockedPlan(requirement, context, "handler_did_not_prepare_operation");
        }
    }

    private static RequirementPlan planEnergy(EnergyRequirement requirement,
                                               List<MachineCapability> capabilities,
                                               PlanningContext context) {
        if (requirement.fePerTick() <= 0) {
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        int maximum = energyMaximum(requirement.fePerTick(), insert, capabilities, context.requestedParallelism());
        if (maximum <= 0) return blockedPlan(requirement, context, "insufficient_energy");
        return deferredPlan(requirement, context, maximum,
                (parallelism, reservations) -> {
                    return planEnergyOperations(requirement, capabilities, parallelism, reservations, insert, true);
                },
                (parallelism, reservations) -> planEnergyOperations(
                        requirement, capabilities, parallelism, reservations, insert, false).failure());
    }

    private static RequirementPlan planItem(ItemRequirement requirement,
                                             List<MachineCapability> capabilities,
                                             PlanningContext context) {
        int parallelism = context.requestedParallelism();
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && !shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        }
        if (requirement.io() == RecipeModifier.IOType.INPUT && requirement.item() == null) {
            return blockedPlan(requirement, context, "missing_item_ingredient");
        }
        boolean[] consumed = requirement.io() == RecipeModifier.IOType.INPUT
                ? consumeDecisions(requirement.consumeChance(), parallelism) : new boolean[parallelism];
        int maximum = itemMaximum(requirement, capabilities, parallelism, consumed, context.allowPartialOutputs());
        if (maximum <= 0) {
            return blockedPlan(requirement, context,
                    requirement.io() == RecipeModifier.IOType.OUTPUT && context.allowPartialOutputs()
                            ? "no_output_capacity" : "insufficient_resource");
        }
        if (requirement.io() == RecipeModifier.IOType.INPUT && requirement.consumeChance() <= 0F) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && requirement.stack(null).isEmpty()) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        return deferredPlan(requirement, context, maximum,
                (finalParallelism, reservations) -> planItemOperations(
                        requirement, capabilities, finalParallelism, consumed, reservations,
                        context.allowPartialOutputs(), true),
                (finalParallelism, reservations) -> planItemOperations(
                        requirement, capabilities, finalParallelism, consumed, reservations,
                        context.allowPartialOutputs(), false).failure());
    }

    private static RequirementPlan planFluid(FluidRequirement requirement,
                                              List<MachineCapability> capabilities,
                                              PlanningContext context) {
        int parallelism = context.requestedParallelism();
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && !shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        }
        int maximum = fluidMaximum(requirement, capabilities, parallelism, context.allowPartialOutputs());
        if (maximum <= 0) {
            return blockedPlan(requirement, context,
                    requirement.io() == RecipeModifier.IOType.OUTPUT && context.allowPartialOutputs()
                            ? "no_output_capacity" : "insufficient_resource");
        }
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && requirement.stack().isEmpty()) {
            return new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }
        return deferredPlan(requirement, context, maximum,
                (finalParallelism, reservations) -> planFluidOperations(
                        requirement, capabilities, finalParallelism, reservations,
                        context.allowPartialOutputs(), true),
                (finalParallelism, reservations) -> planFluidOperations(
                        requirement, capabilities, finalParallelism, reservations,
                        context.allowPartialOutputs(), false).failure());
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
                return deferredPlan(requirement, context, context.requestedParallelism(),
                        (parallelism, reservations) -> new RequirementPlan.OperationPlan(List.of(
                                capability.prepare(new CapabilityRequests.SmartValueRequest(
                                        capability.view().type(), capability.view().ioType(), parallelism,
                                        requirement.interfaceType(), requirement.minValue()))), null));
            }
        }
        return blockedPlan(requirement, context, "missing_smart_interface");
    }

    private static int itemMaximum(ItemRequirement requirement, List<MachineCapability> capabilities,
                                   int requested, boolean[] consumed, boolean allowPartialOutputs) {
        if (requirement.io() == RecipeModifier.IOType.INPUT) {
            long available = matchingItemAmount(requirement, capabilities);
            if (requirement.count() <= 0) return requested;
            if (requirement.consumeChance() <= 0F) {
                return (int) Math.min(requested, available / requirement.count());
            }
            int maximum = 0;
            long consumedAmount = 0L;
            for (int index = 0; index < requested; index++) {
                if (consumed[index]) consumedAmount = saturatingAdd(consumedAmount, requirement.count());
                if (consumedAmount > available) break;
                maximum = index + 1;
            }
            return maximum;
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
                if (storage.amount(slot) > 0L && current instanceof ItemResource existing && !existing.equals(resource)) continue;
                if (!storage.isValidResource(slot, resource)) continue;
                long room = Math.max(0L, storage.capacityResource(slot, resource) - storage.amount(slot));
                capacity = saturatingAdd(capacity, room);
            }
        }
        return allowPartialOutputs
                ? capacity > 0L ? requested : 0
                : (int) Math.min(requested, capacity / stack.getCount());
    }

    private static int fluidMaximum(FluidRequirement requirement, List<MachineCapability> capabilities,
                                    int requested, boolean allowPartialOutputs) {
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
            return (int) Math.min(requested, available / requirement.amount());
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
                if (storage.amount(slot) > 0L && current instanceof FluidResource existing && !existing.equals(resource)) continue;
                if (!storage.isValidResource(slot, resource)) continue;
                capacity = saturatingAdd(capacity,
                        Math.max(0L, storage.capacityResource(slot, resource) - storage.amount(slot)));
            }
        }
        return allowPartialOutputs
                ? capacity > 0L ? requested : 0
                : (int) Math.min(requested, capacity / stack.getAmount());
    }

    private static RequirementPlan.OperationPlan planItemOperations(ItemRequirement requirement,
                                                                       List<MachineCapability> capabilities,
                                                                       int parallelism, boolean[] consumed,
                                                                       PlanningReservations reservations,
                                                                       boolean allowPartialOutputs,
                                                                       boolean materialize) {
        long batches = requirement.io() == RecipeModifier.IOType.INPUT
                ? consumedPrefix(consumed, parallelism) : parallelism;
        long amount = requirement.io() == RecipeModifier.IOType.INPUT
                ? scaled(requirement.count(), batches)
                : scaled(requirement.stack(null).getCount(), parallelism);
        if (amount <= 0L) return new RequirementPlan.OperationPlan(List.of(), null);
        ItemResource requested = requirement.io() == RecipeModifier.IOType.OUTPUT
                ? ItemResource.of(requirement.stack(null)) : null;
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
                    if (currentAmount > 0L && (! (current instanceof ItemResource resource) || !resource.equals(requested))) continue;
                    if (!storage.isValidResource(slot, requested)) continue;
                    long capacity = storage.capacityResource(slot, requested);
                    long moved = Math.min(remaining, Math.max(0L, capacity - currentAmount));
                    if (moved > 0L && reservations.reserveInsert(storage, slot, requested, moved)) {
                        actions.add(new CapabilityRequests.ResourceAction<>(slot, requested, moved, true));
                        remaining -= moved;
                    }
                }
            }
            if (!actions.isEmpty()) actionMap.put(capability, actions);
            if (remaining == 0L) break;
        }
        if (remaining > 0L && !(allowPartialOutputs && requirement.io() == RecipeModifier.IOType.OUTPUT)) {
            return new RequirementPlan.OperationPlan(List.of(), blocked(requirement, "insufficient_resource"));
        }
        if (actionMap.isEmpty()) return new RequirementPlan.OperationPlan(List.of(),
                blocked(requirement, "no_output_capacity"));
        return resourceOperations(actionMap, parallelism, materialize);
    }

    private static RequirementPlan.OperationPlan planFluidOperations(FluidRequirement requirement,
                                                                       List<MachineCapability> capabilities,
                                                                       int parallelism,
                                                                       PlanningReservations reservations,
                                                                       boolean allowPartialOutputs,
                                                                       boolean materialize) {
        long amount = requirement.io() == RecipeModifier.IOType.INPUT
                ? scaled(requirement.amount(), parallelism)
                : scaled(requirement.stack().getAmount(), parallelism);
        if (amount <= 0L) return new RequirementPlan.OperationPlan(List.of(), null);
        FluidResource requested = requirement.io() == RecipeModifier.IOType.OUTPUT
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
                    if (currentAmount > 0L && (!(current instanceof FluidResource resource) || !resource.equals(requested))) continue;
                    if (!storage.isValidResource(slot, requested)) continue;
                    long moved = Math.min(remaining,
                            Math.max(0L, storage.capacityResource(slot, requested) - currentAmount));
                    if (moved > 0L && reservations.reserveInsert(storage, slot, requested, moved)) {
                        actions.add(new CapabilityRequests.ResourceAction<>(slot, requested, moved, true));
                        remaining -= moved;
                    }
                }
            }
            if (!actions.isEmpty()) actionMap.put(capability, actions);
            if (remaining == 0L) break;
        }
        if (remaining > 0L && !(allowPartialOutputs && requirement.io() == RecipeModifier.IOType.OUTPUT)) {
            return new RequirementPlan.OperationPlan(List.of(), blocked(requirement, "insufficient_resource"));
        }
        if (actionMap.isEmpty()) return new RequirementPlan.OperationPlan(List.of(),
                blocked(requirement, "no_output_capacity"));
        return resourceOperations(actionMap, parallelism, materialize);
    }

    private static <R> RequirementPlan.OperationPlan resourceOperations(
            Map<MachineCapability, List<CapabilityRequests.ResourceAction<R>>> actionMap,
            int parallelism, boolean materialize) {
        List<CapabilityOperation> operations = materialize
                ? actionMap.entrySet().stream()
                .map(entry -> entry.getKey().prepare(new CapabilityRequests.ResourceRequest<>(
                        entry.getKey().view().type(), entry.getKey().view().ioType(), parallelism, entry.getValue())))
                .toList()
                : List.of();
        return new RequirementPlan.OperationPlan(operations, null);
    }

    private static RequirementPlan.OperationPlan planEnergyOperations(EnergyRequirement requirement,
                                                                       List<MachineCapability> capabilities,
                                                                       int parallelism,
                                                                       PlanningReservations reservations,
                                                                       boolean insert,
                                                                       boolean materialize) {
        List<CapabilityOperation> operations = new ArrayList<>();
        for (int batch = 0; batch < parallelism; batch++) {
            List<EnergyAction> actions = reserveEnergyBatch(requirement.fePerTick(), insert,
                    capabilities, reservations);
            if (actions == null) {
                return new RequirementPlan.OperationPlan(List.of(), blocked(requirement, "insufficient_energy"));
            }
            if (!materialize) continue;
            for (EnergyAction action : actions) {
                operations.add(action.capability().prepare(new CapabilityRequests.ValueRequest(
                        action.capability().view().type(), action.capability().view().ioType(),
                        parallelism, action.amount(), insert)));
            }
        }
        return new RequirementPlan.OperationPlan(operations, null);
    }

    private static int energyMaximum(long perBatch, boolean insert, List<MachineCapability> capabilities, int requested) {
        PlanningReservations reservations = new PlanningReservations();
        int maximum = 0;
        for (int batch = 0; batch < requested; batch++) {
            if (reserveEnergyBatch(perBatch, insert, capabilities, reservations) == null) break;
            maximum++;
        }
        return maximum;
    }

    private static List<EnergyAction> reserveEnergyBatch(long amount, boolean insert,
                                                          List<MachineCapability> capabilities,
                                                          PlanningReservations reservations) {
        long remaining = amount;
        List<EnergyAction> actions = new ArrayList<>();
        for (MachineCapability capability : capabilities) {
            if (!(capability.storage() instanceof LongValueStorage storage)) continue;
            long available = reservations.valueAvailable(storage, insert);
            long movable = insert ? storage.insert(Math.min(remaining, available), true)
                    : storage.extract(Math.min(remaining, available), true);
            long moved = Math.min(remaining, Math.min(available, movable));
            if (moved <= 0L || !reservations.reserveValue(storage, moved, insert)) continue;
            actions.add(new EnergyAction(capability, moved));
            remaining -= moved;
            if (remaining == 0L) break;
        }
        return remaining == 0L ? actions : null;
    }

    private static long consumedPrefix(boolean[] consumed, int parallelism) {
        long batches = 0L;
        for (int index = 0; index < parallelism; index++) if (consumed[index]) batches++;
        return batches;
    }

    private static boolean[] consumeDecisions(float chance, int parallelism) {
        boolean[] decisions = new boolean[parallelism];
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

    private static final class ItemHandler implements LegacyHandler<ItemRequirement> {
        @Override
        public RequirementType<ItemRequirement> type() {
            return ItemRequirement.TYPE;
        }

        @Override
        public RequirementPlan plan(ItemRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return planItem(requirement, capabilities, context);
        }

        @Override
        public boolean simulate(ItemRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
            return requirement.io() == RecipeModifier.IOType.INPUT
                    ? context.simulateItemInput(requirementIndex, requirement)
                    : context.simulateItemOutput(requirementIndex, requirement);
        }

        @Override
        public boolean commit(ItemRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
            return requirement.io() == RecipeModifier.IOType.INPUT
                    ? context.collectItemInputRoute(requirementIndex)
                    : context.collectItemOutputRoute(requirementIndex);
        }

        @Override
        public int maxInputParallelism(ItemRequirement requirement, RecipeCraftingContext context, int limit) {
            if (requirement.io() != RecipeModifier.IOType.INPUT || requirement.item() == null || requirement.count() <= 0) return -1;
            if (requirement.consumeChance() == 0F) return Math.max(1, limit);
            if (!requirement.tags().isEmpty() || !requirement.components().isEmpty()) return -1;
            try {
                if (requirement.item().items().count() != 1) return -1;
            } catch (UnsupportedOperationException ignored) {
                return -1;
            }
            int available = context.countMatchingItemInputs(requirement.item(), List.of());
            return Math.min(Math.max(1, limit), available / requirement.count());
        }
    }

    private static final class FluidHandler implements LegacyHandler<FluidRequirement> {
        @Override
        public RequirementType<FluidRequirement> type() {
            return FluidRequirement.TYPE;
        }

        @Override
        public RequirementPlan plan(FluidRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return planFluid(requirement, capabilities, context);
        }

        @Override
        public boolean simulate(FluidRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
            return requirement.io() == RecipeModifier.IOType.INPUT
                    ? context.simulateFluidInput(requirementIndex, requirement)
                    : context.simulateFluidOutput(requirementIndex, requirement);
        }

        @Override
        public boolean commit(FluidRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
            return requirement.io() == RecipeModifier.IOType.INPUT
                    ? context.collectFluidInputRoute(requirementIndex)
                    : context.collectFluidOutputRoute(requirementIndex);
        }
    }

    private static final class EnergyHandler implements LegacyHandler<EnergyRequirement> {
        @Override
        public RequirementType<EnergyRequirement> type() {
            return EnergyRequirement.TYPE;
        }

        @Override
        public RequirementPlan plan(EnergyRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return planEnergy(requirement, capabilities, context);
        }

        @Override
        public boolean simulate(EnergyRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
            return requirement.io() == RecipeModifier.IOType.INPUT
                    ? context.simulateEnergyInput(requirementIndex, requirement)
                    : context.simulateEnergyOutput(requirementIndex, requirement);
        }

        @Override
        public boolean commit(EnergyRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
            return requirement.io() == RecipeModifier.IOType.INPUT
                    ? context.collectEnergyInputRoute(requirementIndex)
                    : context.collectEnergyOutputRoute(requirementIndex);
        }

        @Override
        public boolean ioTick(EnergyRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
            if (requirement.io() == RecipeModifier.IOType.INPUT) {
                if (EnergyRecipeIo.consumeInputs(context.taggedEnergyStorages(requirement.tags()), requirement.fePerTick(), 1)) return true;
                return context.simulateEnergyInput(requirementIndex, requirement);
            }
            if (EnergyRecipeIo.produceOutputs(context.taggedEnergyOutputs(requirement.tags()), requirement.fePerTick(), 1)) return true;
            return context.simulateEnergyOutput(requirementIndex, requirement);
        }
    }

    private static final class SmartInterfaceHandler implements LegacyHandler<SmartInterfaceRequirement> {
        @Override
        public RequirementType<SmartInterfaceRequirement> type() {
            return SmartInterfaceRequirement.TYPE;
        }

        @Override
        public RequirementPlan plan(SmartInterfaceRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return planSmartInterface(requirement, capabilities, context);
        }

        @Override
        public boolean simulate(SmartInterfaceRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
            if (requirement.io() == RecipeModifier.IOType.INPUT) {
                boolean matches = context.smartInterfaceValue(requirement.interfaceType())
                        .filter(value -> value >= requirement.minValue() && value <= requirement.maxValue())
                        .isPresent();
                if (!matches) context.setRequirementFailure(context.smartInterfaceFailureMessage(requirement.interfaceType()), null);
                return matches;
            }
            return context.hasSmartInterface(requirement.interfaceType());
        }

        @Override
        public boolean commit(SmartInterfaceRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
            return requirement.io() == RecipeModifier.IOType.INPUT
                    || context.setSmartInterfaceValue(requirement.interfaceType(), requirement.minValue());
        }
    }
}
