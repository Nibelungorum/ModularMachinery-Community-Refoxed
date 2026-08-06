package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import mezz.jei.api.recipe.types.IRecipeType;

/**
 * JEI recipe type constants for MMCR.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiMachineRecipeTypes {

    public static final IRecipeType<MachineRecipeDisplay> MACHINE_RECIPE =
            IRecipeType.create(MMCR.id("machine_recipe"), MachineRecipeDisplay.class);

    private JeiMachineRecipeTypes() {
    }
}
