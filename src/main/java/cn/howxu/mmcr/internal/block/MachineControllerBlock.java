package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

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
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null) return List.of();
        return List.of(asItem().getDefaultInstance());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ROLL_FACING, FORMED, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction horizontalFacing = ctx.getHorizontalDirection().getOpposite();
        double playerX = ctx.getPlayer() == null ? ctx.getClickedPos().getX() + 0.5d : ctx.getPlayer().getX();
        double playerY = ctx.getPlayer() == null ? ctx.getClickedPos().getY() : ctx.getPlayer().getY();
        double playerZ = ctx.getPlayer() == null ? ctx.getClickedPos().getZ() + 0.5d : ctx.getPlayer().getZ();
        Direction facing = facingForPlacement(
                ctx.getClickedFace(),
                ctx.getClickLocation().y,
                playerY,
                horizontalFacing,
                isVerticalAllowed());
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(ROLL_FACING, rollFacingForPlacement(
                        facing,
                        ctx.getClickedPos().getX() + 0.5d,
                        ctx.getClickedPos().getZ() + 0.5d,
                        playerX,
                        playerZ,
                        horizontalFacing));
    }

    static Direction facingForPlacement(Direction clickedFace, double clickY, double playerY, Direction horizontalFallback, boolean verticalAllowed) {
        if (!verticalAllowed) return facingForPlacement(clickedFace, horizontalFallback, false);
        if (clickedFace == Direction.DOWN) return Direction.DOWN;
        if (clickedFace == Direction.UP) return clickY < playerY - 1.0d ? Direction.UP : horizontalFallback;
        return clickY > playerY + 1.0d ? Direction.DOWN : clickedFace;
    }

    static Direction rollFacingForPlacement(Direction facing, double blockCenterX, double blockCenterZ, double playerX, double playerZ, Direction horizontalFallback) {
        if (!facing.getAxis().isVertical()) return horizontalFallback;

        double dx = playerX - blockCenterX;
        double dz = playerZ - blockCenterZ;
        if (Math.abs(dx) == Math.abs(dz)) return horizontalFallback;
        if (Math.abs(dx) > Math.abs(dz)) return dx < 0.0d ? Direction.WEST : Direction.EAST;
        return dz < 0.0d ? Direction.NORTH : Direction.SOUTH;
    }

    static Direction facingForPlacement(Direction clickedFace, Direction horizontalFallback, boolean verticalAllowed) {
        if (verticalAllowed) return clickedFace;
        return clickedFace.getAxis().isVertical() ? horizontalFallback : clickedFace;
    }

    Direction facingForPlacement(Direction clickedFace, Direction horizontalFallback) {
        return facingForPlacement(clickedFace, horizontalFallback, isVerticalAllowed());
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
                (containerId, playerInv, player) -> createMenu(containerId, playerInv, player,
                        level.getBlockEntity(pos) instanceof MachineControllerBlockEntity mc ? mc : null),
                titleFor(machineId));
    }

    static AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory playerInventory,
                                            Player player, @Nullable MachineControllerBlockEntity controller) {
        if (controller != null && controller.hasFactoryController()) {
            return new FactoryControllerMenu(containerId, playerInventory, controller,
                    player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
        }
        if (controller != null && player instanceof ServerPlayer serverPlayer) controller.sendRecipeLockState(serverPlayer);
        return new MachineControllerMenu(containerId, playerInventory, controller);
    }

    static Component titleFor(Identifier machineId) {
        MachineRegistration registration = MachineDefinitions.getRegistration(machineId);
        return registration == null
                ? Component.translatable("container.mmcr.machine_controller")
                : registration.displayName();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && player.isShiftKeyDown()
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty()
                && level.getBlockEntity(pos) instanceof MachineControllerBlockEntity controller
                && !controller.isFormed()
                && player instanceof ServerPlayer serverPlayer) {
            controller.sendStructurePreview(serverPlayer);
            controller.diagnoseFirstStructureMismatch(serverPlayer);
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) player.openMenu(provider, buffer -> {
                MachineControllerBlockEntity controller = level.getBlockEntity(pos) instanceof MachineControllerBlockEntity mc ? mc : null;
                MachineControllerMenu.writeClientOpenData(buffer, pos, controller == null ? machineId : controller.machineId(),
                        controller == null ? null : controller.connectedHostId().orElse(null),
                        MachineControllerMenu.controllerRoleSyncValue(controller),
                        controller != null && controller.isFormed(),
                        controller == null ? 0 : controller.installedModuleCount());
            });
        }
        return InteractionResult.SUCCESS;
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (moving || state.getBlock() == newState.getBlock()) return;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MachineControllerBlockEntity controller) {
            controller.onMachineDestroyed();
            controller.resetLinkedPortAppearances();
        }
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
