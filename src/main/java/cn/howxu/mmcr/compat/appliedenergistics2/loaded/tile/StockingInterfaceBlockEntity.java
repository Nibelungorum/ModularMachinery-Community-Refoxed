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
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InterfaceLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.network.NetworkResourceStorage;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributor;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributorBootstrap;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;
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
                    itemStorage.onStackChange(what, amount);
                    fluidStorage.onStackChange(what, amount);
                    updateStorageMirror(what, amount);
                    notifyStorageChanged();
                    notifyControllerOfInputChange();
                }
            });
    private final IManagedGridNode uiNode = GridHelper.createManagedNode(this, UI_NODE_LISTENER);
    private final InterfaceLogic logic;
    private final LiveResourceStorage<ItemResource> itemStorage = new LiveResourceStorage<>(
            AE2ResourceFamilies.ITEM.stockingInputView(NullInventory.of(), List.of()));
    private final LiveResourceStorage<FluidResource> fluidStorage = new LiveResourceStorage<>(
            AE2ResourceFamilies.FLUID.stockingInputView(NullInventory.of(), List.of()));
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
        if (Transaction.getCurrentOpenedTransaction() != null) return;
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
    public ResourceStorage<ItemResource> itemStorage() {
        return itemStorage;
    }

    @Override
    public ResourceStorage<FluidResource> fluidStorage() {
        return fluidStorage;
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
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        mainNode.serialize(output);
        logic.writeToNBT(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input);
            mainNode.deserialize(input);
            logic.readFromNBT(input);
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
        IGrid grid = mainNode.getGrid();
        MEStorage storage = grid == null ? NullInventory.of() : grid.getStorageService().getInventory();
        List<AEKey> keys = configuredKeys();
        itemStorage.rebind(AE2ResourceFamilies.ITEM.stockingInputView(storage, keys));
        fluidStorage.rebind(AE2ResourceFamilies.FLUID.stockingInputView(storage, keys));
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
                if (key != null) {
                    itemStorage.onStackChange(key, amount);
                    fluidStorage.onStackChange(key, amount);
                }
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

    private static final class LiveResourceStorage<R> implements ResourceStorage<R> {
        private NetworkResourceStorage<R> delegate;

        private LiveResourceStorage(NetworkResourceStorage<R> delegate) {
            this.delegate = delegate;
        }

        private void rebind(NetworkResourceStorage<R> delegate) {
            this.delegate = delegate;
        }

        private void onStackChange(AEKey key, long amount) {
            delegate.onStackChange(key, amount);
        }

        @Override
        public Class<R> resourceType() {
            return delegate.resourceType();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public Object reservationIdentity() {
            return delegate.reservationIdentity();
        }

        @Override
        public @Nullable R resource(int slot) {
            return delegate.resource(slot);
        }

        @Override
        public long amount(int slot) {
            return delegate.amount(slot);
        }

        @Override
        public long capacity(int slot, @Nullable R resource) {
            return delegate.capacity(slot, resource);
        }

        @Override
        public boolean isValid(int slot, R resource) {
            return delegate.isValid(slot, resource);
        }

        @Override
        public long insert(int slot, R resource, long amount, TransactionContext transaction) {
            return delegate.insert(slot, resource, amount, transaction);
        }

        @Override
        public long extract(int slot, R resource, long amount, TransactionContext transaction) {
            return delegate.extract(slot, resource, amount, transaction);
        }
    }
}
