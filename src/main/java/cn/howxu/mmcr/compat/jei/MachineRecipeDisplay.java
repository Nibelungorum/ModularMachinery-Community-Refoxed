package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.SmartInterfaceModifier;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.MekanismTemperatureDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.DynamicOps;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import java.util.stream.Stream;

/**
 * Immutable JEI-facing view of a machine recipe.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRecipeDisplay(
        MachineRecipe recipe,
        ResourceLocation recipeId,
        ResourceLocation recipePoolId,
        int durationTicks,
        List<ItemInputDisplay> itemInputs,
        List<ItemOutputDisplay> itemOutputs,
        List<FluidInputDisplay> fluidInputs,
        List<ChemicalInputDisplay> chemicalInputs,
        List<FluidOutputDisplay> fluidOutputs,
        List<EnergyIngredient> energyInputs,
        List<EnergyIngredient> energyOutputs,
        List<MachineOutput> outputs,
        List<SmartInterfaceDisplay> smartInterfaceInputs,
        List<SmartInterfaceDisplay> smartInterfaceOutputs,
        List<SmartInterfaceModifierDisplay> smartInterfaceModifiers,
        Set<ResourceLocation> requiredHostIds
) {

    public MachineRecipeDisplay {
        requiredHostIds = sortedHostIds(requiredHostIds);
    }

    public static MachineRecipeDisplay from(MachineRecipe recipe) {
        return from(recipe, null);
    }

    public static MachineRecipeDisplay from(MachineRecipe recipe, RegistryAccess registryAccess) {
        DynamicOps<JsonElement> componentOps = registryAccess == null
                ? JsonOps.INSTANCE
                : RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        List<ItemInputDisplay> itemInputs = new ArrayList<>();
        List<FluidInputDisplay> fluidInputs = new ArrayList<>();
        List<ChemicalInputDisplay> chemicalInputs = new ArrayList<>();
        List<EnergyIngredient> energyInputs = new ArrayList<>();
        List<EnergyIngredient> energyOutputs = new ArrayList<>();
        List<SmartInterfaceDisplay> smartInterfaceInputs = new ArrayList<>();
        List<SmartInterfaceDisplay> smartInterfaceOutputs = new ArrayList<>();
        List<SmartInterfaceModifierDisplay> smartInterfaceModifiers = List.of();
        List<MachineRequirement> requirements = recipe.runtimeRequirements();
        for (var requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                DataComponentPredicateSet components = item.components();
                List<ItemStack> baseStacks = safeItems(item.item())
                        .map(holder -> new ItemStack(holder.value(), item.count()))
                        .toList();
                itemInputs.add(new ItemInputDisplay(item.item(), baseStacks, item.count(), item.consumeChance(), components, componentOps));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.INPUT) {
                fluidInputs.add(new FluidInputDisplay(fluid.fluid(), fluid.amount(), fluid.consumeChance()));
            } else if (requirement instanceof LoadedChemicalRequirement chemical && chemical.io() == RecipeModifier.IOType.INPUT) {
                chemicalInputs.add(new ChemicalInputDisplay(chemical.ingredient(), chemical.ingredient().amount(), chemical.consumeChance()));
            } else if (requirement instanceof EnergyRequirement energy) {
                EnergyIngredient ingredient = new EnergyIngredient(energy.fePerTick(), energy.io() == RecipeModifier.IOType.INPUT);
                if (ingredient.input()) energyInputs.add(ingredient);
                else energyOutputs.add(ingredient);
            } else if (requirement instanceof SmartInterfaceRequirement smartInterface
                    && smartInterface.io() == RecipeModifier.IOType.INPUT) {
                smartInterfaceDisplay(smartInterface).ifPresent(smartInterfaceInputs::add);
            }
        }

        List<MachineOutput> outputs = new ArrayList<>();
        List<ItemOutputDisplay> itemOutputs = new ArrayList<>();
        List<FluidOutputDisplay> fluidOutputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                ItemStack stack = item.stack(componentOps);
                itemOutputs.add(new ItemOutputDisplay(stack, item.chance()));
                outputs.add(new MachineOutput.ItemOutput(stack, item.chance()));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                FluidStack stack = fluid.stack().copy();
                fluidOutputs.add(new FluidOutputDisplay(stack, fluid.chance()));
                outputs.add(new MachineOutput.FluidOutput(stack, fluid.chance()));
            } else if (requirement instanceof SmartInterfaceRequirement smartInterface
                    && smartInterface.io() == RecipeModifier.IOType.OUTPUT) {
                smartInterfaceDisplay(smartInterface).ifPresent(smartInterfaceOutputs::add);
            }
        }
        return new MachineRecipeDisplay(
                recipe,
                recipe.id(),
                recipe.recipePoolId(),
                recipe.tickTime(),
                List.copyOf(itemInputs),
                List.copyOf(itemOutputs),
                List.copyOf(fluidInputs),
                List.copyOf(chemicalInputs),
                List.copyOf(fluidOutputs),
                List.copyOf(energyInputs),
                List.copyOf(energyOutputs),
                List.copyOf(outputs),
                List.copyOf(smartInterfaceInputs),
                List.copyOf(smartInterfaceOutputs),
                List.copyOf(smartInterfaceModifiers),
                recipe.requiredHostIds()
        );
    }

    private static Set<ResourceLocation> sortedHostIds(Set<ResourceLocation> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        return ids.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet));
    }

    private static Stream<Holder<Item>> safeItems(Ingredient ingredient) {
        try {
            return ingredient.items();
        } catch (UnsupportedOperationException ignored) {
            return Stream.empty();
        }
    }

    public List<Component> tooltips() {
        Stream<Component> interfaceTooltips = Stream.concat(
                        Stream.concat(smartInterfaceInputs.stream(), smartInterfaceOutputs.stream())
                                .map(SmartInterfaceDisplay::tooltip),
                        smartInterfaceModifiers.stream().map(SmartInterfaceModifierDisplay::tooltip));
        return Stream.concat(minimumTemperature().stream().mapToObj(MachineRecipeDisplay::minimumTemperatureLabel), interfaceTooltips)
                .toList();
    }

    public OptionalDouble minimumTemperature() {
        return recipe.runtimeRequirements().stream()
                .filter(LoadedHeatRequirement.class::isInstance)
                .map(LoadedHeatRequirement.class::cast)
                .filter(requirement -> requirement.heat().kind() == HeatRequirement.Kind.MINIMUM_TEMPERATURE)
                .mapToDouble(requirement -> requirement.heat().value())
                .findFirst();
    }

    public OptionalDouble outputHeat() {
        return recipe.runtimeRequirements().stream()
                .filter(LoadedHeatRequirement.class::isInstance)
                .map(LoadedHeatRequirement.class::cast)
                .filter(requirement -> requirement.heat().kind() == HeatRequirement.Kind.OUTPUT_HEAT)
                .mapToDouble(requirement -> requirement.heat().value())
                .findFirst();
    }

    public static Component minimumTemperatureLabel(double kelvin) {
        var unit = MekanismTemperatureDisplay.configuredUnit();
        return Component.translatable("jei.mmcr.machine_recipe.mekanism_temperature",
                MekanismTemperatureDisplay.fromKelvin(kelvin, unit), MekanismTemperatureDisplay.symbol(unit));
    }

    public static Component outputHeatLabel(double kelvin) {
        var unit = MekanismTemperatureDisplay.configuredUnit();
        return Component.translatable("jei.mmcr.machine_recipe.heat_output",
                MekanismTemperatureDisplay.fromKelvin(kelvin, unit), MekanismTemperatureDisplay.symbol(unit));
    }

    /**
     * Converts effective recipe requirements through the registered JEI type adapters.
     * Unknown types remain visible as text entries so a newly registered requirement
     * cannot prevent its machine category from loading.
     */
    public List<JeiDisplayEntry> entries() {
        return recipe.runtimeRequirements().stream()
                .filter(requirement -> !(requirement instanceof EnergyRequirement)
                        && !(requirement instanceof LoadedHeatRequirement)
                        && !(requirement instanceof SmartInterfaceRequirement)
                        && !(requirement instanceof LevelRequirement)
                        && !(requirement instanceof StageRequirement))
                .map(requirement -> new RecipeIoEntry(
                        requirement.io() == RecipeModifier.IOType.INPUT
                                ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT,
                        requirement.type().id(), requirement, requirementAmount(requirement), requirementChance(requirement)))
                .flatMap(entry -> JeiIngredientAdapterRegistry.display(entry)
                        .map(Stream::of)
                        .orElseGet(() -> Stream.of(JeiIngredientAdapterRegistry.textEntry(entry))))
                .toList();
    }

    private static long requirementAmount(MachineRequirement requirement) {
        if (requirement instanceof ItemRequirement item) {
            return item.io() == RecipeModifier.IOType.INPUT ? item.count() : item.stack().getCount();
        }
        if (requirement instanceof FluidRequirement fluid) {
            return fluid.io() == RecipeModifier.IOType.INPUT ? fluid.amount() : fluid.stack().getAmount();
        }
        if (requirement instanceof EnergyRequirement energy) return energy.fePerTick();
        if (requirement instanceof LoadedChemicalRequirement chemical) return chemical.ingredient().amount();
        return 1L;
    }

    private static float requirementChance(MachineRequirement requirement) {
        if (requirement instanceof ItemRequirement item) {
            return item.io() == RecipeModifier.IOType.INPUT ? item.consumeChance() : item.chance();
        }
        if (requirement instanceof FluidRequirement fluid) {
            return fluid.io() == RecipeModifier.IOType.INPUT ? fluid.consumeChance() : fluid.chance();
        }
        if (requirement instanceof LoadedChemicalRequirement chemical) {
            return chemical.io() == RecipeModifier.IOType.INPUT ? chemical.consumeChance() : chemical.chance();
        }
        return 1F;
    }

    private static Optional<SmartInterfaceDisplay> smartInterfaceDisplay(SmartInterfaceRequirement requirement) {
        Component displayType = Component.translatable("mmcr.smart_interface.type." + requirement.interfaceType());
        String value = valueText(requirement.minValue(), requirement.maxValue());
        boolean input = requirement.io() == RecipeModifier.IOType.INPUT;
        Component tooltip = Component.translatable(input
                ? "jei.mmcr.smart_interface.requirement.input"
                : "jei.mmcr.smart_interface.requirement.output", displayType, value);
        return Optional.of(new SmartInterfaceDisplay(displayType, value, input, tooltip));
    }

    private static String valueText(float minValue, float maxValue) {
        String min = formatValue(minValue);
        if (Float.compare(minValue, maxValue) == 0) return min;
        return "[" + min + ", " + formatValue(maxValue) + "]";
    }

    private static String formatValue(float value) {
        return Float.toString(value);
    }

    public record SmartInterfaceDisplay(Component type, String value, boolean input, Component tooltip) {
        public Component label() {
            return Component.translatable("mmcr.smart_interface.value", type, value);
        }
    }

    public record SmartInterfaceModifierDisplay(String type, String target, String scope, boolean chance,
            float minValue, float maxValue, float atMin, float atMax, RecipeModifier.Operation operation) {
        static SmartInterfaceModifierDisplay from(SmartInterfaceModifier modifier) {
            return new SmartInterfaceModifierDisplay(modifier.interfaceType(), modifier.target(), modifier.scope(),
                    modifier.affectsChance(), modifier.minValue(), modifier.maxValue(), modifier.atMin(),
                    modifier.atMax(), modifier.operation());
        }

        public String label() {
            return type + " -> " + target;
        }

        public Component tooltip() {
            return Component.literal("Smart interface " + type + " modifies " + target + " " + scope
                    + (chance ? " chance" : "") + ": [" + minValue + ", " + maxValue + "] -> ["
                    + atMin + ", " + atMax + "] " + operation);
        }
    }

    /**
     * Recipe data for one fluid input. The {@code ingredient} field is intentionally left
     * nullable to mirror {@link FluidRequirement#fluid()} so empty / unbound tag ingredients
     * remain representable; the JEI renderer skips slots whose ingredient is null or empty.
     */
    public record FluidInputDisplay(
            @org.jspecify.annotations.Nullable FluidIngredient ingredient,
            int amount,
            float consumeChance
    ) {
        public FluidInputDisplay {
            if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
            if (!Float.isFinite(consumeChance) || consumeChance < 0F || consumeChance > 1F) {
                throw new IllegalArgumentException("consumeChance must be in [0, 1]");
            }
        }
    }

    /**
     * Recipe data for one chemical input. Used by the JEI overlay/tooltip code path
     * so the consume-chance semantics from {@link LoadedChemicalRequirement} survive the
     * conversion from internal {@link MachineRequirement} to {@link JeiDisplayEntry}.
     */
    public record ChemicalInputDisplay(
            cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient ingredient,
            long amount,
            float consumeChance
    ) {
        public ChemicalInputDisplay {
            Objects.requireNonNull(ingredient, "ingredient");
            if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
            if (!Float.isFinite(consumeChance) || consumeChance < 0F || consumeChance > 1F) {
                throw new IllegalArgumentException("consumeChance must be in [0, 1]");
            }
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
            componentOps = componentOps == null ? JsonOps.INSTANCE : componentOps;
        }

        public ItemInputDisplay(List<ItemStack> baseStacks, int count, float consumeChance) {
            this(null, baseStacks, count, consumeChance, DataComponentPredicateSet.EMPTY, JsonOps.INSTANCE);
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

    /**
     * Recipe data for one fluid output, retaining the chance required by JEI overlays and tooltips.
     */
    public record FluidOutputDisplay(FluidStack stack, float chance) {
        public FluidOutputDisplay {
            stack = stack == null ? FluidStack.EMPTY : stack.copy();
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
