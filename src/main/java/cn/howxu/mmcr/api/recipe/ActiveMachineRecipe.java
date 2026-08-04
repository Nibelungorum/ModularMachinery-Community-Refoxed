package cn.howxu.mmcr.api.recipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ActiveMachineRecipe {

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
        this.totalTick = recipe == null ? 0 : recipe.getRecipeTotalTickTime();
        this.maxParallelism = Math.max(1, maxParallelism);
        this.parallelism = 1;
        this.data = new CompoundTag();
    }

    public ActiveMachineRecipe(CompoundTag serialized) {
        Identifier recipeId = Identifier.parse(serialized.getStringOr("recipeName", ""));
        this.recipe = RecipeRegistry.getRecipe(recipeId);
        this.tick = serialized.getIntOr("tick", 0);
        this.totalTick = serialized.getIntOr("totalTick", 0);
        this.data = serialized.contains("data") ? serialized.getCompoundOrEmpty("data") : new CompoundTag();
        this.maxParallelism = serialized.getIntOr("maxParallelism", 1);
        this.parallelism = serialized.getIntOr("parallelism", 1);
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
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = Math.max(1, parallelism);
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
    }

    public boolean isCompleted() {
        return this.tick >= totalTick;
    }

    public void doFailureAction(boolean reset) {
        if (reset) {
            this.tick = 0;
        } else if (this.tick > 0) {
            this.tick--;
        }
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
        return tag;
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
