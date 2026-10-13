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
        default boolean supportsLargeStacks() { return false; }

        long amount(int slot);

        long capacity(int slot);

        default long capacity(int slot, ItemStack resource) { return capacity(slot); }

        default boolean isSyncCapacityValid(int slot, ItemStack stack, long capacity) {
            return capacity == capacity(slot);
        }

        void setContents(int slot, ItemStack stack, long amount);

        default void setContents(ItemStack[] stacks, long[] amounts) {
            for (int slot = 0; slot < stacks.length; slot++) setContents(slot, stacks[slot], amounts[slot]);
        }
    }

    public interface Fluid {
        long amount(int tank);

        long capacity(int tank);

        default boolean isSyncCapacityValid(int tank, FluidStack stack, long capacity) {
            return capacity == capacity(tank);
        }

        void setContents(int tank, FluidStack stack, long amount);

        default void setContents(FluidStack[] stacks, long[] amounts) {
            for (int tank = 0; tank < stacks.length; tank++) setContents(tank, stacks[tank], amounts[tank]);
        }
    }
}
