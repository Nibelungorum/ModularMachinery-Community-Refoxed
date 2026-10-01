package cn.howxu.mmcr.publicapi.recipe.component;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced unordered subset condition. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ListCondition extends ComponentCondition { List<ComponentCondition> values(); }
