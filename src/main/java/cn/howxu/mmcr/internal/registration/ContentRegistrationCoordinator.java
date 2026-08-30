package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRoleValidator;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.api.PublicRecipeAdapter;
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
    private static final Map<Identifier, MachineDefinition> MACHINES = new LinkedHashMap<>();
    private static final Map<Identifier, cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition> STRUCTURES =
            new LinkedHashMap<>();
    private static final Map<Identifier, MachineRecipeDefinition> RECIPES = new LinkedHashMap<>();
    private static MMCRMachineStructuresEvent.Snapshot STRUCTURE_SNAPSHOT = emptyStructureSnapshot();
    private static State state = State.BEFORE_BEGIN;
    private static int testCommitCount;
    private static StartupSnapshotForTesting lastStartupSnapshot = new StartupSnapshotForTesting(Set.of(), Set.of(), Set.of());

    private ContentRegistrationCoordinator() {
    }

    public static synchronized void beginStartup() {
        if (state == State.COMMITTED) return;
        MACHINES.clear();
        STRUCTURES.clear();
        RECIPES.clear();
        STRUCTURE_SNAPSHOT = emptyStructureSnapshot();
        state = State.COLLECTING;
    }

    public static synchronized boolean isCommitted() {
        return state == State.COMMITTED;
    }

    public static synchronized void collectMachines(MMCRMachineDefinationsEvent event) {
        requireCollecting();
        event.definitions().forEach((id, definition) -> putUnique(MACHINES, id, definition, "machine"));
    }

    public static synchronized void collectMachine(MachineDefinition definition) {
        requireCollecting();
        putUnique(MACHINES, definition.id(), definition, "machine");
    }

    public static synchronized void collectStructures(MMCRMachineStructuresEvent event) {
        requireCollecting();
        STRUCTURE_SNAPSHOT = event.freeze();
        event.structures().forEach((id, structure) -> putUnique(STRUCTURES, id, structure, "structure"));
    }

    public static synchronized void collectRecipes(MMCRMachineRecipesEvent event) {
        requireCollecting();
        event.recipes().forEach((id, recipe) -> putUnique(RECIPES, id, recipe, "recipe"));
    }

    public static synchronized void commitStartup() {
        synchronized (RuntimeContentVersion.lock()) {
        if (state == State.COMMITTED) {
            return;
        }
        requireCollecting();

        Map<Identifier, MachineRegistration> registrations = validateAndConvertMachines();
        Map<Identifier, MachineStructureDefinition> structures = validateAndConvertStructures(registrations);
        MachineLevelRegistry.installSnapshot(STRUCTURE_SNAPSHOT.levelTypes().values(), STRUCTURE_SNAPSHOT.levels().values());
        Map<Identifier, MachineRecipe> recipes = validateAndConvertRecipes();
        validateDuplicates(registrations, structures, recipes);

        // Prepare and publish recipes before any other registry is changed. The batch validates the
        // complete candidate first, so a recipe failure leaves startup registries untouched.
        RecipeRegistry.registerStaticBatch(recipes.values());
        ModifierRegistry.installSnapshot(STRUCTURE_SNAPSHOT.modifiers(), STRUCTURE_SNAPSHOT.modifierItems());
        registrations.values().forEach(registration -> {
            if (MachineDefinitions.containsStatic(registration.id())) {
                MachineDefinitions.replace(registration);
            } else {
                MachineDefinitions.register(registration);
            }
        });
        MachineStructureRegistry.replaceStartup(structures);
        MachineDefinitions.freezeRegistryPhase();
        state = State.COMMITTED;
        testCommitCount++;
        lastStartupSnapshot = new StartupSnapshotForTesting(
                Set.copyOf(MACHINES.keySet()), Set.copyOf(STRUCTURES.keySet()), Set.copyOf(RECIPES.keySet()));
        }
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
        MACHINES.clear();
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
        MMCRMachineStructuresEvent.resetCollector();
        PublicApiBootstrap.resetStateForTesting();
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        MachineLevelRegistry.installSnapshot(List.of(), List.of());
        RecipeRegistry.clearForTesting();
    }

    private static Map<Identifier, MachineRegistration> validateAndConvertMachines() {
        Map<Identifier, MachineRegistration> registrations = new LinkedHashMap<>();
        MACHINES.forEach((id, definition) -> registrations.put(id,
                PublicMachineAdapter.toStartupRegistration(definition, STRUCTURES.get(id))));
        Map<Identifier, MachineRegistration> all = new LinkedHashMap<>();
        MachineDefinitions.allRegistrations().forEach(registration -> all.put(registration.id(), registration));
        all.putAll(registrations);
        MachineRoleValidator.validate(all.values(), all::get);
        return registrations;
    }

    private static Map<Identifier, MachineStructureDefinition> validateAndConvertStructures(
            Map<Identifier, MachineRegistration> registrations) {
        MMCRMachineStructuresEvent.Snapshot snapshot = STRUCTURE_SNAPSHOT;
        Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
        STRUCTURES.forEach((id, structure) -> {
            if (!registrations.containsKey(id) && MachineDefinitions.getRegistration(id) == null) {
                throw new ApiRegistrationException("Structure " + id + " refers to unknown machine " + id);
            }
            MachineRegistration registration = registrations.get(id);
            if (registration == null) registration = MachineDefinitions.getRegistration(id);
            MachineStructureDefinition converted = PublicMachineAdapter.toStructureDefinition(structure, snapshot.modifiers());
            MachineStructureRegistry.toRuntimeMachine(registration, converted);
            structures.put(id, converted);
        });
        return structures;
    }

    private static Map<Identifier, MachineRecipe> validateAndConvertRecipes() {
        MMCRMachineStructuresEvent.Snapshot snapshot = STRUCTURE_SNAPSHOT;
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        RECIPES.forEach((id, definition) -> {
            if (!MACHINES.containsKey(definition.machineId())
                    && MachineDefinitions.getRegistration(definition.machineId()) == null) {
                throw new ApiRegistrationException("Recipe " + id + " refers to unknown machine "
                        + definition.machineId());
            }
            recipes.put(id, PublicRecipeAdapter.toRecipe(definition, snapshot));
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

    private static MMCRMachineStructuresEvent.Snapshot emptyStructureSnapshot() {
        return new MMCRMachineStructuresEvent.Snapshot(Map.of(), Map.of(), Map.of(), Map.of());
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
