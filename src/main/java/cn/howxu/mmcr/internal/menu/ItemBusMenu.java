package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ItemBusMenu extends AbstractMachineMenu {

    public static final int BASE_IMAGE_HEIGHT = 166;
    public static final int SLOT_SIZE = 18;
    public static final int IMAGE_WIDTH = 176;
    public static final int BUS_SLOT_START = 0;
    public static final int DEFAULT_BUS_SLOT_COUNT = 6;
    public static final int PLAYER_INVENTORY_SLOT_COUNT = 36;

    private final ItemBusBlockEntity owner;
    private final BlockPos pos;
    private final int busSlotCount;
    private final ItemBusSize busSize;
    private final int busRows;
    private final int busColumns;

    /** 服务端:带 BE 引用,槽位直接绑定到 BE 的 ItemStackHandler。 */
    public ItemBusMenu(int containerId, Inventory playerInv, ItemBusBlockEntity owner) {
        super(ModUIs.ITEM_BUS.get(), containerId);
        this.owner = owner;
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.busSize = size(owner);
        this.busSlotCount = slotCount(owner);
        this.busRows = rowsForSize(busSize);
        this.busColumns = columnsForSize(busSize);
        addBusSlots(owner);
        addPlayerSlots(playerInv);
    }

    /** 客户端:BE 为 null;槽位数据由服务端通过数据包同步。 */
    public ItemBusMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, (ItemBusBlockEntity) null);
    }

    public ItemBusMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, pos, ItemBusSize.NORMAL, DEFAULT_BUS_SLOT_COUNT);
    }

    private ItemBusMenu(int containerId, Inventory playerInv, BlockPos pos,
                        ItemBusSize busSize, int busSlotCount) {
        super(ModUIs.ITEM_BUS.get(), containerId);
        this.owner = null;
        this.pos = pos;
        this.busSize = validateSize(busSize);
        this.busSlotCount = validateSlotCount(this.busSize, busSlotCount);
        this.busRows = rowsForSize(this.busSize);
        this.busColumns = columnsForSize(this.busSize);
        addBusSlots(null);
        addPlayerSlots(playerInv);
    }

    public static ItemBusMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        ItemBusSize busSize = readSize(buf);
        int busSlotCount = buf.readVarInt();
        return new ItemBusMenu(containerId, playerInv, pos, busSize, busSlotCount);
    }

    public static void writeClientOpenData(FriendlyByteBuf buf, BlockPos pos,
                                           ItemBusSize busSize, int busSlotCount) {
        ItemBusSize validatedSize = validateSize(busSize);
        int validatedSlotCount = validateSlotCount(validatedSize, busSlotCount);
        buf.writeBlockPos(pos);
        buf.writeEnum(validatedSize);
        buf.writeVarInt(validatedSlotCount);
    }

    public static void writeClientOpenData(FriendlyByteBuf buf, BlockPos pos, ItemBusBlockEntity owner) {
        writeClientOpenData(buf, pos, size(owner), slotCount(owner));
    }

    public ItemBusBlockEntity owner() {
        return owner;
    }

    public BlockPos pos() { return pos; }

    public int busSlotCount() { return busSlotCount; }

    public int busRows() { return busRows; }

    public int busColumns() { return busColumns; }

    public ItemBusSize busSize() { return busSize; }

    public int playerInventorySlotStart() { return playerInventorySlotStart(busSlotCount); }

    public int imageHeight() { return imageHeightForSize(busSize); }

    public String texturePath() { return texturePathForSize(busSize); }

    public boolean showsTitle() { return showsTitleForSize(busSize); }

    public static int rowsForSlots(int slots) {
        return rowsForSize(sizeForSlots(slots));
    }

    public static int imageHeightForSlots(int slots) {
        return imageHeightForSize(sizeForSlots(slots));
    }

    public static int imageHeightForSize(ItemBusSize size) {
        return BASE_IMAGE_HEIGHT + Math.max(0, rowsForSize(size) - 2) * SLOT_SIZE;
    }

    public static int rowsForSize(ItemBusSize size) {
        return slotLayoutForSize(size).rows();
    }

    public static int columnsForSize(ItemBusSize size) {
        return slotLayoutForSize(size).columns();
    }

    public static SlotLayout slotLayoutForSize(ItemBusSize size) {
        return switch (size) {
            case TINY -> new SlotLayout(81, 30, 1, 1);
            case SMALL -> new SlotLayout(70, 18, 2, 2);
            case NORMAL -> new SlotLayout(61, 18, 2, 3);
            case REINFORCED -> new SlotLayout(61, 13, 3, 3);
            case BIG -> new SlotLayout(52, 18, 3, 4);
            case HUGE -> new SlotLayout(53, 8, 4, 4);
            case LUDICROUS -> new SlotLayout(17, 8, 4, 8);
        };
    }

    public static String texturePathForSize(ItemBusSize size) {
        return "textures/gui/inventory_" + size.id() + ".png";
    }

    public static boolean showsTitleForSize(ItemBusSize size) {
        return false;
    }

    public static int playerInventorySlotStart(int busSlots) {
        return BUS_SLOT_START + busSlots;
    }

    public record SlotLayout(int startX, int startY, int rows, int columns) {}

    private void addBusSlots(ItemBusBlockEntity owner) {
        SimpleContainer clientContainer = owner == null ? new SimpleContainer(busSlotCount) : null;
        SlotLayout layout = slotLayoutForSize(busSize);
        for (int index = 0; index < busSlotCount; index++) {
            int row = index / busColumns;
            int col = index % busColumns;
            int x = layout.startX() + col * SLOT_SIZE;
            int y = layout.startY() + row * SLOT_SIZE;
            if (owner == null) addSlot(new Slot(clientContainer, index, x, y));
            else addSlot(new DirectionalItemSlot(owner.getItemStackHandler(null), index, x, y, owner.ioType()));
        }
    }

    private static int slotCount(IOPortBlockEntity owner) {
        return owner == null ? DEFAULT_BUS_SLOT_COUNT : owner.itemStorage().size();
    }

    private static ItemBusSize size(IOPortBlockEntity owner) {
        return owner == null ? ItemBusSize.NORMAL : owner.kind().itemBusSize().orElse(ItemBusSize.NORMAL);
    }

    private static ItemBusSize readSize(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        ItemBusSize[] values = ItemBusSize.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid item bus size ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static ItemBusSize validateSize(ItemBusSize size) {
        if (size == null) throw new IllegalArgumentException("Item bus size must not be null");
        return size;
    }

    private static int validateSlotCount(ItemBusSize size, int slots) {
        if (slots < 1 || slots > size.slots()) {
            throw new IllegalArgumentException("Invalid item bus slot count " + slots + " for " + size.id());
        }
        return slots;
    }

    private static ItemBusSize sizeForSlots(int slots) {
        for (ItemBusSize size : ItemBusSize.values()) {
            if (size.slots() == slots) return size;
        }
        return ItemBusSize.NORMAL;
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
        return owner == null || owner.getLevel() != null
                && owner.getLevel().getBlockEntity(pos) == owner
                && MenuSupport.stillValidWithin(player, owner.getBlockPos());
    }
}
