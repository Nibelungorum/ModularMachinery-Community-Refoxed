package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.compat.ars_nouveau.SourceViewFacet;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
import cn.howxu.mmcr.internal.menu.LongDataSlot;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Synchronizes real source storage without exposing native Ars capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourcePortMenu extends AbstractMachineMenu {
    private final IOPortBlockEntity owner;
    private final BlockPos pos;
    private final LongDataSlot stored;
    private final LongDataSlot capacity;

    public SourcePortMenu(int containerId, Inventory playerInv, IOPortBlockEntity owner) {
        this(containerId, playerInv, owner, owner == null ? BlockPos.ZERO : owner.getBlockPos());
    }

    public SourcePortMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, null, pos);
    }

    private SourcePortMenu(int containerId, Inventory playerInv, IOPortBlockEntity owner, BlockPos pos) {
        super(ModUIs.SOURCE_PORT == null ? null : ModUIs.SOURCE_PORT.get(), containerId);
        this.owner = owner;
        this.pos = pos;
        SourceViewFacet source = owner == null ? null
                : owner.capabilitySnapshot().facets(SourceViewFacet.class).getFirst();
        this.stored = addLongDataSlot(source == null ? LongDataSlot.standalone()
                : new LongDataSlot(source::amount));
        this.capacity = addLongDataSlot(source == null ? LongDataSlot.standalone()
                : new LongDataSlot(source::capacity));
        addPlayerSlots(playerInv);
    }

    public static SourcePortMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buffer) {
        return new SourcePortMenu(containerId, playerInv, buffer.readBlockPos());
    }

    public IOPortBlockEntity owner() { return owner; }

    public BlockPos pos() { return pos; }

    public long storedSource() { return stored.value(); }

    public long sourceCapacity() { return capacity.value(); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || owner.getLevel() != null
                && owner.getLevel().getBlockEntity(pos) == owner
                && MenuSupport.stillValidWithin(player, pos);
    }
}
