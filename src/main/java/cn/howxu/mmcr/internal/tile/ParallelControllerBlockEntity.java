package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * @author howxu <dev@howxu.cn>
 */
public class ParallelControllerBlockEntity extends BlockEntity {

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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("current_parallelism", currentParallelism());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        setCurrentParallelism(input.getIntOr("current_parallelism", tier.maxParallelism()));
    }
}
