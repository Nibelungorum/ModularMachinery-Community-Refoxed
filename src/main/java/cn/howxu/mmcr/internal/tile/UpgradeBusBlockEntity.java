package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

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
    private final LongResourceStorage<ItemResource> storage;
    private final List<Runnable> controllerChangeListeners = new CopyOnWriteArrayList<>();
    private long contentsVersion;

    public UpgradeBusBlockEntity(UpgradeBusSize size, BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get(blockEntityId(size)).get(), pos, state);
        if (size == null) throw new IllegalArgumentException("Upgrade bus size must not be null");
        this.size = size;
        this.storage = new LongResourceStorage<>(ItemResource.class, size.slots(), 64L,
                ItemResource::isEmpty, this::onContentsChanged);
    }

    public UpgradeBusSize size() {
        return size;
    }

    public ResourceStorage<ItemResource> itemStorage() {
        return storage;
    }

    public List<ItemStack> itemSnapshot() {
        return java.util.stream.IntStream.range(0, storage.size())
                .mapToObj(slot -> {
                    ItemResource resource = storage.resource(slot);
                    return resource == null || resource.isEmpty()
                            ? ItemStack.EMPTY
                            : resource.toStack((int) Math.min(storage.amount(slot), resource.getMaxStackSize()));
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
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropContents();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = "_" + slot;
            ItemResource resource = storage.resource(slot);
            output.putBoolean("itemHasResource" + suffix, resource != null && !resource.isEmpty());
            if (resource != null && !resource.isEmpty()) {
                output.store("itemResource" + suffix, ItemResource.OPTIONAL_CODEC, resource);
                output.putLong("itemAmount" + suffix, storage.amount(slot));
            }
        }
        output.putLong(CONTENTS_VERSION_KEY, contentsVersion);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int slot = 0; slot < storage.size(); slot++) {
            String suffix = "_" + slot;
            if (input.getBooleanOr("itemHasResource" + suffix, false)) {
                ItemResource resource = input.read("itemResource" + suffix, ItemResource.OPTIONAL_CODEC)
                        .orElse(ItemResource.EMPTY);
                storage.setContents(slot, resource, input.getLong("itemAmount" + suffix).orElse(0L));
            } else {
                storage.setContents(slot, ItemResource.EMPTY, 0L);
            }
        }
        contentsVersion = input.getLong(CONTENTS_VERSION_KEY).orElse(0L);
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
