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
import java.util.Objects;
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
        if (!dirty || nextCheckTick >= 0L) version++;
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
        reset(null, false);
    }

    void reset(@Nullable Machine configuredMachine, boolean forceVersion) {
        boolean hadState = foundMachine != null || foundPattern != null || foundCompiledPattern != null
                || controllerFacing != null || matchedStructureStage != 0 || formed || scan != null
                || !Objects.equals(machine, configuredMachine);
        long nextVersion = hadState || forceVersion ? version + 1L : version;
        machine = configuredMachine;
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

    StructureWorkSnapshot workSnapshot() {
        return new StructureWorkSnapshot(dirty, checkCounter, nextCheckTick,
                scan == null ? null : new StructureWorkSnapshot.ScanView(scan.cursor(), scan.batchSize(), scan.entries().size(),
                        scan.structureVersion(), scan.frontFacing(), scan.rollFacing(), scan.stageNumber(), scan.patternIdentity()),
                scanMachine, scanCandidate,
                previousMismatch, previousMismatchPattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                mismatchDiagnostic, lastStructureError);
    }

    void publishWork(StructureWorkSnapshot state) {
        dirty = state.dirty();
        checkCounter = state.checkCounter();
        nextCheckTick = state.nextCheckTick();
        scanMachine = state.scanMachine();
        scanCandidate = state.scanCandidate();
        previousMismatch = state.previousMismatch();
        previousMismatchPattern = state.previousMismatchPattern();
        pendingInvalidation = state.pendingInvalidation();
        scanSteppedTick = state.scanSteppedTick();
        scanStartedTick = state.scanStartedTick();
        checkActive = state.checkActive();
        diagnosticRequested = state.diagnosticRequested();
        diagnosticPlayerId = state.diagnosticPlayerId();
        diagnosticDimension = state.diagnosticDimension();
        formationFailure = state.formationFailure();
        mismatchDiagnostic = state.mismatchDiagnostic();
        lastStructureError = state.lastStructureError();
    }

    Machine machine() {
        return machine;
    }

    void setMachine(@Nullable Machine machine) {
        if (Objects.equals(this.machine, machine)) return;
        this.machine = machine;
        version++;
    }

    @Nullable Machine foundMachine() {
        return foundMachine;
    }

    @Nullable BlockArray foundPattern() {
        return foundPattern;
    }

    @Nullable CompiledMachinePattern foundCompiledPattern() {
        return foundCompiledPattern;
    }

    @Nullable Direction controllerFacing() {
        return controllerFacing;
    }

    Direction matchedRollFacing() {
        return matchedRollFacing;
    }

    int matchedStructureStage() {
        return matchedStructureStage;
    }

    void setMatchedStructureStage(int matchedStructureStage) {
        if (this.matchedStructureStage == matchedStructureStage) return;
        this.matchedStructureStage = matchedStructureStage;
        version++;
    }

    boolean dirty() {
        return dirty;
    }

    void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    int checkCounter() {
        return checkCounter;
    }

    void setCheckCounter(int checkCounter) {
        this.checkCounter = checkCounter;
    }

    long nextCheckTick() {
        return nextCheckTick;
    }

    void setNextCheckTick(long nextCheckTick) {
        this.nextCheckTick = nextCheckTick;
    }

    @Nullable StructureMatcher.ScanState scan() {
        return scan;
    }

    void setScan(@Nullable StructureMatcher.ScanState scan) {
        this.scan = scan;
    }

    @Nullable Machine scanMachine() {
        return scanMachine;
    }

    void setScanMachine(@Nullable Machine scanMachine) {
        this.scanMachine = scanMachine;
    }

    @Nullable Object scanCandidate() {
        return scanCandidate;
    }

    void setScanCandidate(@Nullable Object scanCandidate) {
        this.scanCandidate = scanCandidate;
    }

    @Nullable StructureMatcher.Mismatch previousMismatch() {
        return previousMismatch;
    }

    void setPreviousMismatch(@Nullable StructureMatcher.Mismatch previousMismatch) {
        this.previousMismatch = previousMismatch;
    }

    @Nullable Object previousMismatchPattern() {
        return previousMismatchPattern;
    }

    void setPreviousMismatchPattern(@Nullable Object previousMismatchPattern) {
        this.previousMismatchPattern = previousMismatchPattern;
    }

    boolean pendingInvalidation() {
        return pendingInvalidation;
    }

    void setPendingInvalidation(boolean pendingInvalidation) {
        this.pendingInvalidation = pendingInvalidation;
    }

    long scanSteppedTick() {
        return scanSteppedTick;
    }

    void setScanSteppedTick(long scanSteppedTick) {
        this.scanSteppedTick = scanSteppedTick;
    }

    long scanStartedTick() {
        return scanStartedTick;
    }

    void setScanStartedTick(long scanStartedTick) {
        this.scanStartedTick = scanStartedTick;
    }

    boolean checkActive() {
        return checkActive;
    }

    void setCheckActive(boolean checkActive) {
        this.checkActive = checkActive;
    }

    boolean diagnosticRequested() {
        return diagnosticRequested;
    }

    void setDiagnosticRequested(boolean diagnosticRequested) {
        this.diagnosticRequested = diagnosticRequested;
    }

    @Nullable UUID diagnosticPlayerId() {
        return diagnosticPlayerId;
    }

    void setDiagnosticPlayerId(@Nullable UUID diagnosticPlayerId) {
        this.diagnosticPlayerId = diagnosticPlayerId;
    }

    @Nullable ResourceKey<Level> diagnosticDimension() {
        return diagnosticDimension;
    }

    void setDiagnosticDimension(@Nullable ResourceKey<Level> diagnosticDimension) {
        this.diagnosticDimension = diagnosticDimension;
    }

    @Nullable PortRequirementSpec.Failure formationFailure() {
        return formationFailure;
    }

    void setFormationFailure(@Nullable PortRequirementSpec.Failure formationFailure) {
        this.formationFailure = formationFailure;
    }

    @Nullable String mismatchDiagnostic() {
        return mismatchDiagnostic;
    }

    void setMismatchDiagnostic(@Nullable String mismatchDiagnostic) {
        this.mismatchDiagnostic = mismatchDiagnostic;
    }

    @Nullable Object lastStructureError() {
        return lastStructureError;
    }

    void setLastStructureError(@Nullable Object lastStructureError) {
        this.lastStructureError = lastStructureError;
    }

    void setFormed(boolean formed) {
        if (this.formed == formed) return;
        this.formed = formed;
        version++;
    }

    boolean publishFormationState(Machine machine, BlockArray pattern,
                                         @Nullable CompiledMachinePattern compiledPattern,
                                         Direction facing, Direction rollFacing, int matchedStage) {
        Direction normalizedRoll = rollFacing == null ? Direction.SOUTH : rollFacing;
        boolean changed = !Objects.equals(this.machine, machine)
                || !Objects.equals(this.foundMachine, machine)
                || !Objects.equals(this.foundPattern, pattern)
                || !Objects.equals(this.foundCompiledPattern, compiledPattern)
                || !Objects.equals(this.controllerFacing, facing)
                || this.matchedRollFacing != normalizedRoll
                || this.matchedStructureStage != matchedStage
                || !formed;
        this.machine = machine;
        this.foundMachine = machine;
        this.foundPattern = pattern;
        this.foundCompiledPattern = compiledPattern;
        this.controllerFacing = facing;
        this.matchedRollFacing = normalizedRoll;
        this.matchedStructureStage = matchedStage;
        this.formed = true;
        if (changed) version++;
        return changed;
    }

    boolean publishClientState(@Nullable Machine machine, boolean formed, boolean structureAreaLoaded) {
        boolean changed = !Objects.equals(this.machine, machine)
                || !Objects.equals(this.foundMachine, machine)
                || this.foundPattern != null
                || this.foundCompiledPattern != null
                || this.controllerFacing != null
                || this.matchedStructureStage != 0
                || this.lastStructureError != null
                || this.mismatchDiagnostic != null
                || this.formationFailure != null
                || !this.criticalChunks.isEmpty()
                || this.scan != null
                || this.structureAreaLoaded != structureAreaLoaded
                || this.formed != formed;
        this.machine = machine;
        this.foundMachine = machine;
        this.foundPattern = null;
        this.foundCompiledPattern = null;
        this.controllerFacing = null;
        this.matchedRollFacing = Direction.SOUTH;
        this.matchedStructureStage = 0;
        this.lastStructureError = null;
        this.mismatchDiagnostic = null;
        this.formationFailure = null;
        this.criticalChunks = Set.of();
        this.structureAreaLoaded = structureAreaLoaded;
        this.scan = null;
        this.scanMachine = null;
        this.scanCandidate = null;
        this.pendingInvalidation = false;
        this.formed = formed;
        this.dirty = false;
        if (changed) version++;
        return changed;
    }

    boolean structureAreaLoaded() {
        return structureAreaLoaded;
    }

    void setStructureAreaLoaded(boolean structureAreaLoaded) {
        this.structureAreaLoaded = structureAreaLoaded;
    }

    Set<ChunkPos> criticalChunks() {
        return criticalChunks;
    }

    void setCriticalChunks(Set<ChunkPos> criticalChunks) {
        this.criticalChunks = Set.copyOf(criticalChunks == null ? Set.of() : criticalChunks);
    }

    List<ChunkPos> criticalChunkList() {
        return criticalChunks.stream().toList();
    }

    UUID diagnosticPlayerOrNull() {
        return diagnosticPlayerId;
    }

    boolean hasScan() {
        return scan != null;
    }

    int scanCursor() {
        return scan == null ? -1 : scan.cursor();
    }

    int scanBatchSize() {
        return scan == null ? 0 : scan.batchSize();
    }

    int scanEntryCount() {
        return scan == null ? 0 : scan.entries().size();
    }

    long scanVersion() {
        return scan == null ? Long.MIN_VALUE : scan.structureVersion();
    }

    @Nullable Direction scanFacing() {
        return scan == null ? null : scan.frontFacing();
    }

    Direction scanRollFacing() {
        return scan == null ? Direction.SOUTH : scan.rollFacing();
    }

    int scanStage() {
        return scan == null ? 0 : scan.stageNumber();
    }

    @Nullable Object scanPattern() {
        return scan == null ? null : scan.patternIdentity();
    }

    StructureMatcher.ScanResult stepScan(ServerLevel level, BlockPos controllerPos) {
        if (scan == null) throw new IllegalStateException("Structure scan is not active");
        return scan.step(level, controllerPos);
    }

    void clearScan() {
        scan = null;
        scanMachine = null;
        scanCandidate = null;
        scanStartedTick = Long.MIN_VALUE;
    }

    void invalidateScan(StructureMatcher.InvalidationReason reason) {
        if (scan != null) scan.invalidate(reason);
    }

    /**
     * Mutable structure-check state is exchanged as one published value so callers cannot update fields independently.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record StructureWorkSnapshot(
            boolean dirty,
            int checkCounter,
            long nextCheckTick,
            @Nullable ScanView scan,
            @Nullable Machine scanMachine,
            @Nullable Object scanCandidate,
            @Nullable StructureMatcher.Mismatch previousMismatch,
            @Nullable Object previousMismatchPattern,
            boolean pendingInvalidation,
            long scanSteppedTick,
            long scanStartedTick,
            boolean checkActive,
            boolean diagnosticRequested,
            @Nullable UUID diagnosticPlayerId,
            @Nullable ResourceKey<Level> diagnosticDimension,
            @Nullable PortRequirementSpec.Failure formationFailure,
            @Nullable String mismatchDiagnostic,
            @Nullable Object lastStructureError) {

        /**
         * Immutable view of the active incremental scan.
         *
         * @author howxu <dev@howxu.cn>
         */
        public record ScanView(int cursor, int batchSize, int entryCount, long version,
                               @Nullable Direction facing, Direction rollFacing, int stage, @Nullable Object pattern) { }

        public StructureWorkSnapshot withDirty(boolean value) {
            return new StructureWorkSnapshot(value, checkCounter, nextCheckTick, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                    checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                    mismatchDiagnostic, lastStructureError);
        }

        public StructureWorkSnapshot withCheckCounter(int value) {
            return new StructureWorkSnapshot(dirty, value, nextCheckTick, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                    checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                    mismatchDiagnostic, lastStructureError);
        }

        public StructureWorkSnapshot withNextCheckTick(long value) {
            return new StructureWorkSnapshot(dirty, checkCounter, value, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                    checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                    mismatchDiagnostic, lastStructureError);
        }

        public StructureWorkSnapshot withPendingInvalidation(boolean value) {
            return new StructureWorkSnapshot(dirty, checkCounter, nextCheckTick, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, value, scanSteppedTick, scanStartedTick,
                    checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                    mismatchDiagnostic, lastStructureError);
        }

        public StructureWorkSnapshot withPreviousMismatch(@Nullable StructureMatcher.Mismatch mismatch,
                                                           @Nullable Object pattern) {
            return new StructureWorkSnapshot(dirty, checkCounter, nextCheckTick, scan, scanMachine, scanCandidate,
                    mismatch, pattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                    checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                    mismatchDiagnostic, lastStructureError);
        }

        public StructureWorkSnapshot withScanSteppedTick(long value) {
            return new StructureWorkSnapshot(dirty, checkCounter, nextCheckTick, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, pendingInvalidation, value, scanStartedTick,
                    checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                    mismatchDiagnostic, lastStructureError);
        }

        public StructureWorkSnapshot withCheckActive(boolean value) {
            return new StructureWorkSnapshot(dirty, checkCounter, nextCheckTick, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                    value, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                    mismatchDiagnostic, lastStructureError);
        }

        public StructureWorkSnapshot withDiagnostic(boolean requested, @Nullable UUID playerId,
                                                     @Nullable ResourceKey<Level> dimension) {
            return new StructureWorkSnapshot(dirty, checkCounter, nextCheckTick, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                    checkActive, requested, playerId, dimension, formationFailure, mismatchDiagnostic, lastStructureError);
        }

        public StructureWorkSnapshot withFormationFailure(@Nullable PortRequirementSpec.Failure value) {
            return new StructureWorkSnapshot(dirty, checkCounter, nextCheckTick, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                    checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, value,
                    mismatchDiagnostic, lastStructureError);
        }

        public StructureWorkSnapshot withMismatchDiagnostic(@Nullable String value) {
            return new StructureWorkSnapshot(dirty, checkCounter, nextCheckTick, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                    checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                    value, lastStructureError);
        }

        public StructureWorkSnapshot withLastStructureError(@Nullable Object value) {
            return new StructureWorkSnapshot(dirty, checkCounter, nextCheckTick, scan, scanMachine, scanCandidate,
                    previousMismatch, previousMismatchPattern, pendingInvalidation, scanSteppedTick, scanStartedTick,
                    checkActive, diagnosticRequested, diagnosticPlayerId, diagnosticDimension, formationFailure,
                    mismatchDiagnostic, value);
        }
    }
}
