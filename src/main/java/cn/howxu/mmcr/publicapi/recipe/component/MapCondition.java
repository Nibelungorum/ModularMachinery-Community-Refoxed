package cn.howxu.mmcr.publicapi.recipe.component;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced map subset condition. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface MapCondition extends ComponentCondition { Map<String, ComponentCondition> values(); }
