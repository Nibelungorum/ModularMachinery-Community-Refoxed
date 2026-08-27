package cn.howxu.mmcr.internal.port;

/**
 * Extended fluid hatch tank counts.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ExtendedFluidHatchSize {
    BASIC("basic", 8),
    ADVANCED("advanced", 16),
    REINFORCED("reinforced", 24),
    ULTIMATE("ultimate", 32);

    private final String id;
    private final int slots;

    ExtendedFluidHatchSize(String id, int slots) {
        this.id = id;
        this.slots = slots;
    }

    public String id() {
        return id;
    }

    public int slots() {
        return slots;
    }
}
