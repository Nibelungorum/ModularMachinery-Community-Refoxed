package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author howxu <dev@howxu.cn>
 */
public class ParallelControllerBlockEntity extends BlockEntity {

    private final ParallelTier tier;

    public ParallelControllerBlockEntity(ParallelTier tier, BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get(tier.idSuffix()).get(), pos, state);
        this.tier = tier;
    }

    public ParallelTier tier() {
        return tier;
    }

    public int maxParallelism() {
        return tier.maxParallelism();
    }
}
