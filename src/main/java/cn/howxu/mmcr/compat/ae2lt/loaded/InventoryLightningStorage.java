package cn.howxu.mmcr.compat.ae2lt.loaded;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import java.util.Objects;

/** Lightning projection retaining the physical inventory's slot addresses, including foreign keys.
 * @author howxu <dev@howxu.cn>
 */
public class InventoryLightningStorage implements LightningStorage {
    protected final GenericStackInv inventory;

    public InventoryLightningStorage(GenericStackInv inventory) { this.inventory = Objects.requireNonNull(inventory, "inventory"); }
    @Override public int size() { return inventory.size(); }
    @Override public Object identity() { return inventory; }
    @Override public Object address(int slot) { return slot; }
    @Override public AEKey key(LightningTier tier) { return LoadedLightningBridge.key(tier); }
    @Override public Object storedKey(int slot) { return inventory.getKey(slot); }
    @Override public long amount(int slot) { return inventory.getAmount(slot); }
    @Override public long capacity(int slot, LightningTier tier) {
        return inventory.isAllowedIn(slot, key(tier))
                ? PatternReturnInventory.slotCapacity(inventory, slot, key(tier), key(tier).getType()) : 0L;
    }
    @Override public long extract(int slot, LightningTier tier, long amount, boolean simulate) {
        return inventory.extract(slot, key(tier), amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE);
    }
    @Override public long reserveOutput(LightningTier tier, long amount, PlanningReservations reservations) {
        AEKey key = key(tier);
        long remaining = amount;
        long budget = inventory instanceof PatternReturnInventory returns
                ? reservations.outputAvailable(inventory, key, returns.remainingCapacity(key)) : Long.MAX_VALUE;
        for (int slot = 0; inventory.canInsert() && slot < size() && remaining > 0L && budget > 0L; slot++) {
            Object current = reservations.nativeKey(inventory, slot, storedKey(slot));
            long stored = reservations.nativeAmount(inventory, slot, amount(slot));
            if (stored > 0L && !key.equals(current)) continue;
            long capacity = capacity(slot, tier);
            long moved = Math.min(budget, Math.min(remaining, Math.max(0L, capacity - stored)));
            if (moved > 0L && reservations.reserveNativeInsert(inventory, slot, key, storedKey(slot), amount(slot), capacity, moved)) {
                remaining -= moved;
                budget -= moved;
            }
        }
        long accepted = amount - remaining;
        if (accepted > 0L && inventory instanceof PatternReturnInventory) reservations.reserveOutput(inventory, key, accepted);
        return accepted;
    }
    @Override public long insert(int slot, LightningTier tier, long amount, boolean simulate) {
        AEKey key = key(tier);
        if (inventory instanceof PatternReturnInventory returns) amount = Math.min(amount, returns.remainingCapacity(key));
        return inventory.insert(slot, key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE);
    }
    @Override public long insert(LightningTier tier, long amount, boolean simulate) {
        if (inventory instanceof PatternReturnInventory returns) amount = Math.min(amount, returns.remainingCapacity(key(tier)));
        long accepted = 0L;
        for (int slot = 0; slot < size() && accepted < amount; slot++) {
            long requested = Math.min(amount - accepted, Math.max(0L, capacity(slot, tier) - inventory.getAmount(slot)));
            accepted += inventory.insert(slot, key(tier), requested, simulate ? Actionable.SIMULATE : Actionable.MODULATE);
        }
        return accepted;
    }
}
