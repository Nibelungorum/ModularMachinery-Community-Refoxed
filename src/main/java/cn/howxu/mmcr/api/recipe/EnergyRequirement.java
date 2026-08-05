package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.helper.CraftCheck;
import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;

/**
 * @author howxu <dev@howxu.cn>
 */
public record EnergyRequirement(int fePerTick) implements MachineRequirement {

    @Override
    public String type() {
        return "energy";
    }

    @Override
    public String describe() {
        return fePerTick + "FE/t";
    }

    @Override
    public boolean matches(ProcessingComponent component) {
        return contextContainerIsEnergyInput(component);
    }

    @Override
    public CraftCheck simulate(RecipeCraftingContext context) {
        if (EnergyRecipeIo.canConsumeInputs(context.energyStorages(), fePerTick, 1)) {
            return CraftCheck.success();
        }
        return CraftCheck.failure("Missing " + describe() + " (short " + fePerTick + "; searched " + context.energyComponentSummary() + ")");
    }

    @Override
    public boolean commit(RecipeCraftingContext context) {
        return true;
    }

    @Override
    public boolean ioTick(RecipeCraftingContext context) {
        return EnergyRecipeIo.consumeInputs(context.energyStorages(), fePerTick, 1);
    }

    private static boolean contextContainerIsEnergyInput(ProcessingComponent component) {
        return component.getContainer() instanceof cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
    }
}
