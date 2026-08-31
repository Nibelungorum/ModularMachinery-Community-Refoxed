package cn.howxu.mmcr.internal.port;

/**
 * Extended combined port item and fluid type counts.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ExtendedCombinedPortSize {
    ADVANCED("advanced", 6, 2, ItemBusSize.LUDICROUS.ordinal() + 1, FluidHatchSize.VACUUM.ordinal() + 1),
    REINFORCED("reinforced", 12, 4, ItemBusSize.LUDICROUS.ordinal() + 1, FluidHatchSize.VACUUM.ordinal() + 1),
    ULTIMATE("ultimate", 18, 6, ItemBusSize.LUDICROUS.ordinal() + 1, FluidHatchSize.VACUUM.ordinal() + 1);

    private final String id;
    private final int itemTypes;
    private final int fluidTypes;
    private final int itemTier;
    private final int fluidTier;

    ExtendedCombinedPortSize(String id, int itemTypes, int fluidTypes, int itemTier, int fluidTier) {
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
