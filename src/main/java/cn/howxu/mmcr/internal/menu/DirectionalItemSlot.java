package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class DirectionalItemSlot extends SlotItemHandler {
    private final IOType ioType;

    public DirectionalItemSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition, IOType ioType) {
        super(itemHandler, index, xPosition, yPosition);
        this.ioType = ioType;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return ioType == IOType.INPUT;
    }

    @Override
    public boolean mayPickup(Player player) {
        return true;
    }
}
