package cn.howxu.mmcr.publicapi.recipe.extension;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced long-valued capability storage view. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface LongStore { long amount(); long capacity(); long transferLimit(); }
