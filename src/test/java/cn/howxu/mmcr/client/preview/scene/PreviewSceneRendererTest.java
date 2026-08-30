package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies screen-coordinate picking against the preview level.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewSceneRendererTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void screen_center_ray_hits_the_block_seen_by_the_preview_camera() {
        PreviewSceneRenderer renderer = renderer();

        try {
            BlockHitResult hit = renderer.rayTrace(camera(), 80.0D, 50.0D, 160, 100);

            assertThat(hit).isNotNull();
            assertThat(hit.getBlockPos()).isEqualTo(BlockPos.ZERO);
        } finally {
            renderer.dispose();
        }
    }

    @Test
    void screen_ray_rejects_pointer_outside_the_preview_viewport() {
        PreviewSceneRenderer renderer = renderer();

        try {
            assertThat(renderer.rayTrace(camera(), -1.0D, 50.0D, 160, 100)).isNull();
            assertThat(renderer.rayTrace(camera(), 160.0D, 50.0D, 160, 100)).isNull();
        } finally {
            renderer.dispose();
        }
    }

    @Test
    void screen_ray_ignores_a_block_hidden_by_preview_visibility() {
        PreviewSceneRenderer renderer = renderer();

        try {
            renderer.setVisibility(PreviewVisibility.singleLayer(1));

            assertThat(renderer.rayTrace(camera(), 80.0D, 50.0D, 160, 100)).isNull();
        } finally {
            renderer.dispose();
        }
    }

    private static PreviewSceneRenderer renderer() {
        StructurePreviewSchema schema = new StructurePreviewSchema(
                MMCR.id("preview_ray_test"),
                Map.of(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState()),
                Map.of());
        PreviewLevel level = PreviewLevel.create(schema, () -> PreviewVisibility.ALL);
        return new PreviewSceneRenderer(level, schema);
    }

    private static PreviewSceneCamera camera() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(new Vector3f(0.5F, 0.5F, 0.5F), 8.0F);
        return PreviewSceneCamera.from(camera, 160, 100);
    }
}
