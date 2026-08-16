package cn.howxu.mmcr.client.preview.scene;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies ownership transitions for cached preview-scene meshes without creating GPU resources.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewSceneMeshLifecycleTest {

    @Test
    void full_publish_replaces_cache_only_after_new_generation_is_complete() {
        RecordingFull oldCache = new RecordingFull("old", "old-index");
        RecordingFull nextCache = new RecordingFull("next", "next-index");
        PreviewSceneMeshCache cache = new PreviewSceneMeshCache(oldCache);

        cache.publish(nextCache);

        assertThat(cache.current()).isSameAs(nextCache);
        assertThat(oldCache.closeCalls()).isEqualTo(1);
        assertThat(nextCache.closeCalls()).isZero();
    }

    @Test
    void stale_cancelled_and_failed_results_keep_last_complete_cache() {
        RecordingFull oldCache = new RecordingFull("old", "old-index");
        RecordingFull stale = new RecordingFull("stale", "stale-index");
        RecordingTranslucent cancelled = new RecordingTranslucent("cancelled-index");
        RecordingTranslucent failed = new RecordingTranslucent("failed-index");
        PreviewSceneMeshCache cache = new PreviewSceneMeshCache(oldCache);

        cache.reject(stale);
        cache.reject(cancelled);
        cache.reject(failed);

        assertThat(cache.current()).isSameAs(oldCache);
        assertThat(oldCache.closeCalls()).isZero();
        assertThat(stale.closeCalls()).isEqualTo(1);
        assertThat(cancelled.closeCalls()).isEqualTo(1);
        assertThat(failed.closeCalls()).isEqualTo(1);
    }

    @Test
    void translucent_publish_transfers_new_order_and_preserves_solid_and_cutout() {
        Object solid = new Object();
        Object cutout = new Object();
        RecordingFull full = new RecordingFull("full", "old-index", solid, cutout);
        RecordingTranslucent resorted = new RecordingTranslucent("new-index");
        PreviewSceneMeshCache cache = new PreviewSceneMeshCache(full);

        cache.publishTranslucent(resorted);

        assertThat(cache.current()).isSameAs(full);
        assertThat(full.solid()).isSameAs(solid);
        assertThat(full.cutout()).isSameAs(cutout);
        assertThat(full.translucentOrder()).isEqualTo("new-index");
        assertThat(full.displacedIndexCloseCalls()).isEqualTo(1);
        assertThat(resorted.closeCalls()).isZero();
        cache.close();
        assertThat(full.closeCalls()).isEqualTo(1);
    }

    @Test
    void close_and_late_unpublished_result_release_each_owner_exactly_once() {
        RecordingFull current = new RecordingFull("current", "current-index");
        RecordingTranslucent late = new RecordingTranslucent("late-index");
        PreviewSceneMeshCache cache = new PreviewSceneMeshCache(current);

        cache.close();
        cache.reject(late);
        cache.close();
        cache.reject(late);

        assertThat(current.closeCalls()).isEqualTo(1);
        assertThat(late.closeCalls()).isEqualTo(1);
        assertThat(cache.current()).isNull();
    }

    @Test
    void stale_generation_is_rejected_before_it_can_replace_current_cache() {
        SceneCompileState state = new SceneCompileState();
        long oldGeneration = state.requestFullRebuild();
        long currentGeneration = state.requestFullRebuild();
        RecordingFull current = new RecordingFull("current", "current-index");
        RecordingFull stale = new RecordingFull("stale", "stale-index");
        PreviewSceneMeshCache cache = new PreviewSceneMeshCache(current);

        if (!state.accepts(oldGeneration, SceneCompileKind.FULL)) cache.reject(stale);

        assertThat(currentGeneration).isNotEqualTo(oldGeneration);
        assertThat(cache.current()).isSameAs(current);
        assertThat(stale.closeCalls()).isEqualTo(1);
    }

    private static final class RecordingFull implements PreviewSceneMeshCache.FullCache {
        private final String label;
        private final Object solid;
        private final Object cutout;
        private String translucentOrder;
        private int closeCalls;
        private int displacedIndexCloseCalls;

        private RecordingFull(String label, String translucentOrder) {
            this(label, translucentOrder, new Object(), new Object());
        }

        private RecordingFull(String label, String translucentOrder, Object solid, Object cutout) {
            this.label = label;
            this.translucentOrder = translucentOrder;
            this.solid = solid;
            this.cutout = cutout;
        }

        @Override
        public PreviewSceneMeshCache.TranslucentCache replaceTranslucent(PreviewSceneMeshCache.TranslucentCache result) {
            displacedIndexCloseCalls++;
            translucentOrder = ((RecordingTranslucent) result).order;
            return null;
        }

        @Override public void close() { closeCalls++; }
        Object solid() { return solid; }
        Object cutout() { return cutout; }
        String translucentOrder() { return translucentOrder; }
        int closeCalls() { return closeCalls; }
        int displacedIndexCloseCalls() { return displacedIndexCloseCalls; }
    }

    private static final class RecordingTranslucent implements PreviewSceneMeshCache.TranslucentCache {
        private final String order;
        private int closeCalls;

        private RecordingTranslucent(String order) { this.order = order; }
        @Override public void close() { closeCalls++; }
        int closeCalls() { return closeCalls; }
    }
}
