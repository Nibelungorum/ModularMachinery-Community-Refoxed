package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        PreviewLevel level = level(schema, PreviewVisibility.ALL);

        assertThat(level.getBlockState(BlockPos.ZERO)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
        assertThat(level.getFluidState(fluidPos).isEmpty()).isFalse();
        level.updateVisibility(PreviewVisibility.singleLayer(0));
        assertThat(level.getBlockState(fluidPos)).isEqualTo(Blocks.AIR.defaultBlockState());
        assertThat(level.getBlockState(new BlockPos(8, 8, 8))).isEqualTo(Blocks.AIR.defaultBlockState());
    }

    @Test
    void level_creates_default_block_entity_once_and_close_releases_it() {
        BlockPos pos = BlockPos.ZERO;
        PreviewLevel level = level(
                schema(Map.of(pos, Blocks.CHEST.defaultBlockState())), PreviewVisibility.ALL);

        BlockEntity first = level.getBlockEntity(pos);
        assertThat(first).isNotNull();
        assertThat(level.getBlockEntity(pos)).isSameAs(first);
        level.close();
        assertThat(level.getBlockEntity(pos)).isNull();
    }

    @Test
    void clip_hits_visible_schema_block_and_ignores_hidden_layer() {
        PreviewLevel level = level(
                schema(Map.of(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState())), PreviewVisibility.ALL);
        ClipContext context = new ClipContext(new Vec3(-1.0, 0.5, 0.5), new Vec3(2.0, 0.5, 0.5),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty());

        assertThat(level.clip(context).getType()).isEqualTo(HitResult.Type.BLOCK);
        level.updateVisibility(PreviewVisibility.singleLayer(1));
        assertThat(level.clip(context).getType()).isNotEqualTo(HitResult.Type.BLOCK);
    }

    @Test
    void level_uses_dynamic_visibility_supplier_and_update_visibility() {
        AtomicReference<PreviewVisibility> supplied = new AtomicReference<>(PreviewVisibility.ALL);
        PreviewLevel level = PreviewLevel.create(schema(Map.of(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState())), supplied::get);

        supplied.set(PreviewVisibility.singleLayer(1));
        assertThat(level.getBlockState(BlockPos.ZERO)).isEqualTo(Blocks.AIR.defaultBlockState());
        level.updateVisibility(PreviewVisibility.ALL);
        assertThat(level.getBlockState(BlockPos.ZERO)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Test
    void background_thread_cannot_create_block_entity_or_preview_chunk() throws Exception {
        PreviewLevel level = level(schema(Map.of(BlockPos.ZERO, Blocks.CHEST.defaultBlockState())), PreviewVisibility.ALL);
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThatThrownBy(() -> executor.submit(() -> level.getBlockEntity(BlockPos.ZERO)).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> executor.submit(() -> level.getChunkSource().getChunk(0, 0, true)).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void background_thread_cannot_clear_preview_block_entity_cache() throws Exception {
        PreviewLevel level = level(schema(Map.of(BlockPos.ZERO, Blocks.CHEST.defaultBlockState())), PreviewVisibility.ALL);
        assertThat(level.getBlockEntity(BlockPos.ZERO)).isNotNull();

        try (var executor = Executors.newSingleThreadExecutor()) {
            assertThatThrownBy(() -> executor.submit(level::close).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
        assertThat(level.getBlockEntity(BlockPos.ZERO)).isNotNull();
    }

    @Test
    void preview_chunks_cover_neighbor_ring_and_reject_positions_outside_their_chunk() {
        PreviewLevel level = level(schema(Map.of(BlockPos.ZERO, Blocks.CHEST.defaultBlockState())), PreviewVisibility.ALL);

        assertThat(level.getChunkSource().getChunk(1, 1, true)).isNotNull();
        assertThat(level.getChunkSource().getChunk(2, 0, true)).isNull();
        LevelChunk chunk = level.getChunkSource().getChunk(0, 0, true);
        assertThat(chunk.getBlockState(new BlockPos(16, 0, 0))).isEqualTo(Blocks.AIR.defaultBlockState());
        assertThat(chunk.getBlockEntity(new BlockPos(16, 0, 0))).isNull();
    }

    private static PreviewLevel level(StructurePreviewSchema schema, PreviewVisibility visibility) {
        Supplier<PreviewVisibility> supplier = () -> visibility;
        return PreviewLevel.create(schema, supplier);
    }

    private static StructurePreviewSchema schema(Map<BlockPos, BlockState> states) {
        return new StructurePreviewSchema(MMCR.id("preview_level_test"), states, Map.of());
    }
}
