package cn.howxu.mmcr.internal.port;

/**
 * MMCE energy hatch capacities and FE transfer limits.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum EnergyHatchSize {
    TINY("tiny", 2048, 128),
    SMALL("small", 4096, 512),
    NORMAL("normal", 8192, 512),
    REINFORCED("reinforced", 16384, 2048),
    BIG("big", 32768, 8192),
    HUGE("huge", 131072, 32768),
    LUDICROUS("ludicrous", 524288, 131072),
    ULTIMATE("ultimate", 2097152, 131072);

    private final String id;
    private final int capacity;
    private final int transfer;

    EnergyHatchSize(String id, int capacity, int transfer) {
        this.id = id;
        this.capacity = capacity;
        this.transfer = transfer;
    }

    public String id() {
        return id;
    }

    public int capacity() {
        return capacity;
    }

    public int transfer() {
        return transfer;
    }
}
