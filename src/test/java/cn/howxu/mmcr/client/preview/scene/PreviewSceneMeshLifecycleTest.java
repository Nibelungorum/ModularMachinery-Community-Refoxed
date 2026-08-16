package cn.howxu.mmcr.client.preview.scene;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies ownership transitions for cached preview-scene meshes without creating GPU resources.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewSceneMeshLifecycleTest {

    @Test
    void full_publish_replaces_cache_only_after_new_generation_is_complete() {
        RecordingCache oldCache = RecordingCache.complete("old");
        RecordingCache nextCache = RecordingCache.complete("next");
        PreviewSceneMeshCache cache = new PreviewSceneMeshCache(oldCache);

        cache.publish(nextCache);

        assertThat(cache.current()).isSameAs(nextCache);
        assertThat(oldCache.closed()).isTrue();
        assertThat(nextCache.closed()).isFalse();
    }

    @Test
    void rejected_or_cancelled_result_keeps_last_complete_cache() {
        RecordingCache oldCache = RecordingCache.complete("old");
        RecordingCache lateCache = RecordingCache.complete("late");
        PreviewSceneMeshCache cache = new PreviewSceneMeshCache(oldCache);

        cache.reject(lateCache);

        assertThat(cache.current()).isSameAs(oldCache);
        assertThat(oldCache.closed()).isFalse();
        assertThat(lateCache.closed()).isTrue();
    }

    @Test
    void translucent_publish_preserves_solid_and_cutout_layers() {
        RecordingCache full = RecordingCache.complete("full");
        Object solid = new Object();
        Object cutout = new Object();
        Object translucent = new Object();
        full.layers.put(PreviewSceneMeshCache.Layer.SOLID, solid);
        full.layers.put(PreviewSceneMeshCache.Layer.CUTOUT, cutout);
        full.layers.put(PreviewSceneMeshCache.Layer.TRANSLUCENT, translucent);
        RecordingCache translucentOnly = RecordingCache.translucent("resorted", new Object());
        PreviewSceneMeshCache cache = new PreviewSceneMeshCache(full);

        cache.publishTranslucent(translucentOnly);

        assertThat(cache.current()).isSameAs(full);
        assertThat(full.layers.get(PreviewSceneMeshCache.Layer.SOLID)).isSameAs(solid);
        assertThat(full.layers.get(PreviewSceneMeshCache.Layer.CUTOUT)).isSameAs(cutout);
        assertThat(full.layers.get(PreviewSceneMeshCache.Layer.TRANSLUCENT))
                .isSameAs(translucentOnly.layers.get(PreviewSceneMeshCache.Layer.TRANSLUCENT));
        assertThat(translucentOnly.closed()).isFalse();
    }

    @Test
    void close_releases_current_cache_once() {
        RecordingCache current = RecordingCache.complete("current");
        PreviewSceneMeshCache cache = new PreviewSceneMeshCache(current);

        cache.close();
        cache.close();

        assertThat(current.closeCalls()).isEqualTo(1);
        assertThat(cache.current()).isNull();
    }

    private static final class RecordingCache implements PreviewSceneMeshCache.CacheOwner {
        private final String label;
        private final boolean full;
        private final EnumMap<PreviewSceneMeshCache.Layer, Object> layers =
                new EnumMap<>(PreviewSceneMeshCache.Layer.class);
        private int closeCalls;

        private RecordingCache(String label, boolean full) {
            this.label = label;
            this.full = full;
        }

        static RecordingCache complete(String label) {
            return new RecordingCache(label, true);
        }

        static RecordingCache translucent(String label, Object mesh) {
            RecordingCache cache = new RecordingCache(label, false);
            cache.layers.put(PreviewSceneMeshCache.Layer.TRANSLUCENT, mesh);
            return cache;
        }

        @Override
        public boolean full() {
            return full;
        }

        @Override
        public Object replaceTranslucent(PreviewSceneMeshCache.CacheOwner replacement) {
            return layers.put(PreviewSceneMeshCache.Layer.TRANSLUCENT,
                    ((RecordingCache) replacement).layers.get(PreviewSceneMeshCache.Layer.TRANSLUCENT));
        }

        @Override
        public void close() {
            closeCalls++;
        }

        String label() {
            return label;
        }

        boolean closed() {
            return closeCalls > 0;
        }

        int closeCalls() {
            return closeCalls;
        }
    }
}
