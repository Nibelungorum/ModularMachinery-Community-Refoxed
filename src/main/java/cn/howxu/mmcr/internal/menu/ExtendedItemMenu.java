package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.internal.tile.ExtendedItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Text-only menu for an extended item bus.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedItemMenu extends AbstractMachineMenu {
    private final ExtendedItemBusBlockEntity owner;
    private final BlockPos pos;
    private final String kind;
    private final int slotCount;
    private List<ItemStorageEntry> entries;

    public ExtendedItemMenu(int containerId, Inventory playerInv, ExtendedItemBusBlockEntity owner) {
        super(ModUIs.EXTENDED_ITEM.get(), containerId);
        this.owner = owner;
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.kind = owner == null ? "extended_item_input_bus_basic" : owner.kind().id();
        this.slotCount = owner == null ? PktPortStorageSyncPayload.requireKind(kind).itemSlotCount()
                : owner.itemStorage().size();
        this.entries = owner == null ? List.of() : PktPortStorageSyncPayload.from(owner).itemEntries();
        addPlayerSlots(playerInv, 47);
    }

    public ExtendedItemMenu(int containerId, Inventory playerInv, BlockPos pos, String kind, int slotCount) {
        super(ModUIs.EXTENDED_ITEM.get(), containerId);
        PktPortStorageSyncPayload.IOPortKindView view = PktPortStorageSyncPayload.requireKind(kind);
        if (!view.isExtendedItem() || slotCount < 1 || slotCount > view.itemSlotCount()) {
            throw new IllegalArgumentException("Invalid extended item menu data");
        }
        this.owner = null;
        this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.kind = view.id();
        this.slotCount = slotCount;
        this.entries = List.of();
        addPlayerSlots(playerInv, 47);
    }

    public static ExtendedItemMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buffer) {
        return new ExtendedItemMenu(containerId, playerInv, buffer.readBlockPos(),
                buffer.readUtf(256), buffer.readVarInt());
    }

    public static void writeClientOpenData(FriendlyByteBuf buffer, BlockPos pos, String kind, int slotCount) {
        PktPortStorageSyncPayload.IOPortKindView view = PktPortStorageSyncPayload.requireKind(kind);
        if (!view.isExtendedItem() || slotCount < 1 || slotCount > view.itemSlotCount()) {
            throw new IllegalArgumentException("Invalid extended item menu data");
        }
        buffer.writeBlockPos(pos == null ? BlockPos.ZERO : pos);
        buffer.writeUtf(view.id(), 256);
        buffer.writeVarInt(slotCount);
    }

    public static void writeClientOpenData(FriendlyByteBuf buffer, ExtendedItemBusBlockEntity owner) {
        writeClientOpenData(buffer, owner.getBlockPos(), owner.kind().id(), owner.itemStorage().size());
    }

    public ExtendedItemBusBlockEntity owner() { return owner; }

    public BlockPos pos() { return pos; }

    public String kind() { return kind; }

    public int slotCount() { return slotCount; }

    public List<ItemStorageEntry> entries() { return entries; }

    public Identifier selectedCapabilityId() { return MMCR.id("item"); }

    public boolean matches(BlockPos targetPos, String targetKind) {
        return pos.equals(targetPos) && kind.equals(targetKind);
    }

    public void applySnapshot(PktPortStorageSyncPayload payload) {
        if (payload == null || !matches(payload.pos(), payload.kind())) return;
        for (ItemStorageEntry entry : payload.itemEntries()) {
            if (entry.slot() >= slotCount) throw new IllegalArgumentException("Item snapshot slot out of bounds");
        }
        entries = payload.itemEntries();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || owner.getLevel() != null
                && owner.getLevel().getBlockEntity(pos) == owner
                && MenuSupport.stillValidWithin(player, owner.getBlockPos());
    }
}
