package cn.howxu.mmcr.api.publicapi.recipe;

import cn.howxu.mmcr.api.publicapi.machine.LevelRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.publicapi.recipe.component.ComponentPredicate;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Fluent public machine recipe declaration builder.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeBuilder {
    private final Identifier id;
    private final Identifier machineId;
    private int tickTime = 1;
    private int priority;
    private int maxThreads = 1;
    private boolean cancelRecipeOnPerTickFailure;
    private boolean parallelized;
    private boolean allowPartialOutputs;
    private final List<ItemInput> itemInputs = new ArrayList<>();
    private final List<FluidInput> fluidInputs = new ArrayList<>();
    private final List<EnergyInput> energyInputs = new ArrayList<>();
    private final List<ItemOutput> itemOutputs = new ArrayList<>();
    private final List<FluidOutput> fluidOutputs = new ArrayList<>();
    private final List<EnergyInput> energyOutputs = new ArrayList<>();
    private final List<RecipeRequirement> requirements = new ArrayList<>();
    private final List<Identifier> modifierIds = new ArrayList<>();
    private final List<LevelRequirement> levelRequirements = new ArrayList<>();
    private final List<RequiredHost> requiredHosts = new ArrayList<>();

    private MachineRecipeBuilder(Identifier id, Identifier machineId) {
        this.id = id;
        this.machineId = machineId;
    }

    public static MachineRecipeBuilder recipe(Identifier id, Identifier machineId) {
        if (id == null || machineId == null) throw new IllegalArgumentException("Recipe ids must not be null");
        return new MachineRecipeBuilder(id, machineId);
    }

    public MachineRecipeBuilder duration(int duration) { if (duration < 1) throw new IllegalArgumentException("duration must be positive"); tickTime = duration; return this; }
    public MachineRecipeBuilder priority(int priority) { if (priority < 0) throw new IllegalArgumentException("priority must be non-negative"); this.priority = priority; return this; }
    public MachineRecipeBuilder maxThreads(int maxThreads) { if (maxThreads < 1) throw new IllegalArgumentException("maxThreads must be positive"); this.maxThreads = maxThreads; return this; }
    public MachineRecipeBuilder cancelIfPerTickFails(boolean value) { cancelRecipeOnPerTickFailure = value; return this; }
    public MachineRecipeBuilder parallelized(boolean value) { parallelized = value; return this; }
    public MachineRecipeBuilder allowPartialOutputs(boolean value) { allowPartialOutputs = value; return this; }

    public MachineRecipeBuilder inputItem(Item item, int count) { itemInputs.add(new ItemInput(item, count)); return this; }
    public MachineRecipeBuilder inputItem(Ingredient item, int count) { itemInputs.add(new ItemInput(item, count)); return this; }
    public MachineRecipeBuilder inputItem(TagKey<Item> tag, int count) {
        if (tag == null) throw new IllegalArgumentException("tag null");
        itemInputs.add(new ItemInput(Ingredient.of(BuiltInRegistries.ITEM.get(tag)
                .orElseGet(() -> HolderSet.emptyNamed(BuiltInRegistries.ITEM, tag))), count));
        return this;
    }
    public MachineRecipeBuilder inputItemTag(TagKey<Item> tag, int count) { return inputItem(tag, count); }
    public MachineRecipeBuilder inputItem(Ingredient item, int count, DataComponentPredicateSet components, float consumeChance) {
        itemInputs.add(new ItemInput(item, count, components, consumeChance)); return this;
    }
    public MachineRecipeBuilder inputItem(Ingredient item, int count,
            cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet components, float consumeChance) {
        itemInputs.add(new ItemInput(item, count, toPublicComponents(components), consumeChance)); return this;
    }
    public MachineRecipeBuilder inputFluid(Fluid fluid, int amount) { fluidInputs.add(new FluidInput(fluid, amount)); return this; }
    public MachineRecipeBuilder outputFluid(Fluid fluid, int amount) { fluidOutputs.add(new FluidOutput(fluid, amount)); return this; }
    public MachineRecipeBuilder inputEnergy(long fePerTick) { energyInputs.add(new EnergyInput(fePerTick)); return this; }
    public MachineRecipeBuilder outputEnergy(long fePerTick) { energyOutputs.add(new EnergyInput(fePerTick)); return this; }
    public MachineRecipeBuilder outputItem(Item item, int count) { itemOutputs.add(new ItemOutput(item, count)); return this; }
    public MachineRecipeBuilder outputItem(ItemStack stack) { itemOutputs.add(new ItemOutput(stack)); return this; }
    public MachineRecipeBuilder outputItem(ItemStack stack, DataComponentPredicateSet components) { itemOutputs.add(new ItemOutput(stack, components)); return this; }
    public MachineRecipeBuilder outputChance(ItemStack stack, float chance) { itemOutputs.add(new ItemOutput(stack, chance)); return this; }
    public MachineRecipeBuilder outputChance(ItemStack stack, float chance, DataComponentPredicateSet components) { itemOutputs.add(new ItemOutput(stack, chance, components)); return this; }
    public MachineRecipeBuilder levelRequirement(Identifier typeId, Identifier levelId) { levelRequirements.add(new LevelRequirement(typeId, levelId)); return this; }
    public MachineRecipeBuilder requiredHost(Identifier hostId) { requiredHosts.add(new RequiredHost(hostId)); return this; }
    public MachineRecipeBuilder requirement(RecipeRequirement requirement) { if (requirement == null) throw new IllegalArgumentException("requirement null"); requirements.add(requirement); return this; }
    public MachineRecipeBuilder modifier(Identifier modifierId) { if (modifierId == null) throw new IllegalArgumentException("modifier id null"); modifierIds.add(modifierId); return this; }

    public MachineRecipeDefinition build() {
        List<RecipeRequirement> recipeRequirements = requirements.isEmpty() ? derivedRequirements() : requirements;
        return new MachineRecipeDefinition(id, machineId, tickTime, priority, maxThreads,
                cancelRecipeOnPerTickFailure, parallelized, allowPartialOutputs, itemInputs, fluidInputs,
                energyInputs, itemOutputs, fluidOutputs, energyOutputs, recipeRequirements, modifierIds,
                levelRequirements, Set.copyOf(requiredHosts));
    }

    private List<RecipeRequirement> derivedRequirements() {
        List<RecipeRequirement> derived = new ArrayList<>();
        itemInputs.forEach(input -> derived.add(ItemRequirement.input(input)));
        fluidInputs.forEach(input -> derived.add(FluidRequirement.input(input)));
        energyInputs.forEach(input -> derived.add(new EnergyRequirement(RecipeIo.INPUT, input.fePerTick())));
        itemOutputs.forEach(output -> derived.add(ItemRequirement.output(output)));
        fluidOutputs.forEach(output -> derived.add(FluidRequirement.output(output)));
        energyOutputs.forEach(output -> derived.add(new EnergyRequirement(RecipeIo.OUTPUT, output.fePerTick())));
        return derived;
    }

    private static DataComponentPredicateSet toPublicComponents(
            cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet components) {
        if (components.isEmpty()) return DataComponentPredicateSet.EMPTY;
        java.util.Map<Identifier, ComponentPredicate> values = new java.util.HashMap<>();
        components.values().forEach((type, predicate) -> values.put(
                BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type), toPublicPredicate(predicate)));
        return new DataComponentPredicateSet(values);
    }

    private static ComponentPredicate toPublicPredicate(
            cn.howxu.mmcr.api.recipe.component.ComponentPredicate predicate) {
        if (predicate instanceof cn.howxu.mmcr.api.recipe.component.ComponentPredicate.Exact exact) {
            return new ComponentPredicate.Exact(exact.value().convert(JsonOps.INSTANCE).getValue());
        }
        if (predicate instanceof cn.howxu.mmcr.api.recipe.component.ComponentPredicate.MapValue map) {
            java.util.Map<String, ComponentPredicate> values = new java.util.HashMap<>();
            map.values().forEach((key, value) -> values.put(key, toPublicPredicate(value)));
            return new ComponentPredicate.MapValue(values);
        }
        if (predicate instanceof cn.howxu.mmcr.api.recipe.component.ComponentPredicate.ListValue list) {
            return new ComponentPredicate.ListValue(list.values().stream().map(MachineRecipeBuilder::toPublicPredicate).toList());
        }
        if (predicate instanceof cn.howxu.mmcr.api.recipe.component.ComponentPredicate.Range range) {
            return new ComponentPredicate.Range(range.min(), range.max());
        }
        var text = (cn.howxu.mmcr.api.recipe.component.ComponentPredicate.TextValue) predicate;
        return new ComponentPredicate.TextValue(text.value().getString(),
                ComponentPredicate.TextMode.valueOf(text.mode().name()));
    }
}
