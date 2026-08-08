package cn.howxu.mmcr.client.model;

/**
 * Loader marker for shared dynamic machine overlay model declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayModelLoader {
    private static final DynamicOverlayModelLoader CONTROLLER = new DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind.CONTROLLER);
    private static final DynamicOverlayModelLoader PORT = new DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind.PORT);

    private final DynamicOverlayBakedModel.Kind kind;

    private DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind kind) {
        this.kind = kind;
    }

    public static DynamicOverlayModelLoader controller() {
        return CONTROLLER;
    }

    public static DynamicOverlayModelLoader port() {
        return PORT;
    }

    public DynamicOverlayBakedModel.Kind kind() {
        return kind;
    }
}
