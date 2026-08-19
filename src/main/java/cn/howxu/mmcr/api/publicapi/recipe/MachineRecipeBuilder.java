package cn.howxu.mmcr.api.publicapi.recipe;

import cn.howxu.mmcr.api.publicapi.machine.LevelRequirement;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
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
    private final List<Object> requirements = new ArrayList<>();
    private final List<RecipeModifier> modifiers = new ArrayList<>();
    private final List<LevelRequirement> levelRequirements = new ArrayList<>();
    private final List<Identifier> requiredHostIds = new ArrayList<>();

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
    public MachineRecipeBuilder inputFluid(Fluid fluid, int amount) { fluidInputs.add(new FluidInput(fluid, amount)); return this; }
    public MachineRecipeBuilder outputFluid(Fluid fluid, int amount) { fluidOutputs.add(new FluidOutput(fluid, amount)); return this; }
    public MachineRecipeBuilder inputEnergy(long fePerTick) { energyInputs.add(new EnergyInput(fePerTick)); return this; }
    public MachineRecipeBuilder outputEnergy(long fePerTick) { energyOutputs.add(new EnergyInput(fePerTick)); return this; }
    public MachineRecipeBuilder outputItem(Item item, int count) { itemOutputs.add(new ItemOutput(item, count)); return this; }
    public MachineRecipeBuilder outputItem(ItemStack stack) { itemOutputs.add(new ItemOutput(stack)); return this; }
    public MachineRecipeBuilder outputChance(ItemStack stack, float chance) { itemOutputs.add(new ItemOutput(stack, chance)); return this; }
    public MachineRecipeBuilder levelRequirement(Identifier typeId, Identifier levelId) { levelRequirements.add(new LevelRequirement(typeId, levelId)); return this; }
    public MachineRecipeBuilder requiredHost(Identifier hostId) { if (hostId == null) throw new IllegalArgumentException("hostId null"); requiredHostIds.add(hostId); return this; }
    public MachineRecipeBuilder requirement(Object requirement) { if (requirement == null) throw new IllegalArgumentException("requirement null"); requirements.add(requirement); return this; }
    public MachineRecipeBuilder requirement(SmartInterface requirement) { if (requirement == null) throw new IllegalArgumentException("requirement null"); requirements.add(requirement); return this; }
    public MachineRecipeBuilder modifier(RecipeModifier modifier) { if (modifier == null) throw new IllegalArgumentException("modifier null"); modifiers.add(modifier); return this; }

    public MachineRecipeDefinition build() {
        if (requirements.isEmpty()) {
            for (ItemInput input : itemInputs) requirements.add(new ItemRequirement(RecipeModifier.IOType.INPUT,
                    input.ingredient(), input.count(), ItemStack.EMPTY, 1F, List.of(), input.components(), input.consumeChance()));
            for (FluidInput input : fluidInputs) requirements.add(new FluidRequirement(RecipeModifier.IOType.INPUT,
                    input.ingredient(), input.amount(), net.neoforged.neoforge.fluids.FluidStack.EMPTY));
            for (EnergyInput input : energyInputs) requirements.add(new EnergyRequirement(RecipeModifier.IOType.INPUT, (int) input.fePerTick()));
            for (ItemOutput output : itemOutputs) requirements.add(cn.howxu.mmcr.api.recipe.requirement.MachineRequirement.itemOutput(output.stack(), output.chance()));
            for (FluidOutput output : fluidOutputs) requirements.add(cn.howxu.mmcr.api.recipe.requirement.MachineRequirement.fluidOutput(output.stack(), output.chance()));
            for (EnergyInput output : energyOutputs) requirements.add(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, (int) output.fePerTick()));
        }
        return new MachineRecipeDefinition(id, machineId, tickTime, priority, maxThreads,
                cancelRecipeOnPerTickFailure, parallelized, allowPartialOutputs, itemInputs, fluidInputs,
                energyInputs, itemOutputs, fluidOutputs, energyOutputs, requirements, modifiers,
                levelRequirements, Set.copyOf(requiredHostIds));
    }

    /** Public smart-interface requirement value factory.
     * @author howxu <dev@howxu.cn>
     */
    public record SmartInterface(RecipeModifier.IOType io, String interfaceType, float minValue, float maxValue) {
        public SmartInterface { if (io == null) throw new IllegalArgumentException("io null"); }
        public static SmartInterface input(String type, float value) { return input(type, value, value); }
        public static SmartInterface input(String type, float min, float max) { return new SmartInterface(RecipeModifier.IOType.INPUT, type, min, max); }
        public static SmartInterface output(String type, float value) { return new SmartInterface(RecipeModifier.IOType.OUTPUT, type, value, value); }
    }
}
