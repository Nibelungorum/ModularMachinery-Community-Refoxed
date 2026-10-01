package cn.howxu.mmcr.publicapi.recipe;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced fluid output declaration; stack getter copies. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface FluidOutputSpec { FluidStack stack(); float chance(); }
