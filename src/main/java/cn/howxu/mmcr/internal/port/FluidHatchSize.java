package cn.howxu.mmcr.internal.port;

/**
 * MMCE fluid hatch sizes in millibuckets.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum FluidHatchSize {
    TINY("tiny", 1000L),
    SMALL("small", 4000L),
    NORMAL("normal", 8000L),
    REINFORCED("reinforced", 16000L),
    BIG("big", 3200L),
    HUGE("huge", 64000L),
    LUDICROUS("ludicrous", 128000L),
    VACUUM("vacuum", 256000L);

    private final String id;
    private final long capacity;

    FluidHatchSize(String id, long capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public String id() {
        return id;
    }

    public long capacity() {
        return capacity;
    }

}
