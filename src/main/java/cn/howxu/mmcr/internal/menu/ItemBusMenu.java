package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ItemBusMenu extends AbstractMachineMenu {

    public static final int COLS = 4;
    public static final int BASE_IMAGE_HEIGHT = 166;
    public static final int SLOT_SIZE = 18;
    public static final int BUS_SLOT_START = 0;
    public static final int DEFAULT_BUS_SLOT_COUNT = 6;
    public static final int PLAYER_INVENTORY_SLOT_COUNT = 36;

    private final ItemBusBlockEntity owner;
    private final Level level;
    private final BlockPos pos;
    private final int busSlotCount;
    private final int busRows;

    /** 服务端:带 BE 引用,槽位直接绑定到 BE 的 ItemStackHandler。 */
    public ItemBusMenu(int containerId, Inventory playerInv, ItemBusBlockEntity owner) {
        super(ModUIs.ITEM_BUS.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.busSlotCount = slotCount(owner);
        this.busRows = rowsForSlots(busSlotCount);
        addBusSlots(owner);
        addPlayerSlots(playerInv, playerInventoryYOffset());
    }

    /** 客户端:BE 为 null;槽位数据由服务端通过数据包同步。 */
    public ItemBusMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, (ItemBusBlockEntity) null);
    }

    public ItemBusMenu(int containerId, Inventory playerInv, BlockPos pos) {
        super(ModUIs.ITEM_BUS.get(), containerId);
        this.owner = null;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = pos;
        ItemBusBlockEntity resolved = resolvedOwner();
        this.busSlotCount = slotCount(resolved);
        this.busRows = rowsForSlots(busSlotCount);
        addBusSlots(resolved);
        addPlayerSlots(playerInv, playerInventoryYOffset());
    }

    public static ItemBusMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        return new ItemBusMenu(containerId, playerInv, buf.readBlockPos());
    }

    public ItemBusBlockEntity owner() {
        return owner;
    }

    public int busSlotCount() { return busSlotCount; }

    public int busRows() { return busRows; }

    public int playerInventorySlotStart() { return playerInventorySlotStart(busSlotCount); }

    public int imageHeight() { return imageHeightForSlots(busSlotCount); }

    public static int rowsForSlots(int slots) {
        return Math.max(1, (slots + COLS - 1) / COLS);
    }

    public static int imageHeightForSlots(int slots) {
        return BASE_IMAGE_HEIGHT + Math.max(0, rowsForSlots(slots) - 2) * SLOT_SIZE;
    }

    public static int playerInventorySlotStart(int busSlots) {
        return BUS_SLOT_START + busSlots;
    }

    private void addBusSlots(ItemBusBlockEntity owner) {
        SimpleContainer clientContainer = owner == null ? new SimpleContainer(busSlotCount) : null;
        for (int index = 0; index < busSlotCount; index++) {
            int row = index / COLS;
            int col = index % COLS;
            if (owner == null) addSlot(new Slot(clientContainer, index, 52 + col * SLOT_SIZE, 18 + row * SLOT_SIZE));
            else addSlot(new DirectionalItemSlot(owner.getItemStackHandler(null), index, 52 + col * SLOT_SIZE, 18 + row * SLOT_SIZE, owner.ioType()));
        }
    }

    private int playerInventoryYOffset() {
        return Math.max(0, busRows - 2) * SLOT_SIZE;
    }

    private ItemBusBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof ItemBusBlockEntity bus ? bus : null;
    }

    private static int slotCount(ItemBusBlockEntity owner) {
        return owner == null ? DEFAULT_BUS_SLOT_COUNT : owner.getItemStackHandler(null).getSlots();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int busSlots = busSlotCount;
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
