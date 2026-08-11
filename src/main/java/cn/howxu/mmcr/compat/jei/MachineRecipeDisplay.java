package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Immutable JEI-facing view of a machine recipe.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRecipeDisplay(
        MachineRecipe recipe,
        Identifier recipeId,
        Identifier machineId,
        int durationTicks,
        List<ItemInputDisplay> itemInputs,
        List<ItemOutputDisplay> itemOutputs,
        List<FluidIngredient> fluidInputs,
        List<Integer> fluidInputAmounts,
        List<FluidStack> fluidOutputs,
        List<EnergyIngredient> energyInputs,
        List<EnergyIngredient> energyOutputs,
        List<MachineOutput> outputs
) {

    public static MachineRecipeDisplay from(MachineRecipe recipe) {
        List<ItemInputDisplay> itemInputs = new ArrayList<>();
        List<FluidIngredient> fluidInputs = new ArrayList<>();
        List<Integer> fluidInputAmounts = new ArrayList<>();
        List<EnergyIngredient> energyInputs = new ArrayList<>();
        List<EnergyIngredient> energyOutputs = new ArrayList<>();

        for (var requirement : recipe.runtimeRequirements()) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                DataComponentPredicateSet components = item.components();
                List<ItemStack> baseStacks = item.item().items()
                        .map(holder -> new ItemStack(holder.value(), item.count()))
                        .toList();
                itemInputs.add(new ItemInputDisplay(baseStacks, item.count(), item.consumeChance(), components));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.INPUT) {
                fluidInputs.add(fluid.fluid());
                fluidInputAmounts.add(fluid.amount());
            } else if (requirement instanceof EnergyRequirement energy) {
                EnergyIngredient ingredient = new EnergyIngredient(energy.fePerTick(), energy.io() == RecipeModifier.IOType.INPUT);
                if (ingredient.input()) energyInputs.add(ingredient);
                else energyOutputs.add(ingredient);
            }
        }

        List<MachineOutput> outputs = recipe.runtimeMachineOutputs();
        List<ItemOutputDisplay> itemOutputs = new ArrayList<>();
        List<FluidStack> fluidOutputs = new ArrayList<>();
        for (MachineOutput output : outputs) {
            if (output instanceof MachineOutput.ItemOutput item) {
                itemOutputs.add(new ItemOutputDisplay(item.stack(), item.chance()));
            } else if (output instanceof MachineOutput.FluidOutput fluid) {
                fluidOutputs.add(fluid.stack().copy());
            }
        }

        return new MachineRecipeDisplay(
                recipe,
                recipe.id(),
                recipe.machineId(),
                recipe.tickTime(),
                List.copyOf(itemInputs),
                List.copyOf(itemOutputs),
                List.copyOf(fluidInputs),
                List.copyOf(fluidInputAmounts),
                List.copyOf(fluidOutputs),
                List.copyOf(energyInputs),
                List.copyOf(energyOutputs),
                List.copyOf(outputs)
        );
    }

    /**
     * Recipe data for one item input. Mirrors the vanilla anvil recipe layout: the slot
     * builder takes pre-built {@link ItemStack}s, while the recipe's data constraints live
     * here as a {@link DataComponentPredicateSet}. {@link #stacks()} is what gets handed to
     * JEI; it copies every {@code baseStack} and writes the constraints onto the copy via
     * {@link DataComponentPredicateSet#applyTo(ItemStack)}, the same {@code stack.set(...)}
     * path the anvil plugin uses.
     */
    public record ItemInputDisplay(
            List<ItemStack> baseStacks,
            int count,
            float consumeChance,
            DataComponentPredicateSet components
    ) {
        public ItemInputDisplay {
            baseStacks = baseStacks.stream().map(ItemStack::copy).toList();
            components = components == null ? DataComponentPredicateSet.EMPTY : components;
        }

        public ItemInputDisplay(List<ItemStack> baseStacks, int count, float consumeChance) {
            this(baseStacks, count, consumeChance, DataComponentPredicateSet.EMPTY);
        }

        public List<ItemStack> stacks() {
            return baseStacks.stream()
                    .map(stack -> {
                        ItemStack copy = stack.copy();
                        components.applyTo(copy);
                        return copy;
                    })
                    .toList();
        }

        public boolean hasUnexportedComponentConstraints() {
            return !components.isEmpty() && components.exactPatch().isEmpty();
        }
    }

    /**
     * Recipe data for one item output. Unlike the input side, the output stack already
     * carries its full component patch (custom name, enchantments, etc.) straight from the
     * recipe definition. The wrapper exists to keep the JEI data path symmetric with
     * {@link ItemInputDisplay} and to keep the chance for chanced outputs alongside the
     * stack.
     * <p>
     * Hand the stack to JEI via {@code IRecipeSlotBuilder#add(ItemStack)} rather than
     * {@code addItemStacks}. On this version of JEI, routing an item with an empty
     * component patch through the list path leaves the output slot blank; the direct
     * {@code add} call renders correctly regardless of whether the stack carries data.
     */
    public record ItemOutputDisplay(ItemStack stack, float chance) {
        public ItemOutputDisplay {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            chance = MachineOutput.clampChance(chance);
        }
    }

    static String describeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "<empty>";
        String name = stack.getItem().builtInRegistryHolder().getRegisteredName();
        String components = stack.getComponentsPatch().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
        return name + " x" + stack.getCount() + (components.length() > 2 ? " patch=" + components : "");
    }
}
