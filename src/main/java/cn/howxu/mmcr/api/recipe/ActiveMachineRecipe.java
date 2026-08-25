package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActiveMachineRecipe {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveMachineRecipe.class);
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();

    private final int instanceId = INSTANCE_COUNTER.incrementAndGet();

    private final MachineRecipe recipe;
    private CompoundTag data;
    private int tick;
    private int totalTick;
    private int maxParallelism;
    private int parallelism;
    private int nextFinishRetryTick;
    private boolean finishPending;
    private @Nullable InputConsumptionPlan inputConsumptionPlan;

    public record InputConsumptionPlan(List<Integer> consumedInputBatches) {
        public InputConsumptionPlan {
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
                    .orElseGet(() -> new int[0])).boxed().toList());
        }
    }

    public ActiveMachineRecipe(MachineRecipe recipe) {
        this(recipe, 1);
    }

    public ActiveMachineRecipe(MachineRecipe recipe, int maxParallelism) {
        this.recipe = recipe;
        this.totalTick = recipe == null ? 0 : IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyDuration(recipe.modifiers(), recipe.getRecipeTotalTickTime()));
        this.maxParallelism = Math.max(1, maxParallelism);
        this.parallelism = 1;
        this.data = new CompoundTag();
        if (recipe == null) {
            LOG.warn("ActiveMachineRecipe#{} created with null recipe", instanceId);
        }
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

    public int getMaxParallelism() {
        return maxParallelism;
    }

    public void setMaxParallelism(int maxParallelism) {
        this.maxParallelism = Math.max(1, maxParallelism);
        setParallelism(parallelism);
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
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
        output.putString("recipeName", recipe == null ? "" : recipe.id().toString());
        output.putInt("tick", this.tick);
        output.putInt("totalTick", this.totalTick);
        output.putInt("maxParallelism", this.maxParallelism);
        output.putInt("parallelism", this.parallelism);
        output.putInt("nextFinishRetryTick", this.nextFinishRetryTick);
        output.putBoolean("finishPending", this.finishPending);
        if (inputConsumptionPlan != null) {
            output.store("inputConsumptionPlan", CompoundTag.CODEC, inputConsumptionPlan.serialize());
        }
        if (!data.isEmpty()) {
            output.store("data", CompoundTag.CODEC, data);
        }
    }

    public static ActiveMachineRecipe from(ValueInput input) {
        String recipeName = input.getStringOr("recipeName", "");
        Identifier recipeId = recipeName.isEmpty() ? null : Identifier.parse(recipeName);
        MachineRecipe recipe = RecipeRegistry.getRecipe(recipeId);
        ActiveMachineRecipe result = new ActiveMachineRecipe(recipe, input.getIntOr("maxParallelism", 1));
        result.tick = input.getIntOr("tick", 0);
        result.totalTick = input.getIntOr("totalTick", 0);
        result.nextFinishRetryTick = input.getIntOr("nextFinishRetryTick", 0);
        result.finishPending = input.getBooleanOr("finishPending", false);
        result.inputConsumptionPlan = input.read("inputConsumptionPlan", CompoundTag.CODEC)
                .map(InputConsumptionPlan::deserialize).orElse(null);
        result.setParallelism(input.getIntOr("parallelism", 1));
        result.data = input.read("data", CompoundTag.CODEC).orElseGet(CompoundTag::new);
        return result;
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
