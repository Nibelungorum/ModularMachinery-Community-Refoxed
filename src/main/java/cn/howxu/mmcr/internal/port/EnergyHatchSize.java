package cn.howxu.mmcr.internal.port;

/**
 * MMCE energy hatch capacities and FE transfer limits.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum EnergyHatchSize {
    TINY("tiny", 2048L, 128L),
    SMALL("small", 4096L, 512L),
    NORMAL("normal", 8192L, 512L),
    REINFORCED("reinforced", 16384L, 2048L),
    BIG("big", 32768L, 8192L),
    HUGE("huge", 131072L, 32768L),
    LUDICROUS("ludicrous", 524288L, 131072L),
    ULTIMATE("ultimate", 2097152L, 131072L);

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
