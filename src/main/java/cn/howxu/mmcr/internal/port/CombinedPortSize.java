package cn.howxu.mmcr.internal.port;

/**
 * Ordinary combined port item and fluid type counts.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum CombinedPortSize {
    BASIC("basic", 6, 1, ItemBusSize.NORMAL.ordinal(), FluidHatchSize.BIG.ordinal()),
    ADVANCED("advanced", 9, 1, ItemBusSize.REINFORCED.ordinal(), FluidHatchSize.HUGE.ordinal()),
    REINFORCED("reinforced", 12, 2, ItemBusSize.BIG.ordinal(), FluidHatchSize.LUDICROUS.ordinal()),
    ULTIMATE("ultimate", 16, 2, ItemBusSize.HUGE.ordinal(), FluidHatchSize.VACUUM.ordinal());

    private final String id;
    private final int itemTypes;
    private final int fluidTypes;
    private final int itemTier;
    private final int fluidTier;

    CombinedPortSize(String id, int itemTypes, int fluidTypes, int itemTier, int fluidTier) {
        this.id = id;
        this.itemTypes = itemTypes;
        this.fluidTypes = fluidTypes;
        this.itemTier = itemTier;
        this.fluidTier = fluidTier;
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

    public int itemTier() {
        return itemTier;
    }

    public int fluidTier() {
        return fluidTier;
    }
}
