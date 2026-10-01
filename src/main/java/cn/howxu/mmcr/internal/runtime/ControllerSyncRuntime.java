package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Projects one published controller runtime into immutable presentation snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerSyncRuntime {
    public MachineStateSnapshot machineState(ControllerRuntimeSnapshot runtime) {
        Machine machine = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() : runtime.structure().machine();
        return machineState(runtime, MachineRegistry.recipePoolForMachine(machine));
    }

    public MachineStateSnapshot machineState(ControllerRuntimeSnapshot runtime, @Nullable ResourceLocation recipePoolId) {
        require(runtime);
        ExecutionStatus failure = runtime.factory().failure() == null ? runtime.crafting().failure() : runtime.factory().failure();
        return new MachineStateSnapshot(
                runtime.structure().formed(),
                runtime.structure().structureAreaLoaded(),
                active(runtime),
                activeRecipe(runtime),
                runtime.foundLevelIds(),
                runtime.machineId(),
                runtime.controllerRole(),
                runtime.installedModuleCount(),
                runtime.moduleConnectionStatus().connected(),
                runtime.moduleConnectionStatus().connected()
                        ? runtime.moduleConnectionStatus().connectedHostId().toString() : "",
                runtime.crafting().status().getStatus(),
                runtime.crafting().status().getUnlocMessage(),
                failure,
                tick(runtime),
                totalTick(runtime),
                currentParallelism(runtime),
                maxParallelism(runtime),
                runtime.crafting().status().isPaused() || runtime.factory().paused(),
                runtime.factoryControllerPresent(),
                runtime.factoryControllerPresent() ? runtime.factory().laneLimit() : 0,
                activeFactoryThreadCount(runtime),
                runtime.parallelControllerCount(),
                runtime.maxParallelControllerCount(),
                runtime.componentPresentations(),
                runtime.capabilityPresentations(),
                runtime.structure().matchedStage(),
                stageCount(runtime), runtime.recipePresentation(), recipePoolId == null ? "" : recipePoolId.toString());
    }

    public FactorySnapshot factoryState(ControllerRuntimeSnapshot runtime) {
        Machine machine = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() : runtime.structure().machine();
        return factoryState(runtime, MachineRegistry.recipePoolForMachine(machine));
    }

    public FactorySnapshot factoryState(ControllerRuntimeSnapshot runtime, @Nullable ResourceLocation recipePoolId) {
        require(runtime);
        FactorySnapshot factory = runtime.factory();
        ExecutionStatus failure = factory.failure() == null ? runtime.crafting().failure() : factory.failure();
        return new FactorySnapshot(runtime.structure().formed(), factory.active(), factory.lanes(),
                factory.laneLimit(), factory.activeLaneCount(), runtime.maxParallelism(),
                factory.paused(), factory.presentationLanes(), runtime.machineName(),
                runtime.parallelControllerCount(), failure, runtime.foundLevelIds(),
                runtime.structure().matchedStage(), stageCount(runtime), runtime.machineId(),
                recipePoolId == null ? "" : recipePoolId.toString(), runtime.controllerRole(),
                runtime.moduleConnectionStatus().connected()
                        ? runtime.moduleConnectionStatus().connectedHostId().toString() : "");
    }

    public boolean factoryControllerPresent(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        return runtime.factoryControllerPresent();
    }

    public boolean active(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        if (!runtime.structure().formed() || !runtime.structure().structureAreaLoaded()
                || runtime.crafting().status().isPaused() || runtime.factory().paused()) return false;
        Machine machine = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() : runtime.structure().machine();
        return runtime.crafting().recipeId() != null || runtime.factory().active()
                || machine != null && machine.behavior() instanceof TickBehavior;
    }

    public long currentParallelism(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        FactoryRuntime.ThreadSnapshot thread = activeFactoryThread(runtime);
        return thread == null ? runtime.crafting().parallelism() : thread.parallelism();
    }

    public long maxParallelism(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        return runtime.maxParallelism();
    }

    public int activeFactoryThreadCount(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        return factoryControllerPresent(runtime) ? runtime.factory().activeLaneCount() : 0;
    }

    public int parallelControllerCount(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        return runtime.parallelControllerCount();
    }

    public long maxParallelControllerCount(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        return runtime.maxParallelControllerCount();
    }

    private static int stageCount(ControllerRuntimeSnapshot runtime) {
        Machine machine = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine()
                : runtime.structure().machine();
        if (machine == null) return 1;
        List<MachineStructureStage> stages = machine.structureStages();
        return stages == null || stages.isEmpty() ? 1 : stages.size();
    }

    public String activeRecipe(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        FactoryRuntime.ThreadSnapshot thread = activeFactoryThread(runtime);
        if (thread != null && !thread.recipeId().isEmpty()) return thread.recipeId();
        return runtime.crafting().recipeId() == null ? "" : runtime.crafting().recipeId().toString();
    }

    public int tick(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        FactoryRuntime.ThreadSnapshot thread = activeFactoryThread(runtime);
        return thread == null ? runtime.crafting().tick() : thread.tick();
    }

    public int totalTick(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        FactoryRuntime.ThreadSnapshot thread = activeFactoryThread(runtime);
        return thread == null ? runtime.crafting().totalTick() : thread.totalTick();
    }

    /**
     * Temporary translation-key presentation boundary for unchanged Task 7 packet/menu callers.
     * Remove these overloads when those callers consume typed failures directly.
     */
    public String failureMessage(ControllerRuntimeSnapshot runtime) {
        require(runtime);
        FactorySnapshot factory = runtime.factory();
        String factoryFailure = failureUnloc(factory.failure());
        return factoryFailure.isEmpty() ? failureUnloc(runtime.crafting().failure()) : factoryFailure;
    }

    public String failureMessage(FactorySnapshot factory) {
        return failureUnloc(factory == null ? null : factory.failure());
    }

    public String failureMessage(ExecutionStatus failure) {
        return failureUnloc(failure);
    }

    public List<String> foundLevelIds(ControllerRuntimeSnapshot runtime) {
        return runtime == null ? List.of() : runtime.foundLevelIds();
    }

    private FactoryRuntime.ThreadSnapshot activeFactoryThread(ControllerRuntimeSnapshot runtime) {
        if (!factoryControllerPresent(runtime)) return null;
        return runtime.factory().presentationLanes().stream()
                .filter(FactoryRuntime.ThreadSnapshot::active)
                .findFirst().orElse(null);
    }

    private static String failureUnloc(ExecutionStatus failure) {
        if (failure == null) return "";
        var reason = failure.reason();
        return (reason == null ? BuiltinFailureReasons.UNKNOWN : reason).translationKey();
    }

    private static void require(ControllerRuntimeSnapshot runtime) {
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
    }
}
