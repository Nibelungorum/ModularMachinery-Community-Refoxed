package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class DirectionalItemSlot extends SlotItemHandler {

    private final IOType ioType;

    public DirectionalItemSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
        this.ioType = IOType.INPUT;
    }

    public DirectionalItemSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition, IOType ioType) {
        super(itemHandler, index, xPosition, yPosition);
        this.ioType = ioType;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack);
    }

    @Override
    public boolean mayPickup(Player playerIn) {
        return super.mayPickup(playerIn);
    }

    @Override
    public ItemStack safeInsert(ItemStack inputStack, int inputAmount) {
        return super.safeInsert(inputStack, inputAmount);
    }
}
