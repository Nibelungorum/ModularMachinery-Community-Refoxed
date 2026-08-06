package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
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
                .setValue(FORMED, false)
                .setValue(ACTIVE, false));
    }

    public Identifier machineId() {
        return machineId;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, FORMED, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean verticalAllowed = isVerticalAllowed();
        Direction nearest = ctx.getNearestLookingDirection().getOpposite();
        Direction fallback = ctx.getHorizontalDirection().getOpposite();
        Direction facing = verticalAllowed ? nearest : fallback;
        if (!verticalAllowed && facing.getAxis().isVertical()) {
            facing = fallback;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    private boolean isVerticalAllowed() {
        Machine machine = MachineDefinitions.get(machineId);
        return machine != null && machine.controller().allowVerticalFacing();
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
        Machine machine = MachineDefinitions.get(machineId);
        return machine == null
                ? Component.translatable("container.mmcr.machine_controller")
                : Component.literal(machine.localizedName());
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
