package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * AE2 interface output host whose native handlers insert directly into the grid.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AsyncOutputInterfaceBlockEntity extends OutputInterfaceBaseBlockEntity {
    private final IItemHandler itemHandler;
    private final IFluidHandler fluidHandler;

    public AsyncOutputInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        this(pos, state, kind, null, null);
    }

    AsyncOutputInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind,
                                    @Nullable Supplier<@Nullable MEStorage> networkSupplier,
                                    @Nullable IActionSource actionSource) {
        super(pos, state, kind);
        Supplier<@Nullable MEStorage> effectiveNetworkSupplier = networkSupplier == null
                ? this::networkStorage : networkSupplier;
        IActionSource effectiveActionSource = actionSource == null
                ? IActionSource.ofMachine(this) : actionSource;
        itemHandler = AE2NativeAdapters.outputItems(getStorage(), effectiveNetworkSupplier,
                effectiveActionSource, this::notifyStorageChanged);
        fluidHandler = AE2NativeAdapters.outputFluids(getStorage(), effectiveNetworkSupplier,
                effectiveActionSource, this::notifyStorageChanged);
    }

    @Override
    public IItemHandler nativeItemHandler() {
        return itemHandler;
    }

    @Override
    public IFluidHandler nativeFluidHandler() {
        return fluidHandler;
    }

    @Nullable
    private MEStorage networkStorage() {
        IGrid grid = mainNode.getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }
}
