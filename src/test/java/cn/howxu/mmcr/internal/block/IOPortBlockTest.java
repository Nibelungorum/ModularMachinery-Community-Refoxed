package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.PortKinds;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IOPortBlockTest {

    @Test
    void generated_port_ids_route_to_menu_by_category_prefix() {
        assertThat(IOPortBlock.menuKindFor("item_input_bus_ludicrous")).isEqualTo(IOPortBlock.PortMenuKind.ITEM);
        assertThat(IOPortBlock.menuKindFor("fluid_output_hatch_vacuum")).isEqualTo(IOPortBlock.PortMenuKind.FLUID);
        assertThat(IOPortBlock.menuKindFor("energy_input_hatch_ultimate")).isEqualTo(IOPortBlock.PortMenuKind.ENERGY);
    }

    @Test
    void normal_port_ids_route_to_existing_menus() {
        assertThat(IOPortBlock.menuKindFor("item_input_bus")).isEqualTo(IOPortBlock.PortMenuKind.ITEM);
        assertThat(IOPortBlock.menuKindFor("item_output_bus")).isEqualTo(IOPortBlock.PortMenuKind.ITEM);
        assertThat(IOPortBlock.menuKindFor("fluid_input_hatch")).isEqualTo(IOPortBlock.PortMenuKind.FLUID);
        assertThat(IOPortBlock.menuKindFor("fluid_output_hatch")).isEqualTo(IOPortBlock.PortMenuKind.FLUID);
        assertThat(IOPortBlock.menuKindFor("energy_input_hatch")).isEqualTo(IOPortBlock.PortMenuKind.ENERGY);
        assertThat(IOPortBlock.menuKindFor("energy_output_hatch")).isEqualTo(IOPortBlock.PortMenuKind.ENERGY);
    }

    @Test
    void extended_port_ids_route_to_existing_menus() {
        assertThat(IOPortBlock.menuKindFor("extended_item_input_bus_basic"))
                .isEqualTo(IOPortBlock.PortMenuKind.EXTENDED_ITEM);
        assertThat(IOPortBlock.menuKindFor("extended_fluid_output_hatch_ultimate"))
                .isEqualTo(IOPortBlock.PortMenuKind.EXTENDED_FLUID);
        assertThat(IOPortBlock.menuKindFor("extended_energy_input_hatch_reinforced"))
                .isEqualTo(IOPortBlock.PortMenuKind.EXTENDED_ENERGY);
    }

    @Test
    void every_registered_port_kind_has_an_explicit_menu_category() {
        for (IOPortKind kind : PortKinds.all()) {
            IOPortBlock.PortMenuKind expected = expectedCategory(kind);
            assertThat(expected).isNotEqualTo(IOPortBlock.PortMenuKind.NONE);
            assertThat(IOPortBlock.menuKindFor(kind.id()))
                    .as(kind.id())
                    .isEqualTo(expected);
        }
    }

    private static IOPortBlock.PortMenuKind expectedCategory(IOPortKind kind) {
        if (kind.extendedItemBusSize().isPresent()) return IOPortBlock.PortMenuKind.EXTENDED_ITEM;
        if (kind.extendedFluidHatchSize().isPresent()) return IOPortBlock.PortMenuKind.EXTENDED_FLUID;
        if (kind.extendedEnergyHatchSize().isPresent()) return IOPortBlock.PortMenuKind.EXTENDED_ENERGY;
        if (kind.combinedPortSize().isPresent()) return IOPortBlock.PortMenuKind.COMBINED;
        if (kind.extendedCombinedPortSize().isPresent()) return IOPortBlock.PortMenuKind.EXTENDED_COMBINED;
        if (kind.itemBusSize().isPresent()) return IOPortBlock.PortMenuKind.ITEM;
        if (kind.fluidHatchSize().isPresent()) return IOPortBlock.PortMenuKind.FLUID;
        if (kind.energyHatchSize().isPresent()) return IOPortBlock.PortMenuKind.ENERGY;
        return IOPortBlock.PortMenuKind.NONE;
    }

    @Test
    void near_prefix_port_ids_do_not_route_to_menus() {
        assertThat(IOPortBlock.menuKindFor("item_input_busbar")).isEqualTo(IOPortBlock.PortMenuKind.NONE);
        assertThat(IOPortBlock.menuKindFor("fluid_output_hatchery")).isEqualTo(IOPortBlock.PortMenuKind.NONE);
        assertThat(IOPortBlock.menuKindFor("energy_input_hatchling")).isEqualTo(IOPortBlock.PortMenuKind.NONE);
    }

}
