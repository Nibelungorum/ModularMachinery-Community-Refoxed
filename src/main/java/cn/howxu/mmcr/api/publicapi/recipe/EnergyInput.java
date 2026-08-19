package cn.howxu.mmcr.api.publicapi.recipe;

/** Immutable public energy value.
 * @author howxu <dev@howxu.cn>
 */
public record EnergyInput(long fePerTick) {
    public EnergyInput {
        if (fePerTick < 1 || fePerTick > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Energy per tick must be in [1, Integer.MAX_VALUE]");
        }
    }
}
