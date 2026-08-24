package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Authoritative runtime data displayed by an open factory controller menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactoryControllerSnapshot(BlockPos controllerPos, boolean formed, boolean redstonePaused,
                                         int activeThreadCount, int threadCount, int currentParallelism,
                                         int maxParallelism, String machineName, int parallelSlots,
                                         String lastFailureUnloc, List<FactoryRuntime.ThreadSnapshot> threads) {
    public FactoryControllerSnapshot {
        controllerPos = controllerPos == null ? BlockPos.ZERO : controllerPos.immutable();
        machineName = machineName == null ? "" : machineName;
        lastFailureUnloc = lastFailureUnloc == null ? "" : lastFailureUnloc;
        threads = List.copyOf(threads == null ? List.of() : threads);
    }

    public FactoryControllerSnapshot(BlockPos controllerPos, boolean formed, boolean redstonePaused,
                                     int activeThreadCount, int threadCount, int currentParallelism,
                                     int maxParallelism, List<FactoryRuntime.ThreadSnapshot> threads) {
        this(controllerPos, formed, redstonePaused, activeThreadCount, threadCount, currentParallelism,
                maxParallelism, "", 0, "", threads);
    }

    public FactoryControllerSnapshot(BlockPos controllerPos, boolean formed, boolean redstonePaused,
                                     int activeThreadCount, int threadCount, int currentParallelism,
                                     int maxParallelism, String machineName, int parallelSlots,
                                     List<FactoryRuntime.ThreadSnapshot> threads) {
        this(controllerPos, formed, redstonePaused, activeThreadCount, threadCount, currentParallelism,
                maxParallelism, machineName, parallelSlots, "", threads);
    }

    public static FactoryControllerSnapshot empty(BlockPos controllerPos) {
        return new FactoryControllerSnapshot(controllerPos, false, false, 0, 1, 0, 1, "", 0, "",
                List.of(FactoryRuntime.ThreadSnapshot.idleBase()));
    }
}
