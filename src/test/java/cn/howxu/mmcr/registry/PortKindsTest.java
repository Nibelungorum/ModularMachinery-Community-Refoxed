package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.test.TestBootstrap;
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
                .contains("item", "fluid", "energy");
    }

    @Test
    void register_appends_new_kind() {
        BlockEntityType.BlockEntitySupplier<cn.howxu.mmcr.internal.tile.IOPortBlockEntity> dummyFactory =
                (BlockPos p, BlockState s) -> null;
        IOPortKind extra = new PortKinds.Simple("test_extra", dummyFactory);
        PortKinds.register(extra);
        assertThat(PortKinds.all()).hasSize(4);
        assertThat(PortKinds.all()).extracting(IOPortKind::id).contains("test_extra");
    }
}