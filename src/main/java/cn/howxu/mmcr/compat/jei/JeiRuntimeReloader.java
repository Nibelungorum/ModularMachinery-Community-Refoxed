package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Runtime JEI reload bridge. Safe to call before JEI has initialized or when JEI is absent.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiRuntimeReloader {

    private static final Set<Identifier> REGISTERED_RECIPE_POOL_CATEGORIES = ConcurrentHashMap.newKeySet();
    private static volatile Map<Identifier, List<MachineRecipeDisplay>> visibleDisplaysByPool = Map.of();
    private static volatile List<MachineStructureDisplay> visibleStructures = List.of();
    private static volatile RuntimeContentSnapshot latestSnapshot;
    private static volatile boolean categoriesCaptured;
    private static volatile IJeiRuntime runtime;
    private static volatile long lastReloadedVersion = Long.MIN_VALUE;
    private static volatile long scheduledReloadVersion = Long.MIN_VALUE;
    private static volatile Consumer<Runnable> clientExecutor = JeiRuntimeReloader::executeOnClient;

    private JeiRuntimeReloader() {
    }

    static void markRegisteredRecipePoolCategories(Collection<Identifier> poolIds) {
        REGISTERED_RECIPE_POOL_CATEGORIES.clear();
        REGISTERED_RECIPE_POOL_CATEGORIES.addAll(poolIds);
        categoriesCaptured = true;
    }

    static void captureInitialDisplays(Map<Identifier, List<MachineRecipeDisplay>> displaysByPool) {
        visibleDisplaysByPool = copyDisplays(displaysByPool);
    }

    static void captureInitialStructures(List<MachineStructureDisplay> structures) {
        visibleStructures = List.copyOf(structures);
    }

    public static void setRuntime(IJeiRuntime runtime) {
        JeiRuntimeReloader.runtime = runtime;
        lastReloadedVersion = Long.MIN_VALUE;
        scheduledReloadVersion = Long.MIN_VALUE;
        if (runtime == null) {
            latestSnapshot = null;
            visibleDisplaysByPool = Map.of();
            visibleStructures = List.of();
            REGISTERED_RECIPE_POOL_CATEGORIES.clear();
            categoriesCaptured = false;
        } else if (latestSnapshot != null) {
            reloadIfAvailable(latestSnapshot);
        }
    }

    public static void clearRuntimeForTesting() {
        setRuntime(null);
        clientExecutor = JeiRuntimeReloader::executeOnClient;
    }

    static void setClientExecutorForTesting(Consumer<Runnable> executor) {
        clientExecutor = executor;
    }

    public static void reloadIfAvailable(RuntimeContentSnapshot snapshot) {
        if (snapshot.contentVersion() == lastReloadedVersion
                || snapshot.contentVersion() == scheduledReloadVersion) return;
        latestSnapshot = snapshot;
        IJeiRuntime current = runtime;
        if (current == null) return;
        scheduledReloadVersion = snapshot.contentVersion();
        Runnable reload = () -> {
            if (current != runtime || latestSnapshot != snapshot) return;
            try {
                Map<Identifier, List<MachineRecipeDisplay>> displaysByPool = MachineRecipeDisplays.byPool(snapshot);
                Map<Identifier, List<MachineRecipeDisplay>> previousVisible = visibleDisplaysByPool;
                Map<Identifier, List<MachineRecipeDisplay>> updatedVisible = new LinkedHashMap<>();
                Set<Identifier> refreshedPoolIds = new LinkedHashSet<>(previousVisible.keySet());
                refreshedPoolIds.addAll(displaysByPool.keySet());
                for (Identifier poolId : refreshedPoolIds) {
                    if (categoriesCaptured && !REGISTERED_RECIPE_POOL_CATEGORIES.contains(poolId)) {
                        MMCR.LOG.warn("JEI category for synced recipe pool {} was not registered; restart or reload JEI to view it", poolId);
                        continue;
                    }
                    var type = JeiMachineRecipeTypes.forPool(poolId);
                    var recipeManager = current.getRecipeManager();
                    recipeManager.hideRecipes(type, previousVisible.getOrDefault(poolId, List.of()));
                    List<MachineRecipeDisplay> displays = displaysByPool.getOrDefault(poolId, List.of());
                    if (displays.isEmpty()) continue;
                    recipeManager.unhideRecipes(type, displays);
                    recipeManager.addRecipes(type, displays);
                    updatedVisible.put(poolId, displays);
                }
                visibleDisplaysByPool = Map.copyOf(updatedVisible);
                List<MachineStructureDisplay> structures = MachineRegistry.getAll().values().stream()
                        .sorted(Comparator.comparing(machine -> machine.registryName()))
                        .map(MachineStructureDisplay::from).toList();
                var recipeManager = current.getRecipeManager();
                if (!visibleStructures.isEmpty()) {
                    recipeManager.hideRecipes(JeiMachineRecipeTypes.STRUCTURE, visibleStructures);
                }
                if (!structures.isEmpty()) {
                    recipeManager.unhideRecipes(JeiMachineRecipeTypes.STRUCTURE, structures);
                    recipeManager.addRecipes(JeiMachineRecipeTypes.STRUCTURE, structures);
                }
                visibleStructures = structures;
                lastReloadedVersion = snapshot.contentVersion();
            } finally {
                scheduledReloadVersion = Long.MIN_VALUE;
            }
        };
        clientExecutor.accept(reload);
    }

    private static void executeOnClient(Runnable runnable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw new IllegalStateException("JEI reload requires the Minecraft client executor");
        }
        minecraft.execute(runnable);
    }

    private static Map<Identifier, List<MachineRecipeDisplay>> copyDisplays(
            Map<Identifier, List<MachineRecipeDisplay>> displaysByMachine) {
        Map<Identifier, List<MachineRecipeDisplay>> copy = new LinkedHashMap<>();
        displaysByMachine.forEach((machineId, displays) -> copy.put(machineId, List.copyOf(displays)));
        return Map.copyOf(copy);
    }
}
