package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Network-first output admission with shared per-key network reservations and bounded local remainders.
 * MEStorage cannot simulate a batch of different keys atomically; native commit still rechecks each insertion.
 *
 * @author howxu <dev@howxu.cn>
 */
final class NetworkOutputAdmission {
    private final GenericStackInv inventory;
    private final Supplier<@Nullable MEStorage> storage;
    private final IActionSource source;
    private final Runnable changed;

    NetworkOutputAdmission(GenericStackInv inventory, Supplier<@Nullable MEStorage> storage,
                           IActionSource source, Runnable changed) {
        this.inventory = inventory;
        this.storage = storage;
        this.source = source;
        this.changed = changed;
    }

    long available(AEKey key, PlanningReservations reservations) {
        return reserve(key, Long.MAX_VALUE, reservations.copy());
    }

    long reserve(AEKey key, long amount, PlanningReservations reservations) {
        if (key == null || amount <= 0L) return 0L;
        long remaining = amount;
        MEStorage network = storage.get();
        if (network != null) {
            long simulated = Math.max(0L, network.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, source));
            long accepted = Math.min(remaining, reservations.outputAvailable(network, key, simulated));
            if (accepted > 0L && reservations.reserveOutput(network, key, accepted)) remaining -= accepted;
        }
        for (int slot = 0; inventory.canInsert() && slot < inventory.size() && remaining > 0L; slot++) {
            if (!inventory.isAllowedIn(slot, key)) continue;
            Object currentKey = reservations.nativeKey(inventory, slot, inventory.getKey(slot));
            long current = reservations.nativeAmount(inventory, slot, inventory.getAmount(slot));
            if (current > 0L && !key.equals(currentKey)) continue;
            long capacity = inventory.getMaxAmount(key);
            long accepted = Math.min(remaining, Math.max(0L, capacity - current));
            if (reservations.reserveNativeInsert(inventory, slot, key, inventory.getKey(slot),
                    inventory.getAmount(slot), capacity, accepted)) remaining -= accepted;
        }
        return amount - remaining;
    }

    long insert(AEKey key, long amount, boolean simulate) {
        if (key == null || amount <= 0L) return 0L;
        Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        MEStorage network = storage.get();
        long accepted = network == null ? 0L
                : Math.clamp(network.insert(key, amount, mode, source), 0L, amount);
        for (int slot = 0; slot < inventory.size() && accepted < amount; slot++) {
            // Bound before GenericStackInv adds to the stored amount, avoiding long overflow.
            long requested = Math.min(amount - accepted,
                    Math.max(0L, inventory.getMaxAmount(key) - inventory.getAmount(slot)));
            accepted += inventory.insert(slot, key, requested, mode);
        }
        if (!simulate && accepted > 0L) changed.run();
        return accepted;
    }
}
