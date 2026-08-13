package cn.howxu.mmcr.internal.port;

/**
 * MMCE energy hatch capacities and FE transfer limits.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum EnergyHatchSize {
    TINY("tiny", 400000, 1000),
    SMALL("small", 1000000, 1200),
    NORMAL("normal", 1600000, 1600),
    REINFORCED("reinforced", 6400000, 6400),
    BIG("big", 25600000, 25600),
    HUGE("huge", 102400000, 102400),
    LUDICROUS("ludicrous", 256000000, 256000),
    ULTIMATE("ultimate", Integer.MAX_VALUE, 4000000);

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
