package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.tile.ExtendedFluidHatchBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Text-only menu for an extended fluid hatch.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExtendedFluidMenu extends AbstractMachineMenu {
    private final ExtendedFluidHatchBlockEntity owner;
    private final BlockPos pos;
    private final String kind;
    private final int tankCount;
    private List<FluidStorageEntry> entries;

    public ExtendedFluidMenu(int containerId, Inventory playerInv, ExtendedFluidHatchBlockEntity owner) {
        super(ModUIs.EXTENDED_FLUID.get(), containerId);
        this.owner = owner;
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.kind = owner == null ? "extended_fluid_input_hatch_basic" : owner.kind().id();
        this.tankCount = owner == null ? PktPortStorageSyncPayload.requireKind(kind).fluidTankCount()
                : owner.fluidStorage().size();
        this.entries = owner == null ? List.of() : PktPortStorageSyncPayload.from(owner).fluidEntries();
    }

    public ExtendedFluidMenu(int containerId, Inventory playerInv, BlockPos pos, String kind, int tankCount) {
        super(ModUIs.EXTENDED_FLUID.get(), containerId);
        PktPortStorageSyncPayload.IOPortKindView view = PktPortStorageSyncPayload.requireKind(kind);
        if (!view.isExtendedFluid() || tankCount < 1 || tankCount > view.fluidTankCount()) {
            throw new IllegalArgumentException("Invalid extended fluid menu data");
        }
        this.owner = null;
        this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
        this.kind = view.id();
        this.tankCount = tankCount;
        this.entries = List.of();
    }

    public static ExtendedFluidMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buffer) {
        return new ExtendedFluidMenu(containerId, playerInv, buffer.readBlockPos(),
                buffer.readUtf(256), buffer.readVarInt());
    }

    public static void writeClientOpenData(FriendlyByteBuf buffer, BlockPos pos, String kind, int tankCount) {
        PktPortStorageSyncPayload.IOPortKindView view = PktPortStorageSyncPayload.requireKind(kind);
        if (!view.isExtendedFluid() || tankCount < 1 || tankCount > view.fluidTankCount()) {
            throw new IllegalArgumentException("Invalid extended fluid menu data");
        }
        buffer.writeBlockPos(pos == null ? BlockPos.ZERO : pos);
        buffer.writeUtf(view.id(), 256);
        buffer.writeVarInt(tankCount);
    }

    public static void writeClientOpenData(FriendlyByteBuf buffer, ExtendedFluidHatchBlockEntity owner) {
        writeClientOpenData(buffer, owner.getBlockPos(), owner.kind().id(), owner.fluidStorage().size());
    }

    public ExtendedFluidHatchBlockEntity owner() { return owner; }

    public BlockPos pos() { return pos; }

    public String kind() { return kind; }

    public int tankCount() { return tankCount; }

    public int slotCount() { return tankCount; }

    public List<FluidStorageEntry> entries() { return entries; }

    public Identifier selectedCapabilityId() { return MMCR.id("fluid"); }

    public boolean matches(BlockPos targetPos, String targetKind) {
        return pos.equals(targetPos) && kind.equals(targetKind);
    }

    public void applySnapshot(PktPortStorageSyncPayload payload) {
        if (payload == null || !matches(payload.pos(), payload.kind())) return;
        for (FluidStorageEntry entry : payload.fluidEntries()) {
            if (entry.slot() >= tankCount) throw new IllegalArgumentException("Fluid snapshot slot out of bounds");
        }
        entries = payload.fluidEntries();
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
