package cn.howxu.mmcr.internal.assembly;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Player inventory backed storage for structure assembly blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PlayerInventoryStructureItemStorage implements StructureItemStorage {
    private final Slots slots;
    private final StructureItemSource source;
    private final StructureItemSink sink = this::accept;

    public PlayerInventoryStructureItemStorage(Player player) {
        this(new InventorySlots(player.getInventory()));
    }

    private PlayerInventoryStructureItemStorage(Slots slots) {
        this.slots = slots;
        this.source = new StructureItemSource() {
            @Override
            public List<ItemStack> copyStacks() {
                return playerSource().copyStacks();
            }

            @Override
            public boolean canExtractAll(List<ItemStack> requirements) {
                return playerSource().canExtractAll(requirements);
            }

            @Override
            public boolean extractAll(List<ItemStack> requirements) {
                return playerSource().extractAll(requirements);
            }

            private PlayerInventoryStructureItemSource playerSource() {
                return PlayerInventoryStructureItemSource.forStacks(slots.stacks());
            }
        };
    }

    public static PlayerInventoryStructureItemStorage forStacks(List<ItemStack> stacks) {
        return new PlayerInventoryStructureItemStorage(new ListSlots(stacks));
    }

    @Override
    public StructureItemSource source() {
        return source;
    }

    @Override
    public StructureItemSink sink() {
        return sink;
    }

    private boolean accept(ItemStack stack) {
        if (stack.isEmpty()) return true;
        List<ItemStack> simulated = new ArrayList<>(slots.size());
        for (int slot = 0; slot < slots.size(); slot++) simulated.add(slots.get(slot).copy());
        ItemStack remaining = stack.copy();
        for (ItemStack existing : simulated) {
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int inserted = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
            existing.grow(inserted);
            remaining.shrink(inserted);
            if (remaining.isEmpty()) break;
        }
        for (int slot = 0; !remaining.isEmpty() && slot < simulated.size(); slot++) {
            if (!simulated.get(slot).isEmpty()) continue;
            int inserted = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            simulated.set(slot, remaining.copyWithCount(inserted));
            remaining.shrink(inserted);
        }
        if (!remaining.isEmpty()) return false;
        for (int slot = 0; slot < simulated.size(); slot++) slots.set(slot, simulated.get(slot));
        return true;
    }

    private interface Slots {
        int size();
        ItemStack get(int slot);
        void set(int slot, ItemStack stack);

        default List<ItemStack> stacks() {
            List<ItemStack> stacks = new ArrayList<>(size());
            for (int slot = 0; slot < size(); slot++) stacks.add(get(slot));
            return stacks;
        }
    }

    private record ListSlots(List<ItemStack> stacks) implements Slots {
        @Override public int size() { return stacks.size(); }
        @Override public ItemStack get(int slot) { return stacks.get(slot); }
        @Override public void set(int slot, ItemStack stack) { stacks.set(slot, stack); }
    }

    private record InventorySlots(Inventory inventory) implements Slots {
        @Override public int size() { return inventory.getContainerSize(); }
        @Override public ItemStack get(int slot) { return inventory.getItem(slot); }
        @Override public void set(int slot, ItemStack stack) { inventory.setItem(slot, stack); }
    }
}
