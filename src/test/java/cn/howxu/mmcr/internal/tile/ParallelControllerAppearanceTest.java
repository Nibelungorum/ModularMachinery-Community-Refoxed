package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelControllerAppearanceTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void one_controller_owner_uses_formed_machine_casing() {
        ParallelControllerBlockEntity parallel = parallel(ParallelTier.NORMAL);

        parallel.linkControllerAppearance(new BlockPos(1, 64, 1), MMCR.id("block/steel_casing"));

        assertThat(parallel.appearanceBaseTexture()).isEqualTo(MMCR.id("block/steel_casing"));
    }

    @Test
    void multiple_controller_owners_use_basic_casing() {
        ParallelControllerBlockEntity parallel = parallel(ParallelTier.NORMAL);

        parallel.linkControllerAppearance(new BlockPos(1, 64, 1), MMCR.id("block/steel_casing"));
        parallel.linkControllerAppearance(new BlockPos(2, 64, 2), MMCR.id("block/brass_casing"));

        assertThat(parallel.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void removing_one_owner_restores_the_remaining_casing() {
        ParallelControllerBlockEntity parallel = parallel(ParallelTier.NORMAL);
        BlockPos steelOwner = new BlockPos(1, 64, 1);

        parallel.linkControllerAppearance(steelOwner, MMCR.id("block/steel_casing"));
        parallel.linkControllerAppearance(new BlockPos(2, 64, 2), MMCR.id("block/brass_casing"));
        parallel.unlinkControllerAppearance(steelOwner);

        assertThat(parallel.appearanceBaseTexture()).isEqualTo(MMCR.id("block/brass_casing"));
    }

    private static ParallelControllerBlockEntity parallel(ParallelTier tier) {
        return (ParallelControllerBlockEntity) ModBlockEntities.BES.get(tier.idSuffix()).get().create(
                BlockPos.ZERO,
                ModBlocks.BLOCKS.get(tier.idSuffix()).get().defaultBlockState());
    }
}
