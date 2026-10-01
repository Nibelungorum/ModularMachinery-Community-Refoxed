package cn.howxu.mmcr.publicapi.recipe.modifier;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced parallelization flag. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ParallelizationModifierSpec extends ModifierSpec { boolean value(); }
