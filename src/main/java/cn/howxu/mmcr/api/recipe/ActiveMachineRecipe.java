package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public ActiveMachineRecipe(CompoundTag serialized) {
        Identifier recipeId = Identifier.parse(serialized.getStringOr("recipeName", ""));
        this.recipe = RecipeRegistry.getRecipe(recipeId);
        this.tick = serialized.getIntOr("tick", 0);
        this.totalTick = serialized.getIntOr("totalTick", 0);
        this.data = serialized.contains("data") ? serialized.getCompoundOrEmpty("data") : new CompoundTag();
        this.nextFinishRetryTick = serialized.getIntOr("nextFinishRetryTick", 0);
        this.finishPending = serialized.getBooleanOr("finishPending", false);
        setMaxParallelism(serialized.getIntOr("maxParallelism", 1));
        setParallelism(serialized.getIntOr("parallelism", 1));
        LOG.info("ActiveMachineRecipe#{} restored from NBT: recipe={} resolved={} tick={}/{} maxParallelism={} parallelism={}",
                instanceId, recipeId, this.recipe == null ? null : this.recipe.id(),
                this.tick, this.totalTick, this.maxParallelism, this.parallelism);
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

    public void refreshTotalTick(RecipeCraftingContext context) {
        this.totalTick = IntegrationTypeHelper.asInt(
                IntegrationTypeHelper.applyDuration(
                        context.effectiveModifiers(recipe), recipe.getRecipeTotalTickTime()));
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
        int before = this.tick;
        this.tick = 0;
        this.parallelism = 1;
        this.maxParallelism = 1;
        this.data = new CompoundTag();
        LOG.info("ActiveMachineRecipe#{} reset: tick {} → 0; parallelism and data cleared (recipe={})",
                instanceId, before, recipe == null ? null : recipe.id());
    }

    public boolean isCompleted() {
        return this.tick >= totalTick;
    }

    public void doFailureAction(RecipeFailureActions action) {
        if (action == null) action = RecipeFailureActions.getDefaultAction();
        int before = this.tick;
        switch (action) {
            case RESET -> this.tick = 0;
            case DECREASE -> { if (this.tick > 0) this.tick--; }
            case STILL -> { /* no-op */ }
        }
        LOG.info("ActiveMachineRecipe#{} doFailureAction({}): tick {} → {} (recipe={})",
                instanceId, action, before, this.tick, recipe == null ? null : recipe.id());
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("recipeName", recipe == null ? "" : recipe.id().toString());
        tag.putInt("tick", this.tick);
        tag.putInt("totalTick", this.totalTick);
        tag.putInt("maxParallelism", this.maxParallelism);
        tag.putInt("parallelism", this.parallelism);
        tag.putInt("nextFinishRetryTick", this.nextFinishRetryTick);
        tag.putBoolean("finishPending", this.finishPending);
        if (!data.isEmpty()) {
            tag.put("data", data);
        }
        LOG.debug("ActiveMachineRecipe#{} serialize(): recipe={} tick={}/{} maxParallelism={} parallelism={} dataKeys={}",
                instanceId, recipe == null ? "" : recipe.id(), tick, totalTick, maxParallelism, parallelism, data.keySet().size());
        return tag;
    }

    public void serialize(ValueOutput output) {
        output.putString("recipeName", recipe == null ? "" : recipe.id().toString());
        output.putInt("tick", this.tick);
        output.putInt("totalTick", this.totalTick);
        output.putInt("maxParallelism", this.maxParallelism);
        output.putInt("parallelism", this.parallelism);
        output.putInt("nextFinishRetryTick", this.nextFinishRetryTick);
        output.putBoolean("finishPending", this.finishPending);
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
        result.setParallelism(input.getIntOr("parallelism", 1));
        result.data = input.read("data", CompoundTag.CODEC).orElseGet(CompoundTag::new);
        LOG.debug("ActiveMachineRecipe#{} from(ValueInput) → recipe={} tick={}/{} maxParallelism={} parallelism={}",
                result.instanceId, recipeId, result.tick, result.totalTick, result.maxParallelism, result.parallelism);
        return result;
    }

    public enum TickStatus {
        CONTINUE,
        WAITING,
        FINISHED
    }

    public boolean start(RecipeCraftingContext context) {
        if (recipe == null) {
            LOG.debug("ActiveMachineRecipe#{} start(): no recipe attached → refused", instanceId);
            return false;
        }
        refreshTotalTick(context);
        return true;
    }

    public boolean canStartCrafting(RecipeCraftingContext context) {
        refreshTotalTick(context);
        return context.canStartCrafting(this);
    }

    public boolean canRestartCrafting(RecipeCraftingContext context) {
        refreshTotalTick(context);
        return context.canRestartCrafting(this);
    }

    private int highestStartableParallelism(RecipeCraftingContext context) {
        return Math.max(1, ParallelRecipeCalculator.maxStartableParallelism(context, recipe, maxParallelism));
    }

    public boolean shouldRetryFinish(int gameTime) {
        return gameTime >= nextFinishRetryTick;
    }

    public boolean isFinishPending() {
        return finishPending;
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
                && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipe, tick, totalTick, maxParallelism, parallelism, data);
    }
}
