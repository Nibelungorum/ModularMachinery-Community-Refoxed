package cn.howxu.mmcr.api.publicapi.machine;

/** Public processing adjustments supplied by a machine level.
 * @author howxu <dev@howxu.cn>
 */
public record LevelModifier(double durationMultiplier, double energyMultiplier,
                            double outputMultiplier, int parallelismBonus,
                            int factoryThreadBonus) {
    public static final LevelModifier IDENTITY = new LevelModifier(1D, 1D, 1D, 0, 0);

    public LevelModifier {
        if (durationMultiplier <= 0D || energyMultiplier <= 0D || outputMultiplier <= 0D) {
            throw new IllegalArgumentException("Machine level multipliers must be positive");
        }
    }
}
