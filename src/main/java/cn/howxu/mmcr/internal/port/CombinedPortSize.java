package cn.howxu.mmcr.internal.port;

/**
 * Ordinary combined port item and fluid type counts.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum CombinedPortSize {
    BASIC("basic", 6, 1),
    ADVANCED("advanced", 9, 1),
    REINFORCED("reinforced", 12, 2),
    ULTIMATE("ultimate", 16, 2);

    private final String id;
    private final int itemTypes;
    private final int fluidTypes;

    CombinedPortSize(String id, int itemTypes, int fluidTypes) {
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
