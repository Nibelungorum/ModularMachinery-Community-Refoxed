package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ItemBusMenu extends MMCRMenuBase {

    public static final int COLS = 3;
    public static final int ROWS = 3;

    private final ItemBusBlockEntity owner;

    /** 服务端:带 BE 引用,槽位直接绑定到 BE 的 ItemStackHandler。 */
    public ItemBusMenu(int containerId, Inventory playerInv, ItemBusBlockEntity owner) {
        super(ModUIs.ITEM_BUS.get(), containerId);
        this.owner = owner;
        if (owner != null) addBusSlots(owner.getItemStackHandler(null));
        addPlayerSlots(playerInv);
    }

    /** 客户端:BE 为 null;槽位数据由服务端通过数据包同步。 */
    public ItemBusMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, null);
    }

    public static ItemBusMenu clientOpen(int containerId, Inventory playerInv) {
        return new ItemBusMenu(containerId, playerInv);
    }

    public ItemBusBlockEntity owner() {
        return owner;
    }

    private void addBusSlots(ItemStackHandler handler) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                addSlot(new SlotItemHandler(handler, index, 62 + col * 18, 18 + row * 18));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int busSlots = COLS * ROWS;
        if (index < busSlots) {
            if (!moveItemStackTo(stack, busSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, busSlots, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || MMCRMenu.stillValidWithin(player, owner.getBlockPos());
    }
}