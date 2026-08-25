package cn.howxu.mmcr.internal.port;

/**
 * Extended energy hatch capacities and transfer limits.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ExtendedEnergyHatchSize {
    REINFORCED("reinforced", Integer.MAX_VALUE),
    ULTIMATE("ultimate", Long.MAX_VALUE);

    private final String id;
    private final long capacity;

    ExtendedEnergyHatchSize(String id, long capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public String id() {
        return id;
    }

    public long capacity() {
        return capacity;
    }

    public long transfer() {
        return capacity;
    }
}
