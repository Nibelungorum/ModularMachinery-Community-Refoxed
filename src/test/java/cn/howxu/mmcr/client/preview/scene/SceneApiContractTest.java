package cn.howxu.mmcr.client.preview.scene;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies pure preview-scene compilation state transitions.
 *
 * @author howxu <dev@howxu.cn>
 */
class SceneApiContractTest {

    @Test
    void full_generation_rejects_stale_publication_and_keeps_last_complete_cache() {
        SceneCompileState state = new SceneCompileState();
        long oldGeneration = state.requestFullRebuild();
        state.markFullCachePublished();
        long currentGeneration = state.requestFullRebuild();

        assertThat(state.accepts(oldGeneration, SceneCompileKind.FULL)).isFalse();
        assertThat(state.accepts(currentGeneration, SceneCompileKind.FULL)).isTrue();
        assertThat(state.hasCompleteCache()).isTrue();
    }

    @Test
    void rotation_does_not_replace_pending_full_rebuild() {
        SceneCompileState state = new SceneCompileState();
        state.requestFullRebuild();
        state.markFullCachePublished();
        state.requestFullRebuild();

        state.onCameraRotation(1L);

        assertThat(state.pendingKind()).isEqualTo(SceneCompileKind.FULL);
    }

    @Test
    void every_eye_change_requests_translucent_resort_without_replacing_pending_full() {
        SceneCompileState state = new SceneCompileState();
        state.markFullCachePublished();

        state.onCameraPanOrZoom();

        assertThat(state.pendingKind()).isEqualTo(SceneCompileKind.TRANSLUCENT_ONLY);
    }

    @Test
    void translucent_results_require_a_published_full_cache() {
        SceneCompileState state = new SceneCompileState();
        long generation = state.requestFullRebuild();

        assertThat(state.accepts(generation, SceneCompileKind.TRANSLUCENT_ONLY)).isFalse();
        state.markFullCachePublished();

        assertThat(state.accepts(generation, SceneCompileKind.TRANSLUCENT_ONLY)).isTrue();
    }

    @Test
    void translucent_publish_requires_its_own_generation_and_never_replaces_full_pending_work() {
        SceneCompileState state = new SceneCompileState();
        long fullGeneration = state.requestFullRebuild();
        state.markFullCachePublished();
        long translucentGeneration = state.onCameraPanOrZoom();

        assertThat(translucentGeneration).isNotEqualTo(fullGeneration);
        assertThat(state.accepts(translucentGeneration, SceneCompileKind.TRANSLUCENT_ONLY)).isTrue();
        state.markTranslucentCachePublished(translucentGeneration);
        assertThat(state.pendingKind()).isNull();

        state.requestFullRebuild();
        state.onCameraPanOrZoom();
        assertThat(state.pendingKind()).isEqualTo(SceneCompileKind.FULL);
    }

    @Test
    void close_invalidates_generation_and_rejects_all_results() {
        SceneCompileState state = new SceneCompileState();
        long generation = state.requestFullRebuild();

        state.close();

        assertThat(state.accepts(generation, SceneCompileKind.FULL)).isFalse();
        assertThat(state.accepts(generation + 1L, SceneCompileKind.TRANSLUCENT_ONLY)).isFalse();
    }
}
