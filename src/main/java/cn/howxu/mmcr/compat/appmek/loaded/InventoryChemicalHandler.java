package cn.howxu.mmcr.compat.appmek.loaded;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;

import java.util.Objects;

/**
 * Ordinary chemical projection of an AE2 inventory, preserving mixed physical slots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class InventoryChemicalHandler implements IChemicalHandler, NativeReservationAccess {
    private final GenericStackInv inventory;

    public InventoryChemicalHandler(GenericStackInv inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override public int getChemicalTanks() { return inventory.size(); }

    @Override public ChemicalStack getChemicalInTank(int tank) {
        return inventory.getKey(tank) instanceof MekanismKey chemical
                ? chemical.withAmount(inventory.getAmount(tank)) : ChemicalStack.EMPTY;
    }

    @Override public long getChemicalTankCapacity(int tank) {
        AEKey key = inventory.getKey(tank);
        return key == null ? inventory.getCapacity(MekanismKeyType.TYPE)
                : key instanceof MekanismKey ? inventory.getMaxAmount(key) : 0L;
    }

    @Override public boolean isValid(int tank, ChemicalStack stack) {
        MekanismKey key = MekanismKey.of(stack);
        return key != null && ChemicalAttributeValidator.DEFAULT.process(stack) && inventory.isAllowedIn(tank, key);
    }

    @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        if (!isValid(tank, stack)) return stack.copy();
        long inserted = inventory.insert(tank, MekanismKey.of(stack), stack.getAmount(), mode(action));
        return stack.copyWithAmount(stack.getAmount() - inserted);
    }

    @Override public ChemicalStack extractChemical(int tank, long amount, Action action) {
        if (!(inventory.getKey(tank) instanceof MekanismKey key) || amount <= 0L
                || !ChemicalAttributeValidator.DEFAULT.process(key.getStack())) return ChemicalStack.EMPTY;
        return key.withAmount(inventory.extract(tank, key, amount, mode(action)));
    }

    @Override public void setChemicalInTank(int tank, ChemicalStack stack) {
        AEKey current = inventory.getKey(tank);
        if (current != null && !(current instanceof MekanismKey)) {
            if (stack.isEmpty()) return;
            throw new IllegalArgumentException("Cannot replace a non-chemical slot");
        }
        if (!stack.isEmpty() && (!isValid(tank, stack)
                || stack.getAmount() > inventory.getMaxAmount(MekanismKey.of(stack)))) {
            throw new IllegalArgumentException("Invalid chemical state");
        }
        inventory.setStack(tank, stack.isEmpty() ? null : new GenericStack(MekanismKey.of(stack), stack.getAmount()));
    }

    @Override public Object reservationIdentity() { return inventory; }

    boolean isSyncCapacityValid(ChemicalStack stack, long capacity) {
        return stack.isEmpty() ? capacity == 0L || capacity == inventory.getCapacity(MekanismKeyType.TYPE)
                : capacity == inventory.getMaxAmount(MekanismKey.of(stack));
    }
    @Override public Object reservationSlot(int slot) { return slot; }
    @Override public Object resourceKey(Object resource) { return MekanismKey.of((ChemicalStack) resource); }
    @Override public Object resource(Object key) { return key instanceof MekanismKey chemical ? chemical.withAmount(1L) : ChemicalStack.EMPTY; }
    @Override public Object storedKey(int slot) { return inventory.getKey(slot); }
    @Override public long storedAmount(int slot) { return inventory.getAmount(slot); }

    private static Actionable mode(Action action) {
        return action == Action.SIMULATE ? Actionable.SIMULATE : Actionable.MODULATE;
    }
}
