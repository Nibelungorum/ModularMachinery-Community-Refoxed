package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.resources.Identifier;

/**
 * Resolves the shared overlay texture names used by block and item models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayTextures {
    private DynamicOverlayTextures() {
    }

    public static Identifier portOverlayTexture(IOPortKind kind) {
        return kind == null ? DynamicOverlayBakedModel.defaultPortOverlayTexture()
                : portOverlayTextureForName(kind.id());
    }

    public static Identifier portOverlayTextureForName(String blockName) {
        if (blockName == null) {
            return DynamicOverlayBakedModel.defaultPortOverlayTexture();
        }
        if (blockName.startsWith("item_input_bus")) return overlayTexture(blockName, "item_input_bus", "overlay_inputbus");
        if (blockName.startsWith("item_output_bus")) return overlayTexture(blockName, "item_output_bus", "overlay_outputbus");
        if (blockName.startsWith("fluid_input_hatch")) return overlayTexture(blockName, "fluid_input_hatch", "overlay_fluidinputhatch");
        if (blockName.startsWith("fluid_output_hatch")) return overlayTexture(blockName, "fluid_output_hatch", "overlay_fluidoutputhatch");
        if (blockName.startsWith("energy_input_hatch")) return overlayTexture(blockName, "energy_input_hatch", "overlay_energyinputhatch");
        if (blockName.startsWith("energy_output_hatch")) return overlayTexture(blockName, "energy_output_hatch", "overlay_energyoutputhatch");
        if (blockName.startsWith("parallel_controller_")) return parallelControllerOverlayTexture(blockName);
        if (blockName.equals("factory_controller")) return MMCR.id("block/overlay_factory_controller");
        return DynamicOverlayBakedModel.defaultPortOverlayTexture();
    }

    public static Identifier controllerOverlayTexture(Identifier machineId) {
        MachineControllerSpec spec = ControllerSpecCache.specFor(machineId);
        return spec.frontTexture();
    }

    private static Identifier overlayTexture(String blockName, String baseName, String textureBase) {
        String tier = blockName.equals(baseName) ? "normal" : blockName.substring(baseName.length() + 1);
        return MMCR.id("block/" + textureBase + "_" + tier);
    }

    private static Identifier parallelControllerOverlayTexture(String blockName) {
        if (ParallelTier.X4.idSuffix().equals(blockName)) return MMCR.id("block/overlay_parallel_controller_normal");
        if (ParallelTier.X16.idSuffix().equals(blockName)) return MMCR.id("block/overlay_parallel_controller_reinforced");
        if (ParallelTier.X64.idSuffix().equals(blockName)) return MMCR.id("block/overlay_parallel_controller_super");
        if (ParallelTier.X256.idSuffix().equals(blockName)) return MMCR.id("block/overlay_parallel_controller_elite");
        if (ParallelTier.X512.idSuffix().equals(blockName)) return MMCR.id("block/overlay_parallel_controller_ultimate");
        if (ParallelTier.MAX.idSuffix().equals(blockName)) return MMCR.id("block/overlay_parallel_controller_ultimate");
        return DynamicOverlayBakedModel.defaultPortOverlayTexture();
    }
}
