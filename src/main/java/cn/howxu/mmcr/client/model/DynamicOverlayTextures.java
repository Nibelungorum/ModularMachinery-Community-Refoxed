package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.internal.port.CombinedPortSize;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedCombinedPortSize;
import cn.howxu.mmcr.internal.port.ExtendedEnergyHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedFluidHatchSize;
import cn.howxu.mmcr.internal.port.ExtendedItemBusSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.util.IOType;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.Identifier;

/**
 * Resolves the shared overlay texture names used by block and item models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayTextures {
    private DynamicOverlayTextures() {
    }

    public static ImmutableList<Identifier> portOverlayTexture(IOPortKind kind) {
        if (kind == null) return overlay(DynamicOverlayBakedModel.defaultPortOverlayTexture());
        var declared = kind.overlayTextures();
        if (!declared.isEmpty()) return ImmutableList.copyOf(declared);
        // vanilla
        if (kind.itemBusSize().isPresent()) {
            // return overlay(tieredPortOverlay(kind.ioType(), "overlay_inputbus", "overlay_outputbus", kind.itemBusSize().map(ItemBusSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("item"), directionOverlay(kind.ioType()), typeOverlay("item"), tierOverlay(kind.itemBusSize().map(ItemBusSize::id).orElseThrow()));
        }
        if (kind.extendedItemBusSize().isPresent()) {
            //return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_extended_inputbus", "new/overlay_extended_outputbus", kind.extendedItemBusSize().map(ExtendedItemBusSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("item"), directionOverlay(kind.ioType()), typeOverlay("item"), tierOverlay("extended/" + kind.extendedItemBusSize().map(ExtendedItemBusSize::id).orElseThrow()));
        }
        if (kind.fluidHatchSize().isPresent()) {
            //return overlay(tieredPortOverlay(kind.ioType(), "overlay_fluidinputhatch", "overlay_fluidoutputhatch", kind.fluidHatchSize().map(FluidHatchSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("fluid"), directionOverlay(kind.ioType()), typeOverlay("fluid"), tierOverlay(kind.fluidHatchSize().map(FluidHatchSize::id).orElseThrow()));
        }
        if (kind.extendedFluidHatchSize().isPresent()) {
            //return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_extended_fluidinputhatch", "new/overlay_extended_fluidoutputhatch", kind.extendedFluidHatchSize().map(ExtendedFluidHatchSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("fluid"), directionOverlay(kind.ioType()), typeOverlay("fluid"), tierOverlay("extended/" + kind.extendedFluidHatchSize().map(ExtendedFluidHatchSize::id).orElseThrow()));
        }
        if (kind.energyHatchSize().isPresent()) {
            // return overlay(tieredPortOverlay(kind.ioType(), "overlay_energyinputhatch", "overlay_energyoutputhatch", kind.energyHatchSize().map(EnergyHatchSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("energy"), directionOverlay(kind.ioType()), typeOverlay("energy"), tierOverlay(kind.energyHatchSize().map(EnergyHatchSize::id).orElseThrow()));
        }
        if (kind.extendedEnergyHatchSize().isPresent()) {
            // return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_extended_energyinputhatch", "new/overlay_extended_energyoutputhatch", kind.extendedEnergyHatchSize().map(ExtendedEnergyHatchSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("energy"), directionOverlay(kind.ioType()), typeOverlay("energy"), tierOverlay("extended/" + kind.extendedEnergyHatchSize().map(ExtendedEnergyHatchSize::id).orElseThrow()));
        }
        if (kind.combinedPortSize().isPresent()) {
            // return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_combined_input", "new/overlay_combined_output", kind.combinedPortSize().map(CombinedPortSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("item_fluid"), directionOverlay(kind.ioType()), typeOverlay("item_fluid"), tierOverlay(mappingTier(kind.combinedPortSize().map(CombinedPortSize::id).orElseThrow())));
        }
        if (kind.extendedCombinedPortSize().isPresent()) {
            // return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_extended_combined_input", "new/overlay_extended_combined_output", kind.extendedCombinedPortSize().map(ExtendedCombinedPortSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("item_fluid"), directionOverlay(kind.ioType()), typeOverlay("item_fluid"), tierOverlay("extended/" + kind.extendedCombinedPortSize().map(ExtendedCombinedPortSize::id).orElseThrow()));
        }
        return overlay(DynamicOverlayBakedModel.defaultPortOverlayTexture());
    }

    private static ImmutableList<Identifier> overlay(Identifier texture) {
        return ImmutableList.of(texture);
    }

    // new textures and multiple overlays helper from here
    private static Identifier directionOverlay(IOType direction) {
        return MMCR.id("block/overlay/direction/" + (direction == IOType.INPUT ? "input" : "output"));
    }

    private static Identifier tierOverlay(String tierId) {
        return MMCR.id("block/overlay/tier/" + tierId);
    }

    private static Identifier typeOverlay(String typeId) {
        return MMCR.id("block/overlay/type/" + typeId);
    }

    private static Identifier baseOverlay(String typeId) {
        return MMCR.id("block/overlay/base/" + typeId);
    }

    private static String mappingTier(String to_map){
        return switch (to_map) {
            case "basic" -> "big";
            case "advanced" -> "huge";
            case "reinforced" -> "ludicrous";
            default -> "vacuum"; // include ultimate
        };
    }
}
