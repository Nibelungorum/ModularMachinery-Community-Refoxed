package cn.howxu.mmcr.publicapi.recipe.requirement;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced smart-interface requirement. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface SmartInterfaceRequirementSpec extends RequirementSpec { String interfaceType(); float minValue(); float maxValue(); }
