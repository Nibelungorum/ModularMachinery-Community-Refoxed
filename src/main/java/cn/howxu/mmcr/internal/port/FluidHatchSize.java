package cn.howxu.mmcr.internal.port;

/**
 * MMCE fluid hatch sizes in millibuckets.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum FluidHatchSize {
    TINY("tiny", 8000, 100),
    SMALL("small", 12000, 200),
    NORMAL("normal", 16000, 400),
    REINFORCED("reinforced", 32000, 1000),
    BIG("big", 64000, 2400),
    HUGE("huge", 128000, 3200),
    LUDICROUS("ludicrous", 512000, 6400),
    VACUUM("vacuum", Integer.MAX_VALUE, 40000);

    private final String id;
    private final int capacity;
    private final int transfer;

    FluidHatchSize(String id, int capacity, int transfer) {
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
