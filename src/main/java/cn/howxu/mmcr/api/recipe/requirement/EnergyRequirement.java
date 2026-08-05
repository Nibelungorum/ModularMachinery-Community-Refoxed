package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

/**
 * @author howxu <dev@howxu.cn>
 */
public record EnergyRequirement(int fePerTick) implements MachineRequirement {

    @Override
    public String type() {
        return "energy";
    }

    @Override
    public RecipeModifier.IOType io() {
        return RecipeModifier.IOType.INPUT;
    }
}
