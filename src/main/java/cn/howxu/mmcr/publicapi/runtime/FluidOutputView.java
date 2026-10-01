package cn.howxu.mmcr.publicapi.runtime;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced runtime fluid output with a copying getter. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface FluidOutputView extends OutputView { FluidStack stack(); }
