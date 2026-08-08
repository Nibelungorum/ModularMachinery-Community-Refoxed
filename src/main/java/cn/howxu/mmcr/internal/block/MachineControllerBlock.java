package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class MachineControllerBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Direction> ROLL_FACING = EnumProperty.create("roll_facing", Direction.class, Direction.Plane.HORIZONTAL);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final Identifier machineId;

    public MachineControllerBlock(Properties props) {
        this(cn.howxu.mmcr.MMCR.id("unknown"), props);
    }

    public MachineControllerBlock(Identifier machineId, Properties props) {
        super(props.sound(SoundType.METAL));
        if (machineId == null) throw new IllegalArgumentException("machineId null");
        this.machineId = machineId;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ROLL_FACING, Direction.NORTH)
                .setValue(FORMED, false)
                .setValue(ACTIVE, false));
    }

    public Identifier machineId() {
        return machineId;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ROLL_FACING, FORMED, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction horizontalFacing = ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState()
                .setValue(FACING, facingForPlacement(
                        ctx.getClickedFace(),
                        ctx.getClickLocation().y,
                        ctx.getPlayer() == null ? ctx.getClickedPos().getY() : ctx.getPlayer().getY(),
                        horizontalFacing,
                        isVerticalAllowed()))
                .setValue(ROLL_FACING, horizontalFacing);
    }

    static Direction facingForPlacement(Direction clickedFace, double clickY, double playerY, Direction horizontalFallback, boolean verticalAllowed) {
        if (!verticalAllowed || !clickedFace.getAxis().isVertical()) return facingForPlacement(clickedFace, horizontalFallback, false);
        return isClearlyVerticalPlacement(clickedFace, clickY, playerY) ? clickedFace : horizontalFallback;
    }

    static Direction facingForPlacement(Direction clickedFace, Direction horizontalFallback, boolean verticalAllowed) {
        if (verticalAllowed) return clickedFace;
        return clickedFace.getAxis().isVertical() ? horizontalFallback : clickedFace;
    }

    Direction facingForPlacement(Direction clickedFace, Direction horizontalFallback) {
        return facingForPlacement(clickedFace, horizontalFallback, isVerticalAllowed());
    }

    private static boolean isClearlyVerticalPlacement(Direction clickedFace, double clickY, double playerY) {
        return clickedFace == Direction.UP ? clickY < playerY : clickY > playerY + 2.0d;
    }

    private boolean isVerticalAllowed() {
        MachineRegistration registration = MachineDefinitions.getRegistration(machineId);
        return registration != null && registration.controllerSpec().allowVerticalFacing();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.controllerFor(machineId).get().create(pos, state);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, playerInv, player) -> new MachineControllerMenu(containerId, playerInv,
                        level.getBlockEntity(pos) instanceof MachineControllerBlockEntity mc ? mc : null),
                titleFor(machineId));
    }

    static Component titleFor(Identifier machineId) {
        MachineRegistration registration = MachineDefinitions.getRegistration(machineId);
        return registration == null
                ? Component.translatable("container.mmcr.machine_controller")
                : Component.literal(registration.localizedName());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) player.openMenu(provider, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> beType) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof MachineControllerBlockEntity controller) controller.serverTick();
        };
    }
}
