package cn.howxu.mmcr.publicapi.recipe.requirement;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced canonical requirement view. Custom payloads use registered extensions.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface RequirementSpec { ResourceLocation kindId(); IoDirection io(); List<String> tags(); RequirementSpec copy(); }
