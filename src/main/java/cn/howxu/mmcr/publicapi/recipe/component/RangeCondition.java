package cn.howxu.mmcr.publicapi.recipe.component;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced inclusive range condition. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface RangeCondition extends ComponentCondition { double min(); double max(); }
