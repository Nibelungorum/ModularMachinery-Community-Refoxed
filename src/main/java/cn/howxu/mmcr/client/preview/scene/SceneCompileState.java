package cn.howxu.mmcr.client.preview.scene;

/**
 * Tracks preview-scene cache publication eligibility.
 *
 * @author howxu <dev@howxu.cn>
 */
final class SceneCompileState {
    private long generation;
    private boolean closed;
    private boolean completeCache;
    private SceneCompileKind pendingKind;

    long requestFullRebuild() {
        generation++;
        pendingKind = SceneCompileKind.FULL;
        return generation;
    }

    void markFullCachePublished() {
        completeCache = true;
        pendingKind = null;
    }

    long onCameraRotation(long rotationVersion) {
        return requestTranslucentResort();
    }

    long onCameraPanOrZoom() {
        return requestTranslucentResort();
    }

    void markTranslucentCachePublished(long resultGeneration) {
        if (!accepts(resultGeneration, SceneCompileKind.TRANSLUCENT_ONLY)) {
            throw new IllegalStateException("cannot publish stale preview translucent cache");
        }
        pendingKind = null;
    }

    private long requestTranslucentResort() {
        if (completeCache && !closed && pendingKind != SceneCompileKind.FULL) {
            generation++;
            pendingKind = SceneCompileKind.TRANSLUCENT_ONLY;
        }
        return generation;
    }

    void close() {
        generation++;
        closed = true;
        pendingKind = null;
    }

    boolean accepts(long resultGeneration, SceneCompileKind kind) {
        return !closed && generation == resultGeneration && (kind != SceneCompileKind.TRANSLUCENT_ONLY || completeCache);
    }

    boolean hasCompleteCache() {
        return completeCache;
    }

    SceneCompileKind pendingKind() {
        return pendingKind;
    }
}

enum SceneCompileKind {
    FULL,
    TRANSLUCENT_ONLY
}
