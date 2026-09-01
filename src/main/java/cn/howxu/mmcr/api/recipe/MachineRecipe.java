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
            boundedList(MachineOutput.CODEC, "outputs").optionalFieldOf("outputs", List.of()).forGetter(MachineRecipe::outputsWithoutDerivedRequirements),
            boundedList(RecipeModifier.CODEC, "modifiers").optionalFieldOf("modifiers", Collections.emptyList()).forGetter(MachineRecipe::modifiers),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MachineRecipe::priority),
            Codec.INT.optionalFieldOf("max_threads", 1).forGetter(MachineRecipe::maxThreads),
            Codec.BOOL.optionalFieldOf("cancelIfPerTickFails", false).forGetter(MachineRecipe::doesCancelRecipeOnPerTickFailure),
            boundedList(MachineRequirement.CODEC, "requirements").fieldOf("requirements").forGetter(MachineRecipe::requirements),
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
                .filter(output -> recipe.outputs.stream().noneMatch(existing -> sameOutputResource(existing, output)))
                .toList();
        this.outputs = appendOutputs(recipe.outputs, newOutputs);
        List<MachineRequirement> newRequirements = new ArrayList<>(recipe.requirements);
        for (MachineOutput output : newOutputs) {
            MachineRequirement requirement = OutputRegistry.tryToRequirement(output, List.of());
            boolean represented = newRequirements.stream()
                    .anyMatch(existing -> OutputRegistry.matchesOutputRequirement(output, existing));
            if (requirement != null && !represented) newRequirements.add(requirement);
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

    /** Adds registered extension outputs to a canonical recipe. */
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
        List<MachineOutput> effectiveOutputs = appendOutputs(outputs, outputsFromRequirements(requirements));
        return new MachineRecipe(id, machineId, tickTime, requirements, effectiveOutputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, parallelized, levelRequirements, allowPartialOutputs, requiredHostIds);
    }

    private static List<MachineOutput> outputsFromRequirements(List<MachineRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) return List.of();
        return requirements.stream()
                .map(OutputRegistry::fromRequirement)
                .filter(Objects::nonNull)
                .toList();
    }

    private static MachineRecipe create(Identifier id,
                                         Identifier machineId,
                                         int tickTime,
                                         List<MachineOutput> outputs,
                                         List<RecipeModifier> modifiers,
                                         int priority,
                                         int maxThreads,
                                         boolean cancelRecipeOnPerTickFailure,
                                         List<MachineRequirement> requirements,
                                         boolean parallelized,
                                         List<LevelRequirement> levelRequirements,
                                         boolean allowPartialOutputs,
                                         Set<Identifier> requiredHostIds) {
         return fromCanonical(id, machineId, tickTime, requirements, outputs, modifiers,
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

    private static List<MachineOutput> appendOutputs(List<MachineOutput> first, List<MachineOutput> second) {
        List<MachineOutput> result = new ArrayList<>(first == null ? List.of() : first);
        if (second != null) {
            for (MachineOutput output : second) {
                if (result.stream().noneMatch(existing -> sameOutput(existing, output))) result.add(output);
            }
        }
        return List.copyOf(result);
    }

    private static boolean sameOutput(MachineOutput first, MachineOutput second) {
        if (first instanceof MachineOutput.ItemOutput firstItem && second instanceof MachineOutput.ItemOutput secondItem) {
            return firstItem.chance() == secondItem.chance() && firstItem.stack().getCount() == secondItem.stack().getCount()
                    && ItemStack.isSameItemSameComponents(firstItem.stack(), secondItem.stack());
        }
        if (first instanceof MachineOutput.FluidOutput firstFluid && second instanceof MachineOutput.FluidOutput secondFluid) {
            return firstFluid.chance() == secondFluid.chance()
                    && firstFluid.stack().getAmount() == secondFluid.stack().getAmount()
                    && FluidStack.isSameFluidSameComponents(firstFluid.stack(), secondFluid.stack());
        }
        return first.equals(second);
    }

    private static boolean sameOutputResource(MachineOutput first, MachineOutput second) {
        if (first instanceof MachineOutput.ItemOutput firstItem && second instanceof MachineOutput.ItemOutput secondItem) {
            return firstItem.stack().getCount() == secondItem.stack().getCount()
                    && ItemStack.isSameItemSameComponents(firstItem.stack(), secondItem.stack());
        }
        if (first instanceof MachineOutput.FluidOutput firstFluid && second instanceof MachineOutput.FluidOutput secondFluid) {
            return firstFluid.stack().getAmount() == secondFluid.stack().getAmount()
                    && FluidStack.isSameFluidSameComponents(firstFluid.stack(), secondFluid.stack());
        }
        return first.equals(second);
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

    public List<MachineOutput> machineOutputs() {
        return outputs;
    }

    private List<MachineOutput> outputsWithoutDerivedRequirements() {
        return outputs.stream().filter(output -> requirements.stream()
                .map(OutputRegistry::fromRequirement)
                .noneMatch(requirementOutput -> requirementOutput != null && sameOutput(output, requirementOutput)))
                .toList();
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
        List<ItemStack> outputs = OutputRegistry.itemStacks(machineOutputs());
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
                            int payloadIndex = index;
                            return DataResult.error(() -> field + "[" + payloadIndex + "] payload exceeds limit");
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
