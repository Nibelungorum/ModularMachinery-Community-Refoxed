package cn.howxu.mmcr.publicapi.recipe;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced energy rate in FE/t. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface EnergyRateSpec { long fePerTick(); }
