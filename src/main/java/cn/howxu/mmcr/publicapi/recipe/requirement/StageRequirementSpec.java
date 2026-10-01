package cn.howxu.mmcr.publicapi.recipe.requirement;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced stage requirement. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface StageRequirementSpec extends RequirementSpec { int minStage(); }
