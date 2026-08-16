package cn.howxu.mmcr.client.preview;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.BlockHitResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies adapter-owned preview frame and callback lifecycle contracts.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewRendererTest {
    @Test
    void depth_readback_requires_copy_source_usage() throws IOException {
        String source = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/client/preview/StructurePreviewRenderer.java"));

        assertThat(source.indexOf("(depthTexture.usage() & GpuTexture.USAGE_COPY_SRC) == 0"))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(source.indexOf("copyTextureToBuffer(depthTexture"));
    }

    @Test
    void frame_converts_absolute_gui_mouse_through_each_coordinate_space() {
        PreviewFrameViewport frame = new PreviewFrameViewport(
                new PreviewViewport(110, 70, 100, 50),
                new PreviewViewport.FramebufferViewport(400, 300, 250, 125),
                200, 100, 2);

        assertThat(frame.containsAbsoluteGui(160, 95)).isTrue();
        assertThat(frame.pipLocalLogical(160, 95)).isEqualTo(new PreviewFrameViewport.Pixel(50, 25));
        assertThat(frame.framebufferPixel(160, 95)).isEqualTo(new PreviewFrameViewport.Pixel(525, 360));
        assertThat(frame.depthTexturePixel(160, 95, 333, 111)).isEqualTo(new PreviewFrameViewport.Pixel(166, 53));
        assertThat(frame.containsAbsoluteGui(109, 95)).isFalse();
    }

    @Test
    void frame_uses_pip_allocation_not_framebuffer_size_for_depth_texels() {
        PreviewFrameViewport frame = new PreviewFrameViewport(
                new PreviewViewport(17, 23, 3, 2),
                new PreviewViewport.FramebufferViewport(91, 41, 9, 6),
                9, 6, 3);

        assertThat(frame.pipAllocationWidth()).isEqualTo(9);
        assertThat(frame.pipAllocationHeight()).isEqualTo(6);
        assertThat(frame.depthTexturePixel(19, 24)).isEqualTo(new PreviewFrameViewport.Pixel(6, 0));
    }

    @Test
    void owner_rejects_late_callback_after_close_and_queues_one_release() {
        PreviewOwnerLifecycle lifecycle = new PreviewOwnerLifecycle();
        AtomicInteger callbackCalls = new AtomicInteger();
        AtomicInteger releaseCalls = new AtomicInteger();
        CountDownLatch callbackQueued = new CountDownLatch(1);
        long token = lifecycle.nextReadback(2, 3, 100L);

        Thread lateCallback = new Thread(() -> {
            lifecycle.enqueueCallback(token, callbackCalls::incrementAndGet);
            callbackQueued.countDown();
        });
        lateCallback.start();
        try {
            callbackQueued.await();
            lateCallback.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        assertThat(lifecycle.queueRelease(releaseCalls::incrementAndGet)).isTrue();
        assertThat(lifecycle.queueRelease(releaseCalls::incrementAndGet)).isFalse();
        lifecycle.drainOwnerQueue();

        assertThat(callbackCalls.get()).isZero();
        assertThat(releaseCalls.get()).isEqualTo(1);
    }

    @Test
    void owner_coalesces_depth_reads_until_mouse_moves_or_fifty_milliseconds_pass() {
        PreviewOwnerLifecycle lifecycle = new PreviewOwnerLifecycle();
        lifecycle.nextReadback(2, 3, 100L);

        assertThat(lifecycle.shouldRead(2, 3, 149L)).isFalse();
        assertThat(lifecycle.shouldRead(3, 3, 149L)).isTrue();
        assertThat(lifecycle.shouldRead(2, 3, 150L)).isTrue();
    }

    @Test
    void failed_bridge_request_releases_current_readback_for_retry() {
        PreviewOwnerLifecycle lifecycle = new PreviewOwnerLifecycle();
        Object buffer = new Object();
        long token = lifecycle.nextReadback(2, 3, 100L);

        lifecycle.beginReadback(token, buffer);
        lifecycle.failReadback(token, buffer, buffer);

        assertThat(lifecycle.readbackInFlight()).isFalse();
        assertThat(lifecycle.shouldRead(3, 3, 100L)).isTrue();
    }

    @Test
    void hit_result_has_a_nullable_block_hit_result_covariant_return_type() throws NoSuchMethodException {
        assertThat(StructurePreviewRenderer.class.getDeclaredMethod("hitResult").getReturnType())
                .isEqualTo(BlockHitResult.class);
    }

    @Test
    void render_scope_restores_every_state_when_scene_throws() {
        PreviewRenderStateScope.TestState state = new PreviewRenderStateScope.TestState("old-color", "old-depth", "old-projection", 4);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                PreviewRenderStateScope.run(state, "pip-color", "pip-depth", "pip-projection", () -> {
                    state.modelViewDepth++;
                    throw new IllegalStateException("scene failure");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(state.color).isEqualTo("old-color");
        assertThat(state.depth).isEqualTo("old-depth");
        assertThat(state.projection).isEqualTo("old-projection");
        assertThat(state.modelViewDepth).isEqualTo(4);
    }

    @Test
    void outlines_leave_preview_model_view_transform_to_the_render_pipeline() throws IOException {
        String source = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/client/preview/scene/PreviewSceneRenderer.java"));

        int outline = source.indexOf("private static void drawOutline");

        assertThat(outline).isGreaterThanOrEqualTo(0);
        assertThat(source).doesNotContain("poseStack.last().pose().set(com.mojang.blaze3d.systems.RenderSystem.getModelViewStack())");
        assertThat(source).doesNotContain("context.poseStack().last()");
    }

    @Test
    void preview_outline_pipeline_emits_diagnostics_at_hit_selection_and_submission_boundaries() throws IOException {
        String renderer = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/client/preview/StructurePreviewRenderer.java"));
        String widget = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/client/preview/StructurePreviewWidget.java"));
        String scene = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/client/preview/scene/PreviewSceneRenderer.java"));

        assertThat(renderer).contains("MMCR preview depth hit");
        assertThat(widget).contains("MMCR preview selected hit");
        assertThat(scene).contains("MMCR preview outline submit");
        assertThat(scene).contains("MMCR preview outline batch flushed");
    }

    @Test
    void preview_hit_state_snapshots_mutable_block_positions() throws IOException {
        String source = Files.readString(Path.of("src/main/java/cn/howxu/mmcr/client/preview/StructurePreviewRenderer.java"));

        assertThat(source).contains("copyHit(blockHitResult)");
        assertThat(source).contains("copyHit(scene.clip(");
        assertThat(source).contains("hit.getBlockPos().immutable()");
    }
}
