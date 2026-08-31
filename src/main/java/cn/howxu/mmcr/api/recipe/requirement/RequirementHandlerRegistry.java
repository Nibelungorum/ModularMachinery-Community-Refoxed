package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of requirement types used by recipe planning.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RequirementHandlerRegistry {
    private static final Map<RequirementType<?>, RequirementHandler<?>> HANDLERS = new ConcurrentHashMap<>();
    private static final Map<Identifier, RequirementType<?>> CANONICAL_TYPES = new ConcurrentHashMap<>();
    private static final Object MUTATION_LOCK = new Object();
    private static final List<Identifier> BUILT_IN_IDS = List.of(
            Identifier.fromNamespaceAndPath("mmcr", "item"),
            Identifier.fromNamespaceAndPath("mmcr", "fluid"),
            Identifier.fromNamespaceAndPath("mmcr", "energy"),
            Identifier.fromNamespaceAndPath("mmcr", "smart_interface"));

    private RequirementHandlerRegistry() {
    }

    public static <R extends MachineRequirement> void register(RequirementType<R> type) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (type.id() == null) throw new IllegalArgumentException("type id must not be null");
        if (type.codec() == null) throw new IllegalArgumentException("type codec must not be null");
        RequirementHandler<R> handler = type.handler();
        if (handler == null) throw new IllegalArgumentException("type handler must not be null");
        if (isBuiltIn(type.id())) throw new IllegalArgumentException("built-in requirement type is reserved: " + type.id());
        synchronized (MUTATION_LOCK) {
            if (CANONICAL_TYPES.containsKey(type.id())) {
                throw new IllegalArgumentException("Duplicate requirement handler type: " + type.id());
            }
            CANONICAL_TYPES.put(type.id(), type);
            HANDLERS.put(type, handler);
        }
    }

    public static RequirementHandler<?> handlerFor(RequirementType<?> type) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        RequirementType<?> canonical = canonicalType(type);
        return canonical == null || canonical != type ? null : HANDLERS.get(canonical);
    }

    public static RequirementType<?> canonicalType(RequirementType<?> type) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        RequirementType<?> canonical = CANONICAL_TYPES.get(type.id());
        if (canonical == null && isBuiltIn(type.id())) {
            registerBuiltIns();
            canonical = CANONICAL_TYPES.get(type.id());
        }
        return canonical;
    }

    public static RequirementType<?> typeFor(Identifier id) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        RequirementType<?> type = CANONICAL_TYPES.get(id);
        if (type == null && isBuiltIn(id)) {
            registerBuiltIns();
            type = CANONICAL_TYPES.get(id);
        }
        return type;
    }

    public static List<RequirementHandler.ResourceWakeup> resourceWakeupsFor(MachineRequirement requirement) {
        if (requirement == null) throw new IllegalArgumentException("requirement must not be null");
        RequirementType<?> canonical = canonicalType(requirement.type());
        if (canonical == null || canonical != requirement.type()) return List.of();
        return resourceWakeups(canonical, requirement);
    }

    public static MachineRequirement applyModifiers(MachineRequirement requirement,
                                                    List<RecipeModifier> modifiers) {
        return dispatch(requirement, (handler, value) -> handler.applyModifiers(value, modifiers));
    }

    public static MachineRequirement applyLevelModifiers(MachineRequirement requirement,
                                                         double energyMultiplier, double outputMultiplier) {
        return dispatch(requirement, (handler, value) ->
                handler.applyLevelModifiers(value, energyMultiplier, outputMultiplier));
    }

    public static boolean overlaps(MachineRequirement requirement, MachineRequirement other) {
        if (requirement == null || other == null) return false;
        return dispatch(requirement, (handler, value) -> handler.overlaps(value, other));
    }

    public static MachineIngredient legacyInput(MachineRequirement requirement) {
        return dispatch(requirement, (handler, value) -> handler.legacyInput(value));
    }

    public static ItemStack legacyItemOutput(MachineRequirement requirement) {
        return dispatch(requirement, (handler, value) -> handler.legacyItemOutput(value));
    }

    public static FluidStack legacyFluidOutput(MachineRequirement requirement) {
        return dispatch(requirement, (handler, value) -> handler.legacyFluidOutput(value));
    }

    public static Integer legacyEnergyOutput(MachineRequirement requirement) {
        return dispatch(requirement, (handler, value) -> handler.legacyEnergyOutput(value));
    }

    public static void registerBuiltIns() {
        registerBuiltIn(ItemRequirement.TYPE);
        registerBuiltIn(FluidRequirement.TYPE);
        registerBuiltIn(EnergyRequirement.TYPE);
        registerBuiltIn(SmartInterfaceRequirement.TYPE);
    }

    /**
     * Starts a test scope that removes custom registrations on entry and exit while retaining built-ins.
     *
     * @return an auto-closeable registry scope
     */
    public static TestScope openTestScope() {
        synchronized (MUTATION_LOCK) {
            clearCustomTypes();
        }
        return new TestScope();
    }

    public static final class TestScope implements AutoCloseable {
        private boolean closed;

        private TestScope() {
        }

        @Override
        public void close() {
            if (closed) return;
            synchronized (MUTATION_LOCK) {
                clearCustomTypes();
                closed = true;
            }
        }
    }

    private static void registerBuiltIn(RequirementType<?> type) {
        synchronized (MUTATION_LOCK) {
            RequirementType<?> existing = CANONICAL_TYPES.putIfAbsent(type.id(), type);
            if (existing == null) {
                HANDLERS.put(type, type.handler());
            } else if (existing != type) {
                throw new IllegalStateException("Conflicting built-in requirement type: " + type.id());
            }
        }
    }

    private static void clearCustomTypes() {
        CANONICAL_TYPES.keySet().removeIf(id -> !isBuiltIn(id));
        HANDLERS.keySet().removeIf(type -> !isBuiltIn(type.id()));
    }

    private static boolean isBuiltIn(Identifier id) {
        return BUILT_IN_IDS.contains(id);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> T dispatch(MachineRequirement requirement, HandlerCall<T> call) {
        if (requirement == null || requirement.type() == null) return null;
        RequirementHandler<?> handler = handlerFor(requirement.type());
        return handler == null ? null : call.apply((RequirementHandler) handler, requirement);
    }

    @FunctionalInterface
    private interface HandlerCall<T> {
        T apply(RequirementHandler handler, MachineRequirement requirement);
    }

    @SuppressWarnings("unchecked")
    private static <R extends MachineRequirement> List<RequirementHandler.ResourceWakeup> resourceWakeups(
            RequirementType<R> type, MachineRequirement requirement) {
        return type.handler().resourceWakeups((R) requirement);
    }
}
