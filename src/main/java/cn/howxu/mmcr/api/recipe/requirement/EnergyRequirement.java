package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record EnergyRequirement(int fePerTick, List<String> tags) implements MachineRequirement {

    public EnergyRequirement(int fePerTick) {
        this(fePerTick, List.of());
    }

    public EnergyRequirement {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    @Override
    public String type() {
        return "energy";
    }

    @Override
    public RecipeModifier.IOType io() {
        return RecipeModifier.IOType.INPUT;
    }
}
