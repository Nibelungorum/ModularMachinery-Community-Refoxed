package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

import appeng.api.networking.GridHelper;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.me.storage.NullInventory;
import appeng.menu.ISubMenu;
import cn.howxu.mmcr.mixin.compat.appliedenergistics2.ConfigInventoryAccessor;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * MMCR host for an AE2 stocking interface.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StockingInterfaceBlockEntity extends IOPortBlockEntity
        implements InterfaceLogicHost, IGridConnectedBlockEntity {
    private static final IGridNodeListener<StockingInterfaceBlockEntity> NODE_LISTENER =
            new BlockEntityNodeListener<>() {
                @Override
                public void onGridChanged(StockingInterfaceBlockEntity nodeOwner, IGridNode node) {
                    nodeOwner.networkChanged();
                }
            };
    private static final IGridNodeListener<StockingInterfaceBlockEntity> UI_NODE_LISTENER =
            (blockEntity, node) -> {
            };

    private final IOPortKind kind;
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .addService(IStorageWatcherNode.class, new IStorageWatcherNode() {
                @Override
                public void updateWatcher(IStackWatcher newWatcher) {
                    if (storageWatcher != null) storageWatcher.reset();
                    storageWatcher = newWatcher;
                    refreshNetworkStorage();
                    configureWatcher();
                }

                @Override
                public void onStackChange(AEKey what, long amount) {
                    updateStorageMirror(what, amount);
                    notifyStorageChanged();
                    notifyControllerOfInputChange();
                }
            });
    private final IManagedGridNode uiNode = GridHelper.createManagedNode(this, UI_NODE_LISTENER);
    private final InterfaceLogic logic;
    private final IItemHandler itemHandler = AE2NativeAdapters.networkItems(this::networkStorage, this::configuredKeys,
            appeng.api.networking.security.IActionSource.ofMachine(this));
    private final IFluidHandler fluidHandler = AE2NativeAdapters.networkFluids(this::networkStorage, this::configuredKeys,
            appeng.api.networking.security.IActionSource.ofMachine(this));
    @Nullable
    private IStackWatcher storageWatcher;
    private long storageMirrorRefreshes;
    private CapabilitySnapshot capabilitySnapshot;

    public StockingInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        this.kind = kind;
        InterfaceLogicKind logicKind = (InterfaceLogicKind) kind;
        logic = logicKind.createInterfaceLogic(uiNode, this, AEBlocks.INTERFACE.asItem());
        configureStorageMirrorCapacity();
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
        normalizeConfigAmounts();
        refreshNetworkStorage();
        configureWatcher();
        notifyStorageChanged();
        notifyControllerOfInputChange();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return logic.getCableConnectionType(direction);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        refreshNetworkStorage();
        configureWatcher();
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
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            mainNode.loadFromNBT(input);
            logic.readFromNBT(input, registries);
            normalizeConfigAmounts();
            refreshNetworkStorage();
            configureWatcher();
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
        clearNetworkWatcher();
        mainNode.destroy();
        refreshNetworkStorage();
        configureWatcher();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        clearNetworkWatcher();
        mainNode.destroy();
        refreshNetworkStorage();
        configureWatcher();
    }

    @Override
    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        for (ItemStack stack : logic.getUpgrades()) {
            Block.popResource(level, worldPosition, stack);
        }
    }

    private void networkChanged() {
        refreshNetworkStorage();
        configureWatcher();
    }

    private void configureStorageMirrorCapacity() {
        var storage = logic.getStorage();
        if (storage instanceof ConfigInventoryAccessor accessor) {
            accessor.mmcr$setAllowOverstacking(true);
        }
        storage.setCapacity(AEKeyType.items(), Long.MAX_VALUE);
        storage.setCapacity(AEKeyType.fluids(), Long.MAX_VALUE);
    }

    private void clearNetworkWatcher() {
        if (storageWatcher != null) storageWatcher.reset();
        storageWatcher = null;
    }

    private void refreshNetworkStorage() {
        // Native handlers resolve the current grid and configuration lazily.
    }

    private MEStorage networkStorage() {
        IGrid grid = mainNode.getGrid();
        return grid == null ? NullInventory.of() : grid.getStorageService().getInventory();
    }

    private List<AEKey> configuredKeys() {
        List<AEKey> keys = new ArrayList<>();
        for (int slot = 0; slot < logic.getConfig().size(); slot++) {
            AEKey key = logic.getConfig().getKey(slot);
            if (key != null) keys.add(key);
        }
        return List.copyOf(keys);
    }

    private void configureWatcher() {
        if (storageWatcher != null) storageWatcher.reset();
        if (storageWatcher != null) {
            if (mainNode.getGrid() == null) {
                storageWatcher = null;
            } else {
                for (int slot = 0; slot < logic.getConfig().size(); slot++) {
                    AEKey key = logic.getConfig().getKey(slot);
                    if (key != null) storageWatcher.add(key);
                }
            }
        }
        refreshStorageMirror();
    }

    private void refreshStorageMirror() {
        if (level != null && level.isClientSide()) return;
        storageMirrorRefreshes++;
        IGrid grid = mainNode.getGrid();
        boolean reportAmounts = mainNode.isActive() && grid != null;
        var cachedInventory = reportAmounts ? grid.getStorageService().getCachedInventory() : null;
        var storage = logic.getStorage();
        storage.beginBatch();
        try {
            for (int slot = 0; slot < logic.getConfig().size(); slot++) {
                AEKey key = logic.getConfig().getKey(slot);
                long amount = reportAmounts && key != null ? cachedInventory.get(key) : 0L;
                storage.setStack(slot, key == null || amount <= 0L ? null : new GenericStack(key, amount));
            }
        } finally {
            storage.endBatchSuppressed();
        }
    }

    private void updateStorageMirror(AEKey changedKey, long amount) {
        if (level != null && level.isClientSide()) return;
        var storage = logic.getStorage();
        storage.beginBatch();
        try {
            for (int slot = 0; slot < logic.getConfig().size(); slot++) {
                if (changedKey.equals(logic.getConfig().getKey(slot))) {
                    storage.setStack(slot, amount <= 0L ? null : new GenericStack(changedKey, amount));
                }
            }
        } finally {
            storage.endBatchSuppressed();
        }
    }

    public long storageMirrorRefreshes() {
        return storageMirrorRefreshes;
    }

    private void normalizeConfigAmounts() {
        var config = logic.getConfig();
        config.beginBatch();
        try {
            for (int slot = 0; slot < config.size(); slot++) {
                GenericStack stack = config.getStack(slot);
                long defaultAmount = stack != null && stack.what() instanceof AEFluidKey
                        ? 1_000L
                        : 1L;
                if (stack != null && stack.amount() != defaultAmount) {
                    config.setStack(slot, new GenericStack(stack.what(), defaultAmount));
                }
            }
        } finally {
            config.endBatchSuppressed();
        }
    }

}
