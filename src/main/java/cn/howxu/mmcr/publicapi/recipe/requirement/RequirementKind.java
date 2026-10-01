package cn.howxu.mmcr.publicapi.recipe.requirement;
import cn.howxu.mmcr.publicapi.recipe.extension.TypePresentation;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Typed library-produced registration handle; payload returns a defensive extension copy.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface RequirementKind<R> { ResourceLocation id(); TypePresentation presentation(); Optional<R> payload(RequirementSpec requirement); }
