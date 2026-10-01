package cn.howxu.mmcr.publicapi.recipe;
import cn.howxu.mmcr.publicapi.recipe.extension.TypePresentation;
import cn.howxu.mmcr.publicapi.runtime.OutputView;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Typed library-produced output registration handle; payload returns a defensive extension copy.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface OutputKind<O> { ResourceLocation id(); String serializedId(); TypePresentation presentation(); Optional<O> payload(OutputView output); }
