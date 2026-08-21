package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
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
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.LinkedHashSet;

public final class MachineRecipe implements Recipe<RecipeInput> {

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("id", MMCR.id("generated_recipe")).forGetter(MachineRecipe::id),
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
            Codec.BOOL.optionalFieldOf("parallelized", false).forGetter(MachineRecipe::isParallelized),
            LevelRequirement.CODEC.listOf().optionalFieldOf("level_requirements", Collections.emptyList()).forGetter(MachineRecipe::levelRequirements),
            Codec.BOOL.optionalFieldOf("allow_partial_outputs", false).forGetter(MachineRecipe::allowPartialOutputs),
            Identifier.CODEC.listOf().xmap(MachineRecipe::copyHostIds, List::copyOf)
            .optionalFieldOf("required_host_ids", Set.of()).forGetter(MachineRecipe::requiredHostIds)
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
    private final List<LevelRequirement> levelRequirements;
    private final boolean allowPartialOutputs;
    private final Set<Identifier> requiredHostIds;

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
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure,
                fluidOutputs, requirements, parallelized, Collections.emptyList(), false, Set.of());
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
                         boolean parallelized,
                         List<LevelRequirement> levelRequirements) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure,
                fluidOutputs, requirements, parallelized, levelRequirements, false, Set.of());
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
                         boolean parallelized,
                         List<LevelRequirement> levelRequirements,
                         boolean allowPartialOutputs) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure,
                fluidOutputs, requirements, parallelized, levelRequirements, allowPartialOutputs, Set.of());
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
                         boolean parallelized,
                         List<LevelRequirement> levelRequirements,
                         Set<Identifier> requiredHostIds) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure,
                fluidOutputs, requirements, parallelized, levelRequirements, false, requiredHostIds);
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
                          boolean parallelized,
                           List<LevelRequirement> levelRequirements,
                           boolean allowPartialOutputs,
                           Set<Identifier> requiredHostIds) {
        this(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure,
                fluidOutputs, requirements, parallelized, levelRequirements, allowPartialOutputs, requiredHostIds, true);
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
                         boolean parallelized,
                         List<LevelRequirement> levelRequirements,
                         boolean allowPartialOutputs,
                         Set<Identifier> requiredHostIds,
                         boolean deriveEmptyRequirements) {
        if (id == null) {
            throw new IllegalArgumentException("Recipe id must not be null");
        }
        if (machineId == null) {
            throw new IllegalArgumentException("Recipe machineId must not be null");
        }
        if (tickTime < 1) {
            throw new IllegalArgumentException("Recipe tick time must be >= 1");
        }
        this.id = id;
        this.machineId = machineId;
        this.tickTime = tickTime;
        this.requirements = requirements == null || (deriveEmptyRequirements && requirements.isEmpty())
                ? deriveRequirements(inputs, outputs, fluidOutputs)
                : List.copyOf(requirements);
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.priority = priority;
        this.maxThreads = maxThreads;
        this.cancelRecipeOnPerTickFailure = cancelRecipeOnPerTickFailure;
        this.parallelized = parallelized;
        this.levelRequirements = validateLevelRequirements(levelRequirements);
        this.allowPartialOutputs = allowPartialOutputs;
        this.requiredHostIds = copyHostIds(requiredHostIds);
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
                                         boolean parallelized,
                                         List<LevelRequirement> levelRequirements,
                                         boolean allowPartialOutputs,
                                         Set<Identifier> requiredHostIds) {
        return new MachineRecipe(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, fluidOutputs, requirements, parallelized, levelRequirements, allowPartialOutputs, requiredHostIds);
    }

    private static Set<Identifier> copyHostIds(List<Identifier> ids) {
        return copyHostIds(ids == null ? Set.of() : new LinkedHashSet<>(ids));
    }

    private static Set<Identifier> copyHostIds(Set<Identifier> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        LinkedHashSet<Identifier> copy = new LinkedHashSet<>();
        for (Identifier id : ids) {
            if (id == null) throw new IllegalArgumentException("Required host id must not be null");
            copy.add(id);
        }
        return Collections.unmodifiableSet(copy);
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

    private static List<LevelRequirement> validateLevelRequirements(List<LevelRequirement> levelRequirements) {
        if (levelRequirements == null || levelRequirements.isEmpty()) return Collections.emptyList();
        var typeIds = new HashSet<Identifier>();
        for (LevelRequirement requirement : levelRequirements) {
            if (!typeIds.add(requirement.typeId())) {
                throw new IllegalArgumentException("Duplicate machine level requirement type: " + requirement.typeId());
            }
            var level = MachineLevelRegistry.getLevel(requirement.levelId());
            if (level == null) {
                throw new IllegalArgumentException("Unknown machine level: " + requirement.levelId());
            }
            if (!level.typeId().equals(requirement.typeId())) {
                throw new IllegalArgumentException("Machine level " + requirement.levelId() + " does not belong to type " + requirement.typeId());
            }
        }
        return List.copyOf(levelRequirements);
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
                inputs.add(new MachineIngredient.ItemIngredient(item.item(), item.count(), item.components(), item.consumeChance()));
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
                outputs.add(item.resolvedStack());
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
                outputs.add(new MachineOutput.ItemOutput(item.resolvedStack(), item.chance()));
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
        return runtimeRequirements(extraModifiers, 1D, 1D);
    }

    /**
     * Returns runtime requirements after applying independent machine-level multipliers before normal modifiers.
     */
    public List<MachineRequirement> runtimeRequirements(List<RecipeModifier> extraModifiers,
                                                        double energyMultiplier, double outputMultiplier) {
        List<RecipeModifier> effective = combineModifiers(extraModifiers);
        List<MachineRequirement> derived = new ArrayList<>(requirements.size());
        for (MachineRequirement requirement : requirements) {
            derived.add(applyModifiers(applyLevelModifiers(requirement, energyMultiplier, outputMultiplier), effective));
        }
        return List.copyOf(derived);
    }

    private static MachineRequirement applyLevelModifiers(MachineRequirement requirement,
                                                          double energyMultiplier, double outputMultiplier) {
        if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
            ItemStack stack = item.stack().copy();
            stack.setCount(levelOutputCount(stack.getCount(), outputMultiplier));
            return new ItemRequirement(item.io(), item.item(), item.count(), stack, item.chance(), item.tags(), item.components(), item.consumeChance());
        }
        if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
            FluidStack stack = fluid.stack().copy();
            stack.setAmount(levelOutputCount(stack.getAmount(), outputMultiplier));
            return new FluidRequirement(fluid.io(), fluid.fluid(), fluid.amount(), stack, fluid.chance(), fluid.tags());
        }
        if (requirement instanceof EnergyRequirement energy) {
            return new EnergyRequirement(energy.io(), floorNonNegative(energy.fePerTick() * energyMultiplier), energy.tags());
        }
        return requirement;
    }

    private static int levelOutputCount(int original, double multiplier) {
        int result = floorNonNegative(original * multiplier);
        return original > 0 ? Math.max(1, result) : result;
    }

    private static int floorNonNegative(double value) {
        if (value <= 0D) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(value);
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
                float consumeChance = IntegrationTypeHelper.applyItemInputChance(effectiveModifiers, item.consumeChance());
                return new ItemRequirement(item.io(), item.item(), count, item.stack(), item.chance(), item.tags(), item.components(), consumeChance);
            }
            ItemStack stack = item.stack().copy();
            stack.setCount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemOutput(effectiveModifiers, stack.getCount())));
            float chance = IntegrationTypeHelper.applyItemOutputChance(effectiveModifiers, item.chance());
            return new ItemRequirement(item.io(), item.item(), item.count(), stack, chance, item.tags(), item.components(), item.consumeChance());
        }
        if (requirement instanceof FluidRequirement fluid) {
            if (fluid.io() == RecipeModifier.IOType.INPUT) {
                int amount = IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidInput(effectiveModifiers, fluid.amount()));
                float chance = IntegrationTypeHelper.applyFluidInputChance(effectiveModifiers, fluid.chance());
                return new FluidRequirement(fluid.io(), fluid.fluid(), amount, fluid.stack(), chance, fluid.tags());
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

    public MachineRecipe withId(Identifier id) {
        return new MachineRecipe(id, machineId, tickTime, List.of(), List.of(), modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, List.of(), requirements, parallelized, levelRequirements,
                allowPartialOutputs, requiredHostIds, false);
    }

    public List<LevelRequirement> levelRequirements() {
        return levelRequirements;
    }

    public Set<Identifier> requiredHostIds() {
        return requiredHostIds;
    }

    public boolean canRunOnConnectedHost(Identifier hostId) {
        return hostId != null && (requiredHostIds.isEmpty() || requiredHostIds.contains(hostId));
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
        try {
            return left.item().items().anyMatch(leftItem -> right.item().items().anyMatch(rightItem -> leftItem.equals(rightItem)));
        } catch (UnsupportedOperationException ignored) {
            return true;
        }
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

    public boolean allowPartialOutputs() {
        return allowPartialOutputs;
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
    public boolean isSpecial() {
        return true;
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
                && levelRequirements.equals(that.levelRequirements)
                && requiredHostIds.equals(that.requiredHostIds)
                && modifiers.equals(that.modifiers)
                && cancelRecipeOnPerTickFailure == that.cancelRecipeOnPerTickFailure
                && parallelized == that.parallelized
                && allowPartialOutputs == that.allowPartialOutputs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, machineId, tickTime, requirements, modifiers, priority, maxThreads, cancelRecipeOnPerTickFailure, parallelized, levelRequirements, allowPartialOutputs, requiredHostIds);
    }
}
