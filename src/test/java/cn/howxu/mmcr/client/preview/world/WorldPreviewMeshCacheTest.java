package cn.howxu.mmcr.client.preview.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies world preview mesh cache state transitions.
 *
 * @author howxu <dev@howxu.cn>
 */
class WorldPreviewMeshCacheTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void sameKeyDoesNotRequestAnotherCompilation() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();
        WorldPreviewMeshKey key = key(0, 0);

        assertThat(cache.request(key)).isTrue();
        assertThat(cache.request(key)).isFalse();
    }

    @Test
    void changedLayerOrCameraCellRequestsReplacement() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();

        assertThat(cache.request(key(0, 0))).isTrue();
        assertThat(cache.request(key(1, 0))).isTrue();
        assertThat(cache.request(key(1, 1))).isTrue();
    }

    @Test
    void clearingDropsCurrentAndPendingState() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();
        WorldPreviewMeshKey key = key(0, 0);
        cache.request(key);
        cache.publish(key, () -> {});
        cache.clear();

        assertThat(cache.current()).isNull();
        assertThat(cache.request(key)).isTrue();
    }

    private static WorldPreviewMeshKey key(int layer, int cameraCell) {
        return new WorldPreviewMeshKey(Level.OVERWORLD, BlockPos.ZERO, layer,
                new BlockPos(cameraCell, 0, 0));
    }
}
