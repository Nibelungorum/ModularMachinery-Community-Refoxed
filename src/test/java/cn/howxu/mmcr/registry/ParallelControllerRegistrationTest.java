package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelControllerRegistrationTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void every_parallel_tier_has_block_item_and_block_entity_registered() {
        for (ParallelTier tier : ParallelTier.values()) {
            String id = tier.idSuffix();

            assertThat(ModBlocks.BLOCKS).containsKey(id);
            assertThat(ModBlocks.BLOCKS.get(id).get()).isInstanceOf(ParallelControllerBlock.class);
            assertThat(((ParallelControllerBlock) ModBlocks.BLOCKS.get(id).get()).tier()).isSameAs(tier);
            assertThat(ModItems.ITEMS).containsKey(id);
            assertThat(ModBlockEntities.BES).containsKey(id);
            assertThat(ModBlockEntities.BES.get(id).get().create(net.minecraft.core.BlockPos.ZERO,
                    ModBlocks.BLOCKS.get(id).get().defaultBlockState()))
                    .isInstanceOf(ParallelControllerBlockEntity.class)
                    .extracting(entity -> ((ParallelControllerBlockEntity) entity).tier())
                    .isEqualTo(tier);
        }
    }
}
