package cn.howxu.mmcr.compat.botania.loaded;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.LinkedAppearanceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.common.entity.ManaBurstEntity;

import java.util.function.Supplier;

/** Independent pool block preserving linked-port lifecycle and native item use. @author howxu <dev@howxu.cn> */
public final class ManaPortBlock extends Block implements EntityBlock, MachinePort {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 2, 0, 2, 8, 16),
            Block.box(14, 2, 0, 16, 8, 16), Block.box(2, 2, 0, 14, 8, 2),
            Block.box(2, 2, 14, 14, 8, 16));
    private static final VoxelShape INTERACTION_SHAPE = Block.box(0, 0, 0, 16, 8, 16);
    private final IOPortKind kind;
    private final Supplier<? extends BlockEntityType<?>> type;

    public ManaPortBlock(IOPortKind kind, Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        super(properties.noOcclusion());
        this.kind = kind;
        this.type = type;
    }

    @Override public IOPortKind kind() { return kind; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != this.type.get()) return null;
        return (world, pos, blockState, entity) -> {
            if (entity instanceof IOPortBlockEntity port) port.serverTick();
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return INTERACTION_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof ManaBurstEntity
                ? INTERACTION_SHAPE : SHAPE;
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                    @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
        if (!(level.getBlockEntity(pos) instanceof LinkedAppearanceBlockEntity port)
                || port.linkedControllerPositions().isEmpty() || port.appearanceSource().overrideTexture() != null) {
            return state;
        }
        BlockState appearance = BuiltInRegistries.BLOCK.get(port.appearanceSource().blockId()).defaultBlockState();
        return Block.isShapeFullBlock(appearance.getShape(level, pos)) ? appearance : state;
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof IOPortBlockEntity port) port.dropContents();
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!moving && state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof IOPortBlockEntity port) port.onBlockRemoved();
        super.onRemove(state, level, pos, newState, moving);
    }
}
