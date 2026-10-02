package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.core.settings.TickRates;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Normal AE2 Interface output host with a bounded local cache.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class OutputInterfaceBlockEntity extends OutputInterfaceBaseBlockEntity {
    private final IItemHandler itemHandler;
    private final IFluidHandler fluidHandler;
    private final Supplier<@Nullable MEStorage> networkSupplier;
    public final OutputTicker outputTicker;

    public OutputInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        this(pos, state, kind, null, null);
    }

    public OutputInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind,
                                      @Nullable Supplier<@Nullable MEStorage> networkSupplier,
                                      @Nullable IActionSource actionSource) {
        super(pos, state, kind);
        Supplier<@Nullable MEStorage> effectiveNetworkSupplier = networkSupplier == null
                ? this::gridStorage : networkSupplier;
        this.networkSupplier = effectiveNetworkSupplier;
        IActionSource effectiveActionSource = actionSource == null
                ? IActionSource.ofMachine(this) : actionSource;
        outputTicker = new OutputTicker();
        mainNode.addService(IGridTickable.class, outputTicker);
        itemHandler = AE2NativeAdapters.outputItems(getStorage(), effectiveNetworkSupplier,
                effectiveActionSource, this::onStorageChanged);
        fluidHandler = AE2NativeAdapters.outputFluids(getStorage(), effectiveNetworkSupplier,
                effectiveActionSource, this::onStorageChanged);
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
    protected void onNetworkChanged() {
        super.onNetworkChanged();
        wakeOutputTicker();
    }

    public void onStorageChanged() {
        notifyStorageChanged();
        wakeOutputTicker();
    }

    private void wakeOutputTicker() {
        mainNode.ifPresent((grid, node) -> {
            if (getStorage().isEmpty()) {
                grid.getTickManager().sleepDevice(node);
            } else {
                grid.getTickManager().alertDevice(node);
            }
        });
    }

    @Nullable
    public MEStorage networkStorage() {
        return networkSupplier.get();
    }

    @Nullable
    private MEStorage gridStorage() {
        IGrid grid = mainNode.getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    final class OutputTicker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(TickRates.Interface, getStorage().isEmpty());
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!node.isActive()) return TickRateModulation.SLEEP;

            long moved = AE2NativeAdapters.flush(getStorage(), OutputInterfaceBlockEntity.this::networkStorage,
                    IActionSource.ofMachine(OutputInterfaceBlockEntity.this), appeng.api.stacks.AEKeyType.items(), 256L);
            if (moved < 256L) {
                moved += AE2NativeAdapters.flush(getStorage(), OutputInterfaceBlockEntity.this::networkStorage,
                        IActionSource.ofMachine(OutputInterfaceBlockEntity.this), appeng.api.stacks.AEKeyType.fluids(),
                        256L - moved);
            }
            if (moved < 256L) moved += AppMekBridge.get().flush(OutputInterfaceBlockEntity.this, 256L - moved);
            if (getStorage().isEmpty()) return TickRateModulation.SLEEP;
            return moved > 0L ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
        }
    }
}
