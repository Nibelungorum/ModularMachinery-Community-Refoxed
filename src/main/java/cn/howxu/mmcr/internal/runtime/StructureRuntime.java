package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the selected machine, structure scan, formation, and loaded-area state for one controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureRuntime {
    private final MachineControllerBlockEntity controller;

    private Machine machine;
    private Machine foundMachine;
    private BlockArray foundPattern;
    private CompiledMachinePattern foundCompiledPattern;
    private Direction controllerFacing;
    private Direction matchedRollFacing = Direction.SOUTH;
    private int matchedStructureStage;
    private long version;
    private int checkCounter;
    private long nextCheckTick = -1L;
    private boolean dirty = true;
    private @Nullable StructureMatcher.ScanState scan;
    private @Nullable Machine scanMachine;
    private @Nullable Object scanCandidate;
    private @Nullable StructureMatcher.Mismatch previousMismatch;
    private @Nullable Object previousMismatchPattern;
    private boolean pendingInvalidation;
    private long scanSteppedTick = Long.MIN_VALUE;
    private long scanStartedTick = Long.MIN_VALUE;
    private boolean checkActive;
    private boolean diagnosticRequested;
    private @Nullable UUID diagnosticPlayerId;
    private @Nullable ResourceKey<Level> diagnosticDimension;
    private @Nullable PortRequirementSpec.Failure formationFailure;
    private @Nullable String mismatchDiagnostic;
    private @Nullable Object lastStructureError;
    private boolean formed;
    private boolean structureAreaLoaded = true;
    private Set<ChunkPos> criticalChunks = Set.of();

    public StructureRuntime(MachineControllerBlockEntity controller) {
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        this.controller = controller;
    }

    public void requestCheck() {
        dirty = true;
        nextCheckTick = -1L;
    }

    public void onBlockChanged(BlockPos position) {
        controller.handleStructureBlockChanged(position);
    }

    public void tick(ServerLevel level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            throw new IllegalArgumentException("Structure runtime tick requires a level and controller position");
        }
        controller.tickStructure(level, controllerPos);
    }

    public void onChunkStateChanged(ServerLevel level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            throw new IllegalArgumentException("Structure chunk state requires a level and controller position");
        }
        controller.handleStructureChunkChanged(level, controllerPos);
    }

    public void reset() {
        boolean hadState = foundMachine != null || foundPattern != null || foundCompiledPattern != null || formed || scan != null;
        long nextVersion = hadState ? version + 1L : version;
        machine = null;
        foundMachine = null;
        foundPattern = null;
        foundCompiledPattern = null;
        controllerFacing = null;
        matchedRollFacing = Direction.SOUTH;
        matchedStructureStage = 0;
        version = nextVersion;
        checkCounter = 0;
        nextCheckTick = -1L;
        dirty = false;
        scan = null;
        scanMachine = null;
        scanCandidate = null;
        previousMismatch = null;
        previousMismatchPattern = null;
        pendingInvalidation = false;
        scanSteppedTick = Long.MIN_VALUE;
        scanStartedTick = Long.MIN_VALUE;
        checkActive = false;
        diagnosticRequested = false;
        diagnosticPlayerId = null;
        diagnosticDimension = null;
        formationFailure = null;
        mismatchDiagnostic = null;
        lastStructureError = null;
        formed = false;
        structureAreaLoaded = true;
        criticalChunks = Set.of();
    }

    public boolean formed() {
        return formed;
    }

    public long version() {
        return version;
    }

    public StructureSnapshot snapshot() {
        return new StructureSnapshot(machine, foundMachine, foundPattern, foundCompiledPattern, controllerFacing,
                matchedRollFacing, matchedStructureStage, formed, version, lastStructureError,
                mismatchDiagnostic, formationFailure, dirty, structureAreaLoaded, criticalChunks);
    }

    public Machine machine() {
        return machine;
    }

    public void setMachine(@Nullable Machine machine) {
        this.machine = machine;
    }

    public @Nullable Machine foundMachine() {
        return foundMachine;
    }

    public void setFoundMachine(@Nullable Machine foundMachine) {
        this.foundMachine = foundMachine;
    }

    public @Nullable BlockArray foundPattern() {
        return foundPattern;
    }

    public void setFoundPattern(@Nullable BlockArray foundPattern) {
        this.foundPattern = foundPattern;
    }

    public @Nullable CompiledMachinePattern foundCompiledPattern() {
        return foundCompiledPattern;
    }

    public void setFoundCompiledPattern(@Nullable CompiledMachinePattern foundCompiledPattern) {
        this.foundCompiledPattern = foundCompiledPattern;
    }

    public @Nullable Direction controllerFacing() {
        return controllerFacing;
    }

    public void setControllerFacing(@Nullable Direction controllerFacing) {
        this.controllerFacing = controllerFacing;
    }

    public Direction matchedRollFacing() {
        return matchedRollFacing;
    }

    public void setMatchedRollFacing(Direction matchedRollFacing) {
        this.matchedRollFacing = matchedRollFacing == null ? Direction.SOUTH : matchedRollFacing;
    }

    public int matchedStructureStage() {
        return matchedStructureStage;
    }

    public void setMatchedStructureStage(int matchedStructureStage) {
        this.matchedStructureStage = matchedStructureStage;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public boolean dirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public int checkCounter() {
        return checkCounter;
    }

    public void setCheckCounter(int checkCounter) {
        this.checkCounter = checkCounter;
    }

    public long nextCheckTick() {
        return nextCheckTick;
    }

    public void setNextCheckTick(long nextCheckTick) {
        this.nextCheckTick = nextCheckTick;
    }

    public @Nullable StructureMatcher.ScanState scan() {
        return scan;
    }

    public void setScan(@Nullable StructureMatcher.ScanState scan) {
        this.scan = scan;
    }

    public @Nullable Machine scanMachine() {
        return scanMachine;
    }

    public void setScanMachine(@Nullable Machine scanMachine) {
        this.scanMachine = scanMachine;
    }

    public @Nullable Object scanCandidate() {
        return scanCandidate;
    }

    public void setScanCandidate(@Nullable Object scanCandidate) {
        this.scanCandidate = scanCandidate;
    }

    public @Nullable StructureMatcher.Mismatch previousMismatch() {
        return previousMismatch;
    }

    public void setPreviousMismatch(@Nullable StructureMatcher.Mismatch previousMismatch) {
        this.previousMismatch = previousMismatch;
    }

    public @Nullable Object previousMismatchPattern() {
        return previousMismatchPattern;
    }

    public void setPreviousMismatchPattern(@Nullable Object previousMismatchPattern) {
        this.previousMismatchPattern = previousMismatchPattern;
    }

    public boolean pendingInvalidation() {
        return pendingInvalidation;
    }

    public void setPendingInvalidation(boolean pendingInvalidation) {
        this.pendingInvalidation = pendingInvalidation;
    }

    public long scanSteppedTick() {
        return scanSteppedTick;
    }

    public void setScanSteppedTick(long scanSteppedTick) {
        this.scanSteppedTick = scanSteppedTick;
    }

    public long scanStartedTick() {
        return scanStartedTick;
    }

    public void setScanStartedTick(long scanStartedTick) {
        this.scanStartedTick = scanStartedTick;
    }

    public boolean checkActive() {
        return checkActive;
    }

    public void setCheckActive(boolean checkActive) {
        this.checkActive = checkActive;
    }

    public boolean diagnosticRequested() {
        return diagnosticRequested;
    }

    public void setDiagnosticRequested(boolean diagnosticRequested) {
        this.diagnosticRequested = diagnosticRequested;
    }

    public @Nullable UUID diagnosticPlayerId() {
        return diagnosticPlayerId;
    }

    public void setDiagnosticPlayerId(@Nullable UUID diagnosticPlayerId) {
        this.diagnosticPlayerId = diagnosticPlayerId;
    }

    public @Nullable ResourceKey<Level> diagnosticDimension() {
        return diagnosticDimension;
    }

    public void setDiagnosticDimension(@Nullable ResourceKey<Level> diagnosticDimension) {
        this.diagnosticDimension = diagnosticDimension;
    }

    public @Nullable PortRequirementSpec.Failure formationFailure() {
        return formationFailure;
    }

    public void setFormationFailure(@Nullable PortRequirementSpec.Failure formationFailure) {
        this.formationFailure = formationFailure;
    }

    public @Nullable String mismatchDiagnostic() {
        return mismatchDiagnostic;
    }

    public void setMismatchDiagnostic(@Nullable String mismatchDiagnostic) {
        this.mismatchDiagnostic = mismatchDiagnostic;
    }

    public @Nullable Object lastStructureError() {
        return lastStructureError;
    }

    public void setLastStructureError(@Nullable Object lastStructureError) {
        this.lastStructureError = lastStructureError;
    }

    public void setFormed(boolean formed) {
        if (this.formed == formed) return;
        this.formed = formed;
        version++;
    }

    public boolean structureAreaLoaded() {
        return structureAreaLoaded;
    }

    public void setStructureAreaLoaded(boolean structureAreaLoaded) {
        this.structureAreaLoaded = structureAreaLoaded;
    }

    public Set<ChunkPos> criticalChunks() {
        return criticalChunks;
    }

    public void setCriticalChunks(Set<ChunkPos> criticalChunks) {
        this.criticalChunks = Set.copyOf(criticalChunks == null ? Set.of() : criticalChunks);
    }

    public List<ChunkPos> criticalChunkList() {
        return criticalChunks.stream().toList();
    }

    public UUID diagnosticPlayerOrNull() {
        return diagnosticPlayerId;
    }
}
