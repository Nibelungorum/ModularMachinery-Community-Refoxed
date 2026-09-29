package cn.howxu.mmcr.internal.capability;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Packet synchronization hooks for native handlers that retain long slot amounts.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class NativeStackSync {
    private NativeStackSync() {
    }

    public interface Item {
        long amount(int slot);

        long capacity(int slot);

        void setContents(int slot, ItemStack stack, long amount);
    }

    public interface Fluid {
        long amount(int tank);

        long capacity(int tank);

        void setContents(int tank, FluidStack stack, long amount);
    }
}
