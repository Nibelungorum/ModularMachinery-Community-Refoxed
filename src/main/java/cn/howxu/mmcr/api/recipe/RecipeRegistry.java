package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/** Stores static, data-pack, and direct-runtime recipes and publishes one effective view.
 *
 * <p>Precedence is {@code data-pack > static > dynamic}. A lower-priority recipe remains
 * in its source layer when it is shadowed by a higher-priority layer.</p>
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeRegistry {

    private static final Map<Identifier, MachineRecipe> STATIC_RECIPES = new LinkedHashMap<>();
    private static volatile State STATE = State.empty();
    private static long reloadVersion;
    private static long registryVersion;
    private static long catalogGeneration;
    private static final MachineRecipeCatalog EMPTY_CATALOG = new MachineRecipeCatalog(0L, List.of(), List.of(), RecipeCandidateIndex.empty());

    private RecipeRegistry() {
    }

    public static void registerStatic(MachineRecipe recipe) {
        registerStaticBatch(List.of(recipe));
    }

    public static void registerStaticBatch(Collection<MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
        Map<Identifier, MachineRecipe> candidate = new LinkedHashMap<>(STATIC_RECIPES);
        for (MachineRecipe recipe : recipes) {
            if (recipe == null) {
                throw new IllegalArgumentException("Recipe must not be null");
            }
            if (recipe.id() == null) {
                throw new IllegalArgumentException("Recipe id null");
            }
            if (candidate.putIfAbsent(recipe.id(), recipe) != null) {
                throw new IllegalStateException("Recipe already registered: " + recipe.id());
            }
        }
        publish(candidate, STATE.dataPack(), STATE.kubeJS(), STATE.dynamic());
        STATIC_RECIPES.clear();
        STATIC_RECIPES.putAll(candidate);
        registryVersion++;
        RuntimeContentVersion.advance();
        }
    }

    /**
     * @deprecated use {@link #registerStatic(MachineRecipe)} for startup recipes
     */
    @Deprecated(forRemoval = true)
    public static void register(MachineRecipe recipe) {
        registerStatic(recipe);
    }

    public static MachineRecipe getRecipe(Identifier id) {
        if (id == null) return null;
        State state = STATE;
        MachineRecipe recipe = state.effective().get(id);
        return recipe;
    }

    public static List<MachineRecipe> byMachine(Machine machine) {
        if (machine == null || machine.registryName() == null) return Collections.emptyList();
        return byMachineId(machine.registryName());
    }

    public static List<MachineRecipe> byMachineId(Identifier machineId) {
        if (machineId == null) return Collections.emptyList();
        MachineRecipeCatalog catalog = STATE.catalogs().get(machineId);
        return catalog == null ? List.of() : catalog.recipes();
    }

    public static MachineRecipeCatalog catalog(Identifier machineId) {
        if (machineId == null) return EMPTY_CATALOG;
        return STATE.catalogs().getOrDefault(machineId, EMPTY_CATALOG);
    }

    public static List<MachineRecipe> recipes() {
        return STATE.effectiveValues();
    }

    public static Map<Identifier, MachineRecipe> effectiveSnapshot() {
        synchronized (RuntimeContentVersion.lock()) {
            return STATE.effective();
        }
    }

    public static int registeredRecipeCount() {
        return STATE.effective().size();
    }

    public static long reloadVersion() {
        return reloadVersion;
    }

    public static long registryVersion() {
        return registryVersion;
    }

    public static boolean containsStatic(Identifier id) {
        return STATE.staticRecipes().containsKey(id);
    }

    public static void replaceDynamic(Map<Identifier, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
        Map<Identifier, MachineRecipe> replacement = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            Identifier id = entry.getKey();
            if (STATE.staticRecipes().containsKey(id)) {
                throw new IllegalStateException("Dynamic recipe conflicts with static recipe: " + id);
            }
            if (STATE.dataPack().containsKey(id)) {
                throw new IllegalStateException("Dynamic recipe conflicts with data-pack recipe: " + id);
            }
            replacement.put(entry.getKey(), entry.getValue());
        }
        publish(STATE.staticRecipes(), STATE.dataPack(), STATE.kubeJS(), replacement);
        reloadVersion++;
        registryVersion++;
        RuntimeContentVersion.advance();
        }
    }

    public static Map<Identifier, MachineRecipe> dynamicSnapshot() {
        return STATE.dynamic();
    }

    public static Map<Identifier, MachineRecipe> dataPackSnapshot() {
        return STATE.dataPack();
    }

    public static Map<Identifier, MachineRecipe> kubeJSSnapshot() {
        return STATE.kubeJS();
    }

    public static Map<Identifier, MachineRecipe> staticSnapshot() {
        return STATE.staticRecipes();
    }

    public static void replaceClientSnapshot(Map<Identifier, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
        validateClientSnapshot(recipes);
        publish(Map.of(), Map.of(), Map.of(), recipes);
        reloadVersion++;
        registryVersion++;
        }
    }

    public static void validateClientSnapshot(Map<Identifier, MachineRecipe> recipes) {
        if (recipes == null) throw new IllegalArgumentException("recipes null");
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !entry.getKey().equals(entry.getValue().id())) {
                throw new IllegalArgumentException("Recipe key does not match recipe id: " + entry.getKey());
            }
        }
    }

    public static List<String> lastDataPackWarnings() {
        return STATE.warnings();
    }

    public static void replaceDataPack(Map<Identifier, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
        validateDataPackCandidate(recipes);
        Map<Identifier, MachineRecipe> replacement = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            MachineRecipe recipe = entry.getKey().equals(entry.getValue().id())
                    ? entry.getValue() : entry.getValue().withId(entry.getKey());
            if (STATE.staticRecipes().containsKey(entry.getKey())) {
                String warning = "data-pack layer recipe " + entry.getKey() + " overrides static layer recipe " + entry.getKey();
                warnings.add(warning);
                MMCR.LOG.warn(warning);
            }
            replacement.put(entry.getKey(), recipe);
        }
        publish(STATE.staticRecipes(), replacement, STATE.kubeJS(), STATE.dynamic(), warnings);
        reloadVersion++;
        registryVersion++;
        RuntimeContentVersion.advance();
        }
    }

    /** Validates a complete data-pack layer without changing any published state. */
    public static void validateDataPackCandidate(Map<Identifier, MachineRecipe> recipes) {
        if (recipes == null) throw new IllegalArgumentException("Data-pack recipes must not be null");
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            Identifier id = entry.getKey();
            MachineRecipe recipe = entry.getValue();
            if (id == null || recipe == null || recipe.id() == null) {
                throw new IllegalArgumentException("Recipe key or recipe id must not be null: " + id);
            }
            for (var requirement : recipe.requirements()) {
                if (requirement == null || requirement.type() == null
                        || RequirementHandlerRegistry.canonicalType(requirement.type()) != requirement.type()
                        || RequirementHandlerRegistry.handlerFor(requirement.type()) == null) {
                    throw new IllegalArgumentException("Recipe " + id + " contains an unregistered requirement type");
                }
            }
            for (MachineOutput output : recipe.machineOutputs()) {
                if (!OutputRegistry.isCanonical(output)) {
                    throw new IllegalArgumentException("Recipe " + id + " contains an unregistered output type");
                }
            }
        }
    }

    public static void replaceKubeJS(Map<Identifier, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
            Map<Identifier, MachineRecipe> replacement = new LinkedHashMap<>();
            for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
                MachineRecipe recipe = entry.getKey().equals(entry.getValue().id())
                        ? entry.getValue() : entry.getValue().withId(entry.getKey());
                replacement.put(entry.getKey(), recipe);
            }
            publish(STATE.staticRecipes(), STATE.dataPack(), replacement, STATE.dynamic());
            reloadVersion++;
            registryVersion++;
            RuntimeContentVersion.advance();
        }
    }

    private static void publish(Map<Identifier, MachineRecipe> staticRecipes,
                                Map<Identifier, MachineRecipe> dataPack,
                                Map<Identifier, MachineRecipe> kubeJS,
                                Map<Identifier, MachineRecipe> dynamic) {
        publish(staticRecipes, dataPack, kubeJS, dynamic, List.of());
    }

    private static void publish(Map<Identifier, MachineRecipe> staticRecipes,
                                Map<Identifier, MachineRecipe> dataPack,
                                Map<Identifier, MachineRecipe> kubeJS,
                                Map<Identifier, MachineRecipe> dynamic,
                                List<String> warnings) {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>(staticRecipes);
        recipes.putAll(kubeJS);
        recipes.putAll(dataPack);
        for (Map.Entry<Identifier, MachineRecipe> entry : dynamic.entrySet()) {
            recipes.putIfAbsent(entry.getKey(), entry.getValue());
        }
        Map<Identifier, List<MachineRecipe>> byMachine = new LinkedHashMap<>();
        for (MachineRecipe recipe : recipes.values()) {
            byMachine.computeIfAbsent(recipe.machineId(), ignored -> new ArrayList<>()).add(recipe);
        }
        Map<Identifier, MachineRecipeCatalog> catalogs = new LinkedHashMap<>();
        Set<Identifier> machineIds = new LinkedHashSet<>(STATE.catalogs().keySet());
        machineIds.addAll(byMachine.keySet());
        for (Identifier machineId : machineIds) {
            List<MachineRecipe> machineRecipes = byMachine.getOrDefault(machineId, List.of()).stream()
                    .sorted(Comparator.comparingInt(MachineRecipe::priority)
                            .thenComparing(MachineRecipe::id))
                    .toList();
            List<MachineRecipe> orderedRecipes = machineRecipes.stream()
                    .sorted(Comparator.comparingInt(MachineRecipe::priority)
                            .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                            .thenComparing(MachineRecipe::id))
                    .toList();
            MachineRecipeCatalog previous = STATE.catalogs().get(machineId);
            long version = previous != null && previous.orderedRecipes().equals(orderedRecipes)
                    ? previous.version() : ++catalogGeneration;
            catalogs.put(machineId, new MachineRecipeCatalog(version, machineRecipes, orderedRecipes,
                    RecipeCandidateIndex.build(orderedRecipes)));
        }
        STATE = new State(immutable(staticRecipes), immutable(dataPack), immutable(kubeJS), immutable(dynamic),
                immutable(recipes), immutable(catalogs), List.copyOf(warnings));
        CraftingContextPool.onGlobalReload();
    }

    public static void clearAll() {
        synchronized (RuntimeContentVersion.lock()) {
        Map<Identifier, MachineRecipeCatalog> emptyCatalogs = new LinkedHashMap<>();
        for (Identifier machineId : STATE.catalogs().keySet()) {
            emptyCatalogs.put(machineId, new MachineRecipeCatalog(++catalogGeneration,
                    List.of(), List.of(), RecipeCandidateIndex.empty()));
        }
        STATIC_RECIPES.clear();
        STATE = State.empty(emptyCatalogs);
        CraftingContextPool.onGlobalReload();
        reloadVersion++;
        registryVersion++;
        RuntimeContentVersion.advance();
        }
    }

    public static void clearForTesting() {
        clearAll();
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private record State(Map<Identifier, MachineRecipe> staticRecipes,
                         Map<Identifier, MachineRecipe> dataPack,
                         Map<Identifier, MachineRecipe> kubeJS,
                          Map<Identifier, MachineRecipe> dynamic,
                          Map<Identifier, MachineRecipe> effective,
                          Map<Identifier, MachineRecipeCatalog> catalogs,
                          List<String> warnings) {
        private static State empty() {
            return empty(Map.of());
        }

        private static State empty(Map<Identifier, MachineRecipeCatalog> catalogs) {
            return new State(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), immutable(catalogs), List.of());
        }

        private List<MachineRecipe> effectiveValues() {
            return List.copyOf(effective.values());
        }
    }
}
