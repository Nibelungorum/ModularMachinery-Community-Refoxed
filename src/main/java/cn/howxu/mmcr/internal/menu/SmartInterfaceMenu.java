package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Menu backing a smart-interface block.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SmartInterfaceMenu extends AbstractMachineMenu {
    private final SmartInterfaceBlockEntity owner;
    private final BlockPos pos;

    public SmartInterfaceMenu(int containerId, Inventory inventory, SmartInterfaceBlockEntity owner) {
        super(ModUIs.SMART_INTERFACE.get(), containerId);
        this.owner = owner;
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        addPlayerSlots(inventory);
    }

    private SmartInterfaceMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModUIs.SMART_INTERFACE.get(), containerId);
        this.owner = null;
        this.pos = pos;
        addPlayerSlots(inventory);
    }

    public static SmartInterfaceMenu clientOpen(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new SmartInterfaceMenu(containerId, inventory, buffer.readBlockPos());
    }

    public static SmartInterfaceMenu clientOpen(int containerId, Inventory inventory, BlockPos pos) {
        return new SmartInterfaceMenu(containerId, inventory, pos);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || MenuSupport.stillValidWithin(player, pos);
    }
}
