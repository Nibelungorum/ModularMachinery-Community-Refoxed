package cn.howxu.mmcr.client.model;

import net.minecraft.world.level.block.Block;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Explicit runtime model definition for a dynamically rendered block.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RuntimeBlockModelDefinition(
        Block block,
        String blockName,
        DynamicOverlayBakedModel.Kind modelKind,
        RuntimeMachineModelRegistry.RuntimeBlockStateDefinition blockStateDefinition,
        DynamicOverlayItemModel.Description itemDescription) {
    public RuntimeBlockModelDefinition {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(blockName, "blockName");
        Objects.requireNonNull(modelKind, "modelKind");
        Objects.requireNonNull(blockStateDefinition, "blockStateDefinition");
        Objects.requireNonNull(itemDescription, "itemDescription");
    }

    @Override
    public DynamicOverlayItemModel.Description itemDescription() {
        return new DynamicOverlayItemModel.Description(
                itemDescription.kind(), itemDescription.machineId(), itemDescription.portKind(),
                itemDescription.baseModel(), itemDescription.baseTexture(), itemDescription.overlayTexture(),
                EnumSet.copyOf(itemDescription.overlayFaces()));
    }
}
