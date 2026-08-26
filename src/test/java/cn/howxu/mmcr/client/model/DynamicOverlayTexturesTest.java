package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicOverlayTexturesTest {

    @Test
    void dedicated_overlay_port_kinds_resolve_to_their_category_and_tier_texture() {
        PortKinds.all().stream()
                .filter(DynamicOverlayTexturesTest::usesDedicatedOverlay)
                .forEach(kind -> assertThat(DynamicOverlayTextures.portOverlayTexture(kind))
                        .as(kind.id())
                        .isEqualTo(MMCR.id(expectedOverlayPath(kind))));
    }

    @Test
    void ordinary_port_kinds_keep_existing_overlay_names() {
        assertThat(DynamicOverlayTextures.portOverlayTexture(PortKinds.ITEM_INPUT))
                .isEqualTo(MMCR.id("block/overlay_inputbus_normal"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(PortKinds.FLUID_OUTPUT))
                .isEqualTo(MMCR.id("block/overlay_fluidoutputhatch_normal"));
        assertThat(DynamicOverlayTextures.portOverlayTexture(PortKinds.ENERGY_INPUT))
                .isEqualTo(MMCR.id("block/overlay_energyinputhatch_normal"));
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
