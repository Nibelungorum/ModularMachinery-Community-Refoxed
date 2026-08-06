package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack, float chance, List<String> tags) implements MachineRequirement {

    public FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack) {
        this(io, fluid, amount, stack, 1F, List.of());
    }

    public FluidRequirement(RecipeModifier.IOType io, @Nullable FluidIngredient fluid, int amount, FluidStack stack, List<String> tags) {
        this(io, fluid, amount, stack, 1F, tags);
    }

    public FluidRequirement {
        stack = stack == null ? FluidStack.EMPTY : stack.copy();
        chance = MachineOutput.clampChance(chance);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    @Override
    public String type() {
        return "fluid";
    }

    @Override
    public boolean simulate(RecipeCraftingContext context, int requirementIndex) {
        return io == RecipeModifier.IOType.INPUT
                ? context.simulateFluidInput(requirementIndex, this)
                : context.simulateFluidOutput(requirementIndex, this);
    }

    @Override
    public boolean commit(RecipeCraftingContext context, int requirementIndex) {
        return io == RecipeModifier.IOType.INPUT
                ? context.collectFluidInputRoute(requirementIndex)
                : context.collectFluidOutputRoute(requirementIndex);
    }
}
