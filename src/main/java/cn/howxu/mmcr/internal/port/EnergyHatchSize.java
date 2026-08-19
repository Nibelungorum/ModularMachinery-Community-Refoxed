package cn.howxu.mmcr.internal.port;

/**
 * MMCE energy hatch capacities and FE transfer limits.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum EnergyHatchSize {
    TINY("tiny", 400000L, 500L),
    SMALL("small", 800000L, 1000L),
    NORMAL("normal", 1000000L, 2000L),
    REINFORCED("reinforced", 4000000L, 4000L),
    BIG("big", 8000000L, 8000L),
    HUGE("huge", 32000000L, 64000L),
    LUDICROUS("ludicrous", 128000000L, 256000L),
    ULTIMATE("ultimate", 256000000L, 512000L);

    private final String id;
    private final long capacity;
    private final long transfer;

    EnergyHatchSize(String id, long capacity, long transfer) {
        this.id = id;
        this.capacity = capacity;
        this.transfer = transfer;
    }

    public String id() {
        return id;
    }

    public long capacity() {
        return capacity;
    }

    public long transfer() {
        return transfer;
    }
}
