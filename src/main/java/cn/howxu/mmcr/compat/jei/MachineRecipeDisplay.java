package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.SmartInterfaceModifier;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
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
        List<SmartInterfaceDisplay> smartInterfaceOutputs,
        List<SmartInterfaceModifierDisplay> smartInterfaceModifiers
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
        List<SmartInterfaceModifierDisplay> smartInterfaceModifiers = registration == null ? List.of()
                : registration.smartInterfaceModifiers().stream().map(SmartInterfaceModifierDisplay::from).toList();
        List<MachineRequirement> requirements = recipe.runtimeRequirements();
        for (var requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                DataComponentPredicateSet components = item.components();
                List<ItemStack> baseStacks = safeItems(item.item())
                        .map(holder -> new ItemStack(holder.value(), item.count()))
                        .toList();
                itemInputs.add(new ItemInputDisplay(item.item(), baseStacks, item.count(), item.consumeChance(), components, componentOps));
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
                List.copyOf(smartInterfaceOutputs),
                List.copyOf(smartInterfaceModifiers)
        );
    }

    private static java.util.stream.Stream<Holder<Item>> safeItems(Ingredient ingredient) {
        try {
            return ingredient.items();
        } catch (UnsupportedOperationException ignored) {
            return java.util.stream.Stream.empty();
        }
    }

    public List<Component> tooltips() {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(smartInterfaceInputs.stream(), smartInterfaceOutputs.stream())
                                .map(SmartInterfaceDisplay::tooltip),
                        smartInterfaceModifiers.stream().map(SmartInterfaceModifierDisplay::tooltip))
                .toList();
    }

    private static java.util.Optional<SmartInterfaceDisplay> smartInterfaceDisplay(SmartInterfaceType type,
            SmartInterfaceRequirement requirement) {
        if (type == null) return java.util.Optional.empty();
        Component displayType = Component.translatable(type.translationKey());
        String value = valueText(type.valueType(), requirement.minValue(), requirement.maxValue());
        boolean input = requirement.io() == RecipeModifier.IOType.INPUT;
        Component tooltip = Component.translatable(input
                ? "jei.mmcr.smart_interface.requirement.input"
                : "jei.mmcr.smart_interface.requirement.output", displayType, value);
        return java.util.Optional.of(new SmartInterfaceDisplay(displayType, value, input, tooltip));
    }

    private static String valueText(SmartInterfaceType.ValueType valueType, float minValue, float maxValue) {
        String min = formatValue(valueType, minValue);
        if (Float.compare(minValue, maxValue) == 0) return min;
        return min + " - " + formatValue(valueType, maxValue);
    }

    private static String formatValue(SmartInterfaceType.ValueType valueType, float value) {
        return valueType == SmartInterfaceType.ValueType.INTEGER && value == Math.rint(value)
                ? Integer.toString((int) value)
                : Float.toString(value);
    }

    public record SmartInterfaceDisplay(Component type, String value, boolean input, Component tooltip) {
        public Component label() {
            return Component.translatable("mmcr.smart_interface.value", type, value);
        }
    }

    public record SmartInterfaceModifierDisplay(String type, String target, RecipeModifier.IOType io, boolean chance,
            float minValue, float maxValue, float atMin, float atMax, RecipeModifier.Operation operation) {
        static SmartInterfaceModifierDisplay from(SmartInterfaceModifier modifier) {
            return new SmartInterfaceModifierDisplay(modifier.interfaceType(), modifier.target(), modifier.io(),
                    modifier.affectsChance(), modifier.minValue(), modifier.maxValue(), modifier.atMin(),
                    modifier.atMax(), modifier.operation());
        }

        public String label() {
            return type + " -> " + target;
        }

        public Component tooltip() {
            return Component.literal("Smart interface " + type + " modifies " + target + " " + io.getKey()
                    + (chance ? " chance" : "") + ": [" + minValue + ", " + maxValue + "] -> ["
                    + atMin + ", " + atMax + "] " + operation);
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
            Ingredient ingredient,
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
            this(null, baseStacks, count, consumeChance, DataComponentPredicateSet.EMPTY, com.mojang.serialization.JsonOps.INSTANCE);
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
