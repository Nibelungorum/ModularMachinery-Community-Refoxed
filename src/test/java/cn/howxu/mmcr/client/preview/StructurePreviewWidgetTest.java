package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.scene.PreviewSceneCamera;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Vector3f;

import net.minecraft.world.level.block.state.BlockState;
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
    void highlights_forward_explicit_changes_without_reset_or_layer_controls_disabling_them() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(2, 5));
        try (var widget = new StructurePreviewWidget(renderer)) {
            widget.setPositionHighlightsEnabled(true);
            widget.selectNextLayer();
            widget.selectPreviousLayer();
            widget.showAllLayers();
            widget.reset();

            assertThat(renderer.positionHighlightChanges).containsExactly(true);
            widget.setPositionHighlightsEnabled(false);
            assertThat(renderer.positionHighlightChanges).containsExactly(true, false);
        }
    }

    @Test
    void closed_widget_does_not_forward_highlight_changes() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        var widget = new StructurePreviewWidget(renderer);
        widget.setPositionHighlightsEnabled(true);
        widget.close();
        widget.setPositionHighlightsEnabled(false);

        assertThat(renderer.positionHighlightChanges).containsExactly(true);
    }

    @Test
    void initial_camera_and_reset_look_horizontally_at_each_controller_front() {
        BlockState controller = TestBootstrap.newController().getBlockState();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var schema = new StructurePreviewSchema(MMCR.id("front_camera"), Map.of(
                    BlockPos.ZERO, controller.setValue(MachineControllerBlock.FACING, facing),
                    new BlockPos(-3, 0, -3), Blocks.OAK_STAIRS.defaultBlockState(),
                    new BlockPos(3, 0, 3), Blocks.OAK_STAIRS.defaultBlockState()), Map.of());
            var widget = new StructurePreviewWidget(new RecordingRenderer(schema));
            Vector3f front = new Vector3f(facing.getStepX(), 0.0F, facing.getStepZ());
            assertThat(widget.camera().position().sub(widget.camera().lookAt()).normalize().distance(front))
                    .isLessThan(0.00001F);
            assertThat(widget.camera().lookAt().y).isEqualTo((float) BlockPos.ZERO.getCenter().y);
            assertThat(widget.camera().position().y).isEqualTo((float) BlockPos.ZERO.getCenter().y);
            Vector3f initialAnchor = widget.camera().lookAt();
            float initialDistance = widget.camera().distance();
            widget.setViewport(new PreviewViewport(0, 0, 100, 100));
            widget.camera().orbit(0.7F, 0.4F);
            widget.mouseClicked(20, 30, 1);
            widget.mouseDragged(24, 35, 1, 4, 5);
            widget.mouseReleased(24, 35, 1);
            widget.camera().zoom(0.5F);
            assertThat(widget.camera().lookAt()).isNotEqualTo(initialAnchor);
            widget.reset();
            assertThat(widget.camera().lookAt()).isEqualTo(initialAnchor);
            assertThat(widget.camera().distance()).isEqualTo(initialDistance);
            assertThat(widget.camera().position().sub(widget.camera().lookAt()).normalize().distance(front))
                    .isLessThan(0.00001F);
        }
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
        AtomicLong clock = new AtomicLong(1_000L);
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer, clock::get);
        widget.setViewport(new PreviewViewport(10, 20, 30, 40));

        widget.selectNextLayer();
        widget.mouseClicked(20, 30, 0);
        widget.mouseReleased(20, 30, 0);
        clock.addAndGet(100L);
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
    void drag_enters_interactive_mode_and_release_restores_full_quality() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(0, 0, 20, 20));

        assertThat(widget.mouseClicked(10, 10, 0)).isTrue();
        assertThat(renderer.interactive()).isTrue();
        assertThat(widget.mouseReleased(10, 10, 0)).isTrue();
        assertThat(renderer.interactive()).isFalse();
        assertThat(renderer.interactiveChanges()).isEqualTo(2);
    }

    @Test
    void scroll_enters_interactive_mode_for_the_current_zoom_update() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(0, 0, 20, 20));

        assertThat(widget.mouseScrolled(10, 10, 1.0D)).isTrue();
        assertThat(renderer.interactive()).isTrue();
    }

    @Test
    void widget_orbits_horizontally_opposite_and_vertically_with_left_drag_delta() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(10, 20, 30, 40));
        float beforeYaw = widget.camera().yaw();
        float beforePitch = widget.camera().pitch();

        widget.mouseClicked(20, 30, 0);
        widget.mouseDragged(24, 35, 0, 4, 5);

        assertThat(widget.camera().yaw()).isEqualTo(beforeYaw - 0.04F);
        assertThat(widget.camera().pitch()).isEqualTo(beforePitch + 0.05F);
    }

    @Test
    void right_drag_pan_scales_with_zoom_and_viewport_height() {
        try (var widget = new StructurePreviewWidget(new RecordingRenderer(schemaAtLayers(0, 4)))) {
            widget.setViewport(new PreviewViewport(0, 0, 100, 100));
            Vector3f initialAnchor = widget.camera().lookAt();
            widget.mouseClicked(20, 30, 1);
            widget.mouseDragged(24, 35, 1, 4, 5);
            widget.mouseReleased(24, 35, 1);
            Vector3f firstOffset = widget.camera().lookAt().sub(initialAnchor);
            assertThat(firstOffset.length()).isPositive().isLessThan(widget.camera().distance());

            widget.reset();
            widget.camera().zoom(0.5F);
            widget.setViewport(new PreviewViewport(0, 0, 200, 200));
            widget.mouseClicked(20, 30, 1);
            widget.mouseDragged(24, 35, 1, 4, 5);

            Vector3f secondOffset = widget.camera().lookAt().sub(initialAnchor);
            assertThat(secondOffset.distance(firstOffset.mul(0.25F))).isLessThan(0.00001F);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void pan_keeps_the_structure_moving_in_the_mouse_drag_direction(int button) {
        try (var widget = new StructurePreviewWidget(new RecordingRenderer(schemaAtLayers(0, 4)))) {
            widget.setViewport(new PreviewViewport(0, 0, 100, 100));
            Vector3f structurePoint = widget.camera().lookAt();
            widget.mouseClicked(20, 30, button);
            widget.mouseDragged(24, 35, button, 4, 5);

            PreviewSceneCamera scene = PreviewSceneCamera.from(widget.camera(), 100, 100);
            Vector3f projected = scene.projection().mul(scene.view()).transformProject(new Vector3f(structurePoint));
            assertThat(projected.x).isPositive();
            // Projection Y points up, while mouse Y points down.
            assertThat(projected.y).isNegative();

            widget.mouseDragged(20, 30, button, -4, -5);
            assertThat(widget.camera().lookAt().distance(structurePoint)).isLessThan(0.00001F);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void right_and_middle_drag_pan_without_rotation_zoom_or_hit_selection(int button) {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        renderer.hitResult = new Object();
        try (var widget = new StructurePreviewWidget(renderer)) {
            widget.setViewport(new PreviewViewport(10, 20, 100, 100));
            Vector3f initialAnchor = widget.camera().lookAt();
            Vector3f initialDirection = widget.camera().position().sub(initialAnchor);
            long initialRotation = widget.camera().rotationVersion();
            float initialDistance = widget.camera().distance();

            assertThat(widget.mouseClicked(20, 30, button)).isTrue();
            assertThat(widget.mouseDragged(24, 35, button, 4, 5)).isTrue();
            assertThat(widget.mouseReleased(24, 35, button)).isTrue();

            Vector3f offset = widget.camera().lookAt().sub(initialAnchor);
            assertThat(offset.length()).isPositive();
            assertThat(Math.abs(offset.dot(initialDirection))).isLessThan(0.00001F);
            assertThat(widget.camera().rotationVersion()).isEqualTo(initialRotation);
            assertThat(widget.camera().distance()).isEqualTo(initialDistance);
            assertThat(widget.selectedHit()).isNull();
            assertThat(renderer.selectedHit).isNull();
            assertThat(renderer.interactive()).isFalse();
        }
    }

    @Test
    void widget_does_not_select_a_hit_after_a_drag_callback_at_the_drag_threshold() {
        RecordingRenderer renderer = new RecordingRenderer(schemaAtLayers(0));
        renderer.hitResult = new Object();
        StructurePreviewWidget widget = new StructurePreviewWidget(renderer);
        widget.setViewport(new PreviewViewport(10, 20, 30, 40));

        widget.mouseClicked(20, 30, 0);
        widget.mouseDragged(23, 30, 0, 3, 0);
        assertThat(widget.mouseReleased(23, 30, 0)).isTrue();

        assertThat(renderer.selectedHit).isNull();
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
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
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
        private boolean interactive;
        private int interactiveChanges;
        private Object hitResult;
        private Object selectedHit;
        private final List<Boolean> positionHighlightChanges = new ArrayList<>();

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
        public void setPositionHighlightsEnabled(boolean enabled) {
            positionHighlightChanges.add(enabled);
        }

        @Override
        public void resetCamera() {
            resetCameraCalls++;
        }

        @Override
        public void setInteractive(boolean interactive) {
            this.interactive = interactive;
            interactiveChanges++;
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

        private boolean interactive() {
            return interactive;
        }

        private int interactiveChanges() {
            return interactiveChanges;
        }
    }
}
