package cn.howxu.mmcr.api.publicapi.recipe;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.Objects;

/** Immutable public fluid recipe requirement.
 * @author howxu <dev@howxu.cn>
 */
public record FluidRequirement(RecipeIo io, FluidIngredient ingredient, int amount, FluidStack stack, float chance)
        implements RecipeRequirement {
    public FluidRequirement {
        Objects.requireNonNull(io, "io");
        if (io == RecipeIo.INPUT) {
            Objects.requireNonNull(ingredient, "ingredient");
            if (amount < 1) throw new IllegalArgumentException("Fluid input amount must be positive");
            stack = FluidStack.EMPTY;
            chance = 1F;
        } else {
            Objects.requireNonNull(stack, "stack");
            stack = stack.copy();
            if (stack.isEmpty() || stack.getAmount() < 1) throw new IllegalArgumentException("Fluid output must not be empty");
            ingredient = null;
            amount = 0;
            if (!Float.isFinite(chance) || chance < 0F || chance > 1F) throw new IllegalArgumentException("chance must be in [0, 1]");
        }
    }

    public static FluidRequirement input(FluidInput input) {
        return new FluidRequirement(RecipeIo.INPUT, input.ingredient(), input.amount(), FluidStack.EMPTY, 1F);
    }

    public static FluidRequirement output(FluidOutput output) {
        return new FluidRequirement(RecipeIo.OUTPUT, null, 0, output.stack(), output.chance());
    }

    @Override public FluidStack stack() { return stack.copy(); }
}
