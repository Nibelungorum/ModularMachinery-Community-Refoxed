package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.SourcePortKind;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworkOverlay;
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
import net.minecraft.resources.ResourceLocation;

/**
 * Resolves the shared overlay texture names used by block and item models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayTextures {
    private DynamicOverlayTextures() {
    }

    public static ImmutableList<ResourceLocation> portOverlayTexture(IOPortKind kind) {
        if (kind == null) return overlay(DynamicOverlayBakedModel.defaultPortOverlayTexture());
        if (isStressPort(kind)) {
            return overlay(MMCR.id("block/create/" + kind.ioType().getSerializedName()));
        }
        if (FluxNetworkOverlay.isPort(kind)) {
            return FluxNetworkOverlay.textures(kind);
        }
        // for ae2
        ResourceLocation compatibilityOverlay = compatibilityOverlay(kind.id());
        if (compatibilityOverlay != null) return overlay(compatibilityOverlay);
        // mekanism
        if (kind instanceof PortKinds.ChemicalKind chemical) {
            return chemicalOverlay(chemical);
        }
        if (kind instanceof PortKinds.HeatKind) {
            return heatOverlay(kind.ioType());
        }
        if (kind instanceof SourcePortKind) {
            return ImmutableList.of(baseOverlay("ars_nouveau/source"), directionOverlay(kind.ioType()),tierOverlay("big"));
        }
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

    private static ResourceLocation compatibilityOverlay(String kindId) {
        return switch (kindId) {
            case "ae2_me_input_interface" -> MMCR.id("block/appliedenergistics2/ae2_input");
            case "ae2_me_stocking_input_interface" -> MMCR.id("block/appliedenergistics2/ae2_stocking_input");
            case "ae2_me_output_interface" -> MMCR.id("block/appliedenergistics2/ae2_output");
            case "ae2_me_async_output_interface" -> MMCR.id("block/appliedenergistics2/ae2_async_output");
            case "ae2_me_pattern_interface" -> MMCR.id("block/appliedenergistics2/ae2_pattern_interface");
            case "eae_me_extended_input_interface" -> MMCR.id("block/extendedae/eae_me_extended_input_interface");
            case "eae_me_extended_stocking_input_interface" ->
                    MMCR.id("block/extendedae/eae_me_extended_stocking_input_interface");
            case "eae_me_extended_output_interface" -> MMCR.id("block/extendedae/eae_me_extended_output_interface");
            case "eae_me_oversize_input_interface" -> MMCR.id("block/extendedae/eae_me_oversize_input_interface");
            case "eae_me_oversize_stocking_input_interface" ->
                    MMCR.id("block/extendedae/eae_me_oversize_stocking_input_interface");
            case "eae_me_oversize_output_interface" -> MMCR.id("block/extendedae/eae_me_oversize_output_interface");
            case "eae_me_extended_pattern_interface" -> MMCR.id("block/extendedae/eae_me_extended_pattern_interface");
            case "eaep_me_mirror_pattern_interface" -> MMCR.id("block/extendedae_plus/mirror_pattern_interface");
            case "appflux_me_flux_input_interface" -> MMCR.id("block/appliedflux/appflux_input");
            case "appflux_me_flux_output_interface" -> MMCR.id("block/appliedflux/appflux_output");
            default -> null;
        };
    }

    static boolean isStressPort(IOPortKind kind) {
        return kind != null && ("create_stress_input_interface".equals(kind.id())
                || "create_stress_output_interface".equals(kind.id()));
    }

    private static ImmutableList<ResourceLocation> overlay(ResourceLocation texture) {
        return ImmutableList.of(texture);
    }

    private static ResourceLocation directionOverlay(IOType direction) {
        return MMCR.id("block/overlay/direction/" + (direction == IOType.INPUT ? "input" : "output"));
    }

    private static ResourceLocation tierOverlay(String tierId) {
        return MMCR.id("block/overlay/tier/" + tierId);
    }

    private static ResourceLocation typeOverlay(String typeId) {
        return MMCR.id("block/overlay/type/" + typeId);
    }

    private static ResourceLocation baseOverlay(String typeId) {
        return MMCR.id("block/overlay/base/" + typeId);
    }

    private static ImmutableList<ResourceLocation> chemicalOverlay(PortKinds.ChemicalKind kind) {
        if (kind.radioactive()) {
            // return MMCR.id("block/mekanism/overlay_radioactive_chemical_" + (kind.ioType() == IOType.INPUT ? "input" : "output"));
            return ImmutableList.of(
                    baseOverlay("mekanism/raditional_chemical"),
                    directionOverlay(kind.ioType()),
                    // typeOverlay()
                    tierOverlay("ludicrous")
            );
        }

        return ImmutableList.of(
                baseOverlay("mekanism/chemical"),
                directionOverlay(kind.ioType()),
                // typeOverlay()
                tierOverlay("extended/" + kind.id().substring(kind.id().lastIndexOf('_') + 1))
        );

        // String tier = ;
        // String direction = kind.ioType() == IOType.INPUT ? "chemicalinputhatch" : "chemicaloutputhatch";
        // return MMCR.id("block/mekanism/overlay_" + direction + "_" + tier);
    }

    private static ImmutableList<ResourceLocation> heatOverlay(IOType ioType) {
        // return MMCR.id("block/mekanism/overlay_heat_" + (ioType == IOType.INPUT ? "input" : "output"));
        return ImmutableList.of(
                baseOverlay("mekanism/heat"),
                directionOverlay(ioType),
                // typeOverlay()
                tierOverlay("huge")
        );
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
