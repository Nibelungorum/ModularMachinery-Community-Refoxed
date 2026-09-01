package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.publicapi.machine.MachineIoView;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Ordinary combined menu with item slots and fluid presentation data.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CombinedPortMenu extends AbstractMachineMenu {
    public static final int FIRST_TANK_X = 15;
    public static final int FIRST_TANK_Y = 10;
    public static final int SECOND_TANK_X = 42;
    public static final int SECOND_TANK_Y = 10;
    private static final int SLOT_SIZE = 18;

    private final CombinedPortBlockEntity owner;
    private final BlockPos pos;
    private final String kind;
    private final int itemSlotCount;
    private final int fluidTankCount;
    private final List<FluidTankLayout> fluidTankLayouts;
    private List<ItemStorageEntry> itemEntries;
    private List<FluidStorageEntry> fluidEntries;
    private List<CapabilityDisplay> displayEntries;

    public CombinedPortMenu(int containerId, Inventory playerInv, CombinedPortBlockEntity owner) {
        super(ModUIs.COMBINED.get(), containerId);
        this.owner = owner;
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.kind = owner == null ? "combined_input_basic" : owner.kind().id();
        this.displayEntries = owner == null ? List.of() : new MachineIoView(owner.capabilitySnapshot()).displays();
        this.itemSlotCount = owner == null ? PktPortStorageSyncPayload.requireKind(kind).itemSlotCount()
                : owner.itemStorage().size();
        this.fluidTankCount = owner == null ? PktPortStorageSyncPayload.requireKind(kind).fluidTankCount()
                : owner.fluidStorage().size();
        this.fluidTankLayouts = layouts(fluidTankCount);
        addItemSlots(owner);
        addPlayerSlots(playerInv);
        if (owner == null) {
            this.itemEntries = List.of();
            this.fluidEntries = List.of();
        } else {
            this.itemEntries = PktPortStorageSyncPayload.itemEntries(owner.itemStorage());
            this.fluidEntries = PktPortStorageSyncPayload.fluidEntries(owner.fluidStorage());
        }
    }

    public CombinedPortMenu(int containerId, Inventory playerInv, BlockPos pos, String kind,
                            int itemSlotCount, int fluidTankCount) {
        super(ModUIs.COMBINED.get(), containerId);
        PktPortStorageSyncPayload.IOPortKindView view = PktPortStorageSyncPayload.requireKind(kind);
        if (!view.isCombined() || itemSlotCount < 1 || itemSlotCount > view.itemSlotCount()
                || fluidTankCount < 1 || fluidTankCount > view.fluidTankCount()) {
            throw new IllegalArgumentException("Invalid combined menu data");
        }
        this.owner = null;
        this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.kind = view.id();
        this.itemSlotCount = itemSlotCount;
        this.fluidTankCount = fluidTankCount;
        this.fluidTankLayouts = layouts(fluidTankCount);
        addItemSlots(null);
        addPlayerSlots(playerInv);
        this.itemEntries = List.of();
        this.fluidEntries = List.of();
        this.displayEntries = List.of();
    }

    public static CombinedPortMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buffer) {
        return new CombinedPortMenu(containerId, playerInv, buffer.readBlockPos(), buffer.readUtf(256),
                buffer.readVarInt(), buffer.readVarInt());
    }

    public static void writeClientOpenData(FriendlyByteBuf buffer, BlockPos pos, String kind,
                                           int itemSlotCount, int fluidTankCount) {
        PktPortStorageSyncPayload.IOPortKindView view = PktPortStorageSyncPayload.requireKind(kind);
        if (!view.isCombined() || itemSlotCount < 1 || itemSlotCount > view.itemSlotCount()
                || fluidTankCount < 1 || fluidTankCount > view.fluidTankCount()) {
            throw new IllegalArgumentException("Invalid combined menu data");
        }
        buffer.writeBlockPos(pos == null ? BlockPos.ZERO : pos);
        buffer.writeUtf(view.id(), 256);
        buffer.writeVarInt(itemSlotCount);
        buffer.writeVarInt(fluidTankCount);
    }

    public static void writeClientOpenData(FriendlyByteBuf buffer, CombinedPortBlockEntity owner) {
        writeClientOpenData(buffer, owner.getBlockPos(), owner.kind().id(),
                owner.itemStorage().size(), owner.fluidStorage().size());
    }

    public record FluidTankLayout(int slot, int x, int y) {
    }

    public CombinedPortBlockEntity owner() { return owner; }

    public BlockPos pos() { return pos; }

    public String kind() { return kind; }

    public int itemSlotCount() { return itemSlotCount; }

    public int fluidTankCount() { return fluidTankCount; }

    public int busSlotCount() { return itemSlotCount; }

    public int playerInventorySlotStart() { return itemSlotCount; }

    public List<FluidTankLayout> fluidTankLayouts() { return fluidTankLayouts; }

    public List<ItemStorageEntry> itemEntries() { return itemEntries; }

    public List<FluidStorageEntry> fluidEntries() { return fluidEntries; }

    public List<CapabilityDisplay> displayEntries() { return displayEntries; }

    public Identifier selectedCapabilityId() { return MMCR.id("item"); }

    public boolean matches(BlockPos targetPos, String targetKind) {
        return pos.equals(targetPos) && kind.equals(targetKind);
    }

    public void applySnapshot(PktPortStorageSyncPayload payload, IOPortBlockEntity port) {
        if (payload == null || !matches(payload.pos(), payload.kind())) return;
        List<ItemStorageEntry> nextItems = PktPortStorageSyncPayload.itemEntries(port.itemStorage());
        List<FluidStorageEntry> nextFluids = PktPortStorageSyncPayload.fluidEntries(port.fluidStorage());
        for (ItemStorageEntry entry : nextItems) {
            if (entry.slot() >= itemSlotCount) throw new IllegalArgumentException("Item snapshot slot out of bounds");
        }
        for (FluidStorageEntry entry : nextFluids) {
            if (entry.slot() >= fluidTankCount) throw new IllegalArgumentException("Fluid snapshot slot out of bounds");
        }
        itemEntries = nextItems;
        fluidEntries = nextFluids;
        displayEntries = new MachineIoView(port.capabilitySnapshot()).displays();
    }

    private void addItemSlots(CombinedPortBlockEntity owner) {
        SimpleContainer clientContainer = owner == null ? new SimpleContainer(itemSlotCount) : null;
        ItemBusMenu.SlotLayout layout = slotLayout(itemSlotCount);
        int columns = layout.columns();
        for (int index = 0; index < itemSlotCount; index++) {
            int row = index / columns;
            int col = index % columns;
            int x = layout.startX() + col * SLOT_SIZE;
            int y = layout.startY() + row * SLOT_SIZE;
            if (owner == null) addSlot(new Slot(clientContainer, index, x, y));
            else addSlot(new DirectionalItemSlot(owner.getItemStackHandler(null), index, x, y, owner.ioType()));
        }
    }

    private static ItemBusMenu.SlotLayout slotLayout(int itemSlots) {
        return switch (itemSlots) {
            case 6 -> new ItemBusMenu.SlotLayout(62, 22, 2, 3);
            case 9 -> new ItemBusMenu.SlotLayout(62, 14, 3, 3);
            case 12 -> new ItemBusMenu.SlotLayout(81, 14, 3, 4);
            case 16 -> new ItemBusMenu.SlotLayout(80, 7, 4, 4);
            default -> throw new IllegalArgumentException("Unsupported combined item slot count: " + itemSlots);
        };
    }

    private static List<FluidTankLayout> layouts(int tankCount) {
        if (tankCount < 1 || tankCount > 2) throw new IllegalArgumentException("Unsupported fluid tank count: " + tankCount);
        return tankCount == 1
                ? List.of(new FluidTankLayout(0, FIRST_TANK_X, FIRST_TANK_Y))
                : List.of(new FluidTankLayout(0, FIRST_TANK_X, FIRST_TANK_Y),
                        new FluidTankLayout(1, SECOND_TANK_X, SECOND_TANK_Y));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < itemSlotCount) {
            if (!moveItemStackTo(stack, itemSlotCount, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int slotIndex = 0; slotIndex < itemSlotCount && !stack.isEmpty(); slotIndex++) {
                Slot itemSlot = slots.get(slotIndex);
                ItemStack current = itemSlot.getItem();
                if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, stack)) {
                    int previousCount = stack.getCount();
                    stack = itemSlot.safeInsert(stack);
                    if (stack.getCount() < previousCount) {
                        moved = true;
                    }
                }
            }
            for (int slotIndex = 0; slotIndex < itemSlotCount && !stack.isEmpty(); slotIndex++) {
                Slot itemSlot = slots.get(slotIndex);
                if (itemSlot.hasItem()) continue;
                int previousCount = stack.getCount();
                stack = itemSlot.safeInsert(stack);
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
