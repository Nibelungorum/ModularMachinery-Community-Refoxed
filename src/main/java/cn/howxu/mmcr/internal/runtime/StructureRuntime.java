package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Structure state boundary used by the controller runtime.
 *
 * <p>The controller remains the compatibility bridge for the existing incremental scan algorithm;
 * this class owns the published structure state and routes invalidation requests to that algorithm.</p>
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureRuntime {
    private final MachineControllerBlockEntity controller;
    private StructureSnapshot state = StructureSnapshot.empty();

    public StructureRuntime() {
        this(null);
    }

    public StructureRuntime(MachineControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void requestCheck() {
        if (controller == null) {
            state = withDirty(state);
            return;
        }
        controller.requestImmediateStructureCheckFromRuntime();
        refreshFromController();
    }

    public void onBlockChanged(BlockPos position) {
        if (controller == null) {
            state = withDirty(state);
            return;
        }
        controller.onStructureBlockChangedFromRuntime(position);
        refreshFromController();
    }

    public void tick(ServerLevel level, BlockPos controllerPos) {
        if (controller != null) controller.tickStructureRuntime(level, controllerPos);
        refreshFromController();
    }

    public boolean formed() {
        refreshFromController();
        return state.formed();
    }

    public long version() {
        refreshFromController();
        return state.version();
    }

    public StructureSnapshot snapshot() {
        refreshFromController();
        return state;
    }

    public void accept(StructureSnapshot snapshot) {
        state = snapshot == null ? StructureSnapshot.empty() : snapshot;
    }

    private void refreshFromController() {
        if (controller != null) state = controller.structureSnapshotFromRuntime();
    }

    private static StructureSnapshot withDirty(StructureSnapshot snapshot) {
        return new StructureSnapshot(snapshot.machine(), snapshot.pattern(), snapshot.compiledPattern(), snapshot.facing(),
                snapshot.rollFacing(), snapshot.matchedStage(), snapshot.formed(), snapshot.version(),
                snapshot.lastStructureError(), true, snapshot.structureAreaLoaded(), snapshot.criticalChunks());
    }
}
