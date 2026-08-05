package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RequirementFailure;
import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
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

    @Override
    public boolean simulate(RecipeCraftingContext context, int requirementIndex) {
        if (EnergyRecipeIo.canConsumeInputs(context.taggedEnergyStorages(tags), fePerTick, 1)) return true;
        long available = context.taggedAvailableEnergy(tags);
        context.setRequirementFailure(RecipeCraftingContext.FAILURE_MISSING_ENERGY, new RequirementFailure(
                requirementIndex,
                RequirementFailure.Kind.MISSING_ENERGY,
                fePerTick,
                available,
                Math.max(0, fePerTick - available),
                context.energyComponentTraces(tags),
                List.of()
        ));
        return false;
    }

    @Override
    public boolean commit(RecipeCraftingContext context, int requirementIndex) {
        return true;
    }

    @Override
    public boolean ioTick(RecipeCraftingContext context, int requirementIndex) {
        if (EnergyRecipeIo.consumeInputs(context.taggedEnergyStorages(tags), fePerTick, 1)) return true;
        long available = context.taggedAvailableEnergy(tags);
        context.setRequirementFailure(RecipeCraftingContext.FAILURE_MISSING_ENERGY, new RequirementFailure(
                requirementIndex,
                RequirementFailure.Kind.MISSING_ENERGY,
                fePerTick,
                available,
                Math.max(0, fePerTick - available),
                context.energyComponentTraces(tags),
                List.of()
        ));
        return false;
    }
}
