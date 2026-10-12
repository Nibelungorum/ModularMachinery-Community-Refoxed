package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.client.RecipeViewerRegistration;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

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

    private static final Set<ResourceLocation> REGISTERED_RECIPE_POOL_CATEGORIES = ConcurrentHashMap.newKeySet();
    private static volatile Map<ResourceLocation, List<MachineRecipeDisplay>> visibleDisplaysByPool = Map.of();
    private static volatile List<MachineStructureDisplay> visibleStructures = List.of();
    private static volatile RuntimeContentSnapshot latestSnapshot;
    private static volatile boolean categoriesCaptured;
    private static volatile IJeiRuntime runtime;
    private static volatile long lastReloadedVersion = Long.MIN_VALUE;
    private static volatile long scheduledReloadVersion = Long.MIN_VALUE;
    private static volatile long connectionEpoch;
    private static volatile long layoutRevision;
    private static volatile long requestedInformation = Long.MIN_VALUE;
    private static volatile long lastNotifiedInformation = Long.MIN_VALUE;
    private static volatile long scheduledInformation = Long.MIN_VALUE;
    private static volatile Consumer<Runnable> clientExecutor = JeiRuntimeReloader::executeOnClient;

    private JeiRuntimeReloader() {
    }

    static void markRegisteredRecipePoolCategories(Collection<ResourceLocation> poolIds) {
        REGISTERED_RECIPE_POOL_CATEGORIES.clear();
        REGISTERED_RECIPE_POOL_CATEGORIES.addAll(poolIds);
        categoriesCaptured = true;
    }

    static void captureInitialDisplays(Map<ResourceLocation, List<MachineRecipeDisplay>> displaysByPool) {
        visibleDisplaysByPool = copyDisplays(displaysByPool);
    }

    static void captureInitialStructures(List<MachineStructureDisplay> structures) {
        visibleStructures = List.copyOf(structures);
    }

    public static void setRuntime(IJeiRuntime runtime) {
        connectionEpoch++;
        layoutRevision++;
        JeiRuntimeReloader.runtime = runtime;
        lastReloadedVersion = Long.MIN_VALUE;
        scheduledReloadVersion = Long.MIN_VALUE;
        scheduledInformation = lastNotifiedInformation = Long.MIN_VALUE;
        if (runtime == null) {
            latestSnapshot = null;
            visibleDisplaysByPool = Map.of();
            visibleStructures = List.of();
            REGISTERED_RECIPE_POOL_CATEGORIES.clear();
            categoriesCaptured = false;
            requestedInformation = Long.MIN_VALUE;
        } else if (latestSnapshot != null) {
            reloadIfAvailable(latestSnapshot);
        }
        if (runtime != null && requestedInformation != Long.MIN_VALUE) informationChanged();
    }

    /** Consumes the published information counter; does not reinterpret a runtime snapshot as all recipes. */
    public static void informationChanged() {
        long information = RecipeViewerRegistration.informationVersion();
        requestedInformation = information;
        IJeiRuntime current = runtime;
        if (current == null || information == lastNotifiedInformation || information == scheduledInformation) return;
        long epoch = connectionEpoch;
        scheduledInformation = information;
        clientExecutor.accept(() -> {
            if (epoch != connectionEpoch || current != runtime || information != requestedInformation) return;
            try {
                Set<ResourceLocation> pools = new LinkedHashSet<>(REGISTERED_RECIPE_POOL_CATEGORIES);
                pools.addAll(visibleDisplaysByPool.keySet());
                for (ResourceLocation pool : pools) {
                    if (categoriesCaptured && !REGISTERED_RECIPE_POOL_CATEGORIES.contains(pool)) {
                        MMCR.LOG.warn("JEI category for recipe pool {} was not registered; cannot refresh its information", pool);
                        continue;
                    }
                    // This invalidates only JEI's category cache. Existing MMCR widgets observe
                    // layoutRevision separately; registered static displays stay intact.
                    current.getRecipeManager().unhideRecipes(JeiMachineRecipeTypes.forPool(pool), List.of());
                }
                layoutRevision++;
                lastNotifiedInformation = information;
            } finally {
                if (epoch == connectionEpoch && scheduledInformation == information) scheduledInformation = Long.MIN_VALUE;
            }
        });
    }

    /** Invalidates queued ownership immediately while retaining the still-active JEI runtime/categories. */
    public static void resetForConnection() {
        connectionEpoch++;
        layoutRevision++;
        latestSnapshot = null;
        lastReloadedVersion = scheduledReloadVersion = Long.MIN_VALUE;
        requestedInformation = scheduledInformation = lastNotifiedInformation = Long.MIN_VALUE;
        IJeiRuntime current = runtime;
        Map<ResourceLocation, List<MachineRecipeDisplay>> previousDisplays = visibleDisplaysByPool;
        List<MachineStructureDisplay> previousStructures = visibleStructures;
        visibleDisplaysByPool = Map.of();
        visibleStructures = List.of();
        if (current == null) return;
        clientExecutor.accept(() -> {
            if (current != runtime) return;
            previousDisplays.forEach((pool, displays) -> {
                if (!categoriesCaptured || REGISTERED_RECIPE_POOL_CATEGORIES.contains(pool)) {
                    current.getRecipeManager().hideRecipes(JeiMachineRecipeTypes.forPool(pool), displays);
                }
            });
            if (!previousStructures.isEmpty()) current.getRecipeManager().hideRecipes(JeiMachineRecipeTypes.STRUCTURE, previousStructures);
        });
    }

    public static void clearRuntimeForTesting() {
        setRuntime(null);
        clientExecutor = JeiRuntimeReloader::executeOnClient;
    }

    static void setClientExecutorForTesting(Consumer<Runnable> executor) {
        clientExecutor = executor;
    }

    static long layoutRevision() { return layoutRevision; }

    public static void reloadIfAvailable(RuntimeContentSnapshot snapshot) {
        if (snapshot.contentVersion() == lastReloadedVersion
                || snapshot.contentVersion() == scheduledReloadVersion) return;
        latestSnapshot = snapshot;
        IJeiRuntime current = runtime;
        if (current == null) return;
        scheduledReloadVersion = snapshot.contentVersion();
        long epoch = connectionEpoch;
        Runnable reload = () -> {
            if (epoch != connectionEpoch || current != runtime || latestSnapshot != snapshot) return;
            try {
                Map<ResourceLocation, List<MachineRecipeDisplay>> displaysByPool = MachineRecipeDisplays.byPool(snapshot);
                Map<ResourceLocation, List<MachineRecipeDisplay>> previousVisible = visibleDisplaysByPool;
                Map<ResourceLocation, List<MachineRecipeDisplay>> updatedVisible = new LinkedHashMap<>();
                Set<ResourceLocation> refreshedPoolIds = new LinkedHashSet<>(previousVisible.keySet());
                refreshedPoolIds.addAll(displaysByPool.keySet());
                for (ResourceLocation poolId : refreshedPoolIds) {
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
                if (epoch == connectionEpoch && scheduledReloadVersion == snapshot.contentVersion()) {
                    scheduledReloadVersion = Long.MIN_VALUE;
                }
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

    private static Map<ResourceLocation, List<MachineRecipeDisplay>> copyDisplays(
            Map<ResourceLocation, List<MachineRecipeDisplay>> displaysByMachine) {
        Map<ResourceLocation, List<MachineRecipeDisplay>> copy = new LinkedHashMap<>();
        displaysByMachine.forEach((machineId, displays) -> copy.put(machineId, List.copyOf(displays)));
        return Map.copyOf(copy);
    }
}
