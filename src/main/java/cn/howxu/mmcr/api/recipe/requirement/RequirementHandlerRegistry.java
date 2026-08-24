package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

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

    static boolean simulate(MachineRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
        return legacyHandler(requirement).simulate(requirement, context, requirementIndex);
    }

    static boolean commit(MachineRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
        return legacyHandler(requirement).commit(requirement, context, requirementIndex);
    }

    static int maxInputParallelism(MachineRequirement requirement, RecipeCraftingContext context, int limit) {
        return legacyHandler(requirement).maxInputParallelism(requirement, context, limit);
    }

    static boolean ioTick(MachineRequirement requirement, RecipeCraftingContext context, int requirementIndex) {
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
            return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
        }
    }

    private static final class ItemHandler implements LegacyHandler<ItemRequirement> {
        @Override
        public RequirementType<ItemRequirement> type() {
            return ItemRequirement.TYPE;
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
