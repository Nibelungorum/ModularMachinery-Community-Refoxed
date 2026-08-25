package cn.howxu.mmcr.internal.port;

/**
 * Extended item bus slot counts.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ExtendedItemBusSize {
    BASIC("basic", 2),
    ADVANCED("advanced", 4),
    REINFORCED("reinforced", 6),
    ULTIMATE("ultimate", 8);

    private final String id;
    private final int slots;

    ExtendedItemBusSize(String id, int slots) {
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
