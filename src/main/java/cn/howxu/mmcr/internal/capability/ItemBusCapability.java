package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Machine capability backed by an item bus slot storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ItemBusCapability implements MachineCapability {
    private final IOType ioType;
    private final ResourceStorage<ItemResource> storage;
    private final CapabilityView view;

    public ItemBusCapability(ItemBusBlockEntity port) {
        this.ioType = port.ioType();
        this.storage = port.getResourceStorage();
        this.view = CapabilityFactories.view(type(), ioType);
    }

    public ResourceStorage<ItemResource> storage() {
        return storage;
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
