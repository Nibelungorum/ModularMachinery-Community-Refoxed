package cn.howxu.mmcr.api.publicapi.recipe;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.Objects;

/** Immutable public fluid input value.
 * @author howxu <dev@howxu.cn>
 */
public record FluidInput(FluidIngredient ingredient, int amount) {
    public FluidInput {
        Objects.requireNonNull(ingredient, "ingredient");
        if (amount < 1) throw new IllegalArgumentException("Fluid input amount must be positive");
    }

    public FluidInput(Fluid fluid, int amount) {
        this(FluidIngredient.of(Objects.requireNonNull(fluid, "fluid")), amount);
    }
}
