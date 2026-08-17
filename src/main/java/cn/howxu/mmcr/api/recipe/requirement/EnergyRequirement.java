package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record EnergyRequirement(RecipeModifier.IOType io, int fePerTick, List<String> tags) implements MachineRequirement {

    public EnergyRequirement(int fePerTick) {
        this(RecipeModifier.IOType.INPUT, fePerTick, List.of());
    }

    public EnergyRequirement(int fePerTick, List<String> tags) {
        this(RecipeModifier.IOType.INPUT, fePerTick, tags);
    }

    public EnergyRequirement(RecipeModifier.IOType io, int fePerTick) {
        this(io, fePerTick, List.of());
    }

    public EnergyRequirement {
        if (io == null) io = RecipeModifier.IOType.INPUT;
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    @Override
    public String type() {
        return "energy";
    }

    @Override
    public RecipeModifier.IOType io() {
        return io;
    }

    @Override
    public boolean simulate(RecipeCraftingContext context, int requirementIndex) {
        return io == RecipeModifier.IOType.INPUT
                ? context.simulateEnergyInput(requirementIndex, this)
                : context.simulateEnergyOutput(requirementIndex, this);
    }

    @Override
    public boolean commit(RecipeCraftingContext context, int requirementIndex) {
        return io == RecipeModifier.IOType.INPUT
                ? context.collectEnergyInputRoute(requirementIndex)
                : context.collectEnergyOutputRoute(requirementIndex);
    }

    @Override
    public boolean ioTick(RecipeCraftingContext context, int requirementIndex) {
        if (io == RecipeModifier.IOType.INPUT) {
            if (EnergyRecipeIo.consumeInputs(context.taggedEnergyStorages(tags), fePerTick, 1)) return true;
            return context.simulateEnergyInput(requirementIndex, this);
        }
        if (EnergyRecipeIo.produceOutputs(context.taggedEnergyOutputs(tags), fePerTick, 1)) return true;
        return context.simulateEnergyOutput(requirementIndex, this);
    }
}
