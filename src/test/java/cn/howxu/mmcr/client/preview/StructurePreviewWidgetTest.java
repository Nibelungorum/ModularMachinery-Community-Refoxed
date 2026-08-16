package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies host-neutral structure-preview widget state transitions.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewWidgetTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void widget_cycles_single_layers_then_all_and_reset_restores_all_layers() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(2, 5));
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);

        widget.selectNextLayer();
        assertThat(renderer.visibility()).isEqualTo(PreviewVisibility.singleLayer(2));
        widget.selectNextLayer();
        assertThat(renderer.visibility()).isEqualTo(PreviewVisibility.singleLayer(5));
        widget.selectNextLayer();
        assertThat(renderer.visibility()).isEqualTo(PreviewVisibility.ALL);
        widget.reset();

        assertThat(renderer.visibility()).isEqualTo(PreviewVisibility.ALL);
        assertThat(renderer.resetCameraCalls()).isEqualTo(1);
    }

    @Test
    void widget_exposes_its_selected_layer_and_current_hits_to_hosts() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(2, 5));
        renderer.hitResult = new Object();
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(10, 20, 30, 40));

        widget.selectNextLayer();
        widget.mouseClicked(20, 30, 0);
        widget.mouseReleased(20, 30, 0);

        assertThat(widget.selectedLayer()).isEqualTo(2);
        assertThat(widget.hoverHit()).isSameAs(renderer.hitResult);
        assertThat(widget.selectedHit()).isSameAs(renderer.selectedHit);
    }

    @Test
    void widget_rejects_presses_outside_its_viewport_and_closes_renderer_once() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(10, 20, 30, 40));

        assertThat(widget.mouseClicked(9, 20, 0)).isFalse();
        assertThat(widget.mouseClicked(10, 20, 0)).isTrue();
        assertThat(widget.mouseReleased(10, 20, 0)).isTrue();
        widget.close();
        widget.close();

        assertThat(renderer.closeCalls()).isEqualTo(1);
    }

    @Test
    void widget_maps_drag_and_scroll_only_from_inside_its_viewport() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(10, 20, 30, 40));
        long beforeRotation = widget.camera().rotationVersion();
        float beforeDistance = widget.camera().distance();

        widget.mouseClicked(20, 30, 0);
        assertThat(widget.mouseDragged(24, 35, 0, 4, 5)).isTrue();
        assertThat(widget.mouseScrolled(9, 20, 1)).isFalse();
        assertThat(widget.mouseScrolled(20, 30, 1)).isTrue();

        assertThat(widget.camera().rotationVersion()).isGreaterThan(beforeRotation);
        assertThat(widget.camera().distance()).isEqualTo(beforeDistance * 0.9F);
    }

    @Test
    void widget_orbits_opposite_to_left_drag_delta() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(10, 20, 30, 40));
        float beforeYaw = widget.camera().yaw();
        float beforePitch = widget.camera().pitch();

        widget.mouseClicked(20, 30, 0);
        widget.mouseDragged(24, 35, 0, 4, 5);

        assertThat(widget.camera().yaw()).isEqualTo(beforeYaw - 0.04F);
        assertThat(widget.camera().pitch()).isEqualTo(beforePitch - 0.05F);
    }

    @Test
    void widget_selects_a_hit_for_left_release_at_the_drag_threshold() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        renderer.hitResult = new Object();
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(10, 20, 30, 40));

        widget.mouseClicked(20, 30, 0);
        widget.mouseDragged(23, 30, 0, 3, 0);
        assertThat(widget.mouseReleased(23, 30, 0)).isTrue();

        assertThat(renderer.selectedHit).isSameAs(renderer.hitResult);
    }

    @Test
    void widget_does_not_select_a_hit_after_dragging_beyond_threshold_or_with_another_button() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        renderer.hitResult = new Object();
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(10, 20, 30, 40));

        widget.mouseClicked(20, 30, 0);
        widget.mouseDragged(24, 30, 0, 4, 0);
        widget.mouseReleased(24, 30, 0);
        widget.mouseClicked(20, 30, 2);
        widget.mouseReleased(20, 30, 2);

        assertThat(renderer.selectedHit).isNull();
    }

    private static StructurePreviewSchema schemaAtLayers(int... layers) {
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> states = new LinkedHashMap<>();
        for (int layer : layers) {
            states.put(new BlockPos(0, layer, 0), Blocks.IRON_BLOCK.defaultBlockState());
        }
        return new StructurePreviewSchema(MMCR.id("widget_test"), states, Map.of());
    }

    /**
     * Captures calls made by a widget without relying on a rendering host.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingRenderer implements PreviewRenderer {
        private final StructurePreviewSchema schema;
        private PreviewVisibility visibility = PreviewVisibility.ALL;
        private int resetCameraCalls;
        private int closeCalls;
        private Object hitResult;
        private Object selectedHit;

        private RecordingRenderer(StructurePreviewSchema schema) {
            this.schema = schema;
        }

        @Override
        public StructurePreviewSchema schema() {
            return schema;
        }

        @Override
        public void setVisibility(PreviewVisibility visibility) {
            this.visibility = visibility;
        }

        @Override
        public void resetCamera() {
            resetCameraCalls++;
        }

        @Override
        public void render(PreviewRenderContext context) {
        }

        @Override
        public Object hitResult() {
            return hitResult;
        }

        @Override
        public void selectHit(Object hitResult) {
            selectedHit = hitResult;
        }

        @Override
        public void close() {
            closeCalls++;
        }

        private PreviewVisibility visibility() {
            return visibility;
        }

        private int resetCameraCalls() {
            return resetCameraCalls;
        }

        private int closeCalls() {
            return closeCalls;
        }
    }
}
