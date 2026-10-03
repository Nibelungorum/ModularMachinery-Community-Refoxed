package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.ars_nouveau.SourcePortKind;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInterfaceKind;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.AsyncOutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.OutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.StockingInterfaceKind;
import cn.howxu.mmcr.compat.appliedflux.loaded.kind.FluxEnergyInputKind;
import cn.howxu.mmcr.compat.appliedflux.loaded.kind.FluxEnergyOutputKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedOutputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedPatternInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedStockingInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.OversizeInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.OversizeOutputInterfaceKind;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicOverlayTexturesTest {

    @Test
    void fluxNetworksInterfacesUseNativePointAndPlugCoreLayers() {
        assertThat(DynamicOverlayTextures.portOverlayTexture(FluxNetworkInterfaceKind.INPUT))
                .containsExactly(ResourceLocation.parse("fluxnetworks:block/flux_point_on"),
                        ResourceLocation.parse("fluxnetworks:block/flux_point_colour"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(FluxNetworkInterfaceKind.OUTPUT))
                .containsExactly(ResourceLocation.parse("fluxnetworks:block/flux_plug_on"),
                        ResourceLocation.parse("fluxnetworks:block/flux_plug_colour"));
    }

    @Test
    void sourceInterfacesShareTheSourceBaseAndRetainTheirDirectionLayer() {
        assertThat(DynamicOverlayTextures.portOverlayTexture(new SourcePortKind(ArsSourceIds.INPUT, IOType.INPUT)))
                .containsExactly(MMCR.id("block/overlay/base/ars_nouveau/source"),
                         MMCR.id("block/overlay/direction/input"), MMCR.id("block/overlay/tier/big"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(new SourcePortKind(ArsSourceIds.OUTPUT, IOType.OUTPUT)))
                .containsExactly(MMCR.id("block/overlay/base/ars_nouveau/source"),
                         MMCR.id("block/overlay/direction/output"), MMCR.id("block/overlay/tier/big"));
    }

    @Test
    void ae2InputInterfaceUsesTheDedicatedInterfaceOverlay() {
        assertThat(DynamicOverlayTextures.portOverlayTexture(InputInterfaceKind.INSTANCE))
                .isEqualTo(ImmutableList.of(MMCR.id("block/appliedenergistics2/ae2_input")));
    }

    @Test
    void ae2PatternInterfaceUsesItsDedicatedOverlay() {
        assertThat(DynamicOverlayTextures.portOverlayTexture(PatternInterfaceKind.INSTANCE))
                .isEqualTo(ImmutableList.of(MMCR.id("block/appliedenergistics2/ae2_pattern_interface")));
    }

    @Test
    void allAe2InterfacesUseCentralDedicatedOverlays() {
        assertThat(DynamicOverlayTextures.portOverlayTexture(StockingInterfaceKind.INSTANCE))
                .containsExactly(MMCR.id("block/appliedenergistics2/ae2_stocking_input"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(OutputInterfaceKind.INSTANCE))
                .containsExactly(MMCR.id("block/appliedenergistics2/ae2_output"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(AsyncOutputInterfaceKind.INSTANCE))
                .containsExactly(MMCR.id("block/appliedenergistics2/ae2_async_output"));
    }

    @Test
    void extendedAeInterfacesUseCentralDedicatedOverlays() {
        assertThat(DynamicOverlayTextures.portOverlayTexture(ExtendedInputInterfaceKind.INSTANCE))
                .containsExactly(MMCR.id("block/extendedae/eae_me_extended_input_interface"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(ExtendedStockingInputInterfaceKind.INSTANCE))
                .containsExactly(MMCR.id("block/extendedae/eae_me_extended_stocking_input_interface"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(ExtendedOutputInterfaceKind.INSTANCE))
                .containsExactly(MMCR.id("block/extendedae/eae_me_extended_output_interface"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(OversizeInputInterfaceKind.INSTANCE))
                .containsExactly(MMCR.id("block/extendedae/eae_me_oversize_input_interface"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(OversizeOutputInterfaceKind.INSTANCE))
                .containsExactly(MMCR.id("block/extendedae/eae_me_oversize_output_interface"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(ExtendedPatternInterfaceKind.INSTANCE))
                .containsExactly(MMCR.id("block/extendedae/eae_me_extended_pattern_interface"));
    }

    @Test
    void appFluxInputInterfaceUsesTheAppFluxInputOverlay() {
        assertThat(DynamicOverlayTextures.portOverlayTexture(FluxEnergyInputKind.INSTANCE))
                .isEqualTo(ImmutableList.of(MMCR.id("block/appliedflux/appflux_input")));
    }

    @Test
    void appFluxOutputInterfaceUsesTheAppFluxOutputOverlay() {
        assertThat(DynamicOverlayTextures.portOverlayTexture(FluxEnergyOutputKind.INSTANCE))
                .isEqualTo(ImmutableList.of(MMCR.id("block/appliedflux/appflux_output")));
    }

    private static boolean usesDedicatedOverlay(IOPortKind kind) {
        return kind.extendedItemBusSize().isPresent()
                || kind.extendedFluidHatchSize().isPresent()
                || kind.extendedEnergyHatchSize().isPresent()
                || kind.combinedPortSize().isPresent()
                || kind.extendedCombinedPortSize().isPresent();
    }

    private static String expectedOverlayPath(IOPortKind kind) {
        String direction = kind.ioType() == IOType.INPUT ? "input" : "output";
        if (kind.extendedItemBusSize().isPresent()) {
            return "block/new/overlay_extended_" + direction + "bus_"
                    + kind.extendedItemBusSize().orElseThrow().id();
        }
        if (kind.extendedFluidHatchSize().isPresent()) {
            return "block/new/overlay_extended_fluid" + direction + "hatch_"
                    + kind.extendedFluidHatchSize().orElseThrow().id();
        }
        if (kind.extendedEnergyHatchSize().isPresent()) {
            return "block/new/overlay_extended_energy" + direction + "hatch_"
                    + kind.extendedEnergyHatchSize().orElseThrow().id();
        }
        if (kind.combinedPortSize().isPresent()) {
            return "block/new/overlay_combined_" + direction + "_"
                    + kind.combinedPortSize().orElseThrow().id();
        }
        return "block/new/overlay_extended_combined_" + direction + "_"
                + kind.extendedCombinedPortSize().orElseThrow().id();
    }
}
