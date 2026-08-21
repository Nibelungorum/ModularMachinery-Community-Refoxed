package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime JEI reload bridge. Safe to call before JEI has initialized or when JEI is absent.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiRuntimeReloader {

    private static final Set<Identifier> REGISTERED_MACHINE_CATEGORIES = ConcurrentHashMap.newKeySet();
    private static volatile Map<Identifier, List<MachineRecipeDisplay>> visibleDisplaysByMachine = Map.of();
    private static volatile boolean categoriesCaptured;
    private static volatile IJeiRuntime runtime;
    private static volatile long lastReloadedVersion = Long.MIN_VALUE;
    private static volatile long scheduledReloadVersion = Long.MIN_VALUE;

    private JeiRuntimeReloader() {
    }

    static void markRegisteredMachineCategories(Collection<Identifier> machineIds) {
        REGISTERED_MACHINE_CATEGORIES.clear();
        REGISTERED_MACHINE_CATEGORIES.addAll(machineIds);
        categoriesCaptured = true;
    }

    public static void setRuntime(IJeiRuntime runtime) {
        JeiRuntimeReloader.runtime = runtime;
        visibleDisplaysByMachine = Map.of();
        lastReloadedVersion = Long.MIN_VALUE;
        scheduledReloadVersion = Long.MIN_VALUE;
    }

    public static void clearRuntimeForTesting() {
        runtime = null;
        visibleDisplaysByMachine = Map.of();
        REGISTERED_MACHINE_CATEGORIES.clear();
        categoriesCaptured = false;
        lastReloadedVersion = Long.MIN_VALUE;
        scheduledReloadVersion = Long.MIN_VALUE;
    }

    public static void reloadIfAvailable(RuntimeContentSnapshot snapshot) {
        IJeiRuntime current = runtime;
        if (current == null) return;
        if (snapshot.contentVersion() == lastReloadedVersion
                || snapshot.contentVersion() == scheduledReloadVersion) return;
        scheduledReloadVersion = snapshot.contentVersion();
        Runnable reload = () -> {
            try {
                Map<Identifier, List<MachineRecipeDisplay>> displaysByMachine = MachineRecipeDisplays.byMachine(snapshot);
                Map<Identifier, List<MachineRecipeDisplay>> previousVisible = visibleDisplaysByMachine;
                Map<Identifier, List<MachineRecipeDisplay>> updatedVisible = new LinkedHashMap<>();
                Set<Identifier> refreshedMachineIds = new LinkedHashSet<>(previousVisible.keySet());
                refreshedMachineIds.addAll(snapshot.structures().keySet());
                for (Identifier machineId : refreshedMachineIds) {
                    if (categoriesCaptured && !REGISTERED_MACHINE_CATEGORIES.contains(machineId)) {
                        MMCR.LOG.warn("JEI category for synced machine {} was not registered; restart or reload JEI to view it", machineId);
                        continue;
                    }
                    var type = JeiMachineRecipeTypes.forMachine(machineId);
                    current.getRecipeManager().hideRecipes(type, previousVisible.getOrDefault(machineId, List.of()));
                    if (!snapshot.structures().containsKey(machineId)) continue;
                    List<MachineRecipeDisplay> displays = displaysByMachine.getOrDefault(machineId, List.of());
                    current.getRecipeManager().addRecipes(type, displays);
                    updatedVisible.put(machineId, displays);
                }
                visibleDisplaysByMachine = Map.copyOf(updatedVisible);
                lastReloadedVersion = snapshot.contentVersion();
            } finally {
                scheduledReloadVersion = Long.MIN_VALUE;
            }
        };
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) reload.run();
        else minecraft.execute(reload);
    }
}
