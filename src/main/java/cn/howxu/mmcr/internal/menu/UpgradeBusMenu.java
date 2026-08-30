package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Inventory menu for a standalone upgrade bus.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class UpgradeBusMenu extends AbstractMachineMenu {
    public static final int BUS_SLOT_START = 0;
    public static final int PLAYER_INVENTORY_SLOT_COUNT = 36;
    public static final int SLOT_SIZE = 18;
    private static final int BASE_IMAGE_HEIGHT = 166;

    private final UpgradeBusBlockEntity owner;
    private final BlockPos pos;
    private final UpgradeBusSize size;
    private final int busSlotCount;
    private final int busRows;
    private final int busColumns;

    public UpgradeBusMenu(int containerId, Inventory playerInv, UpgradeBusBlockEntity owner) {
        super(ModUIs.UPGRADE_BUS.get(), containerId);
        this.owner = owner;
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.size = owner == null ? UpgradeBusSize.NORMAL : owner.size();
        this.busSlotCount = size.slots();
        this.busRows = slotLayoutForSize(size).rows();
        this.busColumns = slotLayoutForSize(size).columns();
        addBusSlots(owner);
        addPlayerSlots(playerInv);
    }

    public UpgradeBusMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, BlockPos.ZERO, UpgradeBusSize.NORMAL);
    }

    private UpgradeBusMenu(int containerId, Inventory playerInv, BlockPos pos, UpgradeBusSize size) {
        super(ModUIs.UPGRADE_BUS.get(), containerId);
        this.owner = null;
        this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.size = validateSize(size);
        this.busSlotCount = this.size.slots();
        this.busRows = slotLayoutForSize(this.size).rows();
        this.busColumns = slotLayoutForSize(this.size).columns();
        addBusSlots(null);
        addPlayerSlots(playerInv);
    }

    public static UpgradeBusMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buffer) {
        return new UpgradeBusMenu(containerId, playerInv, buffer.readBlockPos(), readSize(buffer));
    }

    public static void writeClientOpenData(FriendlyByteBuf buffer, BlockPos pos, UpgradeBusSize size) {
        buffer.writeBlockPos(pos == null ? BlockPos.ZERO : pos);
        buffer.writeEnum(validateSize(size));
    }

    public UpgradeBusBlockEntity owner() {
        return owner;
    }

    public BlockPos pos() {
        return pos;
    }

    public UpgradeBusSize size() {
        return size;
    }

    public int busSlotCount() {
        return busSlotCount;
    }

    public int busRows() {
        return busRows;
    }

    public int busColumns() {
        return busColumns;
    }

    public int playerInventorySlotStart() {
        return busSlotCount;
    }

    public int imageHeight() {
        return BASE_IMAGE_HEIGHT + Math.max(0, busRows - 2) * SLOT_SIZE;
    }

    public String texturePath() {
        String texture = switch (size) {
            case NORMAL -> "small";
            case REINFORCED -> "normal";
            case ELITE -> "reinforced";
            case SUPER -> "big";
            case ULTIMATE -> "huge";
        };
        return "textures/gui/inventory_" + texture + ".png";
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (index < busSlotCount) {
            if (!slot.mayPickup(player) || !moveItemStackTo(stack, busSlotCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int slotIndex = 0; slotIndex < busSlotCount && !stack.isEmpty(); slotIndex++) {
                Slot busSlot = slots.get(slotIndex);
                ItemStack current = busSlot.getItem();
                if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, stack)) {
                    int previousCount = stack.getCount();
                    stack = busSlot.safeInsert(stack, stack.getCount());
                    if (stack.getCount() < previousCount) moved = true;
                }
            }
            for (int slotIndex = 0; slotIndex < busSlotCount && !stack.isEmpty(); slotIndex++) {
                Slot busSlot = slots.get(slotIndex);
                if (busSlot.hasItem()) continue;
                int previousCount = stack.getCount();
                stack = busSlot.safeInsert(stack, stack.getCount());
                if (stack.getCount() < previousCount) moved = true;
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
                && MenuSupport.stillValidWithin(player, pos);
    }

    private void addBusSlots(UpgradeBusBlockEntity owner) {
        SlotLayout layout = slotLayoutForSize(size);
        ItemStackHandler clientHandler = owner == null ? new ItemStackHandler(busSlotCount) : null;
        for (int index = 0; index < busSlotCount; index++) {
            int row = index / busColumns;
            int column = index % busColumns;
            int x = layout.startX() + column * SLOT_SIZE;
            int y = layout.startY() + row * SLOT_SIZE;
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(
                    owner == null ? clientHandler : owner.itemStackHandler(), index, x, y));
        }
    }

    private static SlotLayout slotLayoutForSize(UpgradeBusSize size) {
        return switch (validateSize(size)) {
            case NORMAL -> new SlotLayout(70, 18, 2, 2);
            case REINFORCED -> new SlotLayout(61, 18, 2, 3);
            case ELITE -> new SlotLayout(61, 13, 3, 3);
            case SUPER -> new SlotLayout(52, 18, 3, 4);
            case ULTIMATE -> new SlotLayout(53, 8, 4, 4);
        };
    }

    private static UpgradeBusSize readSize(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        UpgradeBusSize[] values = UpgradeBusSize.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid upgrade bus size ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static UpgradeBusSize validateSize(UpgradeBusSize size) {
        if (size == null) throw new IllegalArgumentException("Upgrade bus size must not be null");
        return size;
    }

    private record SlotLayout(int startX, int startY, int rows, int columns) {
    }
}
