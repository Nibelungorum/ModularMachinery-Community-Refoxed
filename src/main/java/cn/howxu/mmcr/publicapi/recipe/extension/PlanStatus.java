package cn.howxu.mmcr.publicapi.recipe.extension;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced execution status, not a second status registry. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface PlanStatus { ResourceLocation id(); ResourceLocation source(); Map<String, String> details(); }
