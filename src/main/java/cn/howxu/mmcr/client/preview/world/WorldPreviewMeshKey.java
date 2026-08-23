package cn.howxu.mmcr.client.preview.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Identifies the world preview mesh requested by the client.
 *
 * @author howxu <dev@howxu.cn>
 */
public record WorldPreviewMeshKey(ResourceKey<Level> dimension, BlockPos controllerPos,
                                  int selectedLayer, BlockPos cameraCell) {
    public WorldPreviewMeshKey {
        Objects.requireNonNull(dimension, "dimension");
        controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        cameraCell = cameraCell == null ? null : cameraCell.immutable();
    }
}
