package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * @author howxu <dev@howxu.cn>
 */
public record FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack) implements MachineRequirement {

    @Override
    public String type() {
        return "fluid";
    }
}
