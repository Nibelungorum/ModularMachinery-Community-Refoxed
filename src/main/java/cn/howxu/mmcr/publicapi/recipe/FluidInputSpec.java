package cn.howxu.mmcr.publicapi.recipe;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced fluid input declaration. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface FluidInputSpec { FluidIngredient ingredient(); int amount(); float consumeChance(); }
