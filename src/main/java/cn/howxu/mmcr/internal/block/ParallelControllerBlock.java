package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.api.recipe.ParallelTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * @author howxu <dev@howxu.cn>
 */
public class ParallelControllerBlock extends Block implements EntityBlock {

    private final ParallelTier tier;
    private final Supplier<? extends BlockEntityType<?>> beType;

    public ParallelControllerBlock(ParallelTier tier,
                                   Supplier<? extends BlockEntityType<?>> beType,
                                   Properties properties) {
        super(properties.sound(SoundType.METAL));
        this.tier = tier;
        this.beType = beType;
    }

    public ParallelTier tier() {
        return tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }
}
