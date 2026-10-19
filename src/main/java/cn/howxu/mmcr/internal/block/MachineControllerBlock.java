package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.ControllerMenuOpenData;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.ItemSpecialOperationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndTintGetter;
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

import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class MachineControllerBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Direction> ROLL_FACING = EnumProperty.create("roll_facing", Direction.class, Direction.Plane.HORIZONTAL);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final ResourceLocation machineId;

    public MachineControllerBlock(Properties props) {
        this(MMCR.id("unknown"), props);
    }

    public MachineControllerBlock(ResourceLocation machineId, Properties props) {
        super(props.strength(3.5F).sound(SoundType.METAL));
        if (machineId == null) throw new IllegalArgumentException("machineId null");
        this.machineId = machineId;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ROLL_FACING, Direction.NORTH)
                .setValue(FORMED, false)
                .setValue(ACTIVE, false));
    }

    public ResourceLocation machineId() {
        return machineId;
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                    @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
        if (!state.getValue(FORMED)) return state;
        MachineRegistration registration = MachineDefinitions.effectiveSnapshot().get(machineId);
        return registration == null ? state : AppearanceStateResolver.resolve(state, level, pos,
                registration.appearance().controllerTextureSource());
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        boolean explosion = builder.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) != null;
        if (explosion) return List.of();
        return List.of(asItem().getDefaultInstance());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ROLL_FACING, FORMED, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean verticalAllowed = isVerticalAllowed();
        Direction horizontalFacing = ctx.getHorizontalDirection().getOpposite();
        // Mekanism uses yaw zero without a player; AE2 uses the context's horizontal fallback.
        if (ctx.getPlayer() == null && !verticalAllowed) horizontalFacing = Direction.NORTH;
        float pitch = ctx.getPlayer() == null ? 0.0F : ctx.getPlayer().getXRot();
        Direction facing = facingForPlacement(
                pitch,
                horizontalFacing,
                verticalAllowed,
                isVerticalRequired());
        return defaultBlockState()
                .setValue(FACING, facing)
                // The horizontal projection of screen-up reverses when looking up at the underside.
                .setValue(ROLL_FACING, facing == Direction.UP
                        ? horizontalFacing.getOpposite() : horizontalFacing);
    }

    static Direction facingForPlacement(float pitch, Direction horizontalFacing,
                                        boolean verticalAllowed, boolean verticalRequired) {
        if (verticalRequired) return pitch < 0.0F ? Direction.DOWN : Direction.UP;
        if (verticalAllowed) {
            // ME Drive placement keeps the exact +/-65 degree boundary horizontal.
            if (pitch > 65.0F) return Direction.UP;
            if (pitch < -65.0F) return Direction.DOWN;
        }
        return horizontalFacing;
    }

    private boolean isVerticalAllowed() {
        MachineRegistration registration = MachineDefinitions.effectiveSnapshot().get(machineId);
        return registration != null && registration.controllerSpec().allowVerticalFacing();
    }

    private boolean isVerticalRequired() {
        MachineRegistration registration = MachineDefinitions.effectiveSnapshot().get(machineId);
        return registration != null && registration.controllerSpec().requireVerticalFacing();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.controllerFor(machineId).get().create(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() { return titleFor(machineId); }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                if (!(level.getBlockEntity(pos) instanceof MachineControllerBlockEntity controller)) return null;
                return MachineControllerBlock.createMenu(containerId, inventory, player, controller);
            }

            @Override
            public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
                ControllerMenuOpenData.write(buffer, ((ControllerUiMenu) menu).uiOpenData());
            }
        };
    }

    static AbstractContainerMenu createMenu(int containerId, Inventory playerInventory,
                                            Player player, @Nullable MachineControllerBlockEntity controller) {
        if (controller != null && controller.hasFactoryController()) {
            return new FactoryControllerMenu(containerId, playerInventory, controller,
                    player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
        }
        return new MachineControllerMenu(containerId, playerInventory, controller);
    }

    static Component titleFor(ResourceLocation machineId) {
        MachineRegistration registration = MachineDefinitions.effectiveSnapshot().get(machineId);
        return registration == null
                ? Component.translatable("container.mmcr.machine_controller")
                : registration.displayName();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && ItemSpecialOperationUtil.isSpecialOperated(player)
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty()
                && level.getBlockEntity(pos) instanceof MachineControllerBlockEntity controller
                && !controller.runtimeSnapshot().structure().formed()
            && player instanceof ServerPlayer serverPlayer) {
            controller.sendStructurePreview(serverPlayer);
            controller.requestImmediateStructureCheck(serverPlayer);
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        // Mark the entity removed before cleanup can write its cached controller state back into the world.
        super.onRemove(state, level, pos, newState, moving);
        if (moving || state.getBlock() == newState.getBlock()) return;
        if (!level.isClientSide() && blockEntity instanceof MachineControllerBlockEntity controller) {
            controller.onBlockRemoved();
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
