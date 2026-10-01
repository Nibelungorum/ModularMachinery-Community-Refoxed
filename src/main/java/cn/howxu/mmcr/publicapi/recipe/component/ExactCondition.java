package cn.howxu.mmcr.publicapi.recipe.component;
import com.mojang.serialization.Dynamic;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced exact condition. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ExactCondition extends ComponentCondition { Dynamic<?> value(); }
