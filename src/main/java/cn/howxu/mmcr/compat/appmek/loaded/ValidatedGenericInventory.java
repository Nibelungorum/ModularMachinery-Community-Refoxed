package cn.howxu.mmcr.compat.appmek.loaded;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import me.ramidzkh.mekae2.ae2.MekanismKey;

/**
 * Chemical-key validation for AE2's externally exposed generic inventory.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ValidatedGenericInventory implements GenericInternalInventory {
    private final GenericInternalInventory delegate;

    public ValidatedGenericInventory(GenericInternalInventory delegate) { this.delegate = delegate; }

    static boolean accepted(AEKey key) {
        return !(key instanceof MekanismKey chemical) || !chemical.getStack().isEmpty();
    }

    @Override public int size() { return delegate.size(); }
    @Override public GenericStack getStack(int slot) { return delegate.getStack(slot); }
    @Override public AEKey getKey(int slot) { return delegate.getKey(slot); }
    @Override public long getAmount(int slot) { return delegate.getAmount(slot); }
    @Override public long getMaxAmount(AEKey key) { return delegate.getMaxAmount(key); }
    @Override public long getCapacity(AEKeyType type) { return delegate.getCapacity(type); }
    @Override public boolean canInsert() { return delegate.canInsert(); }
    @Override public boolean canExtract() { return delegate.canExtract(); }
    @Override public boolean isSupportedType(AEKeyType type) { return delegate.isSupportedType(type); }
    @Override public boolean isAllowedIn(int slot, AEKey key) { return accepted(key) && delegate.isAllowedIn(slot, key); }
    @Override public long insert(int slot, AEKey key, long amount, Actionable mode) { return accepted(key) ? delegate.insert(slot, key, amount, mode) : 0L; }
    @Override public long extract(int slot, AEKey key, long amount, Actionable mode) { return delegate.extract(slot, key, amount, mode); }
    @Override public void beginBatch() { delegate.beginBatch(); }
    @Override public void endBatch() { delegate.endBatch(); }
    @Override public void endBatchSuppressed() { delegate.endBatchSuppressed(); }
    @Override public void onChange() { delegate.onChange(); }
    @Override public void setStack(int slot, GenericStack stack) {
        if (stack != null && !accepted(stack.what())) throw new IllegalArgumentException("Invalid chemical state");
        delegate.setStack(slot, stack);
    }
}
