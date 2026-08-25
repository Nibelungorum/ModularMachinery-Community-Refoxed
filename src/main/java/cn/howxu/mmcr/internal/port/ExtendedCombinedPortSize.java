package cn.howxu.mmcr.internal.port;

/**
 * Extended combined port item and fluid type counts.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ExtendedCombinedPortSize {
    ADVANCED("advanced", 3, 1),
    REINFORCED("reinforced", 6, 2),
    ULTIMATE("ultimate", 9, 3);

    private final String id;
    private final int itemTypes;
    private final int fluidTypes;

    ExtendedCombinedPortSize(String id, int itemTypes, int fluidTypes) {
        this.id = id;
        this.itemTypes = itemTypes;
        this.fluidTypes = fluidTypes;
    }

    public String id() {
        return id;
    }

    public int itemTypes() {
        return itemTypes;
    }

    public int fluidTypes() {
        return fluidTypes;
    }
}
