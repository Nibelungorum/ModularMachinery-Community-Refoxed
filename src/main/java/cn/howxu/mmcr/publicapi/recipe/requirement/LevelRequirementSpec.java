package cn.howxu.mmcr.publicapi.recipe.requirement;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced machine level requirement. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface LevelRequirementSpec extends RequirementSpec { ResourceLocation typeId(); ResourceLocation levelId(); }
