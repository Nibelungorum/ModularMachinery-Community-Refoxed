package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRoleValidator;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.registration.ApiRegistrationException;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns the atomic startup collection and commit of public content declarations.
 * @author howxu <dev@howxu.cn>
 */
public final class ContentRegistrationCoordinator {
    private static MachineDefinitionRegistration startupDefinitions = new MachineDefinitionRegistration();
    private static final Map<Identifier, cn.howxu.mmcr.api.machine.definition.MachineStructureDefinition> STRUCTURES =
            new LinkedHashMap<>();
    private static final Map<Identifier, MachineRecipeDefinition> RECIPES = new LinkedHashMap<>();
    private static StructureRegistration.Snapshot STRUCTURE_SNAPSHOT = emptyStructureSnapshot();
    private static State state = State.BEFORE_BEGIN;
    private static int testCommitCount;
    private static StartupSnapshotForTesting lastStartupSnapshot = new StartupSnapshotForTesting(Set.of(), Set.of(), Set.of());

    private ContentRegistrationCoordinator() {
    }

    public static synchronized void beginStartup() {
        // KubeJS plugins can collect declarations before MMCR's construct event begins startup.
        if (state != State.BEFORE_BEGIN) return;
        STRUCTURES.clear();
        RECIPES.clear();
        STRUCTURE_SNAPSHOT = emptyStructureSnapshot();
        state = State.COLLECTING;
    }

    public static synchronized boolean isCommitted() {
        return state == State.COMMITTED;
    }

    /** Shared startup declarations, also available to scripts that run before beginStartup. */
    public static synchronized MachineDefinitionRegistration startupDefinitions() {
        return startupDefinitions;
    }

    public static synchronized void collectMachines(MachineDefinitionRegistration event) {
        requireCollecting();
        startupDefinitions.collectFrom(event);
    }

    public static synchronized void collectMachine(MachineDefinition definition) {
        requireCollecting();
        startupDefinitions.registerMachine(definition);
    }

    public static synchronized void collectStructures(StructureRegistration event) {
        requireCollecting();
        STRUCTURE_SNAPSHOT = event.freeze();
        event.structures().forEach((id, structure) -> putUnique(STRUCTURES, id, structure, "structure"));
        MachineLevelRegistry.validateSnapshot(STRUCTURE_SNAPSHOT.levelTypes().values(),
                STRUCTURE_SNAPSHOT.levels().values());
    }

    public static synchronized void collectRecipes(MachineRecipeRegistration event) {
        requireCollecting();
        event.recipes().forEach((id, recipe) -> putUnique(RECIPES, id, recipe, "recipe"));
    }

    public static synchronized void commitStartup() {
        synchronized (RuntimeContentVersion.lock()) {
        if (state == State.COMMITTED) {
            return;
        }
        requireCollecting();

        commitStructures();
        commitModifierItemsAfterComponentsBound();
        commitRecipes();
        testCommitCount++;
        lastStartupSnapshot = new StartupSnapshotForTesting(
                Set.copyOf(startupDefinitions.definitions().keySet()), Set.copyOf(STRUCTURES.keySet()), Set.copyOf(RECIPES.keySet()));
        }
    }

    /**
     * Validates and publishes machines, structures, levels, and modifiers. After this call,
     * {@link MachineDefinitions#getRegistration(Identifier)} and
     * {@link MachineLevelRegistry} are populated for downstream consumers that run between
     * structure collection and recipe collection (e.g. KubeJS server script reload, datapack
     * recipe parsing). Recipes are not yet committed.
     */
    public static synchronized void commitStructures() {
        requireCollecting();

        var modifiers = startupDefinitions.modifierSnapshot();
        Set<Identifier> registeredIds = modifiers.modifiers().keySet();
        Map<Identifier, MachineRegistration> registrations = validateAndConvertMachines(registeredIds);
        Map<Identifier, MachineStructureDefinition> structures = validateAndConvertStructures(registrations, registeredIds);
        MachineLevelRegistry.installSnapshot(STRUCTURE_SNAPSHOT.levelTypes().values(),
                STRUCTURE_SNAPSHOT.levels().values());
        startupDefinitions.freeze();
        ModifierRegistry.installSnapshot(modifiers.modifiers(), Map.of(), modifiers.stacking(), modifiers.modifierItemLimits());
        registrations.values().forEach(registration -> {
            if (MachineDefinitions.containsStatic(registration.id())) {
                MachineDefinitions.replace(registration);
            } else {
                MachineDefinitions.register(registration);
            }
        });
        MachineStructureRegistry.replaceStartup(structures);
    }

    /** Resolves startup item IDs only after their default data components are available. */
    public static synchronized void commitModifierItemsAfterComponentsBound() {
        requireCollecting();
        var modifiers = startupDefinitions.modifierSnapshot();
        ModifierRegistry.installSnapshot(modifiers.modifiers(), modifiers.resolveModifierItems(),
                modifiers.stacking(), modifiers.modifierItemLimits());
    }

    /**
     * Validates and publishes recipes against the already-committed machines and structures.
     */
    public static synchronized void commitRecipes() {
        requireCollecting();

        Map<Identifier, MachineRecipe> recipes = validateAndConvertRecipes();
        validateRecipeDuplicates(recipes);

        // Prepare and publish recipes before any other registry is changed. The batch validates the
        // complete candidate first, so a recipe failure leaves startup registries untouched.
        RecipeRegistry.registerStaticBatch(recipes.values());
        MachineDefinitions.freezeRegistryPhase();
        PublicApiBootstrap.freeze();
        state = State.COMMITTED;
    }

