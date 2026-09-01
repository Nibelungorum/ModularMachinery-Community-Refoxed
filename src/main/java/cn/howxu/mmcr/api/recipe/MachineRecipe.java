package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
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

    static final int MAX_LIST_ENTRIES = 4096;
    static final int MAX_CHILD_PAYLOAD = 1_000_000;

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("id", MMCR.id("generated_recipe")).forGetter(MachineRecipe::id),
            Identifier.CODEC.fieldOf("machine").forGetter(MachineRecipe::machineId),
            Codec.INT.fieldOf("tick_time").forGetter(MachineRecipe::tickTime),
            boundedList(MachineIngredient.CODEC, "inputs").optionalFieldOf("inputs", Collections.emptyList()).forGetter(recipe -> Collections.emptyList()),
            OutputField.CODEC.optionalFieldOf("outputs", OutputField.legacyEmpty()).forGetter(recipe -> new OutputField(recipe.outputs, true)),
            boundedList(FluidStack.CODEC, "fluid_outputs").optionalFieldOf("fluid_outputs", Collections.emptyList()).forGetter(recipe -> Collections.emptyList()),
            boundedList(MachineOutput.CODEC, "machine_outputs").optionalFieldOf("machine_outputs", Collections.emptyList()).forGetter(recipe -> Collections.emptyList()),
            boundedList(RecipeModifier.CODEC, "modifiers").optionalFieldOf("modifiers", Collections.emptyList()).forGetter(MachineRecipe::modifiers),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MachineRecipe::priority),
            Codec.INT.optionalFieldOf("max_threads", 1).forGetter(MachineRecipe::maxThreads),
            Codec.BOOL.optionalFieldOf("cancelIfPerTickFails", false).forGetter(MachineRecipe::doesCancelRecipeOnPerTickFailure),
            boundedList(MachineRequirement.CODEC, "requirements").optionalFieldOf("requirements", Collections.emptyList()).forGetter(MachineRecipe::requirements),
            Codec.BOOL.optionalFieldOf("parallelized", false).forGetter(MachineRecipe::isParallelized),
            boundedList(LevelRequirement.CODEC, "level_requirements").optionalFieldOf("level_requirements", Collections.emptyList()).forGetter(MachineRecipe::levelRequirements),
            Codec.BOOL.optionalFieldOf("allow_partial_outputs", false).forGetter(MachineRecipe::allowPartialOutputs),
            boundedList(Identifier.CODEC, "required_host_ids").xmap(MachineRecipe::copyHostIds, List::copyOf)
            .optionalFieldOf("required_host_ids", Set.of()).forGetter(MachineRecipe::requiredHostIds)
    ).apply(instance, MachineRecipe::create));

    private final Identifier id;
    private final Identifier machineId;
    private final int tickTime;
    private final List<MachineRequirement> requirements;
    private final List<MachineOutput> outputs;
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
        this(id, machineId, tickTime,
                requirements == null || (deriveEmptyRequirements && requirements.isEmpty())
                        ? deriveRequirements(inputs, outputs, fluidOutputs) : requirements,
                legacyOutputs(requirements, deriveEmptyRequirements, outputs, fluidOutputs), modifiers, priority,
                maxThreads, cancelRecipeOnPerTickFailure, parallelized, levelRequirements, allowPartialOutputs,
                requiredHostIds);
    }

    private MachineRecipe(Identifier id,
                          Identifier machineId,
                          int tickTime,
                          List<MachineRequirement> requirements,
                          List<MachineOutput> outputs,
                          List<RecipeModifier> modifiers,
                          int priority,
                          int maxThreads,
                          boolean cancelRecipeOnPerTickFailure,
                          boolean parallelized,
                          List<LevelRequirement> levelRequirements,
                          boolean allowPartialOutputs,
                          Set<Identifier> requiredHostIds) {
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
        this.requirements = MachineRequirement.copyList(requirements == null ? List.of() : requirements);
        this.outputs = MachineOutput.copyList(outputs == null ? List.of() : outputs);
        this.modifiers = modifiers == null ? Collections.emptyList() : List.copyOf(modifiers);
        this.priority = priority;
        this.maxThreads = maxThreads;
        this.cancelRecipeOnPerTickFailure = cancelRecipeOnPerTickFailure;
        this.parallelized = parallelized;
        this.levelRequirements = validateLevelRequirements(levelRequirements);
        this.allowPartialOutputs = allowPartialOutputs;
        this.requiredHostIds = copyHostIds(requiredHostIds);
    }

    private MachineRecipe(MachineRecipe recipe, List<MachineOutput> additionalOutputs) {
        this.id = recipe.id;
        this.machineId = recipe.machineId;
        this.tickTime = recipe.tickTime;
        this.modifiers = recipe.modifiers;
        this.priority = recipe.priority;
        this.maxThreads = recipe.maxThreads;
        this.cancelRecipeOnPerTickFailure = recipe.cancelRecipeOnPerTickFailure;
        this.parallelized = recipe.parallelized;
        this.levelRequirements = recipe.levelRequirements;
        this.allowPartialOutputs = recipe.allowPartialOutputs;
        this.requiredHostIds = recipe.requiredHostIds;
        List<MachineOutput> copiedAdditionalOutputs = MachineOutput.copyList(additionalOutputs == null
                ? List.of() : additionalOutputs);
        List<MachineOutput> newOutputs = copiedAdditionalOutputs.stream()
                .filter(output -> !recipe.outputs.contains(output))
                .toList();
        this.outputs = appendOutputs(recipe.outputs, newOutputs);
        List<MachineRequirement> newRequirements = new ArrayList<>(recipe.requirements);
        for (MachineOutput output : newOutputs) {
            MachineRequirement requirement = OutputRegistry.tryToRequirement(output, List.of());
            if (requirement != null && !newRequirements.contains(requirement)) newRequirements.add(requirement);
        }
        this.requirements = List.copyOf(newRequirements);
    }

    private MachineRecipe(Identifier id, MachineRecipe recipe) {
        this.id = Objects.requireNonNull(id, "id");
        this.machineId = recipe.machineId;
        this.tickTime = recipe.tickTime;
        this.requirements = recipe.requirements;
        this.outputs = recipe.outputs;
        this.modifiers = recipe.modifiers;
        this.priority = recipe.priority;
        this.maxThreads = recipe.maxThreads;
        this.cancelRecipeOnPerTickFailure = recipe.cancelRecipeOnPerTickFailure;
        this.parallelized = recipe.parallelized;
        this.levelRequirements = recipe.levelRequirements;
        this.allowPartialOutputs = recipe.allowPartialOutputs;
        this.requiredHostIds = recipe.requiredHostIds;
    }

    /**
     * Adds registered extension outputs while preserving the legacy item/fluid constructor contract.
     */
    public static MachineRecipe withAdditionalOutputs(MachineRecipe recipe, List<MachineOutput> additionalOutputs) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(additionalOutputs, "additionalOutputs");
        return additionalOutputs.isEmpty() ? recipe : new MachineRecipe(recipe, additionalOutputs);
    }

    public static MachineRecipe fromCanonical(Identifier id,
                                       Identifier machineId,
                                       int tickTime,
                                       List<MachineRequirement> requirements,
                                       List<MachineOutput> outputs,
                                       List<RecipeModifier> modifiers,
                                       int priority,
                                       int maxThreads,
                                       boolean cancelRecipeOnPerTickFailure,
                                       boolean parallelized,
                                       List<LevelRequirement> levelRequirements,
                                       boolean allowPartialOutputs,
                                       Set<Identifier> requiredHostIds) {
        return new MachineRecipe(id, machineId, tickTime, requirements, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, parallelized, levelRequirements, allowPartialOutputs, requiredHostIds);
    }

    private static MachineRecipe create(Identifier id,
                                        Identifier machineId,
                                        int tickTime,
                                         List<MachineIngredient> inputs,
                                         OutputField outputs,
                                         List<FluidStack> fluidOutputs,
                                         List<MachineOutput> machineOutputs,
                                         List<RecipeModifier> modifiers,
                                         int priority,
                                         int maxThreads,
                                         boolean cancelRecipeOnPerTickFailure,
                                         List<MachineRequirement> requirements,
                                         boolean parallelized,
                                         List<LevelRequirement> levelRequirements,
                                         boolean allowPartialOutputs,
                                         Set<Identifier> requiredHostIds) {
        boolean explicitRequirements = requirements != null && !requirements.isEmpty();
        List<MachineRequirement> canonicalRequirements = explicitRequirements
                ? requirements : deriveRequirements(inputs, outputs.legacyItemStacks(), fluidOutputs);
        List<MachineOutput> canonicalOutputs = explicitRequirements && !outputs.canonical()
                ? appendOutputs(deriveOutputs(canonicalRequirements), outputs.values()) : outputs.values();
        canonicalOutputs = appendOutputs(canonicalOutputs, canonicalFluidOutputs(fluidOutputs));
        canonicalOutputs = appendOutputs(canonicalOutputs, machineOutputs);
        return fromCanonical(id, machineId, tickTime, canonicalRequirements, canonicalOutputs, modifiers,
                priority, maxThreads, cancelRecipeOnPerTickFailure, parallelized, levelRequirements,
                allowPartialOutputs, requiredHostIds);
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

    private static List<MachineOutput> legacyOutputs(List<MachineRequirement> requirements,
                                                     boolean deriveEmptyRequirements,
                                                     List<ItemStack> outputs,
                                                     List<FluidStack> fluidOutputs) {
        if (requirements != null && !(deriveEmptyRequirements && requirements.isEmpty()) && !requirements.isEmpty()) {
            List<MachineOutput> result = appendOutputs(deriveOutputs(requirements), canonicalItemOutputs(outputs));
            return appendOutputs(result, canonicalFluidOutputs(fluidOutputs));
        }
        List<MachineOutput> result = new ArrayList<>();
        if (outputs != null) {
            for (ItemStack output : outputs) result.add(new MachineOutput.ItemOutput(output, 1F));
        }
        if (fluidOutputs != null) {
            for (FluidStack output : fluidOutputs) result.add(new MachineOutput.FluidOutput(output, 1F));
        }
        return List.copyOf(result);
    }

    private static List<MachineOutput> canonicalFluidOutputs(List<FluidStack> fluidOutputs) {
        if (fluidOutputs == null || fluidOutputs.isEmpty()) return List.of();
        List<MachineOutput> result = new ArrayList<>(fluidOutputs.size());
        for (FluidStack output : fluidOutputs) {
            result.add(new MachineOutput.FluidOutput(output, 1F));
        }
        return List.copyOf(result);
    }

    private static List<MachineOutput> canonicalItemOutputs(List<ItemStack> outputs) {
        if (outputs == null || outputs.isEmpty()) return List.of();
        List<MachineOutput> result = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            result.add(new MachineOutput.ItemOutput(output, 1F));
        }
        return List.copyOf(result);
    }

    private static List<MachineOutput> deriveOutputs(List<MachineRequirement> requirements) {
        List<MachineOutput> outputs = new ArrayList<>();
        if (requirements == null) return List.of();
        for (MachineRequirement requirement : requirements) {
            MachineOutput output = OutputRegistry.fromRequirement(requirement);
            if (output != null) outputs.add(output);
        }
        return List.copyOf(outputs);
    }

    static List<MachineRequirement> legacyRequirements(List<MachineIngredient> inputs,
                                                       List<ItemStack> outputs,
                                                       List<FluidStack> fluidOutputs) {
        return deriveRequirements(inputs, outputs, fluidOutputs);
    }

    static List<MachineRequirement> legacyInputRequirements(List<MachineIngredient> inputs) {
        return deriveRequirements(inputs, List.of(), List.of());
    }

    static List<MachineOutput> outputsFromRequirements(List<MachineRequirement> requirements) {
        return deriveOutputs(requirements);
    }

    private static List<MachineOutput> appendOutputs(List<MachineOutput> first, List<MachineOutput> second) {
        List<MachineOutput> result = new ArrayList<>(first == null ? List.of() : first);
        if (second != null) {
            for (MachineOutput output : second) {
                if (!result.contains(output)) result.add(output);
            }
        }
        return List.copyOf(result);
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

    static List<LevelRequirement> validateLevelRequirements(List<LevelRequirement> levelRequirements) {
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
            MachineIngredient input = RequirementHandlerRegistry.legacyInput(requirement);
            if (input != null) inputs.add(input);
        }
        return List.copyOf(inputs);
    }

    public List<ItemStack> outputs() {
        return OutputRegistry.itemStacks(machineOutputs());
    }

    public List<FluidStack> fluidOutputs() {
        return OutputRegistry.fluidStacks(machineOutputs());
    }

    public List<MachineOutput> machineOutputs() {
        return outputs;
    }

    public List<Integer> energyOutputs() {
        List<Integer> outputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            Integer energy = RequirementHandlerRegistry.legacyEnergyOutput(requirement);
            if (energy != null) outputs.add(energy);
        }
        return List.copyOf(outputs);
    }

    private List<MachineRequirement> runtimeRequirementSource() {
        List<MachineRequirement> source = new ArrayList<>(requirements);
        List<MachineRequirement> availableOutputRequirements = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            if (OutputRegistry.matchesOutputRequirement(requirement)) availableOutputRequirements.add(requirement);
        }
        for (MachineOutput machineOutput : outputs) {
            MachineRequirement output = OutputRegistry.tryToRequirement(machineOutput, List.of());
            if (output != null) {
                boolean represented = false;
                for (int index = 0; index < availableOutputRequirements.size(); index++) {
                    if (OutputRegistry.matchesOutputRequirement(machineOutput, availableOutputRequirements.get(index))) {
                        availableOutputRequirements.remove(index);
                        represented = true;
                        break;
                    }
                }
                if (!represented) source.add(output);
            }
        }
        return List.copyOf(source);
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
        List<MachineRequirement> derived = new ArrayList<>();
        for (MachineRequirement requirement : runtimeRequirementSource()) {
            derived.add(RequirementHandlerRegistry.applyModifiers(
                    RequirementHandlerRegistry.applyLevelModifiers(requirement, energyMultiplier, outputMultiplier),
                    effective));
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
        if (effective.isEmpty()) return outputs;
        return outputs.stream()
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

    public List<MachineRequirement> requirements() {
        return requirements;
    }

    public MachineRecipe withId(Identifier id) {
        return new MachineRecipe(id, this);
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
            if (requirement.io() != RecipeModifier.IOType.INPUT) continue;
            for (MachineRequirement otherRequirement : other.requirements) {
                if (otherRequirement.io() == RecipeModifier.IOType.INPUT
                        && RequirementHandlerRegistry.overlaps(requirement, otherRequirement)) return true;
            }
        }
        return false;
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
                && outputs.equals(that.outputs)
                && levelRequirements.equals(that.levelRequirements)
                && requiredHostIds.equals(that.requiredHostIds)
                && modifiers.equals(that.modifiers)
                && cancelRecipeOnPerTickFailure == that.cancelRecipeOnPerTickFailure
                && parallelized == that.parallelized
                && allowPartialOutputs == that.allowPartialOutputs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, machineId, tickTime, requirements, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, parallelized, levelRequirements, allowPartialOutputs, requiredHostIds);
    }

    private record OutputField(List<MachineOutput> values, boolean canonical) {
        private static final Codec<OutputField> CODEC = boundedList(Codec.either(MachineOutput.CODEC, ItemStack.CODEC), "outputs")
                .xmap(OutputField::fromEntries, OutputField::toEntries);

        private OutputField {
            values = List.copyOf(values == null ? List.of() : values);
        }

        private static OutputField legacyEmpty() {
            return new OutputField(List.of(), false);
        }

        private static OutputField fromEntries(List<Either<MachineOutput, ItemStack>> entries) {
            List<MachineOutput> values = new ArrayList<>(entries.size());
            boolean canonical = true;
            for (Either<MachineOutput, ItemStack> entry : entries) {
                if (entry.left().isPresent()) {
                    values.add(entry.left().orElseThrow());
                } else {
                    canonical = false;
                    values.add(new MachineOutput.ItemOutput(entry.right().orElseThrow(), 1F));
                }
            }
            return new OutputField(values, canonical);
        }

        private static List<Either<MachineOutput, ItemStack>> toEntries(OutputField field) {
            return field.values.stream().map(value -> Either.<MachineOutput, ItemStack>left(value)).toList();
        }

        private List<ItemStack> legacyItemStacks() {
            return canonical ? List.of() : OutputRegistry.itemStacks(values);
        }
    }

    private static <E> Codec<List<E>> boundedList(Codec<E> elementCodec, String field) {
        return new Codec<>() {
            @Override
            public <T> DataResult<T> encode(List<E> values, DynamicOps<T> ops, T prefix) {
                    if (values.size() > MAX_LIST_ENTRIES) {
                        return DataResult.error(() -> field + " contains too many entries: " + values.size());
                    }
                    ListBuilder<T> builder = ops.listBuilder();
                    for (E value : values) builder.add(elementCodec.encodeStart(ops, value));
                    return builder.build(prefix);
            }

            @Override
            public <T> DataResult<Pair<List<E>, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getList(input).flatMap(stream -> {
                    List<T> rawValues = new ArrayList<>();
                    stream.accept(rawValues::add);
                    if (rawValues.size() > MAX_LIST_ENTRIES) {
                        return DataResult.error(() -> field + " contains too many entries: " + rawValues.size());
                    }
                    for (int index = 0; index < rawValues.size(); index++) {
                        if (payloadSize(ops, rawValues.get(index)) > MAX_CHILD_PAYLOAD) {
                            return DataResult.error(() -> field + "[" + index + "] payload exceeds limit");
                        }
                    }
                    DataResult<List<E>> decoded = DataResult.success(new ArrayList<>(rawValues.size()));
                    for (T rawValue : rawValues) {
                        decoded = decoded.apply2((values, value) -> {
                            values.add(value);
                            return values;
                        }, elementCodec.parse(ops, rawValue));
                    }
                    return decoded.map(values -> Pair.of(List.copyOf(values), ops.emptyList()));
                });
            }
        };
    }

    private static <T> int payloadSize(DynamicOps<T> ops, T value) {
        try {
            return ops.convertTo(com.mojang.serialization.JsonOps.INSTANCE, value).toString().length();
        } catch (RuntimeException ignored) {
            return MAX_CHILD_PAYLOAD + 1;
        }
    }
}
