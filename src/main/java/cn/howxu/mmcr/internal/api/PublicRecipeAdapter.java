package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.publicapi.machine.LevelRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.FluidInput;
import cn.howxu.mmcr.api.publicapi.recipe.FluidOutput;
import cn.howxu.mmcr.api.publicapi.recipe.ItemInput;
import cn.howxu.mmcr.api.publicapi.recipe.ItemOutput;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** Internal conversion boundary for public recipe declarations.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicRecipeAdapter {
    private PublicRecipeAdapter() {
    }

    public static MachineRecipe toRecipe(MachineRecipeDefinition definition) {
        List<MachineIngredient> inputs = new ArrayList<>();
        for (ItemInput input : definition.itemInputs()) {
            inputs.add(new MachineIngredient.ItemIngredient(input.ingredient(), input.count(), input.components(), input.consumeChance()));
        }
        for (FluidInput input : definition.fluidInputs()) {
            inputs.add(new MachineIngredient.FluidIngredient(input.ingredient(), input.amount()));
        }
        for (var input : definition.energyInputs()) {
            inputs.add(new MachineIngredient.EnergyIngredient((int) input.fePerTick()));
        }
        List<ItemStack> outputs = definition.itemOutputs().stream().map(ItemOutput::stack).toList();
        List<FluidStack> fluidOutputs = definition.fluidOutputs().stream().map(FluidOutput::stack).toList();
        List<MachineRequirement> requirements = new ArrayList<>();
        for (Object value : definition.requirements()) {
            requirements.add(toRequirement(value));
        }
        return new MachineRecipe(definition.id(), definition.machineId(), definition.tickTime(), inputs, outputs,
                definition.modifiers(), definition.priority(), definition.maxThreads(), definition.cancelRecipeOnPerTickFailure(),
                fluidOutputs, requirements, definition.parallelized(), definition.levelRequirements().stream()
                        .map(PublicRecipeAdapter::toInternalLevel).toList(), definition.allowPartialOutputs(), definition.requiredHostIds());
    }

    private static MachineRequirement toRequirement(Object value) {
        if (value instanceof MachineRequirement requirement) return requirement;
        if (value instanceof MachineRecipeBuilder.SmartInterface smart) {
            return new SmartInterfaceRequirement(smart.io(), smart.interfaceType(), smart.minValue(), smart.maxValue());
        }
        throw new IllegalArgumentException("Unsupported public recipe requirement: " + value);
    }

    private static cn.howxu.mmcr.api.recipe.LevelRequirement toInternalLevel(LevelRequirement level) {
        return new cn.howxu.mmcr.api.recipe.LevelRequirement(level.typeId(), level.levelId());
    }
}
