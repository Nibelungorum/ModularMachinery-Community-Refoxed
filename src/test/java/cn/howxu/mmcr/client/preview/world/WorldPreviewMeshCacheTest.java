package cn.howxu.mmcr.client.preview.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        WorldPreviewMeshCache.Request request = cache.requestToken(key);
        cache.publish(request, () -> {});
        cache.clear();

        assertThat(cache.current()).isNull();
        assertThat(cache.request(key)).isTrue();
    }

    @Test
    void matchingPublishMakesMeshReadyForItsKey() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();
        WorldPreviewMeshKey key = key(0, 0);
        TrackingMesh mesh = new TrackingMesh();

        WorldPreviewMeshCache.Request request = cache.requestToken(key);
        cache.publish(request, mesh);

        assertThat(cache.current(key)).isSameAs(mesh);
        assertThat(mesh.closed).isFalse();
    }

    @Test
    void takingCurrentTransfersOwnershipWithoutClosingIt() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();
        WorldPreviewMeshKey key = key(0, 0);
        TrackingMesh mesh = new TrackingMesh();
        WorldPreviewMeshCache.Request request = cache.requestToken(key);
        cache.publish(request, mesh);

        assertThat(cache.takeCurrent(key)).isSameAs(mesh);
        assertThat(mesh.closed).isFalse();
        assertThat(cache.current()).isNull();
    }

    @Test
    void stalePublishIsRejectedAndClosed() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();
        WorldPreviewMeshKey firstKey = key(0, 0);
        WorldPreviewMeshKey secondKey = key(1, 0);
        TrackingMesh staleMesh = new TrackingMesh();

        WorldPreviewMeshCache.Request staleRequest = cache.requestToken(firstKey);
        cache.request(secondKey);
        cache.publish(staleRequest, staleMesh);

        assertThat(staleMesh.closed).isTrue();
        assertThat(cache.current()).isNull();
    }

    @Test
    void olderGenerationForSameKeyIsRejectedAndClosed() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();
        WorldPreviewMeshKey key = key(0, 0);
        TrackingMesh staleMesh = new TrackingMesh();

        WorldPreviewMeshCache.Request oldRequest = cache.requestToken(key);
        cache.clear();
        cache.requestToken(key);
        cache.publish(oldRequest, staleMesh);

        assertThat(staleMesh.closed).isTrue();
        assertThat(cache.current()).isNull();
    }

    @Test
    void replacedMeshIsClosedButRemainsReadyUntilReplacementPublishes() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();
        WorldPreviewMeshKey firstKey = key(0, 0);
        WorldPreviewMeshKey secondKey = key(1, 0);
        TrackingMesh firstMesh = new TrackingMesh();
        TrackingMesh secondMesh = new TrackingMesh();

        WorldPreviewMeshCache.Request firstRequest = cache.requestToken(firstKey);
        cache.publish(firstRequest, firstMesh);
        WorldPreviewMeshCache.Request secondRequest = cache.requestToken(secondKey);

        assertThat(cache.current()).isSameAs(firstMesh);
        assertThat(cache.current(secondKey)).isNull();

        cache.publish(secondRequest, secondMesh);

        assertThat(firstMesh.closed).isTrue();
        assertThat(cache.current(secondKey)).isSameAs(secondMesh);
    }

    @Test
    void clearAndCloseCloseCurrentMeshAndRejectLaterPublish() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();
        WorldPreviewMeshKey key = key(0, 0);
        TrackingMesh currentMesh = new TrackingMesh();
        TrackingMesh rejectedMesh = new TrackingMesh();

        WorldPreviewMeshCache.Request request = cache.requestToken(key);
        cache.publish(request, currentMesh);
        cache.clear();
        cache.publish(request, rejectedMesh);
        cache.close();

        assertThat(currentMesh.closed).isTrue();
        assertThat(rejectedMesh.closed).isTrue();
        assertThat(cache.current()).isNull();
    }

    @Test
    void closeClosesCurrentMeshAndPreventsNewRequests() {
        WorldPreviewMeshCache cache = new WorldPreviewMeshCache();
        TrackingMesh mesh = new TrackingMesh();
        WorldPreviewMeshCache.Request request = cache.requestToken(key(0, 0));
        cache.publish(request, mesh);

        cache.close();

        assertThat(mesh.closed).isTrue();
        assertThatThrownBy(() -> cache.request(key(1, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static WorldPreviewMeshKey key(int layer, int cameraCell) {
        return new WorldPreviewMeshKey(Level.OVERWORLD, BlockPos.ZERO, layer,
                new BlockPos(cameraCell, 0, 0));
    }

    private static final class TrackingMesh implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
