package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Standalone item storage for one fixed-tier upgrade bus.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class UpgradeBusBlockEntity extends LinkedAppearanceBlockEntity {
    private static final String CONTENTS_VERSION_KEY = "contents_version";

    private final UpgradeBusSize size;
    private final LongItemStorage storage;
    private final List<Runnable> controllerChangeListeners = new CopyOnWriteArrayList<>();
    private long contentsVersion;

    public UpgradeBusBlockEntity(UpgradeBusSize size, BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get(blockEntityId(size)).get(), pos, state);
        if (size == null) throw new IllegalArgumentException("Upgrade bus size must not be null");
        this.size = size;
        this.storage = new LongItemStorage(size.slots(), 64L, this::onContentsChanged);
    }

    public UpgradeBusSize size() {
        return size;
    }

    public LongItemStorage itemHandler() {
        return storage;
    }

    public List<ItemStack> itemSnapshot() {
        return IntStream.range(0, storage.size())
                .mapToObj(slot -> {
                    ItemStack resource = storage.resource(slot);
                    return resource.isEmpty()
                            ? ItemStack.EMPTY
                            : resource.copyWithCount((int) Math.min(storage.amount(slot), resource.getMaxStackSize()));
                })
                .toList();
    }

    public long contentsVersion() {
        return contentsVersion;
    }

    public void addControllerChangeListener(Runnable listener) {
        if (listener == null) throw new IllegalArgumentException("Controller change listener must not be null");
        controllerChangeListeners.add(listener);
    }

    public void removeControllerChangeListener(Runnable listener) {
        controllerChangeListeners.remove(listener);
    }

    public void dropContents() {
        ItemBusBlockEntity.dropItemResources(level, worldPosition, storage);
    }

    @Override
    public void onBlockRemoved() {
        super.onBlockRemoved();
        dropContents();
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = "_" + slot;
            ItemStack resource = storage.resource(slot);
            output.putBoolean("itemHasResource" + suffix, !resource.isEmpty());
            if (!resource.isEmpty()) {
                output.put("itemResource" + suffix, resource.save(registries));
                output.putLong("itemAmount" + suffix, storage.amount(slot));
            }
        }
        output.putLong(CONTENTS_VERSION_KEY, contentsVersion);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        super.loadAdditional(input, registries);
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = "_" + slot;
            if (input.getBoolean("itemHasResource" + suffix)) {
                ItemStack resource = ItemStack.parseOptional(registries, input.getCompound("itemResource" + suffix));
                storage.setContents(slot, resource, input.getLong("itemAmount" + suffix));
            } else {
                storage.setContents(slot, ItemStack.EMPTY, 0L);
            }
        }
        contentsVersion = input.getLong(CONTENTS_VERSION_KEY);
    }

    private void onContentsChanged() {
        contentsVersion++;
        setChanged();
        for (Runnable listener : controllerChangeListeners) listener.run();
    }

    private static String blockEntityId(UpgradeBusSize size) {
        if (size == null) throw new IllegalArgumentException("Upgrade bus size must not be null");
        return "upgrade_bus_" + size.id();
    }
}
