package cn.howxu.mmcr.internal.block;

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
    void generated_port_ids_use_direct_container_translation_key() {
        assertThat(IOPortBlock.titleFor("item_input_bus_ludicrous").getString()).isEqualTo("container.mmcr.item_input_bus_ludicrous");
        assertThat(IOPortBlock.titleFor("fluid_output_hatch_vacuum").getString()).isEqualTo("container.mmcr.fluid_output_hatch_vacuum");
    }
}
