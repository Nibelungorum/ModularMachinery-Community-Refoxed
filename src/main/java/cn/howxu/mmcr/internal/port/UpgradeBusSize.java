package cn.howxu.mmcr.internal.port;

/**
 * Fixed storage tiers for standalone upgrade buses.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum UpgradeBusSize {
    NORMAL("normal", 4),
    REINFORCED("reinforced", 6),
    ELITE("elite", 9),
    SUPER("super", 12),
    ULTIMATE("ultimate", 16);

    private final String id;
    private final int slots;

    UpgradeBusSize(String id, int slots) {
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
