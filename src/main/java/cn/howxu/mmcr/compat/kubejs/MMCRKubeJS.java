package cn.howxu.mmcr.compat.kubejs;

/**
 * KubeJS global API facade for Modular Machinery Community Refoxed.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MMCRKubeJS {
    private final KubeJSApi api = new KubeJSApi();
    private final MMCRValues values = new MMCRValues();

    public KubeJSApi getAPI() {
        return api;
    }

    public MMCRValues getValues() {
        return values;
    }
}
