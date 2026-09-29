package cn.howxu.mmcr.api.publicapi.recipe;

import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalOutput;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Fluent public machine recipe declaration builder.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeBuilder {
    private final ResourceLocation id;
    private ResourceLocation recipePoolId;
    private int tickTime = 1;
    private int priority;
    private int maxThreads = 1;
    private boolean cancelRecipeOnPerTickFailure;
    private boolean parallelized;
    private boolean allowPartialOutputs;
    private final List<RecipeRequirement> requirements = new ArrayList<>();
    private final List<CustomRecipeIo> customOutputs = new ArrayList<>();
    private final List<ResourceLocation> modifierIds = new ArrayList<>();
    private final List<RequiredHost> requiredHosts = new ArrayList<>();

    private MachineRecipeBuilder(ResourceLocation id) {
        this.id = id;
    }

    public static MachineRecipeBuilder recipe(ResourceLocation id) {
        if (id == null) throw new IllegalArgumentException("Recipe id must not be null");
        return new MachineRecipeBuilder(id);
    }

    public MachineRecipeBuilder recipePool(ResourceLocation recipePoolId) {
        this.recipePoolId = recipePoolId;
        return this;
    }

    public MachineRecipeBuilder duration(int duration) { if (duration < 1) throw new IllegalArgumentException("duration must be positive"); tickTime = duration; return this; }
    public MachineRecipeBuilder priority(int priority) { if (priority < 0) throw new IllegalArgumentException("priority must be non-negative"); this.priority = priority; return this; }
    public MachineRecipeBuilder maxThreads(int maxThreads) { if (maxThreads < 1) throw new IllegalArgumentException("maxThreads must be positive"); this.maxThreads = maxThreads; return this; }
    public MachineRecipeBuilder cancelIfPerTickFails(boolean value) { cancelRecipeOnPerTickFailure = value; return this; }
    public MachineRecipeBuilder parallelized(boolean value) { parallelized = value; return this; }
    public MachineRecipeBuilder allowPartialOutputs(boolean value) { allowPartialOutputs = value; return this; }

    public MachineRecipeBuilder inputItem(Item item, int count) { return requirement(ItemRequirement.input(new ItemInput(item, count))); }
    public MachineRecipeBuilder inputItem(Ingredient item, int count) { return requirement(ItemRequirement.input(new ItemInput(item, count))); }
    public MachineRecipeBuilder inputItem(TagKey<Item> tag, int count) {
        if (tag == null) throw new IllegalArgumentException("tag null");
        return requirement(ItemRequirement.input(new ItemInput(Ingredient.of(BuiltInRegistries.ITEM.get(tag)
                .orElseGet(() -> HolderSet.emptyNamed(BuiltInRegistries.ITEM, tag))), count)));
    }
    public MachineRecipeBuilder inputItemTag(TagKey<Item> tag, int count) { return inputItem(tag, count); }
    public MachineRecipeBuilder inputItem(Ingredient item, int count, DataComponentPredicateSet components, float consumeChance) {
        return requirement(ItemRequirement.input(new ItemInput(item, count, components, consumeChance)));
    }
    public MachineRecipeBuilder inputFluid(Fluid fluid, int amount) { return requirement(FluidRequirement.input(new FluidInput(fluid, amount))); }
    public MachineRecipeBuilder inputFluid(Fluid fluid, int amount, float consumeChance) {
        return requirement(FluidRequirement.input(new FluidInput(FluidIngredient.of(fluid), amount, consumeChance)));
    }
    public MachineRecipeBuilder outputFluid(Fluid fluid, int amount) { return requirement(FluidRequirement.output(new FluidOutput(fluid, amount))); }
    public MachineRecipeBuilder inputChemical(ResourceLocation id, long amount) {
        return custom(new CustomRecipeIo(MekanismPortFamilies.CHEMICAL, RecipeIo.INPUT,
                chemicalInputPayload(ChemicalIngredient.chemical(id, amount))));
    }
    public MachineRecipeBuilder inputChemical(ResourceLocation id, long amount, float consumeChance) {
        return custom(new CustomRecipeIo(MekanismPortFamilies.CHEMICAL, RecipeIo.INPUT,
                chemicalInputPayload(ChemicalIngredient.chemical(id, amount), consumeChance)));
    }
    public MachineRecipeBuilder inputChemicalTag(ResourceLocation id, long amount) {
        return custom(new CustomRecipeIo(MekanismPortFamilies.CHEMICAL, RecipeIo.INPUT,
                chemicalInputPayload(ChemicalIngredient.tag(id, amount))));
    }
    public MachineRecipeBuilder inputChemicalTag(ResourceLocation id, long amount, float consumeChance) {
        return custom(new CustomRecipeIo(MekanismPortFamilies.CHEMICAL, RecipeIo.INPUT,
                chemicalInputPayload(ChemicalIngredient.tag(id, amount), consumeChance)));
    }
    public MachineRecipeBuilder outputChemical(ResourceLocation id, long amount, float chance) {
        return custom(new CustomRecipeIo(MekanismPortFamilies.CHEMICAL, RecipeIo.OUTPUT,
                chemicalOutputPayload(ChemicalOutput.of(id, amount, chance))));
    }
    public MachineRecipeBuilder inputHeatTemperature(double temperature) {
        return custom(new CustomRecipeIo(MekanismPortFamilies.HEAT_TEMPERATURE, RecipeIo.INPUT,
                heatInputPayload(temperature)));
    }
    public MachineRecipeBuilder outputHeat(double heat) {
        return custom(new CustomRecipeIo(MekanismPortFamilies.HEAT, RecipeIo.OUTPUT,
                heatOutputPayload(heat)));
    }
    public MachineRecipeBuilder inputEnergy(long fePerTick) { return requirement(new EnergyRequirement(RecipeIo.INPUT, fePerTick)); }
    public MachineRecipeBuilder outputEnergy(long fePerTick) { return requirement(new EnergyRequirement(RecipeIo.OUTPUT, fePerTick)); }

    /**
     * Alias for {@link #inputEnergy(long)} measured in FE per tick.
     *
     * @param fePerTick required energy rate in FE/t
     * @return this builder
     * @author howxu <dev@howxu.cn>
     */
    public MachineRecipeBuilder iFEt(long fePerTick) { return inputEnergy(fePerTick); }

    /**
     * Alias for {@link #outputEnergy(long)} measured in FE per tick.
     *
     * @param fePerTick produced energy rate in FE/t
     * @return this builder
     * @author howxu <dev@howxu.cn>
     */
    public MachineRecipeBuilder oFEt(long fePerTick) { return outputEnergy(fePerTick); }
    public MachineRecipeBuilder outputItem(Item item, int count) { return requirement(ItemRequirement.output(new ItemOutput(item, count))); }
    public MachineRecipeBuilder outputItem(ItemStack stack) { return requirement(ItemRequirement.output(new ItemOutput(stack))); }
    public MachineRecipeBuilder outputItem(ItemStack stack, DataComponentPredicateSet components) { return requirement(ItemRequirement.output(new ItemOutput(stack, components))); }
    public MachineRecipeBuilder outputChance(ItemStack stack, float chance) { return requirement(ItemRequirement.output(new ItemOutput(stack, chance))); }
    public MachineRecipeBuilder outputChance(ItemStack stack, float chance, DataComponentPredicateSet components) { return requirement(ItemRequirement.output(new ItemOutput(stack, chance, components))); }
    public MachineRecipeBuilder levelRequirement(ResourceLocation typeId, ResourceLocation levelId) { return requirement(new LevelRequirement(typeId, levelId)); }
    public MachineRecipeBuilder stageRequirement(int minStage) { return requirement(new StageRequirement(minStage)); }
    public MachineRecipeBuilder requiredHost(ResourceLocation hostId) { requiredHosts.add(new RequiredHost(hostId)); return this; }
    public MachineRecipeBuilder requirement(RecipeRequirement requirement) { if (requirement == null) throw new IllegalArgumentException("requirement null"); requirements.add(requirement); return this; }
    public MachineRecipeBuilder custom(CustomRecipeIo io) {
        CustomRecipeIo validated = RecipeRequirement.custom(io.typeId(), io.ioType(), io.payload());
        if (validated.ioType().isInput() || OutputRegistry.typeFor(validated.typeId()) == null) {
            return requirement(validated);
        }
        customOutputs.add(validated);
        return this;
    }
    public MachineRecipeBuilder smartInterface(SmartInterfaceRequirement requirement) {
        if (requirement == null) throw new IllegalArgumentException("smart interface requirement null");
        return requirement(requirement);
    }
    public MachineRecipeBuilder modifier(ResourceLocation modifierId) { if (modifierId == null) throw new IllegalArgumentException("modifier id null"); modifierIds.add(modifierId); return this; }

    public MachineRecipeDefinition build() {
        if (recipePoolId == null) {
            throw new IllegalStateException("Recipe " + id + " must specify a recipe pool");
        }
        List<RecipeRequirement> recipeRequirements = List.copyOf(requirements);
        List<ItemInput> itemInputs = recipeRequirements.stream().filter(ItemRequirement.class::isInstance)
                .map(ItemRequirement.class::cast).filter(requirement -> requirement.io().isInput())
                .map(requirement -> new ItemInput(requirement.ingredient(), requirement.count(),
                        requirement.components(), requirement.consumeChance())).toList();
        List<FluidInput> fluidInputs = recipeRequirements.stream().filter(FluidRequirement.class::isInstance)
                .map(FluidRequirement.class::cast).filter(requirement -> requirement.io().isInput())
                .map(requirement -> new FluidInput(requirement.ingredient(), requirement.amount(), requirement.consumeChance())).toList();
        List<EnergyInput> energyInputs = recipeRequirements.stream().filter(EnergyRequirement.class::isInstance)
                .map(EnergyRequirement.class::cast).filter(requirement -> requirement.io().isInput())
                .map(requirement -> new EnergyInput(requirement.fePerTick())).toList();
        List<ItemOutput> itemOutputs = recipeRequirements.stream().filter(ItemRequirement.class::isInstance)
                .map(ItemRequirement.class::cast).filter(requirement -> !requirement.io().isInput())
                .map(requirement -> new ItemOutput(requirement.stack(), requirement.chance(), requirement.components())).toList();
        List<FluidOutput> fluidOutputs = recipeRequirements.stream().filter(FluidRequirement.class::isInstance)
                .map(FluidRequirement.class::cast).filter(requirement -> !requirement.io().isInput())
                .map(requirement -> new FluidOutput(requirement.stack(), requirement.chance())).toList();
        List<EnergyInput> energyOutputs = recipeRequirements.stream().filter(EnergyRequirement.class::isInstance)
                .map(EnergyRequirement.class::cast).filter(requirement -> !requirement.io().isInput())
                .map(requirement -> new EnergyInput(requirement.fePerTick())).toList();
        return new MachineRecipeDefinition(id, recipePoolId, tickTime, priority, maxThreads,
                cancelRecipeOnPerTickFailure, parallelized, allowPartialOutputs, itemInputs, fluidInputs,
                energyInputs, itemOutputs, fluidOutputs, energyOutputs, recipeRequirements, customOutputs, modifierIds,
                Set.copyOf(requiredHosts));
    }

    /**
     * Builds the canonical chemical input payload from a public {@link ChemicalIngredient}.
     *
     * @param ingredient validated chemical ingredient
     * @return immutable JSON payload for {@code RecipeApi.custom}
     * @author howxu <dev@howxu.cn>
     */
    public static JsonObject chemicalInputPayload(ChemicalIngredient ingredient) {
        if (ingredient == null) throw new IllegalArgumentException("ingredient must not be null");
        JsonObject payload = new JsonObject();
        payload.addProperty("type", MekanismPortFamilies.CHEMICAL.toString());
        payload.addProperty("kind", ingredient.kind().name().toLowerCase(Locale.ROOT));
        payload.addProperty("id", ingredient.id().toString());
        payload.addProperty("amount", ingredient.amount());
        payload.addProperty("io", RecipeIo.INPUT.name().toLowerCase(Locale.ROOT));
        return payload;
    }

    /**
     * Builds the canonical chemical input payload from a public {@link ChemicalIngredient} with a consume chance override.
     *
     * @param ingredient validated chemical ingredient
     * @param consumeChance consume chance value; emits {@code consume_chance} only when not 1F
     * @return immutable JSON payload for {@code RecipeApi.custom}
     * @author howxu <dev@howxu.cn>
     */
    public static JsonObject chemicalInputPayload(ChemicalIngredient ingredient, float consumeChance) {
        if (!Float.isFinite(consumeChance) || consumeChance < 0F || consumeChance > 1F) {
            throw new IllegalArgumentException("consumeChance must be in [0, 1]");
        }
        JsonObject payload = chemicalInputPayload(ingredient);
        if (consumeChance != 1F) payload.addProperty("consume_chance", consumeChance);
        return payload;
    }

    /**
     * Builds the canonical chemical output payload from a public {@link ChemicalOutput}.
     *
     * @param output validated chemical output
     * @return immutable JSON payload for {@code RecipeApi.custom}
     * @author howxu <dev@howxu.cn>
     */
    public static JsonObject chemicalOutputPayload(ChemicalOutput output) {
        if (output == null) throw new IllegalArgumentException("output must not be null");
        JsonObject payload = new JsonObject();
        payload.addProperty("type", MekanismPortFamilies.CHEMICAL.toString());
        payload.addProperty("id", output.id().toString());
        payload.addProperty("amount", output.amount());
        payload.addProperty("chance", output.chance());
        return payload;
    }

    /**
     * Builds the canonical minimum temperature payload for {@code mmcr:mekanism_heat_temperature}.
     *
     * @param temperature minimum required temperature
     * @return immutable JSON payload for {@code RecipeApi.custom}
     * @author howxu <dev@howxu.cn>
     */
    public static JsonObject heatInputPayload(double temperature) {
        return heatPayload(HeatRequirement.minimumTemperature(temperature),
                MekanismPortFamilies.HEAT_TEMPERATURE, RecipeIo.INPUT);
    }

    /**
     * Builds the canonical heat output payload for {@code mmcr:mekanism_heat}.
     *
     * @param heat produced heat value
     * @return immutable JSON payload for {@code RecipeApi.custom}
     * @author howxu <dev@howxu.cn>
     */
    public static JsonObject heatOutputPayload(double heat) {
        return heatPayload(HeatRequirement.outputHeat(heat),
                MekanismPortFamilies.HEAT, RecipeIo.OUTPUT);
    }

    /**
     * Builds the canonical heat payload for a typed {@link HeatRequirement} targeting a port family.
     *
     * @param requirement validated heat requirement
     * @param typeId target port family identifier
     * @param io recipe IO direction
     * @return immutable JSON payload for {@code RecipeApi.custom}
     * @author howxu <dev@howxu.cn>
     */
    public static JsonObject heatPayload(HeatRequirement requirement, ResourceLocation typeId, RecipeIo io) {
        if (requirement == null) throw new IllegalArgumentException("requirement must not be null");
        if (typeId == null) throw new IllegalArgumentException("typeId must not be null");
        if (io == null) throw new IllegalArgumentException("io must not be null");
        JsonObject payload = new JsonObject();
        payload.addProperty("type", typeId.toString());
        payload.addProperty("io", io.name().toLowerCase(Locale.ROOT));
        payload.addProperty("value", requirement.value());
        return payload;
    }

}
