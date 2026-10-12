package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the render region used by the preview mesh compiler.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewSceneMeshCompilerTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void worker_count_uses_single_thread_for_small_previews() {
        assertThat(PreviewSceneMeshCompiler.workerCount(0)).isEqualTo(1);
        assertThat(PreviewSceneMeshCompiler.workerCount(10_000)).isEqualTo(1);
        assertThat(PreviewSceneMeshCompiler.workerCount(80_000)).isEqualTo(1);
    }

    @Test
    void worker_count_uses_two_threads_above_large_preview_limit() {
        assertThat(PreviewSceneMeshCompiler.workerCount(80_001)).isEqualTo(2);
    }

    @Test
    void partitions_cover_entries_once() {
        List<PreviewSceneMeshCompiler.Partition> partitions =
                PreviewSceneMeshCompiler.partitions(10, 3);

        assertThat(partitions).extracting(PreviewSceneMeshCompiler.Partition::startInclusive)
                .containsExactly(0, 4, 7);
        assertThat(partitions).extracting(PreviewSceneMeshCompiler.Partition::endExclusive)
                .containsExactly(4, 7, 10);
    }

    @Test
    void async_compilation_returns_before_worker_starts() {
        AtomicReference<Runnable> pendingWorker = new AtomicReference<>();
        Executor executor = pendingWorker::set;
        PreviewSceneMeshCompiler.CompilationInput input = new PreviewSceneMeshCompiler.CompilationInput(
                List.of(), PreviewVisibility.ALL, null, null);

        var future = PreviewSceneMeshCompiler.compileAsync(input, null, new AtomicBoolean(), executor);

        assertThat(future).isNotDone();
        assertThat(pendingWorker.get()).isNotNull();
    }

    @Test
    void synchronous_cpu_result_is_available_without_a_worker_or_render_callback_turn() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(new Vector3f(), 4);
        var input = new PreviewSceneMeshCompiler.CompilationInput(List.of(), PreviewVisibility.ALL, null, null);
        try (var compiled = PreviewSceneMeshCompiler.compileSynchronously(input,
                PreviewSceneCamera.from(camera, 1, 1), new AtomicBoolean())) {
            assertThat(compiled).isNotNull();
            assertThat(compiled.parts()).hasSize(1);
            assertThat(compiled.blockEntities()).isEmpty();
        }
    }

    @Test
    void synchronous_cpu_cancellation_fails_before_any_result_can_be_published() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(new Vector3f(), 4);
        var input = new PreviewSceneMeshCompiler.CompilationInput(List.of(), PreviewVisibility.ALL, null, null);
        assertThatThrownBy(() -> PreviewSceneMeshCompiler.compileSynchronously(input,
                PreviewSceneCamera.from(camera, 1, 1), new AtomicBoolean(true)))
                .isInstanceOf(CompletionException.class);
    }

    @Test
    void preview_region_uses_full_brightness_without_querying_the_light_engine() {
        StructurePreviewSchema schema = new StructurePreviewSchema(MMCR.id("preview_region_test"),
                Map.of(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState()), Map.of());
        PreviewLevel level = PreviewLevel.create(schema, () -> PreviewVisibility.ALL);

        var region = PreviewSceneMeshCompiler.previewRegion(level, schema, PreviewVisibility.ALL);

        assertThat(region.getBrightness(LightLayer.BLOCK, BlockPos.ZERO)).isEqualTo(15);
        assertThat(region.getBrightness(LightLayer.SKY, BlockPos.ZERO)).isEqualTo(15);
    }

}
