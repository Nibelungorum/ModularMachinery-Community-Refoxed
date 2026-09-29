package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.menu.ISubMenu;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InterfaceLogicKind;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributor;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributorBootstrap;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared AE2 Interface host lifecycle for normal output ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public abstract class OutputInterfaceBaseBlockEntity extends IOPortBlockEntity
        implements InterfaceLogicHost, IGridConnectedBlockEntity {
    private static final IGridNodeListener<OutputInterfaceBaseBlockEntity> NODE_LISTENER =
            new BlockEntityNodeListener<>() {
                @Override
                public void onGridChanged(OutputInterfaceBaseBlockEntity nodeOwner, IGridNode node) {
                    nodeOwner.onNetworkChanged();
                }
            };

    protected final IOPortKind kind;
    protected final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true);
    protected final InterfaceLogic logic;
    private CapabilitySnapshot capabilitySnapshot;

    protected OutputInterfaceBaseBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        this.kind = kind;
        logic = kind instanceof InterfaceLogicKind logicKind
                ? logicKind.createInterfaceLogic(mainNode, this, AEBlocks.INTERFACE.asItem())
                : new InterfaceLogic(mainNode, this, AEBlocks.INTERFACE.asItem());
    }

    @Override
    public final IOType ioType() {
        return IOType.OUTPUT;
    }

    @Override
    public final IOPortKind kind() {
        return kind;
    }

    @Override
    public final IManagedGridNode getMainNode() {
        return mainNode;
    }

    @Override
    public final InterfaceLogic getInterfaceLogic() {
        return logic;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        ExtendedAEContributor contributor = ExtendedAEContributorBootstrap.contributor();
        ItemStack icon = contributor.mainMenuIcon(kind);
        if (icon != null) return icon;
        ItemStack fallback = AEBlocks.INTERFACE.stack();
        fallback.set(DataComponents.CUSTOM_NAME, Component.translatable("container.mmcr." + kind.id()));
        return fallback;
    }

    @Override
    public BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        ExtendedAEContributor contributor = ExtendedAEContributorBootstrap.contributor();
        if (contributor.available() && contributor.isPort(kind.id())
                && player instanceof ServerPlayer serverPlayer) {
            contributor.returnToMainMenu(serverPlayer, subMenu, kind);
            return;
        }
        InterfaceLogicHost.super.returnToMainMenu(player, subMenu);
    }

    @Override
    public void saveChanges() {
        notifyStorageChanged();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return logic.getCableConnectionType(direction);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (mainNode.hasGridBooted()) logic.notifyNeighbors();
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind.definition().bindings().stream()
                    .map(this::createCapability)
                    .toList());
        }
        return capabilitySnapshot;
    }

    @Override
    public void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        mainNode.serialize(output);
        logic.writeToNBT(output, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            mainNode.deserialize(input);
            logic.readFromNBT(input, registries);
        } finally {
            endLoadingAdditional();
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, blockEntity -> {
            if (blockEntity.getLevel() != null) {
                blockEntity.mainNode.create(blockEntity.getLevel(), blockEntity.getBlockPos());
            }
        });
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        List<ItemStack> drops = new ArrayList<>();
        logic.addDrops(drops);
        for (ItemStack stack : drops) {
            Block.popResource(level, worldPosition, stack);
        }
    }

    /** Called when the output host's main node changes networks. */
    protected void onNetworkChanged() {
        logic.gridChanged();
    }
}
