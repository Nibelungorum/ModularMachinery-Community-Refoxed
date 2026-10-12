package cn.howxu.mmcr.internal.sync;

/**
 * Compatibility entrypoint for existing callers of the runtime viewer bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiRuntimeReloadBridge {
    private JeiRuntimeReloadBridge() {
    }

    public static void reloadIfAvailable(RuntimeContentSnapshot snapshot) {
        RecipeViewerReloadBridge.reloadIfAvailable(snapshot);
    }
}
