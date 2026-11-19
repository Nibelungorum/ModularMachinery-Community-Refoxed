package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IOPortBlockTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void retained_provider_refuses_removed_or_wrong_kind_before_invoking_factory() {
        var owner = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        var level = LevelStub.createWithBlockEntities(List.of(owner));
        var output = ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState();
        assertThat(output.getMenuProvider(level, BlockPos.ZERO).createMenu(1, null, null)).isNull();

        var provider = owner.getBlockState().getMenuProvider(level, BlockPos.ZERO);
        owner.setRemoved();
        assertThat(provider.createMenu(1, null, null)).isNull();
    }
}
