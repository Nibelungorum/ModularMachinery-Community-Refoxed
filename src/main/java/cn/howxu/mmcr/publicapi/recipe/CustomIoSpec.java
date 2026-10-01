package cn.howxu.mmcr.publicapi.recipe;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced registered IO declaration; payload getter copies. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface CustomIoSpec { ResourceLocation typeId(); IoDirection io(); default IoDirection ioType() { return io(); } JsonElement payload(); }
