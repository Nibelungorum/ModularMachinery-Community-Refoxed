package cn.howxu.mmcr.api.recipe;

/** Immutable public energy value.
 * @author howxu <dev@howxu.cn>
 */
public record EnergyInput(long fePerTick) {
    public EnergyInput {
        if (fePerTick < 1L) {
            throw new IllegalArgumentException("Energy per tick must be in [1, Long.MAX_VALUE]");
        }
    }
}
