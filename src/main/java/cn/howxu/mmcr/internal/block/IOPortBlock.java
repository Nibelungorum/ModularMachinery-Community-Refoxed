package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class IOPortBlock extends Block implements EntityBlock {

    public static final EnumProperty<IOType> IO_TYPE = EnumProperty.create("io_type", IOType.class);

    private final String kind;
    private final Supplier<? extends BlockEntityType<?>> beType;

    public IOPortBlock(String kind,
                       Supplier<? extends BlockEntityType<?>> beType,
                       Properties props) {
        super(props.sound(SoundType.METAL));
        this.kind = kind;
        this.beType = beType;
        registerDefaultState(stateDefinition.any().setValue(IO_TYPE, IOType.INPUT));
    }

    public String kind() { return kind; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beType.get().create(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(IO_TYPE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            level.setBlock(pos, state.cycle(IO_TYPE), 3);
        }
        return InteractionResult.SUCCESS;
    }
}
