package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
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

    @SuppressWarnings("unchecked")
    private static <R> ResourceStorage<R> resourceStorage(MachineCapability capability) {
        return capability.storage() instanceof ResourceStorage<?> storage
                ? (ResourceStorage<R>) storage : null;
    }

    private static long scaled(long amount, int parallelism) {
        try {
            return Math.multiplyExact(amount, parallelism);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private record ResourceActions<R>(MachineCapability capability,
                                      List<CapabilityRequests.ResourceAction<R>> actions) {
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
        long requested = scaled(requirement.fePerTick(), context.requestedParallelism());
        if (requested <= 0) return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        boolean insert = requirement.io() == RecipeModifier.IOType.OUTPUT;
        long available = 0L;
        for (MachineCapability capability : capabilities) {
            if (!(capability.storage() instanceof LongValueStorage storage)) continue;
            available = saturatingAdd(available, insert
                    ? storage.insert(requested, true) : storage.extract(requested, true));
        }
        if (available < requested) {
            long perBatch = Math.max(1, requirement.fePerTick());
            int maximum = (int) Math.min(context.requestedParallelism(), available / perBatch);
            return maximum <= 0
                    ? blockedPlan(requirement, context, "insufficient_energy")
                    : new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
        }

        long remaining = requested;
        List<CapabilityOperation> operations = new ArrayList<>();
        for (MachineCapability capability : capabilities) {
            if (!(capability.storage() instanceof LongValueStorage storage)) continue;
            long moved = insert ? storage.insert(remaining, true) : storage.extract(remaining, true);
            if (moved <= 0) continue;
            operations.add(capability.prepare(new CapabilityRequests.ValueRequest(
                    capability.view().type(), capability.view().ioType(), context.requestedParallelism(), moved, insert)));
            remaining -= moved;
            if (remaining == 0) break;
        }
        return remaining == 0
                ? new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), operations, null)
                : blockedPlan(requirement, context, "insufficient_energy");
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
        int consumedBatches = requirement.io() == RecipeModifier.IOType.INPUT
                ? consumedBatches(requirement.consumeChance(), parallelism) : parallelism;
        long amount = requirement.io() == RecipeModifier.IOType.INPUT
                ? scaled(requirement.count(), consumedBatches)
                : scaled(requirement.stack(null).getCount(), parallelism);
        if (amount <= 0) return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        if (requirement.io() == RecipeModifier.IOType.INPUT && consumedBatches == 0) {
            long available = matchingItemAmount(requirement, capabilities);
            if (available < amount) return itemLimit(requirement, context, available, requirement.count());
            return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        }

        ItemStack requestedStack = requirement.io() == RecipeModifier.IOType.INPUT
                ? ItemStack.EMPTY : requirement.stack(null);
        ItemResource requestedResource = requestedStack.isEmpty() ? null : ItemResource.of(requestedStack);
        Map<MachineCapability, List<CapabilityRequests.ResourceAction<ItemResource>>> actionMap = new LinkedHashMap<>();
        long remaining = amount;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<ItemResource> storage = resourceStorage(capability);
            if (storage == null) continue;
            List<CapabilityRequests.ResourceAction<ItemResource>> actions = new ArrayList<>();
            for (int slot = 0; slot < storage.size() && remaining > 0; slot++) {
                ItemResource current = storage.resource(slot);
                if (requirement.io() == RecipeModifier.IOType.INPUT) {
                    if (current.isEmpty() || !matchesItem(requirement, current)) continue;
                    long moved = Math.min(remaining, storage.amount(slot));
                    if (moved > 0) {
                        actions.add(new CapabilityRequests.ResourceAction<>(slot, current, moved, false));
                        remaining -= moved;
                    }
                } else if (current.equals(requestedResource) || current.isEmpty()) {
                    long capacity = Math.min(storage.capacity(slot, requestedResource), requestedResource.getMaxStackSize());
                    long moved = Math.min(remaining, Math.max(0, capacity - storage.amount(slot)));
                    if (moved > 0 && storage.isValid(slot, requestedResource)) {
                        actions.add(new CapabilityRequests.ResourceAction<>(slot, requestedResource, moved, true));
                        remaining -= moved;
                    }
                }
            }
            if (!actions.isEmpty()) actionMap.put(capability, actions);
            if (remaining == 0) break;
        }
        if (remaining > 0 && requirement.io() == RecipeModifier.IOType.OUTPUT && context.allowPartialOutputs()) {
            return actionMap.isEmpty()
                    ? blockedPlan(requirement, context, "no_output_capacity")
                    : resourcePlan(requirement, context, actionMap);
        }
        if (remaining > 0) {
            long available = amount - remaining;
            long perBatch = requirement.io() == RecipeModifier.IOType.INPUT ? requirement.count() : requestedStack.getCount();
            return itemLimit(requirement, context, available, perBatch);
        }
        return resourcePlan(requirement, context, actionMap);
    }

    private static RequirementPlan planFluid(FluidRequirement requirement,
                                             List<MachineCapability> capabilities,
                                             PlanningContext context) {
        int parallelism = context.requestedParallelism();
        if (requirement.io() == RecipeModifier.IOType.OUTPUT && !shouldProduce(requirement.chance())) {
            return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        }
        long amount = requirement.io() == RecipeModifier.IOType.INPUT
                ? scaled(requirement.amount(), parallelism)
                : scaled(requirement.stack().getAmount(), parallelism);
        if (amount <= 0) return new RequirementPlan(context.requirementIndex(), parallelism, List.of(), null);
        FluidStack requestedStack = requirement.io() == RecipeModifier.IOType.INPUT
                ? FluidStack.EMPTY : requirement.stack().copy();
        FluidResource requestedResource = requestedStack.isEmpty() ? null : FluidResource.of(requestedStack);
        Map<MachineCapability, List<CapabilityRequests.ResourceAction<FluidResource>>> actionMap = new LinkedHashMap<>();
        long remaining = amount;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<FluidResource> storage = resourceStorage(capability);
            if (storage == null) continue;
            List<CapabilityRequests.ResourceAction<FluidResource>> actions = new ArrayList<>();
            for (int slot = 0; slot < storage.size() && remaining > 0; slot++) {
                FluidResource current = storage.resource(slot);
                if (requirement.io() == RecipeModifier.IOType.INPUT) {
                    if (current.isEmpty() || requirement.fluid() == null
                            || !requirement.fluid().test(current.toStack((int) Math.min(storage.amount(slot), Integer.MAX_VALUE)))) continue;
                    long moved = Math.min(remaining, storage.amount(slot));
                    if (moved > 0) {
                        actions.add(new CapabilityRequests.ResourceAction<>(slot, current, moved, false));
                        remaining -= moved;
                    }
                } else if ((current.isEmpty() || current.equals(requestedResource))
                        && storage.isValid(slot, requestedResource)) {
                    long moved = Math.min(remaining, Math.max(0, storage.capacity(slot, requestedResource) - storage.amount(slot)));
                    if (moved > 0) {
                        actions.add(new CapabilityRequests.ResourceAction<>(slot, requestedResource, moved, true));
                        remaining -= moved;
                    }
                }
            }
            if (!actions.isEmpty()) actionMap.put(capability, actions);
            if (remaining == 0) break;
        }
        if (remaining > 0 && requirement.io() == RecipeModifier.IOType.OUTPUT && context.allowPartialOutputs()) {
            return actionMap.isEmpty()
                    ? blockedPlan(requirement, context, "no_output_capacity")
                    : resourcePlan(requirement, context, actionMap);
        }
        if (remaining > 0) {
            long available = amount - remaining;
            long perBatch = requirement.io() == RecipeModifier.IOType.INPUT ? requirement.amount() : requirement.stack().getAmount();
            return itemLimit(requirement, context, available, perBatch);
        }
        return resourcePlan(requirement, context, actionMap);
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
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(),
                    List.of(capability.prepare(new CapabilityRequests.SmartValueRequest(
                            capability.view().type(), capability.view().ioType(), context.requestedParallelism(),
                            requirement.interfaceType(), requirement.minValue()))), null);
        }
        return blockedPlan(requirement, context, "missing_smart_interface");
    }

    private static boolean matchesItem(ItemRequirement requirement, ItemResource resource) {
        ItemStack stack = resource.toStack((int) Math.min(resource.getMaxStackSize(), Integer.MAX_VALUE));
        return requirement.item().test(stack) && requirement.components().matches(stack);
    }

    private static long matchingItemAmount(ItemRequirement requirement, List<MachineCapability> capabilities) {
        long amount = 0;
        for (MachineCapability capability : capabilities) {
            ResourceStorage<ItemResource> storage = resourceStorage(capability);
            if (storage == null) continue;
            for (int slot = 0; slot < storage.size(); slot++) {
                if (!storage.resource(slot).isEmpty() && matchesItem(requirement, storage.resource(slot))) {
                    amount = saturatingAdd(amount, storage.amount(slot));
                }
            }
        }
        return amount;
    }

    private static RequirementPlan itemLimit(MachineRequirement requirement, PlanningContext context,
                                             long available, long perBatch) {
        int maximum = (int) Math.min(context.requestedParallelism(), perBatch <= 0 ? context.requestedParallelism() : available / perBatch);
        return maximum <= 0 ? blockedPlan(requirement, context, "insufficient_resource")
                : new RequirementPlan(context.requirementIndex(), maximum, List.of(), null);
    }

    private static <R> RequirementPlan resourcePlan(MachineRequirement requirement, PlanningContext context,
                                                    Map<MachineCapability, List<CapabilityRequests.ResourceAction<R>>> actionMap) {
        List<CapabilityOperation> operations = actionMap.entrySet().stream()
                .map(entry -> entry.getKey().prepare(new CapabilityRequests.ResourceRequest<>(
                        entry.getKey().view().type(), entry.getKey().view().ioType(),
                        context.requestedParallelism(), entry.getValue())))
                .toList();
        return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), operations, null);
    }

    private static long saturatingAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return first + second;
    }

    private static boolean shouldProduce(float chance) {
        return chance >= 1F || chance > 0F && Math.random() < chance;
    }

    private static int consumedBatches(float chance, int parallelism) {
        if (chance <= 0F) return 0;
        if (chance >= 1F) return parallelism;
        int consumed = 0;
        for (int batch = 0; batch < parallelism; batch++) {
            if (Math.random() < chance) consumed++;
        }
        return consumed;
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
