package cn.howxu.mmcr.publicapi.recipe.modifier;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced registered modifier declaration. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ModifierBundle { List<ModifierSpec> modifiers(); }
