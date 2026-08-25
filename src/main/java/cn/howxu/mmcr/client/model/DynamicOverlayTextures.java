package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.internal.port.CombinedPortSize;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.util.IOType;
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
        if (kind == null) return DynamicOverlayBakedModel.defaultPortOverlayTexture();
        if (kind.itemBusSize().isPresent()) {
            return tieredPortOverlay(kind.ioType(), "overlay_inputbus", "overlay_outputbus",
                    kind.itemBusSize().map(ItemBusSize::id).orElseThrow());
        }
        if (kind.extendedItemBusSize().isPresent()) {
            return tieredPortOverlay(kind.ioType(), "overlay_inputbus", "overlay_outputbus",
                    ItemBusSize.LUDICROUS.id());
        }
        if (kind.fluidHatchSize().isPresent()) {
            return tieredPortOverlay(kind.ioType(), "overlay_fluidinputhatch", "overlay_fluidoutputhatch",
                    kind.fluidHatchSize().map(FluidHatchSize::id).orElseThrow());
        }
        if (kind.extendedFluidHatchSize().isPresent()) {
            return tieredPortOverlay(kind.ioType(), "overlay_fluidinputhatch", "overlay_fluidoutputhatch",
                    FluidHatchSize.VACUUM.id());
        }
        if (kind.energyHatchSize().isPresent()) {
            return tieredPortOverlay(kind.ioType(), "overlay_energyinputhatch", "overlay_energyoutputhatch",
                    kind.energyHatchSize().map(EnergyHatchSize::id).orElseThrow());
        }
        if (kind.extendedEnergyHatchSize().isPresent()) {
            return tieredPortOverlay(kind.ioType(), "overlay_energyinputhatch", "overlay_energyoutputhatch",
                    EnergyHatchSize.ULTIMATE.id());
        }
        if (kind.combinedPortSize().isPresent()) {
            return tieredPortOverlay(kind.ioType(), "overlay_inputbus", "overlay_outputbus",
                    combinedItemTier(kind.combinedPortSize().orElseThrow()));
        }
        if (kind.extendedCombinedPortSize().isPresent()) {
            return tieredPortOverlay(kind.ioType(), "overlay_inputbus", "overlay_outputbus",
                    ItemBusSize.LUDICROUS.id());
        }
        return DynamicOverlayBakedModel.defaultPortOverlayTexture();
    }

    public static Identifier controllerOverlayTexture(Identifier machineId) {
        MachineControllerSpec spec = ControllerSpecCache.specFor(machineId);
        return spec.frontTexture();
    }

    private static Identifier tieredPortOverlay(IOType ioType, String input, String output, String tier) {
        return MMCR.id("block/" + (ioType == IOType.INPUT ? input : output) + "_" + tier);
    }

    private static String combinedItemTier(CombinedPortSize size) {
        return switch (size) {
            case BASIC -> ItemBusSize.NORMAL.id();
            case ADVANCED -> ItemBusSize.REINFORCED.id();
            case REINFORCED -> ItemBusSize.BIG.id();
            case ULTIMATE -> ItemBusSize.HUGE.id();
        };
    }
}
