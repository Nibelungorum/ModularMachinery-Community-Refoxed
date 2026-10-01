package cn.howxu.mmcr.publicapi.recipe.requirement;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
/** Library-produced fluid requirement; stack getter copies. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface FluidRequirementSpec extends RequirementSpec {
    @Nullable FluidIngredient fluid(); @Nullable FluidIngredient ingredient(); int amount(); FluidStack stack(); float chance(); float consumeChance();
}
