package cn.howxu.mmcr.compat.appmek.loaded;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalOutputAdmission;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Network-first chemical output with bounded local remainder storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class OutputChemicalHandler implements IChemicalHandler, NativeReservationAccess, ChemicalOutputAdmission {
    private final InventoryChemicalHandler local;
    private final Supplier<@Nullable MEStorage> storage;
    private final IActionSource source;
    private final Runnable changed;

    public OutputChemicalHandler(GenericStackInv inventory, Supplier<@Nullable MEStorage> storage,
                                 IActionSource source, Runnable changed) {
        this.local = new InventoryChemicalHandler(inventory);
        this.storage = storage;
        this.source = source;
        this.changed = changed;
    }

    @Override public int getChemicalTanks() { return local.getChemicalTanks(); }
    @Override public ChemicalStack getChemicalInTank(int tank) { return local.getChemicalInTank(tank); }
    @Override public long getChemicalTankCapacity(int tank) { return local.getChemicalTankCapacity(tank); }
    @Override public void setChemicalInTank(int tank, ChemicalStack stack) { local.setChemicalInTank(tank, stack); }
    @Override public boolean isValid(int tank, ChemicalStack stack) { return !stack.isEmpty(); }
    @Override public ChemicalStack extractChemical(int tank, long amount, Action action) { return local.extractChemical(tank, amount, action); }

    @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        return insertChemical(stack, action);
    }

    @Override public ChemicalStack insertChemical(ChemicalStack stack, Action action) {
        MekanismKey key = MekanismKey.of(stack);
        if (key == null) return stack.copy();
        MEStorage network = storage.get();
        long accepted = network == null ? 0L : network.insert(key, stack.getAmount(),
                action == Action.SIMULATE ? Actionable.SIMULATE : Actionable.MODULATE, source);
        accepted = Math.min(stack.getAmount(), Math.max(0L, accepted));
        ChemicalStack remainder = local.insertChemical(stack.copyWithAmount(stack.getAmount() - accepted), action);
        if (action == Action.EXECUTE && remainder.getAmount() < stack.getAmount()) changed.run();
        return remainder;
    }

    @Override public long outputAvailable(ChemicalStack identity, PlanningReservations reservations) {
        List<CapabilityRequests.ResourceAction<ChemicalStack>> actions = reserveOutput(identity, Long.MAX_VALUE, reservations.copy());
        return actions.isEmpty() ? 0L : actions.getFirst().amount();
    }

    @Override public List<CapabilityRequests.ResourceAction<ChemicalStack>> reserveOutput(
            ChemicalStack identity, long amount, PlanningReservations reservations) {
        MekanismKey key = MekanismKey.of(identity);
        if (key == null || amount <= 0L) return List.of();
        long remaining = amount;
        MEStorage network = storage.get();
        if (network != null) {
            long simulated = Math.max(0L, network.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, source));
            long admitted = Math.min(remaining, reservations.outputAvailable(network, key, simulated));
            if (admitted > 0L && reservations.reserveOutput(network, key, admitted)) remaining -= admitted;
        }
        for (int slot = 0; slot < local.getChemicalTanks() && remaining > 0L; slot++) {
            if (!local.isValid(slot, identity)) continue;
            Object virtualKey = reservations.nativeKey(local.reservationIdentity(), slot, local.storedKey(slot));
            long current = reservations.nativeAmount(local.reservationIdentity(), slot, local.storedAmount(slot));
            if (current > 0L && !key.equals(virtualKey)) continue;
            long capacity = local.getChemicalTankCapacity(slot);
            long simulated = remaining - local.insertChemical(slot, identity.copyWithAmount(remaining), Action.SIMULATE).getAmount();
            long admitted = Math.min(simulated, Math.max(0L, capacity - current));
            if (reservations.reserveNativeInsert(local.reservationIdentity(), slot, key,
                    local.storedKey(slot), local.storedAmount(slot), capacity, admitted)) remaining -= admitted;
        }
        long accepted = amount - remaining;
        return accepted == 0L ? List.of()
                : List.of(new CapabilityRequests.ResourceAction<>(-1, identity.copyWithAmount(1L), accepted, true));
    }

    @Override public Object reservationIdentity() { return local.reservationIdentity(); }
    boolean isSyncCapacityValid(ChemicalStack stack, long capacity) { return local.isSyncCapacityValid(stack, capacity); }
    @Override public Object reservationSlot(int slot) { return slot; }
    @Override public Object resourceKey(Object resource) { return local.resourceKey(resource); }
    @Override public Object resource(Object key) { return local.resource(key); }
    @Override public Object storedKey(int slot) { return local.storedKey(slot); }
    @Override public long storedAmount(int slot) { return local.storedAmount(slot); }
}
