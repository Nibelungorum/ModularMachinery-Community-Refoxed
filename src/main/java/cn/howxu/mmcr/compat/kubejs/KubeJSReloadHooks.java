package cn.howxu.mmcr.compat.kubejs;

/**
 * Internal bridge used by the KubeJS reload mixin without placing the mixin in the public KubeJS API package.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class KubeJSReloadHooks {
    private KubeJSReloadHooks() {
    }

    public static void abortServerReload(Object manager) {
        Plugin.abortServerReload(manager);
    }
}
