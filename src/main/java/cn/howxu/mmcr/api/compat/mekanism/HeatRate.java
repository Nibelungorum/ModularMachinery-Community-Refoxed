package cn.howxu.mmcr.api.compat.mekanism;

/** Supplies the existing decimal heat-rate field of the controller UI, without linking a mod implementation.
 * @author howxu <dev@howxu.cn>
 */
public interface HeatRate {
    double outputHeatPerTick();
}
