package cn.howxu.mmcr.internal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiFunction;

/**
 * 调试用无限源方块基类。{@link EntityBlock#newBlockEntity} 通过闭包 {@code factory}
 * 创建具体 BE,允许在工厂内将额外上下文(例如绑定的 Fluid)注入到 BE 构造函数。
 * 不实现 MenuProvider,右击无界面。
 *
 * @author howxu <dev@howxu.cn>
 */
public class DebugSourceBlock extends Block implements EntityBlock {

    private final BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory;

    public DebugSourceBlock(BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory, Properties props) {
        super(props.sound(SoundType.WOOL));
        this.factory = factory;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }
}
