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

class MMCRPortKindsTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void resetRegistry() {
        MMCRPortKinds.clearForTesting();
    }

    @Test
    void native_kinds_present() {
        assertThat(MMCRPortKinds.all()).extracting(IOPortKind::id)
                .contains("item", "fluid", "energy");
    }

    @Test
    void register_appends_new_kind() {
        BlockEntityType.BlockEntitySupplier<cn.howxu.mmcr.internal.tile.IOPortBlockEntity> dummyFactory =
                (BlockPos p, BlockState s) -> null;
        IOPortKind extra = new MMCRPortKinds.Simple("test_extra", dummyFactory);
        MMCRPortKinds.register(extra);
        assertThat(MMCRPortKinds.all()).hasSize(4);
        assertThat(MMCRPortKinds.all()).extracting(IOPortKind::id).contains("test_extra");
    }
}