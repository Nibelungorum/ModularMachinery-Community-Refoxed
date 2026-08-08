package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.EnergyHatchSize;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortKindsTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void resetRegistry() {
        PortKinds.clearForTesting();
    }

    @Test
    void native_kinds_present() {
        assertThat(PortKinds.all()).extracting(IOPortKind::id)
                .containsExactly(
                        "item_input_bus_tiny", "item_input_bus_small", "item_input_bus", "item_input_bus_reinforced", "item_input_bus_big", "item_input_bus_huge", "item_input_bus_ludicrous",
                        "item_output_bus_tiny", "item_output_bus_small", "item_output_bus", "item_output_bus_reinforced", "item_output_bus_big", "item_output_bus_huge", "item_output_bus_ludicrous",
                        "fluid_input_hatch_tiny", "fluid_input_hatch_small", "fluid_input_hatch", "fluid_input_hatch_reinforced", "fluid_input_hatch_big", "fluid_input_hatch_huge", "fluid_input_hatch_ludicrous", "fluid_input_hatch_vacuum",
                        "fluid_output_hatch_tiny", "fluid_output_hatch_small", "fluid_output_hatch", "fluid_output_hatch_reinforced", "fluid_output_hatch_big", "fluid_output_hatch_huge", "fluid_output_hatch_ludicrous", "fluid_output_hatch_vacuum",
                        "energy_input_hatch_tiny", "energy_input_hatch_small", "energy_input_hatch", "energy_input_hatch_reinforced", "energy_input_hatch_big", "energy_input_hatch_huge", "energy_input_hatch_ludicrous", "energy_input_hatch_ultimate",
                        "energy_output_hatch_tiny", "energy_output_hatch_small", "energy_output_hatch", "energy_output_hatch_reinforced", "energy_output_hatch_big", "energy_output_hatch_huge", "energy_output_hatch_ludicrous", "energy_output_hatch_ultimate");
    }

    @Test
    void native_kinds_have_io_type() {
        assertThat(PortKinds.all()).extracting(IOPortKind::ioType)
                .containsExactly(
                        IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT,
                        IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT,
                        IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT,
                        IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT,
                        IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT, IOType.INPUT,
                        IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT, IOType.OUTPUT);
    }

    @Test
    void normal_aliases_keep_public_constants() {
        assertThat(PortKinds.ITEM_INPUT.id()).isEqualTo("item_input_bus");
        assertThat(PortKinds.ITEM_INPUT.itemBusSize()).contains(ItemBusSize.NORMAL);
        assertThat(PortKinds.FLUID_INPUT.id()).isEqualTo("fluid_input_hatch");
        assertThat(PortKinds.FLUID_INPUT.fluidHatchSize()).contains(FluidHatchSize.NORMAL);
        assertThat(PortKinds.ENERGY_INPUT.id()).isEqualTo("energy_input_hatch");
        assertThat(PortKinds.ENERGY_INPUT.energyHatchSize()).contains(EnergyHatchSize.NORMAL);
    }

    @Test
    void register_appends_new_kind() {
        BlockEntityType.BlockEntitySupplier<cn.howxu.mmcr.internal.tile.IOPortBlockEntity> dummyFactory =
                (BlockPos p, BlockState s) -> null;
        IOPortKind extra = new PortKinds.Simple("test_extra", IOType.INPUT, dummyFactory);
        PortKinds.register(extra);
        assertThat(PortKinds.all()).hasSize(47);
        assertThat(PortKinds.all()).extracting(IOPortKind::id).contains("test_extra");
    }
}
