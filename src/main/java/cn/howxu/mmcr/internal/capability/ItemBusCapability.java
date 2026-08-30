package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * Machine capability backed by an item bus slot storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ItemBusCapability implements MachineCapability {
    private final IOPortBlockEntity port;
    private final IOType ioType;
    private final ResourceStorage<ItemResource> storage;
    private final CapabilityView view;

    public ItemBusCapability(ResourceStorage<ItemResource> storage, IOType ioType) {
        this(null, storage, ioType);
    }

    public ItemBusCapability(IOPortBlockEntity port, ResourceStorage<ItemResource> storage, IOType ioType) {
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (ioType == null) throw new IllegalArgumentException("ioType must not be null");
        this.port = port;
        this.ioType = ioType;
        this.storage = storage;
        this.view = CapabilityFactories.view(type(), ioType);
    }

    public ItemBusCapability(ItemBusBlockEntity port) {
        this(port, port.itemStorage(), port.ioType());
    }

    public ResourceStorage<ItemResource> storage() {
        return storage;
    }

    @Nullable
    public Level level() {
        return port == null ? null : port.getLevel();
    }

    public BlockPos position() {
        return port == null ? BlockPos.ZERO : port.getBlockPos();
    }

    public boolean supportsLargeStacks() {
        return port != null && (port.kind().extendedItemBusSize().isPresent()
                || port.kind().extendedCombinedPortSize().isPresent());
    }

    public int transferLimit() {
        return 64;
    }

    @Override
    public CapabilityType type() {
        return CapabilityFactories.ITEM_TYPE;
    }

    @Override
    public IOType ioType() {
        return ioType;
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        return CapabilityFactories.operation(type(), ioType, request, storage);
    }
}
