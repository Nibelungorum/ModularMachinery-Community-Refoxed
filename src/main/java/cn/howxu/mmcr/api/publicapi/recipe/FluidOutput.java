package cn.howxu.mmcr.api.publicapi.recipe;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;

/** Immutable public fluid output value.
 * @author howxu <dev@howxu.cn>
 */
public record FluidOutput(FluidStack stack, float chance) {
    public FluidOutput {
        Objects.requireNonNull(stack, "stack");
        stack = stack.copy();
        if (stack.isEmpty() || stack.getAmount() < 1) throw new IllegalArgumentException("Fluid output must not be empty");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) throw new IllegalArgumentException("chance must be in [0, 1]");
    }

    public FluidOutput(Fluid fluid, int amount) { this(new FluidStack(Objects.requireNonNull(fluid, "fluid"), amount), 1F); }
    public FluidOutput(FluidStack stack) { this(stack, 1F); }
    @Override public FluidStack stack() { return stack.copy(); }
}
