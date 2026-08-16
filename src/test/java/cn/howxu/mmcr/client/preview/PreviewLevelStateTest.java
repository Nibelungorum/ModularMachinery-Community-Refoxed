package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies virtual preview-level state isolation and default block entities.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewLevelStateTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void level_returns_schema_state_fluid_and_air_for_hidden_or_missing_positions() {
        BlockPos fluidPos = new BlockPos(0, 1, 0);
        StructurePreviewSchema schema = schema(Map.of(
                BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                fluidPos, Blocks.WATER.defaultBlockState()));
        PreviewLevel level = PreviewLevel.createForTest(schema, PreviewVisibility.ALL);

        assertThat(level.getBlockState(BlockPos.ZERO)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
        assertThat(level.getFluidState(fluidPos).isEmpty()).isFalse();
        level.updateVisibility(PreviewVisibility.singleLayer(0));
        assertThat(level.getBlockState(fluidPos)).isEqualTo(Blocks.AIR.defaultBlockState());
        assertThat(level.getBlockState(new BlockPos(8, 8, 8))).isEqualTo(Blocks.AIR.defaultBlockState());
    }

    @Test
    void level_creates_default_block_entity_once_and_close_releases_it() {
        BlockPos pos = BlockPos.ZERO;
        PreviewLevel level = PreviewLevel.createForTest(
                schema(Map.of(pos, Blocks.CHEST.defaultBlockState())), PreviewVisibility.ALL);

        BlockEntity first = level.getBlockEntity(pos);
        assertThat(first).isNotNull();
        assertThat(level.getBlockEntity(pos)).isSameAs(first);
        level.close();
        assertThat(level.getBlockEntity(pos)).isNull();
    }

    @Test
    void clip_hits_visible_schema_block_and_ignores_hidden_layer() {
        PreviewLevel level = PreviewLevel.createForTest(
                schema(Map.of(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState())), PreviewVisibility.ALL);
        ClipContext context = new ClipContext(new Vec3(-1.0, 0.5, 0.5), new Vec3(2.0, 0.5, 0.5),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty());

        assertThat(level.clip(context).getType()).isEqualTo(HitResult.Type.BLOCK);
        level.updateVisibility(PreviewVisibility.singleLayer(1));
        assertThat(level.clip(context).getType()).isNotEqualTo(HitResult.Type.BLOCK);
    }

    private static StructurePreviewSchema schema(Map<BlockPos, BlockState> states) {
        return new StructurePreviewSchema(MMCR.id("preview_level_test"), states, Map.of());
    }
}
