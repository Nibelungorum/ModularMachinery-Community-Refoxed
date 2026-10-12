package cn.howxu.mmcr.client.recipe;

import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalRecipeDisplaySupport;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.internal.client.RecipeInformationRegistry;
import cn.howxu.mmcr.util.ReadableNumber;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Immutable, viewer-independent snapshot of canonical runtime recipe requirements.
 * Build on the main thread so registry/tag candidates and component codecs are resolved together.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeData {
    public static final Comparator<MachineRecipeData> ORDER = Comparator
            .comparingInt((MachineRecipeData data) -> data.recipe().priority()).reversed()
            .thenComparing(MachineRecipeData::recipeId);

    private final MachineRecipe recipe;
    private final List<MachineRequirement> requirements;
    private final List<RecipeDisplayEntry> entries;
    private final List<RecipeMetadataRow> metadata;
    private final Set<ResourceLocation> requiredHostIds;
    private final DynamicOps<?> componentOps;

    private MachineRecipeData(MachineRecipe recipe, @Nullable RegistryAccess registries) {
        this.recipe = recipe;
        componentOps = registries == null ? JsonOps.INSTANCE : registries.createSerializationContext(JsonOps.INSTANCE);
        requirements = MachineRequirement.copyList(recipe.runtimeRequirements());
        requiredHostIds = sortedHostIds(recipe.requiredHostIds());
        List<RecipeDisplayEntry> resources = new ArrayList<>();
        List<RecipeMetadataRow> rows = new ArrayList<>();
        rows.add(row(Component.translatable("jei.mmcr.machine_recipe.duration", recipe.tickTime(),
                String.format(Locale.ROOT, "%.1f", recipe.tickTime() / 20.0F))));
        for (MachineRequirement requirement : requirements) {
            boolean input = requirement.io() == IOType.INPUT;
            if (requirement instanceof EnergyRequirement energy) {
                long total = energy.fePerTick() > 0 && recipe.tickTime() > Long.MAX_VALUE / energy.fePerTick()
                        ? Long.MAX_VALUE : energy.fePerTick() * recipe.tickTime();
                rows.add(row(input ? Component.translatable("jei.mmcr.machine_recipe.energy_in",
                        ReadableNumber.format(energy.fePerTick()), ReadableNumber.format(total))
                        : Component.translatable("jei.mmcr.machine_recipe.energy_out", ReadableNumber.format(energy.fePerTick()))));
            } else if (requirement instanceof LoadedHeatRequirement heat) {
                double kelvin = heat.heat().value();
                rows.add(RecipeMetadataRow.deferredText(() -> temperatureLabel(kelvin, input)));
            } else if (requirement instanceof StressRequirement stress) {
                rows.add(stressMetadata(stress.stress(), stress.minRpm(), stress.rpm(), input));
            } else if (requirement instanceof AirRequirement air) {
                rows.add(airMetadata(air.airPerTick(), air.minPressure(), input));
            } else if (requirement instanceof SmartInterfaceRequirement smart) {
                rows.add(smartInterfaceMetadata(smart));
            } else if (requirement instanceof LevelRequirement level) {
                rows.add(levelMetadata(level));
            } else if (requirement instanceof StageRequirement stage) {
                rows.add(row(Component.translatable("jei.mmcr.machine_recipe.minimum_stage", stage.minStage())));
            } else {
                RecipeDisplayEntry entry = resource(requirement);
                resources.add(entry);
                if (requirement instanceof ManaRequirement) rows.add(row(Component.translatable(input
                        ? "jei.mmcr.machine_recipe.mana_input" : "jei.mmcr.machine_recipe.mana_output",
                        ReadableNumber.format(entry.amount()))));
            }
        }
        if (!requiredHostIds.isEmpty()) {
            Component hosts = Component.empty();
            for (ResourceLocation id : requiredHostIds) {
                var machine = MachineDefinitions.getRegistration(id);
                if (!hosts.getSiblings().isEmpty()) hosts = hosts.copy().append(Component.literal(", "));
                hosts = hosts.copy().append(machine == null ? Component.literal(id.toString()) : machine.displayName());
            }
            rows.add(row(Component.translatable("jei.mmcr.machine_recipe.required_host", hosts)));
        }
        RecipeInformationRegistry.componentsFor(recipePoolId(), recipeId()).forEach(text -> rows.add(row(text)));
        entries = List.copyOf(resources);
        metadata = List.copyOf(rows);
    }

    public static MachineRecipeData from(MachineRecipe recipe, @Nullable RegistryAccess registries) {
        return new MachineRecipeData(recipe, registries);
    }

    public MachineRecipe recipe() { return recipe; }
    public ResourceLocation recipeId() { return recipe.id(); }
    public ResourceLocation recipePoolId() { return recipe.recipePoolId(); }
    public int durationTicks() { return recipe.tickTime(); }
    public List<RecipeDisplayEntry> entries() { return entries; }
    public Set<ResourceLocation> requiredHostIds() { return requiredHostIds; }
    public List<MachineRequirement> requirements() { return MachineRequirement.copyList(requirements); }
    public DynamicOps<?> componentOps() { return componentOps; }

    public List<RecipeMetadataRow> metadata() { return metadata; }

    public OptionalDouble minimumTemperature() { return heat(HeatRequirement.Kind.MINIMUM_TEMPERATURE); }
    public OptionalDouble outputHeat() { return heat(HeatRequirement.Kind.OUTPUT_HEAT); }

    private OptionalDouble heat(HeatRequirement.Kind kind) {
        return requirements.stream().filter(LoadedHeatRequirement.class::isInstance)
                .map(LoadedHeatRequirement.class::cast).filter(requirement -> requirement.heat().kind() == kind)
                .mapToDouble(requirement -> requirement.heat().value()).findFirst();
    }

    public static Component temperatureLabel(double kelvin, boolean input) {
        if (mekanismLoaded()) return ChemicalRecipeDisplaySupport.temperatureLabel(kelvin, input);
        return Component.translatable(input ? "jei.mmcr.machine_recipe.mekanism_temperature"
                : "jei.mmcr.machine_recipe.heat_output", kelvin, "K");
    }

    public static Set<ResourceLocation> sortedHostIds(Set<ResourceLocation> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        return ids.stream().sorted(Comparator.comparing(ResourceLocation::toString))
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet));
    }

    private RecipeDisplayEntry resource(MachineRequirement requirement) {
        boolean input = requirement.io() == IOType.INPUT;
        if (requirement instanceof ItemRequirement item) {
            DataComponentPredicateSet components = item.components();
            List<ItemStack> candidates = input ? safeItems(item.item()).filter(stack -> !stack.isEmpty()).map(stack -> {
                ItemStack copy = stack.copyWithCount(item.count());
                components.applyTo(copy, componentOps);
                return copy;
            }).toList() : List.of(item.stack(componentOps));
            boolean exact = !candidates.isEmpty() && !components.hasNonExactValues()
                    && candidates.stream().allMatch(stack -> components.matches(stack, componentOps));
            List<Component> tooltip = new ArrayList<>();
            if (!components.isEmpty()) {
                tooltip.add(Component.translatable("jei.mmcr.machine_recipe.component_constraints"));
                components.values().forEach((type, predicate) -> tooltip.add(Component.literal(
                        BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) + ": "
                                + ComponentPredicate.CODEC
                                .encodeStart(JsonOps.INSTANCE, predicate).getOrThrow())));
            }
            return entry(requirement, RecipeDisplayEntry.Kind.ITEM, input ? item.count() : item.stack().getCount(),
                    input ? item.consumeChance() : item.chance(), candidates, List.of(), List.of(), components, exact, tooltip);
        }
        if (requirement instanceof FluidRequirement fluid) {
            List<FluidStack> candidates = input ? safeFluids(fluid.fluid()).filter(stack -> !stack.isEmpty())
                    .map(stack -> stack.copyWithAmount(fluid.amount())).toList() : List.of(fluid.stack());
            return entry(requirement, RecipeDisplayEntry.Kind.FLUID, input ? fluid.amount() : fluid.stack().getAmount(),
                    input ? fluid.consumeChance() : fluid.chance(), List.of(), candidates, List.of(),
                    DataComponentPredicateSet.EMPTY, !candidates.isEmpty(), List.of());
        }
        if (requirement instanceof LoadedChemicalRequirement chemical) {
            if (mekanismLoaded()) return ChemicalRecipeDisplaySupport.display(chemical);
            return entry(requirement, RecipeDisplayEntry.Kind.CHEMICAL, chemical.ingredient().amount(),
                    input ? chemical.consumeChance() : chemical.chance(), List.of(), List.of(), List.of(),
                    DataComponentPredicateSet.EMPTY, false, List.of(Component.literal(chemical.ingredient().id().toString())));
        }
        long amount = requirement instanceof SourceRequirement source ? source.amount()
                : requirement instanceof ManaRequirement mana ? mana.amount() : 1L;
        Component tooltip = requirement instanceof SourceRequirement
                ? Component.translatable(input ? "jei.mmcr.machine_recipe.source_input"
                        : "jei.mmcr.machine_recipe.source_output", ReadableNumber.format(amount))
                : requirement instanceof ManaRequirement
                ? Component.translatable(input ? "jei.mmcr.machine_recipe.mana_input"
                        : "jei.mmcr.machine_recipe.mana_output", ReadableNumber.format(amount))
                : Component.literal(requirement.type().id().toString());
        return entry(requirement, requirement instanceof SourceRequirement ? RecipeDisplayEntry.Kind.SOURCE
                        : RecipeDisplayEntry.Kind.TEXT, amount, 1F, List.of(), List.of(), List.of(),
                DataComponentPredicateSet.EMPTY, false, List.of(tooltip));
    }

    private static RecipeDisplayEntry entry(MachineRequirement requirement, RecipeDisplayEntry.Kind kind,
            long amount, float chance, List<ItemStack> items, List<FluidStack> fluids, List<ResourceLocation> chemicals,
            DataComponentPredicateSet components, boolean exact, List<Component> tooltip) {
        return new RecipeDisplayEntry(requirement.io(), requirement.type().id(), kind, requirement,
                amount, chance, items, fluids, chemicals, components, exact, tooltip);
    }

    private static Stream<ItemStack> safeItems(@Nullable Ingredient ingredient) {
        if (ingredient == null) return Stream.empty();
        try {
            // NeoForge TagValue adds a display-only barrier for empty tags; filter the tag, not the item.
            Ingredient candidates = ingredient.isCustom() ? ingredient : Ingredient.fromValues(
                    Stream.of(ingredient.getValues()).filter(value -> !(value instanceof Ingredient.TagValue tag)
                            || BuiltInRegistries.ITEM.getTagOrEmpty(tag.tag()).iterator().hasNext()));
            return Stream.of(candidates.getItems());
        }
        catch (UnsupportedOperationException ignored) { return Stream.empty(); }
    }

    private static Stream<FluidStack> safeFluids(@Nullable FluidIngredient ingredient) {
        if (ingredient == null) return Stream.empty();
        try { return Stream.of(ingredient.getStacks()); }
        catch (UnsupportedOperationException ignored) { return Stream.empty(); }
    }

    private static boolean mekanismLoaded() { return ModList.get() != null && ModList.get().isLoaded("mekanism"); }
    private static RecipeMetadataRow row(Component text) { return new RecipeMetadataRow(text, List.of(), List.of()); }

    public static String smartInterfaceValue(float min, float max) {
        return Float.compare(min, max) == 0 ? Float.toString(min) : "[" + min + ", " + max + "]";
    }

    public static RecipeMetadataRow smartInterfaceMetadata(SmartInterfaceRequirement requirement) {
        Component type = Component.translatable("mmcr.smart_interface.type." + requirement.interfaceType());
        String value = smartInterfaceValue(requirement.minValue(), requirement.maxValue());
        return new RecipeMetadataRow(Component.translatable("mmcr.smart_interface.value", type, value),
                List.of(Component.translatable(requirement.io() == IOType.INPUT
                        ? "jei.mmcr.smart_interface.requirement.input" : "jei.mmcr.smart_interface.requirement.output", type, value)), List.of());
    }

    public static RecipeMetadataRow stressMetadata(double stress, double minRpm, double rpm, boolean input) {
        String total = BigDecimal.valueOf(input ? minRpm : Math.abs(rpm)).multiply(BigDecimal.valueOf(stress))
                .stripTrailingZeros().toPlainString();
        Component formula = Component.translatable("jei.mmcr.machine_recipe.stress_formula",
                BigDecimal.valueOf(input ? minRpm : rpm).stripTrailingZeros().toPlainString(),
                BigDecimal.valueOf(stress).stripTrailingZeros().toPlainString(), total);
        return new RecipeMetadataRow(Component.translatable(input ? "jei.mmcr.machine_recipe.stress_in"
                : "jei.mmcr.machine_recipe.stress_out", total), input ? List.of(formula,
                Component.translatable("jei.mmcr.machine_recipe.stress_minimum_note")) : List.of(formula), List.of());
    }

    public static RecipeMetadataRow airMetadata(long amount, float pressure, boolean input) {
        String value = new BigDecimal(Float.toString(pressure)).stripTrailingZeros().toPlainString();
        Component label = !input ? Component.translatable("jei.mmcr.machine_recipe.air_out", ReadableNumber.format(amount))
                : amount == 0 ? Component.translatable("jei.mmcr.machine_recipe.air_condition", value)
                : Component.translatable("jei.mmcr.machine_recipe.air_in", ReadableNumber.format(amount), value);
        Component tooltip = !input ? Component.translatable("jei.mmcr.machine_recipe.air_out.tooltip", amount)
                : amount == 0 ? Component.translatable("jei.mmcr.machine_recipe.air_condition.tooltip", value)
                : Component.translatable("jei.mmcr.machine_recipe.air_in.tooltip", amount, value);
        return new RecipeMetadataRow(label, List.of(tooltip), List.of());
    }

    private static RecipeMetadataRow levelMetadata(LevelRequirement requirement) {
        var type = MachineLevelRegistry.getType(requirement.typeId());
        MachineLevel required = MachineLevelRegistry.getLevel(requirement.levelId());
        List<ItemStack> icons = required == null ? List.of() : MachineLevelRegistry.levelsForType(requirement.typeId()).stream()
                .filter(level -> level.priority() >= required.priority()).sorted(Comparator.comparingInt(MachineLevel::priority)
                        .thenComparing(level -> level.id().toString())).map(level -> {
                    ItemStack stack = level.representative();
                    return stack.isEmpty() ? level.statePredicate().preferredState()
                            .map(state -> new ItemStack(state.getBlock().asItem())).orElse(ItemStack.EMPTY) : stack.copyWithCount(1);
                }).filter(stack -> !stack.isEmpty()).toList();
        return new RecipeMetadataRow(Component.translatable("jei.mmcr.machine_recipe.level_requirement",
                type == null ? Component.literal(requirement.typeId().toString()) : type.displayName(),
                Component.literal(requirement.levelId().toString())),
                List.of(Component.translatable("jei.mmcr.machine_recipe.minimum_level")), icons);
    }
}
