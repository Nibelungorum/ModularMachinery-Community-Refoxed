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
import cn.howxu.mmcr.registry.PortKinds;
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
        // for ae2
        Identifier compatibilityOverlay = compatibilityOverlay(kind.id());
        if (compatibilityOverlay != null) return overlay(compatibilityOverlay);
        // mekanism
        if (kind instanceof PortKinds.ChemicalKind chemical) {
            return overlay(chemicalOverlay(chemical));
        }
        if (kind instanceof PortKinds.HeatKind) {
            return overlay(heatOverlay(kind.ioType()));
        }
        // vanilla
        if (kind.itemBusSize().isPresent()) {
            // return overlay(tieredPortOverlay(kind.ioType(), "overlay_inputbus", "overlay_outputbus", kind.itemBusSize().map(ItemBusSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("item"),directionOverlay(kind.ioType()),typeOverlay("item"),tierOverlay(kind.itemBusSize().map(ItemBusSize::id).orElseThrow()));
        }
        if (kind.extendedItemBusSize().isPresent()) {
            return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_extended_inputbus", "new/overlay_extended_outputbus",
                    kind.extendedItemBusSize().map(ExtendedItemBusSize::id).orElseThrow()));
        }
        if (kind.fluidHatchSize().isPresent()) {
            //return overlay(tieredPortOverlay(kind.ioType(), "overlay_fluidinputhatch", "overlay_fluidoutputhatch", kind.fluidHatchSize().map(FluidHatchSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("fluid"),directionOverlay(kind.ioType()),typeOverlay("fluid"),tierOverlay(kind.fluidHatchSize().map(FluidHatchSize::id).orElseThrow()));
        }
        if (kind.extendedFluidHatchSize().isPresent()) {
            return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_extended_fluidinputhatch", "new/overlay_extended_fluidoutputhatch",
                    kind.extendedFluidHatchSize().map(ExtendedFluidHatchSize::id).orElseThrow()));
        }
        if (kind.energyHatchSize().isPresent()) {
            // return overlay(tieredPortOverlay(kind.ioType(), "overlay_energyinputhatch", "overlay_energyoutputhatch", kind.energyHatchSize().map(EnergyHatchSize::id).orElseThrow()));
            return ImmutableList.of(baseOverlay("energy"),directionOverlay(kind.ioType()),typeOverlay("energy"),tierOverlay(kind.energyHatchSize().map(EnergyHatchSize::id).orElseThrow()));
        }
        if (kind.extendedEnergyHatchSize().isPresent()) {
            return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_extended_energyinputhatch", "new/overlay_extended_energyoutputhatch",
                    kind.extendedEnergyHatchSize().map(ExtendedEnergyHatchSize::id).orElseThrow()));
        }
        if (kind.combinedPortSize().isPresent()) {
            return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_combined_input", "new/overlay_combined_output",
                    kind.combinedPortSize().map(CombinedPortSize::id).orElseThrow()));
        }
        if (kind.extendedCombinedPortSize().isPresent()) {
            return overlay(tieredPortOverlay(kind.ioType(), "new/overlay_extended_combined_input", "new/overlay_extended_combined_output",
                    kind.extendedCombinedPortSize().map(ExtendedCombinedPortSize::id).orElseThrow()));
        }
        return overlay(DynamicOverlayBakedModel.defaultPortOverlayTexture());
    }

    public static Identifier controllerOverlayTexture(Identifier machineId) {
        MachineControllerSpec spec = ControllerSpecCache.specFor(machineId);
        return spec.frontTexture();
    }

    private static Identifier tieredPortOverlay(IOType ioType, String input, String output, String tier) {
        return MMCR.id("block/" + (ioType == IOType.INPUT ? input : output) + "_" + tier);
    }

    private static Identifier compatibilityOverlay(String kindId) {
        return switch (kindId) {
            case "ae2_me_input_interface" -> MMCR.id("block/appliedenergistics2/ae2_input");
            case "ae2_me_stocking_input_interface" -> MMCR.id("block/appliedenergistics2/ae2_stocking_input");
            case "ae2_me_output_interface" -> MMCR.id("block/appliedenergistics2/ae2_output");
            case "ae2_me_async_output_interface" -> MMCR.id("block/appliedenergistics2/ae2_async_output");
            case "ae2_me_pattern_interface" -> MMCR.id("block/appliedenergistics2/ae2_pattern_interface");
            case "eae_me_extended_input_interface" -> MMCR.id("block/extendedae/eae_me_extended_input_interface");
            case "eae_me_extended_stocking_input_interface" -> MMCR.id("block/extendedae/eae_me_extended_stocking_input_interface");
            case "eae_me_extended_output_interface" -> MMCR.id("block/extendedae/eae_me_extended_output_interface");
            case "eae_me_oversize_input_interface" -> MMCR.id("block/extendedae/eae_me_oversize_input_interface");
            case "eae_me_oversize_stocking_input_interface" -> MMCR.id("block/extendedae/eae_me_oversize_stocking_input_interface");
            case "eae_me_oversize_output_interface" -> MMCR.id("block/extendedae/eae_me_oversize_output_interface");
            case "eae_me_extended_pattern_interface" -> MMCR.id("block/extendedae/eae_me_extended_pattern_interface");
            case "appflux_me_flux_input_interface" -> MMCR.id("block/appliedflux/appflux_input");
            case "appflux_me_flux_output_interface" -> MMCR.id("block/appliedflux/appflux_output");
            default -> null;
        };
    }

    private static ImmutableList<Identifier> overlay(Identifier texture) {
        return ImmutableList.of(texture);
    }

    // cause we have overlay now, so the overlay is from this DynamicOverlayTextures declare
    // For AE2 we directly use AE2 resource, so it's better create a bridge
    private static Identifier chemicalOverlay(PortKinds.ChemicalKind kind) {
        if (kind.radioactive()) {
            return MMCR.id("block/mekanism/overlay_radioactive_chemical_" + (kind.ioType() == IOType.INPUT ? "input" : "output"));
        }
        String tier = kind.id().substring(kind.id().lastIndexOf('_') + 1);
        String direction = kind.ioType() == IOType.INPUT ? "chemicalinputhatch" : "chemicaloutputhatch";
        return MMCR.id("block/mekanism/overlay_" + direction + "_" + tier);
    }

    private static Identifier heatOverlay(IOType ioType) {
        return MMCR.id("block/mekanism/overlay_heat_" + (ioType == IOType.INPUT ? "input" : "output"));
    }

    // new textures and multiple overlays helper from here
    private static Identifier directionOverlay(IOType direction){
        return MMCR.id("block/overlay/direction/" + (direction == IOType.INPUT ? "input" : "output"));
    }

    private static Identifier tierOverlay(String tierId){
        return MMCR.id("block/overlay/tier/" + tierId);
    }

    private static Identifier typeOverlay(String typeId){
        return MMCR.id("block/overlay/type/" + typeId);
    }

    private static Identifier baseOverlay(String typeId){
        return MMCR.id("block/overlay/base/" + typeId);
    }
}
