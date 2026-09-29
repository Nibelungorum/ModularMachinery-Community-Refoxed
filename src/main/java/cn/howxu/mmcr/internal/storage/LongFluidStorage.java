package cn.howxu.mmcr.internal.storage;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Native fluid handler backed by long per-tank quantities.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongFluidStorage implements IFluidHandler {
    private final LongSlotStorage<FluidStack> storage;

    public LongFluidStorage(long capacity, Runnable onChange) {
        this(1, capacity, onChange);
    }

    public LongFluidStorage(int slots, long capacity, Runnable onChange) {
        storage = new LongSlotStorage<>(slots, capacity, FluidStack::isEmpty,
                stack -> stack.copyWithAmount(1), FluidStack::isSameFluidSameComponents, onChange);
    }

    public int size() {
        return storage.size();
    }

    public long amount(int slot) {
        return storage.amount(slot);
    }

    public long capacity(int slot) {
        return storage.capacity(slot);
    }

    public long getCapacityAsLong() {
        return capacity(0);
    }

    public long getAmountAsLong() {
        return amount(0);
    }

    public long getAmountAsLong(int slot) {
        return amount(slot);
    }

    public long getCapacityAsLong(int slot) {
        return capacity(slot);
    }

    public FluidStack resource(int slot) {
        FluidStack resource = storage.resource(slot);
        return resource == null ? FluidStack.EMPTY : resource;
    }

    public FluidStack getFluidStack() {
        return getFluidInTank(0);
    }

    public boolean isEmpty() {
        return amount(0) == 0L;
    }

    public void setFluid(FluidStack stack) {
        setContents(0, stack, stack == null ? 0L : stack.getAmount());
    }

    public void setContents(int slot, FluidStack stack, long amount) {
        storage.setContents(slot, stack == null ? FluidStack.EMPTY : stack, amount);
    }

    public void clearContent() {
        setContents(0, FluidStack.EMPTY, 0L);
    }

    public long forceInsert(FluidStack stack, boolean simulate) {
        return forceInsert(0, stack, stack == null ? 0L : stack.getAmount(), simulate);
    }

    public long forceInsert(int slot, FluidStack stack, long amount, boolean simulate) {
        if (stack == null || stack.isEmpty()) return 0L;
        return storage.insertDirect(slot, stack, amount, simulate);
    }

    public long forceExtract(long max, boolean simulate) {
        return forceExtract(0, max, simulate);
    }

    public long forceExtract(int slot, long max, boolean simulate) {
        FluidStack resource = resource(slot);
        return resource.isEmpty() ? 0L : storage.extractDirect(slot, resource, max, simulate);
    }

    @Override
    public int getTanks() {
        return storage.size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        FluidStack resource = resource(tank);
        return resource.isEmpty() ? FluidStack.EMPTY
                : resource.copyWithAmount((int) Math.min(storage.amount(tank), Integer.MAX_VALUE));
    }

    @Override
    public int getTankCapacity(int tank) {
        return (int) Math.min(storage.capacity(tank), Integer.MAX_VALUE);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack != null && !stack.isEmpty() && storage.isValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return 0;
        int remaining = resource.getAmount();
        for (int tank = 0; tank < storage.size() && remaining > 0; tank++) {
            long inserted = storage.insertDirect(tank, resource, remaining, action.simulate());
            remaining -= (int) inserted;
        }
        return resource.getAmount() - remaining;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return FluidStack.EMPTY;
        for (int tank = 0; tank < storage.size(); tank++) {
            long extracted = storage.extractDirect(tank, resource, resource.getAmount(), action.simulate());
            if (extracted > 0L) return resource.copyWithAmount((int) extracted);
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        for (int tank = 0; tank < storage.size(); tank++) {
            FluidStack resource = resource(tank);
            if (resource.isEmpty()) continue;
            long extracted = storage.extractDirect(tank, resource, maxDrain, action.simulate());
            if (extracted > 0L) return resource.copyWithAmount((int) extracted);
        }
        return FluidStack.EMPTY;
    }
}
