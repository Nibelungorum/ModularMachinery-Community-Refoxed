package cn.howxu.mmcr.internal.port;

/**
 * MMCE item bus sizes.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ItemBusSize {
    TINY("tiny", 1),
    SMALL("small", 4),
    NORMAL("normal", 6),
    REINFORCED("reinforced", 9),
    BIG("big", 12),
    HUGE("huge", 16),
    LUDICROUS("ludicrous", 32);

    private final String id;
    private final int slots;

    ItemBusSize(String id, int slots) {
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
