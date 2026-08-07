package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MachineRecipe implements Recipe<RecipeInput> {

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(MachineRecipe::id),
            Identifier.CODEC.fieldOf("machine").forGetter(MachineRecipe::machineId),
            Codec.INT.fieldOf("tick_time").forGetter(MachineRecipe::tickTime),
            MachineIngredient.CODEC.listOf().optionalFieldOf("inputs", Collections.emptyList()).forGetter(recipe -> Collections.emptyList()),
            ItemStack.CODEC.listOf().optionalFieldOf("outputs", Collections.emptyList()).forGetter(recipe -> Collections.emptyList()),
            FluidStack.CODEC.listOf().optionalFieldOf("fluid_outputs", Collections.emptyList()).forGetter(recipe -> Collections.emptyList()),
            RecipeModifier.CODEC.listOf().optionalFieldOf("modifiers", Collections.emptyList()).forGetter(MachineRecipe::modifiers),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MachineRecipe::priority),
            Codec.INT.optionalFieldOf("max_threads", 1).forGetter(MachineRecipe::maxThreads),
            Codec.BOOL.optionalFieldOf("cancelIfPerTickFails", false).forGetter(MachineRecipe::doesCancelRecipeOnPerTickFailure),
            MachineRequirement.CODEC.listOf().optionalFieldOf("requirements", Collections.emptyList()).forGetter(MachineRecipe::requirements),
            Codec.BOOL.optionalFieldOf("parallelized", false).forGetter(MachineRecipe::isParallelized)
    ).apply(instance, MachineRecipe::create));

    private final Identifier id;
    private final Identifier machineId;
    private final int tickTime;
    private final List<MachineRequirement> requirements;
    private final List<RecipeModifier> modifiers;
    private final int priority;
    private final int maxThreads;
    private final boolean cancelRecipeOnPerTickFailure;
    private final boolean parallelized;

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs) {
        this(id, machineId, tickTime, inputs, outputs, Collections.emptyList(), 0, 1);
    }

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, false);
    }

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads,
                         boolean cancelRecipeOnPerTickFailure) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, Collections.emptyList());
    }

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads,
                         boolean cancelRecipeOnPerTickFailure,
                         List<FluidStack> fluidOutputs) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, fluidOutputs, Collections.emptyList());
    }

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads,
                         boolean cancelRecipeOnPerTickFailure,
                         List<FluidStack> fluidOutputs,
                         List<MachineRequirement> requirements) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, fluidOutputs, requirements, false);
    }

    public MachineRecipe(Identifier id,
                         Identifier machineId,
                         int tickTime,
                         List<MachineIngredient> inputs,
                         List<ItemStack> outputs,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads,
                         boolean cancelRecipeOnPerTickFailure,
                         List<FluidStack> fluidOutputs,
                         List<MachineRequirement> requirements,
                         boolean parallelized) {
        if (id == null) {
            throw new IllegalArgumentException("Recipe id must not be null");
        }
        if (machineId == null) {
            throw new IllegalArgumentException("Recipe machineId must not be null");
        }
        this.id = id;
        this.machineId = machineId;
        this.tickTime = Math.max(1, tickTime);
        this.requirements = requirements == null || requirements.isEmpty()
                ? deriveRequirements(inputs, outputs, fluidOutputs)
                : List.copyOf(requirements);
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.priority = priority;
        this.maxThreads = Math.max(1, maxThreads);
        this.cancelRecipeOnPerTickFailure = cancelRecipeOnPerTickFailure;
        this.parallelized = parallelized;
    }

    private static MachineRecipe create(Identifier id,
                                        Identifier machineId,
                                        int tickTime,
                                        List<MachineIngredient> inputs,
                                        List<ItemStack> outputs,
                                         List<FluidStack> fluidOutputs,
                                         List<RecipeModifier> modifiers,
                                         int priority,
                                         int maxThreads,
                                         boolean cancelRecipeOnPerTickFailure,
                                         List<MachineRequirement> requirements,
                                         boolean parallelized) {
        return new MachineRecipe(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, fluidOutputs, requirements, parallelized);
    }

    private static List<MachineRequirement> deriveRequirements(List<MachineIngredient> inputs, List<ItemStack> outputs, List<FluidStack> fluidOutputs) {
        List<MachineRequirement> requirements = new ArrayList<>();
        if (inputs != null) {
            for (MachineIngredient input : inputs) requirements.add(MachineRequirement.fromInput(input));
        }
        if (outputs != null) {
            for (ItemStack output : outputs) requirements.add(MachineRequirement.itemOutput(output));
        }
        if (fluidOutputs != null) {
            for (FluidStack output : fluidOutputs) requirements.add(MachineRequirement.fluidOutput(output));
        }
        return List.copyOf(requirements);
    }

    public Identifier id() {
        return id;
    }

    public Identifier machineId() {
        return machineId;
    }

    public int tickTime() {
        return tickTime;
    }

    public List<MachineIngredient> inputs() {
        List<MachineIngredient> inputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                inputs.add(new MachineIngredient.ItemIngredient(item.item(), item.count()));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.INPUT) {
                inputs.add(new MachineIngredient.FluidIngredient(fluid.fluid(), fluid.amount()));
            } else if (requirement instanceof EnergyRequirement energy && energy.io() == RecipeModifier.IOType.INPUT) {
                inputs.add(new MachineIngredient.EnergyIngredient(energy.fePerTick()));
            }
        }
        return List.copyOf(inputs);
    }

    public List<ItemStack> outputs() {
        List<ItemStack> outputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                outputs.add(item.stack().copy());
            }
        }
        return List.copyOf(outputs);
    }

    public List<FluidStack> fluidOutputs() {
        List<FluidStack> outputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                outputs.add(fluid.stack().copy());
            }
        }
        return List.copyOf(outputs);
    }

    public List<MachineOutput> machineOutputs() {
        List<MachineOutput> outputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                outputs.add(new MachineOutput.ItemOutput(item.stack(), item.chance()));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                outputs.add(new MachineOutput.FluidOutput(fluid.stack(), fluid.chance()));
            }
        }
        return List.copyOf(outputs);
    }

    public List<Integer> energyOutputs() {
        List<Integer> outputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (requirement instanceof EnergyRequirement energy && energy.io() == RecipeModifier.IOType.OUTPUT) {
                outputs.add(energy.fePerTick());
            }
        }
        return List.copyOf(outputs);
    }

    public List<MachineRequirement> runtimeRequirements() {
        return runtimeRequirements(List.of());
    }

    /**
     * Returns derived requirements after applying this recipe's own modifiers and the supplied extra modifiers.
     * The argument must contain only structure/runtime modifiers; recipe-local modifiers are added here.
     */
    public List<MachineRequirement> runtimeRequirements(List<RecipeModifier> extraModifiers) {
        List<RecipeModifier> effective = combineModifiers(extraModifiers);
        if (effective.isEmpty()) return requirements;
        List<MachineRequirement> derived = new ArrayList<>(requirements.size());
        for (MachineRequirement requirement : requirements) {
            derived.add(applyModifiers(requirement, effective));
        }
        return List.copyOf(derived);
    }

    public List<MachineOutput> runtimeMachineOutputs() {
        return runtimeMachineOutputs(List.of());
    }

    /**
     * Returns derived outputs after applying this recipe's own modifiers and the supplied extra modifiers.
     * The argument must contain only structure/runtime modifiers; recipe-local modifiers are added here.
     */
    public List<MachineOutput> runtimeMachineOutputs(List<RecipeModifier> extraModifiers) {
        List<RecipeModifier> effective = combineModifiers(extraModifiers);
        if (effective.isEmpty()) return machineOutputs();
        return machineOutputs().stream()
                .map(output -> output.applyModifiers(effective))
                .toList();
    }

    private List<RecipeModifier> combineModifiers(List<RecipeModifier> extraModifiers) {
        if (extraModifiers == null || extraModifiers.isEmpty()) return modifiers;
        ArrayList<RecipeModifier> combined = new ArrayList<>(modifiers.size() + extraModifiers.size());
        combined.addAll(modifiers);
        combined.addAll(extraModifiers);
        return List.copyOf(combined);
    }

    private MachineRequirement applyModifiers(MachineRequirement requirement, List<RecipeModifier> effectiveModifiers) {
        if (requirement instanceof ItemRequirement item) {
            if (item.io() == RecipeModifier.IOType.INPUT) {
                int count = IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemInput(effectiveModifiers, item.count()));
                return new ItemRequirement(item.io(), item.item(), count, item.stack(), item.chance(), item.tags());
            }
            ItemStack stack = item.stack().copy();
            stack.setCount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemOutput(effectiveModifiers, stack.getCount())));
            float chance = IntegrationTypeHelper.applyItemOutputChance(effectiveModifiers, item.chance());
            return new ItemRequirement(item.io(), item.item(), item.count(), stack, chance, item.tags());
        }
        if (requirement instanceof FluidRequirement fluid) {
            if (fluid.io() == RecipeModifier.IOType.INPUT) {
                int amount = IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidInput(effectiveModifiers, fluid.amount()));
                return new FluidRequirement(fluid.io(), fluid.fluid(), amount, fluid.stack(), fluid.chance(), fluid.tags());
            }
            FluidStack stack = fluid.stack().copy();
            stack.setAmount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidOutput(effectiveModifiers, stack.getAmount())));
            float chance = IntegrationTypeHelper.applyFluidOutputChance(effectiveModifiers, fluid.chance());
            return new FluidRequirement(fluid.io(), fluid.fluid(), fluid.amount(), stack, chance, fluid.tags());
        }
        if (requirement instanceof EnergyRequirement energy) {
            int fePerTick = IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyEnergy(effectiveModifiers, energy.fePerTick()));
            return new EnergyRequirement(fePerTick, energy.tags());
        }
        return requirement;
    }

    public List<MachineRequirement> requirements() {
        return requirements;
    }

    public int inputRequirementCount() {
        int count = 0;
        for (MachineRequirement requirement : requirements) {
            if (requirement.io() == RecipeModifier.IOType.INPUT) {
                count++;
            }
        }
        return count;
    }

    public boolean hasOverlappingInputs(MachineRecipe other) {
        if (other == null) return false;
        for (MachineRequirement requirement : requirements) {
            if (!(requirement instanceof ItemRequirement item) || item.io() != RecipeModifier.IOType.INPUT) continue;
            for (MachineRequirement otherRequirement : other.requirements) {
                if (!(otherRequirement instanceof ItemRequirement otherItem) || otherItem.io() != RecipeModifier.IOType.INPUT) continue;
                if (ingredientsOverlap(item, otherItem)) return true;
            }
        }
        return false;
    }

    private static boolean ingredientsOverlap(ItemRequirement left, ItemRequirement right) {
        return left.item().items().anyMatch(leftItem -> right.item().items().anyMatch(rightItem -> leftItem.equals(rightItem)));
    }

    public List<RecipeModifier> modifiers() {
        return modifiers;
    }

    public int priority() {
        return priority;
    }

    public int maxThreads() {
        return maxThreads;
    }

    public boolean doesCancelRecipeOnPerTickFailure() {
        return cancelRecipeOnPerTickFailure;
    }

    public boolean isParallelized() {
        return parallelized;
    }

    public Identifier getRegistryName() {
        return id;
    }

    public Identifier getOwningMachineIdentifier() {
        return machineId;
    }

    public int getRecipeTotalTickTime() {
        return tickTime;
    }

    public int getConfiguredPriority() {
        return priority;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return true;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        List<ItemStack> outputs = outputs();
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst().copy();
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return ModRecipeTypes.MACHINE_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<RecipeInput>> getType() {
        return ModRecipeTypes.MACHINE_RECIPE_TYPE.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return new RecipeBookCategory();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MachineRecipe that)) return false;
        return tickTime == that.tickTime
                && priority == that.priority
                && maxThreads == that.maxThreads
                && id.equals(that.id)
                && machineId.equals(that.machineId)
                && requirements.equals(that.requirements)
                && modifiers.equals(that.modifiers)
                && cancelRecipeOnPerTickFailure == that.cancelRecipeOnPerTickFailure
                && parallelized == that.parallelized;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, machineId, tickTime, requirements, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, parallelized);
    }
}
