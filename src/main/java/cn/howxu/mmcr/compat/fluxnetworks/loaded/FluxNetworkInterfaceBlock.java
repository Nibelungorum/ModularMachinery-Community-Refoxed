package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import sonar.fluxnetworks.common.device.TileFluxDevice;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.register.RegistryItems;

import java.util.function.Supplier;

/** Native Flux interaction over the ordinary dynamic MMCR port model.
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworkInterfaceBlock extends IOPortBlock {
    public FluxNetworkInterfaceBlock(IOPortKind kind, Supplier<? extends BlockEntityType<?>> type, Properties properties) {
        super(kind, type, properties);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return TileFluxDevice.getTicker(level);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TileFluxDevice device)) return null;
        return new MenuProvider() {
            @Override
            public Component getDisplayName() { return device.getDisplayName(); }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return level.getBlockEntity(pos) == device && !device.isRemoved() && device.canPlayerAccess(player)
                        ? device.createMenu(containerId, inventory, player) : null;
            }

            @Override
            public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
                // Match TileFluxDevice.onPlayerInteract's native opening format.
                buffer.writeBoolean(true);
                buffer.writeBlockPos(pos);
                CompoundTag tag = new CompoundTag();
                device.writeCustomTag(tag, FluxConstants.NBT_TILE_UPDATE);
                buffer.writeNbt(tag);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().is(RegistryItems.FLUX_CONFIGURATOR.get())) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileFluxDevice device) device.onPlayerInteract(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(RegistryItems.FLUX_CONFIGURATOR.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileFluxDevice device) device.onPlayerInteract(player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof TileFluxDevice device) {
            device.applyComponentsFromItemStack(stack);
            if (placer instanceof Player) device.setOwnerUUID(placer.getUUID());
        }
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                    BlockState sourceState, BlockPos sourcePos) {
        if (!(level.getBlockEntity(pos) instanceof FluxNetworkInterfaceBlockEntity port) || port.controllerPos == null) {
            return state;
        }
        MachineAppearanceSpec.TextureSource source = port.appearanceSource();
        if (source.overrideTexture() != null) return state;
        BlockState appearance = BuiltInRegistries.BLOCK.get(source.blockId()).defaultBlockState();
        return Block.isShapeFullBlock(appearance.getShape(level, pos)) ? appearance : state;
    }
}
