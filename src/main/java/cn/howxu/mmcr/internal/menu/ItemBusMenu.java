package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ItemBusMenu extends AbstractMachineMenu {

    public static final int COLS = 3;
    public static final int ROWS = 2;
    public static final int BUS_SLOT_START = 0;
    public static final int BUS_SLOT_COUNT = COLS * ROWS;
    public static final int PLAYER_INVENTORY_SLOT_START = BUS_SLOT_COUNT;
    public static final int PLAYER_INVENTORY_SLOT_COUNT = 36;

    private final ItemBusBlockEntity owner;

    /** 服务端:带 BE 引用,槽位直接绑定到 BE 的 ItemStackHandler。 */
    public ItemBusMenu(int containerId, Inventory playerInv, ItemBusBlockEntity owner) {
        super(ModUIs.ITEM_BUS.get(), containerId);
        this.owner = owner;
        addBusSlots(owner);
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

    private void addBusSlots(ItemBusBlockEntity owner) {
        SimpleContainer clientContainer = owner == null ? new SimpleContainer(COLS * ROWS) : null;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                if (owner == null) addSlot(new Slot(clientContainer, index, 61 + col * 18, 18 + row * 18));
                else addSlot(new DirectionalItemSlot(owner.getItemStackHandler(null), index, 61 + col * 18, 18 + row * 18, owner.ioType()));
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
            if (!slot.mayPickup(player)) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, busSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int slotIndex = 0; slotIndex < busSlots && !stack.isEmpty(); slotIndex++) {
                Slot busSlot = slots.get(slotIndex);
                ItemStack current = busSlot.getItem();
                if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, stack)) {
                    int previousCount = stack.getCount();
                    stack = busSlot.safeInsert(stack);
                    if (stack.getCount() < previousCount) {
                        moved = true;
                    }
                }
            }
            for (int slotIndex = 0; slotIndex < busSlots && !stack.isEmpty(); slotIndex++) {
                Slot busSlot = slots.get(slotIndex);
                if (busSlot.hasItem()) continue;
                int previousCount = stack.getCount();
                stack = busSlot.safeInsert(stack);
                if (stack.getCount() < previousCount) {
                    moved = true;
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || MenuSupport.stillValidWithin(player, owner.getBlockPos());
    }
}
