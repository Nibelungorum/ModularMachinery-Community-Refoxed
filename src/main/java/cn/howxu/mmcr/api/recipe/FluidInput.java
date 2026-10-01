package cn.howxu.mmcr.api.recipe;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.Objects;

/** Immutable public fluid input value.
 * @author howxu <dev@howxu.cn>
 */
public record FluidInput(FluidIngredient ingredient, int amount, float consumeChance) {
    public FluidInput {
        Objects.requireNonNull(ingredient, "ingredient");
        if (amount < 1) throw new IllegalArgumentException("Fluid input amount must be positive");
        if (!Float.isFinite(consumeChance) || consumeChance < 0F || consumeChance > 1F) {
            throw new IllegalArgumentException("consumeChance must be in [0, 1]");
        }
    }

    public FluidInput(FluidIngredient ingredient, int amount) {
        this(ingredient, amount, 1F);
    }

    public FluidInput(Fluid fluid, int amount) {
        this(FluidIngredient.of(Objects.requireNonNull(fluid, "fluid")), amount, 1F);
    }
}