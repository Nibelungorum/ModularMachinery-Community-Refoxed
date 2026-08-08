package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.block.FactoryControllerBlock;
import cn.howxu.mmcr.internal.tile.FactoryControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactoryControllerRegistrationTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void factory_controller_has_block_item_and_block_entity_registered() {
        assertThat(ModBlocks.BLOCKS).containsKey("factory_controller");
        assertThat(ModBlocks.BLOCKS.get("factory_controller").get()).isInstanceOf(FactoryControllerBlock.class);
        assertThat(ModItems.ITEMS).containsKey("factory_controller");
        assertThat(ModBlockEntities.BES).containsKey("factory_controller");

        BlockEntity entity = ModBlockEntities.BES.get("factory_controller").get().create(
                BlockPos.ZERO,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());

        assertThat(entity).isInstanceOf(FactoryControllerBlockEntity.class);
        FactoryControllerBlockEntity factory = (FactoryControllerBlockEntity) entity;
        assertThat(factory.activeLaneCount()).isZero();
        factory.stopAll();
        factory.stopAll();
        assertThat(factory.activeLaneCount()).isZero();
    }
}
