package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

/** Stores static, KubeJS, data-pack, and direct-runtime recipes and publishes one effective view.
 *
 * <p>Precedence is {@code data-pack > KubeJS > static > dynamic}. A lower-priority recipe remains
 * in its source layer when it is shadowed by a higher-priority layer.</p>
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeRegistry {

    private static final Map<ResourceLocation, MachineRecipe> STATIC_RECIPES = new LinkedHashMap<>();
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
        Map<ResourceLocation, MachineRecipe> candidate = new LinkedHashMap<>(STATIC_RECIPES);
        for (MachineRecipe recipe : recipes) {
            if (recipe == null) {
                throw new IllegalArgumentException("Recipe must not be null");
            }
            if (recipe.id() == null) {
                throw new IllegalArgumentException("Recipe id null");
            }
            if (!MachineRegistry.containsRecipePool(recipe.recipePoolId())) {
                MMCR.LOG.warn("Skipping recipe {}: unknown recipe pool {} at recipe_pool",
                        recipe.id(), recipe.recipePoolId());
                continue;
            }
            if (hasConflictingPool(recipe, List.of(STATE.dataPack(), STATE.kubeJS(), STATE.dynamic()))) {
                continue;
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

    public static MachineRecipe getRecipe(ResourceLocation id) {
        if (id == null) return null;
        State state = STATE;
        MachineRecipe recipe = state.effective().get(id);
        return recipe;
    }

    public static List<MachineRecipe> recipesForMachine(Machine machine) {
        return catalogForMachine(machine).recipes();
    }

    public static List<MachineRecipe> recipesForPool(ResourceLocation recipePoolId) {
        return catalogForPool(recipePoolId).recipes();
    }

    public static MachineRecipeCatalog catalogForMachine(Machine machine) {
        return catalogForPool(MachineRegistry.recipePoolForMachine(machine));
    }

    public static MachineRecipeCatalog catalogForMachine(ResourceLocation machineId) {
        if (machineId == null || (MachineDefinitions.getRegistration(machineId) == null
                && MachineRegistry.getMachine(machineId) == null)) {
            return EMPTY_CATALOG;
        }
        return catalogForPool(MachineRegistry.recipePoolForMachine(machineId));
    }

    public static MachineRecipeCatalog catalogForPool(ResourceLocation recipePoolId) {
        if (recipePoolId == null) return EMPTY_CATALOG;
        return STATE.poolCatalogs().getOrDefault(recipePoolId, EMPTY_CATALOG);
    }

    public static List<MachineRecipe> recipes() {
        return STATE.effectiveValues();
    }

    public static Map<ResourceLocation, MachineRecipe> effectiveSnapshot() {
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

    public static boolean containsStatic(ResourceLocation id) {
        return STATE.staticRecipes().containsKey(id);
    }

    public static void replaceDynamic(Map<ResourceLocation, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
        DynamicCandidate candidate = validateDynamicCandidate(recipes);
        Map<ResourceLocation, MachineRecipe> replacement = candidate.acceptedRecipes();
        publish(STATE.staticRecipes(), STATE.dataPack(), STATE.kubeJS(), replacement);
        reloadVersion++;
        registryVersion++;
        RuntimeContentVersion.advance();
        }
    }

    /** Publishes a dynamic layer using the supplied pool-membership rule. */
    public static void replaceDynamic(Map<ResourceLocation, MachineRecipe> recipes,
                                      Predicate<ResourceLocation> poolAvailable) {
        synchronized (RuntimeContentVersion.lock()) {
        DynamicCandidate candidate = validateDynamicCandidate(recipes, poolAvailable);
        Map<ResourceLocation, MachineRecipe> replacement = candidate.acceptedRecipes();
        publish(STATE.staticRecipes(), STATE.dataPack(), STATE.kubeJS(), replacement);
        reloadVersion++;
        registryVersion++;
        RuntimeContentVersion.advance();
        }
    }

    /** Validates a dynamic layer and returns only recipes that can be published. */
    public static DynamicCandidate validateDynamicCandidate(Map<ResourceLocation, MachineRecipe> recipes) {
        return validateDynamicCandidate(recipes, MachineRegistry::containsRecipePool);
    }

    /** Validates a dynamic layer with the pool-membership rule used by its publisher. */
    public static DynamicCandidate validateDynamicCandidate(Map<ResourceLocation, MachineRecipe> recipes,
                                                            Predicate<ResourceLocation> poolAvailable) {
        if (recipes == null) throw new IllegalArgumentException("Dynamic recipes must not be null");
        if (poolAvailable == null) throw new IllegalArgumentException("Pool membership predicate must not be null");
        Map<ResourceLocation, MachineRecipe> acceptedRecipes = new LinkedHashMap<>();
        List<MachineRecipeJson.RecipeJsonException> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            try {
                validateDynamicEntry(entry, poolAvailable);
                acceptedRecipes.put(entry.getKey(), entry.getValue());
            } catch (MachineRecipeJson.RecipeJsonException exception) {
                errors.add(exception);
                MMCR.LOG.warn("Skipping invalid dynamic recipe {}", entry.getKey(), exception);
            }
        }
        return new DynamicCandidate(acceptedRecipes, errors);
    }

    private static void validateDynamicEntry(Map.Entry<ResourceLocation, MachineRecipe> entry,
                                             Predicate<ResourceLocation> poolAvailable) {
        ResourceLocation id = entry.getKey();
        MachineRecipe recipe = entry.getValue();
        if (id == null || recipe == null || recipe.id() == null || !id.equals(recipe.id())) {
            throw new IllegalArgumentException("Recipe key does not match recipe id: " + id);
        }
        if (!poolAvailable.test(recipe.recipePoolId())) {
            throw new MachineRecipeJson.RecipeJsonException(id, "recipe_pool",
                    "unknown recipe pool " + recipe.recipePoolId(), null);
        }
        MachineRecipe conflicting = conflictingPoolRecipe(recipe,
                List.of(STATE.staticRecipes(), STATE.dataPack(), STATE.kubeJS()));
        if (conflicting != null) {
            throw new MachineRecipeJson.RecipeJsonException(id, "recipe_pool",
                    "recipe already belongs to pool " + conflicting.recipePoolId(), null);
        }
        if (STATE.staticRecipes().containsKey(id)) {
            throw new IllegalStateException("Dynamic recipe conflicts with static recipe: " + id);
        }
        if (STATE.dataPack().containsKey(id)) {
            throw new IllegalStateException("Dynamic recipe conflicts with data-pack recipe: " + id);
        }
        try {
            validateRecipeTypes(Map.of(id, recipe));
        } catch (MachineRecipeJson.RecipeJsonException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MachineRecipeJson.RecipeJsonException(id, "$",
                    exception.getMessage() == null ? "candidate validation failed" : exception.getMessage(),
                    exception);
        }
    }

    public record DynamicCandidate(
            Map<ResourceLocation, MachineRecipe> acceptedRecipes,
            List<MachineRecipeJson.RecipeJsonException> errors) {
        public DynamicCandidate {
            acceptedRecipes = immutable(acceptedRecipes == null ? Map.of() : acceptedRecipes);
            errors = List.copyOf(errors == null ? List.of() : errors);
        }
    }

    public static Map<ResourceLocation, MachineRecipe> dynamicSnapshot() {
        return STATE.dynamic();
    }

    public static Map<ResourceLocation, MachineRecipe> dataPackSnapshot() {
        return STATE.dataPack();
    }

    public static Map<ResourceLocation, MachineRecipe> kubeJSSnapshot() {
        return STATE.kubeJS();
    }

    public static Map<ResourceLocation, MachineRecipe> staticSnapshot() {
        return STATE.staticRecipes();
    }

    public static void replaceClientSnapshot(Map<ResourceLocation, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
        validateClientSnapshot(recipes);
        publish(Map.of(), Map.of(), Map.of(), recipes);
        reloadVersion++;
        registryVersion++;
        }
    }

    public static void validateClientSnapshot(Map<ResourceLocation, MachineRecipe> recipes) {
        if (recipes == null) throw new IllegalArgumentException("recipes null");
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !entry.getKey().equals(entry.getValue().id())) {
                throw new IllegalArgumentException("Recipe key does not match recipe id: " + entry.getKey());
            }
        }
        validateRecipeTypes(recipes);
    }

    public static List<String> lastDataPackWarnings() {
        return STATE.warnings();
    }

    public static void replaceDataPack(Map<ResourceLocation, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
        validateDataPackCandidate(recipes);
        Map<ResourceLocation, MachineRecipe> replacement = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            MachineRecipe recipe = entry.getKey().equals(entry.getValue().id())
                    ? entry.getValue() : entry.getValue().withId(entry.getKey());
            replacement.put(entry.getKey(), recipe);
        }
        replacement = filterRecipesWithValidPools(replacement);
        replacement = filterRecipesWithPoolConflicts(replacement,
                List.of(STATE.staticRecipes(), STATE.kubeJS(), STATE.dynamic()));
        for (ResourceLocation id : replacement.keySet()) {
            if (STATE.staticRecipes().containsKey(id)) {
                String warning = "data-pack layer recipe " + id + " overrides static layer recipe " + id;
                warnings.add(warning);
                MMCR.LOG.warn(warning);
            }
        }
        publish(STATE.staticRecipes(), replacement, STATE.kubeJS(), STATE.dynamic(), warnings);
        reloadVersion++;
        registryVersion++;
        RuntimeContentVersion.advance();
        }
    }

    /** Validates a complete data-pack layer without changing any published state. */
    public static void validateDataPackCandidate(Map<ResourceLocation, MachineRecipe> recipes) {
        if (recipes == null) throw new IllegalArgumentException("Data-pack recipes must not be null");
        validateRecipeTypes(recipes);
    }

    private static void validateRecipeTypes(Map<ResourceLocation, MachineRecipe> recipes) {
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            ResourceLocation id = entry.getKey();
            MachineRecipe recipe = entry.getValue();
            if (id == null || recipe == null || recipe.id() == null) {
                throw new MachineRecipeJson.RecipeJsonException(
                        id == null ? MMCR.id("recipe_validation") : id, "$",
                        "Recipe key or recipe id must not be null", null);
            }
            for (int index = 0; index < recipe.requirements().size(); index++) {
                validateRequirement(id, "requirements[" + index + "]", recipe.requirements().get(index));
            }
            for (int index = 0; index < recipe.machineOutputs().size(); index++) {
                MachineOutput output = recipe.machineOutputs().get(index);
                if (!OutputRegistry.isCanonical(output)) {
                    throw new MachineRecipeJson.RecipeJsonException(id, "outputs[" + index + "]",
                            "Output type is not registered canonically", null);
                }
                MachineRequirement derived = OutputRegistry.tryToRequirement(output, List.of());
                if (derived == null) continue;
                validateRequirement(id, "outputs[" + index + "]", derived);
                if (derived.io() != RecipeModifier.IOType.OUTPUT) {
                    throw new MachineRecipeJson.RecipeJsonException(id, "outputs[" + index + "]",
                            "Derived requirement must have output direction", null);
                }
                if (recipe.requirements().stream()
                        .noneMatch(requirement -> OutputRegistry.matchesOutputRequirement(output, requirement))) {
                    throw new MachineRecipeJson.RecipeJsonException(id, "outputs[" + index + "]",
                            "Output has no matching canonical requirement", null);
                }
            }
        }
    }

    private static void validateRequirement(ResourceLocation recipeId, String path,
                                            MachineRequirement requirement) {
        if (requirement == null || requirement.type() == null) {
            throw new MachineRecipeJson.RecipeJsonException(recipeId, path,
                    "Requirement type must not be null", null);
        }
        if (requirement.io() == null) {
            throw new MachineRecipeJson.RecipeJsonException(recipeId, path,
                    "Requirement direction must not be null", null);
        }
        try {
            if (RequirementHandlerRegistry.canonicalType(requirement.type()) != requirement.type()
                    || RequirementHandlerRegistry.handlerFor(requirement.type()) == null) {
                throw new MachineRecipeJson.RecipeJsonException(recipeId, path,
                        "Requirement type is not registered canonically", null);
            }
        } catch (MachineRecipeJson.RecipeJsonException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MachineRecipeJson.RecipeJsonException(recipeId, path,
                    "Requirement type validation failed", exception);
        }
    }

    public static void replaceKubeJS(Map<ResourceLocation, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
            Map<ResourceLocation, MachineRecipe> replacement = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
                MachineRecipe recipe = entry.getKey().equals(entry.getValue().id())
                        ? entry.getValue() : entry.getValue().withId(entry.getKey());
                replacement.put(entry.getKey(), recipe);
            }
            replacement = filterRecipesWithValidPools(replacement);
            replacement = filterRecipesWithPoolConflicts(replacement,
                    List.of(STATE.staticRecipes(), STATE.dataPack(), STATE.dynamic()));
            publish(STATE.staticRecipes(), STATE.dataPack(), replacement, STATE.dynamic());
            reloadVersion++;
            registryVersion++;
            RuntimeContentVersion.advance();
        }
    }

    private static void publish(Map<ResourceLocation, MachineRecipe> staticRecipes,
                                Map<ResourceLocation, MachineRecipe> dataPack,
                                Map<ResourceLocation, MachineRecipe> kubeJS,
                                Map<ResourceLocation, MachineRecipe> dynamic) {
        publish(staticRecipes, dataPack, kubeJS, dynamic, List.of());
    }

    private static void publish(Map<ResourceLocation, MachineRecipe> staticRecipes,
                                Map<ResourceLocation, MachineRecipe> dataPack,
                                Map<ResourceLocation, MachineRecipe> kubeJS,
                                Map<ResourceLocation, MachineRecipe> dynamic,
                                List<String> warnings) {
        State previous = STATE;
        long previousCatalogGeneration = catalogGeneration;
        State next;
        try {
            validateRecipeTypes(staticRecipes);
            validateRecipeTypes(dataPack);
            validateRecipeTypes(kubeJS);
            validateRecipeTypes(dynamic);
            next = buildState(staticRecipes, dataPack, kubeJS, dynamic, warnings);
        } catch (RuntimeException | Error failure) {
            catalogGeneration = previousCatalogGeneration;
            throw failure;
        }
        STATE = next;
        try {
            CraftingContextPool.onGlobalReload();
        } catch (RuntimeException | Error failure) {
            STATE = previous;
            catalogGeneration = previousCatalogGeneration;
            throw failure;
        }
    }

    private static State buildState(Map<ResourceLocation, MachineRecipe> staticRecipes,
                                    Map<ResourceLocation, MachineRecipe> dataPack,
                                    Map<ResourceLocation, MachineRecipe> kubeJS,
                                    Map<ResourceLocation, MachineRecipe> dynamic,
                                    List<String> warnings) {
        Map<ResourceLocation, List<MachineRecipe>> recipesByPool = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> poolByRecipeId = new LinkedHashMap<>();
        mergeLayer(recipesByPool, poolByRecipeId, staticRecipes, false);
        mergeLayer(recipesByPool, poolByRecipeId, kubeJS, true);
        mergeLayer(recipesByPool, poolByRecipeId, dataPack, true);
        mergeLayer(recipesByPool, poolByRecipeId, dynamic, false);

        Map<ResourceLocation, MachineRecipe> recipes = new LinkedHashMap<>();
        recipesByPool.values().forEach(poolRecipes -> poolRecipes.forEach(recipe -> recipes.put(recipe.id(), recipe)));
        Map<ResourceLocation, MachineRecipeCatalog> poolCatalogs = new LinkedHashMap<>();
        Set<ResourceLocation> poolIds = new LinkedHashSet<>(STATE.poolCatalogs().keySet());
        poolIds.addAll(recipesByPool.keySet());
        for (ResourceLocation poolId : poolIds) {
            List<MachineRecipe> poolRecipes = recipesByPool.getOrDefault(poolId, List.of()).stream()
                    .sorted(Comparator.comparingInt(MachineRecipe::priority)
                            .thenComparing(MachineRecipe::id))
                    .toList();
            List<MachineRecipe> orderedRecipes = poolRecipes.stream()
                    .sorted(Comparator.comparingInt(MachineRecipe::priority)
                            .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                            .thenComparing(MachineRecipe::id))
                    .toList();
            MachineRecipeCatalog previous = STATE.poolCatalogs().get(poolId);
            if (previous != null && previous.recipes().equals(poolRecipes)
                    && previous.orderedRecipes().equals(orderedRecipes)) {
                poolCatalogs.put(poolId, previous);
                continue;
            }
            long version = previous != null && previous.orderedRecipes().equals(orderedRecipes)
                    ? previous.version() : ++catalogGeneration;
            poolCatalogs.put(poolId, new MachineRecipeCatalog(version, poolRecipes, orderedRecipes,
                    orderedRecipes.isEmpty() ? RecipeCandidateIndex.empty()
                            : RecipeCandidateIndex.build(poolId, orderedRecipes)));
        }
        return new State(immutable(staticRecipes), immutable(dataPack), immutable(kubeJS), immutable(dynamic),
                immutable(recipes), immutable(poolCatalogs), List.copyOf(warnings));
    }

    private static void mergeLayer(Map<ResourceLocation, List<MachineRecipe>> recipesByPool,
                                   Map<ResourceLocation, ResourceLocation> poolByRecipeId,
                                   Map<ResourceLocation, MachineRecipe> layer,
                                   boolean overridesSamePool) {
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : layer.entrySet()) {
            MachineRecipe recipe = entry.getValue();
            ResourceLocation recipeId = entry.getKey();
            ResourceLocation poolId = recipe.recipePoolId();
            ResourceLocation existingPoolId = poolByRecipeId.get(recipeId);
            if (existingPoolId != null && !existingPoolId.equals(poolId)) {
                MMCR.LOG.warn("Skipping recipe {} from pool {}: recipe already belongs to pool {}",
                        recipeId, poolId, existingPoolId);
                continue;
            }
            List<MachineRecipe> poolRecipes = recipesByPool.computeIfAbsent(poolId, ignored -> new ArrayList<>());
            if (existingPoolId == null) {
                poolRecipes.add(recipe);
                poolByRecipeId.put(recipeId, poolId);
            } else if (overridesSamePool) {
                for (int index = 0; index < poolRecipes.size(); index++) {
                    if (poolRecipes.get(index).id().equals(recipeId)) {
                        poolRecipes.set(index, recipe);
                        break;
                    }
                }
            } else {
                continue;
            }
        }
    }

    private static Map<ResourceLocation, MachineRecipe> filterRecipesWithValidPools(
            Map<ResourceLocation, MachineRecipe> recipes) {
        Map<ResourceLocation, MachineRecipe> valid = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            MachineRecipe recipe = entry.getValue();
            if (!MachineRegistry.containsRecipePool(recipe.recipePoolId())) {
                MMCR.LOG.warn("Skipping recipe {}: unknown recipe pool {} at recipe_pool",
                        entry.getKey(), recipe.recipePoolId());
                continue;
            }
            valid.put(entry.getKey(), recipe);
        }
        return valid;
    }

    private static Map<ResourceLocation, MachineRecipe> filterRecipesWithPoolConflicts(
            Map<ResourceLocation, MachineRecipe> recipes,
            List<Map<ResourceLocation, MachineRecipe>> otherLayers) {
        Map<ResourceLocation, MachineRecipe> valid = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            if (!hasConflictingPool(entry.getValue(), otherLayers)) {
                valid.put(entry.getKey(), entry.getValue());
            }
        }
        return valid;
    }

    private static boolean hasConflictingPool(MachineRecipe recipe,
                                               List<Map<ResourceLocation, MachineRecipe>> otherLayers) {
        MachineRecipe existing = conflictingPoolRecipe(recipe, otherLayers);
        if (existing != null) {
            MMCR.LOG.warn("Skipping recipe {} from pool {}: recipe already belongs to pool {}",
                    recipe.id(), recipe.recipePoolId(), existing.recipePoolId());
            return true;
        }
        return false;
    }

    private static MachineRecipe conflictingPoolRecipe(MachineRecipe recipe,
                                                        List<Map<ResourceLocation, MachineRecipe>> otherLayers) {
        for (Map<ResourceLocation, MachineRecipe> layer : otherLayers) {
            MachineRecipe existing = layer.get(recipe.id());
            if (existing != null && !existing.recipePoolId().equals(recipe.recipePoolId())) {
                return existing;
            }
        }
        return null;
    }

    public static void clearAll() {
        synchronized (RuntimeContentVersion.lock()) {
        State previous = STATE;
        Map<ResourceLocation, MachineRecipe> previousStatic = new LinkedHashMap<>(STATIC_RECIPES);
        long previousCatalogGeneration = catalogGeneration;
        Map<ResourceLocation, MachineRecipeCatalog> emptyCatalogs = new LinkedHashMap<>();
        State next;
        try {
            for (ResourceLocation poolId : STATE.poolCatalogs().keySet()) {
                emptyCatalogs.put(poolId, new MachineRecipeCatalog(++catalogGeneration,
                        List.of(), List.of(), RecipeCandidateIndex.empty()));
            }
            next = State.empty(emptyCatalogs);
        } catch (RuntimeException | Error failure) {
            catalogGeneration = previousCatalogGeneration;
            throw failure;
        }
        try {
            STATIC_RECIPES.clear();
            STATE = next;
            CraftingContextPool.onGlobalReload();
            reloadVersion++;
            registryVersion++;
            RuntimeContentVersion.advance();
        } catch (RuntimeException | Error failure) {
            STATIC_RECIPES.clear();
            STATIC_RECIPES.putAll(previousStatic);
            STATE = previous;
            catalogGeneration = previousCatalogGeneration;
            throw failure;
        }
        }
    }

    public static void clearForTesting() {
        clearAll();
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private record State(Map<ResourceLocation, MachineRecipe> staticRecipes,
                         Map<ResourceLocation, MachineRecipe> dataPack,
                         Map<ResourceLocation, MachineRecipe> kubeJS,
                          Map<ResourceLocation, MachineRecipe> dynamic,
                          Map<ResourceLocation, MachineRecipe> effective,
                          Map<ResourceLocation, MachineRecipeCatalog> poolCatalogs,
                          List<String> warnings) {
        private static State empty() {
            return empty(Map.of());
        }

        private static State empty(Map<ResourceLocation, MachineRecipeCatalog> poolCatalogs) {
            return new State(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), immutable(poolCatalogs), List.of());
        }

        private List<MachineRecipe> effectiveValues() {
            return List.copyOf(effective.values());
        }
    }
}
