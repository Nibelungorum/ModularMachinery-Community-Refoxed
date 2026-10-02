package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActiveMachineRecipe {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveMachineRecipe.class);
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    private static final int RECIPE_DEFINITION_VERSION = 3;
    private static final int EFFECTIVE_EXECUTION_SNAPSHOT_VERSION = 1;
    private static final String EFFECTIVE_DEFINITION_MARKER = "has_effective_definition";
    private static final String EFFECTIVE_DEFINITION_VERSION = "effective_definition_version";

    private final int instanceId = INSTANCE_COUNTER.incrementAndGet();

    private final MachineRecipe recipe;
    private CompoundTag data;
    private int tick;
    private int totalTick;
    private long maxParallelism;
    private long parallelism;
    private int nextFinishRetryTick;
    private boolean finishPending;
    private @Nullable InputConsumptionPlan inputConsumptionPlan;
    private List<MachineRequirement> effectiveRequirements;
    private List<MachineOutput> effectiveOutputs;
    private boolean effectiveSnapshotPresent;
    private long effectiveExecutionRevision;

    public record InputConsumptionPlan(List<Integer> consumedInputBatches) {
        public InputConsumptionPlan {
            if (consumedInputBatches == null || consumedInputBatches.stream()
                    .anyMatch(batch -> batch == null || batch < 0 || batch > 1)) {
                throw new IllegalArgumentException("Input consumption batches must be zero or one");
            }
            consumedInputBatches = List.copyOf(consumedInputBatches);
        }

        public int consumedBatches(int requirementIndex) {
            return requirementIndex < consumedInputBatches.size() ? consumedInputBatches.get(requirementIndex) : 0;
        }

        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("consumedInputBatches", consumedInputBatches.stream().mapToInt(Integer::intValue).toArray());
            return tag;
        }

        public static InputConsumptionPlan deserialize(CompoundTag tag) {
            if (!tag.contains("consumedInputBatches", Tag.TAG_INT_ARRAY)) {
                throw new IllegalArgumentException("Missing input consumption batches");
            }
            return new InputConsumptionPlan(Arrays.stream(tag.getIntArray("consumedInputBatches")).boxed().toList());
        }

        public boolean isValidFor(MachineRecipe recipe) {
            return recipe != null && isValidFor(recipe.requirements());
        }

        public boolean isValidFor(List<MachineRequirement> requirements) {
            if (requirements == null || consumedInputBatches.size() != requirements.size()) return false;
            for (int index = 0; index < consumedInputBatches.size(); index++) {
                MachineRequirement requirement = requirements.get(index);
                if (requirement == null || requirement.io() == null) return false;
                boolean consumable = requirement.io() == RecipeModifier.IOType.INPUT
                        && (requirement instanceof ItemRequirement || requirement instanceof FluidRequirement
                        || requirement instanceof LoadedChemicalRequirement);
                if (!consumable && consumedInputBatches.get(index) != 0) return false;
            }
            return true;
        }
    }

    public record LoadResult(@Nullable ActiveMachineRecipe recipe) {
        public boolean successful() {
            return recipe != null;
        }
    }

    public ActiveMachineRecipe(MachineRecipe recipe) {
        this(recipe, 1);
    }

    public ActiveMachineRecipe(MachineRecipe recipe, long maxParallelism) {
        this(recipe, maxParallelism, false);
    }

    private ActiveMachineRecipe(MachineRecipe recipe, long maxParallelism, boolean effectiveSnapshotPresent) {
        this.recipe = recipe;
        this.totalTick = recipe == null ? 0 : IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyDuration(recipe.modifiers(), recipe.getRecipeTotalTickTime()));
        this.maxParallelism = Math.max(1, maxParallelism);
        this.parallelism = 1;
        this.data = new CompoundTag();
        this.effectiveRequirements = recipe == null ? List.of() : MachineRequirement.copyList(recipe.runtimeRequirements());
        this.effectiveOutputs = recipe == null ? List.of() : MachineOutput.copyList(recipe.runtimeMachineOutputs());
        this.effectiveSnapshotPresent = effectiveSnapshotPresent && recipe != null;
        if (recipe == null) {
            LOG.warn("ActiveMachineRecipe#{} created with null recipe", instanceId);
        }
    }

    public ActiveMachineRecipe(MachineRecipe recipe, long maxParallelism,
                               RecipeStartContext.ExecutionSnapshot execution) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(execution, "execution");
        this.totalTick = execution.duration();
        this.maxParallelism = Math.max(1, maxParallelism);
        this.parallelism = 1;
        this.data = new CompoundTag();
        this.effectiveRequirements = MachineRequirement.copyList(execution.requirements());
        this.effectiveOutputs = MachineOutput.copyList(execution.outputs());
        this.effectiveSnapshotPresent = true;
    }

    public MachineRecipe getRecipe() {
        return recipe;
    }

    public int getTick() {
        return tick;
    }

    public void setTick(int tick) {
        if (tick >= 0) {
            this.tick = tick;
        }
    }

    public int getTotalTick() {
        return totalTick;
    }

    public void setTotalTick(int totalTick) {
        this.totalTick = Math.max(0, totalTick);
    }

    public long getMaxParallelism() {
        return maxParallelism;
    }

    public void setMaxParallelism(long maxParallelism) {
        this.maxParallelism = Math.max(1, maxParallelism);
        setParallelism(parallelism);
    }

    public long getParallelism() {
        return parallelism;
    }

    public void setParallelism(long parallelism) {
        this.parallelism = Math.max(1, Math.min(parallelism, maxParallelism));
    }

    @Nullable
    public String getRegistryName() {
        return recipe == null ? null : recipe.id().getPath();
    }

    public CompoundTag getDataCompound() {
        return data;
    }

    public void setDataCompound(CompoundTag data) {
        this.data = data == null ? new CompoundTag() : data;
    }

    public void reset() {
        this.tick = 0;
        this.parallelism = 1;
        this.maxParallelism = 1;
        this.data = new CompoundTag();
        this.inputConsumptionPlan = null;
        this.finishPending = false;
    }

    public boolean isCompleted() {
        return this.tick >= totalTick;
    }

    public void doFailureAction(RecipeFailureActions action) {
        if (action == null) action = RecipeFailureActions.getDefaultAction();
        switch (action) {
            case RESET -> this.tick = 0;
            case DECREASE -> { if (this.tick > 0) this.tick--; }
            case STILL -> { /* no-op */ }
        }
    }

    public void serialize(CompoundTag output, HolderLookup.Provider registries) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(registries, "registries");
        output.putString("recipeName", recipe == null ? "" : recipe.id().toString());
        output.putInt("tick", this.tick);
        output.putInt("totalTick", this.totalTick);
        output.putLong("maxParallelism", this.maxParallelism);
        output.putLong("parallelism", this.parallelism);
        output.putInt("nextFinishRetryTick", this.nextFinishRetryTick);
        output.putBoolean("finishPending", this.finishPending);
        if (effectiveSnapshotPresent) {
            output.putBoolean("has_effective_definition", true);
            output.putInt("effective_definition_version", EFFECTIVE_EXECUTION_SNAPSHOT_VERSION);
            output.putInt("effective_duration", totalTick);
            put(output, "effective_requirements", MachineRequirement.CODEC.listOf(), effectiveRequirements, registries);
            put(output, "effective_outputs", MachineOutput.CODEC.listOf(), effectiveOutputs, registries);
        }
        if (recipe != null) {
            String fingerprint = definitionFingerprint(recipe, registries);
            output.putBoolean("has_recipe_definition", true);
            output.putInt("recipe_definition_version", RECIPE_DEFINITION_VERSION);
            output.putString("recipe_definition_fingerprint", fingerprint);
            put(output, "recipe_definition", MachineRecipe.CODEC.codec(), recipe, registries);
        }
        if (inputConsumptionPlan != null) {
            output.putBoolean("has_input_consumption_plan", true);
            output.put("inputConsumptionPlan", inputConsumptionPlan.serialize());
        }
        if (!data.isEmpty()) {
            output.put("data", data.copy());
        }
    }

    public static @Nullable ActiveMachineRecipe from(CompoundTag input, HolderLookup.Provider registries) {
        return load(input, registries).recipe();
    }

    public static LoadResult load(CompoundTag input, HolderLookup.Provider registries) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(registries, "registries");
        return load(input, registries, null);
    }

    public static LoadResult loadForPool(CompoundTag input, HolderLookup.Provider registries,
                                         ResourceLocation recipePoolId) {
        return load(input, registries, Objects.requireNonNull(recipePoolId, "recipePoolId"));
    }

    private static LoadResult load(CompoundTag input, HolderLookup.Provider registries,
                                   @Nullable ResourceLocation recipePoolId) {
        String recipeName = input.getString("recipeName");
        ResourceLocation recipeId;
        try {
            recipeId = recipeName.isEmpty() ? null : ResourceLocation.parse(recipeName);
        } catch (IllegalArgumentException exception) {
            return new LoadResult(null);
        }
        if (recipeId == null) return new LoadResult(null);
        MachineRecipe recipe;
        if (input.getBoolean("has_recipe_definition")) {
            int definitionVersion = input.contains("recipe_definition_version")
                    ? input.getInt("recipe_definition_version") : -1;
            if (definitionVersion != RECIPE_DEFINITION_VERSION) {
                return new LoadResult(null);
            }
            if (registries == null) {
                return new LoadResult(null);
            }
            try {
                recipe = get(input, "recipe_definition", MachineRecipe.CODEC.codec(), registries);
            } catch (RuntimeException exception) {
                return new LoadResult(null);
            }
            if (recipe == null || recipeId == null || !recipeId.equals(recipe.id())) {
                return new LoadResult(null);
            }
            String expectedFingerprint = input.getString("recipe_definition_fingerprint");
            String actualFingerprint;
            try {
                actualFingerprint = definitionFingerprint(recipe, registries);
            } catch (IllegalStateException exception) {
                return new LoadResult(null);
            }
            if (!expectedFingerprint.equals(actualFingerprint)) {
                return new LoadResult(null);
            }
        } else {
            recipe = recipePoolId == null ? RecipeRegistry.getRecipe(recipeId)
                    : RecipeRegistry.catalogForPool(recipePoolId).recipes().stream()
                    .filter(candidate -> recipeId.equals(candidate.id())).findFirst().orElse(null);
        }
        if (recipe == null || recipePoolId != null && (!recipePoolId.equals(recipe.recipePoolId())
                || input.getBoolean("has_recipe_definition")
                && RecipeRegistry.catalogForPool(recipePoolId).recipes().stream()
                .noneMatch(candidate -> sameDefinition(recipe, candidate, registries)))) {
            return new LoadResult(null);
        }
        List<MachineRequirement> effectiveRequirements = null;
        List<MachineOutput> effectiveOutputs = null;
        int effectiveDuration = -1;
        boolean hasSnapshotMarker = hasField(input, EFFECTIVE_DEFINITION_MARKER);
        boolean snapshotMarker = input.getBoolean(EFFECTIVE_DEFINITION_MARKER);
        boolean hasSnapshotVersion = hasField(input, EFFECTIVE_DEFINITION_VERSION);
        boolean hasEffectivePayload = hasField(input, "effective_duration")
                || hasField(input, "effective_requirements")
                || hasField(input, "effective_outputs")
                || hasSnapshotVersion;
        if (!snapshotMarker && hasEffectivePayload) {
            return new LoadResult(null);
        }
        if (snapshotMarker) {
            if (!hasSnapshotVersion
                    || (input.contains(EFFECTIVE_DEFINITION_VERSION) ? input.getInt(EFFECTIVE_DEFINITION_VERSION) : -1)
                    != EFFECTIVE_EXECUTION_SNAPSHOT_VERSION) {
                return new LoadResult(null);
            }
            try {
                effectiveDuration = input.contains("effective_duration") ? input.getInt("effective_duration") : -1;
                effectiveRequirements = get(input, "effective_requirements", MachineRequirement.CODEC.listOf(), registries);
                effectiveOutputs = get(input, "effective_outputs", MachineOutput.CODEC.listOf(), registries);
            } catch (RuntimeException exception) {
                return new LoadResult(null);
            }
            if (effectiveDuration <= 0 || !validRequirements(effectiveRequirements)
                    || !validOutputs(effectiveOutputs)) {
                return new LoadResult(null);
            }
        }
        InputConsumptionPlan inputPlan = null;
        boolean hasInputConsumptionPlan = hasField(input, "has_input_consumption_plan")
                || hasField(input, "inputConsumptionPlan");
        if (snapshotMarker && !hasInputConsumptionPlan) {
            return new LoadResult(null);
        }
        if (hasInputConsumptionPlan) {
            try {
                inputPlan = InputConsumptionPlan.deserialize(input.getCompound("inputConsumptionPlan"));
            } catch (RuntimeException exception) {
                return new LoadResult(null);
            }
            if (inputPlan == null || (snapshotMarker
                    && !inputPlan.isValidFor(effectiveRequirements))) return new LoadResult(null);
        }
        long maxParallelism = input.contains("maxParallelism") ? input.getLong("maxParallelism") : 1L;
        long parallelism = input.contains("parallelism") ? input.getLong("parallelism") : 1L;
        int serializedTotalTick = input.contains("totalTick") ? input.getInt("totalTick") : -1;
        int tick = input.getInt("tick");
        boolean finishPending = input.getBoolean("finishPending");
        int totalTick = snapshotMarker ? effectiveDuration : serializedTotalTick;
        if (serializedTotalTick < 1 || !validRuntimeState(tick, totalTick, maxParallelism, parallelism,
                finishPending)) {
            return new LoadResult(null);
        }
        ActiveMachineRecipe result = snapshotMarker
                ? new ActiveMachineRecipe(recipe, maxParallelism,
                new RecipeStartContext.ExecutionSnapshot(effectiveDuration,
                        MachineRequirement.copyList(effectiveRequirements),
                        effectiveOutputs))
                : new ActiveMachineRecipe(recipe, maxParallelism, false);
        result.tick = tick;
        result.totalTick = totalTick;
        result.nextFinishRetryTick = input.getInt("nextFinishRetryTick");
        result.finishPending = finishPending;
        result.inputConsumptionPlan = inputPlan;
        result.parallelism = parallelism;
        result.data = input.getCompound("data").copy();
        return new LoadResult(result);
    }

    public boolean hasValidInputConsumptionPlan() {
        return hasEffectiveExecutionSnapshot()
                ? hasValidInputConsumptionPlan(effectiveRequirements)
                : inputConsumptionPlan == null || inputConsumptionPlan.isValidFor(recipe);
    }

    public boolean hasValidInputConsumptionPlan(List<MachineRequirement> requirements) {
        return hasEffectiveExecutionSnapshot()
                ? inputConsumptionPlan != null && inputConsumptionPlan.isValidFor(requirements)
                : inputConsumptionPlan == null || inputConsumptionPlan.isValidFor(requirements);
    }

    public boolean hasEffectiveExecutionSnapshot() {
        return effectiveSnapshotPresent;
    }

    public long effectiveExecutionRevision() {
        return effectiveExecutionRevision;
    }

    public List<MachineRequirement> effectiveRequirements() {
        return MachineRequirement.copyList(effectiveRequirements);
    }

    public List<MachineOutput> effectiveOutputs() {
        return MachineOutput.copyList(effectiveOutputs);
    }

    public RecipeStartContext.ExecutionSnapshot executionSnapshot() {
        return new RecipeStartContext.ExecutionSnapshot(
                totalTick, MachineRequirement.copyList(effectiveRequirements()), effectiveOutputs());
    }

    /**
     * Publishes the effective runtime definition selected during restore.
     */
    public void setEffectiveExecutionSnapshot(RecipeStartContext.ExecutionSnapshot execution) {
        Objects.requireNonNull(execution, "execution");
        this.totalTick = execution.duration();
        this.effectiveRequirements = MachineRequirement.copyList(execution.requirements().stream()
                .map(MachineRequirement::copyOf).toList());
        this.effectiveOutputs = MachineOutput.copyList(execution.outputs());
        this.effectiveSnapshotPresent = true;
        this.effectiveExecutionRevision++;
    }

    private static boolean validRequirements(List<MachineRequirement> requirements) {
        if (requirements == null) return false;
        return requirements.stream().allMatch(ActiveMachineRecipe::validRequirement);
    }

    private static boolean validRequirement(MachineRequirement requirement) {
        try {
            if (requirement == null || requirement.io() == null || requirement.type() == null
                    || RequirementHandlerRegistry.handlerFor(requirement.type()) == null) return false;
            if (requirement instanceof ItemRequirement item) {
                if (item.io() == RecipeModifier.IOType.INPUT) return item.item() != null && item.count() >= 0;
                return validItemStack(item.stack(null)) && validChance(item.chance());
            }
            if (requirement instanceof FluidRequirement fluid) {
                if (fluid.io() == RecipeModifier.IOType.INPUT) return fluid.fluid() != null && fluid.amount() >= 0;
                return validFluidStack(fluid.stack()) && validChance(fluid.chance());
            }
            if (requirement instanceof EnergyRequirement energy) {
                return energy.fePerTick() >= 0;
            }
            return requirement instanceof SmartInterfaceRequirement || requirement.io() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean validOutputs(List<MachineOutput> outputs) {
        if (outputs == null) return false;
        for (MachineOutput output : outputs) {
            if (!OutputRegistry.isCanonical(output) || !validChance(output.chance())) return false;
            if (output instanceof MachineOutput.ItemOutput item && !validItemStack(item.stack())) return false;
            if (output instanceof MachineOutput.FluidOutput fluid && !validFluidStack(fluid.stack())) return false;
        }
        return true;
    }

    private static boolean validItemStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() > 0;
    }

    private static boolean validFluidStack(FluidStack stack) {
        return stack != null && !stack.isEmpty() && stack.getAmount() > 0;
    }

    private static boolean validChance(float chance) {
        return Float.isFinite(chance) && chance >= 0F && chance <= 1F;
    }

    private static boolean validRuntimeState(int tick, int totalTick, long maxParallelism,
                                             long parallelism, boolean finishPending) {
        return totalTick >= 1
                && tick >= 0 && tick <= totalTick
                && maxParallelism >= 1
                && parallelism >= 1 && parallelism <= maxParallelism
                && (!finishPending || tick == totalTick - 1);
    }

    public static boolean sameDefinition(MachineRecipe first, MachineRecipe second) {
        if (first == null || second == null) return false;
        try {
            return definitionFingerprint(first, null).equals(definitionFingerprint(second, null));
        } catch (IllegalStateException exception) {
            return first.equals(second);
        }
    }

    public static boolean sameDefinition(MachineRecipe first, MachineRecipe second,
                                         @Nullable HolderLookup.Provider registries) {
        if (first == null || second == null) return false;
        try {
            return registries == null
                    ? sameDefinition(first, second)
                    : definitionFingerprint(first, registries).equals(definitionFingerprint(second, registries));
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private static boolean hasField(CompoundTag input, String field) {
        return input.contains(field);
    }

    private static <T> void put(CompoundTag output, String field, Codec<T> codec, T value,
                                HolderLookup.Provider registries) {
        output.put(field, codec.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), value).getOrThrow());
    }

    private static <T> @Nullable T get(CompoundTag input, String field, Codec<T> codec,
                                        HolderLookup.Provider registries) {
        if (!input.contains(field)) return null;
        return codec.parse(RegistryOps.create(NbtOps.INSTANCE, registries), input.get(field)).result().orElse(null);
    }

    private static String definitionFingerprint(MachineRecipe recipe, @Nullable HolderLookup.Provider registries) {
        var ops = registries == null ? NbtOps.INSTANCE : RegistryOps.create(NbtOps.INSTANCE, registries);
        CompoundTag encoded = (CompoundTag) MachineRecipe.CODEC.codec()
                .encodeStart(ops, recipe).getOrThrow();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        updateTag(digest, encoded);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateTag(MessageDigest digest, Tag tag) {
        digest.update(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_BYTE -> digest.update(((NumericTag) tag).getAsByte());
            case Tag.TAG_SHORT -> updateShort(digest, ((NumericTag) tag).getAsShort());
            case Tag.TAG_INT -> updateInt(digest, ((NumericTag) tag).getAsInt());
            case Tag.TAG_LONG -> updateLong(digest, ((NumericTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> updateInt(digest, Float.floatToIntBits(((NumericTag) tag).getAsFloat()));
            case Tag.TAG_DOUBLE -> updateLong(digest, Double.doubleToLongBits(((NumericTag) tag).getAsDouble()));
            case Tag.TAG_BYTE_ARRAY -> {
                byte[] values = ((ByteArrayTag) tag).getAsByteArray();
                updateInt(digest, values.length);
                digest.update(values);
            }
            case Tag.TAG_STRING -> updateString(digest, tag.getAsString());
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                updateInt(digest, list.size());
                for (Tag element : list) updateTag(digest, element);
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = (CompoundTag) tag;
                List<String> keys = new ArrayList<>(compound.getAllKeys());
                keys.sort(String::compareTo);
                updateInt(digest, keys.size());
                for (String key : keys) {
                    updateString(digest, key);
                    updateTag(digest, compound.get(key));
                }
            }
            case Tag.TAG_INT_ARRAY -> {
                int[] values = ((IntArrayTag) tag).getAsIntArray();
                updateInt(digest, values.length);
                for (int value : values) updateInt(digest, value);
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = ((LongArrayTag) tag).getAsLongArray();
                updateInt(digest, values.length);
                for (long value : values) updateLong(digest, value);
            }
            default -> throw new IllegalArgumentException("Unsupported recipe fingerprint tag: " + tag.getId());
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateShort(MessageDigest digest, short value) {
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateLong(MessageDigest digest, long value) {
        updateInt(digest, (int) (value >>> 32));
        updateInt(digest, (int) value);
    }

    public enum TickStatus {
        CONTINUE,
        WAITING,
        CANCELLED,
        FINISHED
    }

    public InputConsumptionPlan inputConsumptionPlan() {
        return inputConsumptionPlan == null ? new InputConsumptionPlan(List.of()) : inputConsumptionPlan;
    }

    public void setInputConsumptionPlan(InputConsumptionPlan inputConsumptionPlan) {
        this.inputConsumptionPlan = inputConsumptionPlan;
    }

    public boolean shouldRetryFinish(int gameTime) {
        return gameTime >= nextFinishRetryTick;
    }

    public boolean isFinishPending() {
        return finishPending;
    }

    public void beginFinishCommit() {
        finishPending = true;
    }

    public void markFinishBlocked(int gameTime) {
        nextFinishRetryTick = gameTime + 10;
    }

    public boolean needsFinishCommit() {
        return tick + 1 >= totalTick;
    }

    public TickStatus applyTickGrant(boolean resourcesGranted, boolean outputsCommitted, int gameTime) {
        if (!resourcesGranted) {
            doFailureAction(RecipeFailureActions.STILL);
            return TickStatus.WAITING;
        }
        int nextTick = Math.min(tick + 1, totalTick);
        if (nextTick < totalTick) {
            tick = nextTick;
            return TickStatus.CONTINUE;
        }
        if (!outputsCommitted) {
            tick = Math.max(0, totalTick - 1);
            finishPending = true;
            markFinishBlocked(gameTime);
            return TickStatus.WAITING;
        }
        tick = nextTick;
        finishPending = false;
        return TickStatus.FINISHED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActiveMachineRecipe that)) return false;
        return tick == that.tick
                && totalTick == that.totalTick
                && maxParallelism == that.maxParallelism
                && parallelism == that.parallelism
                && Objects.equals(recipe, that.recipe)
                && Objects.equals(data, that.data)
                && Objects.equals(inputConsumptionPlan, that.inputConsumptionPlan);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipe, tick, totalTick, maxParallelism, parallelism, data, inputConsumptionPlan);
    }
}
