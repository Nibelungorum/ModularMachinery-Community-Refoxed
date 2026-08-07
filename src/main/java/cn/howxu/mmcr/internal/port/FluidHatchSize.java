package cn.howxu.mmcr.internal.port;

/**
 * MMCE fluid hatch sizes in millibuckets.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum FluidHatchSize {
    TINY("tiny", 100),
    SMALL("small", 400),
    NORMAL("normal", 1000),
    REINFORCED("reinforced", 2000),
    BIG("big", 4500),
    HUGE("huge", 8000),
    LUDICROUS("ludicrous", 16000),
    VACUUM("vacuum", 32000);

    private final String id;
    private final int capacity;

    FluidHatchSize(String id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public String id() {
        return id;
    }

    public int capacity() {
        return capacity;
    }
}
