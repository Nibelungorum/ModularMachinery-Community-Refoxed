package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Projects one published controller runtime into immutable presentation snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerSyncRuntime {
    public MachineStateSnapshot machineState(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        return new MachineStateSnapshot(runtime.structure(), runtime.crafting(), runtime.capabilities(),
                runtime.installedModuleCount(), runtime.moduleConnectionStatus().connected());
    }

    public FactorySnapshot factoryState(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        FactorySnapshot factory = runtime.factory();
        Machine machine = runtime.structure().machine() != null
                ? runtime.structure().machine() : runtime.structure().configuredMachine();
        ExecutionStatus failure = factory.failure() == null ? runtime.crafting().failure() : factory.failure();
        return new FactorySnapshot(runtime.structure().formed(), factory.active(), factory.lanes(),
                factory.activeParallelism(), factory.laneLimit(), factory.activeLaneCount(),
                factory.maxParallelism(), factory.paused(), factory.presentationLanes(),
                machine == null ? "" : machine.displayNameKey(), parallelControllerCount(runtime), failure);
    }

    public boolean factoryControllerPresent(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        return hasFactoryMachine(runtime) && runtime.components().stream()
                .anyMatch(component -> component.getContainer() instanceof FactorySchedulerBlockEntity);
    }

    public boolean active(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        if (!runtime.structure().formed() || !runtime.structure().structureAreaLoaded()
                || runtime.crafting().status().isPaused() || runtime.factory().paused()) return false;
        return runtime.crafting().recipeId() != null || runtime.factory().active();
    }

    public int currentParallelism(ControllerRuntimeSnapshot runtime) {
        return factoryControllerPresent(runtime) ? runtime.factory().activeParallelism() : runtime.crafting().parallelism();
    }

    public int maxParallelism(ControllerRuntimeSnapshot runtime) {
        if (factoryControllerPresent(runtime)) return runtime.factory().maxParallelism();
        if (runtime.crafting().recipeId() != null) return runtime.crafting().maxParallelism();
        Machine machine = runtime.structure().configuredMachine();
        if (machine == null || !machine.parallelizable()) return 1;
        long max = 0L;
        for (ProcessingComponent component : runtime.components()) {
            if (component.getContainer() instanceof ParallelControllerBlockEntity parallel) {
                max += parallel.currentParallelism();
            }
        }
        long levelBonus = runtime.foundLevels().values().stream()
                .mapToLong(level -> level.modifier().parallelismBonus())
                .sum();
        long bounded = Math.min(Integer.MAX_VALUE, Math.max(1L, max) + levelBonus);
        return (int) Math.min(Math.max(1, machine.maxParallelism()), bounded);
    }

    public int activeFactoryThreadCount(ControllerRuntimeSnapshot runtime) {
        return factoryControllerPresent(runtime) ? runtime.factory().activeLaneCount() : 0;
    }

    public int parallelControllerCount(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        return (int) runtime.components().stream()
                .filter(component -> component.getContainer() instanceof ParallelControllerBlockEntity)
                .count();
    }

    public int maxParallelControllerCount(ControllerRuntimeSnapshot runtime) {
        Machine machine = runtime.structure().configuredMachine();
        if (machine == null || !machine.parallelizable()) return 0;
        return Math.max(1, machine.maxParallelism());
    }

    public String activeRecipe(ControllerRuntimeSnapshot runtime) {
        FactoryRuntime.ThreadSnapshot thread = activeFactoryThread(runtime);
        if (thread != null && !thread.recipeId().isEmpty()) return thread.recipeId();
        Identifier recipeId = runtime.crafting().recipeId();
        return recipeId == null ? "" : recipeId.toString();
    }

    public int tick(ControllerRuntimeSnapshot runtime) {
        FactoryRuntime.ThreadSnapshot thread = activeFactoryThread(runtime);
        return thread == null ? runtime.crafting().tick() : thread.tick();
    }

    public int totalTick(ControllerRuntimeSnapshot runtime) {
        FactoryRuntime.ThreadSnapshot thread = activeFactoryThread(runtime);
        return thread == null ? runtime.crafting().totalTick() : thread.totalTick();
    }

    public boolean recipeLocked(ControllerRuntimeSnapshot runtime) {
        FactoryRuntime.ThreadSnapshot thread = activeFactoryThread(runtime);
        return thread == null ? runtime.crafting().recipeLocked() : thread.locked();
    }

    public String lockedRecipeId(ControllerRuntimeSnapshot runtime) {
        FactoryRuntime.ThreadSnapshot thread = activeFactoryThread(runtime);
        String locked = thread == null ? runtime.crafting().lockedRecipeId() : thread.lockedRecipeId();
        return locked == null ? "" : locked;
    }

    public String failureMessage(ControllerRuntimeSnapshot runtime) {
        FactorySnapshot factory = runtime.factory();
        String factoryFailure = failureUnloc(factory.failure());
        return factoryFailure.isEmpty() ? failureUnloc(runtime.crafting().failure()) : factoryFailure;
    }

    public String failureMessage(FactorySnapshot factory) {
        return failureUnloc(factory == null ? null : factory.failure());
    }

    public List<String> foundLevelIds(ControllerRuntimeSnapshot runtime) {
        return runtime == null ? List.of() : runtimeLevels(runtime);
    }

    public Machine machine(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        return runtime.structure().machine() != null ? runtime.structure().machine() : runtime.structure().configuredMachine();
    }

    private FactoryRuntime.ThreadSnapshot activeFactoryThread(ControllerRuntimeSnapshot runtime) {
        if (!factoryControllerPresent(runtime)) return null;
        return runtime.factory().presentationLanes().stream()
                .filter(FactoryRuntime.ThreadSnapshot::active)
                .findFirst().orElse(null);
    }

    private boolean hasFactoryMachine(ControllerRuntimeSnapshot runtime) {
        Machine machine = runtime.structure().configuredMachine();
        return machine != null && machine.hasFactory();
    }

    private static List<String> runtimeLevels(ControllerRuntimeSnapshot runtime) {
        return runtime.foundLevels().values().stream().map(level -> level.id().toString()).toList();
    }

    private static String failureUnloc(cn.howxu.mmcr.api.capability.status.ExecutionStatus failure) {
        if (failure == null) return "";
        return switch (failure.details().getOrDefault("reason", "")) {
            case "module_connection" -> "gui.mmcr.controller.failure.module_connection";
            case "no_output_capacity" -> "gui.mmcr.controller.failure.missing_output";
            case "insufficient_energy" -> "gui.mmcr.controller.failure.missing_energy";
            case "level_insufficient" -> "gui.mmcr.controller.failure.level_insufficient";
            case "version_invalidated" -> "gui.mmcr.controller.failure.structure_changed";
            default -> "gui.mmcr.controller.failure.missing_input";
        };
    }

    private static void require(ControllerRuntimeSnapshot runtime) {
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
    }
}
