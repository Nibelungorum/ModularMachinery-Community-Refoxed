package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.ArrayList;

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

    private RecipeRegistry() {
    }

    public static void registerStatic(MachineRecipe recipe) {
        synchronized (RuntimeContentVersion.lock()) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe must not be null");
        }
        if (recipe.id() == null) {
            throw new IllegalArgumentException("Recipe id null");
        }
        if (STATIC_RECIPES.containsKey(recipe.id())) {
            throw new IllegalStateException("Recipe already registered: " + recipe.id());
        }
        STATIC_RECIPES.put(recipe.id(), recipe);
        publish(STATIC_RECIPES, STATE.dataPack(), STATE.dynamic());
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
        return STATE.byMachine().getOrDefault(machineId, List.of());
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
        publish(STATE.staticRecipes(), STATE.dataPack(), replacement);
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

    public static Map<Identifier, MachineRecipe> staticSnapshot() {
        return STATE.staticRecipes();
    }

    public static void replaceClientSnapshot(Map<Identifier, MachineRecipe> recipes) {
        synchronized (RuntimeContentVersion.lock()) {
        validateClientSnapshot(recipes);
        publish(Map.of(), Map.of(), recipes);
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
        Map<Identifier, MachineRecipe> replacement = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            if (STATE.staticRecipes().containsKey(entry.getKey())) {
                String warning = "data-pack layer recipe " + entry.getKey() + " overrides static layer recipe " + entry.getKey();
                warnings.add(warning);
                MMCR.LOG.warn(warning);
            }
            replacement.put(entry.getKey(), entry.getValue());
        }
        publish(STATE.staticRecipes(), replacement, STATE.dynamic(), warnings);
        reloadVersion++;
        registryVersion++;
        RuntimeContentVersion.advance();
        }
    }

    private static void publish(Map<Identifier, MachineRecipe> staticRecipes,
                                Map<Identifier, MachineRecipe> dataPack,
                                Map<Identifier, MachineRecipe> dynamic) {
        publish(staticRecipes, dataPack, dynamic, List.of());
    }

    private static void publish(Map<Identifier, MachineRecipe> staticRecipes,
                                Map<Identifier, MachineRecipe> dataPack,
                                Map<Identifier, MachineRecipe> dynamic,
                                List<String> warnings) {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>(staticRecipes);
        recipes.putAll(dataPack);
        for (Map.Entry<Identifier, MachineRecipe> entry : dynamic.entrySet()) {
            recipes.putIfAbsent(entry.getKey(), entry.getValue());
        }
        Map<Identifier, List<MachineRecipe>> byMachine = new LinkedHashMap<>();
        Map<Identifier, TreeMap<Integer, TreeSet<MachineRecipe>>> ordered = new LinkedHashMap<>();
        for (MachineRecipe recipe : recipes.values()) {
            TreeMap<Integer, TreeSet<MachineRecipe>> priorities = ordered.computeIfAbsent(
                    recipe.machineId(), ignored -> new TreeMap<>());
            priorities.computeIfAbsent(recipe.priority(), ignored ->
                    new TreeSet<>(Comparator.comparing(MachineRecipe::id))).add(recipe);
        }
        for (Map.Entry<Identifier, TreeMap<Integer, TreeSet<MachineRecipe>>> entry : ordered.entrySet()) {
            byMachine.put(entry.getKey(), entry.getValue().values().stream().flatMap(TreeSet::stream).toList());
        }
        STATE = new State(immutable(staticRecipes), immutable(dataPack), immutable(dynamic),
                immutable(recipes), immutable(byMachine), List.copyOf(warnings));
    }

    public static void clearAll() {
        synchronized (RuntimeContentVersion.lock()) {
        STATIC_RECIPES.clear();
        STATE = State.empty();
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
                         Map<Identifier, MachineRecipe> dynamic,
                         Map<Identifier, MachineRecipe> effective,
                         Map<Identifier, List<MachineRecipe>> byMachine,
                         List<String> warnings) {
        private static State empty() {
            return new State(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        }

        private List<MachineRecipe> effectiveValues() {
            return List.copyOf(effective.values());
        }
    }
}
