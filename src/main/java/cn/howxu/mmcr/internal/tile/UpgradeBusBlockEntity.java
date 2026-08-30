package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Standalone item storage for one fixed-tier upgrade bus.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class UpgradeBusBlockEntity extends LinkedAppearanceBlockEntity {
    private static final String INVENTORY_KEY = "inventory";
    private static final String CONTENTS_VERSION_KEY = "contents_version";
    private static final int MAX_DROPPED_STACKS_PER_SLOT = 1024;

    private final UpgradeBusSize size;
    private final UpgradeBusItemStackHandler itemStackHandler;
    private final List<Runnable> controllerChangeListeners = new CopyOnWriteArrayList<>();
    private long contentsVersion;

    public UpgradeBusBlockEntity(UpgradeBusSize size, BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get(blockEntityId(size)).get(), pos, state);
        if (size == null) throw new IllegalArgumentException("Upgrade bus size must not be null");
        this.size = size;
        this.itemStackHandler = new UpgradeBusItemStackHandler(size.slots(), this::onContentsChanged);
    }

    public UpgradeBusSize size() {
        return size;
    }

    public ItemStackHandler itemStackHandler() {
        return itemStackHandler;
    }

    public List<ItemStack> itemSnapshot() {
        return itemStackHandler.stacks().stream().map(ItemStack::copy).toList();
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
        if (level == null || level.isClientSide()) return;
        for (int slot = 0; slot < itemStackHandler.getSlots(); slot++) {
            ItemStack stack = itemStackHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            int stackLimit = stack.getMaxStackSize();
            long droppedAmount = Math.min((long) stack.getCount(), (long) stackLimit * MAX_DROPPED_STACKS_PER_SLOT);
            while (droppedAmount > 0L) {
                int count = (int) Math.min(droppedAmount, stackLimit);
                Block.popResource(level, worldPosition, stack.copyWithCount(count));
                droppedAmount -= count;
            }
            if (stack.getCount() > (long) stackLimit * MAX_DROPPED_STACKS_PER_SLOT) {
                MMCR.LOG.warn("Discarding excess upgrade bus contents at {}", worldPosition);
            }
            itemStackHandler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropContents();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        itemStackHandler.serialize(output.child(INVENTORY_KEY));
        output.putLong(CONTENTS_VERSION_KEY, contentsVersion);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        itemStackHandler.deserialize(input.childOrEmpty(INVENTORY_KEY));
        contentsVersion = input.getLong(CONTENTS_VERSION_KEY).orElse(0L);
    }

    private void onContentsChanged(int slot) {
        contentsVersion++;
        setChanged();
        for (Runnable listener : controllerChangeListeners) listener.run();
    }

    private static String blockEntityId(UpgradeBusSize size) {
        if (size == null) throw new IllegalArgumentException("Upgrade bus size must not be null");
        return "upgrade_bus_" + size.id();
    }

    private static final class UpgradeBusItemStackHandler extends ItemStackHandler {
        private final int expectedSize;
        private final java.util.function.IntConsumer onChange;

        private UpgradeBusItemStackHandler(int size, java.util.function.IntConsumer onChange) {
            super(size);
            this.expectedSize = size;
            this.onChange = onChange;
        }

        @Override
        public void setSize(int size) {
            if (size == expectedSize) super.setSize(size);
        }

        @Override
        protected void onContentsChanged(int slot) {
            onChange.accept(slot);
        }

        private List<ItemStack> stacks() {
            java.util.ArrayList<ItemStack> snapshot = new java.util.ArrayList<>(getSlots());
            for (int slot = 0; slot < getSlots(); slot++) snapshot.add(getStackInSlot(slot));
            return snapshot;
        }
    }
}
