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
        setParallelism(highestStartableParallelism(context));
        boolean started = context.startCrafting(recipe, parallelism);
        if (started) {
            refreshTotalTick(context);
        }
        LOG.info("ActiveMachineRecipe#{} start(): recipe {} started={} totalTick={}", instanceId, recipe.id(), started, totalTick);
        return started;
    }

    private int highestStartableParallelism(RecipeCraftingContext context) {
        if (!recipe.isParallelized() || maxParallelism <= 1) return 1;
        int low = 1;
        int high = maxParallelism;
        int best = 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (context.simulateInputs(recipe, mid) && context.simulateOutputs(recipe, mid)) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    public TickStatus tick(RecipeCraftingContext context) {
        if (recipe == null) {
            LOG.debug("ActiveMachineRecipe#{} tick(): no recipe attached → WAITING", instanceId);
            return TickStatus.WAITING;
        }
        int total = getTotalTick();
        if (total <= 0) {
            LOG.debug("ActiveMachineRecipe#{} tick(): recipe {} totalTick <=0 → WAITING", instanceId, recipe.id());
            return TickStatus.WAITING;
        }
        int beforeTick = getTick();
        int nextTick = Math.min(beforeTick + 1, total);
        if (nextTick >= total) {
            if (!context.simulateOutputs(recipe, parallelism)) {
                LOG.info("ActiveMachineRecipe#{} tick(): recipe {} outputs unavailable before completion → WAITING at tick {}",
                        instanceId, recipe.id(), beforeTick);
                return TickStatus.WAITING;
            }
        }
        if (!context.ioTick(recipe, parallelism)) {
            doFailureAction(context.failureAction());
            LOG.info("ActiveMachineRecipe#{} tick(): recipe {} ioTick refused at tick {} → WAITING", instanceId, recipe.id(), beforeTick);
            return TickStatus.WAITING;
        }
        setTick(nextTick);

        if (!isCompleted()) {
            return TickStatus.CONTINUE;
        }

        LOG.info("ActiveMachineRecipe#{} tick(): recipe {} reached completion tick {} of {}; entering final commit phase", instanceId, recipe.id(), nextTick, total);

        boolean outputsOk = context.finishCrafting(recipe, parallelism);
        if (!outputsOk) {
            int restored = Math.max(0, total - 1);
            LOG.info("ActiveMachineRecipe#{} tick(): recipe {} finish failed → rollback tick {} → {} (WAITING)",
                    instanceId, recipe.id(), nextTick, restored);
            setTick(restored);
            return TickStatus.WAITING;
        }

        LOG.info("ActiveMachineRecipe#{} tick(): recipe {} FINISHED at tick {} of {}; outputs committed", instanceId, recipe.id(), nextTick, total);
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
