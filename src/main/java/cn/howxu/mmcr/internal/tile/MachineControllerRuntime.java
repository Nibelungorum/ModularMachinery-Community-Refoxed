package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.runtime.ComponentRuntime;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the authoritative runtime state and publishes immutable controller snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerRuntime {
    private final MachineControllerBlockEntity controller;
    private final StructureRuntime structure;
    private final ComponentRuntime components = new ComponentRuntime();
    private final CraftingRuntime craftingRuntime;
    private final FactoryRuntime factoryRuntime;
    private CraftingStateSnapshot craftingState = CraftingStateSnapshot.empty(0L, 0L, 0L);
    private ControllerRuntimeSnapshot publishedSnapshot;

    MachineControllerRuntime(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        this.structure = new StructureRuntime(controller);
        this.craftingRuntime = new CraftingRuntime(controller, components);
        this.factoryRuntime = new FactoryRuntime();
        publishSnapshot();
    }

    public void serverTick(ServerLevel level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            throw new IllegalArgumentException("Controller runtime tick requires a level and controller position");
        }
        if (controller.getLevel() != null && controller.getLevel() != level) {
            throw new IllegalArgumentException("Controller runtime level does not match the controller");
        }
        if (controller.getBlockPos() != null && !controller.getBlockPos().equals(controllerPos)) {
            throw new IllegalArgumentException("Controller runtime position does not match the controller");
        }
        structure.tick(level, controllerPos);
        controller.tickRuntimeWork(level, controllerPos);
    }

    public ControllerRuntimeSnapshot snapshot() {
        return publishedSnapshot;
    }

    void publishSnapshot() {
        controller.ensureFactoryRuntimeLoaded();
        StructureSnapshot structureSnapshot = structure.snapshot();
        FactorySnapshot factorySnapshot = factoryRuntime.snapshot();
        CraftingStateSnapshot nextCrafting = controller.getLevel() != null && controller.getLevel().isClientSide()
                ? craftingState : craftingRuntime.snapshot();
        publishSnapshot(structureSnapshot, factorySnapshot, nextCrafting);
    }

    private void publishSnapshot(StructureSnapshot structureSnapshot, FactorySnapshot factorySnapshot,
                                 CraftingStateSnapshot nextCrafting) {
        Machine machine = structureSnapshot.machine() != null ? structureSnapshot.machine() : structureSnapshot.configuredMachine();
        boolean factorySupported = machine != null && machine.hasFactory();
        boolean factoryControllerPresent = factorySupported && components.components().stream()
                .anyMatch(component -> component.getContainer() instanceof FactorySchedulerBlockEntity);
        int parallelControllerCount = (int) components.components().stream()
                .filter(component -> component.getContainer() instanceof ParallelControllerBlockEntity)
                .count();
        int maxParallelControllerCount = machine != null && machine.parallelizable()
                ? Math.max(1, machine.maxParallelism()) : 0;
        int controllerRole = machine == null ? 0 : machine.isHost() ? 1 : machine.isModule() ? 2 : 0;
        publishedSnapshot = new ControllerRuntimeSnapshot(structureSnapshot, components.capabilityVersion(),
                components.modifierVersion(),
                components.stateVersion(), components.foundModifiers(), components.foundLevels(),
                components.linkedPortPositions(), components.moduleConnectionStatus(), components.installedModuleCount(),
                components.capabilityAggregate(), nextCrafting, factorySnapshot, components.componentPresentations(),
                components.capabilityPresentations(), components.foundLevels().values().stream()
                        .map(level -> level.id().toString()).toList(),
                machine == null ? "" : machine.registryName().toString(),
                machine == null ? "" : machine.displayNameKey(), controllerRole, factorySupported, factoryControllerPresent,
                parallelControllerCount, maxParallelControllerCount, components.maxParallelism(machine));
    }

    List<ProcessingComponent> components() {
        return components.components();
    }

    ComponentRuntime componentRuntime() {
        return components;
    }

    public CraftingRuntime craftingRuntime() {
        return craftingRuntime;
    }

    public FactoryRuntime factoryRuntime() {
        return factoryRuntime;
    }

    void pauseCrafting() {
        craftingRuntime.pause();
        factoryRuntime.pause();
    }

    void resumeCrafting() {
        craftingRuntime.resume();
        factoryRuntime.resume();
    }

    void publishStructureState(boolean structureAreaLoaded, boolean formed,
                               @Nullable Machine configuredMachine, int matchedStage) {
        structure.setStructureAreaLoaded(structureAreaLoaded);
        structure.setFormed(formed);
        structure.setMachine(configuredMachine);
        structure.setMatchedStructureStage(matchedStage);
        publishSnapshot();
    }

    void requestStructureCheck() {
        structure.requestCheck();
    }

    void restoreStructureVersion(long version) {
        structure.restoreVersion(version);
    }

    void onStructureBlockChanged(BlockPos changedPos) {
        structure.onBlockChanged(changedPos);
    }

    void onStructureChunkChanged(ServerLevel level, BlockPos controllerPos) {
        structure.onChunkStateChanged(level, controllerPos);
    }

    StructureRuntime.StructureWorkSnapshot structureWorkSnapshot() {
        return structure.workSnapshot();
    }

    void publishStructureWork(StructureRuntime.StructureWorkSnapshot state) {
        structure.publishWork(state);
    }

    void startStructureScan(StructureMatcher.ScanState scan, Machine scanMachine,
                            Object scanCandidate, long steppedTick, long startedTick) {
        structure.setScan(scan);
        structure.setScanMachine(scanMachine);
        structure.setScanCandidate(scanCandidate);
        structure.setScanSteppedTick(steppedTick);
        structure.setScanStartedTick(startedTick);
    }

    StructureMatcher.ScanResult stepStructureScan(ServerLevel level, BlockPos controllerPos) {
        return structure.stepScan(level, controllerPos);
    }

    void clearStructureScan() {
        structure.clearScan();
    }

    void invalidateStructureScan(StructureMatcher.InvalidationReason reason) {
        structure.invalidateScan(reason);
    }

    boolean publishFormationState(Machine machine, BlockArray pattern,
                                  @Nullable CompiledMachinePattern compiledPattern,
                                  Direction facing, Direction rollFacing, int matchedStage) {
        boolean changed = structure.publishFormationState(machine, pattern, compiledPattern, facing, rollFacing, matchedStage);
        publishSnapshot();
        return changed;
    }

    boolean publishClientStructureState(@Nullable Machine machine, boolean formed,
                                        boolean structureAreaLoaded) {
        boolean changed = structure.publishClientState(machine, formed, structureAreaLoaded);
        publishSnapshot();
        return changed;
    }

    void resetStructure(@Nullable Machine configuredMachine, boolean forceVersion) {
        structure.reset(configuredMachine, forceVersion);
        publishSnapshot();
    }

    void publishCriticalStructureChunks(Set<ChunkPos> criticalChunks) {
        structure.setCriticalChunks(criticalChunks);
        publishSnapshot();
    }

    int maxParallelism(@Nullable Machine machine) {
        return components.maxParallelism(machine);
    }

    void publishClientComponentState(Map<Identifier, MachineLevel> levels,
                                     ModuleConnectionStatus status, int installedModuleCount) {
        components.replaceLevels(levels);
        components.replaceModuleConnectionState(status, installedModuleCount);
    }

    void publishComponentState(List<ProcessingComponent> nextComponents,
                               Map<String, List<RecipeModifier>> modifiers,
                               Map<Identifier, MachineLevel> levels,
                               Set<BlockPos> linkedPositions) {
        components.replaceComponents(nextComponents);
        components.replaceModifiers(modifiers);
        components.replaceLevels(levels);
        components.replaceLinkedPortPositions(linkedPositions);
        publishSnapshot();
    }

    void publishModuleConnectionState(ModuleConnectionStatus status, int installedModuleCount) {
        components.replaceModuleConnectionState(status, installedModuleCount);
        publishSnapshot();
    }

    void refreshModuleConnectionState() {
        if (!(controller.getLevel() instanceof ServerLevel)) {
            publishModuleConnectionState(ModuleConnectionStatus.notRequired(), 0);
            return;
        }
        publishModuleConnectionState(ModuleConnectionCoordinator.connectionStatus(controller),
                ModuleConnectionCoordinator.installedModuleCount(controller));
    }

    void publishCraftingState(@Nullable Identifier recipeId, CraftingStatus status,
                              @Nullable ExecutionStatus failure, int tick, int totalTick,
                              int parallelism, int maxParallelism) {
        boolean client = controller.getLevel() != null && controller.getLevel().isClientSide();
        boolean recipeLocked = client ? controller.hasClientRecipeLock() : controller.lockedRecipeId() != null;
        String lockedRecipeId = client ? controller.clientLockedRecipeId()
                : controller.lockedRecipeId() == null ? "" : controller.lockedRecipeId().toString();
        craftingState = new CraftingStateSnapshot(recipeId, status, failure,
                structure.version(), components.capabilityVersion(), components.modifierVersion(),
                tick, totalTick, parallelism, maxParallelism, recipeLocked, lockedRecipeId);
        publishSnapshot(structure.snapshot(), factoryRuntime.snapshot(), craftingState);
    }
}
