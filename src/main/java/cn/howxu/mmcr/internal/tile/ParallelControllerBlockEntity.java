package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author howxu <dev@howxu.cn>
 */
public class ParallelControllerBlockEntity extends LinkedAppearanceBlockEntity {

    private final ParallelTier tier;
    private int currentParallelism;

    public ParallelControllerBlockEntity(ParallelTier tier, BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get(tier.idSuffix()).get(), pos, state);
        this.tier = tier;
        this.currentParallelism = tier.maxParallelism();
    }

    public ParallelTier tier() {
        return tier;
    }

    public int maxParallelism() {
        return tier.maxParallelism();
    }

    public int currentParallelism() {
        if (currentParallelism <= 0) currentParallelism = tier.maxParallelism();
        return currentParallelism;
    }

    public void setCurrentParallelism(int currentParallelism) {
        int clamped = Math.max(1, Math.min(currentParallelism, tier.maxParallelism()));
        if (this.currentParallelism == clamped) return;
        this.currentParallelism = clamped;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        output.putInt("current_parallelism", currentParallelism());
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        super.loadAdditional(input, registries);
        currentParallelism = Math.max(1, Math.min(
                input.contains("current_parallelism", Tag.TAG_INT)
                        ? input.getInt("current_parallelism") : tier.maxParallelism(),
                tier.maxParallelism()));
    }
}
