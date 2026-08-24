package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the authoritative runtime state and publishes immutable controller snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerRuntime {
    private final MachineControllerBlockEntity controller;
    private final StructureRuntime structure;
    private final ComponentRuntime components = new ComponentRuntime();
    private CraftingContext craftingContext = new CraftingContext(new CapabilitySnapshot(List.of()));
    private CraftingStateSnapshot craftingState = CraftingStateSnapshot.empty(0L, 0L, 0L);
    private long contextCapabilityVersion = Long.MIN_VALUE;
    private long contextModifierVersion = Long.MIN_VALUE;
    private long contextStateVersion = Long.MIN_VALUE;

    public MachineControllerRuntime(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
        this.structure = new StructureRuntime(controller);
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
        return new ControllerRuntimeSnapshot(structure.snapshot(), components.components(), components.capabilities(),
                components.capabilityVersion(), components.modifierVersion(), components.stateVersion(),
                components.foundModifiers(), components.foundLevels(), components.linkedPortPositions(),
                components.moduleConnectionStatus(), components.installedModuleCount(), components.capabilityAggregate(), craftingState,
                FactorySnapshot.empty());
    }

    public CraftingContext craftingContext() {
        return craftingContext;
    }

    public void publishStructureState(boolean structureAreaLoaded) {
        structure.setStructureAreaLoaded(structureAreaLoaded);
    }

    public void setConfiguredMachine(@Nullable Machine machine) {
        structure.setMachine(machine);
    }

    public void requestStructureCheck() {
        structure.requestCheck();
    }

    public void onStructureBlockChanged(BlockPos changedPos) {
        structure.onBlockChanged(changedPos);
    }

    public void onStructureChunkChanged(ServerLevel level, BlockPos controllerPos) {
        structure.onChunkStateChanged(level, controllerPos);
    }

    public void setStructureDiagnosticRequest(boolean requested, @Nullable UUID playerId,
                                              @Nullable ResourceKey<Level> dimension) {
        structure.setDiagnosticRequested(requested);
        structure.setDiagnosticPlayerId(playerId);
        structure.setDiagnosticDimension(dimension);
    }

    public boolean structureDiagnosticRequested() {
        return structure.diagnosticRequested();
    }

    public @Nullable UUID structureDiagnosticPlayerId() {
        return structure.diagnosticPlayerId();
    }

    public @Nullable ResourceKey<Level> structureDiagnosticDimension() {
        return structure.diagnosticDimension();
    }

    public boolean structurePendingInvalidation() {
        return structure.pendingInvalidation();
    }

    public int structureScanCursor() {
        return structure.scanCursor();
    }

    public void setStructureFormed(boolean formed) {
        structure.setFormed(formed);
    }

    public void setStructureCheckActive(boolean active) {
        structure.setCheckActive(active);
    }

    public boolean structureCheckActive() {
        return structure.checkActive();
    }

    public void setStructureDirty(boolean dirty) {
        structure.setDirty(dirty);
    }

    public void setStructureCheckCounter(int counter) {
        structure.setCheckCounter(counter);
    }

    public int structureCheckCounter() {
        return structure.checkCounter();
    }

    public void setNextStructureCheckTick(long tick) {
        structure.setNextCheckTick(tick);
    }

    public long nextStructureCheckTick() {
        return structure.nextCheckTick();
    }

    public boolean hasStructureScan() {
        return structure.hasScan();
    }

    public @Nullable Machine structureScanMachine() {
        return structure.scanMachine();
    }

    public @Nullable Object structureScanCandidate() {
        return structure.scanCandidate();
    }

    public @Nullable StructureMatcher.Mismatch structurePreviousMismatch() {
        return structure.previousMismatch();
    }

    public @Nullable Object structurePreviousMismatchPattern() {
        return structure.previousMismatchPattern();
    }

    public long structureScanStartedTick() {
        return structure.scanStartedTick();
    }

    public long structureScanSteppedTick() {
        return structure.scanSteppedTick();
    }

    public void setStructureScanSteppedTick(long tick) {
        structure.setScanSteppedTick(tick);
    }

    public int structureScanBatchSize() {
        return structure.scanBatchSize();
    }

    public int structureScanEntryCount() {
        return structure.scanEntryCount();
    }

    public boolean structureScanVersionMatches(long structureVersion) {
        return structure.scanVersion() == structureVersion;
    }

    public @Nullable Direction structureScanFacing() {
        return structure.scanFacing();
    }

    public Direction structureScanRollFacing() {
        return structure.scanRollFacing();
    }

    public int structureScanStage() {
        return structure.scanStage();
    }

    public @Nullable Object structureScanPattern() {
        return structure.scanPattern();
    }

    public void startStructureScan(StructureMatcher.ScanState scan, Machine scanMachine,
                                   Object scanCandidate, long steppedTick, long startedTick) {
        structure.setScan(scan);
        structure.setScanMachine(scanMachine);
        structure.setScanCandidate(scanCandidate);
        structure.setScanSteppedTick(steppedTick);
        structure.setScanStartedTick(startedTick);
    }

    public StructureMatcher.ScanResult stepStructureScan(ServerLevel level, BlockPos controllerPos) {
        return structure.stepScan(level, controllerPos);
    }

    public void clearStructureScan() {
        structure.clearScan();
    }

    public void invalidateStructureScan(StructureMatcher.InvalidationReason reason) {
        structure.invalidateScan(reason);
    }

    public void setStructurePendingInvalidation(boolean pending) {
        structure.setPendingInvalidation(pending);
    }

    public void setStructurePreviousMismatch(@Nullable StructureMatcher.Mismatch mismatch,
                                              @Nullable Object pattern) {
        structure.setPreviousMismatch(mismatch);
        structure.setPreviousMismatchPattern(pattern);
    }

    public void setStructureFormationFailure(@Nullable PortRequirementSpec.Failure failure) {
        structure.setFormationFailure(failure);
    }

    public @Nullable PortRequirementSpec.Failure structureFormationFailure() {
        return structure.formationFailure();
    }

    public void setStructureMismatchDiagnostic(@Nullable String diagnostic) {
        structure.setMismatchDiagnostic(diagnostic);
    }

    public @Nullable String structureMismatchDiagnostic() {
        return structure.mismatchDiagnostic();
    }

    public void setLastStructureError(@Nullable Object error) {
        structure.setLastStructureError(error);
    }

    public boolean publishFormationState(Machine machine, BlockArray pattern,
                                         @Nullable CompiledMachinePattern compiledPattern,
                                         Direction facing, Direction rollFacing, int matchedStage) {
        return structure.publishFormationState(machine, pattern, compiledPattern, facing, rollFacing, matchedStage);
    }

    public boolean publishClientStructureState(@Nullable Machine machine, boolean formed) {
        return structure.publishClientState(machine, formed);
    }

    public void setMatchedStructureStage(int matchedStage) {
        structure.setMatchedStructureStage(matchedStage);
    }

    public void resetStructure(@Nullable Machine configuredMachine, boolean forceVersion) {
        structure.reset(configuredMachine, forceVersion);
    }

    public void setCriticalStructureChunks(Set<ChunkPos> criticalChunks) {
        structure.setCriticalChunks(criticalChunks);
    }

    public void replaceLevels(Map<Identifier, MachineLevel> levels) {
        components.replaceLevels(levels);
    }

    public void replaceModifiers(Map<String, List<RecipeModifier>> modifiers) {
        components.replaceModifiers(modifiers);
    }

    public void replaceLinkedPortPositions(Set<BlockPos> positions) {
        components.replaceLinkedPortPositions(positions);
    }

    public void clearComponents() {
        components.clear();
    }

    public int maxParallelism(@Nullable Machine machine) {
        return components.maxParallelism(machine);
    }

    public void publishClientComponentState(Map<Identifier, MachineLevel> levels,
                                            ModuleConnectionStatus status, int installedModuleCount) {
        components.replaceLevels(levels);
        components.replaceModuleConnectionState(status, installedModuleCount);
    }

    public void publishComponentState(List<ProcessingComponent> nextComponents,
                                      Map<String, List<RecipeModifier>> modifiers,
                                      Map<Identifier, MachineLevel> levels,
                                      Set<BlockPos> linkedPositions) {
        components.replaceComponents(nextComponents);
        components.replaceModifiers(modifiers);
        components.replaceLevels(levels);
        components.replaceLinkedPortPositions(linkedPositions);
        refreshCraftingContext();
    }

    public void publishModuleConnectionState(ModuleConnectionStatus status, int installedModuleCount) {
        components.replaceModuleConnectionState(status, installedModuleCount);
    }

    public void refreshModuleConnectionState() {
        if (!(controller.getLevel() instanceof ServerLevel)) {
            publishModuleConnectionState(ModuleConnectionStatus.notRequired(), 0);
            return;
        }
        publishModuleConnectionState(ModuleConnectionCoordinator.connectionStatus(controller),
                ModuleConnectionCoordinator.installedModuleCount(controller));
    }

    public void publishCraftingState(@Nullable Identifier recipeId, CraftingStatus status,
                                     @Nullable ExecutionStatus failure) {
        refreshCraftingContext();
        craftingState = new CraftingStateSnapshot(recipeId, status, failure,
                structure.version(), components.capabilityVersion(), components.modifierVersion());
    }

    private void refreshCraftingContext() {
        if (contextCapabilityVersion == components.capabilityVersion()
                && contextModifierVersion == components.modifierVersion()
                && contextStateVersion == components.stateVersion()) return;
        craftingContext = new CraftingContext(new CapabilitySnapshot(components.capabilities()), components.modifierList());
        contextCapabilityVersion = components.capabilityVersion();
        contextModifierVersion = components.modifierVersion();
        contextStateVersion = components.stateVersion();
    }
}
