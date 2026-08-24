package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Structure state boundary used by the controller runtime.
 *
 * <p>This class owns the published structure state and the runtime boundary for the existing
 * incremental scan algorithm. The controller is retained only as the Minecraft-facing bridge.</p>
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureRuntime {
    private final MachineControllerBlockEntity controller;
    private StructureSnapshot state = StructureSnapshot.empty();
    private boolean authoritative;

    public StructureRuntime() {
        this(null);
    }

    public StructureRuntime(MachineControllerBlockEntity controller) {
        this.controller = controller;
        this.state = controller == null ? StructureSnapshot.empty() : controller.captureStructureSnapshotForRuntime();
    }

    public void requestCheck() {
        if (controller == null) {
            state = withDirty(state);
            return;
        }
        controller.requestImmediateStructureCheckFromRuntime();
        publish(controller.captureStructureSnapshotForRuntime());
    }

    public void onBlockChanged(BlockPos position) {
        if (controller == null) {
            state = withDirty(state);
            return;
        }
        controller.onStructureBlockChangedFromRuntime(position);
        publish(controller.captureStructureSnapshotForRuntime());
    }

    public void tick(ServerLevel level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
            throw new IllegalArgumentException("Structure runtime tick requires a level and controller position");
        }
        if (controller == null) throw new IllegalStateException("Structure runtime tick requires a controller");
        controller.tickStructureRuntime(level, controllerPos);
        publish(controller.captureStructureSnapshotForRuntime());
    }

    public void check() {
        if (controller != null) controller.runStructureCheckPassFromRuntime();
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
        state = snapshot == null ? StructureSnapshot.empty() : snapshot;
        authoritative = true;
    }

    public void publish(StructureSnapshot snapshot) {
        state = snapshot == null ? StructureSnapshot.empty() : snapshot;
        authoritative = true;
    }

    public boolean hasAuthoritativeState() {
        return authoritative;
    }

    private static StructureSnapshot withDirty(StructureSnapshot snapshot) {
        return new StructureSnapshot(snapshot.machine(), snapshot.pattern(), snapshot.compiledPattern(), snapshot.facing(),
                snapshot.rollFacing(), snapshot.matchedStage(), snapshot.formed(), snapshot.version(),
                snapshot.lastStructureError(), snapshot.structureMismatchDiagnostic(), true,
                snapshot.structureAreaLoaded(), snapshot.criticalChunks());
    }
}
