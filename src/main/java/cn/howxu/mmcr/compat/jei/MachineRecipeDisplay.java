package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.machine.SmartInterfaceModifier;
import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.DynamicOps;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        Set<ResourceLocation> requiredHostIds,
        MachineRecipeData data
) {

    public MachineRecipeDisplay {
        requiredHostIds = MachineRecipeData.sortedHostIds(requiredHostIds);
        itemInputs = List.copyOf(itemInputs);
        itemOutputs = List.copyOf(itemOutputs);
        fluidInputs = List.copyOf(fluidInputs);
        chemicalInputs = List.copyOf(chemicalInputs);
        fluidOutputs = List.copyOf(fluidOutputs);
        energyInputs = List.copyOf(energyInputs);
        energyOutputs = List.copyOf(energyOutputs);
        outputs = List.copyOf(outputs);
        smartInterfaceInputs = List.copyOf(smartInterfaceInputs);
        smartInterfaceOutputs = List.copyOf(smartInterfaceOutputs);
        smartInterfaceModifiers = List.copyOf(smartInterfaceModifiers);
        data = Objects.requireNonNull(data, "data");
    }

    /** Retains the existing public constructor for integrations constructing JEI views directly. */
    public MachineRecipeDisplay(MachineRecipe recipe, ResourceLocation recipeId, ResourceLocation recipePoolId,
            int durationTicks, List<ItemInputDisplay> itemInputs, List<ItemOutputDisplay> itemOutputs,
            List<FluidInputDisplay> fluidInputs, List<ChemicalInputDisplay> chemicalInputs,
            List<FluidOutputDisplay> fluidOutputs, List<EnergyIngredient> energyInputs,
            List<EnergyIngredient> energyOutputs, List<MachineOutput> outputs,
            List<SmartInterfaceDisplay> smartInterfaceInputs, List<SmartInterfaceDisplay> smartInterfaceOutputs,
            List<SmartInterfaceModifierDisplay> smartInterfaceModifiers, Set<ResourceLocation> requiredHostIds) {
        this(recipe, recipeId, recipePoolId, durationTicks, itemInputs, itemOutputs, fluidInputs, chemicalInputs,
                fluidOutputs, energyInputs, energyOutputs, outputs, smartInterfaceInputs, smartInterfaceOutputs,
                smartInterfaceModifiers, requiredHostIds, MachineRecipeData.from(recipe, null));
    }

    public static MachineRecipeDisplay from(MachineRecipe recipe) {
        return from(recipe, null);
    }

    public static MachineRecipeDisplay from(MachineRecipe recipe, RegistryAccess registryAccess) {
        MachineRecipeData data = MachineRecipeData.from(recipe, registryAccess);
        List<ItemInputDisplay> itemInputs = new ArrayList<>();
        List<ItemOutputDisplay> itemOutputs = new ArrayList<>();
        List<FluidInputDisplay> fluidInputs = new ArrayList<>();
        List<FluidOutputDisplay> fluidOutputs = new ArrayList<>();
        List<ChemicalInputDisplay> chemicalInputs = new ArrayList<>();
        List<MachineOutput> outputs = new ArrayList<>();
        List<EnergyIngredient> energyInputs = new ArrayList<>();
        List<EnergyIngredient> energyOutputs = new ArrayList<>();
        List<SmartInterfaceDisplay> smartInterfaceInputs = new ArrayList<>();
        List<SmartInterfaceDisplay> smartInterfaceOutputs = new ArrayList<>();
        for (var entry : data.entries()) {
            MachineRequirement requirement = entry.requirement();
            boolean input = entry.io() == IOType.INPUT;
            if (requirement instanceof ItemRequirement item) {
                if (input) itemInputs.add(new ItemInputDisplay(item.item(), entry.items(), item.count(),
                        entry.chance(), entry.components(), data.componentOps()));
                else {
                    ItemStack stack = entry.items().getFirst();
                    itemOutputs.add(new ItemOutputDisplay(stack, entry.chance()));
                    outputs.add(new MachineOutput.ItemOutput(stack, entry.chance()));
                }
            } else if (requirement instanceof FluidRequirement fluid) {
                if (input) fluidInputs.add(new FluidInputDisplay(fluid.fluid(), fluid.amount(), entry.chance()));
                else {
                    FluidStack stack = entry.fluids().getFirst();
                    fluidOutputs.add(new FluidOutputDisplay(stack, entry.chance()));
                    outputs.add(new MachineOutput.FluidOutput(stack, entry.chance()));
                }
            } else if (requirement instanceof LoadedChemicalRequirement chemical && input) {
                chemicalInputs.add(new ChemicalInputDisplay(chemical.ingredient(), entry.amount(), entry.chance()));
            }
        }
        for (MachineRequirement requirement : data.requirements()) {
            if (requirement instanceof EnergyRequirement energy) {
                EnergyIngredient ingredient = new EnergyIngredient(energy.fePerTick(), energy.io() == RecipeModifier.IOType.INPUT);
                if (ingredient.input()) energyInputs.add(ingredient);
                else energyOutputs.add(ingredient);
            } else if (requirement instanceof SmartInterfaceRequirement smart) {
                var row = MachineRecipeData.smartInterfaceMetadata(smart);
                SmartInterfaceDisplay display = new SmartInterfaceDisplay(
                        Component.translatable("mmcr.smart_interface.type." + smart.interfaceType()),
                        MachineRecipeData.smartInterfaceValue(smart.minValue(), smart.maxValue()),
                        smart.io() == IOType.INPUT, row.tooltip().getFirst());
                if (display.input()) smartInterfaceInputs.add(display);
                else smartInterfaceOutputs.add(display);
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
                List.of(),
                data.requiredHostIds(),
                data
        );
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
        return data.minimumTemperature();
    }

    public OptionalDouble outputHeat() {
        return data.outputHeat();
    }

    public List<StressDisplay> stressInputs() { return stressDisplays(IOType.INPUT); }

    public List<StressDisplay> stressOutputs() { return stressDisplays(IOType.OUTPUT); }

    private List<StressDisplay> stressDisplays(IOType io) {
        return data.requirements().stream()
                .filter(StressRequirement.class::isInstance)
                .map(StressRequirement.class::cast)
                .filter(requirement -> requirement.io() == io)
                .map(requirement -> new StressDisplay(requirement.stress(), requirement.minRpm(),
                        requirement.rpm(), requirement.tags()))
                .toList();
    }

    /** Stress metadata uses minimum input RPM or specified output RPM; no ingredient slots are allocated.
     * @author howxu <dev@howxu.cn>
     */
    public record StressDisplay(double baseStress, double minRpm, double rpm, List<String> tags) {
        public StressDisplay { tags = List.copyOf(tags); }

        public Component label(boolean input) {
            return MachineRecipeData.stressMetadata(baseStress, minRpm, rpm, input).text();
        }

        public List<Component> tooltip(boolean input) {
            return MachineRecipeData.stressMetadata(baseStress, minRpm, rpm, input).tooltip();
        }
    }

    public List<AirDisplay> airInputs() { return airDisplays(IOType.INPUT); }

    public List<AirDisplay> airOutputs() { return airDisplays(IOType.OUTPUT); }

    private List<AirDisplay> airDisplays(IOType io) {
        return data.requirements().stream()
                .filter(AirRequirement.class::isInstance)
                .map(AirRequirement.class::cast)
                .filter(requirement -> requirement.io() == io)
                .map(requirement -> new AirDisplay(requirement.airPerTick(), requirement.minPressure(), requirement.tags()))
                .toList();
    }

    /** Air is the resource; minimum pressure is an input condition, including zero-rate inputs.
     * @author howxu <dev@howxu.cn>
     */
    public record AirDisplay(long airPerTick, float minPressure, List<String> tags) {
        public AirDisplay { tags = List.copyOf(tags); }

        public Component label(boolean input) {
            return MachineRecipeData.airMetadata(airPerTick, minPressure, input).text();
        }

        public List<Component> tooltip(boolean input) {
            return MachineRecipeData.airMetadata(airPerTick, minPressure, input).tooltip();
        }
    }

    public static Component minimumTemperatureLabel(double kelvin) {
        return MachineRecipeData.temperatureLabel(kelvin, true);
    }

    public static Component outputHeatLabel(double kelvin) {
        return MachineRecipeData.temperatureLabel(kelvin, false);
    }

    /**
     * Converts effective recipe requirements through the registered JEI type adapters.
     * Unknown types remain visible as text entries so a newly registered requirement
     * cannot prevent its machine category from loading.
     */
    public List<JeiDisplayEntry> entries() {
        return data.entries().stream()
                .map(entry -> new RecipeIoEntry(
                        entry.io() == IOType.INPUT
                                ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT,
                        entry.typeId(), entry.requirement(), entry.amount(), entry.chance()))
                .flatMap(entry -> JeiIngredientAdapterRegistry.display(entry)
                        .map(Stream::of)
                        .orElseGet(() -> Stream.of(JeiIngredientAdapterRegistry.textEntry(entry))))
                .toList();
    }

    public record SmartInterfaceDisplay(Component type, String value, boolean input, Component tooltip) {
        public SmartInterfaceDisplay {
            type = ComponentSnapshots.copy(type);
            tooltip = ComponentSnapshots.copy(tooltip);
        }

        @Override public Component type() { return ComponentSnapshots.copy(type); }
        @Override public Component tooltip() { return ComponentSnapshots.copy(tooltip); }

        public Component label() {
            return Component.translatable("mmcr.smart_interface.value", type(), value);
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
            @Nullable FluidIngredient ingredient,
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
            List<ItemStack> candidates = stacks();
            return !components.isEmpty() && (candidates.isEmpty() || components.hasNonExactValues()
                    || candidates.stream().anyMatch(stack -> !components.matches(stack, componentOps)));
        }

        @Override public List<ItemStack> baseStacks() { return baseStacks.stream().map(ItemStack::copy).toList(); }
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

        @Override public ItemStack stack() { return stack.copy(); }
    }

    /**
     * Recipe data for one fluid output, retaining the chance required by JEI overlays and tooltips.
     */
    public record FluidOutputDisplay(FluidStack stack, float chance) {
        public FluidOutputDisplay {
            stack = stack == null ? FluidStack.EMPTY : stack.copy();
            chance = MachineOutput.clampChance(chance);
        }

        @Override public FluidStack stack() { return stack.copy(); }
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
