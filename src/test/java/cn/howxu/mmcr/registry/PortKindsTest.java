package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.port.IOPortKind;
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
                        "item_input_bus", "item_output_bus",
                        "fluid_input_hatch", "fluid_output_hatch",
                        "energy_input_hatch", "energy_output_hatch");
    }

    @Test
    void native_kinds_have_io_type() {
        assertThat(PortKinds.all()).extracting(IOPortKind::ioType)
                .containsExactly(
                        IOType.INPUT, IOType.OUTPUT,
                        IOType.INPUT, IOType.OUTPUT,
                        IOType.INPUT, IOType.OUTPUT);
    }

    @Test
    void register_appends_new_kind() {
        BlockEntityType.BlockEntitySupplier<cn.howxu.mmcr.internal.tile.IOPortBlockEntity> dummyFactory =
                (BlockPos p, BlockState s) -> null;
        IOPortKind extra = new PortKinds.Simple("test_extra", IOType.INPUT, dummyFactory);
        PortKinds.register(extra);
        assertThat(PortKinds.all()).hasSize(7);
        assertThat(PortKinds.all()).extracting(IOPortKind::id).contains("test_extra");
    }
}