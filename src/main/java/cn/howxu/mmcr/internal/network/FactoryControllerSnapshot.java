package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Authoritative runtime data displayed by an open factory controller menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactoryControllerSnapshot(BlockPos controllerPos, boolean formed, boolean redstonePaused,
                                        int activeThreadCount, int threadCount, int currentParallelism,
                                        int maxParallelism, List<FactoryRecipeScheduler.ThreadSnapshot> threads) {
    public FactoryControllerSnapshot {
        controllerPos = controllerPos == null ? BlockPos.ZERO : controllerPos.immutable();
        threads = List.copyOf(threads == null ? List.of() : threads);
    }

    public static FactoryControllerSnapshot empty(BlockPos controllerPos) {
        return new FactoryControllerSnapshot(controllerPos, false, false, 0, 0, 0, 1, List.of());
    }
}