    /** Test-only counter for verifying that bootstrap paths share this coordinator. */
    public static synchronized int commitCountForTesting() {
        return testCommitCount;
    }

    /** Test-only declaration snapshot for comparing complete bootstrap paths.
     * @author howxu <dev@howxu.cn>
     */
    public record StartupSnapshotForTesting(Set<Identifier> machines, Set<Identifier> structures,
            Set<Identifier> recipes) {
    }

    public static synchronized StartupSnapshotForTesting startupSnapshotForTesting() {
        return lastStartupSnapshot;
    }

    /** Test-only reset hook; resets coordinator-owned collection state only. */
    public static synchronized void clearForTesting() {
        startupDefinitions = new MachineDefinitionRegistration();
        STRUCTURES.clear();
        RECIPES.clear();
        STRUCTURE_SNAPSHOT = emptyStructureSnapshot();
        state = State.BEFORE_BEGIN;
        testCommitCount = 0;
        lastStartupSnapshot = new StartupSnapshotForTesting(Set.of(), Set.of(), Set.of());
        StartupContentRegistration.resetForTesting();
    }

    /** Resets the complete startup test seam, including public API lifecycle state. */
    public static synchronized void resetForTesting() {
        clearForTesting();
        StructureRegistration.resetCollector();
        PublicApiBootstrap.resetStateForTesting();
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        MachineLevelRegistry.installSnapshot(List.of(), List.of());
        ModifierRegistry.installSnapshot(Map.of());
        RecipeRegistry.clearForTesting();
    }

    private static Map<Identifier, MachineRegistration> validateAndConvertMachines(Set<Identifier> registeredIds) {
        Map<Identifier, MachineRegistration> registrations = new LinkedHashMap<>();
        startupDefinitions.definitions().forEach((id, definition) -> {
            for (Identifier modifierId : definition.allowedModifierIds()) {
                if (!registeredIds.contains(modifierId)) throw new ApiRegistrationException(
                        "Machine " + id + " allows unknown machine modifier " + modifierId);
            }
            registrations.put(id, MachineDefinitionConverter.toStartupRegistration(definition, STRUCTURES.get(id)));
        });
        Map<Identifier, MachineRegistration> all = new LinkedHashMap<>();
        MachineDefinitions.allRegistrations().forEach(registration -> all.put(registration.id(), registration));
        all.putAll(registrations);
        MachineRoleValidator.validate(all.values(), all::get);
        return registrations;
    }

    private static Map<Identifier, MachineStructureDefinition> validateAndConvertStructures(
            Map<Identifier, MachineRegistration> registrations, Set<Identifier> registeredIds) {
        Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
        STRUCTURES.forEach((id, structure) -> {
            if (!registrations.containsKey(id) && MachineDefinitions.getRegistration(id) == null) {
                throw new ApiRegistrationException("Structure " + id + " refers to unknown machine " + id);
            }
            MachineRegistration registration = registrations.get(id);
            if (registration == null) registration = MachineDefinitions.getRegistration(id);
            MachineStructureDefinition converted = MachineDefinitionConverter.toStructureDefinition(structure);
            converted.validateModifiers(registration.allowedModifierIds(), registeredIds);
            MachineStructureRegistry.toRuntimeMachine(registration, converted);
            structures.put(id, converted);
        });
        return structures;
    }

    private static Map<Identifier, MachineRecipe> validateAndConvertRecipes() {
        var modifiers = startupDefinitions.modifiers();
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        RECIPES.forEach((id, definition) -> {
            recipes.put(id, MachineRecipeConverter.toRecipe(definition, STRUCTURE_SNAPSHOT.levels(), modifiers));
        });
        return recipes;
    }

    private static void validateDuplicates(Map<Identifier, MachineRegistration> registrations,
            Map<Identifier, MachineStructureDefinition> structures, Map<Identifier, MachineRecipe> recipes) {
        structures.keySet().forEach(id -> {
            if (!id.equals(structures.get(id).machineId())) {
                throw new ApiRegistrationException("Structure key does not match machine id: " + id);
            }
        });
        recipes.keySet().forEach(id -> {
            if (RecipeRegistry.containsStatic(id)) throw duplicate(id, "recipe");
        });
    }

    private static void validateRecipeDuplicates(Map<Identifier, MachineRecipe> recipes) {
        recipes.keySet().forEach(id -> {
            if (RecipeRegistry.containsStatic(id)) throw duplicate(id, "recipe");
        });
    }

    private static StructureRegistration.Snapshot emptyStructureSnapshot() {
        return new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of());
    }

    private static <V> void putUnique(Map<Identifier, V> target, Identifier id, V value, String kind) {
        if (target.putIfAbsent(id, value) != null) throw duplicate(id, kind);
    }

    private static ApiRegistrationException duplicate(Identifier id, String kind) {
        return new ApiRegistrationException("Duplicate " + kind + " ID " + id + " during startup collection");
    }

    private static void requireCollecting() {
        if (state != State.COLLECTING) {
            throw new ApiRegistrationException("Startup content collection rejected: lifecycle is " + state);
        }
    }

    private enum State {
        BEFORE_BEGIN, COLLECTING, COMMITTED
    }
}
