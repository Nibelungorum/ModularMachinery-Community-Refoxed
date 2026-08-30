package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record EnergyRequirement(RecipeModifier.IOType io, int fePerTick, List<String> tags) implements MachineRequirement {
    public static final RequirementType<EnergyRequirement> TYPE = new RequirementType<>(MMCR.id("energy"));

    public EnergyRequirement(int fePerTick) {
        this(RecipeModifier.IOType.INPUT, fePerTick, List.of());
    }

    public EnergyRequirement(int fePerTick, List<String> tags) {
        this(RecipeModifier.IOType.INPUT, fePerTick, tags);
    }

    public EnergyRequirement(RecipeModifier.IOType io, int fePerTick) {
        this(io, fePerTick, List.of());
    }

    public EnergyRequirement(RecipeIo io, int fePerTick) {
        this(io == RecipeIo.OUTPUT ? RecipeModifier.IOType.OUTPUT : RecipeModifier.IOType.INPUT, fePerTick);
    }

    public EnergyRequirement {
        if (io == null) io = RecipeModifier.IOType.INPUT;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    @Override
    public RequirementType<EnergyRequirement> type() {
        return TYPE;
    }

}
