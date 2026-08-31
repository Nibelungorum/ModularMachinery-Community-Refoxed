package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.publicapi.machine.RecipeStartContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
    private static final int LEGACY_RECIPE_DEFINITION_VERSION = 1;
    private static final int RECIPE_DEFINITION_VERSION = 2;
    private static final int EFFECTIVE_EXECUTION_SNAPSHOT_VERSION = 1;
    private static final String EFFECTIVE_DEFINITION_MARKER = "has_effective_definition";
    private static final String EFFECTIVE_DEFINITION_VERSION = "effective_definition_version";
    private static final String LEGACY_EFFECTIVE_SNAPSHOT_MARKER = "has_effective_execution_snapshot";
    private static final String LEGACY_EFFECTIVE_SNAPSHOT_VERSION = "effective_execution_snapshot_version";

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
            return new InputConsumptionPlan(Arrays.stream(tag.getIntArray("consumedInputBatches")
                    .orElseThrow(() -> new IllegalArgumentException("Missing input consumption batches"))).boxed().toList());
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
                        && (requirement instanceof ItemRequirement || requirement instanceof FluidRequirement);
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

    public void serialize(ValueOutput output) {
        serialize(output, null);
    }

    public void serialize(ValueOutput output, @Nullable HolderLookup.Provider registries) {
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
            output.store("effective_requirements", MachineRequirement.CODEC.listOf(), effectiveRequirements);
            output.store("effective_outputs", MachineOutput.CODEC.listOf(), effectiveOutputs);
        }
        if (recipe != null && registries != null) {
            String fingerprint = definitionFingerprint(recipe, registries);
            output.putBoolean("has_recipe_definition", true);
            output.putInt("recipe_definition_version", RECIPE_DEFINITION_VERSION);
            output.putString("recipe_definition_fingerprint", fingerprint);
            output.store("recipe_definition", MachineRecipe.CODEC.codec(), recipe);
        }
        if (inputConsumptionPlan != null) {
            output.putBoolean("has_input_consumption_plan", true);
            output.store("inputConsumptionPlan", CompoundTag.CODEC, inputConsumptionPlan.serialize());
        }
        if (!data.isEmpty()) {
            output.store("data", CompoundTag.CODEC, data);
        }
    }

    public static @Nullable ActiveMachineRecipe from(ValueInput input) {
        return load(input).recipe();
    }

    public static LoadResult load(ValueInput input) {
        HolderLookup.Provider registries = input.lookup();
        String recipeName = input.getStringOr("recipeName", "");
        Identifier recipeId;
        try {
            recipeId = recipeName.isEmpty() ? null : Identifier.parse(recipeName);
        } catch (IllegalArgumentException exception) {
            return new LoadResult(null);
        }
        MachineRecipe recipe;
        if (input.getBooleanOr("has_recipe_definition", false)) {
            int definitionVersion = input.getIntOr("recipe_definition_version", -1);
            if (definitionVersion != LEGACY_RECIPE_DEFINITION_VERSION
                    && definitionVersion != RECIPE_DEFINITION_VERSION) {
                return new LoadResult(null);
            }
            if (definitionVersion == RECIPE_DEFINITION_VERSION && registries == null) {
                return new LoadResult(null);
            }
            try {
                recipe = input.read("recipe_definition", MachineRecipe.CODEC.codec()).orElse(null);
            } catch (RuntimeException exception) {
                return new LoadResult(null);
            }
            if (recipe == null || recipeId == null || !recipeId.equals(recipe.id())) {
                return new LoadResult(null);
            }
            String expectedFingerprint = input.getStringOr("recipe_definition_fingerprint", "");
            String actualFingerprint;
            try {
                actualFingerprint = definitionFingerprint(recipe,
                        definitionVersion == LEGACY_RECIPE_DEFINITION_VERSION ? null : registries);
            } catch (IllegalStateException exception) {
                return new LoadResult(null);
            }
            if (!expectedFingerprint.equals(actualFingerprint)) {
                return new LoadResult(null);
            }
        } else {
            recipe = RecipeRegistry.getRecipe(recipeId);
        }
        if (recipe == null) return new LoadResult(null);
        List<MachineRequirement> effectiveRequirements = null;
        List<MachineOutput> effectiveOutputs = null;
        int effectiveDuration = -1;
        boolean hasCurrentSnapshotMarker = hasField(input, EFFECTIVE_DEFINITION_MARKER);
        boolean hasLegacySnapshotMarker = hasField(input, LEGACY_EFFECTIVE_SNAPSHOT_MARKER);
        boolean currentSnapshotMarker = input.getBooleanOr(EFFECTIVE_DEFINITION_MARKER, false);
        boolean legacySnapshotMarker = input.getBooleanOr(LEGACY_EFFECTIVE_SNAPSHOT_MARKER, false);
        boolean hasCurrentSnapshotVersion = hasField(input, EFFECTIVE_DEFINITION_VERSION);
        boolean hasLegacySnapshotVersion = hasField(input, LEGACY_EFFECTIVE_SNAPSHOT_VERSION);
        boolean hasEffectivePayload = hasField(input, "effective_duration")
                || hasField(input, "effective_requirements")
                || hasField(input, "effective_outputs")
                || hasCurrentSnapshotVersion
                || hasLegacySnapshotVersion;
        if (hasCurrentSnapshotMarker && hasLegacySnapshotMarker
                && currentSnapshotMarker != legacySnapshotMarker) {
            return new LoadResult(null);
        }
        if (hasCurrentSnapshotVersion && hasLegacySnapshotVersion
                && input.getIntOr(EFFECTIVE_DEFINITION_VERSION, -1)
                != input.getIntOr(LEGACY_EFFECTIVE_SNAPSHOT_VERSION, -1)) {
            return new LoadResult(null);
        }
        boolean hasEffectiveExecutionSnapshot = currentSnapshotMarker || legacySnapshotMarker;
        if (!hasEffectiveExecutionSnapshot && hasEffectivePayload) {
            return new LoadResult(null);
        }
        if (currentSnapshotMarker && hasLegacySnapshotVersion && !hasLegacySnapshotMarker) {
            return new LoadResult(null);
        }
        if (legacySnapshotMarker && hasCurrentSnapshotVersion && !hasCurrentSnapshotMarker) {
            return new LoadResult(null);
        }
        if (hasEffectiveExecutionSnapshot) {
            if ((currentSnapshotMarker && (!hasCurrentSnapshotVersion
                    || input.getIntOr(EFFECTIVE_DEFINITION_VERSION, -1)
                    != EFFECTIVE_EXECUTION_SNAPSHOT_VERSION))
                    || (legacySnapshotMarker && (!hasLegacySnapshotVersion
                    || input.getIntOr(LEGACY_EFFECTIVE_SNAPSHOT_VERSION, -1)
                    != EFFECTIVE_EXECUTION_SNAPSHOT_VERSION))) {
                return new LoadResult(null);
            }
            try {
                effectiveDuration = input.getIntOr("effective_duration", -1);
                effectiveRequirements = input.read("effective_requirements", MachineRequirement.CODEC.listOf())
                        .orElse(null);
                effectiveOutputs = input.read("effective_outputs", MachineOutput.CODEC.listOf()).orElse(null);
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
        if (hasEffectiveExecutionSnapshot && !hasInputConsumptionPlan) {
            return new LoadResult(null);
        }
        if (hasInputConsumptionPlan) {
            try {
                inputPlan = input.read("inputConsumptionPlan", CompoundTag.CODEC)
                        .map(InputConsumptionPlan::deserialize).orElse(null);
            } catch (RuntimeException exception) {
                return new LoadResult(null);
            }
            List<MachineRequirement> validationRequirements = effectiveRequirements == null
                    ? recipe.requirements() : effectiveRequirements;
            if (inputPlan == null || !inputPlan.isValidFor(validationRequirements)) return new LoadResult(null);
        }
        long maxParallelism = input.getLongOr("maxParallelism", 1L);
        long parallelism = input.getLongOr("parallelism", 1L);
        int serializedTotalTick = input.getIntOr("totalTick", -1);
        int tick = input.getIntOr("tick", 0);
        boolean finishPending = input.getBooleanOr("finishPending", false);
        int totalTick = hasEffectiveExecutionSnapshot ? effectiveDuration : serializedTotalTick;
        if (serializedTotalTick < 1 || !validRuntimeState(tick, totalTick, maxParallelism, parallelism,
                finishPending)) {
            return new LoadResult(null);
        }
        ActiveMachineRecipe result = hasEffectiveExecutionSnapshot
                ? new ActiveMachineRecipe(recipe, maxParallelism,
                new RecipeStartContext.ExecutionSnapshot(effectiveDuration, effectiveRequirements, effectiveOutputs))
                : new ActiveMachineRecipe(recipe, maxParallelism, false);
        result.tick = tick;
        result.totalTick = totalTick;
        result.nextFinishRetryTick = input.getIntOr("nextFinishRetryTick", 0);
        result.finishPending = finishPending;
        result.inputConsumptionPlan = inputPlan;
        result.parallelism = parallelism;
        result.data = input.read("data", CompoundTag.CODEC).orElseGet(CompoundTag::new);
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

    public List<MachineRequirement> effectiveRequirements() {
        return MachineRequirement.copyList(effectiveRequirements);
    }

    public List<MachineOutput> effectiveOutputs() {
        return MachineOutput.copyList(effectiveOutputs);
    }

    public RecipeStartContext.ExecutionSnapshot executionSnapshot() {
        return new RecipeStartContext.ExecutionSnapshot(
                totalTick, effectiveRequirements(), effectiveOutputs());
    }

    /**
     * Promotes a legacy active recipe to the effective runtime definition selected during restore.
     */
    public void setEffectiveExecutionSnapshot(RecipeStartContext.ExecutionSnapshot execution) {
        Objects.requireNonNull(execution, "execution");
        this.totalTick = execution.duration();
        this.effectiveRequirements = MachineRequirement.copyList(execution.requirements());
        this.effectiveOutputs = MachineOutput.copyList(execution.outputs());
        this.effectiveSnapshotPresent = true;
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

    private static boolean hasField(ValueInput input, String field) {
        return input.getBooleanOr(field, false)
                || input.child(field).isPresent()
                || input instanceof TagValueInput tagInput && tagInput.keySet().contains(field);
    }

    private static String definitionFingerprint(MachineRecipe recipe, @Nullable HolderLookup.Provider registries) {
        var ops = registries == null ? NbtOps.INSTANCE : RegistryOps.create(NbtOps.INSTANCE, registries);
        CompoundTag encoded = (CompoundTag) MachineRecipe.CODEC.codec()
                .encodeStart(ops, recipe).getOrThrow();
        encoded.remove("inputs");
        encoded.remove("outputs");
        encoded.remove("fluid_outputs");
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
            case Tag.TAG_BYTE -> digest.update(((NumericTag) tag).byteValue());
            case Tag.TAG_SHORT -> updateShort(digest, ((NumericTag) tag).shortValue());
            case Tag.TAG_INT -> updateInt(digest, ((NumericTag) tag).intValue());
            case Tag.TAG_LONG -> updateLong(digest, ((NumericTag) tag).longValue());
            case Tag.TAG_FLOAT -> updateInt(digest, Float.floatToIntBits(((NumericTag) tag).floatValue()));
            case Tag.TAG_DOUBLE -> updateLong(digest, Double.doubleToLongBits(((NumericTag) tag).doubleValue()));
            case Tag.TAG_BYTE_ARRAY -> {
                byte[] values = tag.asByteArray().orElseThrow();
                updateInt(digest, values.length);
                digest.update(values);
            }
            case Tag.TAG_STRING -> updateString(digest, tag.asString().orElseThrow());
            case Tag.TAG_LIST -> {
                ListTag list = tag.asList().orElseThrow();
                updateInt(digest, list.size());
                for (Tag element : list) updateTag(digest, element);
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = tag.asCompound().orElseThrow();
                List<String> keys = new ArrayList<>(compound.keySet());
                keys.sort(String::compareTo);
                updateInt(digest, keys.size());
                for (String key : keys) {
                    updateString(digest, key);
                    updateTag(digest, compound.get(key));
                }
            }
            case Tag.TAG_INT_ARRAY -> {
                int[] values = tag.asIntArray().orElseThrow();
                updateInt(digest, values.length);
                for (int value : values) updateInt(digest, value);
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = tag.asLongArray().orElseThrow();
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
