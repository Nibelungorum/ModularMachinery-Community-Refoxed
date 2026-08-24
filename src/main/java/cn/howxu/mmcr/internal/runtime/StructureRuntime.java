package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Structure state boundary used by the controller runtime.
 *
 * <p>This class owns the published structure state and the runtime boundary for the existing
 * incremental scan algorithm. The controller is retained only as the Minecraft-facing bridge.</p>
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureRuntime {
    /**
     * State transferred across the Minecraft-facing scan bridge.
     */
    public record ExecutionState(
            StructureSnapshot snapshot,
            @Nullable StructureMatcher.ScanState scan,
            @Nullable Machine scanMachine,
            @Nullable Object scanCandidate,
            @Nullable StructureMatcher.Mismatch previousMismatch,
            @Nullable Object previousMismatchPattern,
            boolean pendingInvalidation,
            long scanSteppedTick,
            long scanStartedTick,
            int checkCounter,
            long nextCheckTick,
            @Nullable PortRequirementSpec.Failure formationFailure,
            boolean diagnosticRequested,
            @Nullable UUID diagnosticPlayerId,
            @Nullable ResourceKey<Level> diagnosticDimension) {

        public ExecutionState {
            snapshot = snapshot == null ? StructureSnapshot.empty() : snapshot;
            if (snapshot.lastFormationFailure() == null && formationFailure != null) {
                snapshot = new StructureSnapshot(snapshot.machine(), snapshot.pattern(), snapshot.compiledPattern(),
                        snapshot.facing(), snapshot.rollFacing(), snapshot.matchedStage(), snapshot.formed(), snapshot.version(),
                        snapshot.lastStructureError(), snapshot.structureMismatchDiagnostic(), formationFailure,
                        snapshot.dirty(), snapshot.structureAreaLoaded(), snapshot.criticalChunks());
            }
            formationFailure = snapshot.lastFormationFailure();
        }

        private static ExecutionState empty(StructureSnapshot snapshot) {
            return new ExecutionState(snapshot, null, null, null, null, null,
                    false, Long.MIN_VALUE, Long.MIN_VALUE, 0, -1L, null, false, null, null);
        }
    }

    private final MachineControllerBlockEntity controller;
    private StructureSnapshot state = StructureSnapshot.empty();
    private ExecutionState executionState = ExecutionState.empty(state);
    private boolean authoritative;

    public StructureRuntime() {
        this(null);
    }

    public StructureRuntime(MachineControllerBlockEntity controller) {
        this.controller = controller;
        this.executionState = controller == null
                ? ExecutionState.empty(state) : controller.captureStructureExecutionStateForRuntime();
        this.state = executionState.snapshot();
    }

    public void requestCheck() {
        if (controller == null) {
            publish(withDirty(executionState));
            return;
        }
        controller.requestImmediateStructureCheckFromRuntime();
        publish(controller.captureStructureExecutionStateForRuntime());
    }

    public void onBlockChanged(BlockPos position) {
        if (controller == null) {
            publish(withDirty(executionState));
            return;
        }
        controller.onStructureBlockChangedFromRuntime(position);
        publish(controller.captureStructureExecutionStateForRuntime());
    }

    public void tick(ServerLevel level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            throw new IllegalArgumentException("Structure runtime tick requires a level and controller position");
        }
        if (controller == null) throw new IllegalStateException("Structure runtime tick requires a controller");
        controller.tickStructureRuntime(level, controllerPos);
        publish(controller.captureStructureExecutionStateForRuntime());
    }

    public void check() {
        if (controller == null) throw new IllegalStateException("Structure runtime check requires a controller");
        controller.restoreStructureStateFromRuntime(executionState);
        controller.runStructureCheckPassFromRuntime();
        publish(controller.captureStructureExecutionStateForRuntime());
    }

    public boolean formed() {
        return state.formed();
    }

    public long version() {
        return state.version();
    }

    public StructureSnapshot snapshot() {
        return state;
    }

    public void accept(StructureSnapshot snapshot) {
        accept(ExecutionState.empty(snapshot));
    }

    public void publish(StructureSnapshot snapshot) {
        publish(ExecutionState.empty(snapshot));
    }

    public void publish(ExecutionState nextState) {
        executionState = nextState == null ? ExecutionState.empty(StructureSnapshot.empty()) : nextState;
        state = executionState.snapshot();
        authoritative = true;
    }

    private void accept(ExecutionState nextState) {
        publish(nextState);
    }

    public boolean hasAuthoritativeState() {
        return authoritative;
    }

    private static ExecutionState withDirty(ExecutionState executionState) {
        StructureSnapshot snapshot = executionState.snapshot();
        StructureSnapshot dirty = new StructureSnapshot(snapshot.machine(), snapshot.pattern(), snapshot.compiledPattern(),
                snapshot.facing(), snapshot.rollFacing(), snapshot.matchedStage(), snapshot.formed(), snapshot.version(),
                snapshot.lastStructureError(), snapshot.structureMismatchDiagnostic(), snapshot.lastFormationFailure(), true,
                snapshot.structureAreaLoaded(), snapshot.criticalChunks());
        return new ExecutionState(dirty, executionState.scan(), executionState.scanMachine(), executionState.scanCandidate(),
                executionState.previousMismatch(), executionState.previousMismatchPattern(), executionState.pendingInvalidation(),
                executionState.scanSteppedTick(), executionState.scanStartedTick(), executionState.checkCounter(),
                executionState.nextCheckTick(), executionState.formationFailure(), executionState.diagnosticRequested(),
                executionState.diagnosticPlayerId(), executionState.diagnosticDimension());
    }
}
