package cn.howxu.mmcr.publicapi.recipe.requirement;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced energy requirement. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface EnergyRequirementSpec extends RequirementSpec { long fePerTick(); }
