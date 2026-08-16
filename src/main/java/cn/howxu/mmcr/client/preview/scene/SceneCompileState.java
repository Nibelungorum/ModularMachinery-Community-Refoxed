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

    void onCameraRotation(long rotationVersion) {
        if (completeCache && !closed) {
            pendingKind = SceneCompileKind.TRANSLUCENT_ONLY;
        }
    }

    void onCameraPanOrZoom() {
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
