package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Menu for viewing the values stored by a data-storage block.
 * @author howxu <dev@howxu.cn>
 */
public final class DataStorageMenu extends AbstractMachineMenu {
    private final DataStorageBlockEntity owner;
    private final BlockPos pos;

    public DataStorageMenu(int containerId, Inventory inventory, DataStorageBlockEntity owner) {
        super(ModUIs.DATA_STORAGE.get(), containerId);
        this.owner = owner;
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        addPlayerSlots(inventory);
    }

    private DataStorageMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModUIs.DATA_STORAGE.get(), containerId);
        owner = null;
        this.pos = pos;
        addPlayerSlots(inventory);
    }

    public static DataStorageMenu clientOpen(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new DataStorageMenu(containerId, inventory, buffer.readBlockPos());
    }

    public BlockPos pos() {
        return pos;
    }

    public DataStorageBlockEntity owner() {
        return owner;
    }

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
