package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.block.SmartInterfaceBlock;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmartInterfaceRegistrationTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void smart_interface_is_registered_independently_from_ports() {
        assertThat(ModBlocks.SMART_INTERFACE.get()).isInstanceOf(SmartInterfaceBlock.class);
        assertThat(ModBlockEntities.SMART_INTERFACE.get().create(BlockPos.ZERO,
                ModBlocks.SMART_INTERFACE.get().defaultBlockState())).isInstanceOf(SmartInterfaceBlockEntity.class);
        assertThat(ModItems.ITEMS).containsKey("smart_interface");
        assertThat(PortKinds.all()).noneMatch(kind -> kind.id().equals("smart_interface"));
    }
}
