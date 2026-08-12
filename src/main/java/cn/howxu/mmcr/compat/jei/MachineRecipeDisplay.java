package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
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
        List<MachineOutput> outputs,
        List<SmartInterfaceDisplay> smartInterfaceInputs,
        List<SmartInterfaceDisplay> smartInterfaceOutputs
) {

    public static MachineRecipeDisplay from(MachineRecipe recipe) {
        return from(recipe, null);
    }

    public static MachineRecipeDisplay from(MachineRecipe recipe, RegistryAccess registryAccess) {
        DynamicOps<com.google.gson.JsonElement> componentOps = registryAccess == null
                ? com.mojang.serialization.JsonOps.INSTANCE
                : RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registryAccess);
        List<ItemInputDisplay> itemInputs = new ArrayList<>();
        List<FluidIngredient> fluidInputs = new ArrayList<>();
        List<Integer> fluidInputAmounts = new ArrayList<>();
        List<EnergyIngredient> energyInputs = new ArrayList<>();
        List<EnergyIngredient> energyOutputs = new ArrayList<>();
        List<SmartInterfaceDisplay> smartInterfaceInputs = new ArrayList<>();
        List<SmartInterfaceDisplay> smartInterfaceOutputs = new ArrayList<>();
        var registration = MachineDefinitions.getRegistration(recipe.machineId());
        List<MachineRequirement> requirements = recipe.runtimeRequirements();
        for (var requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                DataComponentPredicateSet components = item.components();
                List<ItemStack> baseStacks = item.item().items()
                        .map(holder -> new ItemStack(holder.value(), item.count()))
                        .toList();
                itemInputs.add(new ItemInputDisplay(baseStacks, item.count(), item.consumeChance(), components, componentOps));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.INPUT) {
                fluidInputs.add(fluid.fluid());
                fluidInputAmounts.add(fluid.amount());
            } else if (requirement instanceof EnergyRequirement energy) {
                EnergyIngredient ingredient = new EnergyIngredient(energy.fePerTick(), energy.io() == RecipeModifier.IOType.INPUT);
                if (ingredient.input()) energyInputs.add(ingredient);
                else energyOutputs.add(ingredient);
            } else if (requirement instanceof SmartInterfaceRequirement smartInterface
                    && smartInterface.io() == RecipeModifier.IOType.INPUT) {
                smartInterfaceDisplay(registration == null ? null : registration.smartInterfaceTypes().get(smartInterface.interfaceType()),
                        smartInterface).ifPresent(smartInterfaceInputs::add);
            }
        }

        List<MachineOutput> outputs = new ArrayList<>();
        List<ItemOutputDisplay> itemOutputs = new ArrayList<>();
        List<FluidStack> fluidOutputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                ItemStack stack = item.stack(componentOps);
                itemOutputs.add(new ItemOutputDisplay(stack, item.chance()));
                outputs.add(new MachineOutput.ItemOutput(stack, item.chance()));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                FluidStack stack = fluid.stack().copy();
                fluidOutputs.add(stack);
                outputs.add(new MachineOutput.FluidOutput(stack, fluid.chance()));
            } else if (requirement instanceof SmartInterfaceRequirement smartInterface
                    && smartInterface.io() == RecipeModifier.IOType.OUTPUT) {
                smartInterfaceDisplay(registration == null ? null : registration.smartInterfaceTypes().get(smartInterface.interfaceType()),
                        smartInterface).ifPresent(smartInterfaceOutputs::add);
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
                List.copyOf(outputs),
                List.copyOf(smartInterfaceInputs),
                List.copyOf(smartInterfaceOutputs)
        );
    }

    public List<String> tooltips() {
        return java.util.stream.Stream.concat(smartInterfaceInputs.stream(), smartInterfaceOutputs.stream())
                .map(SmartInterfaceDisplay::tooltip).toList();
    }

    private static java.util.Optional<SmartInterfaceDisplay> smartInterfaceDisplay(SmartInterfaceType type,
            SmartInterfaceRequirement requirement) {
        if (type == null) return java.util.Optional.empty();
        String tooltip = standardSmartInterfaceText(requirement);
        if (!type.jeiTooltip().isBlank()) {
            Object[] arguments = requirement.minValue() == requirement.maxValue()
                    ? new Object[]{requirement.minValue()} : new Object[]{requirement.minValue(), requirement.maxValue()};
            if (arguments.length == type.jeiTooltipArgsCount()) {
                try {
                    tooltip = String.format(java.util.Locale.ROOT, type.jeiTooltip(), arguments);
                } catch (java.util.IllegalFormatException ignored) {
                    // Keep the standard localized fallback.
                }
            }
        }
        return java.util.Optional.of(new SmartInterfaceDisplay(requirement.interfaceType(), requirement.minValue(),
                requirement.maxValue(), requirement.io() == RecipeModifier.IOType.INPUT, tooltip));
    }

    private static String standardSmartInterfaceText(SmartInterfaceRequirement requirement) {
        return requirement.io() == RecipeModifier.IOType.INPUT
                ? "Smart interface " + requirement.interfaceType() + ": [" + requirement.minValue() + ", " + requirement.maxValue() + "]"
                : "Smart interface " + requirement.interfaceType() + ": " + requirement.minValue();
    }

    public record SmartInterfaceDisplay(String type, float minValue, float maxValue, boolean input, String tooltip) {
        public String label() {
            return input ? type + ": [" + minValue + ", " + maxValue + "]" : type + ": " + minValue;
        }
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
            DataComponentPredicateSet components,
            DynamicOps<?> componentOps
    ) {
        public ItemInputDisplay {
            baseStacks = baseStacks.stream().map(ItemStack::copy).toList();
            components = components == null ? DataComponentPredicateSet.EMPTY : components;
            componentOps = componentOps == null ? com.mojang.serialization.JsonOps.INSTANCE : componentOps;
        }

        public ItemInputDisplay(List<ItemStack> baseStacks, int count, float consumeChance) {
            this(baseStacks, count, consumeChance, DataComponentPredicateSet.EMPTY, com.mojang.serialization.JsonOps.INSTANCE);
        }

        public List<ItemStack> stacks() {
            return baseStacks.stream()
                    .map(stack -> {
                        ItemStack copy = stack.copy();
                        components.applyTo(copy, componentOps);
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
