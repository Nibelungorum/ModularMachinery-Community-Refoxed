package cn.howxu.mmcr.internal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Structural coupler that links one host controller to one module controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ModuleCouplerBlock extends Block implements EntityBlock {
    private final Supplier<? extends BlockEntityType<?>> beType;

    public ModuleCouplerBlock(Supplier<? extends BlockEntityType<?>> beType, Properties properties) {
        super(properties.sound(SoundType.METAL));
        this.beType = beType;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }
}
