package cn.howxu.mmcr.internal.storage;

import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Long-backed fluid storage that can expose one or more fixed tanks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LongFluidStorage extends LongResourceStorage<FluidResource>
        implements ResourceHandler<FluidResource> {

    public LongFluidStorage(long capacity, Runnable onChange) {
        this(1, capacity, onChange);
    }

    public LongFluidStorage(int slots, long capacity, Runnable onChange) {
        super(FluidResource.class, slots, capacity, FluidResource::isEmpty, onChange);
    }

    public long getCapacityAsLong() {
        return slotCapacity();
    }

    public long getAmountAsLong() {
        return amount(0);
    }

    public FluidResource getResource() {
        return resource(0);
    }

    public FluidStack getFluidStack() {
        if (isEmpty()) return FluidStack.EMPTY;
        return getResource().toStack((int) Math.min(getAmountAsLong(), Integer.MAX_VALUE));
    }

    public boolean isEmpty() {
        return getAmountAsLong() <= 0L || getResource().isEmpty();
    }

    public void setFluid(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            setContents(FluidResource.EMPTY, 0L);
        } else {
            setContents(FluidResource.of(stack), stack.getAmount());
        }
    }

    public void setContents(FluidResource resource, long amount) {
        super.setContents(0, resource, amount);
    }

    public void clearContent() {
        setContents(FluidResource.EMPTY, 0L);
    }

    public long forceInsert(FluidStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty()) return 0L;
        return insertDirect(0, FluidResource.of(stack), stack.getAmount(), simulate);
    }

    public long forceExtract(long max, boolean simulate) {
        if (max <= 0L || isEmpty()) return 0L;
        return extractDirect(0, getResource(), max, simulate);
    }

    @Override
    public FluidResource resource(int slot) {
        FluidResource resource = super.resource(slot);
        return resource == null ? FluidResource.EMPTY : resource;
    }

    @Override
    public FluidResource getResource(int slot) {
        return resource(slot);
    }

    @Override
    public long getAmountAsLong(int slot) {
        return amount(slot);
    }

    @Override
    public long getCapacityAsLong(int slot, FluidResource resource) {
        return capacity(slot, resource);
    }

    @Override
    public int insert(int slot, FluidResource resource, int amount, TransactionContext transaction) {
        return (int) super.insert(slot, resource, (long) amount, transaction);
    }

    @Override
    public int extract(int slot, FluidResource resource, int amount, TransactionContext transaction) {
        return (int) super.extract(slot, resource, (long) amount, transaction);
    }
}
