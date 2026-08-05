package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import cn.howxu.mmcr.util.IOType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MachineRecipe implements Recipe<RecipeInput> {

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(MachineRecipe::id),
            Identifier.CODEC.fieldOf("machine").forGetter(MachineRecipe::machineId),
            Codec.INT.fieldOf("tick_time").forGetter(MachineRecipe::tickTime),
            MachineIngredient.CODEC.listOf().optionalFieldOf("inputs", Collections.emptyList()).forGetter(MachineRecipe::inputs),
            ItemStack.CODEC.listOf().optionalFieldOf("outputs", Collections.emptyList()).forGetter(MachineRecipe::outputs),
            MachineIngredient.CODEC.listOf().optionalFieldOf("fluid_outputs", Collections.emptyList()).forGetter(recipe -> Collections.emptyList()),
            MachineRequirement.CODEC.listOf().optionalFieldOf("requirements", Collections.emptyList()).forGetter(MachineRecipe::requirements),
            RecipeModifier.CODEC.listOf().optionalFieldOf("modifiers", Collections.emptyList()).forGetter(MachineRecipe::modifiers),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MachineRecipe::priority),
            Codec.INT.optionalFieldOf("max_threads", 1).forGetter(MachineRecipe::maxThreads),
            Codec.BOOL.optionalFieldOf("cancelIfPerTickFails", false).forGetter(MachineRecipe::doesCancelRecipeOnPerTickFailure)
    ).apply(instance, MachineRecipe::fromCodec));

    private final Identifier id;
    private final Identifier machineId;
    private final int tickTime;
    private final List<MachineIngredient> inputs;
    private final List<ItemStack> outputs;
    private final List<MachineRequirement> requirements;
    private final List<RecipeModifier> modifiers;
    private final int priority;
    private final int maxThreads;
    private final boolean cancelRecipeOnPerTickFailure;

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
                         List<MachineRequirement> requirements,
                         List<RecipeModifier> modifiers,
                         int priority,
                         int maxThreads,
                         boolean cancelRecipeOnPerTickFailure) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, requirements);
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
                         List<MachineRequirement> requirements) {
        if (id == null) {
            throw new IllegalArgumentException("Recipe id must not be null");
        }
        if (machineId == null) {
            throw new IllegalArgumentException("Recipe machineId must not be null");
        }
        this.id = id;
        this.machineId = machineId;
        this.tickTime = Math.max(1, tickTime);
        boolean explicitRequirements = requirements != null && !requirements.isEmpty();
        List<MachineRequirement> resolvedRequirements = !explicitRequirements
                ? requirementsFromLegacy(inputs, outputs)
                : List.copyOf(requirements);
        this.requirements = resolvedRequirements;
        this.inputs = explicitRequirements ? inputsFromRequirements(resolvedRequirements) : inputs == null ? Collections.emptyList() : List.copyOf(inputs);
        this.outputs = explicitRequirements && (outputs == null || outputs.isEmpty())
                ? outputsFromRequirements(resolvedRequirements)
                : outputs == null ? Collections.emptyList() : List.copyOf(outputs);
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.priority = priority;
        this.maxThreads = Math.max(1, maxThreads);
        this.cancelRecipeOnPerTickFailure = cancelRecipeOnPerTickFailure;
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
        return inputs;
    }

    public List<ItemStack> outputs() {
        return outputs;
    }

    public List<MachineRequirement> requirements() {
        return requirements;
    }

    public List<FluidRequirement> fluidOutputs() {
        return requirements.stream()
                .filter(FluidRequirement.class::isInstance)
                .map(FluidRequirement.class::cast)
                .filter(requirement -> requirement.ioType() == IOType.OUTPUT)
                .toList();
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

    private static MachineRecipe fromCodec(Identifier id,
                                           Identifier machineId,
                                           int tickTime,
                                           List<MachineIngredient> inputs,
                                           List<ItemStack> outputs,
                                           List<MachineIngredient> fluidOutputs,
                                           List<MachineRequirement> requirements,
                                           List<RecipeModifier> modifiers,
                                           int priority,
                                           int maxThreads,
                                           boolean cancelRecipeOnPerTickFailure) {
        return new MachineRecipe(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, requirementsFromCodecLegacy(inputs, outputs, fluidOutputs, requirements));
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
                && inputs.equals(that.inputs)
                && outputs.equals(that.outputs)
                && requirements.equals(that.requirements)
                && modifiers.equals(that.modifiers)
                && cancelRecipeOnPerTickFailure == that.cancelRecipeOnPerTickFailure;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, machineId, tickTime, inputs, outputs, requirements, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure);
    }

    private static List<MachineRequirement> requirementsFromLegacy(List<MachineIngredient> inputs, List<ItemStack> outputs) {
        List<MachineRequirement> requirements = new java.util.ArrayList<>();
        if (inputs != null) {
            for (MachineIngredient input : inputs) {
                if (input instanceof MachineIngredient.ItemIngredient item) {
                    requirements.add(new ItemRequirement(item.item(), item.count(), null, IOType.INPUT));
                } else if (input instanceof MachineIngredient.FluidIngredient fluid) {
                    requirements.add(new FluidRequirement(fluid.fluid(), fluid.amount(), null, IOType.INPUT));
                } else if (input instanceof MachineIngredient.EnergyIngredient energy) {
                    requirements.add(new EnergyRequirement(energy.fePerTick()));
                }
            }
        }
        if (outputs != null) {
            for (ItemStack output : outputs) {
                if (!output.isEmpty()) {
                    requirements.add(new ItemRequirement(Ingredient.of(output.getItem()), output.getCount(), null, IOType.OUTPUT));
                }
            }
        }
        return List.copyOf(requirements);
    }

    private static List<MachineRequirement> requirementsFromCodecLegacy(List<MachineIngredient> inputs,
                                                                        List<ItemStack> outputs,
                                                                        List<MachineIngredient> fluidOutputs,
                                                                        List<MachineRequirement> explicitRequirements) {
        if (explicitRequirements != null && !explicitRequirements.isEmpty()) return explicitRequirements;

        List<MachineRequirement> requirements = new java.util.ArrayList<>(requirementsFromLegacy(inputs, outputs));
        for (MachineIngredient output : fluidOutputs) {
            if (output instanceof MachineIngredient.FluidIngredient fluid) {
                requirements.add(new FluidRequirement(fluid.fluid(), fluid.amount(), null, IOType.OUTPUT));
            }
        }
        return List.copyOf(requirements);
    }

    private static List<MachineIngredient> inputsFromRequirements(List<MachineRequirement> requirements) {
        return requirements.stream()
                .filter(requirement -> !(requirement instanceof ItemRequirement item && item.ioType() == IOType.OUTPUT))
                .filter(requirement -> !(requirement instanceof FluidRequirement fluid && fluid.ioType() == IOType.OUTPUT))
                .map(MachineRecipe::legacyInput)
                .filter(Objects::nonNull)
                .toList();
    }

    private static MachineIngredient legacyInput(MachineRequirement requirement) {
        if (requirement instanceof ItemRequirement item) {
            return new MachineIngredient.ItemIngredient(item.item(), item.count());
        }
        if (requirement instanceof FluidRequirement fluid) {
            return new MachineIngredient.FluidIngredient(fluid.fluid(), fluid.amount());
        }
        if (requirement instanceof EnergyRequirement energy) {
            return new MachineIngredient.EnergyIngredient(energy.fePerTick());
        }
        return null;
    }

    private static List<ItemStack> outputsFromRequirements(List<MachineRequirement> requirements) {
        return requirements.stream()
                .filter(ItemRequirement.class::isInstance)
                .map(ItemRequirement.class::cast)
                .filter(requirement -> requirement.ioType() == IOType.OUTPUT)
                .map(requirement -> requirement.item().items()
                        .findFirst()
                        .map(holder -> new ItemStack(holder.value(), requirement.count()))
                        .orElse(ItemStack.EMPTY))
                .filter(stack -> !stack.isEmpty())
                .toList();
    }
}
