package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.internal.tile.ExtendedCombinedPortBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Text-only menu for an extended combined port.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedCombinedMenu extends AbstractMachineMenu {
    private final ExtendedCombinedPortBlockEntity owner;
    private final BlockPos pos;
    private final String kind;
    private final int itemSlotCount;
    private final int fluidTankCount;
    private List<ItemStorageEntry> itemEntries;
    private List<FluidStorageEntry> fluidEntries;

    public ExtendedCombinedMenu(int containerId, Inventory playerInv, ExtendedCombinedPortBlockEntity owner) {
        super(ModUIs.EXTENDED_COMBINED.get(), containerId);
        this.owner = owner;
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.kind = owner == null ? "extended_combined_input_advanced" : owner.kind().id();
        this.itemSlotCount = owner == null ? PktPortStorageSyncPayload.requireKind(kind).itemSlotCount()
                : owner.itemStorage().size();
        this.fluidTankCount = owner == null ? PktPortStorageSyncPayload.requireKind(kind).fluidTankCount()
                : owner.fluidStorage().size();
        if (owner == null) {
            this.itemEntries = List.of();
            this.fluidEntries = List.of();
        } else {
            PktPortStorageSyncPayload snapshot = PktPortStorageSyncPayload.from(owner);
            this.itemEntries = snapshot.itemEntries();
            this.fluidEntries = snapshot.fluidEntries();
        }
        addPlayerSlots(playerInv, 47);
    }

    public ExtendedCombinedMenu(int containerId, Inventory playerInv, BlockPos pos, String kind,
                                int itemSlotCount, int fluidTankCount) {
        super(ModUIs.EXTENDED_COMBINED.get(), containerId);
        PktPortStorageSyncPayload.IOPortKindView view = PktPortStorageSyncPayload.requireKind(kind);
        if (!view.isExtendedCombined() || itemSlotCount < 1 || itemSlotCount > view.itemSlotCount()
                || fluidTankCount < 1 || fluidTankCount > view.fluidTankCount()) {
            throw new IllegalArgumentException("Invalid extended combined menu data");
        }
        this.owner = null;
        this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.kind = view.id();
        this.itemSlotCount = itemSlotCount;
        this.fluidTankCount = fluidTankCount;
        this.itemEntries = List.of();
        this.fluidEntries = List.of();
        addPlayerSlots(playerInv, 47);
    }

    public static ExtendedCombinedMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buffer) {
        return new ExtendedCombinedMenu(containerId, playerInv, buffer.readBlockPos(), buffer.readUtf(256),
                buffer.readVarInt(), buffer.readVarInt());
    }

    public static void writeClientOpenData(FriendlyByteBuf buffer, BlockPos pos, String kind,
                                           int itemSlotCount, int fluidTankCount) {
        PktPortStorageSyncPayload.IOPortKindView view = PktPortStorageSyncPayload.requireKind(kind);
        if (!view.isExtendedCombined() || itemSlotCount < 1 || itemSlotCount > view.itemSlotCount()
                || fluidTankCount < 1 || fluidTankCount > view.fluidTankCount()) {
            throw new IllegalArgumentException("Invalid extended combined menu data");
        }
        buffer.writeBlockPos(pos == null ? BlockPos.ZERO : pos);
        buffer.writeUtf(view.id(), 256);
        buffer.writeVarInt(itemSlotCount);
        buffer.writeVarInt(fluidTankCount);
    }

    public static void writeClientOpenData(FriendlyByteBuf buffer, ExtendedCombinedPortBlockEntity owner) {
        writeClientOpenData(buffer, owner.getBlockPos(), owner.kind().id(),
                owner.itemStorage().size(), owner.fluidStorage().size());
    }

    public ExtendedCombinedPortBlockEntity owner() { return owner; }

    public BlockPos pos() { return pos; }

    public String kind() { return kind; }

    public int itemSlotCount() { return itemSlotCount; }

    public int fluidTankCount() { return fluidTankCount; }

    public List<ItemStorageEntry> itemEntries() { return itemEntries; }

    public List<FluidStorageEntry> fluidEntries() { return fluidEntries; }

    public Identifier selectedCapabilityId() { return MMCR.id("item"); }

    public boolean matches(BlockPos targetPos, String targetKind) {
        return pos.equals(targetPos) && kind.equals(targetKind);
    }

    public void applySnapshot(PktPortStorageSyncPayload payload) {
        if (payload == null || !matches(payload.pos(), payload.kind())) return;
        for (ItemStorageEntry entry : payload.itemEntries()) {
            if (entry.slot() >= itemSlotCount) throw new IllegalArgumentException("Item snapshot slot out of bounds");
        }
        for (FluidStorageEntry entry : payload.fluidEntries()) {
            if (entry.slot() >= fluidTankCount) throw new IllegalArgumentException("Fluid snapshot slot out of bounds");
        }
        itemEntries = payload.itemEntries();
        fluidEntries = payload.fluidEntries();
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
