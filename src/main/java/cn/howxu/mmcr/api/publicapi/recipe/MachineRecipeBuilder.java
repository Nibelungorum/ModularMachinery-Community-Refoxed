package cn.howxu.mmcr.api.publicapi.recipe;

import cn.howxu.mmcr.api.publicapi.machine.LevelRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;
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
    public MachineRecipeBuilder outputFluid(Fluid fluid, int amount) { return requirement(FluidRequirement.output(new FluidOutput(fluid, amount))); }
    public MachineRecipeBuilder inputEnergy(long fePerTick) { return requirement(new EnergyRequirement(RecipeIo.INPUT, fePerTick)); }
    public MachineRecipeBuilder outputEnergy(long fePerTick) { return requirement(new EnergyRequirement(RecipeIo.OUTPUT, fePerTick)); }
    public MachineRecipeBuilder outputItem(Item item, int count) { return requirement(ItemRequirement.output(new ItemOutput(item, count))); }
    public MachineRecipeBuilder outputItem(ItemStack stack) { return requirement(ItemRequirement.output(new ItemOutput(stack))); }
    public MachineRecipeBuilder outputItem(ItemStack stack, DataComponentPredicateSet components) { return requirement(ItemRequirement.output(new ItemOutput(stack, components))); }
    public MachineRecipeBuilder outputChance(ItemStack stack, float chance) { return requirement(ItemRequirement.output(new ItemOutput(stack, chance))); }
    public MachineRecipeBuilder outputChance(ItemStack stack, float chance, DataComponentPredicateSet components) { return requirement(ItemRequirement.output(new ItemOutput(stack, chance, components))); }
    public MachineRecipeBuilder levelRequirement(Identifier typeId, Identifier levelId) { levelRequirements.add(new LevelRequirement(typeId, levelId)); return this; }
    public MachineRecipeBuilder requiredHost(Identifier hostId) { requiredHosts.add(new RequiredHost(hostId)); return this; }
    public MachineRecipeBuilder requirement(RecipeRequirement requirement) { if (requirement == null) throw new IllegalArgumentException("requirement null"); requirements.add(requirement); return this; }
    public MachineRecipeBuilder custom(CustomRecipeIo io) { return requirement(RecipeRequirement.custom(io.typeId(), io.ioType(), io.payload())); }
    public MachineRecipeBuilder smartInterface(SmartInterfaceRequirement requirement) {
        if (requirement == null) throw new IllegalArgumentException("smart interface requirement null");
        return requirement(requirement);
    }
    public MachineRecipeBuilder modifier(Identifier modifierId) { if (modifierId == null) throw new IllegalArgumentException("modifier id null"); modifierIds.add(modifierId); return this; }

    public MachineRecipeDefinition build() {
        List<RecipeRequirement> recipeRequirements = List.copyOf(requirements);
        List<ItemInput> itemInputs = recipeRequirements.stream().filter(ItemRequirement.class::isInstance)
                .map(ItemRequirement.class::cast).filter(requirement -> requirement.io().isInput())
                .map(requirement -> new ItemInput(requirement.ingredient(), requirement.count(),
                        requirement.components(), requirement.consumeChance())).toList();
        List<FluidInput> fluidInputs = recipeRequirements.stream().filter(FluidRequirement.class::isInstance)
                .map(FluidRequirement.class::cast).filter(requirement -> requirement.io().isInput())
                .map(requirement -> new FluidInput(requirement.ingredient(), requirement.amount())).toList();
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
        return new MachineRecipeDefinition(id, machineId, tickTime, priority, maxThreads,
                cancelRecipeOnPerTickFailure, parallelized, allowPartialOutputs, itemInputs, fluidInputs,
                energyInputs, itemOutputs, fluidOutputs, energyOutputs, recipeRequirements, modifierIds,
                levelRequirements, Set.copyOf(requiredHosts));
    }

}
