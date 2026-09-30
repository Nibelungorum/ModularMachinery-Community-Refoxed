package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.menu.ISubMenu;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InterfaceLogicKind;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributor;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributorBootstrap;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MMCR host for one AE2 interface logic storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InputInterfaceBlockEntity extends IOPortBlockEntity
        implements InterfaceLogicHost, IGridConnectedBlockEntity, MemoryCardHost, NetworkOwnedInputHost {
    private static final String NETWORK_OWNED_KEY = "network_owned";
    private static final String NETWORK_OWNED_SLOT_KEY = "slot";
    private static final IGridNodeListener<InputInterfaceBlockEntity> NODE_LISTENER =
            new BlockEntityNodeListener<>() {
                @Override
                public void onGridChanged(InputInterfaceBlockEntity nodeOwner, IGridNode node) {
                    nodeOwner.logic.gridChanged();
                }
            };

    private final IOPortKind kind;
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true);
    private final InterfaceLogic logic;
    private final GenericStack[] networkOwned;
    private final IItemHandler itemHandler;
    private final IFluidHandler fluidHandler;
    private CapabilitySnapshot capabilitySnapshot;
    private boolean loadingProvenance;

    public InputInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        this.kind = kind;
        InterfaceLogicKind logicKind = (InterfaceLogicKind) kind;
        logic = logicKind.createInterfaceLogic(mainNode, this, AEBlocks.INTERFACE.asItem());
        networkOwned = new GenericStack[logic.getStorage().size()];
        itemHandler = AE2NativeAdapters.items(logic.getStorage());
        fluidHandler = AE2NativeAdapters.fluids(logic.getStorage());
    }

    @Override
    public IOType ioType() {
        return IOType.INPUT;
    }

    @Override
    public IOPortKind kind() {
        return kind;
    }

    @Override
    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    @Override
    public InterfaceLogic getInterfaceLogic() {
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
    public Component memoryCardSettingsSource() {
        return getMainMenuIcon().getHoverName();
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
        if (loadingProvenance) return;
        reconcileNetworkOwned();
        notifyStorageChanged();
        notifyControllerOfInputChange();
    }

    /** Records the portion of a storage insertion that was pulled by AE2 from the network. */
    public void recordNetworkPull(int slot, AEKey what, long amount) {
        if (slot < 0 || slot >= networkOwned.length || what == null || amount <= 0L) return;

        GenericStack current = logic.getStorage().getStack(slot);
        if (current == null || !what.equals(current.what())) return;

        GenericStack owned = networkOwned[slot];
        long ownedAmount = owned != null && what.equals(owned.what()) ? owned.amount() : 0L;
        networkOwned[slot] = new GenericStack(what,
                Math.min(current.amount(), saturatingAdd(ownedAmount, amount)));
    }

    /** Records the portion of a storage extraction that was returned to the AE2 network. */
    public void recordNetworkReturn(int slot, AEKey what, long amount) {
        if (slot < 0 || slot >= networkOwned.length || what == null || amount <= 0L) return;

        GenericStack owned = networkOwned[slot];
        long ownedAmount = owned != null && what.equals(owned.what()) ? owned.amount() : 0L;
        long remaining = ownedAmount - Math.min(ownedAmount, amount);
        networkOwned[slot] = remaining > 0L ? new GenericStack(what, remaining) : null;
    }

    /** Returns the amount in a slot that may be returned to the AE2 network. */
    public long networkOwnedAmount(int slot, AEKey what) {
        if (slot < 0 || slot >= networkOwned.length || what == null) return 0L;

        GenericStack current = logic.getStorage().getStack(slot);
        GenericStack owned = networkOwned[slot];
        if (current == null || owned == null || !what.equals(current.what()) || !what.equals(owned.what())) {
            return 0L;
        }
        long result = Math.min(current.amount(), owned.amount());
        return result;
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
    public IItemHandler nativeItemHandler() {
        return itemHandler;
    }

    @Override
    public IFluidHandler nativeFluidHandler() {
        return fluidHandler;
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
        mainNode.saveToNBT(output);
        logic.writeToNBT(output, registries);
        ListTag provenance = new ListTag();
        for (int slot = 0; slot < networkOwned.length; slot++) {
            GenericStack owned = networkOwned[slot];
            if (owned == null || owned.amount() <= 0L) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt(NETWORK_OWNED_SLOT_KEY, slot);
            entry.put("stack", GenericStack.writeTag(registries, owned));
            provenance.add(entry);
        }
        output.put(NETWORK_OWNED_KEY, provenance);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        loadingProvenance = true;
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            mainNode.loadFromNBT(input);
            readNetworkOwned(input, registries);
            logic.readFromNBT(input, registries);
            reconcileNetworkOwned();
        } finally {
            endLoadingAdditional();
            loadingProvenance = false;
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

    private void readNetworkOwned(CompoundTag input, HolderLookup.Provider registries) {
        Arrays.fill(networkOwned, null);
        ListTag networkOwnedEntries = input.getList(NETWORK_OWNED_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < networkOwnedEntries.size(); index++) {
            CompoundTag entry = networkOwnedEntries.getCompound(index);
            int slot = entry.getInt(NETWORK_OWNED_SLOT_KEY);
            GenericStack owned = GenericStack.readTag(registries, entry.getCompound("stack"));
            if (slot >= 0 && slot < networkOwned.length && owned != null && owned.amount() > 0L) {
                networkOwned[slot] = owned;
            }
        }
    }

    private void reconcileNetworkOwned() {
        for (int slot = 0; slot < networkOwned.length; slot++) {
            GenericStack owned = networkOwned[slot];
            GenericStack current = logic.getStorage().getStack(slot);
            if (owned == null) continue;
            if (current == null || !owned.what().equals(current.what())) {
                networkOwned[slot] = null;
            } else if (owned.amount() > current.amount()) {
                networkOwned[slot] = new GenericStack(owned.what(), current.amount());
            }
        }
    }

    private static long saturatingAdd(long first, long second) {
        return second > Long.MAX_VALUE - first ? Long.MAX_VALUE : first + second;
    }
}
