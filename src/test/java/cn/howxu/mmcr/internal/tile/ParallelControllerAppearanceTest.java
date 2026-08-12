package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.ParallelTier;
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
    void parallel_controller_uses_unique_owner_appearance() {
        ParallelControllerBlockEntity parallel = parallelController();
        var texture = MMCR.id("block/steel_casing");

        parallel.linkControllerAppearance(new BlockPos(0, 64, 0), texture);

        assertThat(parallel.appearanceBaseTexture()).isEqualTo(texture);
    }

    @Test
    void parallel_controller_falls_back_to_basic_casing_when_owner_appearances_conflict() {
        ParallelControllerBlockEntity parallel = parallelController();
        parallel.linkControllerAppearance(new BlockPos(0, 64, 0), MMCR.id("block/steel_casing"));
        parallel.linkControllerAppearance(new BlockPos(4, 64, 0), MMCR.id("block/titanium_casing"));

        assertThat(parallel.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void removing_conflicting_owner_restores_remaining_owner_appearance() {
        ParallelControllerBlockEntity parallel = parallelController();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(4, 64, 0);
        var secondTexture = MMCR.id("block/titanium_casing");
        parallel.linkControllerAppearance(first, MMCR.id("block/steel_casing"));
        parallel.linkControllerAppearance(second, secondTexture);

        parallel.unlinkControllerAppearance(first);

        assertThat(parallel.appearanceBaseTexture()).isEqualTo(secondTexture);
    }

    private static ParallelControllerBlockEntity parallelController() {
        return new ParallelControllerBlockEntity(ParallelTier.NORMAL, BlockPos.ZERO,
                ModBlocks.BLOCKS.get("parallel_controller_normal").get().defaultBlockState());
    }
}
